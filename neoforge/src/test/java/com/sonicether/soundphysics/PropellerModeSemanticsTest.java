package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PropellerModeSemanticsTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        DiagnosticRuntimeOverrides.clear();
        SoundPhysicsPolicyDiagnostics.reset();
    }

    @Test
    void safeModeReportsBypassNotAllowed() {
        SoundPhysicsConfig config = config();
        DiagnosticRuntimeOverrides.enablePropellerSafe();

        SoundPhysicsSoundPolicy.evaluateAcoustic(config, propeller());
        SoundPhysicsSoundPolicy.evaluateDoppler(config, propeller());

        String summary = SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText();
        assertTrue(summary.contains("safeBypassedAcoustic=1"));
        assertTrue(summary.contains("safeBypassedDoppler=1"));
        assertTrue(summary.contains("debugAllowedAcoustic=0"));
        assertTrue(summary.contains("debugAllowedDoppler=0"));
        assertTrue(summary.contains("allowedAcoustic=0"));
        assertTrue(summary.contains("allowedDoppler=0"));
        assertTrue(SoundPhysicsPolicyDiagnostics.latestPropellerObservationText().contains("decision=PROPELLER_SAFE_MODE"));
    }

    @Test
    void debugModeReportsDebugAllowed() {
        SoundPhysicsConfig config = config();
        DiagnosticRuntimeOverrides.enablePropellerDebug();

        SoundPhysicsSoundPolicy.evaluateAcoustic(config, propeller());
        SoundPhysicsSoundPolicy.evaluateDoppler(config, propeller());

        String summary = SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText();
        assertTrue(summary.contains("debugAllowedAcoustic=1"));
        assertTrue(summary.contains("debugAllowedDoppler=1"));
        assertTrue(summary.contains("safeBypassedAcoustic=0"));
        assertTrue(summary.contains("safeBypassedDoppler=0"));
    }

    @Test
    void knownPropellerStillExemptFromDedupeThrottleAndRateLimit() {
        SoundPhysicsConfig config = config();
        SoundPhysicsSoundPolicy.SoundContext propeller = propeller();

        assertTrue(SoundPhysicsSoundPolicy.isImpactBurstDedupeExempt(config, propeller));
        assertTrue(SoundPhysicsSoundPolicy.isStartThrottleExempt(propeller));
        assertTrue(SoundPhysicsSoundPolicy.isSoundRateLimitExempt(propeller));
    }

    private SoundPhysicsSoundPolicy.SoundContext propeller() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL,
                SoundSource.AMBIENT,
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS,
                false,
                false,
                false,
                false,
                true
        );
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

}
