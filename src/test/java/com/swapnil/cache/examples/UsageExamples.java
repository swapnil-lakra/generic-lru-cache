package com.swapnil.cache.examples;

import com.swapnil.cache.Cache;
import com.swapnil.cache.CacheBuilder;
import com.swapnil.cache.CacheStats;
import com.swapnil.cache.policy.EvictionPolicy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable demonstrations of every public feature.
 *
 * <p>Run from your IDE, or from the command line:</p>
 * <pre>
 *   mvn -q test-compile exec:java \
 *     -Dexec.mainClass=com.swapnil.cache.examples.UsageExamples
 * </pre>
 * <p>(The {@code exec:java} invocation requires the
 * {@code exec-maven-plugin}. Alternatively, run the {@code main} method
 * directly from your IDE.)</p>
 */
public final class UsageExamples {

    public static void main(String[] args) throws InterruptedException {
        basicPutGet();
        lruEviction();
        ttlExpiry();
        cacheAsideLoading();
        evictionListener();
        customPolicy();
        statistics();
        tryWithResources();
        System.out.println("\nAll examples completed successfully.");
    }

    // ------------------------------------------------------------------

    private static void basicPutGet() {
        section("1. Basic put / get");

        Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .capacity(10)
                .build();

        try {
            cache.put("greeting", "hello");
            System.out.println("get(greeting) = " + cache.get("greeting"));
            System.out.println("get(missing)  = " + cache.get("missing"));
            System.out.println("size          = " + cache.size());
        } finally {
            cache.close();
        }
    }

    // ------------------------------------------------------------------

    private static void lruEviction() {
        section("2. LRU eviction (capacity = 2)");

        Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .capacity(2)
                .build();

        try {
            cache.put("a", "1");
            cache.put("b", "2");
            System.out.println("after put a, put b         -> size = " + cache.size());

            cache.get("a");            // touch "a" -> "b" is now LRU
            System.out.println("after get a (touch a)      -> 'a' is now MRU");

            cache.put("c", "3");       // evicts "b"
            System.out.println("after put c                -> evicted 'b'");
            System.out.println("  get(b) = " + cache.get("b"));
            System.out.println("  get(a) = " + cache.get("a"));
            System.out.println("  get(c) = " + cache.get("c"));
        } finally {
            cache.close();
        }
    }

    // ------------------------------------------------------------------

    private static void ttlExpiry() throws InterruptedException {
        section("3. TTL — per-entry and default");

        Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .defaultTtlMillis(50)
                .build();

        try {
            cache.put("short", "v1");
            cache.put("long", "v2", 10_000);

            System.out.println("immediately after put:");
            System.out.println("  get(short) = " + cache.get("short"));
            System.out.println("  get(long)  = " + cache.get("long"));

            Thread.sleep(120);

            System.out.println("after ~120ms:");
            System.out.println("  get(short) = " + cache.get("short") + "  (expired)");
            System.out.println("  get(long)  = " + cache.get("long") + "   (custom 10s TTL)");
        } finally {
            cache.close();
        }
    }

    // ------------------------------------------------------------------

    private static void cacheAsideLoading() {
        section("4. Cache-aside loading");

        AtomicInteger dbHits = new AtomicInteger();
        Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .build();

        try {
            // Simulated "database" -- expensive, called only on miss
            java.util.function.Function<String, String> db =
                    key -> {
                        dbHits.incrementAndGet();
                        return "DB[" + key + "]";
                    };

            String first  = cache.get("user:1", db::apply);
            String second = cache.get("user:1", db::apply);

            System.out.println("first  = " + first);
            System.out.println("second = " + second + "  (from cache)");
            System.out.println("dbHits = " + dbHits.get() + "  (called only once)");
        } finally {
            cache.close();
        }
    }

    // ------------------------------------------------------------------

    private static void evictionListener() {
        section("5. Eviction listener");

        Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .capacity(2)
                .evictionListener((key, value, reason) ->
                        System.out.println("  [evicted] key=" + key
                                + ", value=" + value
                                + ", reason=" + reason))
                .build();

        try {
            cache.put("a", "1");
            cache.put("b", "2");
            System.out.println("put c (triggers SIZE_EXCEEDED):");
            cache.put("c", "3");
            System.out.println("remove a (triggers EXPLICIT):");
            cache.remove("a");
            System.out.println("put c again (triggers REPLACED):");
            cache.put("c", "3-new");
        } finally {
            cache.close();
        }
    }

    // ------------------------------------------------------------------

    private static void customPolicy() {
        section("6. Custom eviction policy (FIFO)");

        Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .capacity(2)
                .evictionPolicy(new FifoPolicy<>())
                .build();

        try {
            cache.put("a", "1");
            cache.put("b", "2");
            System.out.println("after put a, put b:       cache has [a, b]");

            cache.get("a");            // FIFO ignores access
            System.out.println("after get a (FIFO ignores access)");

            cache.put("c", "3");       // FIFO evicts "a" (oldest insert)
            System.out.println("after put c:");
            System.out.println("  get(a) = " + cache.get("a")
                    + "  (evicted -- FIFO ignores access)");
            System.out.println("  get(b) = " + cache.get("b"));
            System.out.println("  get(c) = " + cache.get("c"));
        } finally {
            cache.close();
        }
    }

    // ------------------------------------------------------------------

    private static void statistics() {
        section("7. Statistics");

        Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .capacity(2)
                .build();

        try {
            cache.put("a", "1");
            cache.put("b", "2");
            cache.get("a");            // hit
            cache.get("a");            // hit
            cache.get("x");            // miss
            cache.put("c", "3");       // SIZE_EXCEEDED eviction

            CacheStats s = cache.stats();
            System.out.println("hits            = " + s.hitCount());
            System.out.println("misses          = " + s.missCount());
            System.out.println("evictions       = " + s.evictionCount());
            System.out.println("hit ratio       = " + String.format("%.2f", s.hitRatio()));
            System.out.println("request count   = " + s.requestCount());
        } finally {
            cache.close();
        }
    }

    // ------------------------------------------------------------------

    private static void tryWithResources() {
        section("8. Try-with-resources auto-close");

        Cache<String, String> reference;
        try (Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .backgroundCleanup(true)
                .cleanupIntervalMillis(60_000)
                .build()) {
            reference = cache;
            cache.put("a", "1");
            System.out.println("inside block, get(a) = " + cache.get("a"));
        }
        // Cache is now closed; further ops throw
        try {
            reference.get("a");
            System.out.println("UNEXPECTED: get() did not throw after close");
        } catch (IllegalStateException e) {
            System.out.println("after block, get() throws IllegalStateException — expected");
        }
    }

    // ------------------------------------------------------------------

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    /**
     * Minimal FIFO policy — proves custom policies drop in cleanly.
     */
    private static final class FifoPolicy<K> implements EvictionPolicy<K> {
        private final Deque<K> queue = new ArrayDeque<>();

        @Override public void onAccess(K key) { /* FIFO ignores access */ }
        @Override public void onInsert(K key) { queue.addLast(key); }
        @Override public void onRemove(K key) { queue.remove(key); }
        @Override public K selectVictim()    { return queue.peekFirst(); }
    }
}