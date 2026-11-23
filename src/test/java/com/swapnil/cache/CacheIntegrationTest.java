package com.swapnil.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.swapnil.cache.policy.EvictionPolicy;

/**
 * End-to-end tests using only the public API: {@link CacheBuilder} →
 * {@link Cache} → {@link CacheLoader} → {@link EvictionListener} →
 * {@link CacheStats} → {@link Cache#close()}.
 *
 * <p>These tests deliberately do <b>not</b> reach into {@code internal}
 * classes — they exercise the library the way a user would.</p>
 */
class CacheIntegrationTest {

    // ==================================================================
    // Realistic end-to-end flow
    // ==================================================================

    @Nested 
    class RealisticFlow {

        @Test
        void loaderPopulatesCacheAndStatsReflectReality() {
            List<String> evictions = new CopyOnWriteArrayList<>();
            AtomicInteger loaderCalls = new AtomicInteger();

            Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                    .capacity(3)
                    .evictionListener((k, v, r) -> evictions.add(k + ":" + r))
                    .build();

            try {
                // Loader populates on miss
                String a = cache.get("a", key -> {
                    loaderCalls.incrementAndGet();
                    return "value-" + key;
                });
                assertThat(a).isEqualTo("value-a");
                assertThat(loaderCalls.get()).isEqualTo(1);

                // Second call for "a" -- served from cache, loader NOT called
                String a2 = cache.get("a", key -> {
                    throw new AssertionError("loader should not be called on hit");
                });
                assertThat(a2).isEqualTo("value-a");
                assertThat(loaderCalls.get()).isEqualTo(1);

                // Fill cache
                cache.put("b", "value-b");
                cache.put("c", "value-c");

                // Touch "a" -- "b" becomes LRU
                cache.get("a");

                // Overflow -- evicts "b"
                cache.put("d", "value-d");
                assertThat(cache.get("b")).isNull();
                assertThat(evictions).contains("b:SIZE_EXCEEDED");

                // Explicit remove
                cache.remove("c");
                assertThat(evictions).contains("c:EXPLICIT");

                // Stats invariants
                CacheStats s = cache.stats();
                System.out.println("STATS BEFORE ASSERT: " + s);
                assertThat(s.loadSuccessCount()).isEqualTo(1);
                assertThat(s.loadFailureCount()).isZero();
                assertThat(s.hitCount()).isEqualTo(2);   // two get("a") hits
                assertThat(s.missCount()).isEqualTo(2);  // get("b") after eviction
                assertThat(s.evictionCount()).isEqualTo(1); // "b" SIZE + "c" EXPLICIT... plus "c" replaced? no
            } finally {
                cache.close();
            }
        }

        @Test 
        void customFifoPolicyFlowsThroughBuilder(){
            Cache<String, String> cache = CacheBuilder
                        .<String, String>newBuilder()
                        .capacity(2)
                        .evictionPolicy(new FifoPolicy<>())
                        .build();
            
            try {
                cache.put("a", "1");
                cache.put("b", "2");
                cache.get("a");
                cache.put("c", "3");

                assertThat(cache.get("a")).isNull();
                assertThat(cache.get("b")).isEqualTo("2");
                assertThat(cache.get("c")).isEqualTo("3");
            } finally {
                cache.close();
            }
        }

        @Test
        void tryWithResourcesClosesCache(){
            Cache<String, String> leaked;

            try(Cache<String, String> cache 
                = CacheBuilder.<String, String>newBuilder()
                .build()){
                leaked = cache;
                cache.put("a", "1");
                assertThat(cache.get("a")).isEqualTo("1");    
            }
            // Auto-closed -- subsequent ops throw
            assertThatThrownBy(() -> leaked.get("a"))
                  .isInstanceOf(IllegalStateException.class);
        }
    }

    // ==================================================================
    // TTL + background cleanup integration
    // ==================================================================

    @Nested 
    class TtlIntegration { 

