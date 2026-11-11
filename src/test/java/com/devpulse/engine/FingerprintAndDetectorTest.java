package com.devpulse.engine;

import com.devpulse.engine.fingerprint.ErrorFingerprint;
import com.devpulse.engine.fingerprint.RepeatedErrorDetector;
import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FingerprintAndDetectorTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private Event error(String message, Instant at) {
        return new Event(UUID.randomUUID(), "shop", "api", Severity.ERROR, message, "TimeoutException", at, Map.of());
    }

    @Test
    void messagesDifferingOnlyInNumbersShareAFingerprint() {
        ErrorFingerprint a = ErrorFingerprint.from(error("Timeout after 3021ms", T0));
        ErrorFingerprint b = ErrorFingerprint.from(error("Timeout after 87ms", T0));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals("Timeout after <n>ms", a.normalizedMessage());
    }

    @Test
    void uuidsAreNormalizedBeforeNumbers() {
        Event e = error("Order 123e4567-e89b-12d3-a456-426614174000 failed at 0xFF", T0);
        assertEquals("Order <uuid> failed at <hex>", ErrorFingerprint.from(e).normalizedMessage());
    }

    @Test
    void differentErrorTypesAreDifferentFingerprints() {
        Event a = new Event(UUID.randomUUID(), "shop", "api", Severity.ERROR, "boom", "A", T0, Map.of());
        Event b = new Event(UUID.randomUUID(), "shop", "api", Severity.ERROR, "boom", "B", T0, Map.of());
        assertNotEquals(ErrorFingerprint.from(a), ErrorFingerprint.from(b));
    }

    @Test
    void detectsOnlyWhenThresholdReachedInsideWindow() {
        RepeatedErrorDetector detector = new RepeatedErrorDetector(3, Duration.ofSeconds(10));

        assertTrue(detector.observe(error("fail 1", T0)).isEmpty());
        assertTrue(detector.observe(error("fail 2", T0.plusSeconds(1))).isEmpty());
        var detected = detector.observe(error("fail 3", T0.plusSeconds(2)));

        assertTrue(detected.isPresent());
        assertEquals(3, detected.get().occurrencesInWindow());
    }

    @Test
    void oldOccurrencesSlideOutOfTheWindow() {
        RepeatedErrorDetector detector = new RepeatedErrorDetector(3, Duration.ofSeconds(10));

        detector.observe(error("fail 1", T0));
        detector.observe(error("fail 2", T0.plusSeconds(1)));
        // 20s later: the first two are outside the 10s window, so this is occurrence #1 again
        assertTrue(detector.observe(error("fail 3", T0.plusSeconds(20))).isEmpty());
    }

    @Test
    void nonErrorsAreIgnored() {
        RepeatedErrorDetector detector = new RepeatedErrorDetector(1, Duration.ofSeconds(10));
        Event info = new Event(UUID.randomUUID(), "shop", "api", Severity.INFO, "fine", null, T0, Map.of());
        assertTrue(detector.observe(info).isEmpty());
    }
}
