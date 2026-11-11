package com.devpulse.engine.exception;

import java.util.List;
import java.util.UUID;

/**
 * Thrown when an incoming event breaks one or more business rules.
 * It carries ALL violations (not just the first) so the sender can fix everything at once.
 */
public class ValidationException extends DevPulseException {

    private final UUID eventId;
    private final List<String> violations;

    public ValidationException(UUID eventId, List<String> violations) {
        super("Event " + eventId + " rejected: " + violations);
        this.eventId = eventId;
        this.violations = List.copyOf(violations); // immutable snapshot
    }

    public UUID eventId() {
        return eventId;
    }

    public List<String> violations() {
        return violations;
    }
}
