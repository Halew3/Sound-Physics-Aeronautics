package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DopplerDiagnosticsTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void resetEngine() {
        DopplerEngine.clearForTests();
        DiagnosticRuntimeOverrides.clear();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void summaryIncludesCountersAndAverageMultiplier() {
        DopplerDiagnostics diagnostics = new DopplerDiagnostics();

        diagnostics.recordSourceRegistered();
        diagnostics.recordSourceReuseReset();
        diagnostics.recordSourceUpdated(10L);
        diagnostics.recordSkippedByCategory();
        diagnostics.recordSkippedByRelativeOrNoAttenuation();
        diagnostics.recordSkippedBySableDelegate();
        diagnostics.recordSkippedByPositionalAmbientPolicy();
        diagnostics.recordSkippedByDisabledConfig();
        diagnostics.recordUnreliableListenerVelocity();
        diagnostics.recordUnreliableSourceVelocity();
        diagnostics.recordPitchUpdateApplied();
        diagnostics.recordPitchUpdateSkippedUnchanged();
        diagnostics.recordMultiplier(1.10D);
        diagnostics.recordMultiplier(0.90D);

        DopplerDiagnosticsSummary summary = diagnostics.summary(3L);

        assertEquals(3L, summary.sourcesTracked());
        assertEquals(1L, summary.sourcesRegistered());
        assertEquals(1L, summary.sourceReuseResets());
        assertEquals(1L, summary.sourcesUpdatedThisTick());
        assertEquals(1L, summary.sourcesUpdatedTotal());
        assertEquals(1L, summary.sourcesSkippedByCategory());
        assertEquals(1L, summary.sourcesSkippedByRelativeOrNoAttenuation());
        assertEquals(1L, summary.sourcesSkippedBySableDelegate());
        assertEquals(1L, summary.sourcesSkippedByPositionalAmbientPolicy());
        assertEquals(1L, summary.sourcesSkippedByDisabledConfig());
        assertEquals(1L, summary.unreliableListenerVelocityEvents());
        assertEquals(1L, summary.unreliableSourceVelocityEvents());
        assertEquals(1L, summary.pitchUpdatesApplied());
        assertEquals(1L, summary.pitchUpdatesSkippedUnchanged());
        assertEquals(1.0D, summary.averageMultiplier(), 1.0E-9D);
        assertEquals(0.90D, summary.latestMultiplier(), 1.0E-9D);
    }

    @Test
    void sourceDetailsAppearInDiagnostics() {
        SoundPhysicsMod.CONFIG = config();
        ResourceLocation sound = ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.pling");

        DopplerEngine.onPlaySource(42, new Vec3(1D, 2D, 3D), SoundSource.BLOCKS, sound, false, false, "example.BlockMachineSound", false, false);

        String sources = DopplerEngine.sourcesDiagnosticsText(4);
        assertTrue(sources.contains("source=42"));
        assertTrue(sources.contains("sound=minecraft:block.note_block.pling"));
        assertTrue(sources.contains("category=BLOCKS"));
        assertTrue(sources.contains("class=BlockMachineSound"));
        assertTrue(sources.contains("pitchDecision=NEVER_EVALUATED"));
    }

    @Test
    void debugOverrideChangesEffectiveValues() {
        SoundPhysicsConfig config = config();

        DiagnosticRuntimeOverrides.enableDopplerDebug();

        assertTrue(DiagnosticRuntimeOverrides.dopplerEnabled(config));
        assertEquals(3.0D, DiagnosticRuntimeOverrides.effectiveDopplerStrength(config), 1.0E-9D);
        assertEquals(50, DiagnosticRuntimeOverrides.effectiveDopplerSmoothingTimeMs(config));
    }

    @Test
    void simulateCommandTextProvesApproachAndRecedeDirection() {
        SoundPhysicsMod.CONFIG = config();

        String approach = DopplerEngine.simulateText(true);
        String recede = DopplerEngine.simulateText(false);

        assertTrue(approach.contains("approach"));
        assertTrue(approach.contains("expected=>1 higher pitch"));
        assertTrue(recede.contains("recede"));
        assertTrue(recede.contains("expected=<1 lower pitch"));
        assertTrue(simulatedMultiplier(true) > 1.0D);
        assertTrue(simulatedMultiplier(false) < 1.0D);
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    private double simulatedMultiplier(boolean approach) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        double speed = 20.0D;
        Vec3 sourcePosition = new Vec3(20.0D, 0.0D, 0.0D);
        Vec3 sourceVelocity = approach ? new Vec3(-speed, 0.0D, 0.0D) : new Vec3(speed, 0.0D, 0.0D);
        return DopplerMath.computeMultiplier(
                sourcePosition,
                sourceVelocity,
                Vec3.ZERO,
                Vec3.ZERO,
                config.dopplerSpeedOfSound.get(),
                DiagnosticRuntimeOverrides.effectiveDopplerStrength(config),
                DiagnosticRuntimeOverrides.effectiveDopplerMinPitchMultiplier(config),
                DiagnosticRuntimeOverrides.effectiveDopplerMaxPitchMultiplier(config)
        );
    }

}
