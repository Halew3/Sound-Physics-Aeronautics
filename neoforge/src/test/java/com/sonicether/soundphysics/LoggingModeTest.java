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

class LoggingModeTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void beforeEach() {
        RuntimeLoggingController.resetForTests();
    }

    @AfterEach
    void reset() {
        RuntimeLoggingController.resetForTests();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void quietDisablesVerboseFlags() {
        SoundPhysicsConfig config = config();
        config.debugLogging.set(true);
        config.occlusionLogging.set(true);
        config.environmentLogging.set(true);
        config.performanceLogging.set(true);
        config.dopplerDebugLogging.set(true);
        config.soundPhysicsPolicyDebugLogging.set(true);
        config.sableAcousticDebugLogging.set(true);
        config.soundPhysicsTraceLogging.set(true);
        config.openAlErrorChecks.set(true);

        RuntimeLoggingController.quiet(config);

        assertFalse(config.debugLogging.get());
        assertFalse(config.occlusionLogging.get());
        assertFalse(config.environmentLogging.get());
        assertFalse(config.performanceLogging.get());
        assertFalse(config.dopplerDebugLogging.get());
        assertFalse(config.soundPhysicsPolicyDebugLogging.get());
        assertFalse(config.sableAcousticDebugLogging.get());
        assertFalse(config.soundPhysicsTraceLogging.get());
        assertFalse(config.openAlErrorChecks.get());
        assertFalse(RuntimeLoggingController.verboseActive());
    }

    @Test
    void captureExpires() {
        RuntimeLoggingController.startCaptureSeconds(10);
        assertTrue(RuntimeLoggingController.captureActive());

        RuntimeLoggingController.forceCaptureExpiredForTests();

        assertFalse(RuntimeLoggingController.captureActive());
        assertTrue(RuntimeLoggingController.statusText(null).contains("expiredCaptures=1"));
    }

    @Test
    void perTemplateCapSuppressesSpam() {
        RuntimeLoggingController.setMaxLinesPerSecondForTests(2);
        RuntimeLoggingController.enableTrace();

        assertTrue(RuntimeLoggingController.shouldLog(RuntimeLoggingController.Category.DEBUG, "same sound line"));
        assertTrue(RuntimeLoggingController.shouldLog(RuntimeLoggingController.Category.DEBUG, "same sound line"));
        assertFalse(RuntimeLoggingController.shouldLog(RuntimeLoggingController.Category.DEBUG, "same sound line"));
        assertTrue(RuntimeLoggingController.statusText(null).contains("suppressedLines=1"));
    }

    @Test
    void statusReportsSummaries() {
        RuntimeLoggingController.recordSound(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "entity.fish.swim"));
        RuntimeLoggingController.recordSkipReason(SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_SAFE_MODE);

        String status = RuntimeLoggingController.statusText(null);

        assertTrue(status.contains("topSounds=minecraft:entity.fish.swim=1"));
        assertTrue(status.contains("topSkips=PROPELLER_SAFE_MODE=1"));
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
