package com.swapnil.cache;

import java.util.Objects;
/**
 * Immutable snapshot of cache performance counters.
 *
 * Returned by {@link Cache#stats()}. Snapshots are independent of the
 * cache — capturing one does not slow down subsequent cache operations.
 */

public final class CacheStats {
    
    private final long hitCount;
    private final long missCount;
    private final long evictionCount;
    private final long loadSuccessCount;
    private final long loadFailureCount;
    private final long totalLoadTimeNanos;

    /**
     * Creates a new stats snapshot.
     *
     * @throws IllegalArgumentException if any count is negative
     */

    public CacheStats(long hitCount,
                      long missCount,
                      long evictionCount,
                      long loadSuccessCount,
                      long loadFailureCount,
                      long totalLoadTimeNanos) {
        
        if(hitCount < 0 || missCount < 0 || evictionCount < 0
            || loadSuccessCount < 0 || loadFailureCount < 0
            || totalLoadTimeNanos < 0){
                throw new IllegalArgumentException("All cache statictics must be non-negative");
            }
        
        
        this.hitCount = hitCount;
        this.missCount = missCount;
        this.evictionCount = evictionCount;
        this.loadSuccessCount = loadSuccessCount;
        this.loadFailureCount = loadFailureCount;
        this.totalLoadTimeNanos = totalLoadTimeNanos;

        
    }

    /** Number of {@code get} calls that returned a cached value. */
    public long hitCount(){
        return hitCount;
    }

    /** Number of {@code get} calls that found no value in the cache. */
    public long missCount(){
        return missCount;
    }

    /** Total number of entries removed due to any {@link EvictionReason}. */
    public long evictionCount(){
        return evictionCount;
    }

    /** Number of successful {@link CacheLoader} invocations. */
    public long loadSuccessCount(){
        return loadSuccessCount;
    }

    /** Number of failed {@link CacheLoader} invocations. */
    public long loadFailureCount(){
        return loadFailureCount;
    }

    /** Total nanoseconds spent inside {@link CacheLoader} calls. */
    public long totalLoadTimeNanos(){
        return totalLoadTimeNanos;
    }

    /** Total number of cache lookup requests ({@code hitCount + missCount}). */
    public long requestCount(){
        return hitCount + missCount;
    }

    /** Total number of loader invocations ({@code loadSuccessCount + loadFailureCount}). */
    public long loadCount(){
        return loadSuccessCount + loadFailureCount;
    }

    /**
     * Fraction of requests served from the cache.
     *
     * @return a value in {@code [0.0, 1.0]}, or {@code 0.0} if there were no requests
     */

    public double hitRatio(){
        long total = requestCount();

        return total == 0 ? 0.0 : (double) hitCount / total;
    }

    /**
     * Fraction of requests that missed the cache.
     *
     * @return a value in {@code [0.0, 1.0]}, or {@code 0.0} if there were no requests
     */

    public double missRatio(){
        long total = requestCount();

        return total == 0 ? 0.0 : (double) missCount / total;
    }

    /**
     * Fraction of loader calls that threw an exception.
     *
     * @return a value in {@code [0.0, 1.0]}, or {@code 0.0} if there were no loads
     */
    public double loadExceptionRate(){
        long total = loadCount();

        return total == 0 ? 0.0 : (double) loadFailureCount / total; 
    }

    /**
    * Average time spent in a single loader invocation, in nanoseconds.
    *
    * @return average load duration, or {@code 0.0} if there were no loads
    */
    public double averageLoadPenaltyNanos(){
        long total = loadCount();
        return total == 0 ? 0.0 : (double) totalLoadTimeNanos / total;
    }

    @Override 
    public boolean equals(Object o){
        if(this == o){
            return true;
        }

        if(!(o instanceof CacheStats)){
            return false;
        }

        CacheStats other = (CacheStats) o;

        return this.hitCount == other.hitCount
               && this.missCount == other.missCount
               && this.evictionCount == other.evictionCount
               && this.loadSuccessCount == other.loadSuccessCount
               && this.loadFailureCount == other.loadFailureCount
               && this.totalLoadTimeNanos == other.totalLoadTimeNanos;   
    }

    @Override 
    public int hashCode(){
        return Objects.hash(hitCount, missCount, evictionCount,
            loadSuccessCount, loadFailureCount, totalLoadTimeNanos);
    }

    @Override 
    public String toString(){
        return String.format("CacheStats{ hits=%d, misses=%d, evictions=%d,"
        +"loadSuccess=%d, loadFailure=%d, totalLoadNanos=%d, "+
        "hitRatio=%.2f }", hitCount, missCount, evictionCount,
        loadSuccessCount, loadFailureCount, totalLoadTimeNanos,
        hitRatio()
        );
    }
}