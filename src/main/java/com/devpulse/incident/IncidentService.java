package com.devpulse.incident;

import com.devpulse.engine.exception.EntityNotFoundException;
import com.devpulse.engine.incident.Incident;
import com.devpulse.engine.incident.IncidentManager;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Thin service over the Phase 1 IncidentManager so controllers never touch engine classes directly. */
@Service
public class IncidentService {

    private final IncidentManager manager;

    public IncidentService(IncidentManager manager) {
        this.manager = manager;
    }

    public List<Incident> list(boolean activeOnly) {
        List<Incident> incidents = activeOnly ? manager.findActive() : manager.findAll();
        return incidents.stream()
                .sorted(Comparator.comparing(Incident::openedAt).reversed())
                .toList();
    }

    public Incident get(UUID id) {
        return manager.findById(id).orElseThrow(() -> new EntityNotFoundException("Incident", id));
    }

    public Incident acknowledge(UUID id, String by) {
        return manager.acknowledge(id, by);   // may throw EntityNotFound / IncidentStateException
    }

    public Incident resolve(UUID id) {
        return manager.resolve(id);
    }
}
