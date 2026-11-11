package com.devpulse.engine.store;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A tiny, educational re-implementation of java.util.HashMap's core idea.
 * NOT thread-safe, NOT for production: it exists so you can SEE how HashMap works.
 *
 * ===== THE STRUCTURE =====
 * An array ("table") of buckets. Each bucket is a singly linked list of Nodes.
 *
 *   index 0:  null
 *   index 1:  [k=A] -> [k=Q] -> null      <- two keys collided into bucket 1
 *   index 2:  null
 *   index 3:  [k=B] -> null
 *
 * ===== put(key, value) =====
 *  1. hash   = key.hashCode(), then "spread" so high bits influence low bits
 *  2. index  = hash & (tableLength - 1)    (fast modulo; works because length is a power of 2)
 *  3. walk the bucket's chain: if a node has an EQUAL key -> replace its value
 *  4. otherwise append a new node
 *  5. if size > capacity * 0.75 (load factor) -> resize (double the table, redistribute)
 *
 * ===== get(key) =====
 *  Same hash -> same index -> walk the chain comparing with equals().
 *
 * ===== WHY equals/hashCode MATTER =====
 *  - hashCode picks the BUCKET. equals picks the NODE inside it.
 *  - Equal keys with different hashCodes land in different buckets -> get() never finds them.
 *  - A constant hashCode puts everything in ONE bucket -> every lookup walks a long chain,
 *    O(n) instead of O(1). Run HashMapDemo to watch both problems happen.
 *
 * (The real HashMap also converts long chains into balanced trees (Java 8+). We skip that.)
 */
public class SimpleHashMap<K, V> {

    private static final int INITIAL_CAPACITY = 16;   // must stay a power of two
    private static final float LOAD_FACTOR = 0.75f;

    /** One entry in a bucket's chain. We cache the hash so resize doesn't recompute it. */
    private static final class Node<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private Node<K, V>[] table;
    private int size;
    private int resizeCount;

    @SuppressWarnings("unchecked") // Java cannot create generic arrays directly; this cast is safe here
    public SimpleHashMap() {
        this.table = (Node<K, V>[]) new Node[INITIAL_CAPACITY];
    }

    /** Mixes the high 16 bits into the low 16 so poor hashCodes spread a bit better. */
    private static int spread(Object key) {
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    private static int indexFor(int hash, int tableLength) {
        return hash & (tableLength - 1);
    }

    /** Returns the previous value for the key, or null if the key was new. */
    public V put(K key, V value) {
        Objects.requireNonNull(key, "this simple map does not allow null keys");
        int hash = spread(key);
        int index = indexFor(hash, table.length);

        Node<K, V> last = null;
        for (Node<K, V> n = table[index]; n != null; n = n.next) {
            if (n.hash == hash && (n.key == key || n.key.equals(key))) {
                V old = n.value;
                n.value = value;      // key already present: replace value, size unchanged
                return old;
            }
            last = n;
        }

        Node<K, V> fresh = new Node<>(hash, key, value, null);
        if (last == null) {
            table[index] = fresh;     // empty bucket
        } else {
            last.next = fresh;        // collision: append to the chain
        }

        if (++size > table.length * LOAD_FACTOR) {
            resize();
        }
        return null;
    }

    public V get(Object key) {
        if (key == null) return null;
        int hash = spread(key);
        for (Node<K, V> n = table[indexFor(hash, table.length)]; n != null; n = n.next) {
            if (n.hash == hash && (n.key == key || n.key.equals(key))) {
                return n.value;
            }
        }
        return null;
    }

    public boolean containsKey(Object key) {
        if (key == null) return false;
        int hash = spread(key);
        for (Node<K, V> n = table[indexFor(hash, table.length)]; n != null; n = n.next) {
            if (n.hash == hash && (n.key == key || n.key.equals(key))) {
                return true;
            }
        }
        return false;
    }

    public V remove(Object key) {
        if (key == null) return null;
        int hash = spread(key);
        int index = indexFor(hash, table.length);

        Node<K, V> prev = null;
        for (Node<K, V> n = table[index]; n != null; prev = n, n = n.next) {
            if (n.hash == hash && (n.key == key || n.key.equals(key))) {
                if (prev == null) {
                    table[index] = n.next;   // removing the head of the chain
                } else {
                    prev.next = n.next;      // unlink from the middle/end
                }
                size--;
                return n.value;
            }
        }
        return null;
    }

    /** Doubles the table and re-places every node. Indexes change because the mask changed. */
    @SuppressWarnings("unchecked")
    private void resize() {
        Node<K, V>[] old = table;
        Node<K, V>[] bigger = (Node<K, V>[]) new Node[old.length * 2];

        for (Node<K, V> head : old) {
            Node<K, V> n = head;
            while (n != null) {
                Node<K, V> next = n.next;                  // remember before we overwrite n.next
                int index = indexFor(n.hash, bigger.length);
                n.next = bigger[index];                    // push onto the front of the new bucket
                bigger[index] = n;
                n = next;
            }
        }
        table = bigger;
        resizeCount++;
    }

    public void forEach(BiConsumer<? super K, ? super V> action) {
        for (Node<K, V> head : table) {
            for (Node<K, V> n = head; n != null; n = n.next) {
                action.accept(n.key, n.value);
            }
        }
    }

    // ---- diagnostics: let us measure how healthy the hashing is ----

    public int size() { return size; }
    public int capacity() { return table.length; }
    public int resizeCount() { return resizeCount; }

    public int usedBuckets() {
        int used = 0;
        for (Node<K, V> head : table) {
            if (head != null) used++;
        }
        return used;
    }

    /** Length of the longest chain. 1-3 is healthy; hundreds means hashCode is broken. */
    public int longestChain() {
        int longest = 0;
        for (Node<K, V> head : table) {
            int length = 0;
            for (Node<K, V> n = head; n != null; n = n.next) length++;
            longest = Math.max(longest, length);
        }
        return longest;
    }
}
