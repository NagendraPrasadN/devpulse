package com.devpulse.engine.pipeline;

import com.devpulse.engine.model.Event;

import java.time.Clock;
import java.util.Locale;

/**
 * Step 2 of the pipeline: clean up and add information to a VALID event.
 *
 *  - normalizes the service name ("Payment-Service " and "payment-service" are one service)
 *  - stamps when the platform received the event
 *  - truncates very long messages (protects memory and fingerprinting)
 *  - gives failures without an errorType a default, so grouping never meets "no type"
 *
 * Event is immutable, so every step returns a NEW Event. Reassigning the local
 * variable "e" is fine: we are replacing our own reference, not mutating an object.
 */
public final class EventEnricher implements EventProcessor {

    private static final int MAX_STORED_MESSAGE_LENGTH = 500;

    private final Clock clock;

    public EventEnricher(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Event process(Event event) {
        Event e = event.withService(event.service().trim().toLowerCase(Locale.ROOT));
        e = e.withAttribute("receivedAt", clock.instant().toString());

        if (e.message().length() > MAX_STORED_MESSAGE_LENGTH) {
            e = e.withMessage(e.message().substring(0, MAX_STORED_MESSAGE_LENGTH));
        }
        if (e.severity().isError() && e.errorType().isEmpty()) {
            e = e.withErrorType("UnknownError");
        }
        return e;
    }
}
