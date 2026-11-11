package com.devpulse.engine.concurrent;

import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;

import java.util.List;
import java.util.Random;

/**
 * A fake application that sends events for a while. Each instance runs on its own thread
 * (it implements Runnable, so you can do: new Thread(app).start()).
 */
public final class SimulatedApp implements Runnable {

    private record ErrorTemplate(String errorType, String messageFormat) {}

    // %d gets a random number each time -> different text, SAME fingerprint after normalization
    private static final List<ErrorTemplate> ERROR_TEMPLATES = List.of(
            new ErrorTemplate("TimeoutException", "Timeout after %dms calling upstream"),
            new ErrorTemplate("SQLException", "Connection pool exhausted, %d active connections"),
            new ErrorTemplate("NullPointerException", "Cannot read field user for session %d"));

    private final EventEngine engine;
    private final String projectId;
    private final String service;
    private final double errorRate;
    private final long durationMillis;
    private final long intervalMillis;
    private final Random random;

    public SimulatedApp(EventEngine engine, String projectId, String service,
                        double errorRate, long durationMillis, long intervalMillis, long seed) {
        this.engine = engine;
        this.projectId = projectId;
        this.service = service;
        this.errorRate = errorRate;
        this.durationMillis = durationMillis;
        this.intervalMillis = intervalMillis;
        this.random = new Random(seed);
    }

    @Override
    public void run() {
        long end = System.currentTimeMillis() + durationMillis;
        try {
            while (System.currentTimeMillis() < end) {
                engine.submit(nextEvent());
                Thread.sleep(intervalMillis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Event nextEvent() {
        double roll = random.nextDouble();
        if (roll < 0.02) {
            // occasionally send junk so we can watch validation reject it
            return Event.create("ghost-project", service, Severity.INFO, "orphan event", null);
        }
        if (roll < 0.02 + errorRate) {
            ErrorTemplate t = ERROR_TEMPLATES.get(random.nextInt(ERROR_TEMPLATES.size()));
            return Event.create(projectId, service, Severity.ERROR,
                    String.format(t.messageFormat(), random.nextInt(5000)), t.errorType());
        }
        if (roll < 0.02 + errorRate + 0.10) {
            return Event.create(projectId, service, Severity.WARN, "Slow response", null);
        }
        return Event.create(projectId, service, Severity.INFO, "Request handled", null);
    }
}
