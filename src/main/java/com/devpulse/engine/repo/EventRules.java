package com.devpulse.engine.repo;

import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;

import java.util.Locale;

/**
 * Ready-made Rule<Event> building blocks. Combine them:
 *     EventRules.forProject("shop").and(EventRules.severityAtLeast(Severity.ERROR))
 */
public final class EventRules {

    private EventRules() {} // utility class: no instances

    public static Rule<Event> severityAtLeast(Severity min) {
        return event -> event.severity().isAtLeast(min);
    }

    public static Rule<Event> forProject(String projectId) {
        return event -> event.projectId().equals(projectId);
    }

    public static Rule<Event> forService(String service) {
        return event -> event.service().equals(service);
    }

    public static Rule<Event> messageContains(String text) {
        String needle = text.toLowerCase(Locale.ROOT);
        return event -> event.message().toLowerCase(Locale.ROOT).contains(needle);
    }
}
