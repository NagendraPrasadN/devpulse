package com.devpulse.engine.incident;

import com.devpulse.engine.exception.EntityNotFoundException;
import com.devpulse.engine.fingerprint.ErrorFingerprint;
import com.devpulse.engine.fingerprint.RepeatedError;
import com.devpulse.engine.repo.InMemoryRepository;
import com.devpulse.engine.repo.Repository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Owns incidents: opens them when repeated errors are detected, moves them through
 * their lifecycle, and tells listeners what happened.
 *
 * KEY RULE: at most ONE active incident per ErrorFingerprint.
 *  When the 6th identical error arrives while an incident is open, we do not open
 *  another one; we just count the occurrence.
 *
 * RACE-CONDITION NOTE:
 *  Two worker threads can detect the same fingerprint at the same instant. If both did
 *  "if (!active.containsKey(fp)) active.put(fp, incident)" we'd open two incidents
 *  (check-then-act race). putIfAbsent does check+insert as ONE atomic step, so exactly one
 *  thread "wins" and only the winner notifies.
 */
public final class IncidentManager {

    private final Repository<Incident, UUID> incidents = new InMemoryRepository<>(Incident::id);
    private final ConcurrentHashMap<ErrorFingerprint, Incident> activeByFingerprint = new ConcurrentHashMap<>();
    // CopyOnWriteArrayList: iteration is lock-free and safe while others register; ideal for
    // "few writes (registering listeners), many reads (notifying)".
    private final List<IncidentListener> listeners = new CopyOnWriteArrayList<>();
    private final Clock clock;

    public IncidentManager(Clock clock) {
        this.clock = clock;
    }

    public void addListener(IncidentListener listener) {
        listeners.add(listener);
    }

    /** @return the new incident, or empty if one was already active for this error */
    public Optional<Incident> onRepeatedError(RepeatedError repeated) {
        ErrorFingerprint fingerprint = repeated.fingerprint();

        Incident existing = activeByFingerprint.get(fingerprint);
        if (existing != null) {
            existing.recordOccurrence();
            return Optional.empty();
        }

        Incident candidate = Incident.open(repeated, clock);
        Incident winner = activeByFingerprint.putIfAbsent(fingerprint, candidate); // atomic
        if (winner != null) {                 // another thread beat us to it
            winner.recordOccurrence();
            return Optional.empty();
        }

        incidents.save(candidate);
        fire(listener -> listener.onOpened(candidate));
        return Optional.of(candidate);
    }

    public Incident acknowledge(UUID incidentId, String by) {
        Incident incident = require(incidentId);
        IncidentStatus previous = incident.acknowledge(by);   // may throw IncidentStateException
        fire(listener -> listener.onStatusChanged(incident, previous));
        return incident;
    }

    public Incident resolve(UUID incidentId) {
        Incident incident = require(incidentId);
        IncidentStatus previous = incident.resolve();         // may throw IncidentStateException
        activeByFingerprint.remove(incident.fingerprint(), incident); // next occurrence may open a fresh incident
        fire(listener -> listener.onStatusChanged(incident, previous));
        return incident;
    }

    public List<Incident> findAll() {
        return incidents.findAll();
    }

    public List<Incident> findActive() {
        return incidents.findWhere(incident -> incident.status().isActive());
    }

    private Incident require(UUID id) {
        return incidents.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Incident", id));
    }

    /** A broken listener must never break incident handling or other listeners. */
    private void fire(Consumer<IncidentListener> action) {
        for (IncidentListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (RuntimeException ex) {
                System.err.println("[IncidentManager] listener failed: " + ex);
            }
        }
    }
}
