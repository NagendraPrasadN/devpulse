package com.devpulse.engine;

import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private Event eventWithId(UUID id, String message) {
        return new Event(id, "shop", "api", Severity.INFO, message, null, now, Map.of());
    }

    @Test
    void eventsWithSameIdAreEqualAndHaveSameHashCode() {
        UUID id = UUID.randomUUID();
        Event a = eventWithId(id, "first message");
        Event b = eventWithId(id, "different message");

        assertEquals(a, b);                              // equals contract
        assertEquals(a.hashCode(), b.hashCode());        // hashCode contract
        assertEquals(1, new HashSet<>(Set.of(a)).stream().filter(b::equals).count());
    }

    @Test
    void eventsWithDifferentIdsAreNotEqual() {
        assertNotEquals(eventWithId(UUID.randomUUID(), "x"), eventWithId(UUID.randomUUID(), "x"));
    }

    @Test
    void withersReturnNewEventAndLeaveOriginalUntouched() {
        Event original = eventWithId(UUID.randomUUID(), "hello");
        Event changed = original.withMessage("changed");

        assertEquals("hello", original.message());
        assertEquals("changed", changed.message());
        assertEquals(original.id(), changed.id());
    }

    @Test
    void attributesAreDefensivelyCopied() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("env", "prod");
        Event event = new Event(UUID.randomUUID(), "shop", "api", Severity.INFO, "m", null, now, mutable);

        mutable.put("env", "hacked");                    // caller changes its own map afterwards

        assertEquals("prod", event.attributes().get("env"));
        assertThrows(UnsupportedOperationException.class, () -> event.attributes().put("a", "b"));
    }

    @Test
    void nullRequiredFieldFailsFast() {
        assertThrows(NullPointerException.class,
                () -> new Event(UUID.randomUUID(), null, "api", Severity.INFO, "m", null, now, Map.of()));
    }
}
