package com.swapnil.cache;

/**
 * Thrown when a {@link CacheLoader} fails to compute a value.
 *
 * Wraps the original exception thrown by the loader so that callers of
 * {@code Cache#get(Object, CacheLoader)} do not need to catch checked
 * exceptions from application code.
 */

public class CacheLoadException extends RuntimeException {
    private final Object key;

    /**
     * Creates a new exception.
     *
     * @param key   the key whose loading failed
     * @param message human-readable description
     * @param cause the original exception thrown by the loader
     */
    public CacheLoadException(Object key, String message, Throwable cause){
        super(message,cause);
        this.key = key;
    }

    /**
    * Returns the key whose loading failed.
    *
    * @return the failed key
    */

    public Object getKey(){
        return key;
    }
}