package com.swapnil.cache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.swapnil.cache.policy.EvictionPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheBuilderTest{
    // ------------------------------------------------------------------
    // FIFO stub — used to prove custom policies are honored
    // ------------------------------------------------------------------

    private static final class FifoPolicy<K> implements 
                                               EvictionPolicy<K>{
        private final Deque<K> queue = new ArrayDeque<>();

        @Override 
        public void onAccess(K key){}

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

    // ==================================================================
    // Defaults
    // ==================================================================

    @Nested 
    class Defaults {

        @Test 
        void buildSucceedsWithNoConfiguration() {
            Cache<String, String> cache = CacheBuilder
                                        .<String,String>newBuilder().build();
            
            cache.put("a", "1");
            assertThat(cache.get("a")).isEqualTo("1");
            cache.close();
        }

        @Test 
        void defaultCapacityIs100(){
            Cache<Integer, Integer> cache = CacheBuilder
                           .<Integer, Integer>newBuilder().build();
            
            for(int i = 0;i < 150;i++){
                cache.put(i, i);
            }
            // After 150 inserts with capacity 100, size must be
            assertThat(cache.size()).isEqualTo(CacheBuilder.DEFAULT_CAPACITY); 
            cache.close();
        }

        @Test 
        void defaultTtlMeansNoExpiry() throws InterruptedException{
            Cache<String, String> cache = CacheBuilder
                          .<String, String>newBuilder().build();
            
            cache.put("a", "1");
            Thread.sleep(50);
            assertThat(cache.get("a")).isEqualTo("1");
            cache.close();
        }

        @Test 
        void defaultPolicyIsLru(){
            Cache<String, String> cache = CacheBuilder
                          .<String, String>newBuilder()
                          .capacity(2)
                          .build();
            
            cache.put("a", "1");
            cache.put("b", "2");
            cache.get("a");             // promote "a" (LRU semantics)
            cache.put("c", "3");  // should evict "b"

            assertThat(cache.get("a")).isEqualTo("1");
            assertThat(cache.get("b")).isNull();
            assertThat(cache.get("c")).isEqualTo("3");
            cache.close();
        }

        @Test 
        void defaultBackgroundCleanupIsOff() throws InterruptedException{
            Cache<String, String> cache = CacheBuilder
                          .<String, String>newBuilder()
                          .defaultTtlMillis(30)
                          .build();
            
            cache.put("a", "1");
            Thread.sleep(120);
            // Without background cleanup, lazy expiry still happens on get
            assertThat(cache.get("a")).isNull();
            cache.close();
        }

        @Test 
        void constantsMatchDocumentation(){
            assertThat(CacheBuilder.DEFAULT_CAPACITY).isEqualTo(100);
            assertThat(CacheBuilder.DEFAULT_TTL_MILLIS).isZero();
            assertThat(CacheBuilder.DEFAULT_CLEANUP_INTERVAL_MILLIS).isEqualTo(60_000L);
        }
    }

    // ==================================================================
    // Custom configuration flows through
    // ==================================================================

    @Nested 
    class CustomConfigured{
        
        @Test
        void customCapacityHonored(){
            Cache<String, String> cache = CacheBuilder
                          .<String, String>newBuilder().capacity(2).build();
                        
            cache.put("a", "1");
            cache.put("b", "2");
            cache.put("c", "3");

            assertThat(cache.size()).isEqualTo(2);
            cache.close();
        }

        @Test 
        void customTtlHonored() throws InterruptedException{
            Cache<String, String> cache = CacheBuilder
                          .<String, String>newBuilder()
                          .defaultTtlMillis(50)
                          .build();
            cache.put("a", "1");
            Thread.sleep(120);
            assertThat(cache.get("a")).isNull();
            cache.close(); 
        }

        @Test 
        void customEvictionPolicyHonored(){
            // FIFO -- ignores access; if LRU was silently applied, "b" would be evicted
            Cache<String, String> cache = CacheBuilder
                          .<String, String>newBuilder().capacity(2)
                          .evictionPolicy(new FifoPolicy<>())
                          .build();
            cache.put("a", "1");
            cache.put("b", "2");
            cache.get("a");              // LRU would promote "a"; FIFO ignores
            cache.put("c", "3");   // FIFO evicts "a" (oldest insert)

            assertThat(cache.get("a")).isNull();
            assertThat(cache.get("b")).isEqualTo("2");
            assertThat(cache.get("c")).isEqualTo("3");
            cache.close();
        }

        @Test
        void evictionListenerReceivesCallbacks(){
            List<EvictionReason> reasons = new ArrayList<>();
            Cache<String, String> cache = CacheBuilder
                          .<String, String>newBuilder()
                          .capacity(2)
                          .evictionListener((k, v, r) -> reasons.add(r))
                          .build();
            cache.put("a", "1");
            cache.put("b", "2");
            cache.put("c", "3"); // SIZE_EXCEEDED
            cache.remove("b");          // EXPLICIT

            assertThat(reasons)
                  .containsExactlyInAnyOrder(
                    EvictionReason.SIZE_EXCEEDED,
                    EvictionReason.EXPLICIT
                  );
            cache.close();
        }

        @Test 
        void backgroundCleanupSweepsExpiredEntries() throws 
                                           InterruptedException {
            Cache<String, String> cache = CacheBuilder
                          .<String, String>newBuilder()
                          .defaultTtlMillis(30)
                          .backgroundCleanup(true)
                          .cleanupIntervalMillis(20)
                          .build();
            try {
                cache.put("a", "1");
                cache.put("b", "2");
                // Wait for a few sweeps
                long deadline = System.currentTimeMillis() + 1000;
                while(cache.size() > 0 && System.currentTimeMillis() < deadline){
                    Thread.sleep(10);
                }
                System.out.println("Cache size is: "+cache.size());
                assertThat(cache.size()).isZero();
            } finally {
                cache.close();
            }
        }
    }

    // ==================================================================
    // Validation — fail fast at setter level
    // ==================================================================

    @Nested 
    class Validation {
        
        @Test
        void capacityRejectsZeroAndNegative(){
            assertThatThrownBy(() -> 
        CacheBuilder.<String, String>newBuilder().capacity(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("capacity");

           assertThatThrownBy(() -> 
                    CacheBuilder.<String, String>newBuilder().capacity(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void defaultTtlRejectsNegative(){
            assertThatThrownBy(() -> 
                    CacheBuilder.<String, String>newBuilder().defaultTtlMillis(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("defaultTtlMillis");
        }

        @Test 
        void defaultTtlZeroAllowed() {
            // 0 means "no expiry" -- explicitly valid
            CacheBuilder<String, String> b = CacheBuilder
                                .<String, String>newBuilder().defaultTtlMillis(0);
            assertThat(b).isNotNull();
        }

        @Test 
        void cleanupIntervalRejectsZeroAndNegative(){
            assertThatThrownBy(() -> 
                  CacheBuilder.<String, String>newBuilder().cleanupIntervalMillis(0))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("cleanupIntervalMillis");

            assertThatThrownBy(() -> 
                    CacheBuilder.<String, String>newBuilder().cleanupIntervalMillis(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test 
        void evictionPolicyRejectsNull(){
            assertThatThrownBy(() -> 
                  CacheBuilder.<String, String>newBuilder().evictionPolicy(null))
                  .isInstanceOf(NullPointerException.class)
                  .hasMessageContaining("evictionPolicy");
        }

        @Test
        void evictionListenerRejectsNull(){
            assertThatThrownBy(() -> 
                  CacheBuilder.<String, String>newBuilder().evictionListener(null))
                  .isInstanceOf(NullPointerException.class)
                  .hasMessageContaining("evictionListener");
        }
    }

    // ==================================================================
    // Fluent chaining & builder reuse
    // ==================================================================

    @Nested
    class Fluent {

        @Test
        void settersReturnSameInstance(){
            CacheBuilder<String, String> b = CacheBuilder.newBuilder();
            assertThat(b.capacity(10)).isSameAs(b);
            assertThat(b.defaultTtlMillis(1000)).isSameAs(b);
            assertThat(b.backgroundCleanup(true)).isSameAs(b);
            assertThat(b.cleanupIntervalMillis(500)).isSameAs(b);
            assertThat(b.evictionListener((k, v, r) -> { /* noop */ })).isSameAs(b);
        }

        @Test
        void buildReturnsFreshCacheEachCall(){
            CacheBuilder<String, String> b = CacheBuilder.<String, String>newBuilder()
                                             .capacity(5);
            Cache<String, String> c1 = b.build();
            Cache<String, String> c2 = b.build();

            assertThat(c1).isNotSameAs(c2);
            c1.put("a", "1");
            assertThat(c2.get("a")).isNull();
            c1.close();
            c2.close();
        }

        @Test 
        void fullChainCompilesAndWorks(){
            Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                                          .capacity(500)
                                          .defaultTtlMillis(60_000)
                                          .backgroundCleanup(true)
                                          .cleanupIntervalMillis(30_000)
                                          .evictionListener((k, v, r) -> {})
                                          .evictionPolicy(new FifoPolicy<>())
                                          .build();
            
            assertThat(cache).isNotNull();
            cache.close();
        }
    }

    // ==================================================================
    // Type inference
    // ==================================================================

    @Nested 
    class TypeInference {

        @Test
        void explicitTypesFlowThrough() {
            Cache<String, Integer> cache = 
                 CacheBuilder.<String, Integer>newBuilder().build();
            cache.put("one", 1);
            assertThat(cache.get("one")).isEqualTo(1);
            cache.close();
        }

        @Test 
        void assignmentContextInfersTypes() {
            CacheBuilder<Long, Double> b = CacheBuilder.newBuilder();
            Cache<Long, Double> cache = b.build();
            cache.put(1L, 1.5);
            assertThat(cache.get(1L)).isEqualTo(1.5);
            cache.close();
        }
    }

    // ------------------------------------------------------------------
    // Helper POJO used by fullChainCompilesAndWorks
    // ------------------------------------------------------------------
    private static final class User {
        final String name;
        User(String name){
            this.name = name;
        }
    }

}