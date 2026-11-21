package com.swapnil.cache.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.swapnil.cache.CacheStats;
import com.swapnil.cache.EvictionReason;
import com.swapnil.cache.policy.LRUEvictionPolicy;

class backgroundCleanupTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static final class Record {
        final Object key;
        final EvictionReason reason;

        Record(Object key, EvictionReason reason){
            this.key = key;
            this.reason = reason;
        }
    }

    private static void awaitCondition(BooleanSupplier cond, 
                        long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while(System.currentTimeMillis() < deadline){
            if(cond.getAsBoolean()){
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Condition not met withing "+timeoutMillis+ " ms");
    }


    // ==================================================================
    // CleanupTask unit tests
    // ==================================================================

    @Nested 
    class CleanupTaskUnit {

        @Test 
        void runInvokesSweetAction(){
            AtomicInteger calls = new AtomicInteger();
            CleanUpTask task = new CleanUpTask(calls::incrementAndGet);

            task.run();
            task.run();

            assertThat(calls.get()).isEqualTo(2);
        }

        @Test 
        void runSwallowsRuntimeExceptions(){
            CleanUpTask task = new CleanUpTask(() -> {
                throw new RuntimeException("boom");
            });

            task.run(); // must not propagate
            task.run(); // still functional after failure
        }

        @Test
        void runSwallowErrors(){
            CleanUpTask task = new CleanUpTask(() -> {
                throw new AssertionError("simulated OOM-ish");
            });

            task.run(); // must not propagate
        }

        @Test
        void constructorRejectsNull(){
            assertThatThrownBy(() -> new CleanUpTask(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================================================================
    // sweepExpired() direct
    // ==================================================================

    @Nested 
    class DirectSweep {

        @Test
        void sweepOnEmptyCacheReturnsZero(){
            LRUCache<String, String> cache = new LRUCache<>(
                10, 0, 
                new LRUEvictionPolicy<>(), null);

            assertThat(cache.sweepExpired()).isZero();
        }

        @Test 
        void sweepReturnsZeroWhenNoEntriesExpried(){
            LRUCache<String, String> cache = new LRUCache<>(
                10, 0, 
                new LRUEvictionPolicy<>(), null);

            cache.put("a", "1");
            cache.put("b", "2");

            assertThat(cache.sweepExpired()).isZero();
            assertThat(cache.size()).isEqualTo(2);
        }

        @Test 
        void sweepRemovesExpiredEntriesAndReturnsCount() throws 
                                                   InterruptedException {
            LRUCache<String, String> cache = new LRUCache<>(
                10, 0, 
                new LRUEvictionPolicy<>(), null);
            cache.put("a", "1", 50);
            cache.put("b", "2", 50);
            cache.put("c", "3");

            Thread.sleep(120);
            int removed = cache.sweepExpired();

            assertThat(removed).isEqualTo(2);
            assertThat(cache.size()).isEqualTo(1);
            assertThat(cache.get("c")).isEqualTo("3");
        }

        @Test
        void sweepFiresListenerWithExpiredReason() throws 
                                                   InterruptedException {
            List<Record> records = new ArrayList<>();
            LRUCache<String, String> cache = new LRUCache<>(
                10, 0, 
                new LRUEvictionPolicy<>(), 
                (k, v, r) -> records.add(new Record(k, r)));
            cache.put("a", "1", 50);
            Thread.sleep(120);

            cache.sweepExpired();

            assertThat(records).hasSize(1);
            assertThat(records.get(0).key).isEqualTo("a");
            assertThat(records.get(0).reason)
                                    .isEqualTo(EvictionReason.EXPIRED);
        }

        @Test 
        void sweepIncrementsEvictionCount() throws InterruptedException {
            LRUCache<String, String> cache = new LRUCache<>(
                10, 0, 
                new LRUEvictionPolicy<>(), null);
            cache.put("a", "1", 50);
            Thread.sleep(120);

            cache.sweepExpired();

            CacheStats s = cache.stats();
            assertThat(s.evictionCount()).isEqualTo(1);
        }

        @Test
        void sweepAfterCloseReturnsZero(){
            LRUCache<String, String> cache = new LRUCache<>(
                10, 0, new LRUEvictionPolicy<>(), 
                null);
            cache.put("a", "1", 50);
            cache.close();

            assertThat(cache.sweepExpired()).isZero();
        }
    }

    // ==================================================================
    // Background executor wiring
    // ==================================================================

    @Nested 
    class AutomaticCleanup{

        @Test
        void expiredEntriesSweptAutomatically() 
                                        throws InterruptedException {
            LRUCache<String, String> cache = new LRUCache<>(
                10, 50, new LRUEvictionPolicy<>(),
                 null, true, 
                 30);
            try {
                cache.put("a", "1");
                cache.put("b", "2");
                assertThat(cache.size()).isEqualTo(2);

                // TTL 50ms + sweep interval 30ms → 300ms is comfortable
                awaitCondition(() -> cache.size() == 0, 1000);
            } finally {
                cache.close();
            }
        }

        @Test 
        void nonExpiredEntriesSurvive() throws InterruptedException {
            LRUCache<String, String> cache = new LRUCache<>(
                10, 10_000, 
                new LRUEvictionPolicy<>(), null,
                true, 30);
            
            try {
                cache.put("a", "1");
                Thread.sleep(150);
                assertThat(cache.get("a")).isEqualTo("1");
            } finally {
                cache.close();
            }
        }

        @Test 
        void zeroTtlEntriesNeverSwept() throws InterruptedException {
            LRUCache<String, String> cache = new LRUCache<>(
                10, 0,
                 new LRUEvictionPolicy<>(), null,
                true, 30);
            
            try {
                cache.put("a", "1");
                Thread.sleep(200);
                assertThat(cache.get("a")).isEqualTo("1");
            } finally {
                cache.close();
            }
        }

        @Test 
        void automaticSweepFiresListener() throws InterruptedException {
            List<Record> records = new ArrayList<>();
            LRUCache<String, String> cache = new LRUCache<>(
                10, 05,
                 new LRUEvictionPolicy<>(),
                  (k, v, r) -> records.add(new Record(k, r)),
                true, 30);
            
            try {
                cache.put("a", "1");
                awaitCondition(() -> !records.isEmpty(),
                                     1000);
                assertThat(records.get(0).reason)
                          .isEqualTo(EvictionReason.EXPIRED);
            } finally {
                cache.close();
            }
        }
    }

    // ==================================================================
    // Construction & lifecycle
    // ==================================================================

    @Nested 
    class Lifecycle {

        @Test 
        void nonPositiveCleanupIntervalRejectedWhenEnabled(){
            assertThatThrownBy(() -> new LRUCache<>(
                10, 0, 
                new LRUEvictionPolicy<>(), null,
            true, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cleanupIntervalMillis");

            assertThatThrownBy(() -> new LRUCache<>(
                10, 0,
                 new LRUEvictionPolicy<>(), null,
                true, -1))
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        void cleanupIntervalIgnoredWhenDisabled(){
            // Must not throw — interval 0 is valid when cleanup is off
            LRUCache<String, String> cache = new LRUCache<>(
                10, 0,
                 new LRUEvictionPolicy<>(), null,
                false, 0);
            cache.close();  
        } 
        
        @Test
        void closeShutsDownExecutorGracefully() throws InterruptedException {
            LRUCache<String, String> cache = new LRUCache<>(
                10, 50,
                 new LRUEvictionPolicy<>(), null,
                true, 30);
            cache.put("a","1");
            cache.close();

            // No assertions on executor directly — close() must not hang
            // or throw. Give it a moment for shutdownNow to settle.
            Thread.sleep(50);
        }

        @Test 
        void closeIsIdempotentWithBackgroundCleanup() {
            LRUCache<String, String> cache = new LRUCache<>(
                10, 0,
                 new LRUEvictionPolicy<>(), null,
                true, 30);
            cache.close();
            cache.close(); // must not throw
        }

        @Test 
        void concurrentCloseWhileSweepingIsSafe() throws InterruptedException {
            LRUCache<String, String> cache = new LRUCache<>(
                10, 10, 
                new LRUEvictionPolicy<>(), null,
            true, 5);

            for(int i = 0; i < 50; i++){
                cache.put("k"+i, "v"+i);
            }

            // Let a few sweeps fire, then close while tasks may still be running
            Thread.sleep(30);
            cache.close();
            Thread.sleep(30); // no exceptions should escape to console
        }
    }
}