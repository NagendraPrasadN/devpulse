package com.devpulse.engine.exception;

/**
 * The ONE checked exception in the engine (extends Exception, NOT RuntimeException).
 *
 * WHY CHECKED HERE?
 *  Sending a notification talks to the outside world (file, network, email later).
 *  That can fail for reasons outside our control, and the caller MUST decide what to do
 *  (retry, log, fall back). Making it checked forces that decision at compile time.
 *
 * Note it deliberately does not extend DevPulseException, because that is unchecked.
 */
public class NotificationException extends Exception {

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
