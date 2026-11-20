package com.swapnil.cache.internal;

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
 * <p>This class deliberately contains no logic today — all behavior lives
 * in {@link AbstractCache}. It exists as a concrete, stable type for the
 * builder to instantiate, and to serve as the base for future
 * policy-specific variants.</p>
 */
public final class LRUCache<K, V> extends AbstractCache<K, V>{
    public LRUCache(int capacity,
        long defaultTtlMillis,
        EvictionPolicy<K> policy,
        EvictionListener<K,V> listener ){
            super(capacity, defaultTtlMillis, policy, listener);
    }
}