package com.swapnil.cache;

import java.util.Objects;

import com.swapnil.cache.internal.LRUCache;
import com.swapnil.cache.policy.EvictionPolicy;
import com.swapnil.cache.policy.LRUEvictionPolicy;

/**
 * Fluent builder for {@link Cache} instances.
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 * Cache<String, User> cache = CacheBuilder.<String, User>newBuilder()
 *         .capacity(500)
 *         .defaultTtlMillis(Duration.ofMinutes(10).toMillis())
 *         .evictionListener((k, v, r) -> log.warn("Evicted {} due to {}", k, r))
 *         .backgroundCleanup(true)
 *         .build();
 * }</pre>
 *
 * <p><b>Defaults:</b></p>
 * <ul>
 *   <li>{@code capacity} = {@value #DEFAULT_CAPACITY}</li>
 *   <li>{@code defaultTtlMillis} = {@value #DEFAULT_TTL_MILLIS} (no expiry)</li>
 *   <li>{@code evictionPolicy} = {@link LRUEvictionPolicy} (created lazily in {@link #build()})</li>
 *   <li>{@code evictionListener} = none</li>
 *   <li>{@code backgroundCleanup} = {@code false}</li>
 *   <li>{@code cleanupIntervalMillis} = {@value #DEFAULT_CLEANUP_INTERVAL_MILLIS} (only used when background cleanup is enabled)</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> this builder is <b>not</b> thread-safe. Configure
 * it from a single thread. The caches it produces are thread-safe.</p>
 *
 * <p><b>Type inference:</b> {@code CacheBuilder.newBuilder()} returns
 * {@code CacheBuilder<Object, Object>} unless the target type is inferable.
 * For type-specific caches use the explicit form:
 * {@code CacheBuilder.<String, User>newBuilder()}.</p>
 *
 * @param <K> key type
 * @param <V> value type
 * @see Cache
 * @see LRUCache
 */
public final class CacheBuilder<K, V> {

    /** Default capacity when not specified. */
    public static final int DEFAULT_CAPACITY = 100;

    /** Default TTL in milliseconds. {@code 0} means "no expiry". */
    public static final long DEFAULT_TTL_MILLIS = 0L;

    /** Default cleanup interval in milliseconds — only applies when background cleanup is enabled. */
    public static final long DEFAULT_CLEANUP_INTERVAL_MILLIS = 60_000L;

    private int capacity = DEFAULT_CAPACITY;
    private long defaultTtlMillis = DEFAULT_TTL_MILLIS;
    private EvictionPolicy<K> evictionPolicy;        // null = use default
    private EvictionListener<K, V> evictionListener; // null = no listener
    private boolean backgroundCleanup = false;
    private long cleanupIntervalMillis = DEFAULT_CLEANUP_INTERVAL_MILLIS;

    private CacheBuilder(){}

    /**
     * Returns a fresh builder for {@code Cache<K, V>}.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a new builder with default settings
     */
    public static <K, V> CacheBuilder<K, V> newBuilder(){
        return new CacheBuilder<>();
    }

     /**
     * Sets the maximum number of entries the cache will hold before eviction
     * kicks in.
     *
     * @param capacity maximum entries; must be {@code > 0}
     * @return this builder
     * @throws IllegalArgumentException if {@code capacity <= 0}
     */
    public CacheBuilder<K, V> capacity(int capacity){
        if(capacity <= 0){
            throw new IllegalArgumentException(
                "capacity must be > 0, got" + capacity
            );
        }
        this.capacity = capacity;
        return this;
    }

    /**
     * Sets the default time-to-live for every entry that does not specify
     * a per-entry TTL via {@link Cache#put(Object, Object, long)}.
     *
     * @param defaultTtlMillis TTL in milliseconds; must be {@code >= 0}.
     *                         {@code 0} disables expiry.
     * @return this builder
     * @throws IllegalArgumentException if {@code defaultTtlMillis < 0}
     */
    public CacheBuilder<K, V> defaultTtlMillis(long defaultTtlMillis){
        if (defaultTtlMillis < 0) {
            throw new IllegalArgumentException(
                "defaultTtlMillis must be >= 0, got "+defaultTtlMillis
            );
        }
        this.defaultTtlMillis = defaultTtlMillis;
        return this;
    }

    /**
     * Sets the eviction policy. When not called, an {@link LRUEvictionPolicy}
     * is created at {@link #build()} time.
     *
     * @param evictionPolicy the policy to use; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code evictionPolicy} is {@code null}
     */
    public CacheBuilder<K, V> evictionPolicy(
                             EvictionPolicy<K> evictionPolicy){
        this.evictionPolicy = Objects
                            .requireNonNull(evictionPolicy,"evictionPolicy");
        return this;
    }

    /**
     * Sets a listener that will be notified on every entry removal.
     *
     * <p>The listener is invoked while holding the cache's internal lock.
     * Keep it fast and non-blocking; avoid I/O or acquiring other locks.</p>
     *
     * @param evictionListener the listener; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code evictionListener} is {@code null}
     */
    public CacheBuilder<K, V> evictionListener(
        EvictionListener<K, V> evictionListener){
        this.evictionListener = Objects.requireNonNull(
                            evictionListener,"evictionListener");
        return this;
    }

    /**
     * Enables or disables periodic background cleanup of expired entries.
     *
     * <p>When enabled, a daemon scheduled executor periodically sweeps
     * entries whose TTL has elapsed. When disabled, expired entries are
     * only removed lazily on access.</p>
     *
     * @param backgroundCleanup whether to run a periodic sweeper
     * @return this builder
     */
    public CacheBuilder<K, V> backgroundCleanup(
                                         boolean backgroundCleanup){
        this.backgroundCleanup = backgroundCleanup;
        return this;                                    
    }

    /**
     * Sets the interval between background cleanup sweeps.
     *
     * <p>Ignored when background cleanup is disabled — but still validated
     * here so that a misconfigured value never silently persists.</p>
     *
     * @param cleanupIntervalMillis sweep interval in milliseconds; must be {@code > 0}
     * @return this builder
     * @throws IllegalArgumentException if {@code cleanupIntervalMillis <= 0}
     */
    public CacheBuilder<K, V> cleanupIntervalMillis(long cleanupIntervalMillis){
        if(cleanupIntervalMillis <= 0){
            throw new IllegalArgumentException(
                "cleanupIntervalMillis must be > 0, got "+ 
                cleanupIntervalMillis
            );
        }
        this.cleanupIntervalMillis = cleanupIntervalMillis;
        return this;
    }

    /**
     * Builds a new {@link Cache} with the configured settings.
     *
     * <p>Each call returns a fresh cache instance. The builder may be reused
     * to produce multiple caches with the same configuration.</p>
     *
     * @return a freshly constructed cache
     */
    public Cache<K, V> build(){
        EvictionPolicy<K> policy = (evictionPolicy != null)
                                   ? evictionPolicy
                                   : new LRUEvictionPolicy<>();
        return new LRUCache<>(capacity, defaultTtlMillis, 
                            policy, evictionListener,
                        backgroundCleanup, cleanupIntervalMillis);
    }

}