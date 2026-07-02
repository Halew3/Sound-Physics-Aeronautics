package com.sonicether.soundphysics.doppler;

import net.minecraft.world.phys.Vec3;

public final class DopplerMath {

    private static final double MIN_DISTANCE_SQUARED = 1.0E-6D;
    private static final double MIN_SAFE_SPEED_OF_SOUND = 1.0D;
    private static final double MIN_WAVE_TERM_FACTOR = 0.05D;

    private DopplerMath() {
    }

    public static double computeMultiplier(
            Vec3 sourcePos,
            Vec3 sourceVel,
            Vec3 listenerPos,
            Vec3 listenerVel,
            double speedOfSound,
            double strength,
            double minMultiplier,
            double maxMultiplier
    ) {
        if (!isFinite(sourcePos) || !isFinite(sourceVel) || !isFinite(listenerPos) || !isFinite(listenerVel)) {
            return 1.0D;
        }

        Vec3 delta = listenerPos.subtract(sourcePos);
        if (delta.lengthSqr() < MIN_DISTANCE_SQUARED) {
            return 1.0D;
        }

        Vec3 sourceToListener = delta.normalize();
        double sourceTowardListener = sourceVel.dot(sourceToListener);
        double listenerAwayFromSource = listenerVel.dot(sourceToListener);
        double safeSpeedOfSound = Math.max(speedOfSound, MIN_SAFE_SPEED_OF_SOUND);
        double safeStrength = Math.max(strength, 0.0D);

        double numerator = safeSpeedOfSound - listenerAwayFromSource * safeStrength;
        double denominator = safeSpeedOfSound - sourceTowardListener * safeStrength;
        double minimumWaveTerm = safeSpeedOfSound * MIN_WAVE_TERM_FACTOR;

        numerator = Math.max(numerator, minimumWaveTerm);
        denominator = Math.max(denominator, minimumWaveTerm);

        double multiplier = numerator / denominator;
        return clamp(multiplier, minMultiplier, maxMultiplier);
    }

    public static double radialVelocity(Vec3 sourcePos, Vec3 sourceVel, Vec3 listenerPos, Vec3 listenerVel) {
        return -relativeSpeedAlongLineOfSight(sourcePos, sourceVel, listenerPos, listenerVel);
    }

    public static double relativeSpeedAlongLineOfSight(Vec3 sourcePos, Vec3 sourceVel, Vec3 listenerPos, Vec3 listenerVel) {
        if (!isFinite(sourcePos) || !isFinite(sourceVel) || !isFinite(listenerPos) || !isFinite(listenerVel)) {
            return 0.0D;
        }
        Vec3 delta = listenerPos.subtract(sourcePos);
        if (delta.lengthSqr() < MIN_DISTANCE_SQUARED) {
            return 0.0D;
        }
        Vec3 sourceToListener = delta.normalize();
        double sourceTowardListener = sourceVel.dot(sourceToListener);
        double listenerAwayFromSource = listenerVel.dot(sourceToListener);
        return sourceTowardListener - listenerAwayFromSource;
    }

    public static double smoothMultiplier(double previous, double target, double dtSeconds, double smoothingTimeSeconds) {
        if (!Double.isFinite(previous)) {
            previous = 1.0D;
        }
        if (!Double.isFinite(target)) {
            target = 1.0D;
        }
        if (smoothingTimeSeconds <= 0.0D || dtSeconds <= 0.0D) {
            return target;
        }

        double alpha = 1.0D - Math.exp(-dtSeconds / smoothingTimeSeconds);
        return previous + (target - previous) * clamp(alpha, 0.0D, 1.0D);
    }

    private static double clamp(double value, double configuredMin, double configuredMax) {
        double min = Math.min(configuredMin, configuredMax);
        double max = Math.max(configuredMin, configuredMax);

        if (!Double.isFinite(min)) {
            min = 0.5D;
        }
        if (!Double.isFinite(max)) {
            max = 2.0D;
        }
        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
        }

        return Math.max(min, Math.min(max, value));
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

}
