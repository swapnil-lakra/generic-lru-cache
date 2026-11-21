# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial project scaffolding: Maven build, package structure, IDE/OS ignores
- MIT license
- README with project overview, features, and usage examples
- GitHub issue and PR templates
- EditorConfig for cross-IDE consistency
- JaCoCo coverage plugin configuration

  <br>

- `EvictionReason` enum (SIZE_EXCEEDED, EXPIRED, EXPLICIT, REPLACED)
- `CacheLoader<K, V>` functional interface for cache-aside loading
- `EvictionListener<K, V>` functional interface for eviction callbacks
- `CacheLoadException` unchecked exception wrapping loader failures
- `CacheStats` immutable snapshot with hit/miss/load/eviction counters
- Unit tests for `CacheStats` derivations and edge cases

  <br>

- `Cache<K, V>` public interface extending `AutoCloseable` with
  `get`, `get(key, loader)`, `put`, `remove`, `containsKey`, `size`, `isEmpty`, `clear`, `stats`, and `close`
- `EvictionPolicy<K>` public interface with pluggable eviction hooks:
  `onAccess`, `onInsert`, `onRemove`, `selectVictim`

  <br>

- `LRUEvictionPolicy<K>` — O(1) LRU policy backed by a doubly linked
  list with sentinel head/tail plus `HashMap<K, Node<K>>` for O(1) lookup
- Unit tests covering order invariants, head/middle/tail removal,
  access promotion, contract violations, peek-only semantics, and a
  full eviction cycle simulation

  <br>
  
- `ExpiryEntry<V>` internal wrapper for value + expiration timestamp
- `AbstractCache<K, V>` template implementing the full `Cache` contract:
  capacity enforcement, lazy TTL expiry, cache-aside loading, eviction
  listener notifications, and lock-free `stats()`
- `LRUCache<K, V>` concrete cache — policy-agnostic, driven by an
  externally supplied `EvictionPolicy<K>`
- Comprehensive `LRUCacheTest` covering CRUD, eviction ordering, TTL,
  cache-aside loading, statistics, edge cases, and thread-safety smoke test

  <br>

- Background TTL cleanup via `ScheduledExecutorService` (daemon thread),
  opt-in through the `LRUCache` constructor
- `CleanupTask` — Runnable wrapper that invokes `sweepExpired()`, logs and
  swallows exceptions so a failed sweep cannot kill the scheduler
- `AbstractCache#sweepExpired()` — protected method that removes all
  currently expired entries, returns removed count, safe to call after close
- Tests for direct sweep, automatic sweep, listener firing, eviction
  counting, lifecycle, and concurrent close-vs-sweep safety

[Unreleased]: https://github.com/swapnil-lakra/generic-lru-cache/commits/develop