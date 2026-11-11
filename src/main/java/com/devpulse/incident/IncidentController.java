package com.devpulse.incident;

import com.devpulse.engine.incident.Incident;
import com.devpulse.engine.incident.IncidentStatus;
import com.devpulse.engine.model.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST API for incidents.
 *
 *   GET  /api/incidents?activeOnly=true       list (newest first)
 *   GET  /api/incidents/{id}                  one incident, or 404
 *   POST /api/incidents/{id}/acknowledge      body {"by": "dave"}  -> 200, or 409 if the move is illegal
 *   POST /api/incidents/{id}/resolve          -> 200, or 409 if already resolved
 *
 * State changes are POSTs to an "action" URL, not PUT/PATCH of a status field, because they are
 * commands with rules (the state machine), not plain edits.
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    public record AcknowledgeRequest(@NotBlank String by) {
    }

    public record IncidentResponse(UUID id, String projectId, String service, String errorType,
                                   String title, Severity severity, IncidentStatus status,
                                   int occurrences, Instant openedAt, String acknowledgedBy,
                                   Instant resolvedAt) {
        static IncidentResponse from(Incident i) {
            return new IncidentResponse(i.id(), i.fingerprint().projectId(), i.fingerprint().service(),
                    i.fingerprint().errorType(), i.title(), i.severity(), i.status(),
                    i.occurrences(), i.openedAt(), i.acknowledgedBy(), i.resolvedAt());
        }
    }

    private final IncidentService incidents;

    public IncidentController(IncidentService incidents) {
        this.incidents = incidents;
    }

    @GetMapping
    public List<IncidentResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return incidents.list(activeOnly).stream().map(IncidentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@PathVariable UUID id) {
        return IncidentResponse.from(incidents.get(id));
    }

    @PostMapping("/{id}/acknowledge")
    public IncidentResponse acknowledge(@PathVariable UUID id, @Valid @RequestBody AcknowledgeRequest request) {
        return IncidentResponse.from(incidents.acknowledge(id, request.by()));
    }

    @PostMapping("/{id}/resolve")
    public IncidentResponse resolve(@PathVariable UUID id) {
        return IncidentResponse.from(incidents.resolve(id));
    }
}
