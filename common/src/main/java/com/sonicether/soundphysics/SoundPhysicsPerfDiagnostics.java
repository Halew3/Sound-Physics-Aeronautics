package com.sonicether.soundphysics;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class SoundPhysicsPerfDiagnostics {

    private static final AtomicLong soundStartsTotal = new AtomicLong();
    private static final AtomicLong soundStartsThisTick = new AtomicLong();
    private static final AtomicLong soundStartsMaxPerTick = new AtomicLong();
    private static final AtomicLong soundStartsThrottled = new AtomicLong();
    private static final AtomicLong impactBurstDeduped = new AtomicLong();
    private static final AtomicLong processSoundCalls = new AtomicLong();
    private static final AtomicLong processSoundMaxNanos = new AtomicLong();
    private static final AtomicLong evaluateEnvironmentCalls = new AtomicLong();
    private static final AtomicLong evaluateEnvironmentMaxNanos = new AtomicLong();
    private static final AtomicLong calculateOcclusionCalls = new AtomicLong();
    private static final AtomicLong calculateOcclusionMaxNanos = new AtomicLong();
    private static final AtomicLong runOcclusionCalls = new AtomicLong();
    private static final AtomicLong runOcclusionMaxNanos = new AtomicLong();
    private static final AtomicLong acousticRayCasts = new AtomicLong();
    private static final AtomicLong acousticRayCastMaxNanos = new AtomicLong();
    private static final AtomicLong sableRayCandidateTests = new AtomicLong();
    private static final AtomicLong sableSnapshotBuilds = new AtomicLong();
    private static final AtomicLong sableSnapshotMaxNanos = new AtomicLong();
    private static final AtomicLong debugRaysQueuedTotal = new AtomicLong();
    private static final AtomicLong debugRaysQueuedThisTick = new AtomicLong();
    private static final AtomicLong debugRaysThrottled = new AtomicLong();
    private static final AtomicLong debugRaysDrawnTotal = new AtomicLong();
    private static final AtomicLong debugRaysDrawnThisTick = new AtomicLong();
    private static final AtomicLong rayRenderCalls = new AtomicLong();
    private static final AtomicLong rayRenderMaxNanos = new AtomicLong();
    private static final AtomicLong adaptiveReflectionBudgetResolutions = new AtomicLong();
    private static final AtomicLong adaptiveReflectionBudgetReductions = new AtomicLong();
    private static final AtomicLong adaptiveReflectionConfiguredRays = new AtomicLong();
    private static final AtomicLong adaptiveReflectionEffectiveRays = new AtomicLong();
    private static final AtomicLong adaptiveReflectionConfiguredBounces = new AtomicLong();
    private static final AtomicLong adaptiveReflectionEffectiveBounces = new AtomicLong();
    private static final AtomicLong adaptiveReflectionLegacy = new AtomicLong();
    private static final AtomicLong adaptiveReflectionNearFull = new AtomicLong();
    private static final AtomicLong adaptiveReflectionFarReduced = new AtomicLong();
    private static final AtomicLong adaptiveReflectionContinuousLoop = new AtomicLong();
    private static final AtomicLong adaptiveReflectionPropeller = new AtomicLong();
    private static final AtomicLong adaptiveReflectionCrosswindLoop = new AtomicLong();

    private static long soundStartTick = Long.MIN_VALUE;
    private static long debugRayTick = Long.MIN_VALUE;
    private static long debugDrawTick = Long.MIN_VALUE;

    private SoundPhysicsPerfDiagnostics() {
    }

    public static synchronized boolean recordSoundStart(long gameTime, int maxStartsPerTick) {
        if (gameTime != soundStartTick) {
            soundStartTick = gameTime;
            soundStartsThisTick.set(0L);
        }
        long current = soundStartsThisTick.incrementAndGet();
        soundStartsTotal.incrementAndGet();
        updateMax(soundStartsMaxPerTick, current);
        if (maxStartsPerTick > 0 && current > maxStartsPerTick) {
            soundStartsThrottled.incrementAndGet();
            return false;
        }
        return true;
    }

    public static synchronized boolean recordDebugRayQueued(int maxRaysPerTick) {
        long gameTime = currentGameTime();
        if (gameTime != debugRayTick) {
            debugRayTick = gameTime;
            debugRaysQueuedThisTick.set(0L);
        }
        long current = debugRaysQueuedThisTick.incrementAndGet();
        if (maxRaysPerTick > 0 && current > maxRaysPerTick) {
            debugRaysThrottled.incrementAndGet();
            return false;
        }
        debugRaysQueuedTotal.incrementAndGet();
        return true;
    }

    public static synchronized void recordDebugRaysDrawn(long count) {
        long gameTime = currentGameTime();
        if (gameTime != debugDrawTick) {
            debugDrawTick = gameTime;
            debugRaysDrawnThisTick.set(0L);
        }
        debugRaysDrawnThisTick.addAndGet(count);
        debugRaysDrawnTotal.addAndGet(count);
    }

    public static void recordImpactBurstDeduped() {
        impactBurstDeduped.incrementAndGet();
    }

    public static void recordProcessSound(long nanos) {
        processSoundCalls.incrementAndGet();
        updateMax(processSoundMaxNanos, nanos);
    }

    public static void recordEvaluateEnvironment(long nanos) {
        evaluateEnvironmentCalls.incrementAndGet();
        updateMax(evaluateEnvironmentMaxNanos, nanos);
    }

    public static void recordCalculateOcclusion(long nanos) {
        calculateOcclusionCalls.incrementAndGet();
        updateMax(calculateOcclusionMaxNanos, nanos);
    }

    public static void recordRunOcclusion(long nanos) {
        runOcclusionCalls.incrementAndGet();
        updateMax(runOcclusionMaxNanos, nanos);
    }

    public static void recordAcousticRayCast(long nanos) {
        acousticRayCasts.incrementAndGet();
        updateMax(acousticRayCastMaxNanos, nanos);
    }

    public static void recordSableRayCandidateTest() {
        sableRayCandidateTests.incrementAndGet();
    }

    public static void recordSableSnapshotBuild(long nanos) {
        sableSnapshotBuilds.incrementAndGet();
        updateMax(sableSnapshotMaxNanos, nanos);
    }

    public static void recordRayRender(long nanos) {
        rayRenderCalls.incrementAndGet();
        updateMax(rayRenderMaxNanos, nanos);
    }

    public static void recordAdaptiveReflectionBudget(AdaptiveReflectionBudget.Budget budget) {
        adaptiveReflectionBudgetResolutions.incrementAndGet();
        if (budget.reduced()) {
            adaptiveReflectionBudgetReductions.incrementAndGet();
        }
        adaptiveReflectionConfiguredRays.addAndGet(budget.configuredRays());
        adaptiveReflectionEffectiveRays.addAndGet(budget.rays());
        adaptiveReflectionConfiguredBounces.addAndGet(budget.configuredBounces());
        adaptiveReflectionEffectiveBounces.addAndGet(budget.bounces());
        switch (budget.reason()) {
            case LEGACY -> adaptiveReflectionLegacy.incrementAndGet();
            case NEAR_FULL -> adaptiveReflectionNearFull.incrementAndGet();
            case FAR_REDUCED -> adaptiveReflectionFarReduced.incrementAndGet();
            case CONTINUOUS_LOOP -> adaptiveReflectionContinuousLoop.incrementAndGet();
            case PROPELLER -> adaptiveReflectionPropeller.incrementAndGet();
            case CROSSWIND_LOOP -> adaptiveReflectionCrosswindLoop.incrementAndGet();
        }
    }

    public static String summaryText() {
        return "soundStarts(total=" + soundStartsTotal.get()
                + ", thisTick=" + soundStartsThisTick.get()
                + ", maxPerTick=" + soundStartsMaxPerTick.get()
                + ", throttled=" + soundStartsThrottled.get()
                + ", impactDeduped=" + impactBurstDeduped.get() + ")"
                + ", rays(total=" + acousticRayCasts.get()
                + ", maxMs=" + millis(acousticRayCastMaxNanos.get())
                + ", sableCandidateTests=" + sableRayCandidateTests.get() + ")"
                + ", debugRays(queuedTotal=" + debugRaysQueuedTotal.get()
                + ", queuedThisTick=" + debugRaysQueuedThisTick.get()
                + ", drawnTotal=" + debugRaysDrawnTotal.get()
                + ", drawnThisTick=" + debugRaysDrawnThisTick.get()
                + ", throttled=" + debugRaysThrottled.get() + ")"
                + ", adaptiveReflectionBudget(total=" + adaptiveReflectionBudgetResolutions.get()
                + ", reduced=" + adaptiveReflectionBudgetReductions.get()
                + ", rays=" + adaptiveReflectionConfiguredRays.get() + "->" + adaptiveReflectionEffectiveRays.get()
                + ", bounces=" + adaptiveReflectionConfiguredBounces.get() + "->" + adaptiveReflectionEffectiveBounces.get()
                + ", reasons(legacy=" + adaptiveReflectionLegacy.get()
                + ", near_full=" + adaptiveReflectionNearFull.get()
                + ", far_reduced=" + adaptiveReflectionFarReduced.get()
                + ", continuous_loop=" + adaptiveReflectionContinuousLoop.get()
                + ", propeller=" + adaptiveReflectionPropeller.get()
                + ", crosswind_loop=" + adaptiveReflectionCrosswindLoop.get() + "))"
                + ", maxMs(process=" + millis(processSoundMaxNanos.get())
                + ", evaluate=" + millis(evaluateEnvironmentMaxNanos.get())
                + ", occlusion=" + millis(calculateOcclusionMaxNanos.get())
                + ", runOcclusion=" + millis(runOcclusionMaxNanos.get())
                + ", sableSnapshot=" + millis(sableSnapshotMaxNanos.get())
                + ", rayRender=" + millis(rayRenderMaxNanos.get()) + ")";
    }

    public static String shortSummaryText() {
        return "soundsThisTick=" + soundStartsThisTick.get()
                + ", soundsMaxPerTick=" + soundStartsMaxPerTick.get()
                + ", soundsThrottled=" + soundStartsThrottled.get()
                + ", rays=" + acousticRayCasts.get()
                + ", adaptiveBudgetReduced=" + adaptiveReflectionBudgetReductions.get()
                + ", debugQueuedThisTick=" + debugRaysQueuedThisTick.get()
                + ", worstEvaluateMs=" + millis(evaluateEnvironmentMaxNanos.get());
    }

    public static void reset() {
        soundStartsTotal.set(0L);
        soundStartsThisTick.set(0L);
        soundStartsMaxPerTick.set(0L);
        soundStartsThrottled.set(0L);
        impactBurstDeduped.set(0L);
        processSoundCalls.set(0L);
        processSoundMaxNanos.set(0L);
        evaluateEnvironmentCalls.set(0L);
        evaluateEnvironmentMaxNanos.set(0L);
        calculateOcclusionCalls.set(0L);
        calculateOcclusionMaxNanos.set(0L);
        runOcclusionCalls.set(0L);
        runOcclusionMaxNanos.set(0L);
        acousticRayCasts.set(0L);
        acousticRayCastMaxNanos.set(0L);
        sableRayCandidateTests.set(0L);
        sableSnapshotBuilds.set(0L);
        sableSnapshotMaxNanos.set(0L);
        debugRaysQueuedTotal.set(0L);
        debugRaysQueuedThisTick.set(0L);
        debugRaysThrottled.set(0L);
        debugRaysDrawnTotal.set(0L);
        debugRaysDrawnThisTick.set(0L);
        rayRenderCalls.set(0L);
        rayRenderMaxNanos.set(0L);
        adaptiveReflectionBudgetResolutions.set(0L);
        adaptiveReflectionBudgetReductions.set(0L);
        adaptiveReflectionConfiguredRays.set(0L);
        adaptiveReflectionEffectiveRays.set(0L);
        adaptiveReflectionConfiguredBounces.set(0L);
        adaptiveReflectionEffectiveBounces.set(0L);
        adaptiveReflectionLegacy.set(0L);
        adaptiveReflectionNearFull.set(0L);
        adaptiveReflectionFarReduced.set(0L);
        adaptiveReflectionContinuousLoop.set(0L);
        adaptiveReflectionPropeller.set(0L);
        adaptiveReflectionCrosswindLoop.set(0L);
        soundStartTick = Long.MIN_VALUE;
        debugRayTick = Long.MIN_VALUE;
        debugDrawTick = Long.MIN_VALUE;
    }

    public static String soundStartThrottleReason(@Nullable ResourceLocation sound) {
        return "sound start throttle max=" + SoundPhysicsMod.CONFIG.soundPhysicsMaxSoundStartsPerTick.get() + " sound=" + sound;
    }

    private static void updateMax(AtomicLong target, long value) {
        long previous;
        do {
            previous = target.get();
            if (value <= previous) {
                return;
            }
        } while (!target.compareAndSet(previous, value));
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    private static long currentGameTime() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
    }

}
