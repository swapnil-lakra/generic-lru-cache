# Generic LRU Cache

> A thread-safe, generic, in-memory LRU cache library for Java — with TTL support, pluggable eviction policies, cache-aside loading, and built-in statistics. **Zero external dependencies.**

[![Java](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Tests](https://img.shields.io/badge/tests-JUnit%205-orange.svg)]()

---

## Why this library?

Real-world applications repeatedly fetch the same expensive data — from a database, a REST API, or a disk. A well-designed in-memory cache eliminates most of these round-trips. But building one correctly (thread-safe, type-safe, memory-bounded, with TTL) is non-trivial.

**Generic LRU Cache** solves that in a small, dependency-free, production-style library that you can drop into any Java project.

### Key highlights

- 🧵 **Thread-safe** — safe under concurrent access
- 🎯 **Type-safe** — `Cache<String, User>` gives you `User` back, no casting
- ⏱️ **TTL support** — per-entry and global default time-to-live
- 🔌 **Pluggable eviction** — LRU by default, custom policies via `EvictionPolicy<K>`
- 🧩 **Cache-aside loading** — one-liner `cache.get(key, loader)` pattern
- 📊 **Observability** — hits, misses, evictions, load times via `CacheStats`
- 🏗️ **Fluent builder** — clean, readable configuration
- 📦 **Zero dependencies** — only the JDK

---

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Features](#features)
- [Usage Examples](#usage-examples)
- [API Reference](#api-reference)
- [Design](#design)
- [Testing](#testing)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Installation

This library is currently not published to Maven Central. To use it in your project:

### Option 1 — Clone and build locally

```bash
git clone https://github.com/swapnil-lakra/generic-lru-cache.git
cd generic-lru-cache
mvn clean install
```

Then add the dependency:

```xml
<dependency>
    <groupId>com.swapnil</groupId>
    <artifactId>generic-lru-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Option 2 — Copy source

Since it's dependency-free, you can copy the `com.swapnil.cache` package directly into your project.

---

## Quick Start

```java
import com.swapnil.cache.Cache;
import com.swapnil.cache.CacheBuilder;

Cache<String, User> cache = CacheBuilder.<String, User>newBuilder()
    .capacity(500)
    .defaultTtlMillis(10 * 60 * 1000L)   // 10 minutes
    .build();

// Put & get
cache.put("user:42", new User("Alice"));
User u = cache.get("user:42");

// Cache-aside loading — DB hit only on miss
User u2 = cache.get("user:99", key -> userRepository.findById(99));
```

---

## Features

| Feature | Status |
|---------|--------|
| Basic `get` / `put` / `remove` / `clear` | ✅ |
| LRU eviction with O(1) operations | ✅ |
| Per-entry TTL | ✅ |
| Global default TTL | ✅ |
| Lazy expiry on `get()` | ✅ |
| Background TTL cleanup | ✅ |
| Cache-aside loading (`get(key, loader)`) | ✅ |
| Eviction listener callback | ✅ |
| Pluggable `EvictionPolicy<K>` | ✅ |
| Thread-safe operations | ✅ |
| Cache statistics (`CacheStats`) | ✅ |
| Fluent builder API | ✅ |
| Zero external dependencies | ✅ |
| Custom eviction policy (LFU, FIFO) | 🔜 planned |
| JMH benchmarks | 🔜 planned |
| Maven Central publish | 🔜 planned |

---

## Usage Examples

### 1. Simple bounded cache

```java
Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
    .capacity(3)
    .build();

cache.put("a", "1");
cache.put("b", "2");
cache.put("c", "3");
cache.get("a");          // touch "a" (most recently used)
cache.put("d", "4");     // evicts "b" (least recently used)

assert cache.get("b") == null;
assert cache.get("d").equals("4");
```

### 2. With TTL

```java
Cache<String, Session> sessions = CacheBuilder.<String, Session>newBuilder()
    .capacity(1000)
    .defaultTtlMillis(30 * 60 * 1000L)   // 30 minutes default
    .backgroundCleanup(true)
    .cleanupIntervalMillis(60 * 1000L)   // sweep every 60s
    .build();

sessions.put("token-abc", session);
sessions.put("token-xyz", shortLivedSession, 5 * 1000L);  // 5 seconds only
```

### 3. Cache-aside loading (DB call only on miss)

```java
Cache<Long, Product> productCache = CacheBuilder.<Long, Product>newBuilder()
    .capacity(500)
    .build();

Product p = productCache.get(42L, id -> productRepo.findById(id));
// first call hits DB → cached; subsequent calls served from cache
```

### 4. Eviction listener

```java
Cache<String, byte[]> cache = CacheBuilder.<String, byte[]>newBuilder()
    .capacity(100)
    .evictionListener((key, value, reason) ->
        log.warn("Evicted {} ({} bytes) — reason: {}", key, value.length, reason))
    .build();
```

### 5. Custom eviction policy (e.g., FIFO)

```java
public class FIFOEvictionPolicy<K> implements EvictionPolicy<K> {
    private final Deque<K> queue = new ArrayDeque<>();

    public void onAccess(K key) { /* FIFO ignores access */ }
    public void onInsert(K key) { queue.addLast(key); }
    public void onRemove(K key) { queue.remove(key); }
    public K selectVictim()    { return queue.peekFirst(); }
}

Cache<String, String> fifo = CacheBuilder.<String, String>newBuilder()
    .capacity(10)
    .evictionPolicy(new FIFOEvictionPolicy<>())
    .build();
```

### 6. Observing stats

```java
CacheStats stats = cache.stats();
System.out.printf("hits=%d, misses=%d, evictions=%d, hitRatio=%.2f%n",
    stats.hitCount(), stats.missCount(),
    stats.evictionCount(), stats.hitRatio());
```

---

## API Reference

### `Cache<K, V>`

| Method | Description |
|--------|-------------|
| `V get(K key)` | Returns value, or `null` if absent/expired |
| `V get(K key, CacheLoader<K,V> loader)` | Returns value; loads & caches on miss |
| `void put(K key, V value)` | Insert/replace using default TTL |
| `void put(K key, V value, long ttlMillis)` | Insert/replace with custom TTL |
| `boolean remove(K key)` | Remove entry, fires `EXPLICIT` eviction |
| `boolean containsKey(K key)` | Presence check (expired = absent) |
| `int size()` | Current entry count |
| `void clear()` | Remove all entries |
| `CacheStats stats()` | Snapshot of counters |
| `void close()` | Shutdown cleanup threads |

### `CacheBuilder<K, V>`

| Method | Default | Description |
|--------|---------|-------------|
| `capacity(int)` | `100` | Max entries before eviction |
| `defaultTtlMillis(long)` | `0` (no expiry) | Default per-entry TTL |
| `evictionPolicy(EvictionPolicy<K>)` | `LRUEvictionPolicy` | Custom policy |
| `evictionListener(EvictionListener<K,V>)` | `null` | Eviction callback |
| `backgroundCleanup(boolean)` | `false` | Enable TTL sweeper |
| `cleanupIntervalMillis(long)` | `60000` | Sweep interval |

### `EvictionReason`

`SIZE_EXCEEDED` · `EXPIRED` · `EXPLICIT` · `REPLACED`

---

## Design

### Class diagram (simplified)

```
              ┌──────────────┐
              │  Cache<K,V>  │  «interface»
              └──────┬───────┘
                     △
                     │ implements
              ┌──────┴────────┐
              │ AbstractCache │  «abstract template»
              └──────┬────────┘
                     △
                     │ extends
              ┌──────┴───────┐
              │  LRUCache<K,V>│
              └───────────────┘

    Collaborators:
    • EvictionPolicy<K>      ← pluggable (default: LRUEvictionPolicy)
    • CacheLoader<K,V>       ← functional (cache-aside)
    • EvictionListener<K,V>  ← functional (callbacks)
    • CacheStats             ← immutable snapshot
    • CacheBuilder<K,V>      ← fluent builder
```

### Implementation notes

- **LRU order** maintained via `HashMap<K, LruNode<K>>` + doubly-linked list with dummy head/tail → **O(1)** insert / access / evict.
- **Thread safety** via a single `ReentrantLock` guarding mutation + reads. Eviction and TTL sweep run under the same lock to avoid races.
- **TTL** handled with a per-entry `ExpiryEntry<V>` wrapper — lazy check on every `get` plus optional background sweep for memory hygiene.
- **Cache-aside loader** runs **outside** the lock to avoid blocking unrelated cache operations; double-check is done post-load.
- **Null keys/values** are rejected — matches `ConcurrentHashMap` semantics and avoids ambiguous "is it absent or null?" logic.
- **Eviction listeners** fire **inside** the lock for consistent ordering; users should keep them fast and non-blocking.

### Scope boundaries (intentionally out of scope)

- ❌ Disk persistence
- ❌ Distributed / multi-JVM
- ❌ JMX / Prometheus export
- ❌ Object serialization

This project focuses purely on a **single-JVM, in-memory, generic cache**.

---

## Testing

```bash
mvn test
```

Coverage includes:

- Unit tests for `LRUEvictionPolicy` (order invariants, boundary conditions)
- Behavioural tests for `LRUCache` (get/put/remove, eviction, TTL, cache-aside)
- Concurrency tests (multi-threaded stress, no `ConcurrentModificationException`)
- Edge cases (null keys, closed cache, zero capacity, expired entries)
- Builder validation & defaults

---

## Roadmap

- [x] v1.0 — Core LRU cache, TTL, loader, builder, stats
- [ ] v1.1 — `LFUEvictionPolicy`, `FIFOEvictionPolicy`
- [ ] v1.2 — JMH benchmarks
- [ ] v1.3 — Publish to Maven Central
- [ ] v2.0 — Async `CompletionStage` loader API

---

## Contributing

Issues and pull requests welcome! For major changes, please open an issue first to discuss the proposed change.

```bash
git clone https://github.com/swapnil-lakra/generic-lru-cache.git
cd generic-lru-cache
mvn clean install
```

---

## License

[MIT](LICENSE)