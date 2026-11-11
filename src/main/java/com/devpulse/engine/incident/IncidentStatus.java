package com.devpulse.engine.incident;

/**
 * The lifecycle of an incident:
 *
 *      OPEN ──► ACKNOWLEDGED ──► RESOLVED
 *        └─────────────────────────▲
 *             (can resolve directly)
 *
 * ENUM WITH BEHAVIOR: the rules about which moves are legal live here, next to the
 * states themselves, instead of in if/else chains scattered through other classes.
 * The switch expression below is exhaustive: if you add a new constant and forget it,
 * the compiler refuses to build.
 */
public enum IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED;

    public boolean canTransitionTo(IncidentStatus target) {
        return switch (this) {
            case OPEN -> target == ACKNOWLEDGED || target == RESOLVED;
            case ACKNOWLEDGED -> target == RESOLVED;
            case RESOLVED -> false; // terminal state
        };
    }

    /** An "active" incident still needs attention. */
    public boolean isActive() {
        return this != RESOLVED;
    }
}
