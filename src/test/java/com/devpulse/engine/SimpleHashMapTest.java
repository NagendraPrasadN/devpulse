package com.devpulse.engine;

import com.devpulse.engine.store.SimpleHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleHashMapTest {

    /** Every instance collides on purpose. */
    private record CollidingKey(int id) {
        @Override public int hashCode() { return 7; }
    }

    @Test
    void putGetAndReplace() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        assertNull(map.put("a", 1));
        assertEquals(1, map.put("a", 2));          // returns the old value
        assertEquals(2, map.get("a"));
        assertEquals(1, map.size());
    }

    @Test
    void missingKeyReturnsNull() {
        assertNull(new SimpleHashMap<String, Integer>().get("nope"));
    }

    @Test
    void resizesAndKeepsEveryEntry() {
        SimpleHashMap<Integer, Integer> map = new SimpleHashMap<>();
        for (int i = 0; i < 1_000; i++) map.put(i, i * 2);

        assertEquals(1_000, map.size());
        assertTrue(map.capacity() > 16);
        assertTrue(map.resizeCount() > 0);
        for (int i = 0; i < 1_000; i++) assertEquals(i * 2, map.get(i));
    }

    @Test
    void collidingKeysShareOneBucketButStillWork() {
        SimpleHashMap<CollidingKey, String> map = new SimpleHashMap<>();
        for (int i = 0; i < 50; i++) map.put(new CollidingKey(i), "v" + i);

        assertEquals(50, map.longestChain());      // all in one chain
        assertEquals("v33", map.get(new CollidingKey(33)));
    }

    @Test
    void removeWorksForHeadMiddleAndMissing() {
        SimpleHashMap<CollidingKey, String> map = new SimpleHashMap<>();
        for (int i = 0; i < 3; i++) map.put(new CollidingKey(i), "v" + i);

        assertEquals("v0", map.remove(new CollidingKey(0)));   // head of chain
        assertEquals("v1", map.remove(new CollidingKey(1)));   // new head
        assertNull(map.remove(new CollidingKey(99)));          // not present
        assertEquals(1, map.size());
        assertEquals("v2", map.get(new CollidingKey(2)));
    }

    @Test
    void nullKeyIsRejected() {
        assertThrows(NullPointerException.class, () -> new SimpleHashMap<String, String>().put(null, "x"));
    }
}
