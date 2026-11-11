package com.devpulse.engine.incident;

import com.devpulse.engine.exception.IncidentStateException;
import com.devpulse.engine.fingerprint.ErrorFingerprint;
import com.devpulse.engine.fingerprint.RepeatedError;
import com.devpulse.engine.model.Severity;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A problem that needs a human: "this error keeps happening".
 *
 * IMMUTABILITY vs CONTROLLED MUTATION
 *  Event is fully immutable. Incident cannot be: its whole purpose is to move through
 *  states over time. So we split fields into two groups:
 *    - identity & origin (id, fingerprint, title, severity, openedAt) -> final, never change
 *    - lifecycle (status, occurrences, acknowledgedBy, resolvedAt)   -> private, changeable
 *      ONLY through methods that enforce the rules (no public setters).
 *
 * Mutable state can be touched by several threads (workers add occurrences while a human
 * acknowledges), so every method touching it is synchronized: only one thread at a time
 * per Incident object. "synchronized" on an instance method locks on `this`.
 */
public final class Incident {

    private final UUID id;
    private final ErrorFingerprint fingerprint;
    private final String title;
    private final Severity severity;
    private final Instant openedAt;

    private IncidentStatus status = IncidentStatus.OPEN;
    private int occurrences;
    private String acknowledgedBy;
    private Instant resolvedAt;
    private final Clock clock;

    private Incident(UUID id, ErrorFingerprint fingerprint, String title,
                     Severity severity, int occurrences, Clock clock) {
        this.id = id;
        this.fingerprint = fingerprint;
        this.title = title;
        this.severity = severity;
        this.occurrences = occurrences;
        this.clock = clock;
        this.openedAt = clock.instant();
    }

    /** Factory: builds an incident from a detection. Private constructor keeps creation in one place. */
    public static Incident open(RepeatedError repeated, Clock clock) {
        ErrorFingerprint fp = repeated.fingerprint();
        String title = "Repeated error in " + fp.projectId() + "/" + fp.service()
                + ": " + fp.errorType() + " (" + repeated.occurrencesInWindow() + " occurrences in window)";
        return new Incident(UUID.randomUUID(), fp, title, repeated.latest().severity(),
                repeated.occurrencesInWindow(), clock);
    }

    // ---- state transitions (the only way status changes) ----

    /** @return the previous status */
    public synchronized IncidentStatus acknowledge(String by) {
        Objects.requireNonNull(by, "by");
        IncidentStatus previous = transitionTo(IncidentStatus.ACKNOWLEDGED);
        this.acknowledgedBy = by;
        return previous;
    }

    /** @return the previous status */
    public synchronized IncidentStatus resolve() {
        IncidentStatus previous = transitionTo(IncidentStatus.RESOLVED);
        this.resolvedAt = clock.instant();
        return previous;
    }

    /** Called by the manager when the same error keeps arriving while the incident is active. */
    public synchronized void recordOccurrence() {
        if (status.isActive()) {
            occurrences++;
        }
    }

    private IncidentStatus transitionTo(IncidentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IncidentStateException(id, status, target); // state unchanged: fail fast
        }
        IncidentStatus previous = status;
        status = target;
        return previous;
    }

    // ---- read access ----

    public UUID id() { return id; }
    public ErrorFingerprint fingerprint() { return fingerprint; }
    public String title() { return title; }
    public Severity severity() { return severity; }
    public Instant openedAt() { return openedAt; }

    public synchronized IncidentStatus status() { return status; }
    public synchronized int occurrences() { return occurrences; }
    public synchronized String acknowledgedBy() { return acknowledgedBy; }
    public synchronized Instant resolvedAt() { return resolvedAt; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Incident other && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Incident[" + id.toString().substring(0, 8) + " " + status() + " x" + occurrences() + " " + title + "]";
    }
}
