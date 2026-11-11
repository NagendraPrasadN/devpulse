package com.devpulse.engine.store;

import java.util.Objects;

/**
 * Run this main() to SEE the three classic HashMap lessons.
 *   1. equals without hashCode  -> lookups fail
 *   2. constant hashCode        -> everything collides, lookups crawl
 *   3. proper hashCode          -> short chains, fast
 */
public class HashMapDemo {

    /** BROKEN: overrides equals but NOT hashCode (inherits identity-based Object.hashCode). */
    static final class NoHashKey {
        final int id;
        NoHashKey(int id) { this.id = id; }

        @Override public boolean equals(Object o) {
            return o instanceof NoHashKey other && other.id == id;
        }
    }

    /** BAD: legal (contract holds) but useless: every key lands in the same bucket. */
    static final class ConstantHashKey {
        final int id;
        ConstantHashKey(int id) { this.id = id; }

        @Override public boolean equals(Object o) {
            return o instanceof ConstantHashKey other && other.id == id;
        }
        @Override public int hashCode() { return 42; }
    }

    /** GOOD: hashCode derived from the same field as equals. */
    static final class GoodKey {
        final int id;
        GoodKey(int id) { this.id = id; }

        @Override public boolean equals(Object o) {
            return o instanceof GoodKey other && other.id == id;
        }
        @Override public int hashCode() { return Objects.hash(id); }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. equals without hashCode ===");
        SimpleHashMap<NoHashKey, String> broken = new SimpleHashMap<>();
        broken.put(new NoHashKey(1), "value");
        // A *different* object that is equals() to the first one:
        System.out.println("get(equal key) = " + broken.get(new NoHashKey(1))
                + "   <- null! different hashCode sent us to the wrong bucket");

        int n = 10_000;

        System.out.println("\n=== 2. constant hashCode, " + n + " keys ===");
        SimpleHashMap<ConstantHashKey, Integer> bad = new SimpleHashMap<>();
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) bad.put(new ConstantHashKey(i), i);
        long badMs = (System.nanoTime() - t1) / 1_000_000;
        report(bad.size(), bad.capacity(), bad.usedBuckets(), bad.longestChain(), badMs);

        System.out.println("\n=== 3. proper hashCode, " + n + " keys ===");
        SimpleHashMap<GoodKey, Integer> good = new SimpleHashMap<>();
        long t2 = System.nanoTime();
        for (int i = 0; i < n; i++) good.put(new GoodKey(i), i);
        long goodMs = (System.nanoTime() - t2) / 1_000_000;
        report(good.size(), good.capacity(), good.usedBuckets(), good.longestChain(), goodMs);
        System.out.println("resizes: " + good.resizeCount() + " (table doubled each time load passed 0.75)");
    }

    private static void report(int size, int capacity, int used, int longest, long ms) {
        System.out.printf("size=%d capacity=%d usedBuckets=%d longestChain=%d time=%dms%n",
                size, capacity, used, longest, ms);
    }
}
