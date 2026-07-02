package com.sonicether.soundphysics;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.doppler.DopplerEngine;
import com.sonicether.soundphysics.propeller.PropellerLongRangeAudio;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

public final class AudioSourceRecovery {

    private static final float MUTED_VOLUME = 0.0001F;
    private static final int INVALID_RECOVERY_THRESHOLD = 2;

    private static final Map<SoundSource, Float> LAST_VOLUMES = new EnumMap<>(SoundSource.class);
    private static final ConcurrentMap<Integer, Integer> INVALID_SOURCE_FAILURES = new ConcurrentHashMap<>();
    private static final AtomicLong volumeMuteEpoch = new AtomicLong();
    private static final AtomicLong invalidOpenAlSourceCount = new AtomicLong();
    private static final AtomicLong staleSourcePrunedCount = new AtomicLong();
    private static final AtomicLong mutedUpdateSkips = new AtomicLong();
    private static final AtomicLong recoveries = new AtomicLong();

    private static volatile String lastVolumeTransition = "none";
    private static volatile String lastRecoveryReason = "none";
    private static volatile String lastInvalidSourceSound = "none";
    private static volatile String lastInvalidSourceCategory = "none";
    private static volatile String lastInvalidSourceReason = "none";
    private static volatile String pendingRecoveryReason;
    private static volatile String pendingChatNote;

    private AudioSourceRecovery() {
    }

    public static void tick() {
        observeMinecraftVolumes();
        String requested = pendingRecoveryReason;
        if (requested != null) {
            pendingRecoveryReason = null;
            recover(requested, true);
        }
    }

