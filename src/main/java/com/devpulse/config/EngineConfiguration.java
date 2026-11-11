package com.devpulse.config;

import com.devpulse.engine.concurrent.EngineConfig;
import com.devpulse.engine.concurrent.EventEngine;
import com.devpulse.engine.fingerprint.RepeatedErrorDetector;
import com.devpulse.engine.incident.ConsoleNotifier;
import com.devpulse.engine.incident.FileNotifier;
import com.devpulse.engine.incident.IncidentListener;
import com.devpulse.engine.incident.IncidentManager;
import com.devpulse.engine.incident.Notifier;
import com.devpulse.engine.incident.NotifierListener;
import com.devpulse.engine.pipeline.EventEnricher;
import com.devpulse.engine.pipeline.EventProcessor;
import com.devpulse.engine.pipeline.EventValidator;
import com.devpulse.engine.pipeline.Pipeline;
import com.devpulse.engine.store.EventStore;
import com.devpulse.project.ProjectService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Phase 1's EngineMain wired everything with "new". This class is the same wiring, but now
 * Spring does the calling. Each @Bean method says: "to build this object, I need these
 * other beans" (the method parameters), and Spring supplies them. That is DEPENDENCY INJECTION.
 *
 * Compare with EngineMain: the objects and the order are identical. What changed is WHO does
 * the wiring. The engine classes remain plain Java with no Spring imports.
 *
 * Beans are SINGLETONS by default: one instance shared by the whole application.
 * (That is why EventStore, written to be thread-safe in Phase 1, matters.)
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DevPulseProperties.class)
public class EngineConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();   // a bean, so tests can replace it with a fixed clock
    }

    /** Translates application.properties values into the Phase 1 settings record. */
    @Bean
    public EngineConfig engineConfig(DevPulseProperties properties) {
        DevPulseProperties.Engine e = properties.engine();
        return new EngineConfig(e.queueCapacity(), e.workerThreads(), e.errorThreshold(), e.errorWindow());
    }

    @Bean
    public EventStore eventStore() {
        return new EventStore();
    }

    @Bean
    public RepeatedErrorDetector repeatedErrorDetector(EngineConfig config) {
        return new RepeatedErrorDetector(config.errorThreshold(), config.errorWindow());
    }

    /**
     * The pipeline needs "does this project exist?". In Phase 1 that was a lambda over an
     * in-memory repository. Now it asks ProjectService (backed by the database).
     * Spring injects the ProjectService bean into this method.
     */
    @Bean
    public EventProcessor eventPipeline(Clock clock, ProjectService projects) {
        return Pipeline.of(new EventValidator(clock, projects::exists), new EventEnricher(clock));
    }

    // ---- CONDITIONAL BEANS: the same mechanism auto-configuration uses ----
    // Exactly one of these two exists, depending on devpulse.notifications.type.

    @Bean
    @ConditionalOnProperty(prefix = "devpulse.notifications", name = "type",
            havingValue = "console", matchIfMissing = true)
    public Notifier consoleNotifier() {
        return new ConsoleNotifier();
    }

    @Bean
    @ConditionalOnProperty(prefix = "devpulse.notifications", name = "type", havingValue = "file")
    public Notifier fileNotifier(DevPulseProperties properties) {
        return new FileNotifier(Path.of(properties.notifications().filePath()));
    }

    @Bean
    public IncidentListener notifierListener(Notifier notifier) {
        return new NotifierListener(notifier);
    }

    /**
     * "List<IncidentListener>": Spring injects EVERY bean of that type. Add another
     * IncidentListener bean anywhere and it is registered here with no change to this method.
     */
    @Bean
    public IncidentManager incidentManager(Clock clock, List<IncidentListener> listeners) {
        IncidentManager manager = new IncidentManager(clock);
        listeners.forEach(manager::addListener);
        return manager;
    }

    /**
     * BEAN LIFECYCLE for a plain class we do not own annotations on:
     *   initMethod    -> Spring calls engine.start() after the bean is created and wired
     *   destroyMethod -> Spring calls engine.shutdown() when the application stops
     * shutdown() drains the queue first, so no accepted event is lost on a clean stop.
     * Spring destroys beans in REVERSE dependency order, so the engine stops before the
     * database connection pool it depends on is closed.
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public EventEngine eventEngine(EngineConfig config, EventProcessor eventPipeline, EventStore store,
                                   RepeatedErrorDetector detector, IncidentManager incidents) {
        return new EventEngine(config, eventPipeline, store, detector, incidents);
    }
}
