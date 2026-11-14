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

- `EvictionReason` enum (SIZE_EXCEEDED, EXPIRED, EXPLICIT, REPLACED)
- `CacheLoader<K, V>` functional interface for cache-aside loading
- `EvictionListener<K, V>` functional interface for eviction callbacks
- `CacheLoadException` unchecked exception wrapping loader failures
- `CacheStats` immutable snapshot with hit/miss/load/eviction counters
- Unit tests for `CacheStats` derivations and edge cases

- `Cache<K, V>` public interface extending `AutoCloseable` with
  `get`, `get(key, loader)`, `put`, `remove`, `containsKey`, `size`, `isEmpty`, `clear`, `stats`, and `close`
- `EvictionPolicy<K>` public interface with pluggable eviction hooks:
  `onAccess`, `onInsert`, `onRemove`, `selectVictim`

[Unreleased]: https://github.com/swapnil-lakra/generic-lru-cache/commits/develop