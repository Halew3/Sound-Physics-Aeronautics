package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DopplerSourceVelocityTest {

    private static final ResourceLocation SOUND = ResourceLocation.fromNamespaceAndPath("sound_physics_test", "moving_source");

    @Test
    void sourcePositionDeltaProducesVelocity() {
        DopplerSourceState state = state();
        state.sampleVelocity(Vec3.ZERO, 1L, 350.0D, "root", 0L);

        DopplerKinematicState second = state.sampleVelocity(new Vec3(1.0D, 0.0D, 0.0D), 2L, 350.0D, "root", 0L);

        assertTrue(second.reliable());
        assertEquals(20.0D, second.worldVelocity().x, 1.0E-9D);
        assertEquals(DopplerSourceState.VelocitySource.SOUND_POSITION_DELTA, state.velocitySource());
    }

    @Test
    void stationarySourceRemainsReliableWithZeroVelocity() {
        DopplerSourceState state = state();
        state.sampleVelocity(Vec3.ZERO, 1L, 350.0D, "root", 0L);

        DopplerKinematicState second = state.sampleVelocity(Vec3.ZERO, 2L, 350.0D, "root", 0L);

        assertTrue(second.reliable());
        assertEquals(0.0D, second.worldVelocity().lengthSqr(), 1.0E-12D);
    }

    @Test
    void movingSourceMultiplierDiffersFromOne() {
        double multiplier = DopplerMath.computeMultiplier(
                new Vec3(20.0D, 0.0D, 0.0D),
                new Vec3(-20.0D, 0.0D, 0.0D),
                Vec3.ZERO,
                Vec3.ZERO,
                343.0D,
                1.0D,
                0.5D,
                2.0D
        );

        assertTrue(multiplier > 1.0D);
    }

    @Test
    void rejectedOutlierReportsReason() {
        DopplerSourceState state = state();
        state.sampleVelocity(Vec3.ZERO, 1L, 10.0D, "root", 0L);

        DopplerKinematicState outlier = state.sampleVelocity(new Vec3(1000.0D, 0.0D, 0.0D), 2L, 10.0D, "root", 0L);

        assertFalse(outlier.reliable());
        assertEquals("velocity_outlier", state.rejectedVelocityReason());
    }

    @Test
    void sameTickSampleDoesNotDestroyReliability() {
        DopplerSourceState state = state();
        state.sampleVelocity(Vec3.ZERO, 1L, 350.0D, "root", 0L);
        state.sampleVelocity(new Vec3(1.0D, 0.0D, 0.0D), 2L, 350.0D, "root", 0L);

        DopplerKinematicState sameTick = state.sampleVelocity(new Vec3(2.0D, 0.0D, 0.0D), 2L, 350.0D, "root", 0L);

        assertTrue(sameTick.reliable());
        assertTrue(state.reliableVelocity());
    }

    private static DopplerSourceState state() {
        return new DopplerSourceState(3, SOUND, SoundSource.BLOCKS, Vec3.ZERO);
    }

}
