package com.devpulse.engine.fingerprint;

import com.devpulse.engine.model.Event;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The "identity" of a kind of error. Thousands of events like
 *     "Timeout after 3021ms calling upstream"
 *     "Timeout after 87ms calling upstream"
 * are the SAME problem, so they must produce the SAME fingerprint.
 *
 * HOW: project + service + errorType + message with the variable parts (numbers, ids)
 * replaced by placeholders ("normalization").
 *
 * WHY A RECORD:
 *  We need a map key: equals/hashCode over ALL four components, immutable. That is
 *  exactly what a record generates. Writing it by hand would be 25 lines of
 *  bug-prone boilerplate. This is the type used as the key of the detector's HashMap.
 *
 * LATER: this becomes the grouping key in the database (Phase 3) and the
 * idempotency/partition key in Kafka (Phase 5).
 */
public record ErrorFingerprint(String projectId, String service, String errorType, String normalizedMessage) {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HEX_PATTERN = Pattern.compile("0x[0-9a-fA-F]+");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    public ErrorFingerprint {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(service);
        Objects.requireNonNull(errorType);
        Objects.requireNonNull(normalizedMessage);
    }

    public static ErrorFingerprint from(Event event) {
        return new ErrorFingerprint(
                event.projectId(),
                event.service(),
                event.errorType().orElse("UnknownError"),
                normalize(event.message()));
    }

    /** Order matters: UUIDs first (they contain digits), then hex, then plain numbers. */
    static String normalize(String message) {
        String result = UUID_PATTERN.matcher(message).replaceAll("<uuid>");
        result = HEX_PATTERN.matcher(result).replaceAll("<hex>");
        result = NUMBER_PATTERN.matcher(result).replaceAll("<n>");
        return result;
    }
}
