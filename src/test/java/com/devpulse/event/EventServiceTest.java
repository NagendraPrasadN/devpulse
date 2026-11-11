package com.devpulse.event;

import com.devpulse.common.QueueFullException;
import com.devpulse.engine.concurrent.EventEngine;
import com.devpulse.engine.exception.EntityNotFoundException;
import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Project;
import com.devpulse.engine.model.Severity;
import com.devpulse.engine.store.EventStore;
import com.devpulse.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);   // frozen time = predictable test

    @Mock
    ProjectService projects;

    @Mock
    EventEngine engine;          // Mockito can mock final classes (inline mock maker is the default)

    private final EventStore store = new EventStore();    // real: it is plain Java and cheap

    private EventService service() {
        return new EventService(projects, engine, store, clock);
    }

    private EventController.IngestEventRequest request(String projectId) {
        return new EventController.IngestEventRequest(projectId, "api", Severity.ERROR,
                "Timeout after 12ms", "TimeoutException", null, null);
    }

    @Test
    void ingestQueuesEventWithServerTimestampWhenNoneGiven() throws Exception {
        when(projects.get("shop")).thenReturn(new Project("shop", "Shop", "alice"));
        when(engine.submit(any(Event.class))).thenReturn(true);

        UUID id = service().ingest(request("shop"));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(engine).submit(captor.capture());
        Event queued = captor.getValue();
        assertThat(queued.id()).isEqualTo(id);
        assertThat(queued.timestamp()).isEqualTo(NOW);
        assertThat(queued.attributes()).isEmpty();
        assertThat(queued.errorType()).contains("TimeoutException");
    }

    @Test
    void ingestForUnknownProjectNeverTouchesTheEngine() {
        when(projects.get("ghost")).thenThrow(new EntityNotFoundException("Project", "ghost"));

        assertThatThrownBy(() -> service().ingest(request("ghost")))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(engine);
    }

    @Test
    void ingestFailsWhenQueueIsFull() throws Exception {
        when(projects.get("shop")).thenReturn(new Project("shop", "Shop", "alice"));
        when(engine.submit(any(Event.class))).thenReturn(false);

        assertThatThrownBy(() -> service().ingest(request("shop")))
                .isInstanceOf(QueueFullException.class);
    }

    @Test
    void interruptionIsTranslatedAndTheInterruptFlagIsRestored() throws Exception {
        when(projects.get("shop")).thenReturn(new Project("shop", "Shop", "alice"));
        when(engine.submit(any(Event.class))).thenThrow(new InterruptedException());

        assertThatThrownBy(() -> service().ingest(request("shop")))
                .isInstanceOf(QueueFullException.class);

        assertThat(Thread.interrupted()).isTrue();   // reading it also clears it for other tests
    }

    @Test
    void findByProjectFiltersSortsNewestFirstAndLimits() {
        when(projects.get("shop")).thenReturn(new Project("shop", "Shop", "alice"));
        store.save(event("shop", Severity.INFO, NOW.minusSeconds(30)));
        store.save(event("shop", Severity.ERROR, NOW.minusSeconds(20)));
        store.save(event("shop", Severity.ERROR, NOW.minusSeconds(10)));
        store.save(event("chat", Severity.ERROR, NOW));

        List<Event> result = service().findByProject("shop", Severity.ERROR, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).timestamp()).isEqualTo(NOW.minusSeconds(10));
    }

    private Event event(String project, Severity severity, Instant at) {
        return new Event(UUID.randomUUID(), project, "api", severity, "msg", null, at, Map.of());
    }
}