    public static void observeMinecraftVolumes() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) {
            return;
        }
        for (SoundSource source : SoundSource.values()) {
            observeVolume(source, client.options.getSoundSourceVolume(source));
        }
    }

    public static void observeVolume(SoundSource source, float volume) {
        boolean currentMuted = isMutedVolume(volume);
        Float previous = LAST_VOLUMES.put(source, volume);
        if (previous == null) {
            return;
        }

        boolean previousMuted = isMutedVolume(previous);
        if (!previousMuted && currentMuted) {
            volumeMuteEpoch.incrementAndGet();
            lastVolumeTransition = source + " entered zero volume";
            return;
        }
        if (previousMuted && !currentMuted) {
            lastVolumeTransition = source + " returned from zero volume";
            requestAutomaticRecovery("volume " + source + " returned from zero");
        }
    }

    public static boolean sourceUpdatesSuspended(@Nullable SoundSource category) {
        if (isLastVolumeMuted(SoundSource.MASTER)) {
            return true;
        }
        return category != null && isLastVolumeMuted(category);
    }

    public static void recordMutedUpdateSkipped(@Nullable SoundSource category, @Nullable ResourceLocation sound) {
        mutedUpdateSkips.incrementAndGet();
    }

    public static boolean safeSourceExists(
            int sourceId,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            String reason
    ) {
        if (sourceId <= 0) {
            recordInvalidSource(sourceId, category, sound, reason);
            return false;
        }
        try {
            boolean exists = AL10.alIsSource(sourceId);
            if (!exists) {
                recordInvalidSource(sourceId, category, sound, reason);
            }
            return exists;
        } catch (Throwable throwable) {
            recordInvalidSource(sourceId, category, sound, reason + ": " + throwable.getMessage());
            return false;
        }
    }

    public static boolean safeSetSourceFloat(
            int sourceId,
            int parameter,
            float value,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            String reason
    ) {
        if (!safeSourceExists(sourceId, category, sound, reason)) {
            return false;
        }
        try {
            AL10.alSourcef(sourceId, parameter, value);
            Loggers.logALError(reason);
            return true;
        } catch (Throwable throwable) {
            recordInvalidSource(sourceId, category, sound, reason + ": " + throwable.getMessage());
            return false;
        }
    }

    public static float safeGetSourceFloat(
            int sourceId,
            int parameter,
            float fallback,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            String reason
    ) {
        if (!safeSourceExists(sourceId, category, sound, reason)) {
            return fallback;
        }
        try {
            float value = AL10.alGetSourcef(sourceId, parameter);
            Loggers.logALError(reason);
            return Float.isFinite(value) ? value : fallback;
        } catch (Throwable throwable) {
            recordInvalidSource(sourceId, category, sound, reason + ": " + throwable.getMessage());
            return fallback;
        }
    }

    public static boolean safeSetSource3f(
            int sourceId,
            int parameter,
            float x,
            float y,
            float z,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            String reason
    ) {
        if (!safeSourceExists(sourceId, category, sound, reason)) {
            return false;
        }
        try {
            AL11.alSource3f(sourceId, parameter, x, y, z);
            Loggers.logALError(reason);
            return true;
        } catch (Throwable throwable) {
            recordInvalidSource(sourceId, category, sound, reason + ": " + throwable.getMessage());
            return false;
        }
    }

    public static void recordOpenAlInvalidName(String reason) {
        recordInvalidSource(-1, null, null, reason);
    }

    public static void recordInvalidSource(
            int sourceId,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            String reason
    ) {
        invalidOpenAlSourceCount.incrementAndGet();
        lastInvalidSourceSound = sound == null ? "unknown" : sound.toString();
        lastInvalidSourceCategory = category == null ? "unknown" : category.name();
        lastInvalidSourceReason = reason == null || reason.isBlank() ? "unknown" : reason;

        if (sourceId > 0) {
            int failures = INVALID_SOURCE_FAILURES.merge(sourceId, 1, Integer::sum);
            SoundPhysicsSoundPolicy.SoundContext context = SoundPhysicsSoundPolicy.SoundContext.of(sound, category);
            if (failures >= INVALID_RECOVERY_THRESHOLD
                    && (SoundPhysicsSoundPolicy.isRecord(context) || SoundPhysicsSoundPolicy.isKnownPropeller(context))) {
                requestAutomaticRecovery("invalid OpenAL source " + sourceId + " for " + sound);
            }
        }
    }

    public static void recordStaleSourcesPruned(int count, String reason) {
        if (count <= 0) {
            return;
        }
        staleSourcePrunedCount.addAndGet(count);
        lastInvalidSourceReason = reason;
    }

    public static void requestAutomaticRecovery(String reason) {
        pendingRecoveryReason = reason;
    }

    public static String recover(String reason, boolean notifyPlayer) {
        String safeReason = reason == null || reason.isBlank() ? "manual audio recover" : reason;
        int dopplerCleared = DopplerEngine.clearAudioStateForRecovery(safeReason);
        int propellerRangeCleared = PropellerLongRangeAudio.clearAudioStateForRecovery(safeReason);
        int recordsCleared = RecordDiagnostics.clearSourcesForAudioRecovery(safeReason);
        SoundProcessingDeduper.reset();
        SoundPhysics.refreshEfxIfNeeded("audio recover: " + safeReason);
        INVALID_SOURCE_FAILURES.clear();
        recoveries.incrementAndGet();
        lastRecoveryReason = safeReason + " (dopplerSourcesCleared=" + dopplerCleared + ", propellerRangeSourcesCleared=" + propellerRangeCleared + ", recordSourcesCleared=" + recordsCleared + ")";
        if (notifyPlayer) {
            pendingChatNote = "SPR Aeronautics detected audio sources were invalidated after volume/audio reload; cleared stale source state.";
        }
        return "SPR Aeronautics audio recover: cleared stale Doppler/record/propeller source tracking, cleared forced pitch, reset sound dedupe, refreshed EFX if needed. reason=" + safeReason;
    }

    @Nullable
    public static String consumePendingChatNote() {
        String note = pendingChatNote;
        pendingChatNote = null;
        return note;
    }

    public static String statusText() {
        return "audioRecovery(volumeMuteEpoch=" + volumeMuteEpoch.get()
                + ", mutedUpdates=" + mutedUpdateSkips.get()
                + ", invalidOpenAlSourceCount=" + invalidOpenAlSourceCount.get()
                + ", staleSourcePrunedCount=" + staleSourcePrunedCount.get()
                + ", lastInvalidSourceSound=" + lastInvalidSourceSound
                + ", lastInvalidSourceCategory=" + lastInvalidSourceCategory
                + ", lastInvalidSourceReason=" + lastInvalidSourceReason
                + ", lastVolumeTransition=" + lastVolumeTransition
                + ", lastRecoveryReason=" + lastRecoveryReason
                + ", recoveries=" + recoveries.get() + ")";
    }

    public static String sourcesText() {
        return "audio sources: dopplerTracked=" + DopplerEngine.trackedSourceCount()
                + ", propellerTracked=" + DopplerEngine.trackedPropellerSourceCount()
                + ", propellerLongRange=" + PropellerLongRangeAudio.diagnosticsSummaryText()
                + ", recordTracked=" + RecordDiagnostics.trackedSourceCount()
                + ", invalidOpenAlSourceCount=" + invalidOpenAlSourceCount.get()
                + ", staleSourcePrunedCount=" + staleSourcePrunedCount.get()
                + ", forcedPitchActive=" + DopplerEngine.forcedPitchActive()
                + ", lastVolumeTransition=" + lastVolumeTransition
                + ", lastRecoveryReason=" + lastRecoveryReason;
    }

    public static void resetForTests() {
        LAST_VOLUMES.clear();
        INVALID_SOURCE_FAILURES.clear();
        volumeMuteEpoch.set(0L);
        invalidOpenAlSourceCount.set(0L);
        staleSourcePrunedCount.set(0L);
        mutedUpdateSkips.set(0L);
        recoveries.set(0L);
        lastVolumeTransition = "none";
        lastRecoveryReason = "none";
        lastInvalidSourceSound = "none";
        lastInvalidSourceCategory = "none";
        lastInvalidSourceReason = "none";
        pendingRecoveryReason = null;
        pendingChatNote = null;
    }

    static void runPendingRecoveryForTests() {
        String requested = pendingRecoveryReason;
        if (requested != null) {
            pendingRecoveryReason = null;
            recover(requested, true);
        }
    }

    private static boolean isLastVolumeMuted(SoundSource source) {
        Float volume = LAST_VOLUMES.get(source);
        return volume != null && isMutedVolume(volume);
    }

    private static boolean isMutedVolume(float volume) {
        return !Float.isFinite(volume) || volume <= MUTED_VOLUME;
    }
}
