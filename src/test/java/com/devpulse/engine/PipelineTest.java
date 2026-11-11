package com.devpulse.engine;

import com.devpulse.engine.exception.ValidationException;
import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import com.devpulse.engine.pipeline.EventEnricher;
import com.devpulse.engine.pipeline.EventValidator;
import com.devpulse.engine.pipeline.Pipeline;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);   // time frozen: deterministic tests

    private final Pipeline pipeline = Pipeline.of(
            new EventValidator(fixedClock, id -> id.equals("shop")),
            new EventEnricher(fixedClock));

    private Event event(String project, String service, String message, Instant at, String errorType, Severity sev) {
        return new Event(UUID.randomUUID(), project, service, sev, message, errorType, at, Map.of());
    }

    @Test
    void validEventIsEnriched() {
        Event result = pipeline.process(event("shop", "  Payment-API ", "ok", NOW, null, Severity.ERROR));

        assertEquals("payment-api", result.service());                       // normalized
        assertEquals("UnknownError", result.errorType().orElseThrow());      // default type for errors
        assertEquals(NOW.toString(), result.attributes().get("receivedAt")); // stamped
    }

    @Test
    void allViolationsAreReportedTogether() {
        Event bad = event("ghost", " ", " ", NOW.plusSeconds(3600), null, Severity.INFO);

        ValidationException ex = assertThrows(ValidationException.class, () -> pipeline.process(bad));

        assertEquals(4, ex.violations().size());   // unknown project, blank service, blank message, future timestamp
    }

    @Test
    void longMessagesAreTruncated() {
        Event result = pipeline.process(event("shop", "api", "x".repeat(2_000), NOW, null, Severity.INFO));
        assertEquals(500, result.message().length());
    }

    @Test
    void pipelineIsItselfAProcessorSoItCanBeNested() {
        Pipeline outer = Pipeline.of(pipeline, e -> e.withAttribute("extra", "yes"));
        Event result = outer.process(event("shop", "api", "m", NOW, null, Severity.INFO));
        assertEquals("yes", result.attributes().get("extra"));
    }
}
