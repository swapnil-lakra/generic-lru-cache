package com.swapnil.cache.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.swapnil.cache.Cache;
import com.swapnil.cache.CacheLoadException;
import com.swapnil.cache.CacheLoader;
import com.swapnil.cache.CacheStats;
import com.swapnil.cache.EvictionListener;
import com.swapnil.cache.EvictionReason;
import com.swapnil.cache.policy.EvictionPolicy;

/**
 * Abstract template for cache implementations that combine an
 * {@link EvictionPolicy} with a capacity-bounded value store.
 *
 * <p>This class owns <b>all</b> cache logic — reads, writes, capacity
 * enforcement, lazy TTL expiry, statistics, and listener notifications.
 * Concrete subclasses (e.g. {@link LRUCache}) only choose how to
 * construct the instance (default policy, background cleanup wiring, etc.).</p>
 *
 * <p><b>Thread-safety:</b> all cache methods are guarded by a single
 * {@link ReentrantLock}. Statistics counters use {@link AtomicLong} so that
 * {@link #stats()} reads are lock-free and do not block cache operations.</p>
 *
 * <p><b>Loader exceptions:</b> exceptions thrown by a
 * {@link CacheLoader} are wrapped in {@link CacheLoadException} and the
 * cache state is left unchanged (nothing cached).</p>
 */

public abstract class AbstractCache<K, V> implements Cache<K, V>{
    protected final int capacity;
    protected final long defaultTtlMillis;
    protected final EvictionPolicy<K> policy;
    protected final EvictionListener<K, V> listener; //nullable

    protected final Map<K, ExpiryEntry<V>> store;
    protected final ReentrantLock lock = new ReentrantLock();

    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();
    private final AtomicLong loadSuccessCount = new AtomicLong();
    private final AtomicLong loadFailureCount = new AtomicLong();
    private final AtomicLong totalLoadTimeNanos = new AtomicLong();

    private volatile boolean closed = false;

    protected AbstractCache(
        int capacity,
        long defaultTtlMillis,
        EvictionPolicy<K> policy,
        EvictionListener<K, V> listener
    ){
        if(capacity <= 0){
            throw new IllegalArgumentException(
                "Capacity must be > 0, got "+capacity
            );
        }

        if(defaultTtlMillis < 0){
            throw new IllegalArgumentException(
                "defaultTtlMillis must be >= 0, got "+defaultTtlMillis
            );
        }

        this.capacity = capacity;
        this.defaultTtlMillis = defaultTtlMillis;
        this.policy = Objects.requireNonNull(policy,"policy");
        this.listener = listener;
        this.store = new HashMap<>(Math.min(capacity, 128));
    }


    // ------------------------------------------------------------------
    // Read operations
    // ------------------------------------------------------------------

    @Override 
    public V get(K key){
        Objects.requireNonNull(key,"key");
        lock.lock();
        try {
            checkNotClosed();
            long now = System.currentTimeMillis();
            ExpiryEntry<V> entry = store.get(key);

            if(entry == null){
                missCount.incrementAndGet();
                return null;
            }

            if(entry.isExpired(now)){
                removeInternal(key, entry, EvictionReason.EXPIRED);
                missCount.incrementAndGet();
                return null;
            }

            policy.onAccess(key);
            hitCount.incrementAndGet();
            return entry.value;
        } finally {
            lock.unlock();
        }
    }

    @Override 
    public V get(K key, CacheLoader<K, V> loader){
        Objects.requireNonNull(loader,"loader");

        // Fast path — try cache first (counts as hit/miss)
        V cached = get(key);
        if(cached != null){
            return cached;
        }

        // Slow path — load outside the lock to avoid blocking the cache
        long startNanos = System.nanoTime();
        V loaded;
        try {
            loaded = loader.load(key);
        } catch (Exception e) {
            loadFailureCount.incrementAndGet();
            totalLoadTimeNanos.addAndGet(System.nanoTime() - startNanos);
            throw new CacheLoadException(key,
                 "Failed to load key: "+key, e);
        }
        totalLoadTimeNanos.addAndGet(System.nanoTime() - startNanos);
        loadSuccessCount.incrementAndGet();

        if(loaded != null){
            put(key, loaded);
        }

        return loaded;
    }

