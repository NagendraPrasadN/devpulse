package com.devpulse.project;

import com.devpulse.engine.exception.DevPulseException;

/** A project with this id is already registered. Becomes HTTP 409 in GlobalExceptionHandler. */
public class DuplicateProjectException extends DevPulseException {

    public DuplicateProjectException(String projectId) {
        super("Project already registered: " + projectId);
    }
}
