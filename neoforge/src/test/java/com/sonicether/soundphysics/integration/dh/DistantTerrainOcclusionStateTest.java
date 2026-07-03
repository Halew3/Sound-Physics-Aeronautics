package com.sonicether.soundphysics.integration.dh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DistantTerrainOcclusionStateTest {

    @AfterEach
    void reset() {
        DistantHorizonsAudioBridge.clearForTests();
    }

    @Test
    void probeIntervalIsClampedAndMovementReprobesAfterMinimumInterval() {
        DistantTerrainOcclusionState state = new DistantTerrainOcclusionState(42);
        Vec3 listener = new Vec3(0.0D, 80.0D, 0.0D);
        Vec3 source = new Vec3(500.0D, 80.0D, 0.0D);

        assertTrue(state.shouldProbe(100L, listener, source, 1));
        state.recordMiss(100L, listener, source, 500.0D, "ray_miss", 0.55D, 0.45D);

        assertFalse(state.shouldProbe(109L, listener.add(40.0D, 0.0D, 0.0D), source, 1));
        assertTrue(state.shouldProbe(110L, listener.add(40.0D, 0.0D, 0.0D), source, 200));
        assertFalse(state.shouldProbe(150L, listener, source, 200));
        assertTrue(state.shouldProbe(300L, listener, source, 200));

        assertEquals(10, DistantTerrainOcclusionState.clampProbeInterval(1));
        assertEquals(20, DistantTerrainOcclusionState.clampProbeInterval(20));
        assertEquals(200, DistantTerrainOcclusionState.clampProbeInterval(500));
    }

    @Test
    void strengthWeightsDistancePlacementAndDetail() {
        double mid = DistantTerrainOcclusionState.computeStrength(192.0D, 2048.0D, 0.80D, 512.0D, 256.0D, (byte) 5);
        double nearListener = DistantTerrainOcclusionState.computeStrength(192.0D, 2048.0D, 0.80D, 512.0D, 4.0D, (byte) 5);
        double nearSource = DistantTerrainOcclusionState.computeStrength(192.0D, 2048.0D, 0.80D, 512.0D, 504.0D, (byte) 5);
        double tooClose = DistantTerrainOcclusionState.computeStrength(192.0D, 2048.0D, 0.80D, 100.0D, 50.0D, (byte) 5);
        double highDetail = DistantTerrainOcclusionState.computeStrength(192.0D, 2048.0D, 0.80D, 512.0D, 256.0D, (byte) 9);

        assertTrue(mid > 0.0D);
        assertTrue(mid < 0.80D);
        assertTrue(nearListener < mid);
        assertEquals(0.0D, nearSource, 0.001D);
        assertEquals(0.0D, tooClose, 0.001D);
        assertTrue(highDetail > mid);
    }

    @Test
    void hitAndMissSmoothingPreventAbruptMultiplierChanges() {
        DistantTerrainOcclusionState state = new DistantTerrainOcclusionState(7);
        Vec3 listener = new Vec3(0.0D, 80.0D, 0.0D);
        Vec3 source = new Vec3(500.0D, 80.0D, 0.0D);

        DistantTerrainOcclusionResult firstHit = state.recordHit(1L, listener, source, 500.0D, 250.0D, (byte) 5, 0.80D, 0.55D, 0.45D);
        assertEquals(0.160D, firstHit.occlusionStrength(), 0.001D);
        assertEquals(0.928F, firstHit.directGainMultiplier(), 0.001F);
        assertEquals(0.912F, firstHit.directCutoffMultiplier(), 0.001F);

        DistantTerrainOcclusionResult secondHit = state.recordHit(20L, listener, source, 500.0D, 250.0D, (byte) 5, 0.80D, 0.55D, 0.45D);
        assertEquals(0.288D, secondHit.occlusionStrength(), 0.001D);
        assertTrue(secondHit.directGainMultiplier() < firstHit.directGainMultiplier());

        DistantTerrainOcclusionResult miss = state.recordMiss(40L, listener, source, 500.0D, "ray_miss", 0.55D, 0.45D);
        assertEquals(0.265D, miss.occlusionStrength(), 0.001D);
        assertTrue(miss.directGainMultiplier() > secondHit.directGainMultiplier());
    }

    @Test
    void missingApiIsReportedWithoutLoadingRuntime() {
        ClassLoader emptyLoader = new ClassLoader(null) {
        };

        DistantHorizonsAudioBridge.Availability availability = DistantHorizonsAudioBridge.checkApiAvailability(emptyLoader);

        assertFalse(availability.available());
        assertEquals("dh_not_loaded", availability.reason());
    }

}