    @Override 
    public boolean containsKey(K key){
        Objects.requireNonNull(key,"key");
        lock.lock();
        try {
            checkNotClosed();
            long now = System.currentTimeMillis();
            ExpiryEntry<V> entry = store.get(key);

            if(entry == null){
                return false;
            }

            if(entry.isExpired(now)){
                removeInternal(key, entry, EvictionReason.EXPIRED);
                return false;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override 
    public int size(){
        lock.lock();
        try {
            checkNotClosed();
            return store.size();
        } finally {
            lock.unlock();
        }
    }

    @Override 
    public boolean isEmpty(){
        lock.lock();
        try {
            checkNotClosed();
            return store.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Write operations
    // ------------------------------------------------------------------

    @Override 
    public void put(K key, V value){
        putInternal(key, value, defaultTtlMillis);
    }

    @Override 
    public void put(K key, V value, long ttlMillis){
        if(ttlMillis <= 0){
            throw new IllegalArgumentException(
                "ttlMillis must be > 0, got "+ttlMillis
            );
        }
        putInternal(key,value,ttlMillis);
    }

    private void putInternal(K key, V value, long ttlMillis){
        Objects.requireNonNull(key,"key");
        Objects.requireNonNull(value,"value");

        lock.lock();
        try{
            checkNotClosed();
            long now = System.currentTimeMillis();
            long expiryAt = computeExpiryAt(now,ttlMillis);

            ExpiryEntry<V> existing = store.get(key);
            if(existing != null){
                EvictionReason reason = existing.isExpired(now)
                        ? EvictionReason.EXPIRED
                        : EvictionReason.REPLACED;
                store.remove(key);
                policy.onRemove(key);
                fireListener(key, existing.value, reason);
                evictionCount.incrementAndGet();
            }

            policy.onInsert(key);
            store.put(key, new ExpiryEntry<>(value, expiryAt));

            while(store.size() > capacity){
                evictOne();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override 
    public boolean remove(K key){
        Objects.requireNonNull(key,"key");
        lock.lock();
        try{
            checkNotClosed();
            ExpiryEntry<V> entry = store.remove(key);
            if(entry == null){
                return false;
            }
            policy.onRemove(key);
            fireListener(key, entry.value, EvictionReason.EXPLICIT);
            return true;
        }finally {
            lock.unlock();
        }
    }

    @Override 
    public void clear(){
        lock.lock();
        try {
            checkNotClosed();
            if(store.isEmpty()){
                return;
            }
            for(K key: store.keySet()){
                policy.onRemove(key);
            }
            store.clear();
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Observability
    // ------------------------------------------------------------------

    @Override 
    public CacheStats stats(){
        return new CacheStats(hitCount.get(), 
                              missCount.get(), 
                              evictionCount.get(), 
                              loadSuccessCount.get(), 
                              loadFailureCount.get(), 
                              totalLoadTimeNanos.get());
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override 
    public void close(){
        lock.lock();
        try {
            if(closed){
                return ;
            }
            closed = true;
            store.clear();
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Helpers — all called with lock held
    // ------------------------------------------------------------------

    private void evictOne(){
        K victim = policy.selectVictim();
        if(victim == null){
            return ;
        }
        ExpiryEntry<V> entry = store.remove(victim);
        policy.onRemove(victim);
        if(entry != null){
            fireListener(victim,entry.value,EvictionReason.SIZE_EXCEEDED);
            evictionCount.incrementAndGet();
        }
    }

    private void removeInternal(K key, ExpiryEntry<V> entry, 
                                    EvictionReason reason){
        store.remove(key);
        policy.onRemove(key);
        fireListener(key,entry.value, reason);
        evictionCount.incrementAndGet();
    }

    private void fireListener(K key, V value, EvictionReason reason){
        if(listener != null){
            listener.onEviction(key, value, reason);
        }
    }

    private void checkNotClosed(){
        if(closed){
            throw new IllegalStateException("Cache is closed");
        }
    }

    private static long computeExpiryAt(long now, long ttlMillis){
        if(ttlMillis <= 0){
            return 0;
        }
        long candidate = now + ttlMillis;
        return candidate < now ? Long.MAX_VALUE : candidate;
    }

     /**
     * Removes all currently expired entries from the cache.
     *
     * <p>Invoked by {@link CleanupTask} on the scheduled executor, and may
     * also be called directly by subclasses that want to trigger a sweep
     * on demand (e.g. after a config reload).</p>
     *
     * <p><b>Safe to call after close:</b> returns {@code 0} without touching
     * state. This lets an in-flight scheduled sweep race safely against
     * {@link #close()}.</p>
     *
     * @return the number of entries removed; {@code 0} if none were expired
     *         or the cache is already closed
     */
    protected int sweepExpired(){
        lock.lock();
        try {
            if(closed){
                return 0;
            }
            long now = System.currentTimeMillis();

            // Snapshot expired keys first — mutating a HashMap while
            // iterating its entry set is not safe even under a lock.
            List<K> expiredKeys = null;
            for(Map.Entry<K, ExpiryEntry<V>> e : store.entrySet()){
                if(e.getValue().isExpired(now)){
                    if(expiredKeys == null){
                        expiredKeys = new ArrayList<>();
                    }
                    expiredKeys.add(e.getKey());
                }
            }

            if(expiredKeys == null){
                return 0;
            }

            for(K key : expiredKeys){
                ExpiryEntry<V> entry = store.remove(key);
                if(entry != null){
                    policy.onRemove(key);
                    fireListener(key, entry.value, 
                        EvictionReason.EXPIRED
                    );
                    evictionCount.incrementAndGet();
                }
            }
            return expiredKeys.size();
        } finally {
            lock.unlock();
        }
    }
}