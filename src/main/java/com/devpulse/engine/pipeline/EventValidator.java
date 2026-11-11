package com.devpulse.engine.pipeline;

import com.devpulse.engine.exception.ValidationException;
import com.devpulse.engine.model.Event;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Step 1 of the pipeline: reject events that break business rules.
 *
 * It COLLECTS every violation and throws once at the end, so the sender learns about
 * all problems in one go. (Fail-fast is still respected: an invalid event never
 * gets past this class.)
 *
 * The Clock and the project-lookup Predicate are passed in (dependency injection by
 * hand!) instead of calling Instant.now() / a global registry directly. That makes the
 * class testable: tests can pass a fixed Clock or "id -> true". Spring does the same
 * thing automatically in Phase 2.
 */
public final class EventValidator implements EventProcessor {

    private static final int MAX_MESSAGE_LENGTH = 10_000;
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final Clock clock;
    private final Predicate<String> projectExists;

    public EventValidator(Clock clock, Predicate<String> projectExists) {
        this.clock = clock;
        this.projectExists = projectExists;
    }

    @Override
    public Event process(Event event) {
        List<String> violations = new ArrayList<>();

        if (event.projectId().isBlank()) {
            violations.add("projectId must not be blank");
        } else if (!projectExists.test(event.projectId())) {
            violations.add("unknown project '" + event.projectId() + "'");
        }
        if (event.service().isBlank()) {
            violations.add("service must not be blank");
        }
        if (event.message().isBlank()) {
            violations.add("message must not be blank");
        }
        if (event.message().length() > MAX_MESSAGE_LENGTH) {
            violations.add("message longer than " + MAX_MESSAGE_LENGTH + " characters");
        }
        if (event.timestamp().isAfter(clock.instant().plus(MAX_CLOCK_SKEW))) {
            violations.add("timestamp is in the future");
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(event.id(), violations);
        }
        return event; // valid: pass it along unchanged
    }
}
