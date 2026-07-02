package com.sonicether.soundphysics.doppler;

import java.util.concurrent.atomic.AtomicLong;

public final class DopplerDiagnostics {

    private final AtomicLong sourcesRegistered = new AtomicLong();
    private final AtomicLong sourceReuseResets = new AtomicLong();
    private final AtomicLong sourcesUpdatedTotal = new AtomicLong();
    private final AtomicLong sourcesUpdatedThisTick = new AtomicLong();
    private final AtomicLong sourcesSkippedByCategory = new AtomicLong();
    private final AtomicLong sourcesSkippedByRelativeOrNoAttenuation = new AtomicLong();
    private final AtomicLong sourcesSkippedBySableDelegate = new AtomicLong();
    private final AtomicLong sourcesSkippedByPositionalAmbientPolicy = new AtomicLong();
    private final AtomicLong sourcesSkippedByDisabledConfig = new AtomicLong();
    private final AtomicLong unreliableListenerVelocityEvents = new AtomicLong();
    private final AtomicLong unreliableSourceVelocityEvents = new AtomicLong();
    private final AtomicLong pitchUpdatesApplied = new AtomicLong();
    private final AtomicLong pitchUpdatesSkippedUnchanged = new AtomicLong();
    private final AtomicLong multiplierSamples = new AtomicLong();
    private final AtomicLong multiplierTotalScaled = new AtomicLong();
    private final AtomicLong latestMultiplierScaled = new AtomicLong(10000L);

    private long currentTick = Long.MIN_VALUE;

    void recordSourceRegistered() {
        sourcesRegistered.incrementAndGet();
    }

    void recordSourceReuseReset() {
        sourceReuseResets.incrementAndGet();
    }

    synchronized void recordSourceUpdated(long gameTime) {
        if (gameTime != currentTick) {
            currentTick = gameTime;
            sourcesUpdatedThisTick.set(0L);
        }

        sourcesUpdatedThisTick.incrementAndGet();
        sourcesUpdatedTotal.incrementAndGet();
    }

    void recordSkippedByCategory() {
        sourcesSkippedByCategory.incrementAndGet();
    }

    void recordSkippedByRelativeOrNoAttenuation() {
        sourcesSkippedByRelativeOrNoAttenuation.incrementAndGet();
    }

    void recordSkippedBySableDelegate() {
        sourcesSkippedBySableDelegate.incrementAndGet();
    }

    void recordSkippedByPositionalAmbientPolicy() {
        sourcesSkippedByPositionalAmbientPolicy.incrementAndGet();
    }

    void recordSkippedByDisabledConfig() {
        sourcesSkippedByDisabledConfig.incrementAndGet();
    }

    void recordUnreliableListenerVelocity() {
        unreliableListenerVelocityEvents.incrementAndGet();
    }

    void recordUnreliableSourceVelocity() {
        unreliableSourceVelocityEvents.incrementAndGet();
    }

    void recordPitchUpdateApplied() {
        pitchUpdatesApplied.incrementAndGet();
    }

    void recordPitchUpdateSkippedUnchanged() {
        pitchUpdatesSkippedUnchanged.incrementAndGet();
    }

    void recordMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier)) {
            return;
        }

        multiplierSamples.incrementAndGet();
        long scaled = Math.round(multiplier * 10000.0D);
        multiplierTotalScaled.addAndGet(scaled);
        latestMultiplierScaled.set(scaled);
    }

    DopplerDiagnosticsSummary summary(long sourcesTracked) {
        long sampleCount = multiplierSamples.get();
        double averageMultiplier = sampleCount == 0L ? 1.0D : (double) multiplierTotalScaled.get() / 10000.0D / (double) sampleCount;

        return new DopplerDiagnosticsSummary(
                sourcesTracked,
                sourcesRegistered.get(),
                sourceReuseResets.get(),
                sourcesUpdatedThisTick.get(),
                sourcesUpdatedTotal.get(),
                sourcesSkippedByCategory.get(),
                sourcesSkippedByRelativeOrNoAttenuation.get(),
                sourcesSkippedBySableDelegate.get(),
                sourcesSkippedByPositionalAmbientPolicy.get(),
                sourcesSkippedByDisabledConfig.get(),
                unreliableListenerVelocityEvents.get(),
                unreliableSourceVelocityEvents.get(),
                pitchUpdatesApplied.get(),
                pitchUpdatesSkippedUnchanged.get(),
                averageMultiplier,
                (double) latestMultiplierScaled.get() / 10000.0D
        );
    }

    void reset() {
        sourcesRegistered.set(0L);
        sourceReuseResets.set(0L);
        sourcesUpdatedTotal.set(0L);
        sourcesUpdatedThisTick.set(0L);
        sourcesSkippedByCategory.set(0L);
        sourcesSkippedByRelativeOrNoAttenuation.set(0L);
        sourcesSkippedBySableDelegate.set(0L);
        sourcesSkippedByPositionalAmbientPolicy.set(0L);
        sourcesSkippedByDisabledConfig.set(0L);
        unreliableListenerVelocityEvents.set(0L);
        unreliableSourceVelocityEvents.set(0L);
        pitchUpdatesApplied.set(0L);
        pitchUpdatesSkippedUnchanged.set(0L);
        multiplierSamples.set(0L);
        multiplierTotalScaled.set(0L);
        latestMultiplierScaled.set(10000L);
        currentTick = Long.MIN_VALUE;
    }

}

record DopplerDiagnosticsSummary(
        long sourcesTracked,
        long sourcesRegistered,
        long sourceReuseResets,
        long sourcesUpdatedThisTick,
        long sourcesUpdatedTotal,
        long sourcesSkippedByCategory,
        long sourcesSkippedByRelativeOrNoAttenuation,
        long sourcesSkippedBySableDelegate,
        long sourcesSkippedByPositionalAmbientPolicy,
        long sourcesSkippedByDisabledConfig,
        long unreliableListenerVelocityEvents,
        long unreliableSourceVelocityEvents,
        long pitchUpdatesApplied,
        long pitchUpdatesSkippedUnchanged,
        double averageMultiplier,
        double latestMultiplier
) {
}
