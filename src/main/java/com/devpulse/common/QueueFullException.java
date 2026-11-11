package com.devpulse.common;

import com.devpulse.engine.exception.DevPulseException;

/** The engine could not accept the event right now (queue full, or the request thread was interrupted). */
public class QueueFullException extends DevPulseException {

    public QueueFullException(String message) {
        super(message);
    }
}
