package com.devpulse.engine.stats;

import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard-style statistics, written with Streams and Collectors.
 *
 * A stream is a pipeline:  source -> intermediate ops (filter, map, sorted, limit) -> terminal op
 * (collect, toList, findFirst...). Nothing runs until the terminal operation is called.
 *
 * Every method here is a pure function: input in, result out, nothing modified.
 * That is why they are static and why they are trivially thread-safe.
 */
public final class EventStats {

    private EventStats() {}

    /** One row of the "top failing services" table. */
    public record ServiceFailures(String service, long errors) {}

    /**
     * Generic helper: count items grouped by any key.
     *
     * <T, K>        -> two type parameters, inferred at the call site
     * Collection<? extends T>  -> we only READ items from it (producer => extends)
     * Function<? super T, ? extends K> -> we feed it T's and read K's (consumer super / producer extends)
     */
    public static <T, K> Map<K, Long> countBy(Collection<? extends T> items,
                                              Function<? super T, ? extends K> classifier) {
        return items.stream()
                .collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }

    /** projectId -> number of ERROR/FATAL events. */
    public static Map<String, Long> errorsPerProject(Collection<Event> events) {
        return countBy(
                events.stream().filter(e -> e.severity().isError()).toList(),
                Event::projectId);
    }

    /** Severity -> count, always in severity order (EnumMap), including only severities that occur. */
    public static Map<Severity, Long> countBySeverity(Collection<Event> events) {
        return events.stream()
                .collect(Collectors.groupingBy(
                        Event::severity,
                        () -> new EnumMap<>(Severity.class), // choose the map implementation
                        Collectors.counting()));
    }

    /** The n services with the most errors, worst first (ties broken alphabetically). */
    public static List<ServiceFailures> topFailingServices(Collection<Event> events, int n) {
        return countBy(
                    events.stream().filter(e -> e.severity().isError()).toList(),
                    Event::service)
                .entrySet().stream()
                .map(entry -> new ServiceFailures(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(ServiceFailures::errors).reversed()
                        .thenComparing(ServiceFailures::service))
                .limit(n)
                .toList();
    }

    /** Most common errorType among failures, or empty if there were no typed failures. */
    public static Optional<String> mostFrequentErrorType(Collection<Event> events) {
        return events.stream()
                .filter(e -> e.severity().isError())
                .flatMap(e -> e.errorType().stream())          // Optional -> 0 or 1 element stream
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    /** partitioningBy always returns exactly two groups: true (failures) and false (everything else). */
    public static Map<Boolean, Long> failuresVsOthers(Collection<Event> events) {
        return events.stream()
                .collect(Collectors.partitioningBy(e -> e.severity().isError(), Collectors.counting()));
    }
}
