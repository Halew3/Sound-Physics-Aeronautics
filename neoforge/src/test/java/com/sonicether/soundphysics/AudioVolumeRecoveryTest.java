package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;
import com.sonicether.soundphysics.doppler.DopplerEngine;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioVolumeRecoveryTest {

    private static final ResourceLocation RECORD = ResourceLocation.fromNamespaceAndPath("minecraft", "music_disc.far");

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        AudioSourceRecovery.resetForTests();
        DopplerEngine.clearAudioStateForRecovery("test cleanup");
        RecordDiagnostics.reset();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void masterVolumeZeroSuspendsSourceUpdates() {
        AudioSourceRecovery.observeVolume(SoundSource.MASTER, 1.0F);
        AudioSourceRecovery.observeVolume(SoundSource.MASTER, 0.0F);

        assertTrue(AudioSourceRecovery.sourceUpdatesSuspended(SoundSource.BLOCKS));
        assertTrue(AudioSourceRecovery.statusText().contains("volumeMuteEpoch=1"));
        assertTrue(AudioSourceRecovery.statusText().contains("MASTER entered zero volume"));
    }

    @Test
    void volumeReturnClearsTrackedSourceState() {
        SoundPhysicsMod.CONFIG = config();
        DopplerEngine.onPlaySource(11, Vec3.ZERO, SoundSource.RECORDS, RECORD, false, false, "example.RecordSound", false, false);
        RecordDiagnostics.observeSource(11, Vec3.ZERO, recordContext());

        AudioSourceRecovery.observeVolume(SoundSource.MASTER, 1.0F);
        AudioSourceRecovery.observeVolume(SoundSource.MASTER, 0.0F);
        AudioSourceRecovery.observeVolume(SoundSource.MASTER, 1.0F);
        AudioSourceRecovery.runPendingRecoveryForTests();

        assertEquals(0, DopplerEngine.trackedSourceCount());
        assertEquals(0, RecordDiagnostics.trackedSourceCount());
        assertTrue(AudioSourceRecovery.sourcesText().contains("lastRecoveryReason=volume MASTER returned from zero"));
    }

    @Test
    void manualRecoverIsIdempotentAndDoesNotChangeConfig() {
        SoundPhysicsConfig config = config();
        config.soundPhysicsApplyToRecords.set(true);
        SoundPhysicsMod.CONFIG = config;

        String first = AudioSourceRecovery.recover("test manual", false);
        String second = AudioSourceRecovery.recover("test manual", false);

        assertTrue(first.contains("cleared stale Doppler/record/propeller"));
        assertTrue(second.contains("cleared stale Doppler/record/propeller"));
        assertTrue(config.soundPhysicsApplyToRecords.get());
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    private SoundPhysicsSoundPolicy.SoundContext recordContext() {
        return new SoundPhysicsSoundPolicy.SoundContext(RECORD, SoundSource.RECORDS, "example.RecordSound", false, false, true, true, false);
    }
}
