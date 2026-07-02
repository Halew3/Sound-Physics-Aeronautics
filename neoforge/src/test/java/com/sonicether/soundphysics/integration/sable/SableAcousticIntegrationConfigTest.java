package com.sonicether.soundphysics.integration.sable;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Path;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.acoustic.AcousticScenes;
import com.sonicether.soundphysics.acoustic.RootAcousticWorldProvider;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;
import com.sonicether.soundphysics.doppler.DopplerKinematics;
import com.sonicether.soundphysics.doppler.RootDopplerKinematicsProvider;

import de.maxhenkel.configbuilder.ConfigBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SableAcousticIntegrationConfigTest {

    @TempDir
    Path tempDir;

    private SoundPhysicsConfig previousConfig;

    @BeforeEach
    void captureConfig() {
        previousConfig = SoundPhysicsMod.CONFIG;
    }

    @AfterEach
    void restoreConfig() {
        SoundPhysicsMod.CONFIG = previousConfig;
        AcousticScenes.setProvider(null);
        DopplerKinematics.setProvider(null);
        SableAcousticSnapshotManager.clear();
    }

    @Test
    void disabledSableAcousticsLeavesRootProvidersAfterInit() {
        SoundPhysicsMod.CONFIG = config();
        SoundPhysicsMod.CONFIG.sableAcousticsEnabled.set(false);

        SableAcousticIntegration.init();

        assertInstanceOf(RootAcousticWorldProvider.class, AcousticScenes.getProvider());
        assertInstanceOf(RootDopplerKinematicsProvider.class, DopplerKinematics.getProvider());
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

}
