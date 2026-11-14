package com.swapnil.cache.policy;

/**
 * Decides which entry to evict when a cache exceeds its capacity.
 *
 * A policy is notified of every cache mutation via {@link #onAccess(Object)},
 * {@link #onInsert(Object)}, and {@link #onRemove(Object)}, and is asked to
 * nominate a victim via {@link #selectVictim()} when the cache needs space.
 *
 * Lifecycle contract:
 * 
 *   {@code onInsert(key)} is called before the cache stores the entry
 *   {@code onAccess(key)} is called on every cache hit
 *   {@code onRemove(key)} is called after the cache removes the entry,
 *       for every removal reason (eviction, expiry, explicit, replacement)
 *   {@code selectVictim()} is called when the cache is over capacity;
 *       it must peek only and not mutate state — the cache will call
 *       {@code onRemove(victim)} afterwards
 * 
 *
 * Thread-safety: the policy is accessed under the cache's internal
 * lock, so implementations do not need to synchronize internally.
 *
 * @param <K> key type
 * @see LRUEvictionPolicy
 */

public interface EvictionPolicy<K> {
    /**
     * Notifies the policy that {@code key} was accessed via a cache hit.
     *
     * @param key the accessed key; never {@code null}
     */
    void onAccess(K key);

    /**
     * Notifies the policy that {@code key} is about to be inserted.
     *
     * Called before the cache stores the entry, so an implementation may
     * assume the key is not already tracked unless the cache is performing
     * a replacement (in which case {@code onRemove} precedes {@code onInsert}).
     *
     * @param key the key being inserted; never {@code null}
     */
    void onInsert(K key);

    /**
     * Notifies the policy that {@code key} has been removed from the cache.
     *
     * @param key the removed key; never {@code null}
     */
    void onRemove(K key);

    /**
     * Selects the next key to evict.
     *
     * <p><b>Important:</b> this method must <b>peek</b> — it must not mutate
     * the policy's internal state. The cache will call {@link #onRemove(Object)}
     * on the returned key, giving the policy a chance to clean up.</p>
     *
     * @return the key to evict, or {@code null} if the policy has no entries
     *         to evict (which should only happen when the cache is empty)
     */
    K selectVictim();
}