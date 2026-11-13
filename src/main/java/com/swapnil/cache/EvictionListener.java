package com.swapnil.cache;

/**
 * Callback invoked whenever an entry is removed from the cache.
 *
 * Thread-safety: the listener is invoked while holding the cache's
 * internal lock in most implementations. Implementations must be fast and
 * non-blocking — avoid performing I/O or acquiring other locks here.
 *
 * @param <K> key type
 * @param <V> value type
 */

@FunctionalInterface 
public interface EvictionListener<K, V> {
    /**
     * Called after an entry has been removed from the cache.
     *
     * @param key    the evicted key
     * @param value  the evicted value
     * @param reason why the entry was evicted
     */

    void onEviction(K key, V value, EvictionReason reason);
}