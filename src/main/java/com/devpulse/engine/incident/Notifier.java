package com.devpulse.engine.incident;

import com.devpulse.engine.exception.NotificationException;

/**
 * Something that can deliver a text message somewhere (console, file, later email/Slack).
 * Declares the CHECKED NotificationException: delivery can fail and callers must plan for it.
 */
public interface Notifier {

    void send(String message) throws NotificationException;
}
