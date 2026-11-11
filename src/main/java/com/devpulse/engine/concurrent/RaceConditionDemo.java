package com.devpulse.engine.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Run this main() to watch a RACE CONDITION happen, then see three ways to deal with it.
 *
 * counter++ looks like one step but is THREE: read the value, add 1, write it back.
 * If two threads read the same value before either writes, one increment is lost:
 *
 *      Thread A reads 5      Thread B reads 5
 *      Thread A writes 6     Thread B writes 6    <- should be 7; one update vanished
 *
 * Expected total: THREADS * INCREMENTS = 2,000,000. Only the fixed versions always reach it.
 * (A broken counter can occasionally get lucky on one run; the bug is still there. Run it several times.)
 */
public class RaceConditionDemo {

    interface Counter {
        void increment();
        long get();
    }

    /** BROKEN: no protection at all. */
    static final class UnsafeCounter implements Counter {
        private long value;
        public void increment() { value++; }
        public long get() { return value; }
    }

    /** STILL BROKEN: volatile guarantees visibility, NOT atomicity of read-modify-write. */
    static final class VolatileCounter implements Counter {
        private volatile long value;
        public void increment() { value++; }
        public long get() { return value; }
    }

    /** FIXED with a lock: only one thread may be inside increment() at a time. */
    static final class SynchronizedCounter implements Counter {
        private long value;
        public synchronized void increment() { value++; }
        public synchronized long get() { return value; }
    }

    /** FIXED lock-free: hardware compare-and-swap makes the read-add-write indivisible. */
    static final class AtomicCounter implements Counter {
        private final AtomicLong value = new AtomicLong();
        public void increment() { value.incrementAndGet(); }
        public long get() { return value.get(); }
    }

    private static final int THREADS = 4;
    private static final int INCREMENTS = 500_000;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Expected total: " + ((long) THREADS * INCREMENTS));
        run("UnsafeCounter      (broken)", UnsafeCounter::new);
        run("VolatileCounter    (broken)", VolatileCounter::new);
        run("SynchronizedCounter(fixed) ", SynchronizedCounter::new);
        run("AtomicCounter      (fixed) ", AtomicCounter::new);
    }

    private static void run(String label, Supplier<Counter> factory) throws InterruptedException {
        Counter counter = factory.get();
        // A latch is a starting gun: threads wait at it, then all start at the same instant.
        // Without it, thread 1 may finish before thread 2 even starts, and no race would occur.
        CountDownLatch startingGun = new CountDownLatch(1);
        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startingGun.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int j = 0; j < INCREMENTS; j++) {
                    counter.increment();
                }
            });
            threads[i].start();
        }
        startingGun.countDown();   // fire!
        for (Thread t : threads) {
            t.join();   // wait for all to finish before reading the result
        }
        System.out.println(label + " -> " + counter.get());
    }
}
