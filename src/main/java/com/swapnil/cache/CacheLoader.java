package com.swapnil.cache;

/**
 * Computes a value for a cache miss — the "cache-aside" pattern.
 *
 * Typical usage:
 * {@code
 * User user = cache.get(userId, key -> userRepository.findById(key));
 * }
 *
 * Implementations should be idempotent and thread-safe.
 * The same loader may be invoked concurrently for the same key if the cache
 * does not implement single-flight loading.
 *
 * @param <K> key type
 * @param <V> value type
 */
@FunctionalInterface 
public interface CacheLoader<K, V> {
    /**
     * Loads the value associated with the given key.
     *
     * @param key the key to load a value for; never {@code null}
     * @return the loaded value, or {@code null} if no value exists for the key
     * @throws Exception if loading fails; the cache will not store the result
     *                   and the exception will be wrapped in
     *                   {@link CacheLoadException} before propagating
     */
    V load(K key) throws Exception;
}

