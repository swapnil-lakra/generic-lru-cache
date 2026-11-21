package com.swapnil.cache.internal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.swapnil.cache.EvictionListener;
import com.swapnil.cache.policy.EvictionPolicy;

/**
 * Concrete {@link AbstractCache} backed by an externally supplied
 * {@link EvictionPolicy}.
 *
 * <p>Most callers use {@link com.swapnil.cache.CacheBuilder} rather than
 * constructing this class directly. The builder supplies a default
 * {@code LRUEvictionPolicy} when the user does not provide one.</p>
 *
 * <p>When constructed with {@code backgroundCleanup = true}, this cache
 * schedules a {@link CleanupTask} on a single daemon thread that
 * periodically calls {@link AbstractCache#sweepExpired()} to proactively
 * remove expired entries. Without background cleanup, expired entries are
 * only removed lazily on access.</p>
 *
 * <p><b>Lifecycle:</b> always call {@link #close()} (or use
 * try-with-resources) to release the cleanup thread.</p>
 */
public final class LRUCache<K, V> extends AbstractCache<K, V>{
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * Creates a cache without background cleanup. Expired entries are
     * removed lazily on access.
     */
    public LRUCache(int capacity,
        long defaultTtlMillis,
        EvictionPolicy<K> policy,
        EvictionListener<K,V> listener ){
            this(capacity, defaultTtlMillis, policy, listener, false, 0L);
    }

     /**
     * Creates a cache with optional background cleanup.
     *
     * @param backgroundCleanup   when {@code true}, expired entries are
     *                            proactively swept on a daemon thread
     * @param cleanupIntervalMillis sweep interval; ignored when
     *                            {@code backgroundCleanup} is {@code false},
     *                            must be {@code > 0} otherwise
     * @throws IllegalArgumentException if {@code backgroundCleanup} is true
     *                                  and {@code cleanupIntervalMillis <= 0}
     */
    public LRUCache(int capacity,
                    long defaultTtlMillis,
                    EvictionPolicy<K> policy,
                    EvictionListener<K, V> listener,
                    boolean backgroundCleanup,
                    long cleanupIntervalMillis){
        super(capacity, defaultTtlMillis, policy, listener);

        if(backgroundCleanup){
            if(cleanupIntervalMillis <= 0){
                throw new IllegalArgumentException(
                    "cleanupIntervalMillis must be > 0 when backgroundCleanup "
                    + "is enabled, got " + cleanupIntervalMillis );
            }

            this.cleanupExecutor = Executors
                                   .newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "generic-cache-cleanup");
                t.setDaemon(true);
                return t;
            });

            this.cleanupExecutor.scheduleAtFixedRate(
                    new CleanUpTask(this::sweepExpired), 
                    cleanupIntervalMillis, cleanupIntervalMillis, 
                    TimeUnit.MILLISECONDS);
        } else {
            this.cleanupExecutor = null;
        }
    }

    @Override 
    public void close(){
        super.close(); // sets closed=true, clears store, releases lock
        if(cleanupExecutor != null){
            cleanupExecutor.shutdown();
        }
    }

}