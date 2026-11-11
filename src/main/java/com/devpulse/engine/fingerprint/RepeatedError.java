package com.devpulse.engine.fingerprint;

import com.devpulse.engine.model.Event;

/** "This fingerprint happened {@code occurrencesInWindow} times recently; here is the latest event." */
public record RepeatedError(ErrorFingerprint fingerprint, int occurrencesInWindow, Event latest) {
}
