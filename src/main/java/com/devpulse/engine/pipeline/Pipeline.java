package com.devpulse.engine.pipeline;

import com.devpulse.engine.model.Event;

import java.util.List;

/**
 * Runs several EventProcessors one after another.
 *
 * COMPOSITION OVER INHERITANCE:
 *  We do not create "ValidatingEnrichingEventProcessor extends ...".
 *  Instead Pipeline HAS-A list of small processors and delegates to them.
 *  Add a step = add an item to the list; no class hierarchy to rewire.
 *
 * COMPOSITE PATTERN:
 *  Pipeline itself implements EventProcessor, so a Pipeline can be a step inside
 *  another Pipeline. Callers never need to know whether they hold one step or ten.
 */
public final class Pipeline implements EventProcessor {

    private final List<EventProcessor> steps;

    public Pipeline(List<EventProcessor> steps) {
        this.steps = List.copyOf(steps); // immutable copy: nobody can alter the order later
    }

    /** Varargs factory: Pipeline.of(validator, enricher). */
    public static Pipeline of(EventProcessor... steps) {
        return new Pipeline(List.of(steps));
    }

    @Override
    public Event process(Event event) {
        Event current = event;
        for (EventProcessor step : steps) {
            current = step.process(current); // output of one step is input of the next
        }
        return current;
    }
}
