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
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PropellerProcessingDistanceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        PropellerLongRangeAudio.clearForTests();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void eligiblePropellerExtendsProcessingDistanceToComputedRange() {
        SoundPhysicsMod.CONFIG = config();

        double normal = PropellerLongRangeAudio.effectiveProcessingDistance(3, nonPropellerContext(), 512.0D);
        double propeller = PropellerLongRangeAudio.effectiveProcessingDistance(3, largeAeronauticsContext(), 512.0D);

        assertEquals(512.0D, normal, 0.001D);
        assertEquals(896.000D, propeller, 0.001D);
    }

    @Test
    void configuredPropellerProcessingCapIsAppliedAboveNormalCap() {
        SoundPhysicsConfig config = config();
        config.propellerLongRangeMaxProcessingDistance.set(400.0D);
        SoundPhysicsMod.CONFIG = config;

        double distance = PropellerLongRangeAudio.effectiveProcessingDistance(3, largeAeronauticsContext(), 512.0D);

        assertEquals(512.0D, distance, 0.001D);
    }

    @Test
    void crosswindVehiclePropellersRequireExplicitOptIn() {
        SoundPhysicsConfig config = config();
        SoundPhysicsMod.CONFIG = config;
        SoundPhysicsSoundPolicy.SoundContext crosswind = new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("crosswind", "vehicle.propeller.loop"),
                SoundSource.AMBIENT,
                "crosswind.VehiclePropellerSound",
                false,
                false,
                false,
                false,
                true
        );

        assertFalse(PropellerLongRangeAudio.isEligible(crosswind));

        config.propellerLongRangeApplyToCrosswindVehiclePropellers.set(true);

        assertTrue(PropellerLongRangeAudio.isEligible(crosswind));
    }

    private SoundPhysicsSoundPolicy.SoundContext aeronauticsContext() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL,
                SoundSource.AMBIENT,
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS,
                false,
                false,
                false,
                false,
                true
        );
    }

    private SoundPhysicsSoundPolicy.SoundContext largeAeronauticsContext() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE,
                SoundSource.AMBIENT,
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS,
                false,
                false,
                false,
                false,
                true
        );
    }

    private SoundPhysicsSoundPolicy.SoundContext nonPropellerContext() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.pling"),
                SoundSource.BLOCKS,
                "example.Sound",
                false,
                false,
                false,
                false,
                false
        );
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }
}
