package com.devpulse.engine.pipeline;

import com.devpulse.engine.model.Event;

/**
 * One step in the processing pipeline. This is the STRATEGY pattern:
 * the pipeline does not care HOW a step works, only that it takes an Event and
 * returns an Event (possibly a changed copy), or throws if the event must be rejected.
 *
 * A functional interface (exactly one abstract method), so a step can even be a lambda:
 *     EventProcessor upper = e -> e.withMessage(e.message().toUpperCase());
 *
 * In Phase 2, implementations of this interface become Spring beans and Spring
 * injects them where needed. The interface is what makes that swap painless.
 */
@FunctionalInterface
public interface EventProcessor {

    Event process(Event event);
}
