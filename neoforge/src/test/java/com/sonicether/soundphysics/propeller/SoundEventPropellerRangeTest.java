package com.sonicether.soundphysics.propeller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundEventPropellerRangeTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        PropellerLongRangeAudio.clearForTests();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void propellerSoundEventMixinUsesFallbackProfileRanges() {
        SoundPhysicsMod.CONFIG = config();

        PropellerAudioProfile small = PropellerAudioProfileResolver.fallbackForContext(SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL);
        PropellerAudioProfile large = PropellerAudioProfileResolver.fallbackForContext(SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE);

        assertEquals(322.152D, small.computedMaxDistance(), 0.001D);
        assertEquals(896.0D, large.computedMaxDistance(), 0.001D);
        assertTrue(SoundPhysicsSoundPolicy.isAeronauticsPropeller(SoundPhysicsSoundPolicy.SoundContext.of(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE,
                null
        )));
        assertFalse(SoundPhysicsSoundPolicy.isAeronauticsPropeller(SoundPhysicsSoundPolicy.SoundContext.of(
                ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.pling"),
                null
        )));
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }
}
