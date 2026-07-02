package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DopplerSoundPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void skipsSableDelegatedSoundsByDefaultUsingClassNameOnly() {
        DopplerSoundPolicy.Result result = DopplerSoundPolicy.evaluate(
                config(),
                SoundSource.BLOCKS,
                DopplerSoundPolicy.SABLE_MOVING_SOUND_INSTANCE_DELEGATE,
                false,
                false
        );

        assertFalse(result.apply());
        assertEquals(DopplerSoundPolicy.SkipReason.SABLE_DELEGATE, result.skipReason());
    }

    @Test
    void allowsSableDelegatedSoundsWhenExplicitlyEnabled() {
        SoundPhysicsConfig config = config();
        config.dopplerApplyToSableDelegatedSounds.set(true);

        DopplerSoundPolicy.Result result = DopplerSoundPolicy.evaluate(
                config,
                SoundSource.BLOCKS,
                DopplerSoundPolicy.SABLE_MOVING_SOUND_INSTANCE_DELEGATE,
                false,
                false
        );

        assertTrue(result.apply());
    }

    @Test
    void positionalAmbientSoundsRemainOptInAfterAmbientIsEnabled() {
        SoundPhysicsConfig config = config();
        config.dopplerApplyToAmbientSounds.set(true);

        DopplerSoundPolicy.Result result = DopplerSoundPolicy.evaluate(config, SoundSource.AMBIENT, "example.AmbientLoop", false, false);

        assertFalse(result.apply());
        assertEquals(DopplerSoundPolicy.SkipReason.POSITIONAL_AMBIENT, result.skipReason());
    }

    @Test
    void positionalAmbientSoundsCanBeExplicitlyEnabled() {
        SoundPhysicsConfig config = config();
        config.dopplerApplyToAmbientSounds.set(true);
        config.dopplerApplyToPositionalAmbientSounds.set(true);

        assertTrue(DopplerSoundPolicy.evaluate(config, SoundSource.AMBIENT, "example.AmbientLoop", false, false).apply());
    }

    @Test
    void relativeAndNoAttenuationSoundsAreSkippedBeforeCategory() {
        SoundPhysicsConfig config = config();

        assertEquals(
                DopplerSoundPolicy.SkipReason.RELATIVE,
                DopplerSoundPolicy.evaluate(config, SoundSource.BLOCKS, "example.Sound", true, false).skipReason()
        );
        assertEquals(
                DopplerSoundPolicy.SkipReason.NO_ATTENUATION,
                DopplerSoundPolicy.evaluate(config, SoundSource.BLOCKS, "example.Sound", false, true).skipReason()
        );
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

}
