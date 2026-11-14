package com.swapnil.cache;

/**
 * A type-safe, in-memory cache that maps keys of type {@code K} to values
 * of type {@code V}.
 *
 * Implementations are expected to be thread-safe. The cache automatically
 * evicts entries when its configured capacity is exceeded, based on the
 * configured {@link com.swapnil.cache.policy.EvictionPolicy}.
 *
 * Entry removal triggers the configured
 * {@link EvictionListener} with the appropriate {@link EvictionReason}.
 *
 * {@code Cache} extends {@link AutoCloseable}, so it can be used in a
 * try-with-resources block. Closing a cache releases any background
 * resources (e.g. TTL cleanup threads). After {@code close()}, all other
 * operations throw {@link IllegalStateException.
 *
 * {@code
 * try (Cache<String, User> cache = CacheBuilder.<String, User>newBuilder()
 *         .capacity(500)
 *         .build()) {
 *
 *     cache.put("user:42", user);
 *     User u = cache.get("user:99", key -> userRepo.findById(99));
 * }
 * }
 *
 * Null handling: implementations must reject {@code null} keys and
 * values with a {@link NullPointerException}, matching the semantics of
 * {@link java.util.concurrent.ConcurrentHashMap}.
 *
 * Thread-safety: all methods are safe for concurrent use by
 * multiple threads unless a specific implementation documents otherwise.</p>
 *
 * @param <K> key type
 * @param <V> value type
 * @see CacheBuilder
 * @see CacheLoader
 * @see EvictionListener
 * @see CacheStats
 */
public interface Cache<K, V> extends AutoCloseable {
    
    /**
     * Returns the value associated with {@code key}, or {@code null} if the
     * key is not present or its entry has expired.
     *
     * A cache hit updates the entry's "recently used" status if the
     * configured eviction policy is access-order based (e.g. LRU).
     *
     * @param key the key to look up; must not be {@code null}
     * @return the cached value, or {@code null}
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalStateException if the cache has been closed
     */
    V get(K key);

    /**
     * Returns the value associated with {@code key}, computing it with
     * {@code loader} if the key is absent or its entry has expired — the
     * standard cache-aside pattern.
     *
     * On a miss, the loader is invoked and its result is stored in the
     * cache using the cache's default TTL before being returned. If the
     * loader returns {@code null} or throws, nothing is cached.</p>
     *
     * Note: implementations are not required to provide
     * single-flight semantics. Two concurrent misses on the same key may
     * result in the loader being invoked twice.
     *
     * @param key    the key to look up; must not be {@code null}
     * @param loader computes the value on miss; must not be {@code null}
     * @return the cached or newly loaded value, or {@code null} if the
     *         loader returned {@code null}
     * @throws NullPointerException if {@code key} or {@code loader} is {@code null}
     * @throws CacheLoadException if the loader threw; the original exception
     *         is available via {@link CacheLoadException#getCause()}
     * @throws IllegalStateException if the cache has been closed
     */
    V get(K key, CacheLoader<K, V> loader);

    /**
     * Stores {@code value} under {@code key}, using the cache's default TTL.
     *
     * If the key is already present with a non-expired value, the
     * existing entry is replaced and the {@link EvictionListener} is fired
     * with {@link EvictionReason#REPLACED}. If the write causes the cache
     * to exceed its capacity, an eviction is triggered via
     * {@link com.swapnil.cache.policy.EvictionPolicy#selectVictim()}.
     *
     * @param key   the key; must not be {@code null}
     * @param value the value; must not be {@code null}
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     * @throws IllegalStateException if the cache has been closed
     */
    void put(K key, V value);

    /**
     * Stores {@code value} under {@code key} with a custom time-to-live.
     *
     * Behaviour matches {@link #put(Object, Object)} except that the
     * entry expires after {@code ttlMillis} milliseconds from the moment
     * of the call, regardless of the cache's default TTL.
     *
     * @param key       the key; must not be {@code null}
     * @param value     the value; must not be {@code null}
     * @param ttlMillis time-to-live in milliseconds; must be {@code > 0}.
     *                  Use {@link #put(Object, Object)} for the default TTL,
     *                  or configure {@code defaultTtlMillis(0)} on the builder
     *                  to disable expiry.
     * @throws IllegalArgumentException if {@code ttlMillis <= 0}
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     * @throws IllegalStateException if the cache has been closed
     */
    void put(K key, V value, long ttlMillis);

     /**
     * Removes the entry for {@code key}, if present. The
     * {@link EvictionListener} is fired with {@link EvictionReason#EXPLICIT}.
     *
     * @param key the key to remove; must not be {@code null}
     * @return {@code true} if an entry was removed, {@code false} if the key
     *         was absent or had expired
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalStateException if the cache has been closed
     */
    boolean remove(K key);

    /**
     * Returns {@code true} if a non-expired entry exists for {@code key}.
     *
     * This is not a cache hit — it does not update the eviction
     * policy's access order. Use {@link #get(Object)} if you want the
     * access to count as a hit.</p>
     *
     * @param key the key to check; must not be {@code null}
     * @return {@code true} if a non-expired value is present
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalStateException if the cache has been closed
     */
    boolean containsKey(K key);

    /**
     * Returns the current number of entries in the cache, including
     * entries that have expired but not yet been swept away.
     *
     * @return number of entries currently stored
     * @throws IllegalStateException if the cache has been closed
     */
    int size();

    /**
     * Returns {@code true} if the cache contains no entries.
     *
     * @return {@code true} if the cache is empty
     * @throws IllegalStateException if the cache has been closed
     */
    boolean isEmpty();

    /**
     * Removes all entries from the cache.
     *
     * Whether the {@link EvictionListener} fires for each removed entry
     * is implementation-defined. The default {@code LRUCache} implementation
     * does not fire the listener for {@code clear()} — bulk removals
     * are not individual evictions.</p>
     *
     * @throws IllegalStateException if the cache has been closed
     */
    void clear();

     /**
     * Returns an immutable snapshot of the cache's performance counters.
     *
     * @return a {@link CacheStats} snapshot
     * @throws IllegalStateException if the cache has been closed
     */
    CacheStats stats();

    /**
     * Releases background resources held by the cache (e.g. the TTL
     * cleanup thread). Subsequent calls to any other method throw
     * {@link IllegalStateException}.
     *
     * Calling {@code close()} on an already-closed cache is a no-op.
     *
     * This method is invoked automatically at the end of a
     * try-with-resources block.
     */
    @Override 
    void close();

}
