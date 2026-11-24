# Architecture

This document explains the design decisions behind `generic-lru-cache`.
It's intended for contributors and for anyone curious about why certain
choices were made.

---

## High-level structure
```text
com.swapnil.cache (public API)
├── Cache<K,V> interface — the contract
├── CacheBuilder<K,V> fluent factory
├── CacheLoader<K,V> functional — cache-aside
├── EvictionListener<K,V> functional — callbacks
├── EvictionReason enum — why an entry left
├── CacheStats immutable snapshot
└── CacheLoadException unchecked wrapper

com.swapnil.cache.policy (extension point)
├── EvictionPolicy<K> interface — pluggable
└── LRUEvictionPolicy<K> default implementation

com.swapnil.cache.internal (implementation — not for external use)
├── AbstractCache<K,V> template — all cache behavior lives here
├── LRUCache<K,V> concrete — wires policy + executor
├── ExpiryEntry<V> value + expiry timestamp
└── CleanupTask Runnable wrapper for scheduled sweeps
```


`AbstractCache` is the **only** place with real cache logic. `LRUCache`
exists as a concrete type that the builder can instantiate; it holds no
logic today beyond executor wiring. This split lets future cache variants
(different defaults, different policies) reuse everything.

---

## Threading model

**One lock.** Every cache operation acquires a single `ReentrantLock`.
This is a deliberate simplification:

- **Correctness first.** Multi-lock or lock-free designs are extremely
  easy to get wrong. A single lock is trivially correct.
- **Stats reads are lock-free.** Counters are `AtomicLong`, so `stats()`
  never acquires the lock. If stats are queried more often than expected,
  the cache never pays for it.
- **Loaders run outside the lock.** `get(key, loader)` releases the lock
  while `loader.load(key)` runs. A slow DB call therefore doesn't block
  other cache operations.

**Trade-off:** under extreme parallelism with a very hot working set,
single-lock contention is measurable. For v1 this is acceptable — a
future optimization branch could introduce striped locks or a
`ConcurrentHashMap` with a side LRU order structure.

**Non-single-flight loaders.** Two concurrent `get(missingKey, loader)`
calls may both invoke the loader. This is documented in `Cache#get(K,
CacheLoader)` Javadoc. Single-flight can be layered on later via a
`ConcurrentHashMap<K, CompletableFuture<V>>` keyed on in-flight loads.

---

## LRU algorithm

`LRUEvictionPolicy` implements O(1) LRU with the classic two-structure
trick:

```text
head (sentinel)                       tail (sentinel)
    │                                        │
    ▼                                        ▼
┌────────┐    ┌───┐    ┌───┐    ┌───┐    ┌────────┐
│ dummy  │◄──►│ c │◄──►│ b │◄──►│ a │◄──►│ dummy  │
└────────┘    └───┘    └───┘    └───┘    └────────┘

MRU ... LRU

HashMap<K, Node<K>> — O(1) key → node lookup
```

### Operations

| Operation | Steps | Complexity |
|-----------|-------|------------|
| `onInsert(k)` | `linkAfterHead(new Node(k))` + `map.put(k, node)` | O(1) |
| `onAccess(k)` | `unlink(node)` + `linkAfterHead(node)` | O(1) |
| `onRemove(k)` | `unlink(node)` + `map.remove(k)` | O(1) |
| `selectVictim()` | return `tail.prev.key` (or `null` if empty) | O(1) |

### Why sentinel head and tail?

Without sentinels, every `unlink` and `linkAfterHead` needs a null check
on `prev` and `next`. With sentinels, those pointers are always valid
and the code is branch-free. Classic CLRS trick.

### `selectVictim()` is peek-only

`selectVictim()` returns `tail.prev.key` **without** mutating state. The
cache then calls `onRemove(victim)` to complete the eviction. This split
keeps the policy side-effect free during selection and makes concurrent
reasoning easier (from the cache's perspective, it can safely call
`selectVictim()` and decide not to evict without the policy's state
drifting).

---

## TTL strategy

Two complementary mechanisms:

### Lazy expiry

On `get(K)` / `containsKey(K)`, the entry's expiry timestamp is checked.
If elapsed, the entry is removed and the miss path is taken:

```java
if (entry.isExpired(now)) {
    removeInternal(key, entry, EvictionReason.EXPIRED);
    missCount.incrementAndGet();
    return null;
}
```

### Background cleanup (optional)

When configured via `CacheBuilder.backgroundCleanup(true)`, a
`ScheduledExecutorService` runs `sweepExpired()` at a fixed interval.
`sweepExpired()` snapshots all currently-expired keys under the lock,
then removes them in a second pass:

```java
List<K> expiredKeys = /* snapshot under lock */;
for (K key : expiredKeys) {
    ExpiryEntry<V> entry = store.remove(key);
    if (entry != null) {
        policy.onRemove(key);
        fireListener(key, entry.value, EvictionReason.EXPIRED);
        evictionCount.incrementAndGet();
    }
}
```

