package com.devpulse.common;

import com.devpulse.engine.exception.EntityNotFoundException;
import com.devpulse.engine.exception.IncidentStateException;
import com.devpulse.engine.exception.ValidationException;
import com.devpulse.project.DuplicateProjectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ONE place that turns exceptions into HTTP responses, for every controller.
 *
 * Controllers and services just throw meaningful exceptions (EntityNotFoundException, ...).
 * They never build error responses. This class decides the status code and body.
 *
 * Body format: ProblemDetail = the standard RFC 7807 "application/problem+json" shape
 *   { "type": ..., "title": ..., "status": 404, "detail": "Project not found: x" }
 *
 * Status codes used:
 *   400 Bad Request            malformed JSON, failed bean validation, wrong parameter type
 *   404 Not Found              something that was asked for does not exist
 *   409 Conflict               valid request, but clashes with current state (duplicate, illegal transition)
 *   422 Unprocessable Entity   well-formed request that breaks a business rule
 *   503 Service Unavailable    engine queue is full; the client should retry later
 *   500 Internal Server Error  our bug; details are logged, NOT sent to the client
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex) {
        // field name -> first message, e.g. {"name": "must not be blank"}
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "Request body has invalid fields");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Body is missing, is not valid JSON, or contains an invalid value (e.g. unknown severity)");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Parameter '" + ex.getName() + "' has invalid value '" + ex.getValue() + "'");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler({DuplicateProjectException.class, IncidentStateException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleBusinessValidation(ValidationException ex) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Event rejected", ex.getMessage());
        problem.setProperty("violations", ex.violations());
        return problem;
    }

    @ExceptionHandler(QueueFullException.class)
    public ProblemDetail handleQueueFull(QueueFullException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Temporarily unavailable", ex.getMessage());
    }

    /**
     * Safety net for everything unexpected.
     * IMPORTANT: Spring's own exceptions (405 Method Not Allowed, 404 no such URL, 415 ...)
     * implement ErrorResponse and already know their status. Without the first check, this
     * catch-all would turn them all into 500s.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(errorResponse.getBody());
        }
        log.error("Unexpected error", ex);   // full stack trace stays in OUR logs
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                        "Something went wrong on our side"));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
