package com.devpulse.engine.concurrent;

import java.time.Duration;

/**
 * Settings for the engine, as an immutable record with validation in the compact constructor.
 * Invalid configuration fails at startup (fail fast), not at 3 a.m. under load.
 */
public record EngineConfig(int queueCapacity, int workerThreads, int errorThreshold, Duration errorWindow) {

    public EngineConfig {
        if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity must be >= 1");
        if (workerThreads < 1) throw new IllegalArgumentException("workerThreads must be >= 1");
        if (errorThreshold < 1) throw new IllegalArgumentException("errorThreshold must be >= 1");
        if (errorWindow.isNegative() || errorWindow.isZero()) {
            throw new IllegalArgumentException("errorWindow must be positive");
        }
    }

    public static EngineConfig defaults() {
        return new EngineConfig(1_000, 4, 5, Duration.ofSeconds(5));
    }
}