**Why snapshot first?** Mutating a `HashMap` while iterating its
`entrySet()` throws `ConcurrentModificationException` — even under a lock.

## `expiryAtMillis == 0` means "never expires"

This uses `0` as a sentinel rather than `Long.MAX_VALUE`, because TTL is
ubiquitous and `0` is unambiguous. Wall-clock overflow when computing
`now + ttlMillis` is capped at `Long.MAX_VALUE` (i.e. "effectively never").

---

## Eviction reasons

Four values, each with a specific meaning:

| Reason | Fires When | Counts toward `evictionCount`? |
|----------|--------------|----------------------------------|
| `SIZE_EXCEEDED` | `put()` pushes `size > capacity` | Yes |
| `EXPIRED` | TTL elapsed (lazy or sweep) | Yes | 
| `EXPLICIT` | `remove(key)` succeeded | No (state on this is deliberate) |
| `REPLACED` | `put()` over a live existing entry | Yes |

**Why `EXPLICIT` doesn't count as eviction:** user-initiated removal is
not cache inefficiency. If every `remove()` bumps `evictionCount`, the
stat becomes noise. This matches Guava Cache semantics.

**Why `REPLACED` counts:** replacing a live entry is a cache miss from
the perspective of the previous value — the cache paid the cost of
storing something the user discarded. It's still an "eviction" in the
resource-management sense.

**`put()` over an *expired* entry → `EXPIRED`:** the entry was effectively
already gone from the cache's perspective. Reporting it as REPLACED would
imply the user observed it, which they didn't.

---

## Cache-aside loading

```java
public V get(K key, CacheLoader<K, V> loader) {
    V cached = get(key);       // 1. try cache (counts as hit/miss)
    if (cached != null) return cached;

    long start = System.nanoTime();
    V loaded;
    try {
        loaded = loader.load(key);   // 2. load outside the lock
    } catch (Exception e) {
        loadFailureCount.incrementAndGet();
        totalLoadTimeNanos.addAndGet(System.nanoTime() - start);
        throw new CacheLoadException(key, ..., e);
    }
    totalLoadTimeNanos.addAndGet(System.nanoTime() - start);
    loadSuccessCount.incrementAndGet();

    if (loaded != null) put(key, loaded);   // 3. store
    return loaded;
}
```

**Why loaders run outside the lock:** a slow `loader.load(key)` (network
call, DB query) must not freeze the entire cache for other threads.

**Why exceptions are wrapped:** `CacheLoader.load` declares
`throws Exception`, which would force every caller to handle checked
exceptions from unrelated code. Wrapping in unchecked
`CacheLoadException` keeps the caller API clean. The original exception
is available via `getCause()`.

**Why `null` is not cached:** a loader returning `null` means "no value
exists for this key" — caching that would prevent the loader from being
retried when the value becomes available.

---

## Statistics: `totalLoadTimeNanos` not `Millis`

`System.currentTimeMillis()` is wall-clock — it can jump backwards when
the system clock is adjusted (NTP sync, user changes timezone). That
would corrupt load-time averages.

`System.nanoTime()` is monotonic — it always moves forward, and gives
higher precision. Same choice as Guava and Caffeine. The cost is that
absolute nano timestamps are meaningless; only **differences** matter,
which is exactly what we compute.

---

## `close()` ordering

```java
@Override
public void close() {
    super.close();              // 1. sets closed=true, clears store
    if (cleanupExecutor != null) {
        cleanupExecutor.shutdownNow();   // 2. interrupt executor
    }
}
```

**Why `super.close()` first?** If a sweep is currently running, it
acquires the lock, sees `closed == true`, and returns `0`. Only after
that does `shutdownNow()` interrupt any queued tasks. The reverse order
would let a queued sweep fire AFTER `shutdownNow()` on a cache that
hasn't yet marked itself closed — a race that could mutate state.

**`stats()` remains callable after `close()`** — this is intentional.
Post-mortem inspection is a valid debugging workflow.

---

## Non-goals
Deliberately not in scope for v1:

- Disk persistence

- Distributed / multi-JVM caching

- JMX or Prometheus export

- Object serialization
 
- Annotation-based @Cacheable
 
- Single-flight loader deduplication
 
- Reference-based eviction (soft, weak, phantom)
 
Each is a valid feature for a future version; bundling them here would
delay the v1 release and complicate the design.

---

## Testing strategy
| Layer | What's tested |
|-------|---------------|
| Unit | `LRUEvictionPolicy` order invariants; `CacheStats` derivations; `CacheLoadException` |
| Behavioral | `LRUCache` — every public method, every `EvictionReason`, TTL, loader semantics |
| Integration |	End-to-end via `CacheBuilder` only — no `internal` imports |
| Concurrency |	16-thread mixed ops; capacity invariant; close race; concurrent loader |
| Coverage gate | `mvn verify` enforces LINE ≥ 85%, BRANCH ≥ 70% |

Tests are fast (< 10s wall-clock on a laptop). No fixed sleeps except
where TTL timing genuinely requires them — and even there we poll with
deadlines rather than blind waits.







