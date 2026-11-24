/**
 * Public API for the {@code generic-lru-cache} library.
 *
 * <p>This package contains everything a user typically needs:</p>
 * <ul>
 *   <li>{@link com.swapnil.cache.Cache} — the main interface</li>
 *   <li>{@link com.swapnil.cache.CacheBuilder} — fluent factory</li>
 *   <li>{@link com.swapnil.cache.CacheLoader} — cache-aside loading hook</li>
 *   <li>{@link com.swapnil.cache.EvictionListener} — removal callback</li>
 *   <li>{@link com.swapnil.cache.EvictionReason} — eviction classification</li>
 *   <li>{@link com.swapnil.cache.CacheStats} — performance snapshot</li>
 *   <li>{@link com.swapnil.cache.CacheLoadException} — loader failure wrapper</li>
 * </ul>
 *
 * <p><b>Quick start:</b></p>
 * <pre>{@code
 * Cache<String, User> cache = CacheBuilder.<String, User>newBuilder()
 *         .capacity(500)
 *         .defaultTtlMillis(60_000)
 *         .build();
 *
 * cache.put("user:42", user);
 * User u = cache.get("user:99", key -> userRepo.findById(99));
 * }</pre>
 *
 * <p>See {@link com.swapnil.cache.CacheBuilder} for all configuration
 * options, or the {@code docs/ARCHITECTURE.md} file in the repository for
 * design rationale.</p>
 */
package com.swapnil.cache;