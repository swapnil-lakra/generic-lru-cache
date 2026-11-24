/**
 * Pluggable eviction-policy extension point.
 *
 * <p>{@link com.swapnil.cache.policy.EvictionPolicy} lets callers replace
 * the default LRU behavior with a custom policy. The cache notifies the
 * policy on every insert, access, and removal, and asks it to nominate a
 * victim when capacity is exceeded.</p>
 *
 * <p>{@link com.swapnil.cache.policy.LRUEvictionPolicy} is the default
 * implementation and demonstrates the required shape: a doubly linked list
 * for O(1) recency updates plus a {@code HashMap} for O(1) key lookup.
 * Custom policies (FIFO, LFU, random) follow the same interface and must
 * honor the same contracts — see the interface Javadoc for lifecycle
 * expectations.</p>
 *
 * <p>Plug a policy in via
 * {@link com.swapnil.cache.CacheBuilder#evictionPolicy}:</p>
 * <pre>{@code
 * Cache<K, V> cache = CacheBuilder.<K, V>newBuilder()
 *         .evictionPolicy(new MyCustomPolicy<>())
 *         .build();
 * }</pre>
 */
package com.swapnil.cache.policy;