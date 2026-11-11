package com.devpulse.engine;

import com.devpulse.engine.concurrent.EngineConfig;
import com.devpulse.engine.concurrent.EventEngine;
import com.devpulse.engine.fingerprint.RepeatedErrorDetector;
import com.devpulse.engine.incident.IncidentManager;
import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import com.devpulse.engine.pipeline.EventEnricher;
import com.devpulse.engine.pipeline.EventValidator;
import com.devpulse.engine.pipeline.Pipeline;
import com.devpulse.engine.store.EventStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class EventEngineTest {

    private static final int PRODUCERS = 4;
    private static final int EVENTS_EACH = 500;

    @Test
    void everyAcceptedEventIsProcessedExactlyOnceUnderConcurrency() throws Exception {
        Clock clock = Clock.systemUTC();
        EventStore store = new EventStore();
        IncidentManager incidents = new IncidentManager(clock);
        EventEngine engine = new EventEngine(
                new EngineConfig(1_000, 4, 5, Duration.ofSeconds(10)),
                Pipeline.of(new EventValidator(clock, id -> true), new EventEnricher(clock)),
                store,
                new RepeatedErrorDetector(5, Duration.ofSeconds(10)),
                incidents);
        engine.start();

        Thread[] producers = new Thread[PRODUCERS];
        for (int p = 0; p < PRODUCERS; p++) {
            producers[p] = new Thread(() -> {
                try {
                    for (int i = 0; i < EVENTS_EACH; i++) {
                        // every event is an identical error -> exactly ONE incident must result
                        engine.submit(Event.create("shop", "api", Severity.ERROR, "Timeout after " + i + "ms", "TimeoutException"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            producers[p].start();
        }
        for (Thread t : producers) t.join();
        engine.shutdown();

        long expected = (long) PRODUCERS * EVENTS_EACH;
        assertEquals(expected, engine.acceptedCount());
        assertEquals(expected, engine.processedCount());
        assertEquals(expected, store.count());
        assertEquals(0, engine.failedCount());
        assertEquals(1, incidents.findAll().size());     // check-then-act race would give > 1
    }

    @Test
    void submitAfterShutdownIsRejected() throws Exception {
        Clock clock = Clock.systemUTC();
        EventEngine engine = new EventEngine(EngineConfig.defaults(),
                Pipeline.of(new EventEnricher(clock)), new EventStore(),
                new RepeatedErrorDetector(5, Duration.ofSeconds(5)), new IncidentManager(clock));
        engine.start();
        engine.shutdown();

        assertThrows(IllegalStateException.class,
                () -> engine.submit(Event.create("shop", "api", Severity.INFO, "late", null)));
    }
}
