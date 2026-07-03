package com.sonicether.soundphysics.integration.dh;

import java.util.Locale;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DistantTerrainOcclusionState {

    public static final int MIN_PROBE_INTERVAL_TICKS = 10;
    public static final int MAX_PROBE_INTERVAL_TICKS = 200;
    public static final double MOVEMENT_REPROBE_BLOCKS = 32.0D;
    private static final double MOVEMENT_REPROBE_BLOCKS_SQR = MOVEMENT_REPROBE_BLOCKS * MOVEMENT_REPROBE_BLOCKS;
    private static final double HIT_SMOOTHING_ALPHA = 0.20D;
    private static final double MISS_SMOOTHING_ALPHA = 0.08D;

    private final int sourceId;
    private long lastProbeGameTime = Long.MIN_VALUE;
    private Vec3 lastListenerPos;
    private Vec3 lastSourcePos;
    private double smoothedStrength;
    private DistantTerrainOcclusionResult lastResult;
    private int consecutiveFailures;

    public DistantTerrainOcclusionState(int sourceId) {
        this.sourceId = sourceId;
    }

    public boolean shouldProbe(long gameTime, Vec3 listenerPos, Vec3 sourcePos, int configuredIntervalTicks) {
        if (lastResult == null || lastProbeGameTime == Long.MIN_VALUE) {
            return true;
        }

        long elapsed = gameTime - lastProbeGameTime;
        if (elapsed < MIN_PROBE_INTERVAL_TICKS) {
            return false;
        }
        if (elapsed >= clampProbeInterval(configuredIntervalTicks)) {
            return true;
        }
        return movedEnough(lastListenerPos, listenerPos) || movedEnough(lastSourcePos, sourcePos);
    }

    public DistantTerrainOcclusionResult currentResult(
            String reason,
            double sourceDistance,
            double gainAtFullOcclusion,
            double cutoffAtFullOcclusion
    ) {
        if (lastResult == null || smoothedStrength <= 0.0D) {
            return DistantTerrainOcclusionResult.identity(true, false, sourceDistance, reason);
        }
        return result(
                true,
                false,
                lastResult.hit(),
                lastResult.hitDistance(),
                lastResult.detailLevel(),
                sourceDistance,
                smoothedStrength,
                gainAtFullOcclusion,
                cutoffAtFullOcclusion,
                reason
        );
    }

    public DistantTerrainOcclusionResult recordHit(
            long gameTime,
            Vec3 listenerPos,
            Vec3 sourcePos,
            double sourceDistance,
            double hitDistance,
            byte detailLevel,
            double rawStrength,
            double gainAtFullOcclusion,
            double cutoffAtFullOcclusion
    ) {
        rememberProbe(gameTime, listenerPos, sourcePos);
        consecutiveFailures = 0;
        smoothedStrength = Mth.lerp(HIT_SMOOTHING_ALPHA, smoothedStrength, Mth.clamp(rawStrength, 0.0D, 1.0D));
        lastResult = result(
                true,
                true,
                true,
                hitDistance,
                detailLevel,
                sourceDistance,
                smoothedStrength,
                gainAtFullOcclusion,
                cutoffAtFullOcclusion,
                "ray_hit"
        );
        return lastResult;
    }

    public DistantTerrainOcclusionResult recordMiss(
            long gameTime,
            Vec3 listenerPos,
            Vec3 sourcePos,
            double sourceDistance,
            String reason,
            double gainAtFullOcclusion,
            double cutoffAtFullOcclusion
    ) {
        rememberProbe(gameTime, listenerPos, sourcePos);
        consecutiveFailures = 0;
        return fade(gameTime, sourceDistance, reason, true, gainAtFullOcclusion, cutoffAtFullOcclusion);
    }

    public DistantTerrainOcclusionResult recordFailure(
            long gameTime,
            Vec3 listenerPos,
            Vec3 sourcePos,
            double sourceDistance,
            String reason,
            double gainAtFullOcclusion,
            double cutoffAtFullOcclusion
    ) {
        rememberProbe(gameTime, listenerPos, sourcePos);
        consecutiveFailures++;
        return fade(gameTime, sourceDistance, reason, false, gainAtFullOcclusion, cutoffAtFullOcclusion);
    }

    public String diagnosticsFields() {
        DistantTerrainOcclusionResult result = lastResult;
        if (result == null) {
            return " dhKnown=false"
                    + " dhQueried=false"
                    + " dhHit=false"
                    + " dhStrength=0.000"
                    + " dhGainMul=1.000"
                    + " dhCutoffMul=1.000"
                    + " dhHitDistance=unknown"
                    + " dhDetail=unknown"
                    + " dhFailures=0"
                    + " dhReason=no_state";
        }
        return " dhKnown=true"
                + " dhQueried=" + result.queried()
                + " dhHit=" + result.hit()
                + " dhStrength=" + format(result.occlusionStrength())
                + " dhGainMul=" + format(result.directGainMultiplier())
                + " dhCutoffMul=" + format(result.directCutoffMultiplier())
                + " dhHitDistance=" + (result.hitDistance() < 0.0D ? "unknown" : format(result.hitDistance()))
                + " dhDetail=" + (result.detailLevel() < 0 ? "unknown" : Byte.toString(result.detailLevel()))
                + " dhFailures=" + consecutiveFailures
                + " dhReason=" + result.reason();
    }

    public int sourceId() {
        return sourceId;
    }

    public DistantTerrainOcclusionResult lastResult() {
        return lastResult;
    }

    public double smoothedStrength() {
        return smoothedStrength;
    }

    public static int clampProbeInterval(int configuredIntervalTicks) {
        return Mth.clamp(configuredIntervalTicks, MIN_PROBE_INTERVAL_TICKS, MAX_PROBE_INTERVAL_TICKS);
    }

    public static double computeStrength(
            double minDistance,
            double maxRayLength,
            double maxStrength,
            double sourceDistance,
            double hitDistance,
            byte detailLevel
    ) {
        double distanceFar = smoothstep(minDistance, Math.max(minDistance + 1.0D, maxRayLength), sourceDistance);
        double hitPlacement = 1.0D - Mth.clamp(hitDistance / Math.max(sourceDistance, 1.0D), 0.0D, 1.0D);
        double placementWeight = smoothstep(0.05D, 0.25D, hitPlacement)
                * (1.0D - smoothstep(0.85D, 1.0D, hitPlacement));
        double detailWeight = Mth.clamp(0.65D + 0.05D * detailLevel, 0.65D, 1.0D);
        return Mth.clamp(maxStrength * distanceFar * placementWeight * detailWeight, 0.0D, maxStrength);
    }

    static double smoothstep(double edge0, double edge1, double value) {
        if (edge1 <= edge0) {
            return value >= edge1 ? 1.0D : 0.0D;
        }
        double x = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private DistantTerrainOcclusionResult fade(
            long gameTime,
            double sourceDistance,
            String reason,
            boolean queried,
            double gainAtFullOcclusion,
            double cutoffAtFullOcclusion
    ) {
        smoothedStrength = Mth.lerp(MISS_SMOOTHING_ALPHA, smoothedStrength, 0.0D);
        lastResult = result(
                true,
                queried,
                false,
                -1.0D,
                (byte) -1,
                sourceDistance,
                smoothedStrength,
                gainAtFullOcclusion,
                cutoffAtFullOcclusion,
                reason
        );
        return lastResult;
    }

    private void rememberProbe(long gameTime, Vec3 listenerPos, Vec3 sourcePos) {
        lastProbeGameTime = gameTime;
        lastListenerPos = listenerPos;
        lastSourcePos = sourcePos;
    }

    private static boolean movedEnough(Vec3 previous, Vec3 current) {
        return previous == null || current == null || previous.distanceToSqr(current) > MOVEMENT_REPROBE_BLOCKS_SQR;
    }

    private static DistantTerrainOcclusionResult result(
            boolean available,
            boolean queried,
            boolean hit,
            double hitDistance,
            byte detailLevel,
            double sourceDistance,
            double strength,
            double gainAtFullOcclusion,
            double cutoffAtFullOcclusion,
            String reason
    ) {
        double clampedStrength = Mth.clamp(strength, 0.0D, 1.0D);
        float gainMultiplier = multiplier(clampedStrength, gainAtFullOcclusion);
        float cutoffMultiplier = multiplier(clampedStrength, cutoffAtFullOcclusion);
        return new DistantTerrainOcclusionResult(
                available,
                queried,
                hit,
                hitDistance,
                detailLevel,
                sourceDistance,
                clampedStrength,
                gainMultiplier,
                cutoffMultiplier,
                reason
        );
    }

    private static float multiplier(double strength, double atFullOcclusion) {
        return (float) Mth.lerp(strength, 1.0D, Mth.clamp(atFullOcclusion, 0.0D, 1.0D));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

}
