package com.devpulse.engine.exception;

/** Thrown when a lookup by id finds nothing and the caller required a result. */
public class EntityNotFoundException extends DevPulseException {

    public EntityNotFoundException(String entityType, Object id) {
        super(entityType + " not found: " + id);
    }
}
