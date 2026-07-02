package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RadialVelocityDiagnosticsTest {

    @Test
    void stationaryReliableMotionIsNearOne() {
        Vec3 source = new Vec3(20.0D, 0.0D, 0.0D);
        Vec3 listener = Vec3.ZERO;

        double radialVelocity = DopplerMath.radialVelocity(source, Vec3.ZERO, listener, Vec3.ZERO);
        double multiplier = DopplerMath.computeMultiplier(source, Vec3.ZERO, listener, Vec3.ZERO, 343.0D, 1.0D, 0.5D, 2.0D);

        assertEquals(0.0D, radialVelocity, 1.0E-9D);
        assertEquals(1.0D, multiplier, 1.0E-9D);
    }

    @Test
    void approachingSourceHasNegativeRadialVelocityAndHigherMultiplier() {
        Vec3 source = new Vec3(20.0D, 0.0D, 0.0D);
        Vec3 listener = Vec3.ZERO;
        Vec3 sourceVelocity = new Vec3(-20.0D, 0.0D, 0.0D);

        double radialVelocity = DopplerMath.radialVelocity(source, sourceVelocity, listener, Vec3.ZERO);
        double closingSpeed = DopplerMath.relativeSpeedAlongLineOfSight(source, sourceVelocity, listener, Vec3.ZERO);
        double multiplier = DopplerMath.computeMultiplier(source, sourceVelocity, listener, Vec3.ZERO, 343.0D, 1.0D, 0.5D, 2.0D);

        assertTrue(radialVelocity < 0.0D);
        assertTrue(closingSpeed > 0.0D);
        assertTrue(multiplier > 1.0D);
    }

    @Test
    void recedingSourceHasPositiveRadialVelocityAndLowerMultiplier() {
        Vec3 source = new Vec3(20.0D, 0.0D, 0.0D);
        Vec3 listener = Vec3.ZERO;
        Vec3 sourceVelocity = new Vec3(20.0D, 0.0D, 0.0D);

        double radialVelocity = DopplerMath.radialVelocity(source, sourceVelocity, listener, Vec3.ZERO);
        double multiplier = DopplerMath.computeMultiplier(source, sourceVelocity, listener, Vec3.ZERO, 343.0D, 1.0D, 0.5D, 2.0D);

        assertTrue(radialVelocity > 0.0D);
        assertTrue(multiplier < 1.0D);
    }
}
