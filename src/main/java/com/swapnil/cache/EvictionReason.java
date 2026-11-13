package com.swapnil.cache;

/**
 * Reasons why an entry was removed from the cache.
 *
 * Every eviction fires the configured {@link EvictionListener}
 * with one of these reasons.
 */
public enum EvictionReason{
    
    /**
     * The cache exceeded its configured capacity and had to evict an entry
     * according to the {@link com.swapnil.cache.policy.EvictionPolicy}.
     */
    SIZE_EXCEEDED,

    /**
     * The entry's time-to-live elapsed. May be triggered lazily on access
     * or proactively by the background cleanup task.
     */
    EXPIRED,

    /**
     * The entry was explicitly removed via {@code Cache#remove(Object)}
     * or {@code Cache#clear()}.
     */
    EXPLICIT,

    /**
     * The entry was replaced by a subsequent {@code Cache#put(Object, Object)}
     * call using the same key.
     */
    REPLACED
}