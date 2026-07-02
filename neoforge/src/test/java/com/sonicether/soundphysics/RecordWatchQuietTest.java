package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RecordWatchQuietTest {

    @AfterEach
    void reset() {
        RuntimeLoggingController.resetForTests();
        RecordDiagnostics.reset();
    }

    @Test
    void recordWatchStateDoesNotEnableRuntimeCaptureByDefault() {
        RecordDiagnostics.startWatchSeconds(10);

        assertFalse(RuntimeLoggingController.captureActive());
    }

    @Test
    void verboseCaptureRemainsExplicit() {
        RecordDiagnostics.startWatchSeconds(10);
        RuntimeLoggingController.startCaptureSeconds(10);

        assertTrue(RuntimeLoggingController.captureActive());
    }
}
