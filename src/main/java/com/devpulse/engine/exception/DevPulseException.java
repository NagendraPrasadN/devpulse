package com.devpulse.engine.exception;

/**
 * Root of all DevPulse *unchecked* exceptions.
 *
 * WHY UNCHECKED (extends RuntimeException)?
 *  - These represent problems the caller usually cannot "recover" from locally:
 *    bad input, illegal state, missing entity. Forcing every method to declare
 *    them with "throws" would only add noise.
 *  - One common base type lets a caller write a single
 *    catch (DevPulseException e) when it wants to handle "anything DevPulse-specific".
 *
 * The one deliberate exception is NotificationException (checked) - see that class.
 */
public abstract class DevPulseException extends RuntimeException {

    protected DevPulseException(String message) {
        super(message);
    }

    protected DevPulseException(String message, Throwable cause) {
        super(message, cause);
    }
}
