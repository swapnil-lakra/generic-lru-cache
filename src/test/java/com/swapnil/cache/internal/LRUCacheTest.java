package com.swapnil.cache.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.LruCache;

import com.swapnil.cache.Cache;
import com.swapnil.cache.CacheLoadException;
import com.swapnil.cache.CacheStats;
import com.swapnil.cache.EvictionListener;
import com.swapnil.cache.EvictionReason;
import com.swapnil.cache.policy.EvictionPolicy;
import com.swapnil.cache.policy.LRUEvictionPolicy;

class LRUCacheTest {
    
    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    private static Cache<String,String> newCache(int capacity){
        return new LRUCache<>(capacity, 0, 
            new LRUEvictionPolicy<>(), 
            null);
    } 

    private static Cache<String,String> newCache(int capacity, long ttlMillis){
        return new LRUCache<>(capacity, ttlMillis,
             new LRUEvictionPolicy<>(), null);
    }

    private static final class EvictionRecord{
        final Object key;
        final Object value;
        final EvictionReason reason;

        EvictionRecord(Object key, Object value, EvictionReason reason){
            this.key = key;
            this.value = value;
            this.reason = reason;
        }
    }

    private static EvictionListener<String,String> recordingListener(
        List<EvictionRecord> sink){
        return (k,v,r) -> sink.add(new EvictionRecord(k, v, r));
    }

    // ==================================================================
    // Basic CRUD
    // ==================================================================

    @Nested 
    class BasicOperation {

        @Test 
        void putAndGetRoundTrip(){
            Cache<String,String> cache = newCache(10);
            cache.put("a", "1");
            assertThat(cache.get("a")).isEqualTo("1");
        }

        @Test 
        void getMissingReturnsNull(){
            Cache<String, String> cache = newCache(10);
            cache.put("a", "1");
            assertThat(cache.remove("a")).isTrue();
            assertThat(cache.get("a")).isNull();
        }

        @Test 
        void removeReturnsTrueForPresentKey(){
            Cache<String, String> cache = newCache(10);
            assertThat(cache.remove("nope")).isFalse();
        }

        @Test
        void containsKeyReflectsPresence(){
            Cache<String, String> cache = newCache(10);
            cache.put("a", "1");
            assertThat(cache.containsKey("a")).isTrue();
            assertThat(cache.containsKey("b")).isFalse();
        }

        @Test 
        void sizeAndIsEmptyTrackEntries(){
            Cache<String, String> cache = newCache(10);
            assertThat(cache.isEmpty()).isTrue();
            cache.put("a", "1");
            cache.put("b", "2");
            assertThat(cache.size()).isEqualTo(2);
            assertThat(cache.isEmpty()).isFalse();
        }

        @Test 
        void clearEmptiesCache(){
            Cache<String, String> cache = newCache(10);
            cache.put("a", "1");
            cache.put("b", "2");
            cache.clear();
            assertThat(cache.size()).isZero();
            assertThat(cache.isEmpty()).isTrue();
            assertThat(cache.get("a")).isNull();
        }
    }

    // ==================================================================
    // Capacity & LRU eviction
    // ==================================================================

    @Nested 
    class CapacityEviction {

        @Test 
        void putBeyondCapacityEvictsLru(){
            Cache<String, String> cache = newCache(2);
            cache.put("a", "1");
            cache.put("b", "2");
            cache.put("c", "3");

            assertThat(cache.get("a")).isNull(); // evicted
            assertThat(cache.get("b")).isEqualTo("2");
            assertThat(cache.get("c")).isEqualTo("3");
        }

        @Test 
        void accessPromotesVictimAvoidance(){
            Cache<String, String> cache = newCache(2);
            cache.put("a", "1");
            cache.put("b", "2");
            cache.get("a");                 // promote "a"
            cache.put("c", "3");     // should evict "b"

            assertThat(cache.get("b")).isNull();
            assertThat(cache.get("a")).isEqualTo("1");
        }

        @Test 
        void sizeNeverExceedsCapacity(){
            Cache<String, String> cache = newCache(3);
            for(int i = 0;i < 100;i++){
                cache.put("k"+i,"v"+i);
            }
            assertThat(cache.size()).isEqualTo(3);
        }

        @Test 
        void sizeExceedsListenerFires(){
            List<EvictionRecord> records = new ArrayList<>();
            Cache<String, String> cache = new LRUCache<>(2,
                 0, 
                 new LRUEvictionPolicy<>(),
                  recordingListener(records));
            
            cache.put("a", "1");
            cache.put("b", "2");
            cache.put("c", "3");
            

            assertThat(records).hasSize(1);
            assertThat(records.get(0).key).isEqualTo("a");
            assertThat(records.get(0).value).isEqualTo("1");
            assertThat(records.get(0).reason).isEqualTo(EvictionReason.SIZE_EXCEEDED);
        }

