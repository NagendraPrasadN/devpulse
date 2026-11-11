package com.devpulse.engine;

import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import com.devpulse.engine.repo.EventRules;
import com.devpulse.engine.stats.EventStats;
import com.devpulse.engine.store.EventStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventStatsTest {

    private Event e(String project, String service, Severity sev, String errorType) {
        return Event.create(project, service, sev, "msg", errorType);
    }

    private final List<Event> events = List.of(
            e("shop", "payment", Severity.ERROR, "Timeout"),
            e("shop", "payment", Severity.ERROR, "Timeout"),
            e("shop", "cart", Severity.FATAL, "Crash"),
            e("chat", "gateway", Severity.ERROR, "Timeout"),
            e("chat", "gateway", Severity.INFO, null),
            e("chat", "gateway", Severity.WARN, null));

    @Test
    void errorsPerProject() {
        assertEquals(Map.of("shop", 3L, "chat", 1L), EventStats.errorsPerProject(events));
    }

    @Test
    void topFailingServicesAreSortedWorstFirst() {
        var top = EventStats.topFailingServices(events, 2);

        assertEquals(2, top.size());
        assertEquals("payment", top.get(0).service());
        assertEquals(2, top.get(0).errors());
    }

    @Test
    void mostFrequentErrorTypeAndEmptyCase() {
        assertEquals("Timeout", EventStats.mostFrequentErrorType(events).orElseThrow());
        assertTrue(EventStats.mostFrequentErrorType(List.of()).isEmpty());
    }

    @Test
    void failuresVsOthersAlwaysHasBothKeys() {
        assertEquals(Map.of(true, 4L, false, 2L), EventStats.failuresVsOthers(events));
        assertEquals(0L, EventStats.failuresVsOthers(List.of()).get(true));
    }

    @Test
    void rulesCombineAndQueryTheStore() {
        EventStore store = new EventStore();
        events.forEach(store::save);

        var shopErrors = store.query(EventRules.forProject("shop").and(EventRules.severityAtLeast(Severity.ERROR)));
        var notShop = store.query(EventRules.forProject("shop").negate());

        assertEquals(3, shopErrors.size());
        assertEquals(3, notShop.size());
        assertEquals(2, store.projectIds().size());
        assertEquals(3, store.findByProject("chat").size());
    }
}
