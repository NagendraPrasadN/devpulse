package com.devpulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Type-safe settings, read from application.properties (prefix "devpulse").
 *
 * Spring binds  devpulse.engine.queue-capacity=1000  into engine().queueCapacity().
 * "queue-capacity" (kebab-case) maps to queueCapacity automatically (relaxed binding).
 * Because this is a record, binding goes through the constructor, so the values are immutable.
 * @DefaultValue supplies a value when the property is missing: the app starts with zero config.
 * "5s" is converted to a Duration for us.
 */
@ConfigurationProperties(prefix = "devpulse")
public record DevPulseProperties(
        @DefaultValue Engine engine,
        @DefaultValue Notifications notifications,
        @DefaultValue("false") boolean seedDemoData) {

    public record Engine(
            @DefaultValue("1000") int queueCapacity,
            @DefaultValue("4") int workerThreads,
            @DefaultValue("5") int errorThreshold,
            @DefaultValue("5s") Duration errorWindow) {
    }

    /** type = "console" or "file" (decides which Notifier bean exists; see EngineConfiguration). */
    public record Notifications(
            @DefaultValue("console") String type,
            @DefaultValue("devpulse-notifications.log") String filePath) {
    }
}
