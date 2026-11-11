package com.devpulse.engine.concurrent;

import com.devpulse.engine.exception.ValidationException;
import com.devpulse.engine.fingerprint.RepeatedErrorDetector;
import com.devpulse.engine.incident.IncidentManager;
import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import com.devpulse.engine.pipeline.EventProcessor;
import com.devpulse.engine.store.EventStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The heart of the engine: PRODUCERS -> BlockingQueue -> WORKERS.
 *
 *   producer threads          bounded queue              worker threads
 *   (apps sending events) ──► [ e e e e e . . . ] ──►  take() -> pipeline -> store
 *                                                              -> detector -> incidents
 *
 * WHY A QUEUE? It decouples speed. Producers just drop events off and return quickly;
 * workers process at their own pace. This exact idea is what Kafka (Phase 5) does at scale.
 *
 * WHY BOUNDED (ArrayBlockingQueue)? If producers are faster than workers, an unbounded queue
 * grows until the JVM runs out of memory. A bounded queue pushes back: submit() waits briefly
 * and then reports failure ("backpressure").
 *
 * BlockingQueue does the hard thread-coordination for us:
 *   take() -> waits (sleeps, no CPU) until an item exists
 *   offer(x, timeout) -> waits for free space up to the timeout
 *
 * SHUTDOWN uses a "poison pill": a special sentinel event. After producers stop, we enqueue
 * one pill per worker. A worker that takes a pill exits its loop. Because the queue is FIFO,
 * every real event queued before the pills is processed first -> graceful drain.
 *
 * Counters are AtomicLong: incrementAndGet() is one indivisible operation, so no updates are lost
 * (compare with RaceConditionDemo, where a plain counter loses updates).
 *
 * `running` is volatile: a write by one thread is immediately visible to all others.
 * (volatile gives VISIBILITY, not atomicity. Fine for a simple on/off flag.)
 */
public final class EventEngine {

    /** Sentinel compared by REFERENCE (==), never processed as a real event. */
    private static final Event POISON_PILL =
            Event.create("__poison__", "__poison__", Severity.INFO, "poison", null);

    private final EngineConfig config;
    private final EventProcessor pipeline;
    private final EventStore store;
    private final RepeatedErrorDetector detector;
    private final IncidentManager incidents;

    private final BlockingQueue<Event> queue;
    private final List<Thread> workers = new ArrayList<>();

    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    private volatile boolean running;

    public EventEngine(EngineConfig config, EventProcessor pipeline, EventStore store,
                       RepeatedErrorDetector detector, IncidentManager incidents) {
        this.config = config;
        this.pipeline = pipeline;
        this.store = store;
        this.detector = detector;
        this.incidents = incidents;
        this.queue = new ArrayBlockingQueue<>(config.queueCapacity());
    }

    public void start() {
        running = true;
        for (int i = 1; i <= config.workerThreads(); i++) {
            Thread worker = new Thread(this::workLoop, "worker-" + i);
            workers.add(worker);
            worker.start();
        }
    }

    /**
     * Called by producer threads.
     * @return true if queued; false if the queue stayed full for a second (event dropped)
     */
    public boolean submit(Event event) throws InterruptedException {
        if (!running) {
            throw new IllegalStateException("Engine is not running");
        }
        boolean queued = queue.offer(event, 1, TimeUnit.SECONDS);
        (queued ? accepted : dropped).incrementAndGet();
        return queued;
    }

    /** Stop producers first, then call this. Waits until every queued event has been processed. */
    public void shutdown() throws InterruptedException {
        if (!running) return;
        running = false;
        for (int i = 0; i < workers.size(); i++) {
            queue.put(POISON_PILL);
        }
        for (Thread worker : workers) {
            worker.join();           // wait for the worker to finish
        }
    }

    // ---- what each worker thread runs ----

    private void workLoop() {
        try {
            while (true) {
                Event event = queue.take();            // blocks while the queue is empty
                if (event == POISON_PILL) {
                    return;                            // time to stop
                }
                handle(event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();        // restore the flag; let the thread end
        }
    }

    private void handle(Event raw) {
        try {
            Event event = pipeline.process(raw);       // validate + enrich
            store.save(event);
            processed.incrementAndGet();
            detector.observe(event).ifPresent(incidents::onRepeatedError);
        } catch (ValidationException e) {
            rejected.incrementAndGet();                // expected: bad input from a client
        } catch (RuntimeException e) {
            failed.incrementAndGet();                  // unexpected bug: count it, but keep the worker alive!
            System.err.println("[" + Thread.currentThread().getName() + "] unexpected failure: " + e);
        }
    }

    // ---- metrics ----

    public long acceptedCount() { return accepted.get(); }
    public long droppedCount() { return dropped.get(); }
    public long processedCount() { return processed.get(); }
    public long rejectedCount() { return rejected.get(); }
    public long failedCount() { return failed.get(); }

    public String summary() {
        return "accepted=" + accepted + " processed=" + processed + " rejected=" + rejected
                + " failed=" + failed + " dropped=" + dropped;
    }
}