        @Test 
        void backgroundCleanupEmptiesExpiredEntries() throws 
                            InterruptedException {
            List<EvictionReason> reasons = new CopyOnWriteArrayList<>();
            Cache<Integer, String> cache = CacheBuilder
                         .<Integer, String>newBuilder()
                         .capacity(200)
                         .defaultTtlMillis(30)
                         .backgroundCleanup(true)
                         .cleanupIntervalMillis(20)
                         .evictionListener((k, v, r) -> reasons.add(r))
                         .build();
            
            try {
                for(int i = 0;i < 50;i++){
                    cache.put(i, "v"+i);
                }
                assertThat(cache.size()).isEqualTo(50);

                // Wait for TTL + at least one sweep
                long deadline = System.currentTimeMillis() + 1500;
                while(cache.size() > 0 && System.currentTimeMillis() < deadline){
                    Thread.sleep(20);
                }

                assertThat(cache.size()).isZero();
                assertThat(reasons)
                      .isNotEmpty()
                      .allMatch(r -> r == EvictionReason.EXPIRED);
            } finally {
                cache.close();
            }
        }

        @Test 
        void perEntryTtlOverridesDefault() throws InterruptedException {
            Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                         .defaultTtlMillis(10_000)
                         .build();
            
            try{
                cache.put("short", "v1", 30);
                cache.put("long", "v2"); // uses default 10s

                Thread.sleep(120);

                assertThat(cache.get("short")).isNull();
                assertThat(cache.get("long")).isEqualTo("v2");
            }finally{
                cache.close();
            }
        }
    }

    // ==================================================================
    // Specific design behaviors
    // ==================================================================

    @Nested 
    class SpecificBehaviors {

        @Test
        void putOnExpiredExistingFiresExpiredNotReplaced() throws InterruptedException {
            List<EvictionReason> reasons = new CopyOnWriteArrayList<>();
            Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                                          .evictionListener((k, v, r) -> reasons.add(r))
                                          .build();
            
            try {
                cache.put("a", "old", 30);
                Thread.sleep(120);

                // Overwrite -- existing is expired, so reason must be EXPIRED
                cache.put("a", "new");

                assertThat(reasons).containsExactly(EvictionReason.EXPIRED);
                assertThat(cache.get("a")).isEqualTo("new");
            } finally {
                cache.close();
            }
        }

        @Test
        void statsSnapshotsAreIndependent(){
            Cache<String, String> cache = CacheBuilder.<String, String>newBuilder().build();

            try {
                cache.put("a", "1");
                cache.get("a");             // 1 hit
                CacheStats s1 = cache.stats();
 
                cache.get("b");            // 1 miss (total 1 hit, 1 miss)
                CacheStats s2 = cache.stats();

                // s1 is immutable -- unchanged despite later ops
                assertThat(s1.hitCount()).isEqualTo(1);
                assertThat(s1.missCount()).isZero();

                assertThat(s2.hitCount()).isEqualTo(1);
                assertThat(s2.missCount()).isEqualTo(1);
                assertThat(s1).isNotEqualTo(s2);
            } finally {
                cache.close();
            }
        }

        @Test
        void largeCapacityAndManyEntriesWork() {
            Cache<Integer, Integer> cache = CacheBuilder.<Integer, Integer>newBuilder()
                    .capacity(10_000)
                    .build();

            try {
                for (int i = 0; i < 5_000; i++) {
                    cache.put(i, i * 2);
                }
                assertThat(cache.size()).isEqualTo(5_000);
                assertThat(cache.get(1234)).isEqualTo(2468);
            } finally {
                cache.close();
            }
        }

        @Test
        void clearDoesNotFireListenerButResetsSize(){
            AtomicInteger callbacks = new AtomicInteger();
            Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                                          .evictionListener((k, v, r) -> callbacks.incrementAndGet())
                                          .build();
            
            try{
                cache.put("a", "1");
                cache.put("b", "2");
                cache.clear();

                assertThat(cache.size()).isZero();
                assertThat(callbacks.get()).isZero();
                assertThat(cache.stats().evictionCount()).isZero();
            }finally{
                cache.close();
            }
        }
    }

    // ------------------------------------------------------------------
    // FIFO stub -- proving custom policies flow end-to-end
    // ------------------------------------------------------------------
    private static final class FifoPolicy<K> 
                            implements EvictionPolicy<K> {
        private final Deque<K> queue = new ArrayDeque<>();

        @Override 
        public void onAccess(K key){ /* ignore */ }

        @Override 
        public void onInsert(K key){ 
            queue.addLast(key); 
        }

        @Override 
        public void onRemove(K key){
            queue.remove(key);
        }

        @Override 
        public K selectVictim(){
            return queue.peekFirst();
        }

    }
}