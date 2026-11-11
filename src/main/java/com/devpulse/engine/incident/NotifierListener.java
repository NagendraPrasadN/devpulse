package com.devpulse.engine.incident;

import com.devpulse.engine.exception.NotificationException;

/**
 * ADAPTER: connects the observer world (IncidentListener) to the delivery world (Notifier).
 *
 * This is where the checked NotificationException finally gets HANDLED. A failed
 * notification is logged and swallowed: it must not stop the engine from processing events.
 * (Swallowing is only acceptable because we deliberately decided so and log it.)
 */
public final class NotifierListener implements IncidentListener {

    private final Notifier notifier;

    public NotifierListener(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public void onOpened(Incident incident) {
        deliver("INCIDENT OPENED [" + incident.severity() + "] " + incident.title()
                + " (id=" + incident.id() + ")");
    }

    @Override
    public void onStatusChanged(Incident incident, IncidentStatus previous) {
        deliver("INCIDENT " + incident.id() + ": " + previous + " -> " + incident.status());
    }

    private void deliver(String message) {
        try {
            notifier.send(message);
        } catch (NotificationException e) {
            System.err.println("[NotifierListener] delivery failed: " + e.getMessage()
                    + " (cause: " + e.getCause() + ")");
        }
    }
}
