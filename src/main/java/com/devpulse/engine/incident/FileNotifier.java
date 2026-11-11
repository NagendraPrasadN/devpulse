package com.devpulse.engine.incident;

import com.devpulse.engine.exception.NotificationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Appends notifications to a file.
 *
 * Shows exception TRANSLATION: Files.writeString throws IOException (low-level, checked).
 * We catch it and rethrow our own NotificationException, keeping the original as the
 * "cause" so the stack trace is not lost. Callers depend on our exception type,
 * not on the fact that we happen to use files.
 *
 * synchronized: two threads appending at once could interleave their writes.
 */
public final class FileNotifier implements Notifier {

    private final Path file;

    public FileNotifier(Path file) {
        this.file = file;
    }

    @Override
    public synchronized void send(String message) throws NotificationException {
        try {
            Files.writeString(file, message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new NotificationException("Could not write notification to " + file, e);
        }
    }
}
