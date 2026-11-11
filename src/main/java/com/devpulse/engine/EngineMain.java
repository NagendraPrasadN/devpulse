package com.devpulse.engine;

import com.devpulse.engine.concurrent.EngineConfig;
import com.devpulse.engine.concurrent.EventEngine;
import com.devpulse.engine.concurrent.SimulatedApp;
import com.devpulse.engine.exception.DevPulseException;
import com.devpulse.engine.fingerprint.RepeatedErrorDetector;
import com.devpulse.engine.incident.ConsoleNotifier;
import com.devpulse.engine.incident.FileNotifier;
import com.devpulse.engine.incident.Incident;
import com.devpulse.engine.incident.IncidentManager;
import com.devpulse.engine.incident.NotifierListener;
import com.devpulse.engine.model.Project;
import com.devpulse.engine.pipeline.EventEnricher;
import com.devpulse.engine.pipeline.EventValidator;
import com.devpulse.engine.pipeline.Pipeline;
import com.devpulse.engine.repo.InMemoryRepository;
import com.devpulse.engine.repo.Repository;
import com.devpulse.engine.stats.EventStats;
import com.devpulse.engine.store.EventStore;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Phase 1 simulator. Wires every piece together BY HAND, which is exactly the job
 * Spring's dependency injection will take over in Phase 2. Read this file top to bottom
 * and notice how many "new" calls and constructor arguments Spring will soon do for us.
 *
 * Run it from IntelliJ (green arrow next to main). No Spring involved.
 */
public class EngineMain {

    public static void main(String[] args) throws Exception {
        Clock clock = Clock.systemUTC();

        // 1. Developers register projects
        Repository<Project, String> projects = new InMemoryRepository<>(Project::id);
        projects.save(new Project("shop", "Shop Backend", "alice"));
        projects.save(new Project("chat", "Chat Platform", "bob"));
        projects.save(new Project("analytics", "Analytics", "carol"));

        // 2. Build the pipeline (composition: small steps, injected dependencies)
        Pipeline pipeline = Pipeline.of(
                new EventValidator(clock, id -> projects.findById(id).isPresent()),
                new EventEnricher(clock));

        // 3. Build the remaining collaborators
        EngineConfig config = EngineConfig.defaults();
        EventStore store = new EventStore();
        RepeatedErrorDetector detector = new RepeatedErrorDetector(config.errorThreshold(), config.errorWindow());
        IncidentManager incidents = new IncidentManager(clock);
        incidents.addListener(new NotifierListener(new ConsoleNotifier()));
        incidents.addListener(new NotifierListener(new FileNotifier(Path.of("devpulse-notifications.log"))));

        EventEngine engine = new EventEngine(config, pipeline, store, detector, incidents);
        engine.start();

        // 4. Start simulated applications, each on its own producer thread
        long durationMs = 3_000;
        List<Thread> producers = List.of(
                new Thread(new SimulatedApp(engine, "shop", "checkout-service", 0.02, durationMs, 20, 1), "app-checkout"),
                new Thread(new SimulatedApp(engine, "shop", "Payment-Service", 0.50, durationMs, 20, 2), "app-payment"),
                new Thread(new SimulatedApp(engine, "chat", "gateway", 0.35, durationMs, 20, 3), "app-gateway"),
                new Thread(new SimulatedApp(engine, "analytics", "ingest-worker", 0.03, durationMs, 20, 4), "app-ingest"));

        System.out.println("=== Simulating " + producers.size() + " applications for " + durationMs + "ms ===");
        producers.forEach(Thread::start);
        for (Thread producer : producers) {
            producer.join();                       // wait until every app has finished sending
        }
        engine.shutdown();                         // then drain the queue and stop the workers

        // 5. Results
        System.out.println("\n=== Engine metrics ===");
        System.out.println(engine.summary());

        System.out.println("\n=== Stats (Streams) ===");
        var events = store.findAll();
        System.out.println("Events by severity : " + EventStats.countBySeverity(events));
        System.out.println("Errors per project : " + EventStats.errorsPerProject(events));
        System.out.println("Top failing service: " + EventStats.topFailingServices(events, 3));
        System.out.println("Most common error  : " + EventStats.mostFrequentErrorType(events).orElse("none"));
        System.out.println("Failures vs others : " + EventStats.failuresVsOthers(events));

        System.out.println("\n=== Incidents ===");
        incidents.findAll().forEach(System.out::println);

        // 6. Incident lifecycle + exception handling
        System.out.println("\n=== Incident lifecycle ===");
        List<Incident> active = incidents.findActive();
        if (!active.isEmpty()) {
            UUID id = active.get(0).id();
            System.out.println("ack     -> " + incidents.acknowledge(id, "dave"));
            System.out.println("resolve -> " + incidents.resolve(id));
            try {
                incidents.acknowledge(id, "dave");       // illegal: RESOLVED is terminal
            } catch (DevPulseException e) {
                System.out.println("expected failure: " + e.getMessage());
            }
        }
        try {
            incidents.resolve(UUID.randomUUID());        // unknown id
        } catch (DevPulseException e) {
            System.out.println("expected failure: " + e.getMessage());
        }
    }
}
