package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DopplerMathTest {

    private static final Vec3 SOURCE = new Vec3(0.0D, 0.0D, 0.0D);
    private static final Vec3 LISTENER = new Vec3(10.0D, 0.0D, 0.0D);
    private static final double SPEED_OF_SOUND = 343.0D;

    @Test
    void sourceMovingTowardListenerIncreasesPitch() {
        double multiplier = multiplier(new Vec3(20.0D, 0.0D, 0.0D), Vec3.ZERO);

        assertTrue(multiplier > 1.0D);
    }

    @Test
    void sourceMovingAwayFromListenerDecreasesPitch() {
        double multiplier = multiplier(new Vec3(-20.0D, 0.0D, 0.0D), Vec3.ZERO);

        assertTrue(multiplier < 1.0D);
    }

    @Test
    void listenerMovingTowardSourceIncreasesPitch() {
        double multiplier = multiplier(Vec3.ZERO, new Vec3(-20.0D, 0.0D, 0.0D));

        assertTrue(multiplier > 1.0D);
    }

    @Test
    void listenerMovingAwayFromSourceDecreasesPitch() {
        double multiplier = multiplier(Vec3.ZERO, new Vec3(20.0D, 0.0D, 0.0D));

        assertTrue(multiplier < 1.0D);
    }

    @Test
    void sourceAndListenerSameVelocityGivesNeutralPitch() {
        Vec3 velocity = new Vec3(30.0D, 4.0D, -2.0D);

        double multiplier = multiplier(velocity, velocity);

        assertEquals(1.0D, multiplier, 1.0E-9D);
    }

    @Test
    void clampsAtMinimumAndMaximum() {
        double maximum = multiplier(new Vec3(342.0D, 0.0D, 0.0D), Vec3.ZERO, 0.75D, 1.25D);
        double minimum = multiplier(new Vec3(-900.0D, 0.0D, 0.0D), Vec3.ZERO, 0.75D, 1.25D);

        assertEquals(1.25D, maximum, 1.0E-9D);
        assertEquals(0.75D, minimum, 1.0E-9D);
    }

    @Test
    void handlesZeroDistance() {
        double multiplier = DopplerMath.computeMultiplier(
                SOURCE,
                new Vec3(100.0D, 0.0D, 0.0D),
                SOURCE,
                Vec3.ZERO,
                SPEED_OF_SOUND,
                1.0D,
                0.5D,
                2.0D
        );

        assertEquals(1.0D, multiplier, 1.0E-9D);
    }

    @Test
    void handlesNearSupersonicDenominatorSafely() {
        double multiplier = DopplerMath.computeMultiplier(
                SOURCE,
                new Vec3(342.999D, 0.0D, 0.0D),
                LISTENER,
                Vec3.ZERO,
                SPEED_OF_SOUND,
                1.0D,
                0.5D,
                4.0D
        );

        assertTrue(Double.isFinite(multiplier));
        assertTrue(multiplier <= 4.0D);
    }

    @Test
    void smoothingMovesTowardTargetWithoutOvershooting() {
        double rising = DopplerMath.smoothMultiplier(1.0D, 2.0D, 0.05D, 0.1D);
        double falling = DopplerMath.smoothMultiplier(2.0D, 1.0D, 0.05D, 0.1D);

        assertTrue(rising > 1.0D);
        assertTrue(rising < 2.0D);
        assertTrue(falling < 2.0D);
        assertTrue(falling > 1.0D);
    }

    private static double multiplier(Vec3 sourceVelocity, Vec3 listenerVelocity) {
        return multiplier(sourceVelocity, listenerVelocity, 0.5D, 2.0D);
    }

    private static double multiplier(Vec3 sourceVelocity, Vec3 listenerVelocity, double min, double max) {
        return DopplerMath.computeMultiplier(
                SOURCE,
                sourceVelocity,
                LISTENER,
                listenerVelocity,
                SPEED_OF_SOUND,
                1.0D,
                min,
                max
        );
    }

}
