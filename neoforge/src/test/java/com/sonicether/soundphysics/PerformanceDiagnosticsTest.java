package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PerformanceDiagnosticsTest {

    @BeforeEach
    void reset() {
        SoundPhysicsPerfDiagnostics.reset();
    }

    @Test
    void soundStartCountersRollOverAndThrottle() {
        assertTrue(SoundPhysicsPerfDiagnostics.recordSoundStart(10L, 2));
        assertTrue(SoundPhysicsPerfDiagnostics.recordSoundStart(10L, 2));
        assertFalse(SoundPhysicsPerfDiagnostics.recordSoundStart(10L, 2));

        String sameTick = SoundPhysicsPerfDiagnostics.summaryText();
        assertTrue(sameTick.contains("thisTick=3"));
        assertTrue(sameTick.contains("maxPerTick=3"));
        assertTrue(sameTick.contains("throttled=1"));

        assertTrue(SoundPhysicsPerfDiagnostics.recordSoundStart(11L, 2));
        assertTrue(SoundPhysicsPerfDiagnostics.summaryText().contains("thisTick=1"));
    }

    @Test
    void rayAndBurstCountersRecord() {
        SoundPhysicsPerfDiagnostics.recordImpactBurstDeduped();
        SoundPhysicsPerfDiagnostics.recordAcousticRayCast(2_500_000L);
        SoundPhysicsPerfDiagnostics.recordSableRayCandidateTest();
        SoundPhysicsPerfDiagnostics.recordEvaluateEnvironment(5_000_000L);
        SoundPhysicsPerfDiagnostics.recordAdaptiveReflectionBudget(new AdaptiveReflectionBudget.Budget(
                32,
                4,
                12,
                2,
                AdaptiveReflectionBudget.Reason.PROPELLER
        ));

        String summary = SoundPhysicsPerfDiagnostics.summaryText();
        assertTrue(summary.contains("impactDeduped=1"));
        assertTrue(summary.contains("sableCandidateTests=1"));
        assertTrue(summary.contains("evaluate=5.000"));
        assertTrue(summary.contains("adaptiveReflectionBudget(total=1"));
        assertTrue(summary.contains("reduced=1"));
        assertTrue(summary.contains("rays=32->12"));
        assertTrue(summary.contains("bounces=4->2"));
        assertTrue(summary.contains("propeller=1"));
        assertTrue(SoundPhysicsPerfDiagnostics.shortSummaryText().contains("adaptiveBudgetReduced=1"));
    }

}
