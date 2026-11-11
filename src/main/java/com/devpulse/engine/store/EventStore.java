package com.devpulse.engine.store;

import com.devpulse.engine.model.Event;
import com.devpulse.engine.repo.InMemoryRepository;
import com.devpulse.engine.repo.Repository;
import com.devpulse.engine.repo.Rule;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Where processed events live, with two ways to find them:
 *   - by event id            (Repository, a map id -> Event)
 *   - grouped by project     (map projectId -> queue of that project's events)
 *
 * Worker threads write concurrently, so we use ConcurrentHashMap (not our SimpleHashMap)
 * and ConcurrentLinkedQueue (a lock-free queue safe for many writers).
 *
 * computeIfAbsent is ATOMIC: if two threads ask for a missing project at the same
 * moment, only one queue is created and both get that same queue.
 *
 * Note: saving the same event id twice would add it to the project queue twice.
 * Handling duplicates (idempotency) is a Phase 5 topic, so it is intentionally absent.
 */
public class EventStore {

    private final Repository<Event, UUID> byId = new InMemoryRepository<>(Event::id);
    private final ConcurrentHashMap<String, Queue<Event>> byProject = new ConcurrentHashMap<>();

    public void save(Event event) {
        byId.save(event);
        byProject.computeIfAbsent(event.projectId(), key -> new ConcurrentLinkedQueue<>())
                 .add(event);
    }

    public Optional<Event> findById(UUID id) {
        return byId.findById(id);
    }

    /** Returns a snapshot copy, so callers can't modify our internal queue. */
    public List<Event> findByProject(String projectId) {
        Queue<Event> queue = byProject.get(projectId);
        return queue == null ? List.of() : List.copyOf(queue);
    }

    public List<Event> findAll() {
        return byId.findAll();
    }

    public List<Event> query(Rule<? super Event> rule) {
        return byId.findWhere(rule);
    }

    public long count() {
        return byId.count();
    }

    public Set<String> projectIds() {
        return Set.copyOf(byProject.keySet());
    }
}
