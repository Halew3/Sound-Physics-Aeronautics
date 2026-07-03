package com.sonicether.soundphysics;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

public final class SoundPhysicsPolicyDiagnostics {

    private static final AtomicLong acousticSkippedByRelative = new AtomicLong();
    private static final AtomicLong acousticSkippedByNoAttenuation = new AtomicLong();
    private static final AtomicLong acousticSkippedByCategory = new AtomicLong();
    private static final AtomicLong acousticSkippedByDenylist = new AtomicLong();
    private static final AtomicLong acousticAllowedByAllowlist = new AtomicLong();
    private static final AtomicLong dopplerAllowedByAllowlist = new AtomicLong();
    private static final AtomicLong propellerCandidateSeen = new AtomicLong();
    private static final AtomicLong crosswindCandidateSkipped = new AtomicLong();
    private static final AtomicLong crosswindWindSkipped = new AtomicLong();
    private static final AtomicLong recordSkipped = new AtomicLong();
    private static final AtomicLong ambientSkipped = new AtomicLong();
    private static final AtomicLong ambientAllowed = new AtomicLong();
    private static final AtomicLong environmentResetSkips = new AtomicLong();
    private static final AtomicLong environmentUntouchedSkips = new AtomicLong();
    private static final AtomicLong processedNormally = new AtomicLong();
    private static final AtomicLong overloadFallbackNearestApplied = new AtomicLong();
    private static final AtomicLong overloadFallbackDirectOnlyApplied = new AtomicLong();
    private static final AtomicLong overloadFallbackFailed = new AtomicLong();
    private static final AtomicLong overloadUntouchedSkipped = new AtomicLong();
    private static final AtomicLong overloadFallbackPreservedExistingEnvironment = new AtomicLong();
    private static final AtomicLong blockEventDirectOnlyLastResort = new AtomicLong();
    private static final AtomicLong blockEventDirectOnlyFallbackApplied = new AtomicLong();
    private static final AtomicLong duplicateFallbackWouldOverwriteReverb = new AtomicLong();
    private static final AtomicLong propellerSeen = new AtomicLong();
    private static final AtomicLong propellerStartEventSeen = new AtomicLong();
    private static final AtomicLong propellerMovingUpdateSeen = new AtomicLong();
    private static final AtomicLong propellerAllowedAcoustic = new AtomicLong();
    private static final AtomicLong propellerAllowedDoppler = new AtomicLong();
    private static final AtomicLong propellerSafeSkippedAcousticUpdate = new AtomicLong();
    private static final AtomicLong propellerSafeBypassedAcoustic = new AtomicLong();
    private static final AtomicLong propellerSafeBypassedDoppler = new AtomicLong();
    private static final AtomicLong propellerDebugAllowedAcoustic = new AtomicLong();
    private static final AtomicLong propellerDebugAllowedDoppler = new AtomicLong();
    private static final AtomicLong propellerProcessedAcoustic = new AtomicLong();
    private static final AtomicLong propellerProcessedDoppler = new AtomicLong();
    private static final AtomicLong propellerMuffledOrFiltered = new AtomicLong();
    private static final AtomicLong propellerSkippedButUntouched = new AtomicLong();
    private static final AtomicLong propellerStartDeferred = new AtomicLong();
    private static final AtomicLong propellerSkippedRelative = new AtomicLong();
    private static final AtomicLong propellerSkippedNoSource = new AtomicLong();
    private static final AtomicLong propellerSkippedNoAttenuation = new AtomicLong();
    private static final AtomicLong propellerSkippedSableDelegate = new AtomicLong();
    private static final AtomicLong propellerExemptImpactDedupe = new AtomicLong();
    private static final AtomicLong propellerExemptStartThrottle = new AtomicLong();
    private static final AtomicLong propellerEnvironmentUntouched = new AtomicLong();
    private static final AtomicLong recordSeen = new AtomicLong();
    private static final AtomicLong recordStreamingEventSeen = new AtomicLong();
    private static final AtomicLong recordStartEventSeen = new AtomicLong();
    private static final AtomicLong recordAllowedByConfig = new AtomicLong();
    private static final AtomicLong recordAllowedByRecordTestMode = new AtomicLong();
    private static final AtomicLong recordSkippedRelative = new AtomicLong();
    private static final AtomicLong recordSkippedNoAttenuation = new AtomicLong();
    private static final AtomicLong recordSkippedZeroPosition = new AtomicLong();
    private static final AtomicLong recordSkippedPolicy = new AtomicLong();
    private static final AtomicLong recordProcessedAcoustic = new AtomicLong();
    private static final AtomicLong recordProcessedDoppler = new AtomicLong();
    private static volatile PropellerObservation latestPropellerObservation = PropellerObservation.none();

