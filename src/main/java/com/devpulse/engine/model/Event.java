package com.devpulse.engine.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One thing an application tells us: a log line, a failure, a heartbeat.
 *
 * ===== IMMUTABILITY =====
 * Events travel between threads (producer -> queue -> worker). If an event could be
 * changed after creation, every thread would have to coordinate. An immutable object
 * is automatically thread-safe: nobody can change it, so nobody can disagree about it.
 *
 * The recipe used below:
 *   1. class is final            -> nobody can subclass it and add mutability
 *   2. all fields private final  -> assigned exactly once, in the constructor
 *   3. no setters                -> "changes" are done with with...() methods that return a NEW Event
 *   4. mutable inputs (the Map) are copied in the constructor (defensive copy)
 *
 * ===== equals / hashCode CONTRACT =====
 * Two events are "the same event" if they have the same id (the id identifies the
 * event; two different events can have identical messages). Rules the contract demands:
 *   - equals is reflexive, symmetric, transitive, consistent
 *   - if a.equals(b) then a.hashCode() == b.hashCode()
 * Because we base BOTH on the same field (id), the contract holds automatically.
 * If you override equals without hashCode, HashMap/HashSet will silently misbehave
 * (see HashMapDemo).
 */
public final class Event {

    private final UUID id;
    private final String projectId;
    private final String service;
    private final Severity severity;
    private final String message;
    private final String errorType;            // nullable: only failures usually have one
    private final Instant timestamp;
    private final Map<String, String> attributes;

    public Event(UUID id, String projectId, String service, Severity severity,
                 String message, String errorType, Instant timestamp,
                 Map<String, String> attributes) {
        // Fail fast: null here is a programming bug, so throw immediately (NullPointerException).
        // Business rules (blank text, future timestamps...) are checked later by EventValidator.
        this.id = Objects.requireNonNull(id, "id");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.service = Objects.requireNonNull(service, "service");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.message = Objects.requireNonNull(message, "message");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.errorType = errorType;
        // Map.copyOf = defensive copy AND unmodifiable. The caller's map can change later; ours can't.
        this.attributes = Map.copyOf(attributes);
    }

    /** Convenience factory: new random id, timestamp = now, no attributes. */
    public static Event create(String projectId, String service, Severity severity,
                               String message, String errorType) {
        return new Event(UUID.randomUUID(), projectId, service, severity,
                message, errorType, Instant.now(), Map.of());
    }

    // ---- accessors (record-style names: id() instead of getId()) ----

    public UUID id() { return id; }
    public String projectId() { return projectId; }
    public String service() { return service; }
    public Severity severity() { return severity; }
    public String message() { return message; }
    public Instant timestamp() { return timestamp; }
    public Map<String, String> attributes() { return attributes; } // already unmodifiable

    /** Optional makes "might be absent" visible in the type instead of hiding a null. */
    public Optional<String> errorType() {
        return Optional.ofNullable(errorType);
    }

    // ---- "withers": immutable updates. Each returns a NEW Event; this one is untouched ----

    public Event withService(String newService) {
        return new Event(id, projectId, newService, severity, message, errorType, timestamp, attributes);
    }

    public Event withMessage(String newMessage) {
        return new Event(id, projectId, service, severity, newMessage, errorType, timestamp, attributes);
    }

    public Event withErrorType(String newErrorType) {
        return new Event(id, projectId, service, severity, message, newErrorType, timestamp, attributes);
    }

    public Event withAttribute(String key, String value) {
        Map<String, String> copy = new LinkedHashMap<>(attributes);
        copy.put(key, value);
        return new Event(id, projectId, service, severity, message, errorType, timestamp, copy);
    }

    // ---- identity ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                        // same reference: trivially equal
        if (!(o instanceof Event other)) return false;     // also handles null; pattern matching (Java 16+)
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();                              // same field as equals -> contract holds
    }

    @Override
    public String toString() {
        return "Event[" + severity + " " + projectId + "/" + service + " '" + message + "']";
    }
}
