package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordAcousticUpdateTest {

    private static final ResourceLocation RECORD = ResourceLocation.fromNamespaceAndPath("minecraft", "music_disc.far");

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        DiagnosticRuntimeOverrides.clear();
        RecordDiagnostics.reset();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void recordTestModeEnablesPeriodicRecordAcoustics() {
        SoundPhysicsConfig config = config();
        DiagnosticRuntimeOverrides.enableRecordTest();

        assertTrue(SoundPhysicsSoundPolicy.shouldUpdateRecordAcoustics(config, record(false)));
    }

    @Test
    void normalModeDefaultDoesNotEnablePeriodicRecordAcoustics() {
        SoundPhysicsConfig config = config();

        assertFalse(SoundPhysicsSoundPolicy.shouldUpdateRecordAcoustics(config, record(false)));
    }

    @Test
    void unsafeModeAllowsNoAttenuationRecords() {
        SoundPhysicsConfig config = config();
        DiagnosticRuntimeOverrides.enableRecordTestUnsafe();

        assertTrue(SoundPhysicsSoundPolicy.shouldUpdateRecordAcoustics(config, record(true)));
    }

    @Test
    void movingUpdateSeenAndOcclusionFieldsUpdate() {
        RecordDiagnostics.observeSource(7, Vec3.ZERO, record(false).withStartEvent(false));
        RecordDiagnostics.recordAcousticProcessed(7, 8.0D, 0.42F, 0.91F, 0.1F, 0.2F, 0.0F, 0.0F);

        String lines = String.join("\n", RecordDiagnostics.sourceLines(8));

        assertTrue(lines.contains("movingUpdateSeen=true"));
        assertTrue(lines.contains("directCutoff=0.420"));
        assertTrue(lines.contains("occlusion=8.000"));
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    private SoundPhysicsSoundPolicy.SoundContext record(boolean noAttenuation) {
        return new SoundPhysicsSoundPolicy.SoundContext(RECORD, SoundSource.RECORDS, "example.RecordSound", false, noAttenuation, true, false, false);
    }
}
