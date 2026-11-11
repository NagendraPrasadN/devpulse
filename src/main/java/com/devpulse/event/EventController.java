package com.devpulse.event;

import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for events.
 *
 *   POST /api/events                         applications send an event   -> 202 Accepted
 *   GET  /api/projects/{id}/events           recent events of a project   -> 200
 *            ?minSeverity=ERROR&limit=50
 *
 * Two layers of validation protect the system:
 *   1. here: Bean Validation checks the SHAPE of the request (required fields, sizes) -> 400
 *   2. in the engine pipeline: EventValidator re-checks business rules on the worker thread
 */
@RestController
@RequestMapping("/api")
public class EventController {

    public record IngestEventRequest(
            @NotBlank String projectId,
            @NotBlank @Size(max = 100) String service,
            @NotNull Severity severity,
            @NotBlank @Size(max = 10_000) String message,
            @Size(max = 200) String errorType,
            Instant timestamp,                    // optional: defaults to "now" on the server
            Map<String, String> attributes) {     // optional
    }

    public record IngestResponse(UUID eventId, String status) {
    }

    public record EventResponse(UUID id, String projectId, String service, Severity severity,
                                String message, String errorType, Instant timestamp) {
        static EventResponse from(Event e) {
            return new EventResponse(e.id(), e.projectId(), e.service(), e.severity(),
                    e.message(), e.errorType().orElse(null), e.timestamp());
        }
    }

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    @PostMapping("/events")
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestEventRequest request) {
        UUID id = events.ingest(request);
        return ResponseEntity.accepted().body(new IngestResponse(id, "QUEUED"));
    }

    @GetMapping("/projects/{projectId}/events")
    public List<EventResponse> list(@PathVariable String projectId,
                                    @RequestParam(defaultValue = "INFO") Severity minSeverity,
                                    @RequestParam(defaultValue = "50") int limit) {
        return events.findByProject(projectId, minSeverity, limit).stream()
                .map(EventResponse::from)
                .toList();
    }
}
