package com.swapnil.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Deeper concurrency tests. Slower than the smoke test in {@code LRUCacheTest};
 * intended to run in the default test suite but catch subtle races.
 */
class ConcurrencyStressTest {

    @Test 
    void mixedReadWriteRemoveUnderContention() throws InterruptedException {
        int threads = 16;
        int opsPerThread = 2_000;
        int capacity = 100;

        Cache<Integer, Integer> cache = CacheBuilder
                    .<Integer, Integer>newBuilder()
                    .capacity(capacity)
                    .build();
        
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = rnd.nextInt(capacity * 3);
                        int op = rnd.nextInt(10);
                        if (op < 6) {
                            cache.put(key, i);
                        } else if (op < 9) {
                            cache.get(key);
                        } else {
                            cache.remove(key);
                        }
                    }
                } catch (Throwable e) {
                    unexpected.compareAndSet(null, e);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        boolean done = pool.awaitTermination(60, TimeUnit.SECONDS);

        int finalSize = cache.size();
        CacheStats stats = cache.stats();
        cache.close();

        assertThat(done).as("all threads completed within 60s").isTrue();
        assertThat(unexpected.get()).as("no unexpected worker exceptions").isNull();
        assertThat(finalSize).isLessThanOrEqualTo(capacity);
        assertThat(stats.requestCount()).isPositive();
    }

    @Test 
    void capacityIsNeverExceededUnderConcurrentPuts() throws InterruptedException {
        int thread = 8;
        int capacity = 20;

        Cache<Integer, Integer> cache = CacheBuilder.<Integer, Integer>newBuilder()
                                        .capacity(capacity)
                                        .build();
        ExecutorService pool = Executors.newFixedThreadPool(thread);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        for(int t = 0;t < thread;t++){
            pool.submit(() -> {
                try {
                    start.await();
                    for(int i = 0;i < 5_000;i++){
                        cache.put(i, i);
                        if(cache.size() > capacity){
                            throw new AssertionError(
                                "Capacity exceeded: "+ cache.size()
                            );
                        }
                    }
                } catch (Exception e) {
                    unexpected.compareAndSet(null, e);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        boolean done = pool.awaitTermination(60, TimeUnit.SECONDS);
        int finalSize = cache.size();
        cache.close();

        assertThat(done).isTrue();
        assertThat(unexpected.get()).isNull();
        assertThat(finalSize).isLessThanOrEqualTo(capacity);
    }

    @Test 
    void closeDuringActiveUseYieldsOnlyIllegalStateException()
                        throws InterruptedException {
        Cache<Integer, Integer> cache = CacheBuilder.<Integer, Integer>newBuilder()
                                        .capacity(50)
                                        .build();
        
        ExecutorService pool = Executors.newFixedThreadPool(4);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        for(int t = 0; t < 4; t++){
            pool.submit(() -> {
                for(int i = 0;i < 2_000;i++){
                    try {
                        cache.put(i % 100, i);
                        cache.get(i % 100);
                    } catch (IllegalStateException exception) {
                        // cache was closed mid-op -- documented behavior
                        return;
                    } catch (Throwable other){
                        unexpected.compareAndSet(null, other);
                        return;
                    }
                }
            });
        }

        Thread.sleep(10);
        cache.close();

        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(unexpected.get())
                   .as("only IllegalStateException allowed after close")
                   .isNull();
    }

    @Test 
    void concurrentLoaderDoesNotCorruptCache() throws InterruptedException {
        Cache<String, String> cache = CacheBuilder
                    .<String, String>newBuilder()
                    .capacity(100)
                    .build();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        for(int t = 0; t < threads; t++){
            pool.submit(() -> {
                try{
                    start.await();
                    for(int i = 0;i < 500;i++){
                        String key = "k" + (i % 20);
                        cache.get(key, k -> "loaded-"+k);
                    }
                }catch(Throwable e){
                    unexpected.compareAndSet(null, e);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        boolean done = pool.awaitTermination(30, TimeUnit.SECONDS);

        CacheStats stats = cache.stats();
        int finalSize = cache.size();
        cache.close();

        assertThat(done).isTrue();
        assertThat(unexpected.get()).isNull();
        assertThat(finalSize).isLessThanOrEqualTo(100);
        // Loader was invoked at least once per distinct key; multiple misses
        // on the same key may race (documented non-single-flight behavior)
        assertThat(stats.loadSuccessCount()).isGreaterThanOrEqualTo(20);
    }
}