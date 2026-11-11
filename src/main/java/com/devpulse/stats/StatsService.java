package com.devpulse.stats;

import com.devpulse.engine.concurrent.EventEngine;
import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import com.devpulse.engine.stats.EventStats;
import com.devpulse.engine.store.EventStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Builds the dashboard snapshot from the Phase 1 EventStats (Streams) plus engine counters. */
@Service
public class StatsService {

    public record EngineMetrics(long accepted, long processed, long rejected, long failed, long dropped) {
    }

    public record StatsSnapshot(long totalEvents,
                                Map<Severity, Long> bySeverity,
                                Map<String, Long> errorsPerProject,
                                List<EventStats.ServiceFailures> topFailingServices,
                                String mostFrequentErrorType,
                                EngineMetrics engine) {
    }

    private final EventStore store;
    private final EventEngine engine;

    public StatsService(EventStore store, EventEngine engine) {
        this.store = store;
        this.engine = engine;
    }

    public StatsSnapshot snapshot() {
        List<Event> events = store.findAll();
        return new StatsSnapshot(
                events.size(),
                EventStats.countBySeverity(events),
                EventStats.errorsPerProject(events),
                EventStats.topFailingServices(events, 5),
                EventStats.mostFrequentErrorType(events).orElse(null),
                new EngineMetrics(engine.acceptedCount(), engine.processedCount(),
                        engine.rejectedCount(), engine.failedCount(), engine.droppedCount()));
    }
}
