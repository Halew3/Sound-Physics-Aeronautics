package com.sonicether.soundphysics.propeller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PropellerFarFieldEffectTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        PropellerLongRangeAudio.clearForTests();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void farFieldUsesHybridLogProgressAndGameplayGain() {
        SoundPhysicsMod.CONFIG = config();
        PropellerLongRangeParameters parameters = PropellerLongRangeAudio.parameters(896.0D);

        PropellerFarFieldEffect near = PropellerFarFieldEffect.compute(30.0D, 896.0D, 1.0D, parameters, 0.1F);
        PropellerFarFieldEffect mid = PropellerFarFieldEffect.compute(120.0D, 896.0D, 1.0D, parameters, 0.1F);
        PropellerFarFieldEffect far = PropellerFarFieldEffect.compute(200.0D, 896.0D, 1.0D, parameters, 0.1F);
        PropellerFarFieldEffect full = PropellerFarFieldEffect.compute(280.0D, 896.0D, 1.0D, parameters, 0.1F);

        assertEquals(0.0D, near.farField(), 0.001D);
        assertEquals(1.0F, near.directCutoffMultiplier(), 0.001F);
        assertEquals(1.0F, near.directGainMultiplier(), 0.001F);
        assertEquals(0.376D, mid.farField(), 0.001D);
        assertEquals(0.729F, mid.directCutoffMultiplier(), 0.001F);
        assertEquals(0.368F, mid.directGainMultiplier(), 0.001F);
        assertEquals(0.752D, far.farField(), 0.001D);
        assertEquals(0.458F, far.directCutoffMultiplier(), 0.001F);
        assertEquals(1.0D, full.farField(), 0.001D);
        assertEquals(0.28F, full.directCutoffMultiplier(), 0.001F);
        assertEquals(1.85F, full.airAbsorption(), 0.001F);
        assertTrue(mid.directGainMultiplier() < 1.0F);
        assertTrue(far.directGainMultiplier() < mid.directGainMultiplier());
    }

    @Test
    void highSourceVolumeIsCompensatedAfterCloseDistance() {
        SoundPhysicsMod.CONFIG = config();
        PropellerLongRangeParameters parameters = PropellerLongRangeAudio.parameters(896.0D);

        PropellerFarFieldEffect effect = PropellerFarFieldEffect.compute(120.0D, 896.0D, 3.328D, parameters, 0.1F);

        assertEquals(0.368D, effect.extraGameplayGain(), 0.001D);
        assertEquals(0.774D, effect.volumeCompensation(), 0.001D);
        assertEquals(0.285F, effect.directGainMultiplier(), 0.001F);
        assertEquals(0.239D, effect.finalEstimatedGain(), 0.001D);
    }

    @Test
    void disabledConfigLeavesDirectPathUnchanged() {
        SoundPhysicsConfig config = config();
        config.propellerFarFieldEnabled.set(false);
        SoundPhysicsMod.CONFIG = config;

        PropellerFarFieldEffect effect = PropellerFarFieldEffect.compute(
                1000.0D,
                1000.0D,
                1.0D,
                PropellerLongRangeAudio.parameters(1000.0D),
                0.2F
        );

        assertEquals(0.0D, effect.farField(), 0.001D);
        assertEquals(1.0F, effect.directCutoffMultiplier(), 0.001F);
        assertEquals(1.0F, effect.directGainMultiplier(), 0.001F);
        assertEquals(0.2F, effect.airAbsorption(), 0.001F);
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }
}
