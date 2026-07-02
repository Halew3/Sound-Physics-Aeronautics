package com.sonicether.soundphysics.propeller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PropellerProfileResolverTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        PropellerLongRangeAudio.clearForTests();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void resolvesAeronauticsBearingFieldsWithoutCompileDependency() {
        SoundPhysicsMod.CONFIG = config();
        FakePropellerSound sound = new FakePropellerSound(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE,
                new FakeBearing(32, 64.0D)
        );

        PropellerAudioProfile profile = PropellerAudioProfileResolver.resolve(sound, sound.getLocation(), 1.0F, 1.0F);

        assertEquals(32, profile.sailCount());
        assertEquals(192.0D, profile.rpm(), 0.001D);
        assertEquals("reflection:be.totalSailPower", profile.sizeSource());
        assertEquals("reflection:be.getAngularSpeed", profile.rpmSource());
        assertEquals(597.863D, profile.computedMaxDistance(), 0.001D);
    }

    @Test
    void fallsBackToSoundIdSizeAndPitchProxyRpm() {
        SoundPhysicsMod.CONFIG = config();

        PropellerAudioProfile small = PropellerAudioProfileResolver.resolve(
                null,
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL,
                0.8F,
                1.0F
        );
        PropellerAudioProfile large = PropellerAudioProfileResolver.fallbackForContext(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE
        );

        assertEquals(16, small.sailCount());
        assertEquals(96.0D, small.rpm(), 0.001D);
        assertEquals("sound_id_fallback", small.sizeSource());
        assertEquals("pitch_proxy", small.rpmSource());
        assertEquals(48, large.sailCount());
        assertEquals(192.0D, large.rpm(), 0.001D);
        assertEquals(896.0D, large.computedMaxDistance(), 0.001D);
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    private static final class FakeBearing {
        public final int totalSailPower;
        private final double angularSpeed;

        private FakeBearing(int totalSailPower, double angularSpeed) {
            this.totalSailPower = totalSailPower;
            this.angularSpeed = angularSpeed;
        }

        public double getAngularSpeed() {
            return angularSpeed;
        }
    }

    private static final class FakePropellerSound implements SoundInstance {
        public final FakeBearing be;
        private final ResourceLocation soundId;

        private FakePropellerSound(ResourceLocation soundId, FakeBearing be) {
            this.soundId = soundId;
            this.be = be;
        }

        @Override
        public ResourceLocation getLocation() {
            return soundId;
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return null;
        }

        @Nullable
        @Override
        public Sound getSound() {
            return null;
        }

        @Override
        public SoundSource getSource() {
            return SoundSource.AMBIENT;
        }

        @Override
        public boolean isLooping() {
            return true;
        }

        @Override
        public boolean isRelative() {
            return false;
        }

        @Override
        public int getDelay() {
            return 0;
        }

        @Override
        public float getVolume() {
            return 1.0F;
        }

        @Override
        public float getPitch() {
            return 1.0F;
        }

        @Override
        public double getX() {
            return 0.0D;
        }

        @Override
        public double getY() {
            return 0.0D;
        }

        @Override
        public double getZ() {
            return 0.0D;
        }

        @Override
        public Attenuation getAttenuation() {
            return Attenuation.LINEAR;
        }
    }
}
