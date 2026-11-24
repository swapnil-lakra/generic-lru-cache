# Generic LRU Cache

> A thread-safe, generic, in-memory LRU cache library for Java — with TTL
> support, pluggable eviction policies, cache-aside loading, and built-in
> statistics. **Zero external dependencies.**

[![Build](https://github.com/swapnil-lakra/generic-lru-cache/actions/workflows/build.yml/badge.svg)](https://github.com/swapnil-lakra/generic-lru-cache/actions/workflows/build.yml)
[![Java](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

Real-world applications repeatedly fetch the same expensive data — from
a database, a REST API, or a disk. A well-designed in-memory cache
eliminates most of these round-trips. But building one correctly
(thread-safe, type-safe, memory-bounded, TTL-aware) is non-trivial.

**Generic LRU Cache** solves that in a small, dependency-free library
you can drop into any Java project.

---

## Key highlights

- 🧵 **Thread-safe** — safe under concurrent access
- 🎯 **Type-safe** — `Cache<String, User>` returns `User`, no casting
- ⏱️ **TTL support** — per-entry and default time-to-live
- 🔌 **Pluggable eviction** — LRU by default, custom via `EvictionPolicy<K>`
- 🧩 **Cache-aside loading** — one-liner `cache.get(key, loader)` pattern
- 📊 **Observability** — hits, misses, evictions, load timings via `CacheStats`
- 🏗️ **Fluent builder** — clean, readable configuration
- 📦 **Zero dependencies** — only the JDK

---

## Installation

This library is not yet published to Maven Central. To use it:

### Build from source

```bash
git clone https://github.com/swapnil-lakra/generic-lru-cache.git
cd generic-lru-cache
mvn clean install
```

Then in your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.swapnil</groupId>
    <artifactId>generic-lru-cache</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Or copy the source
The library has zero runtime dependencies. The entire
`com.swapnil.cache` package is self-contained and can be copied
directly into your project.

---

## Quick Start

```java
import com.swapnil.cache.Cache;
import com.swapnil.cache.CacheBuilder;

Cache<String, User> cache = CacheBuilder.<String, User>newBuilder()
        .capacity(500)
        .defaultTtlMillis(10 * 60 * 1000L)   // 10 minutes
        .build();

cache.put("user:42", new User("Alice"));
User u = cache.get("user:42");

// Cache-aside loading — DB hit only on miss
User u2 = cache.get("user:99", key -> userRepository.findById(99));
```

---

## Features
| Feature |	Status |
|---------|--------|
| `get` / `put` / `remove` / `clear` / `containsKey` / `size` / `isEmpty` | ✅ |
| LRU eviction with O(1) operations	| ✅ |
| Per-entry TTL and default TTL | ✅ |
| Lazy expiry on access | ✅ |
| Background TTL cleanup (opt-in) |	✅ |
| Cache-aside loading via `CacheLoader`	| ✅ |
| Eviction listener callback | ✅ |
| Pluggable `EvictionPolicy<K>` | ✅ |
| Thread-safe operations | ✅ |
| Cache statistics (CacheStats) | ✅ |
| Fluent builder API | ✅ |
| Zero external dependencies | ✅ |
| LFU / FIFO policies (library-provided) | 🔜 planned |
| Maven Central publication | 🔜 planned |

---

## Usage Examples

All examples below are backed by a runnable demo in
`src/test/java/com/swapnil/cache/examples/UsageExamples.java`.
Run it from your IDE, or via:

```bash
mvn -q test-compile exec:java \
  -Dexec.mainClass=com.swapnil.cache.examples.UsageExamples
``` 
### 1. Simple bounded cache

```java
Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
        .capacity(3)
        .build();

cache.put("a", "1");
cache.put("b", "2");
cache.put("c", "3");
cache.get("a");          // touch "a" -> "b" becomes LRU
cache.put("d", "4");     // evicts "b"

assert cache.get("b") == null;
assert cache.get("d").equals("4");
```
### 2. With TTL and background cleanup
```java
Cache<String, Session> sessions = CacheBuilder.<String, Session>newBuilder()
        .capacity(1000)
        .defaultTtlMillis(30 * 60 * 1000L)   // 30 minutes
        .backgroundCleanup(true)
        .cleanupIntervalMillis(60 * 1000L)   // sweep every 60s
        .build();

sessions.put("token-abc", session);
sessions.put("token-xyz", shortLivedSession, 5 * 1000L);   // 5s only
```

### 3. Cache-aside loading
```java 
Cache<Long, Product> productCache = CacheBuilder.<Long, Product>newBuilder()
        .capacity(500)
        .build();

// First call hits DB -> cached
// Subsequent calls -> served from cache
Product p = productCache.get(42L, id -> productRepo.findById(id));
```

### 4. Eviction listener
```java
Cache<String, byte[]> cache = CacheBuilder.<String, byte[]>newBuilder()
        .capacity(100)
        .evictionListener((key, value, reason) ->
            log.warn("Evicted {} ({} bytes), reason={}",
                     key, value.length, reason))
        .build();
```

### 5. Custom eviction policy (FIFO)

The library ships with LRU but any policy that honors
`EvictionPolicy<K>` drops in. A minimal FIFO implementation:
```java
public class FifoPolicy<K> implements EvictionPolicy<K> {
    private final Deque<K> queue = new ArrayDeque<>();

    public void onAccess(K key) { /* FIFO ignores access */ }
    public void onInsert(K key) { queue.addLast(key); }
    public void onRemove(K key) { queue.remove(key); }
    public K selectVictim()    { return queue.peekFirst(); }
}

Cache<String, String> fifo = CacheBuilder.<String, String>newBuilder()
        .capacity(10)
        .evictionPolicy(new FifoPolicy<>())
        .build();
```

### 6. Try-with-resources (auto-close)
```java
try (Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
        .backgroundCleanup(true)
        .build()) {

    cache.put("a", "1");
    System.out.println(cache.get("a"));

}   // cleanup thread shut down automatically
```

### 7. Observing statistics
```java
CacheStats stats = cache.stats();
System.out.printf("hits=%d, misses=%d, evictions=%d, hitRatio=%.2f%n",
    stats.hitCount(),
    stats.missCount(),
    stats.evictionCount(),
    stats.hitRatio());
```

## API Reference

### `Cache<K, V>`
| Method | Description |
|--------|-------------|
| `V get(K key)` | Returns value or `null` if absent/expired |
| `V get(K key, CacheLoader<K,V> loader)` | Loads & caches on miss |
| `void put(K key, V value)` | Insert/replace using default TTL |
| `void put(K key, V value, long ttlMillis)` | Insert/replace with custom TTL |`
| `boolean remove(K key)` |	Remove entry; fires `EXPLICIT` eviction |
| `boolean containsKey(K key)` | Presence check (expired = absent) |
| `int size()` | Current entry count |
| `boolean isEmpty()` |	Whether the cache has entries |
| `void clear()` | Remove all entries |
| `CacheStats stats()` | Snapshot of counters |
| `void close()` | Release background resources |

### `CacheBuilder<K, V>`
| Method | Default | Description |
|--------|---------|-------------|
| `capacity(int)` | `100` | Max entries before eviction |
| `defaultTtlMillis(long)` | `0` (no expiry) | Default per-entry TTL |
| `evictionPolicy(EvictionPolicy<K>)` |	LRU |	Custom policy |
| `evictionListener(EvictionListener<K,V>)` |	none |	Eviction callback |
| `backgroundCleanup(boolean)` |	`false` |	Enable TTL sweeper |
| `cleanupIntervalMillis(long)` |	`60_000` |	Sweep interval |

### `EvictionReason`

`SIZE_EXCEEDED` · `EXPIRED` · `EXPLICIT`· `REPLACED`

---

## Design

Full design rationale is in docs/ARCHITECTURE.md.
Quick summary:

- **Single** `ReentrantLock` for all cache operations — correctness first

- **Lock-free** `stats()` via `AtomicLong` counters

- **Loaders run outside the lock** — slow I/O never blocks the cache

- **LRU:** doubly linked list with sentinel head/tail + `HashMap<K,Node>`

- **TTL:** per-entry timestamp, lazy check on access, optional background sweep

- **Background cleanup:** daemon `ScheduledExecutorService`, opt-in

- **`close():`** idempotent, interrupts executor after marking closed

## Scope boundaries (intentionally out of scope)

- ❌ Disk persistence

- ❌ Distributed / multi-JVM

- ❌ JMX / Prometheus export

- ❌ Object serialization

- ❌ Annotation-based caching

- ❌ Single-flight loader deduplication

## Building & Testing
**Requirements:** JDK 11 or later, Maven 3.6+.

```bash
# Compile
mvn clean compile

# Run tests (fast — no coverage gate)
mvn clean test

# Full build with JaCoCo coverage gate
# (LINE >= 85%, BRANCH >= 70%)
mvn clean verify
```

Test suite covers:

- `LRUEvictionPolicy` order invariants and contract violations

- `LRUCache` behavior — every method, every eviction reason, TTL, loader

- End-to-end integration via the public API only

- Concurrency stress: mixed ops, capacity invariant, close race, concurrent loader

- Edge cases: null handling, closed state, TTL overflow

## Project Structure

```text
src/main/java/com/swapnil/cache/          public API
├── Cache.java
├── CacheBuilder.java
├── CacheLoader.java
├── EvictionListener.java
├── EvictionReason.java
├── CacheStats.java
├── CacheLoadException.java
├── policy/                               extension point
│   ├── EvictionPolicy.java
│   └── LRUEvictionPolicy.java
└── internal/                             implementation (not for external use)
    ├── AbstractCache.java
    ├── LRUCache.java
    ├── ExpiryEntry.java
    └── CleanupTask.java

src/test/java/com/swapnil/cache/          tests
├── CacheStatsTest.java
├── CacheBuilderTest.java
├── CacheLoadExceptionTest.java
├── CacheIntegrationTest.java
├── ConcurrencyStressTest.java
├── examples/UsageExamples.java           runnable demo
├── policy/LRUEvictionPolicyTest.java
└── internal/
    ├── LRUCacheTest.java
    └── BackgroundCleanupTest.java

docs/ARCHITECTURE.md                      design rationale
```

## Roadmap

- [x] v1.0 — Core LRU cache, TTL, loader, builder, stats
- [ ] v1.1 — Bundled LFUEvictionPolicy, FIFOEvictionPolicy
- [ ] v1.2 — JMH benchmarks
- [ ] v1.3 — Publish to Maven Central
- [ ] v2.0 — Single-flight loader via CompletionStage

---

## Contributing
Issues and pull requests welcome! For major changes, please open an
issue first to discuss the proposal. See
[CONTRIBUTING.md](CONTRIBUTING.md) for branch strategy and commit
conventions.

```bash
git clone https://github.com/swapnil-lakra/generic-lru-cache.git
cd generic-lru-cache
mvn clean verify
```

## License
[MIT](LICENCE)