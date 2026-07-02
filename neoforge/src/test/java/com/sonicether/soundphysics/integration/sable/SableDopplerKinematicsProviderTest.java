package com.sonicether.soundphysics.integration.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.sonicether.soundphysics.doppler.DopplerKinematicState;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SableDopplerKinematicsProviderTest {

    @Test
    void containingSableSpaceReturnsReliablePointVelocity() {
        FakeAcousticSpace space = new FakeAcousticSpace("sable:test", SableAcousticBounds.of(0D, 0D, 0D, 8D, 8D, 8D))
                .withPointVelocity(new Vec3(12.0D, 0.0D, 0.0D));
        SableAcousticSnapshot snapshot = snapshot(space);

        DopplerKinematicState state = SableDopplerKinematicsProvider.stateFor(null, snapshot, new Vec3(2.0D, 2.0D, 2.0D));

        assertTrue(state.reliable());
        assertEquals("sable:test", state.acousticSpaceId());
        assertEquals(12.0D, state.worldVelocity().x, 1.0E-6D);
        assertEquals(42L, state.version());
    }

    @Test
    void unreliablePointVelocityRemainsUnreliableWithSpaceIdentity() {
        FakeAcousticSpace space = new FakeAcousticSpace("sable:test", SableAcousticBounds.of(0D, 0D, 0D, 8D, 8D, 8D))
                .withUnreliablePointVelocity(Vec3.ZERO);
        SableAcousticSnapshot snapshot = snapshot(space);

        DopplerKinematicState state = SableDopplerKinematicsProvider.stateFor(null, snapshot, new Vec3(2.0D, 2.0D, 2.0D));

        assertFalse(state.reliable());
        assertEquals("sable:test", state.acousticSpaceId());
    }

    @Test
    void missingSnapshotFallsBackToUnreliableRoot() {
        DopplerKinematicState state = SableDopplerKinematicsProvider.stateFor(null, null, Vec3.ZERO);

        assertFalse(state.reliable());
        assertEquals("root", state.acousticSpaceId());
        assertEquals(0L, state.version());
    }

    @Test
    void pointOutsideSableSpacesFallsBackToUnreliableRootWithSnapshotVersion() {
        FakeAcousticSpace space = new FakeAcousticSpace("sable:test", SableAcousticBounds.of(0D, 0D, 0D, 8D, 8D, 8D))
                .withPointVelocity(new Vec3(12.0D, 0.0D, 0.0D));

        DopplerKinematicState state = SableDopplerKinematicsProvider.stateFor(null, snapshot(space), new Vec3(20.0D, 2.0D, 2.0D));

        assertFalse(state.reliable());
        assertEquals("root", state.acousticSpaceId());
        assertEquals(42L, state.version());
    }

    private static SableAcousticSnapshot snapshot(SableAcousticSpace... spaces) {
        return new SableAcousticSnapshot(null, 10L, 42L, 42L, List.of(spaces), spaces.length, 0, 0, 0);
    }

}
