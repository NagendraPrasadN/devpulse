package com.devpulse.engine.incident;

/** Prints to standard output. PrintStream.println is internally synchronized, so threads don't interleave mid-line. */
public final class ConsoleNotifier implements Notifier {

    @Override
    public void send(String message) {   // may narrow the signature: no exception thrown here
        System.out.println("[NOTIFY:console] " + message);
    }
}
