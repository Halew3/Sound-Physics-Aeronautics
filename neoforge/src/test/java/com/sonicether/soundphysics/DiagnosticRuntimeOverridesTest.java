package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticRuntimeOverridesTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void beforeEach() {
        DiagnosticRuntimeOverrides.clear();
        RuntimeLoggingController.resetForTests();
    }

    @AfterEach
    void afterEach() {
        DiagnosticRuntimeOverrides.clear();
        RuntimeLoggingController.resetForTests();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void openAlErrorChecksDefaultToDisabled() {
        SoundPhysicsConfig config = config();

        assertFalse(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));
    }

    @Test
    void openAlErrorChecksCanBeEnabledByConfig() {
        SoundPhysicsConfig config = config();
        config.openAlErrorChecks.set(true);

        assertTrue(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));
    }

    @Test
    void focusedDebugModesEnableOpenAlErrorChecks() {
        SoundPhysicsConfig config = config();

        DiagnosticRuntimeOverrides.enableRootDebug();
        assertTrue(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));

        DiagnosticRuntimeOverrides.clear();
        DiagnosticRuntimeOverrides.enableDopplerDebug();
        assertTrue(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));

        DiagnosticRuntimeOverrides.clear();
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        assertTrue(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));
    }

    @Test
    void releaseDiagnosticModesDoNotForceOpenAlErrorChecks() {
        SoundPhysicsConfig config = config();

        DiagnosticRuntimeOverrides.enablePropellerSafe();
        assertFalse(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));

        DiagnosticRuntimeOverrides.enableRecordTest();
        assertFalse(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));

        DiagnosticRuntimeOverrides.enableRecordTestUnsafe();
        assertFalse(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));
    }

    @Test
    void clearReturnsOpenAlErrorChecksToConfigBehavior() {
        SoundPhysicsConfig config = config();

        DiagnosticRuntimeOverrides.enablePropellerDebug();
        assertTrue(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));

        DiagnosticRuntimeOverrides.clear();
        assertFalse(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));

        config.openAlErrorChecks.set(true);
        assertTrue(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));
    }

    @Test
    void traceCaptureEnablesOpenAlErrorChecks() {
        SoundPhysicsConfig config = config();

        RuntimeLoggingController.startCaptureSeconds(1);

        assertTrue(DiagnosticRuntimeOverrides.openAlErrorChecksEnabled(config));
    }

    private SoundPhysicsConfig config() {
        SoundPhysicsConfig config = ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
        SoundPhysicsMod.CONFIG = config;
        return config;
    }

}
