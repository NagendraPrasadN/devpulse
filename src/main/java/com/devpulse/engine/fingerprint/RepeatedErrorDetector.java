package com.devpulse.engine.fingerprint;

import com.devpulse.engine.model.Event;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects "the same error, again and again" using a SLIDING TIME WINDOW.
 *
 * For every fingerprint we keep a Deque (double-ended queue) of timestamps:
 *
 *   window = 10s, threshold = 5
 *   deque:  [t=1s, t=3s, t=4s, t=12s, t=13s]   new event at t=14s
 *   1. addLast(14s)
 *   2. cutoff = 14s - 10s = 4s  -> pollFirst() while the oldest is before the cutoff
 *      removes 1s and 3s  ->  [4s, 12s, 13s, 14s]  -> count = 4  -> below threshold
 *
 * Why a Deque? Newest goes in at the tail, expired ones leave from the head. Both are O(1).
 *
 * THREAD SAFETY:
 *  - the outer ConcurrentHashMap handles concurrent lookup/creation of deques
 *  - an ArrayDeque is NOT thread-safe, so every access to ONE deque is done inside
 *    synchronized(deque). Different fingerprints use different locks -> they don't block each other.
 *
 * KNOWN LIMITATIONS (good to know, intentionally simple here):
 *  - we trust event timestamps; slightly out-of-order arrival makes the window approximate
 *  - map entries are never removed, so memory grows with the number of distinct fingerprints
 *    (a real system would evict idle ones)
 */
public final class RepeatedErrorDetector {

    private final int threshold;
    private final Duration window;
    private final ConcurrentHashMap<ErrorFingerprint, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public RepeatedErrorDetector(int threshold, Duration window) {
        if (threshold < 1) throw new IllegalArgumentException("threshold must be >= 1");
        this.threshold = threshold;
        this.window = window;
    }

    /**
     * Records the event. Returns a RepeatedError if this fingerprint has now occurred
     * at least {@code threshold} times inside the window; otherwise empty.
     * Non-error events are ignored.
     */
    public Optional<RepeatedError> observe(Event event) {
        if (!event.severity().isError()) {
            return Optional.empty();
        }

        ErrorFingerprint fingerprint = ErrorFingerprint.from(event);
        Deque<Instant> timestamps = windows.computeIfAbsent(fingerprint, key -> new ArrayDeque<>());

        int count;
        synchronized (timestamps) {
            timestamps.addLast(event.timestamp());
            Instant cutoff = event.timestamp().minus(window);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            count = timestamps.size();
        }

        return count >= threshold
                ? Optional.of(new RepeatedError(fingerprint, count, event))
                : Optional.empty();
    }
}
