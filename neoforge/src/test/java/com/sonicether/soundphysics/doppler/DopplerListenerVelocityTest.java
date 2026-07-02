package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DopplerListenerVelocityTest {

    @Test
    void listenerBecomesReliableAfterTwoCameraSamples() {
        DopplerListenerState state = new DopplerListenerState();

        DopplerKinematicState first = state.sample(Vec3.ZERO, 10L, 250.0D, "root", 0L, DopplerListenerState.VelocitySource.CAMERA_DELTA);
        DopplerKinematicState second = state.sample(new Vec3(1.0D, 0.0D, 0.0D), 11L, 250.0D, "root", 0L, DopplerListenerState.VelocitySource.CAMERA_DELTA);

        assertFalse(first.reliable());
        assertTrue(second.reliable());
        assertEquals(20.0D, second.worldVelocity().x, 1.0E-9D);
        assertEquals(DopplerListenerState.VelocitySource.CAMERA_DELTA, state.velocitySource());
        assertEquals("none", state.rejectedReason());
    }

    @Test
    void sameTickSampleDoesNotDestroyReliability() {
        DopplerListenerState state = new DopplerListenerState();
        state.sample(Vec3.ZERO, 10L, 250.0D, "root", 0L);
        state.sample(new Vec3(1.0D, 0.0D, 0.0D), 11L, 250.0D, "root", 0L);

        DopplerKinematicState sameTick = state.sample(new Vec3(2.0D, 0.0D, 0.0D), 11L, 250.0D, "root", 0L);

        assertTrue(sameTick.reliable());
        assertTrue(state.reliable());
        assertEquals("none", state.rejectedReason());
    }

    @Test
    void playerDeltaFallbackCanBeAcceptedAsReliable() {
        DopplerListenerState state = new DopplerListenerState();

        state.acceptExternal(Vec3.ZERO, new Vec3(0.5D, 0.0D, 0.0D).scale(20.0D), 42L, DopplerListenerState.VelocitySource.PLAYER_DELTA);

        assertTrue(state.reliable());
        assertEquals(DopplerListenerState.VelocitySource.PLAYER_DELTA, state.velocitySource());
        assertEquals(10.0D, state.sampledVelocity().x, 1.0E-9D);
    }

    @Test
    void resetDoesNotPreventFutureReliableSamples() {
        DopplerListenerState state = new DopplerListenerState();
        state.reset();

        state.sample(Vec3.ZERO, 1L, 250.0D, "root", 0L);
        DopplerKinematicState second = state.sample(new Vec3(0.25D, 0.0D, 0.0D), 2L, 250.0D, "root", 0L);

        assertTrue(second.reliable());
    }

}
