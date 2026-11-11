package com.devpulse.event;

import com.devpulse.common.QueueFullException;
import com.devpulse.engine.concurrent.EventEngine;
import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import com.devpulse.engine.repo.EventRules;
import com.devpulse.engine.store.EventStore;
import com.devpulse.project.ProjectService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bridges the web layer and the Phase 1 engine.
 *
 * INGEST is ASYNCHRONOUS: we check the project exists, build the Event and put it on the
 * engine's queue, then return immediately. A worker thread validates, enriches, stores and
 * analyses it a moment later. That is why the HTTP answer is 202 Accepted ("received, not yet
 * processed"), not 201 Created. A GET right after a POST may not show the event yet.
 */
@Service
public class EventService {

    private static final int MAX_LIMIT = 500;

    private final ProjectService projects;
    private final EventEngine engine;
    private final EventStore store;
    private final Clock clock;

    public EventService(ProjectService projects, EventEngine engine, EventStore store, Clock clock) {
        this.projects = projects;
        this.engine = engine;
        this.store = store;
        this.clock = clock;
    }

    /** @return the id of the queued event */
    public UUID ingest(EventController.IngestEventRequest request) {
        projects.get(request.projectId());   // throws EntityNotFoundException -> 404 if unregistered

        Event event = new Event(
                UUID.randomUUID(),
                request.projectId(),
                request.service(),
                request.severity(),
                request.message(),
                request.errorType(),
                request.timestamp() != null ? request.timestamp() : clock.instant(),
                cleanAttributes(request.attributes()));

        try {
            if (!engine.submit(event)) {
                throw new QueueFullException("Event queue is full, retry shortly");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // restore the flag; never swallow an interrupt
            throw new QueueFullException("Ingestion was interrupted");
        }
        return event.id();
    }

    /** Newest first, at least {@code minSeverity}, at most {@code limit}. */
    public List<Event> findByProject(String projectId, Severity minSeverity, int limit) {
        projects.get(projectId);             // 404 for an unknown project
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return store.query(EventRules.forProject(projectId).and(EventRules.severityAtLeast(minSeverity)))
                .stream()
                .sorted(Comparator.comparing(Event::timestamp).reversed())
                .limit(safeLimit)
                .toList();
    }

    /** Event's attribute map rejects null values; drop them instead of crashing with a 500. */
    private static Map<String, String> cleanAttributes(Map<String, String> attributes) {
        if (attributes == null) {
            return Map.of();
        }
        return attributes.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
