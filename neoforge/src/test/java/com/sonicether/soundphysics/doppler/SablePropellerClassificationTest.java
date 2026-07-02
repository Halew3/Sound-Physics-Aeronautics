package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SablePropellerClassificationTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        DopplerEngine.clearForTests();
        DiagnosticRuntimeOverrides.clear();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void unresolvedSableDelegateIsNotReportedAsRootStatic() {
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        DopplerEngine.onPlaySource(12, Vec3.ZERO, SoundSource.AMBIENT, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL, false, false, sableDelegateClass(), false, true);

        String joined = String.join("\n", DopplerEngine.sourcesDiagnosticsLines(DopplerEngine.SourceQuery.propeller(12)));

        assertTrue(joined.contains("sublevelClassification=SABLE_DELEGATED_UNRESOLVED"));
        assertTrue(joined.contains("isOnSableSublevel=unknown"));
    }

    @Test
    void confirmedSableSourceReportsSublevelConfirmed() {
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        DopplerEngine.onPlaySource(12, Vec3.ZERO, SoundSource.AMBIENT, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL, false, false, sableDelegateClass(), false, true);
        DopplerEngine.rememberAcousticSpaceForTests(12, "sable:test_space", 42L);

        String joined = String.join("\n", DopplerEngine.sourcesDiagnosticsLines(DopplerEngine.SourceQuery.propeller(12)));

        assertTrue(joined.contains("sublevelClassification=SUBLEVEL_CONFIRMED"));
        assertTrue(joined.contains("isOnSableSublevel=true"));
        assertTrue(joined.contains("acousticSpace=sable:test_space@42"));
    }

    @Test
    void rootPropellerReportsRootStatic() {
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        DopplerEngine.onPlaySource(12, Vec3.ZERO, SoundSource.AMBIENT, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL, false, false, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS, false, true);

        String joined = String.join("\n", DopplerEngine.sourcesDiagnosticsLines(DopplerEngine.SourceQuery.propeller(12)));

        assertTrue(joined.contains("sublevelClassification=ROOT_STATIC"));
        assertTrue(joined.contains("isOnSableSublevel=false"));
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