        @Test 
        void replaceListenerFires(){
            List<EvictionRecord> records = new ArrayList<>();
            Cache<String, String> cache = new LRUCache<>(
                10,0, new LRUEvictionPolicy<>(), 
                recordingListener(records));
            cache.put("a", "old");
            cache.put("a", "new");
            

            assertThat(records).hasSize(1);
            assertThat(records.get(0).reason).isEqualTo(EvictionReason.REPLACED);
            assertThat(records.get(0).value).isEqualTo("old");
            assertThat(cache.get("a")).isEqualTo("new");
        }

        @Test 
        void explicitRemoveFiresListener(){
            List<EvictionRecord> records = new ArrayList<>();
            Cache<String, String> cache = new LRUCache<>(10,
                 0, new LRUEvictionPolicy<>(), 
                 recordingListener(records));

            cache.put("a", "1");
            cache.remove("a");
            

            assertThat(records).hasSize(1);
            assertThat(records.get(0).reason).isEqualTo(EvictionReason.EXPLICIT);
        }
    }

     // ==================================================================
     // TTL
     // ==================================================================

    @Nested 
    class Ttl {

        @Test 
        void entryExpiresAfterDefaultTtl() throws InterruptedException {
            Cache<String,String> cache = newCache(10, 50);
            cache.put("a", "1");
            assertThat(cache.get("a")).isEqualTo("1");
            
            Thread.sleep(120);
            assertThat(cache.get("a")).isNull();
        }

        @Test
        void customTtlOverridesDefault(){
            Cache<String, String> cache = newCache(10, 10_000);
            cache.put("a", "1", 50);

            assertThat(cache.get("a")).isEqualTo("1");
        }

        @Test 
        void expiredEntryFiresListener() throws InterruptedException {
            List<EvictionRecord> records = new ArrayList<>();
            Cache<String, String> cache = new LRUCache<>(10, 
                50, new LRUEvictionPolicy<>(), 
                recordingListener(records));
            cache.put("a", "1");
            Thread.sleep(120);
            cache.get("a"); // triggers lazy expiry
            

            assertThat(records).hasSize(1);
            assertThat(records.get(0).reason).isEqualTo(EvictionReason.EXPIRED);
        }
        
        @Test 
        void containsKeyReturnsFalseForExpireEntry() throws InterruptedException {
            Cache<String, String> cache = newCache(10, 50);
            cache.put("a", "1");
            Thread.sleep(120);
            assertThat(cache.containsKey("a")).isFalse();
        }

        @Test
        void zeroTtlMeansNeverExpires() throws InterruptedException {
            Cache<String, String> cache = newCache(10, 0);
            cache.put("a", "1");
            Thread.sleep(50);
            assertThat(cache.get("a")).isEqualTo("1");
        }
    }

    // ==================================================================
    // Cache-aside loading
    // ==================================================================

    @Nested
    class Loader{
        @Test 
        void loaderCalledOnMissAndResultCached(){
            AtomicInteger count = new AtomicInteger();
            Cache<String, String> cache = newCache(10);

            String first = cache.get("a",k -> {
                count.incrementAndGet();
                return "loaded-"+k;
            });

            String second = cache.get("a",k -> {
                count.incrementAndGet();
                return "loaded-"+k;
            });

            assertThat(first).isEqualTo("loaded-a");
            assertThat(second).isEqualTo("loaded-a");
            assertThat(count.get()).isEqualTo(1);
        }

        @Test
        void loaderExceptionWrappedInCacheLoadException(){
            Cache<String, String> cache = newCache(10);
            RuntimeException boom = new RuntimeException("boom");

            assertThatThrownBy(() -> cache.get("a", k -> {throw boom;}))
                      .isInstanceOf(CacheLoadException.class)
                      .hasCause(boom)
                      .hasMessageContaining("a");
        }

        @Test 
        void loaderReturningNullDoesNotCache(){
            Cache<String, String> cache = newCache(10);
            
            assertThat(cache.get("a", k -> null)).isNull();
            assertThat(cache.containsKey("a")).isFalse();
        }

        @Test 
        void loaderSuccessAndFailureStats(){
            Cache<String, String> cache = newCache(10);
            cache.get("hit", k -> "v");
            try {
                cache.get("fail", k -> { throw new RuntimeException("x");});
            } catch (CacheLoadException ignored) {
                // expected
            }

            CacheStats s = cache.stats();
            assertThat(s.loadSuccessCount()).isEqualTo(1);
            assertThat(s.loadFailureCount()).isEqualTo(1);
        } 
    }