    private SoundPhysicsPolicyDiagnostics() {
    }

    static void recordPropellerCandidate() {
        propellerCandidateSeen.incrementAndGet();
    }

    public static void recordContextObserved(SoundPhysicsSoundPolicy.SoundContext context) {
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)) {
            propellerSeen.incrementAndGet();
            if (context.startEvent()) {
                propellerStartEventSeen.incrementAndGet();
            } else {
                propellerMovingUpdateSeen.incrementAndGet();
            }
        }
        if (SoundPhysicsSoundPolicy.isRecord(context)) {
            recordSeen.incrementAndGet();
            if (context.streaming()) {
                recordStreamingEventSeen.incrementAndGet();
            }
            if (context.startEvent()) {
                recordStartEventSeen.incrementAndGet();
            }
        }
    }

    static void recordPropellerExemptImpactDedupe() {
        propellerExemptImpactDedupe.incrementAndGet();
    }

    static void recordPropellerExemptStartThrottle() {
        propellerExemptStartThrottle.incrementAndGet();
    }

    public static void recordEnvironmentReset() {
        environmentResetSkips.incrementAndGet();
    }

    public static void recordEnvironmentUntouched(SoundPhysicsSoundPolicy.SoundContext context) {
        environmentUntouchedSkips.incrementAndGet();
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)) {
            propellerEnvironmentUntouched.incrementAndGet();
            propellerSkippedButUntouched.incrementAndGet();
        }
    }

    public static void recordOverloadFallbackNearestApplied() {
        overloadFallbackNearestApplied.incrementAndGet();
    }

    public static void recordOverloadFallbackDirectOnlyApplied() {
        overloadFallbackDirectOnlyApplied.incrementAndGet();
    }

    public static void recordOverloadFallbackFailed() {
        overloadFallbackFailed.incrementAndGet();
        overloadUntouchedSkipped.incrementAndGet();
    }

    public static void recordOverloadFallbackPreservedExistingEnvironment() {
        overloadFallbackPreservedExistingEnvironment.incrementAndGet();
    }

    public static void recordBlockEventDirectOnlyLastResort() {
        blockEventDirectOnlyLastResort.incrementAndGet();
    }

    public static void recordBlockEventDirectOnlyFallbackApplied() {
        blockEventDirectOnlyFallbackApplied.incrementAndGet();
    }

    public static void recordDuplicateFallbackWouldOverwriteReverb() {
        duplicateFallbackWouldOverwriteReverb.incrementAndGet();
    }

    public static void recordProcessedNormally(SoundPhysicsSoundPolicy.SoundContext context) {
        processedNormally.incrementAndGet();
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)) {
            propellerProcessedAcoustic.incrementAndGet();
        }
        if (SoundPhysicsSoundPolicy.isRecord(context)) {
            recordProcessedAcoustic.incrementAndGet();
        }
    }

    public static void recordPropellerProcessedDoppler() {
        propellerProcessedDoppler.incrementAndGet();
    }

    public static void recordPropellerSafeSkippedAcousticUpdate() {
        propellerSafeSkippedAcousticUpdate.incrementAndGet();
    }

    public static void recordPropellerMuffledOrFiltered() {
        propellerMuffledOrFiltered.incrementAndGet();
    }

    static void recordAcousticDecision(SoundPhysicsSoundPolicy.SoundContext context, SoundPhysicsSoundPolicy.Decision decision) {
        recordLatestPropellerObservation("acoustic", context, decision);
        recordAcousticDecision(decision);
    }

    static void recordAcousticDecision(SoundPhysicsSoundPolicy.Decision decision) {
        RuntimeLoggingController.recordSkipReason(decision.apply() ? null : decision.reason());
        if (decision.apply()) {
            if (decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.ALLOWLIST) {
                acousticAllowedByAllowlist.incrementAndGet();
            }
            if (decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_ALLOWED) {
                if (DiagnosticRuntimeOverrides.propellerDebugMode()) {
                    propellerDebugAllowedAcoustic.incrementAndGet();
                } else {
                    propellerAllowedAcoustic.incrementAndGet();
                }
            }
            if (decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.RECORD_ALLOWED_BY_CONFIG) {
                recordAllowedByConfig.incrementAndGet();
            }
            if (decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.RECORD_ALLOWED_BY_TEST_MODE
                    || decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.RECORD_ALLOWED_UNSAFE) {
                recordAllowedByRecordTestMode.incrementAndGet();
            }
            if (decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.AMBIENT_MACHINERY
                    || decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.POSITIONAL_AMBIENT) {
                ambientAllowed.incrementAndGet();
            }
            return;
        }

        switch (decision.reason()) {
            case RELATIVE -> acousticSkippedByRelative.incrementAndGet();
            case NO_ATTENUATION -> acousticSkippedByNoAttenuation.incrementAndGet();
            case DENYLIST -> acousticSkippedByDenylist.incrementAndGet();
            case CROSSWIND_WIND -> {
                crosswindCandidateSkipped.incrementAndGet();
                crosswindWindSkipped.incrementAndGet();
            }
            case RECORDS -> recordSkipped.incrementAndGet();
            case AMBIENT_POLICY, POSITIONAL_AMBIENT -> ambientSkipped.incrementAndGet();
            case PROPELLER_START_DEFERRED -> propellerStartDeferred.incrementAndGet();
            case PROPELLER_SAFE_MODE -> propellerSafeBypassedAcoustic.incrementAndGet();
            case PROPELLER_SKIPPED_RELATIVE -> propellerSkippedRelative.incrementAndGet();
            case PROPELLER_SKIPPED_NO_SOURCE -> propellerSkippedNoSource.incrementAndGet();
            case PROPELLER_SKIPPED_NO_ATTENUATION -> propellerSkippedNoAttenuation.incrementAndGet();
            case PROPELLER_SKIPPED_SABLE_DELEGATE -> propellerSkippedSableDelegate.incrementAndGet();
            case RECORD_SKIPPED_RELATIVE -> recordSkippedRelative.incrementAndGet();
            case RECORD_SKIPPED_NO_ATTENUATION -> recordSkippedNoAttenuation.incrementAndGet();
            case RECORD_SKIPPED_ZERO_POSITION -> recordSkippedZeroPosition.incrementAndGet();
            case RECORD_SKIPPED_POLICY -> recordSkippedPolicy.incrementAndGet();
            case CATEGORY, MUSIC, MASTER, VOICE -> acousticSkippedByCategory.incrementAndGet();
            default -> {
            }
        }
    }

    static void recordDopplerDecision(SoundPhysicsSoundPolicy.SoundContext context, SoundPhysicsSoundPolicy.Decision decision) {
        recordLatestPropellerObservation("doppler", context, decision);
        recordDopplerDecision(decision);
    }

    static void recordDopplerDecision(SoundPhysicsSoundPolicy.Decision decision) {
        RuntimeLoggingController.recordSkipReason(decision.apply() ? null : decision.reason());
        if (decision.apply() && decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.ALLOWLIST) {
            dopplerAllowedByAllowlist.incrementAndGet();
        }
        if (decision.apply() && decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_ALLOWED) {
            if (DiagnosticRuntimeOverrides.propellerDebugMode()) {
                propellerDebugAllowedDoppler.incrementAndGet();
            } else {
                propellerAllowedDoppler.incrementAndGet();
            }
        }
        if (decision.apply() && (decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.RECORD_ALLOWED_BY_CONFIG
                || decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.RECORD_ALLOWED_BY_TEST_MODE
                || decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.RECORD_ALLOWED_UNSAFE)) {
            recordProcessedDoppler.incrementAndGet();
        }
        if (!decision.apply() && decision.reason() == SoundPhysicsSoundPolicy.DecisionReason.CROSSWIND_WIND) {
            crosswindCandidateSkipped.incrementAndGet();
            crosswindWindSkipped.incrementAndGet();
        }
        if (!decision.apply()) {
            switch (decision.reason()) {
                case PROPELLER_START_DEFERRED -> propellerStartDeferred.incrementAndGet();
                case PROPELLER_SAFE_MODE -> propellerSafeBypassedDoppler.incrementAndGet();
                case PROPELLER_SKIPPED_RELATIVE -> propellerSkippedRelative.incrementAndGet();
                case PROPELLER_SKIPPED_NO_SOURCE -> propellerSkippedNoSource.incrementAndGet();
                case PROPELLER_SKIPPED_NO_ATTENUATION -> propellerSkippedNoAttenuation.incrementAndGet();
                case PROPELLER_SKIPPED_SABLE_DELEGATE -> propellerSkippedSableDelegate.incrementAndGet();
                case RECORD_SKIPPED_RELATIVE -> recordSkippedRelative.incrementAndGet();
                case RECORD_SKIPPED_NO_ATTENUATION -> recordSkippedNoAttenuation.incrementAndGet();
                case RECORD_SKIPPED_POLICY -> recordSkippedPolicy.incrementAndGet();
                default -> {
                }
            }
        }
    }

    public static String diagnosticsSummaryText() {
        return "acousticSkipped(relative=" + acousticSkippedByRelative.get()
                + ", noAttenuation=" + acousticSkippedByNoAttenuation.get()
                + ", category=" + acousticSkippedByCategory.get()
                + ", denylist=" + acousticSkippedByDenylist.get() + ")"
                + ", acousticAllowedByAllowlist=" + acousticAllowedByAllowlist.get()
                + ", dopplerAllowedByAllowlist=" + dopplerAllowedByAllowlist.get()
                + ", propellerCandidateSeen=" + propellerCandidateSeen.get()
                + ", crosswindCandidateSkipped=" + crosswindCandidateSkipped.get()
                + ", crosswindWindSkipped=" + crosswindWindSkipped.get()
                + ", recordSkipped=" + recordSkipped.get()
                + ", ambient(skipped=" + ambientSkipped.get()
                + ", allowed=" + ambientAllowed.get() + ")"
                + ", env(resetSkips=" + environmentResetSkips.get()
                + ", untouchedSkips=" + environmentUntouchedSkips.get()
                + ", processed=" + processedNormally.get() + ")"
                + ", overloadFallback(nearestApplied=" + overloadFallbackNearestApplied.get()
                + ", directOnlyApplied=" + overloadFallbackDirectOnlyApplied.get()
                + ", failed=" + overloadFallbackFailed.get()
                + ", untouchedSkipped=" + overloadUntouchedSkipped.get() + ")"
                + ", overloadFallbackGuards(preservedExistingEnvironment=" + overloadFallbackPreservedExistingEnvironment.get()
                + ", blockEventDirectOnlyLastResort=" + blockEventDirectOnlyLastResort.get()
                + ", blockEventDirectOnlyFallbackApplied=" + blockEventDirectOnlyFallbackApplied.get()
                + ", duplicateFallbackWouldOverwriteReverb=" + duplicateFallbackWouldOverwriteReverb.get() + ")"
                + ", propeller(seen=" + propellerSeen.get()
                + ", start=" + propellerStartEventSeen.get()
                + ", moving=" + propellerMovingUpdateSeen.get()
                + ", safeSkippedAcousticUpdate=" + propellerSafeSkippedAcousticUpdate.get()
                + ", safeBypassedAcoustic=" + propellerSafeBypassedAcoustic.get()
                + ", safeBypassedDoppler=" + propellerSafeBypassedDoppler.get()
                + ", debugAllowedAcoustic=" + propellerDebugAllowedAcoustic.get()
                + ", debugAllowedDoppler=" + propellerDebugAllowedDoppler.get()
                + ", processedAcoustic=" + propellerProcessedAcoustic.get()
                + ", processedDoppler=" + propellerProcessedDoppler.get()
                + ", muffledOrFiltered=" + propellerMuffledOrFiltered.get()
                + ", skippedButUntouched=" + propellerSkippedButUntouched.get()
                + ", allowedAcoustic=" + propellerAllowedAcoustic.get()
                + ", allowedDoppler=" + propellerAllowedDoppler.get()
                + ", deferred=" + propellerStartDeferred.get()
                + ", relative=" + propellerSkippedRelative.get()
                + ", noSource=" + propellerSkippedNoSource.get()
                + ", noAttenuation=" + propellerSkippedNoAttenuation.get()
                + ", sableDelegate=" + propellerSkippedSableDelegate.get()
                + ", exemptDedupe=" + propellerExemptImpactDedupe.get()
                + ", exemptThrottle=" + propellerExemptStartThrottle.get()
                + ", envUntouched=" + propellerEnvironmentUntouched.get() + ")"
                + ", records(seen=" + recordSeen.get()
                + ", streaming=" + recordStreamingEventSeen.get()
                + ", start=" + recordStartEventSeen.get()
                + ", allowedConfig=" + recordAllowedByConfig.get()
                + ", allowedTest=" + recordAllowedByRecordTestMode.get()
                + ", relative=" + recordSkippedRelative.get()
                + ", noAttenuation=" + recordSkippedNoAttenuation.get()
                + ", zeroPos=" + recordSkippedZeroPosition.get()
                + ", policy=" + recordSkippedPolicy.get()
                + ", processedAcoustic=" + recordProcessedAcoustic.get()
                + ", processedDoppler=" + recordProcessedDoppler.get() + ")";
    }

    public static String latestPropellerObservationText() {
        return latestPropellerObservation.line();
    }

    public static boolean hasLatestPropellerObservation() {
        return latestPropellerObservation.present();
    }

    public static void reset() {
        acousticSkippedByRelative.set(0L);
        acousticSkippedByNoAttenuation.set(0L);
        acousticSkippedByCategory.set(0L);
        acousticSkippedByDenylist.set(0L);
        acousticAllowedByAllowlist.set(0L);
        dopplerAllowedByAllowlist.set(0L);
        propellerCandidateSeen.set(0L);
        crosswindCandidateSkipped.set(0L);
        crosswindWindSkipped.set(0L);
        recordSkipped.set(0L);
        ambientSkipped.set(0L);
        ambientAllowed.set(0L);
        environmentResetSkips.set(0L);
        environmentUntouchedSkips.set(0L);
        processedNormally.set(0L);
        overloadFallbackNearestApplied.set(0L);
        overloadFallbackDirectOnlyApplied.set(0L);
        overloadFallbackFailed.set(0L);
        overloadUntouchedSkipped.set(0L);
        overloadFallbackPreservedExistingEnvironment.set(0L);
        blockEventDirectOnlyLastResort.set(0L);
        blockEventDirectOnlyFallbackApplied.set(0L);
        duplicateFallbackWouldOverwriteReverb.set(0L);
        propellerSeen.set(0L);
        propellerStartEventSeen.set(0L);
        propellerMovingUpdateSeen.set(0L);
        propellerAllowedAcoustic.set(0L);
        propellerAllowedDoppler.set(0L);
        propellerSafeSkippedAcousticUpdate.set(0L);
        propellerSafeBypassedAcoustic.set(0L);
        propellerSafeBypassedDoppler.set(0L);
        propellerDebugAllowedAcoustic.set(0L);
        propellerDebugAllowedDoppler.set(0L);
        propellerProcessedAcoustic.set(0L);
        propellerProcessedDoppler.set(0L);
        propellerMuffledOrFiltered.set(0L);
        propellerSkippedButUntouched.set(0L);
        propellerStartDeferred.set(0L);
        propellerSkippedRelative.set(0L);
        propellerSkippedNoSource.set(0L);
        propellerSkippedNoAttenuation.set(0L);
        propellerSkippedSableDelegate.set(0L);
        propellerExemptImpactDedupe.set(0L);
        propellerExemptStartThrottle.set(0L);
        propellerEnvironmentUntouched.set(0L);
        recordSeen.set(0L);
        recordStreamingEventSeen.set(0L);
        recordStartEventSeen.set(0L);
        recordAllowedByConfig.set(0L);
        recordAllowedByRecordTestMode.set(0L);
        recordSkippedRelative.set(0L);
        recordSkippedNoAttenuation.set(0L);
        recordSkippedZeroPosition.set(0L);
        recordSkippedPolicy.set(0L);
        recordProcessedAcoustic.set(0L);
        recordProcessedDoppler.set(0L);
        latestPropellerObservation = PropellerObservation.none();
    }

    private static void recordLatestPropellerObservation(String path, SoundPhysicsSoundPolicy.SoundContext context, SoundPhysicsSoundPolicy.Decision decision) {
        if (!SoundPhysicsSoundPolicy.isKnownPropeller(context) && !SoundPhysicsSoundPolicy.isPropellerCandidate(context)) {
            return;
        }
        latestPropellerObservation = PropellerObservation.create(path, context, decision);
    }

    private static String shortClassName(@Nullable String className) {
        if (className == null || className.isBlank()) {
            return "unknown";
        }
        int index = className.lastIndexOf('.');
        return index < 0 ? className : className.substring(index + 1);
    }

    private record PropellerObservation(
            boolean present,
            String path,
            @Nullable ResourceLocation soundId,
            @Nullable SoundSource category,
            @Nullable String className,
            boolean knownPropeller,
            boolean propellerCandidate,
            boolean sableDelegated,
            boolean relative,
            boolean noAttenuation,
            boolean streaming,
            boolean startEvent,
            boolean tickable,
            boolean apply,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason,
            long observedNanos
    ) {

        static PropellerObservation none() {
            return new PropellerObservation(
                    false,
                    "none",
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    SoundPhysicsSoundPolicy.DecisionReason.APPLY,
                    0L
            );
        }

        static PropellerObservation create(String path, SoundPhysicsSoundPolicy.SoundContext context, SoundPhysicsSoundPolicy.Decision decision) {
            return new PropellerObservation(
                    true,
                    path,
                    context.soundId(),
                    context.category(),
                    context.soundInstanceClassName(),
                    SoundPhysicsSoundPolicy.isKnownPropeller(context),
                    SoundPhysicsSoundPolicy.isPropellerCandidate(context),
                    SoundPhysicsSoundPolicy.isSableDelegated(context),
                    context.relative(),
                    context.noAttenuation(),
                    context.streaming(),
                    context.startEvent(),
                    context.tickable(),
                    decision.apply(),
                    decision.reason(),
                    System.nanoTime()
            );
        }

        String line() {
            if (!present) {
                return "Latest propeller seen: none";
            }
            return "Latest propeller seen: path=" + path
                    + " sound=" + soundId
                    + " class=" + shortClassName(className)
                    + " category=" + category
                    + " knownPropeller=" + knownPropeller
                    + " propellerCandidate=" + propellerCandidate
                    + " sableDelegated=" + sableDelegated
                    + " relative=" + relative
                    + " noAttenuation=" + noAttenuation
                    + " streaming=" + streaming
                    + " startEvent=" + startEvent
                    + " tickable=" + tickable
                    + " apply=" + apply
                    + " decision=" + decisionReason
                    + " ageMs=" + ageMs();
        }

        private long ageMs() {
            if (!present || observedNanos <= 0L) {
                return -1L;
            }
            return Math.max(0L, (System.nanoTime() - observedNanos) / 1_000_000L);
        }
    }

}
