package com.devpulse.engine.exception;

import com.devpulse.engine.incident.IncidentStatus;

import java.util.UUID;

/** Thrown when someone tries an illegal state transition, e.g. RESOLVED -> ACKNOWLEDGED. */
public class IncidentStateException extends DevPulseException {

    public IncidentStateException(UUID incidentId, IncidentStatus from, IncidentStatus to) {
        super("Incident " + incidentId + " cannot move from " + from + " to " + to);
    }
}
