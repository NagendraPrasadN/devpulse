package com.devpulse.engine;

import com.devpulse.engine.exception.EntityNotFoundException;
import com.devpulse.engine.exception.IncidentStateException;
import com.devpulse.engine.fingerprint.ErrorFingerprint;
import com.devpulse.engine.fingerprint.RepeatedError;
import com.devpulse.engine.incident.Incident;
import com.devpulse.engine.incident.IncidentManager;
import com.devpulse.engine.incident.IncidentStatus;
import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IncidentTest {

    private RepeatedError repeated(String service) {
        Event latest = new Event(UUID.randomUUID(), "shop", service, Severity.ERROR, "boom 1", "IOException",
                Instant.now(), Map.of());
        return new RepeatedError(ErrorFingerprint.from(latest), 5, latest);
    }

    @Test
    void stateMachineAllowsOnlyLegalMoves() {
        assertTrue(IncidentStatus.OPEN.canTransitionTo(IncidentStatus.ACKNOWLEDGED));
        assertTrue(IncidentStatus.OPEN.canTransitionTo(IncidentStatus.RESOLVED));
        assertTrue(IncidentStatus.ACKNOWLEDGED.canTransitionTo(IncidentStatus.RESOLVED));
        assertFalse(IncidentStatus.ACKNOWLEDGED.canTransitionTo(IncidentStatus.OPEN));
        assertFalse(IncidentStatus.RESOLVED.canTransitionTo(IncidentStatus.OPEN));
    }

    @Test
    void illegalTransitionThrowsAndKeepsState() {
        Incident incident = Incident.open(repeated("api"), Clock.systemUTC());
        incident.resolve();

        assertThrows(IncidentStateException.class, () -> incident.acknowledge("alice"));
        assertEquals(IncidentStatus.RESOLVED, incident.status());
    }

    @Test
    void secondDetectionOfSameErrorDoesNotOpenAnotherIncident() {
        IncidentManager manager = new IncidentManager(Clock.systemUTC());

        assertTrue(manager.onRepeatedError(repeated("api")).isPresent());
        assertTrue(manager.onRepeatedError(repeated("api")).isEmpty());   // same fingerprint, still active

        assertEquals(1, manager.findAll().size());
        assertEquals(6, manager.findActive().get(0).occurrences());       // 5 + 1 recorded
    }

    @Test
    void resolvingAllowsAFreshIncidentForTheSameError() {
        IncidentManager manager = new IncidentManager(Clock.systemUTC());
        Incident first = manager.onRepeatedError(repeated("api")).orElseThrow();
        manager.resolve(first.id());

        assertTrue(manager.onRepeatedError(repeated("api")).isPresent());
        assertEquals(2, manager.findAll().size());
    }

    @Test
    void listenersAreNotifiedAndABrokenListenerDoesNotBreakOthers() {
        IncidentManager manager = new IncidentManager(Clock.systemUTC());
        List<String> seen = new ArrayList<>();
        manager.addListener(new com.devpulse.engine.incident.IncidentListener() {
            @Override public void onOpened(Incident incident) { throw new IllegalStateException("broken listener"); }
        });
        manager.addListener(incident -> seen.add("opened"));   // lambda works: only one abstract method

        manager.onRepeatedError(repeated("api"));

        assertEquals(List.of("opened"), seen);
    }

    @Test
    void unknownIncidentIdThrowsNotFound() {
        IncidentManager manager = new IncidentManager(Clock.systemUTC());
        assertThrows(EntityNotFoundException.class, () -> manager.resolve(UUID.randomUUID()));
    }
}
