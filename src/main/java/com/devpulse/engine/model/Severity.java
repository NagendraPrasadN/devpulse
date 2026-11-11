package com.devpulse.engine.model;

/**
 * How serious an event is. (This also plays the role of "log level" in DevPulse.)
 *
 * An enum is perfect here: a closed set of known values, type-safe, usable as a map key.
 * Enums can carry data and behavior: every constant has a numeric rank, and the
 * comparison logic lives right next to the data instead of being scattered around.
 */
public enum Severity {
    DEBUG(10),
    INFO(20),
    WARN(30),
    ERROR(40),
    FATAL(50);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /** True if this severity is the same as or worse than {@code other}. */
    public boolean isAtLeast(Severity other) {
        return this.rank >= other.rank;
    }

    /** ERROR and FATAL are the "failures" the platform tracks. */
    public boolean isError() {
        return isAtLeast(ERROR);
    }
}
