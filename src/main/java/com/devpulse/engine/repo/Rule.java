package com.devpulse.engine.repo;

/**
 * A reusable yes/no condition on something of type T ("is this event an error?").
 *
 * GENERICS:
 *   Rule<T> works for any type: Rule<Event>, Rule<Incident>...
 *
 * WILDCARD  "Rule<? super T>"  (PECS: Producer Extends, Consumer Super):
 *   Inside Rule<Event>.and(...) we accept Rule<? super Event>. That means a rule that
 *   can judge Events OR any parent type of Event (e.g. Rule<Object>) is allowed,
 *   because anything that can judge a parent can judge an Event. Our rule only CONSUMES
 *   T values (passes them in), hence "super".
 */
@FunctionalInterface
public interface Rule<T> {

    boolean test(T value);

    default Rule<T> and(Rule<? super T> other) {
        return value -> this.test(value) && other.test(value);
    }

    default Rule<T> or(Rule<? super T> other) {
        return value -> this.test(value) || other.test(value);
    }

    default Rule<T> negate() {
        return value -> !this.test(value);
    }
}
