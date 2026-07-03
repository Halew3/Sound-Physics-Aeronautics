package com.sonicether.soundphysics.propeller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.AudioSourceRecovery;
import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;
import com.sonicether.soundphysics.integration.dh.DistantHorizonsAudioBridge;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.lwjgl.openal.AL10;

public final class PropellerLongRangeAudio {

    private static final ConcurrentMap<Integer, SourceState> SOURCES = new ConcurrentHashMap<>();
    private static final AtomicLong seen = new AtomicLong();
    private static final AtomicLong applied = new AtomicLong();
    private static final AtomicLong skipped = new AtomicLong();
    private static final AtomicLong invalidSource = new AtomicLong();
    private static final AtomicLong profileFallback = new AtomicLong();
    private static final AtomicLong reflectionProfile = new AtomicLong();
    private static final AtomicLong farFieldApplied = new AtomicLong();
    private static final AtomicLong reverbSkipped = new AtomicLong();
    private static final SourceBackend OPENAL_SOURCE_BACKEND = new OpenAlSourceBackend();

    private static volatile SourceBackend sourceBackend = OPENAL_SOURCE_BACKEND;
    private static volatile String latestSkipReason = "none";

    private PropellerLongRangeAudio() {
    }

    public static boolean isEligible(SoundPhysicsSoundPolicy.SoundContext context) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null || !config.propellerLongRangeEnabled.get()) {
            return false;
        }
        if (SoundPhysicsSoundPolicy.isAeronauticsPropeller(context)) {
            return true;
        }
        return config.propellerLongRangeApplyToCrosswindVehiclePropellers.get()
                && SoundPhysicsSoundPolicy.isCrosswindVehiclePropeller(context);
    }

    public static boolean applySourceRange(int sourceId, SoundInstance sound, boolean streaming) {
        SoundPhysicsSoundPolicy.SoundContext context = new SoundPhysicsSoundPolicy.SoundContext(
                sound.getLocation(),
                sound.getSource(),
                sound.getClass().getName(),
                sound.isRelative(),
                sound.getAttenuation() == SoundInstance.Attenuation.NONE,
                streaming,
                false,
                sound instanceof TickableSoundInstance
        );
        return applySourceRange(sourceId, sound, context, sound.getPitch(), sound.getVolume());
    }

    public static boolean applySourceRange(
            int sourceId,
            @Nullable SoundInstance soundInstance,
            SoundPhysicsSoundPolicy.SoundContext context,
            float pitch,
            float volume
    ) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null || !config.propellerLongRangeEnabled.get()) {
            latestSkipReason = "disabled";
            skipped.incrementAndGet();
            return false;
        }
        if (!isEligible(context)) {
            latestSkipReason = "not eligible sound=" + context.soundId();
            skipped.incrementAndGet();
            return false;
        }
        if (DiagnosticRuntimeOverrides.propellerSafeMode() && !config.propellerLongRangeApplyInSafeMode.get()) {
            latestSkipReason = "propeller_safe disabled range extension";
            skipped.incrementAndGet();
            return false;
        }

        seen.incrementAndGet();
        PropellerAudioProfile profile = PropellerAudioProfileResolver.resolve(soundInstance, context.soundId(), pitch, volume);
        recordProfileSource(profile);
        return applyProfile(sourceId, context, profile, pitch, volume);
    }

    public static boolean applyFallbackSourceRange(
            int sourceId,
            SoundPhysicsSoundPolicy.SoundContext context,
            float pitch,
            float volume
    ) {
        if (!isEligible(context)) {
            skipped.incrementAndGet();
            return false;
        }
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (DiagnosticRuntimeOverrides.propellerSafeMode() && config != null && !config.propellerLongRangeApplyInSafeMode.get()) {
            latestSkipReason = "propeller_safe disabled range extension";
            skipped.incrementAndGet();
            return false;
        }
        PropellerAudioProfile profile = PropellerAudioProfileResolver.resolve(null, context.soundId(), pitch, volume);
        profileFallback.incrementAndGet();
        return applyProfile(sourceId, context, profile, pitch, volume);
    }

    public static double effectiveProcessingDistance(
            int sourceId,
            SoundPhysicsSoundPolicy.SoundContext context,
            double normalMaxProcessingDistance
    ) {
        if (!isEligible(context)) {
            return normalMaxProcessingDistance;
        }
        double propellerDistance = processingDistanceFor(sourceId, context);
        return Math.max(normalMaxProcessingDistance, propellerDistance);
    }

    public static double processingDistanceFor(int sourceId, SoundPhysicsSoundPolicy.SoundContext context) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null) {
            return 512.0D;
        }
        SourceState state = SOURCES.get(sourceId);
        double range = state == null ? PropellerAudioProfileResolver.fallbackForContext(context.soundId()).computedMaxDistance() : state.smoothedRange();
        return Math.min(range, config.propellerLongRangeMaxProcessingDistance.get());
    }

    public static boolean shouldSkipReverb(double distance, SoundPhysicsSoundPolicy.SoundContext context) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null || !config.propellerFarFieldEnabled.get() || !isEligible(context)) {
            return false;
        }
        boolean skip = distance > config.propellerFarFieldSkipReverbAfterDistance.get();
        if (skip) {
            reverbSkipped.incrementAndGet();
        }
        return skip;
    }

    public static PropellerFarFieldEffect computeFarField(
            int sourceId,
            SoundPhysicsSoundPolicy.SoundContext context,
            double distance,
            float baseAirAbsorption
    ) {
        SourceState state = SOURCES.get(sourceId);
        PropellerAudioProfile profile = state == null
                ? PropellerAudioProfileResolver.fallbackForContext(context.soundId())
                : state.profile();
        double maxDistance = state == null ? profile.computedMaxDistance() : state.smoothedRange();
        PropellerLongRangeParameters parameters = state == null ? parameters(maxDistance) : state.parameters();
        double sourceVolume = state == null ? 1.0D : state.volume();
        PropellerFarFieldEffect effect = PropellerFarFieldEffect.compute(distance, maxDistance, sourceVolume, parameters, baseAirAbsorption);
        if (effect.farField() > 0.0D) {
            farFieldApplied.incrementAndGet();
        }
        if (state != null) {
            state.rememberDistance(distance, effect);
        }
        return effect;
    }

    public static List<String> rangeDiagnosticsLines(int limit) {
        List<SourceState> states = SOURCES.values().stream()
                .sorted(Comparator.comparingLong(SourceState::lastUpdateNanos).reversed())
                .limit(limit)
                .toList();
        List<String> lines = new ArrayList<>();
        lines.add("propellerLongRange(" + diagnosticsSummaryText() + ")");
        lines.add("dhFarPropellerOcclusion(" + DistantHorizonsAudioBridge.diagnosticsSummaryText() + ")");
        if (DiagnosticRuntimeOverrides.propellerSafeMode()) {
            lines.add("safe mode: acoustic/Doppler bypass active; long-range source distance "
                    + (SoundPhysicsMod.CONFIG != null && SoundPhysicsMod.CONFIG.propellerLongRangeApplyInSafeMode.get() ? "applied when sources exist" : "disabled by config"));
        }
        if (states.isEmpty()) {
            lines.add("No tracked propeller long-range sources.");
            lines.add("No distant propeller source exists. If testing beyond vanilla client/sublevel range, confirm Separate Sable Render Distance or equivalent keeps the sublevel and its sound instance active.");
            lines.add("latestSkipReason=" + latestSkipReason);
            return lines;
        }
        for (SourceState state : states) {
            lines.add(state.diagnosticsLine());
        }
        return lines;
    }

    public static String diagnosticsSummaryText() {
        double maxRange = SOURCES.values().stream().mapToDouble(SourceState::smoothedRange).max().orElse(0.0D);
        SourceState latest = SOURCES.values().stream().max(Comparator.comparingLong(SourceState::lastUpdateNanos)).orElse(null);
        double lastRange = latest == null ? 0.0D : latest.smoothedRange();
        return "profile=" + PropellerLongRangeTuning.profileName()
                + ", seen=" + seen.get()
                + ", applied=" + applied.get()
                + ", skipped=" + skipped.get()
                + ", invalidSource=" + invalidSource.get()
                + ", profileFallback=" + profileFallback.get()
                + ", reflectionProfile=" + reflectionProfile.get()
                + ", farFieldApplied=" + farFieldApplied.get()
                + ", reverbSkipped=" + reverbSkipped.get()
                + ", tracked=" + SOURCES.size()
                + ", maxRange=" + format(maxRange)
                + ", lastRange=" + format(lastRange)
                + ", latestSkipReason=" + latestSkipReason;
    }

    public static void resetDiagnostics() {
        seen.set(0L);
        applied.set(0L);
        skipped.set(0L);
        invalidSource.set(0L);
        profileFallback.set(0L);
        reflectionProfile.set(0L);
        farFieldApplied.set(0L);
        reverbSkipped.set(0L);
        latestSkipReason = "none";
        DistantHorizonsAudioBridge.resetDiagnostics();
    }

    public static int clearAudioStateForRecovery(String reason) {
        int count = SOURCES.size();
        SOURCES.clear();
        latestSkipReason = reason;
        DistantHorizonsAudioBridge.clearAudioStateForRecovery(reason);
        return count;
    }

    public static void onLevelUnload() {
        clearAudioStateForRecovery("level unload");
    }

    public static void onAudioLibraryReset() {
        clearAudioStateForRecovery("audio library reset");
    }

    public static void onConfigReload() {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config != null && !config.propellerLongRangeEnabled.get()) {
            clearAudioStateForRecovery("config reload disabled propeller long range");
        } else {
            DistantHorizonsAudioBridge.onConfigReload();
        }
    }

    public static void clearForTests() {
        SOURCES.clear();
        resetDiagnostics();
        sourceBackend = OPENAL_SOURCE_BACKEND;
        PropellerAudioProfileResolver.clearReflectionCache();
        DistantHorizonsAudioBridge.clearForTests();
    }

    public static void setSourceBackendForTests(SourceBackend backend) {
        sourceBackend = backend == null ? OPENAL_SOURCE_BACKEND : backend;
    }

    public static int trackedSourceCountForTests() {
        return SOURCES.size();
    }

    public static PropellerLongRangeParameters parameters(double computedRange) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null) {
            double reference = Mth.clamp(computedRange * 0.018D, 8.0D, 18.0D);
            return new PropellerLongRangeParameters(computedRange, reference, 1.35D);
        }
        double reference = Mth.clamp(
                computedRange * PropellerLongRangeTuning.referenceFraction(config),
                PropellerLongRangeTuning.referenceMin(config),
                PropellerLongRangeTuning.referenceMax(config)
        );
        return new PropellerLongRangeParameters(computedRange, reference, PropellerLongRangeTuning.rolloff(config));
    }

    private static boolean applyProfile(
            int sourceId,
            SoundPhysicsSoundPolicy.SoundContext context,
            PropellerAudioProfile profile,
            float pitch,
            float volume
    ) {
        if (!safeSourceExists(sourceId, context.category(), context.soundId(), "propeller long-range source")) {
            invalidSource.incrementAndGet();
            skipped.incrementAndGet();
            latestSkipReason = "invalid source " + sourceId;
            return false;
        }

        SourceState previous = SOURCES.get(sourceId);
        double smoothedRange = previous == null || !previous.matches(context)
                ? profile.computedMaxDistance()
                : Mth.lerp(0.15D, previous.smoothedRange(), profile.computedMaxDistance());
        PropellerLongRangeParameters parameters = parameters(smoothedRange);
        if (!setFloat(sourceId, AL10.AL_MAX_DISTANCE, (float) parameters.maxDistance(), context, "propeller AL_MAX_DISTANCE")
                || !setFloat(sourceId, AL10.AL_REFERENCE_DISTANCE, (float) parameters.referenceDistance(), context, "propeller AL_REFERENCE_DISTANCE")
                || !setFloat(sourceId, AL10.AL_ROLLOFF_FACTOR, (float) parameters.rolloffFactor(), context, "propeller AL_ROLLOFF_FACTOR")) {
            skipped.incrementAndGet();
            latestSkipReason = "OpenAL set failed source=" + sourceId;
            return false;
        }

        float openAlMax = getFloat(sourceId, AL10.AL_MAX_DISTANCE, (float) parameters.maxDistance(), context, "read propeller AL_MAX_DISTANCE");
        float openAlReference = getFloat(sourceId, AL10.AL_REFERENCE_DISTANCE, (float) parameters.referenceDistance(), context, "read propeller AL_REFERENCE_DISTANCE");
        float openAlRolloff = getFloat(sourceId, AL10.AL_ROLLOFF_FACTOR, (float) parameters.rolloffFactor(), context, "read propeller AL_ROLLOFF_FACTOR");
        SOURCES.put(sourceId, new SourceState(
                sourceId,
                context.soundId(),
                context.category(),
                context.soundInstanceClassName(),
                PropellerLongRangeTuning.profileName(),
                profile,
                smoothedRange,
                parameters,
                pitch,
                volume,
                openAlMax,
                openAlReference,
                openAlRolloff,
                true,
                previous == null ? -1.0D : previous.listenerDistance(),
                previous == null ? PropellerFarFieldEffect.disabled(0.0F) : previous.farFieldEffect(),
                System.nanoTime()
        ));
        applied.incrementAndGet();
        latestSkipReason = "none";
        return true;
    }

    private static void recordProfileSource(PropellerAudioProfile profile) {
        if (profile.sizeSource().startsWith("reflection") || profile.rpmSource().startsWith("reflection")) {
            reflectionProfile.incrementAndGet();
        } else {
            profileFallback.incrementAndGet();
        }
    }

    private static boolean safeSourceExists(int sourceId, @Nullable SoundSource category, @Nullable ResourceLocation sound, String reason) {
        try {
            boolean exists = sourceBackend.sourceExists(sourceId);
            if (!exists) {
                AudioSourceRecovery.recordInvalidSource(sourceId, category, sound, reason);
            }
            return exists;
        } catch (Throwable throwable) {
            AudioSourceRecovery.recordInvalidSource(sourceId, category, sound, reason + ": " + throwable.getMessage());
            return false;
        }
    }

    private static boolean setFloat(int sourceId, int parameter, float value, SoundPhysicsSoundPolicy.SoundContext context, String reason) {
        try {
            sourceBackend.setFloat(sourceId, parameter, value);
            return true;
        } catch (Throwable throwable) {
            AudioSourceRecovery.recordInvalidSource(sourceId, context.category(), context.soundId(), reason + ": " + throwable.getMessage());
            Loggers.logDebug("Failed propeller OpenAL range write source={} reason={}", sourceId, throwable.getMessage());
            return false;
        }
    }

    private static float getFloat(int sourceId, int parameter, float fallback, SoundPhysicsSoundPolicy.SoundContext context, String reason) {
        try {
            float value = sourceBackend.getFloat(sourceId, parameter);
            return Float.isFinite(value) ? value : fallback;
        } catch (Throwable throwable) {
            AudioSourceRecovery.recordInvalidSource(sourceId, context.category(), context.soundId(), reason + ": " + throwable.getMessage());
            return fallback;
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    public interface SourceBackend {

        boolean sourceExists(int sourceId);

        void setFloat(int sourceId, int parameter, float value);

        float getFloat(int sourceId, int parameter);
    }

    private static final class OpenAlSourceBackend implements SourceBackend {

        @Override
        public boolean sourceExists(int sourceId) {
            return sourceId > 0 && AL10.alIsSource(sourceId);
        }

        @Override
        public void setFloat(int sourceId, int parameter, float value) {
            AL10.alSourcef(sourceId, parameter, value);
            Loggers.logALError("Set propeller long-range source parameter");
        }

        @Override
        public float getFloat(int sourceId, int parameter) {
            float value = AL10.alGetSourcef(sourceId, parameter);
            Loggers.logALError("Read propeller long-range source parameter");
            return value;
        }
    }

    private record SourceState(
            int sourceId,
            @Nullable ResourceLocation sound,
            @Nullable SoundSource category,
            @Nullable String soundInstanceClassName,
            String rangeProfile,
            PropellerAudioProfile profile,
            double smoothedRange,
            PropellerLongRangeParameters parameters,
            float pitch,
            float volume,
            float openAlMaxDistance,
            float openAlReferenceDistance,
            float openAlRolloff,
            boolean rangeApplied,
            double listenerDistance,
            PropellerFarFieldEffect farFieldEffect,
            long lastUpdateNanos
    ) {

        boolean matches(SoundPhysicsSoundPolicy.SoundContext context) {
            return java.util.Objects.equals(sound, context.soundId())
                    && category == context.category()
                    && java.util.Objects.equals(soundInstanceClassName, context.soundInstanceClassName());
        }

        void rememberDistance(double distance, PropellerFarFieldEffect effect) {
            SOURCES.put(sourceId, new SourceState(
                    sourceId,
                    sound,
                    category,
                    soundInstanceClassName,
                    rangeProfile,
                    profile,
                    smoothedRange,
                    parameters,
                    pitch,
                    volume,
                    openAlMaxDistance,
                    openAlReferenceDistance,
                    openAlRolloff,
                    rangeApplied,
                    distance,
                    effect,
                    System.nanoTime()
            ));
        }

        String diagnosticsLine() {
            String prefix = "source=" + sourceId
                    + " sound=" + sound
                    + " class=" + shortClassName(soundInstanceClassName)
                    + " profile=" + rangeProfile
                    + " sailCount=" + profile.sailCount()
                    + " sailSource=" + profile.sizeSource()
                    + " rpm=" + format(profile.rpm())
                    + " rpmSource=" + profile.rpmSource()
                    + " pitch=" + format(pitch)
                    + " volume=" + format(volume)
                    + " sizeFactor=" + format(profile.sizeFactor())
                    + " rpmFactor=" + format(profile.rpmFactor())
                    + " computedRange=" + format(profile.computedMaxDistance())
                    + " smoothedRange=" + format(smoothedRange)
                    + " referenceDistance=" + format(parameters.referenceDistance())
                    + " rolloff=" + format(parameters.rolloffFactor())
                    + " sourceVolume=" + format(volume)
                    + " openAlMaxDistance=" + format(openAlMaxDistance)
                    + " openAlReferenceDistance=" + format(openAlReferenceDistance)
                    + " openAlRolloff=" + format(openAlRolloff)
                    + " rangeApplied=" + rangeApplied;
            if (listenerDistance < 0.0D) {
                return prefix
                        + " distanceKnown=false"
                        + " distanceBlocks=unknown"
                        + " reason=distance_unknown"
                        + " openAlEstimatedGain=unknown"
                        + " extraGameplayGain=unknown"
                        + " volumeCompensation=unknown"
                        + " finalEstimatedGain=unknown"
                        + " distanceNorm=unknown"
                        + " farField=unknown"
                        + " directCutoffMultiplier=unknown"
                        + " directGainMultiplier=unknown"
                    + " effectiveCutoff=unknown"
                    + " airAbsorption=unknown"
                    + DistantHorizonsAudioBridge.diagnosticsFieldsForSource(sourceId);
            }
            return prefix
                    + " distanceKnown=true"
                    + " distanceBlocks=" + format(listenerDistance)
                    + " listenerDistance=" + format(listenerDistance)
                    + " openAlEstimatedGain=" + format(farFieldEffect.openAlEstimatedGain())
                    + " extraGameplayGain=" + format(farFieldEffect.extraGameplayGain())
                    + " volumeCompensation=" + format(farFieldEffect.volumeCompensation())
                    + " finalEstimatedGain=" + format(farFieldEffect.finalEstimatedGain())
                    + " distanceNorm=" + format(farFieldEffect.distanceNorm())
                    + " farField=" + format(farFieldEffect.farField())
                    + " directCutoffMultiplier=" + format(farFieldEffect.directCutoffMultiplier())
                    + " directGainMultiplier=" + format(farFieldEffect.directGainMultiplier())
                    + " effectiveCutoff=" + format(farFieldEffect.effectiveCutoff())
                    + " airAbsorption=" + format(farFieldEffect.airAbsorption())
                    + DistantHorizonsAudioBridge.diagnosticsFieldsForSource(sourceId);
        }

        private static String shortClassName(@Nullable String className) {
            if (className == null || className.isBlank()) {
                return "unknown";
            }
            int index = className.lastIndexOf('.');
            return index < 0 ? className : className.substring(index + 1);
        }
    }
}
