package com.sonicether.soundphysics.propeller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PropellerLongRangeFormulaTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        PropellerLongRangeAudio.clearForTests();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void defaultFormulaMatchesRequestedExamples() {
        SoundPhysicsConfig config = config();

        assertEquals(189.908D, range(config, 16, 64), 0.001D);
        assertEquals(259.504D, range(config, 16, 128), 0.001D);
        assertEquals(322.152D, range(config, 16, 192), 0.001D);
        assertEquals(304.395D, range(config, 32, 64), 0.001D);
        assertEquals(458.837D, range(config, 32, 128), 0.001D);
        assertEquals(597.863D, range(config, 32, 192), 0.001D);
        assertEquals(428.195D, range(config, 48, 64), 0.001D);
        assertEquals(674.385D, range(config, 48, 128), 0.001D);
        assertEquals(896.000D, range(config, 48, 192), 0.001D);
    }

    @Test
    void referenceDistanceUsesConfiguredFractionAndClamp() {
        SoundPhysicsMod.CONFIG = config();

        PropellerLongRangeParameters small = PropellerLongRangeAudio.parameters(322.152057923649D);
        PropellerLongRangeParameters large = PropellerLongRangeAudio.parameters(896.0D);

        assertEquals(8.0D, small.referenceDistance(), 0.001D);
        assertEquals(16.128D, large.referenceDistance(), 0.001D);
        assertEquals(1.35D, large.rolloffFactor(), 0.001D);
    }

    @Test
    void legacyProfilePreservesPreviousFormulaAndOpenAlDefaults() {
        SoundPhysicsConfig config = config();
        config.propellerLongRangeProfile.set("legacy");
        SoundPhysicsMod.CONFIG = config;

        assertEquals(306.588D, range(config, 16, 64), 0.001D);
        assertEquals(550.380D, range(config, 16, 192), 0.001D);
        assertEquals(1024.000D, range(config, 48, 192), 0.001D);

        PropellerLongRangeParameters large = PropellerLongRangeAudio.parameters(1024.0D);

        assertEquals(40.96D, large.referenceDistance(), 0.001D);
        assertEquals(1.0D, large.rolloffFactor(), 0.001D);
    }

    @Test
    void balancedProfileMigratesOldGeneratedDefaults() {
        SoundPhysicsConfig config = config();
        config.propellerLongRangeProfile.set("balanced");
        config.propellerLongRangeMaxDistance.set(1024.0D);
        config.propellerLongRangeSizeExponent.set(0.65D);
        config.propellerLongRangeRpmExponent.set(0.70D);
        config.propellerLongRangeReferenceDistanceFraction.set(0.04D);
        config.propellerLongRangeReferenceDistanceMin.set(24.0D);
        config.propellerLongRangeReferenceDistanceMax.set(48.0D);
        config.propellerLongRangeRolloffFactor.set(1.0D);
        SoundPhysicsMod.CONFIG = config;

        assertEquals(322.152D, range(config, 16, 192), 0.001D);

        PropellerLongRangeParameters large = PropellerLongRangeAudio.parameters(896.0D);

        assertEquals(16.128D, large.referenceDistance(), 0.001D);
        assertEquals(1.35D, large.rolloffFactor(), 0.001D);
    }

    private double range(SoundPhysicsConfig config, int sails, double rpm) {
        return PropellerAudioProfileResolver.profile(sails, rpm, "test", "test", config).computedMaxDistance();
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }
}
