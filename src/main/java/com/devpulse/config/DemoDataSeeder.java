package com.devpulse.config;

import com.devpulse.engine.concurrent.EventEngine;
import com.devpulse.engine.concurrent.SimulatedApp;
import com.devpulse.project.ProjectService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Optional demo: when devpulse.seed-demo-data=true, registers three projects and starts the
 * Phase 1 SimulatedApp threads so incidents appear without you sending anything by hand.
 *
 * ApplicationRunner.run() is called ONCE, after the whole application context is ready
 * (all beans created, web server started). That is a different lifecycle point from
 * @PostConstruct (right after ONE bean is wired) and from destroyMethod (shutdown).
 *
 * @ConditionalOnProperty: the bean only exists when the property is "true".
 */
@Component
@ConditionalOnProperty(prefix = "devpulse", name = "seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final long DURATION_MS = 20_000;
    private static final long INTERVAL_MS = 100;

    private final ProjectService projects;
    private final EventEngine engine;

    public DemoDataSeeder(ProjectService projects, EventEngine engine) {
        this.projects = projects;
        this.engine = engine;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("shop", "Shop Backend", "alice");
        seed("chat", "Chat Platform", "bob");
        seed("analytics", "Analytics", "carol");

        List<SimulatedApp> apps = List.of(
                new SimulatedApp(engine, "shop", "checkout-service", 0.02, DURATION_MS, INTERVAL_MS, 1),
                new SimulatedApp(engine, "shop", "Payment-Service", 0.50, DURATION_MS, INTERVAL_MS, 2),
                new SimulatedApp(engine, "chat", "gateway", 0.35, DURATION_MS, INTERVAL_MS, 3),
                new SimulatedApp(engine, "analytics", "ingest-worker", 0.03, DURATION_MS, INTERVAL_MS, 4));

        for (SimulatedApp app : apps) {
            Thread thread = new Thread(() -> {
                try {
                    app.run();
                } catch (IllegalStateException engineStopped) {
                    // application is shutting down while the demo was still sending: ignore
                }
            }, "demo-app");
            thread.setDaemon(true);   // never keeps the JVM alive on shutdown
            thread.start();
        }
    }

    private void seed(String id, String name, String owner) {
        if (!projects.exists(id)) {
            projects.register(id, name, owner);
        }
    }
}
