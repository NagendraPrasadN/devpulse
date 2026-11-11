package com.devpulse.engine.incident;

/**
 * OBSERVER pattern: the IncidentManager (the "subject") does not know who cares about
 * incidents. Anyone who does implements this interface and registers itself.
 * Adding email/Slack later = a new listener, zero changes to the manager.
 */
public interface IncidentListener {

    void onOpened(Incident incident);

    /** default method: listeners that don't care about status changes needn't implement it. */
    default void onStatusChanged(Incident incident, IncidentStatus previous) {
    }
}