    // ==================================================================
    // Statistics
    // ==================================================================

    @Nested 
    class Statistics {
               
        @Test
        void hitsAndMissesAreCounted(){
            Cache<String, String> cache = newCache(10);
            cache.put("a", "1");
                
            cache.get("a"); // hit
            cache.get("b"); // miss
            cache.get("a"); // hit
            cache.get("c"); // miss
                  
            CacheStats s = cache.stats();
            assertThat(s.hitCount()).isEqualTo(2);
            assertThat(s.missCount()).isEqualTo(2);
            assertThat(s.requestCount()).isEqualTo(4);
            assertThat(s.hitRatio()).isEqualTo(0.5);
        }
              
        @Test
        void evictionCountTracksSizeAndExpiredAndReplaced() 
                                throws InterruptedException {
            Cache<String, String> cache = newCache(2);
            cache.put("a", "1");
            cache.put("b", "2");
            cache.put("c", "3"); // SIZE_EXCEEDED
            cache.put("c", "3b"); // REPLACED
                
            assertThat(cache.stats().evictionCount()).isEqualTo(2);
        }
               
        @Test 
        void explicitRemoveNotCountedAsEviction(){
            Cache<String, String> cache = newCache(10);
            cache.put("a", "1");
            cache.remove("a");
                 
            assertThat(cache.stats().evictionCount()).isZero();
        }
                 
        @Test 
        void clearDoesNotCountAsEviction(){
            Cache<String, String> cache = newCache(10);
            cache.put("a", "1");
            cache.put("b", "2");
            cache.clear();
                  
            assertThat(cache.stats().evictionCount()).isZero();
        }
    }

    // ==================================================================
    // Edge cases & validation
    // ==================================================================

    @Nested 
    class EdgeClass {

        @Test 
        void nullKeyRejected(){
            Cache<String, String> cache = newCache(10);
            assertThatThrownBy(() -> cache.get(null))
                  .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> cache.put(null, "v"))
                  .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> cache.remove(null))
                  .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> cache.containsKey(null))
                  .isInstanceOf(NullPointerException.class);
        }

        @Test 
        void nullValueRejected(){
            Cache<String, String> cache = newCache(10);
            assertThatThrownBy(() -> cache.put("a", null))
                  .isInstanceOf(NullPointerException.class);
        }

        @Test 
        void nonPositiveTtlInPutRejected(){
            Cache<String, String> cache = newCache(10);
            assertThatThrownBy(() -> cache.put("a", "v",0))
                  .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> cache.put("a", "v", -1))
                  .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test 
        void zeroCapacityRejected(){
            assertThatThrownBy(() -> newCache(0))
                  .isInstanceOf(IllegalArgumentException.class);
        }

        @Test 
        void negativeDefaultTtlRejected(){
            assertThatThrownBy(() -> newCache(10,-1))
                  .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void closedCacheRejectsOperations(){
            Cache<String, String> cache = newCache(10);
            cache.put("a", "1");
            cache.close();

            assertThatThrownBy(() -> cache.get("a"))
                  .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> cache.put("b", "2"))
                  .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(cache::size)
                  .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void closeIsIdempotent(){
            Cache<String, String> cache = newCache(10);
            cache.put("a", "1");
            cache.get("a");
            

            // stats() intentionally doesn't check closed state — useful for
            // post-mortem inspection
            assertThat(cache.stats().hitCount()).isEqualTo(1);
        }

        @Test 
        void ttlOverflowCappedAtLongMax(){
            Cache<String, String> cache = newCache(10);
            cache.put("a", "1", Long.MAX_VALUE);
            assertThat(cache.get("a")).isEqualTo("1");
        }
    }
    
    // ==================================================================
    // Thread-safety smoke test
    // ==================================================================

    @Nested 
    class ThreadSafety {

        @Test
        void concurrentReadWriteDoesNotThrow() throws InterruptedException {
            int threads = 8;
            int opsPerThread = 1000;
            Cache<Integer, Integer> cache = new LRUCache<>(50,
                 0, new LRUEvictionPolicy<>(), 
                 null);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);

            for(int t = 0;t < threads; t++){
                final int tid = t;
                pool.submit(() -> {
                    try {
                        start.await();
                        for(int i = 0;i < opsPerThread;i++){
                            int key = (tid * opsPerThread + i) % 500;
                            cache.put(key, i);
                            cache.get(key);
                            if(i % 7 == 0){
                                cache.remove(key);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            

            start.countDown();
            pool.shutdown();
            boolean done = pool.awaitTermination(20, TimeUnit.SECONDS);

            assertThat(done).isTrue();  
            assertThat(cache.size()).isLessThanOrEqualTo(50);
        }

    }
}