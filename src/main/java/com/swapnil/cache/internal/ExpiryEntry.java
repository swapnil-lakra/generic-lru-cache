package com.swapnil.cache.internal;

/**
 * Internal wrapper holding a cached value along with its expiration timestamp.
 *
 * <p>{@code expiryAtMillis == 0} means "no expiry" — the entry lives until
 * evicted or the cache is closed. A positive value means the entry is
 * considered expired at that wall-clock millisecond (inclusive).</p>
 *
 * <p>Not thread-safe. Access must be guarded by the owning cache's lock.</p>
 */
final class ExpiryEntry<V> {

    final V value;
    final long expiryAtMillis;

    ExpiryEntry(V value, long expiryAtMillis){
        this.value = value;
        this.expiryAtMillis = expiryAtMillis;
    }

    boolean isExpired(long nowMillis){
        return expiryAtMillis > 0 && nowMillis >= expiryAtMillis;
    }
}