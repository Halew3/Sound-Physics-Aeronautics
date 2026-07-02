package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsPolicyDiagnostics;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PropellerSourceDiagnosticsTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        DopplerEngine.clearForTests();
        DiagnosticRuntimeOverrides.clear();
        SoundPhysicsPolicyDiagnostics.reset();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void safeModeExplainsMissingTrackedPropellerSource() {
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerSafe();

        DopplerEngine.onPlaySource(11, Vec3.ZERO, SoundSource.AMBIENT, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL, false, false, sableDelegateClass(), false, true);

        String joined = String.join("\n", DopplerEngine.sourcesDiagnosticsLines(DopplerEngine.SourceQuery.propeller(12)));

        assertTrue(joined.contains("No tracked propeller Doppler sources."));
        assertTrue(joined.contains("Current mode=propeller_safe bypasses propeller Doppler tracking; this is expected."));
        assertTrue(joined.contains("Latest propeller seen:"));
        assertTrue(joined.contains("class=MovingSoundInstanceDelegate"));
        assertTrue(joined.contains("decision=PROPELLER_SAFE_MODE"));
    }

    @Test
    void propellerSourcesShowSableClassificationAndPitchFields() {
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerDebug();

        DopplerEngine.onPlaySource(12, new Vec3(1.0D, 2.0D, 3.0D), SoundSource.AMBIENT, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE, false, false, sableDelegateClass(), false, true);

        List<String> lines = DopplerEngine.sourcesDiagnosticsLines(DopplerEngine.SourceQuery.propeller(12));
        String joined = String.join("\n", lines);

        assertTrue(joined.contains("isKnownPropeller=true"));
        assertTrue(joined.contains("isSableDelegated=true"));
        assertTrue(joined.contains("pitchControlSource=NONE"));
        assertTrue(joined.contains("forcedPitchActive=false"));
        assertTrue(joined.contains("sableVelocityMayApply=true"));
        assertTrue(joined.contains("propeller summary:"));
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    private static String sableDelegateClass() {
        return "dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate";
    }
}
