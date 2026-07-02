package com.sonicether.soundphysics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class RecordDiagnostics {

    private static final Map<Integer, RecordSource> SOURCES = new ConcurrentHashMap<>();
    private static final AtomicLong recordStartEvents = new AtomicLong();
    private static final AtomicLong recordMovingUpdates = new AtomicLong();
    private static final AtomicLong acousticProcessed = new AtomicLong();
    private static final AtomicLong dopplerRegistered = new AtomicLong();
    private static final AtomicLong dopplerUpdated = new AtomicLong();
    private static final AtomicLong movingUpdatesAfterResetWithoutStart = new AtomicLong();
    private static final AtomicLong expiredWatches = new AtomicLong();
    private static volatile String latestMessage = "none";
    private static volatile long watchUntilNanos;
    private static volatile int watchSeconds;

    private RecordDiagnostics() {
    }

    public static void observeSource(int sourceId, Vec3 position, SoundPhysicsSoundPolicy.SoundContext context) {
        if (!SoundPhysicsSoundPolicy.isRecord(context)) {
            return;
        }
        RecordSource state = SOURCES.computeIfAbsent(sourceId, ignored -> RecordSource.create(sourceId, context, position));
        state.updateContext(context, position);
        if (context.startEvent()) {
            recordStartEvents.incrementAndGet();
            state.startSeen = true;
        } else {
            recordMovingUpdates.incrementAndGet();
            state.movingUpdateSeen = true;
            if (recordStartEvents.get() == 0L) {
                movingUpdatesAfterResetWithoutStart.incrementAndGet();
                state.alreadyActive = true;
                latestMessage = "record source already active; no new start event seen after reset; moving updates observed";
            }
        }
    }

    public static void recordAcousticDecision(SoundPhysicsSoundPolicy.SoundContext context, SoundPhysicsSoundPolicy.Decision decision) {
        if (!SoundPhysicsSoundPolicy.isRecord(context)) {
            return;
        }
        latestSource().ifPresent(source -> source.acousticDecision = decision.reason().name());
    }

    public static void recordDopplerDecision(SoundPhysicsSoundPolicy.SoundContext context, SoundPhysicsSoundPolicy.Decision decision) {
        if (!SoundPhysicsSoundPolicy.isRecord(context)) {
            return;
        }
        latestSource().ifPresent(source -> source.dopplerDecision = decision.reason().name());
    }

    public static void recordAcousticProcessed(int sourceId, double occlusion, float directCutoff, float directGain, float sendGain0, float sendGain1, float sendGain2, float sendGain3) {
        RecordSource source = SOURCES.get(sourceId);
        if (source == null) {
            return;
        }
        acousticProcessed.incrementAndGet();
        source.acousticProcessed = true;
        source.latestOcclusion = occlusion;
        source.latestDirectCutoff = directCutoff;
        source.latestDirectGain = directGain;
        source.latestReverbSends = formatSends(sendGain0, sendGain1, sendGain2, sendGain3);
    }

    public static void recordDopplerRegistered(int sourceId, Vec3 position, SoundPhysicsSoundPolicy.SoundContext context) {
        if (!SoundPhysicsSoundPolicy.isRecord(context)) {
            return;
        }
        dopplerRegistered.incrementAndGet();
        RecordSource source = SOURCES.computeIfAbsent(sourceId, ignored -> RecordSource.create(sourceId, context, position));
        source.updateContext(context, position);
        source.dopplerRegistered = true;
    }

    public static void recordDopplerUpdated(int sourceId, String pitchDecision) {
        RecordSource source = SOURCES.get(sourceId);
        if (source == null) {
            return;
        }
        dopplerUpdated.incrementAndGet();
        source.dopplerUpdated = true;
        source.pitchDecision = pitchDecision;
    }

    public static String statusText() {
        return "records(startEvents=" + recordStartEvents.get()
                + ", movingUpdates=" + recordMovingUpdates.get()
                + ", acousticProcessed=" + acousticProcessed.get()
                + ", dopplerRegistered=" + dopplerRegistered.get()
                + ", dopplerUpdated=" + dopplerUpdated.get()
                + ", staleAfterReset=" + movingUpdatesAfterResetWithoutStart.get()
                + ", watchActive=" + watchActive()
                + ", watchRemainingSeconds=" + watchRemainingSeconds()
                + ", expiredWatches=" + expiredWatches.get()
                + ", trackedSources=" + SOURCES.size()
                + ", latestMessage=" + latestMessage + ")";
    }

    public static int trackedSourceCount() {
        return SOURCES.size();
    }

    public static void markSourceInvalidated(int sourceId, String reason) {
        RecordSource source = SOURCES.get(sourceId);
        if (source != null) {
            source.invalidated = true;
            source.invalidationReason = reason;
        }
        latestMessage = reason;
    }

    public static int clearSourcesForAudioRecovery(String reason) {
        int count = SOURCES.size();
        SOURCES.clear();
        latestMessage = "record sources cleared for audio recovery: " + reason;
        if (count > 0) {
            AudioSourceRecovery.recordStaleSourcesPruned(count, reason);
        }
        return count;
    }

    public static int startWatchSeconds(int seconds) {
        int clamped = Math.max(1, Math.min(seconds, 60));
        watchSeconds = clamped;
        watchUntilNanos = System.nanoTime() + clamped * 1_000_000_000L;
        latestMessage = "record watch active for " + clamped + " seconds";
        return clamped;
    }

    public static List<String> pollExpiredWatchSummaryLines() {
        long until = watchUntilNanos;
        if (until <= 0L || System.nanoTime() < until) {
            return List.of();
        }
        watchUntilNanos = 0L;
        expiredWatches.incrementAndGet();
        latestMessage = "record watch complete after " + watchSeconds + " seconds";
        List<String> lines = new ArrayList<>();
        lines.add("SPR Aeronautics records watch summary after " + watchSeconds + " seconds");
        lines.addAll(sourceLines(8));
        return List.copyOf(lines);
    }

    public static List<String> sourceLines(int limit) {
        if (SOURCES.isEmpty()) {
            return List.of("record sources: none", statusText());
        }
        List<RecordSource> sources = new ArrayList<>(SOURCES.values());
        sources.sort(Comparator.comparingLong(RecordSource::lastUpdateNanos).reversed());
        List<String> lines = new ArrayList<>();
        lines.add("record sources: total=" + SOURCES.size() + ", showing=" + Math.max(1, limit));
        int added = 0;
        for (RecordSource source : sources) {
            if (added >= Math.max(1, limit)) {
                lines.add("... more record sources not shown");
                break;
            }
            lines.add(source.line());
            added++;
        }
        lines.add(statusText());
        return List.copyOf(lines);
    }

    public static void reset() {
        SOURCES.clear();
        recordStartEvents.set(0L);
        recordMovingUpdates.set(0L);
        acousticProcessed.set(0L);
        dopplerRegistered.set(0L);
        dopplerUpdated.set(0L);
        movingUpdatesAfterResetWithoutStart.set(0L);
        expiredWatches.set(0L);
        watchUntilNanos = 0L;
        watchSeconds = 0;
        latestMessage = "record diagnostics reset; start a jukebox after reset for clean start counters";
    }

    public static void markModeSwitch(String modeName) {
        latestMessage = "record diagnostics mode switch: " + modeName + "; active records update on the next source tick";
    }

    static void forceWatchExpiredForTests() {
        if (watchUntilNanos > 0L) {
            watchUntilNanos = System.nanoTime() - 1L;
        }
    }

    private static java.util.Optional<RecordSource> latestSource() {
        return SOURCES.values().stream().max(Comparator.comparingLong(RecordSource::lastUpdateNanos));
    }

    private static String formatSends(float sendGain0, float sendGain1, float sendGain2, float sendGain3) {
        return "(" + format(sendGain0) + "," + format(sendGain1) + "," + format(sendGain2) + "," + format(sendGain3) + ")";
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static boolean watchActive() {
        long until = watchUntilNanos;
        return until > 0L && System.nanoTime() < until;
    }

    private static long watchRemainingSeconds() {
        long until = watchUntilNanos;
        if (until <= 0L) {
            return 0L;
        }
        long remaining = until - System.nanoTime();
        if (remaining <= 0L) {
            return 0L;
        }
        return Math.max(1L, (remaining + 999_999_999L) / 1_000_000_000L);
    }

    private static final class RecordSource {
        private final int sourceId;
        @Nullable
        private ResourceLocation soundId;
        @Nullable
        private SoundSource category;
        @Nullable
        private String className;
        private Vec3 position = Vec3.ZERO;
        private boolean relative;
        private boolean noAttenuation;
        private boolean streaming;
        private boolean startSeen;
        private boolean movingUpdateSeen;
        private boolean alreadyActive;
        private boolean acousticProcessed;
        private boolean dopplerRegistered;
        private boolean dopplerUpdated;
        private boolean invalidated;
        private String acousticDecision = "unknown";
        private String dopplerDecision = "unknown";
        private String invalidationReason = "none";
        private double latestOcclusion;
        private float latestDirectCutoff = 1.0F;
        private float latestDirectGain = 1.0F;
        private String latestReverbSends = "(0.000,0.000,0.000,0.000)";
        private String pitchDecision = "unknown";
        private long lastUpdateNanos;

        private RecordSource(int sourceId) {
            this.sourceId = sourceId;
        }

        static RecordSource create(int sourceId, SoundPhysicsSoundPolicy.SoundContext context, Vec3 position) {
            RecordSource source = new RecordSource(sourceId);
            source.updateContext(context, position);
            return source;
        }

        void updateContext(SoundPhysicsSoundPolicy.SoundContext context, Vec3 newPosition) {
            soundId = context.soundId();
            category = context.category();
            className = context.soundInstanceClassName();
            relative = context.relative();
            noAttenuation = context.noAttenuation();
            streaming = context.streaming();
            position = newPosition;
            lastUpdateNanos = System.nanoTime();
        }

        long lastUpdateNanos() {
            return lastUpdateNanos;
        }

        String line() {
            return "source=" + sourceId
                    + " sound=" + soundId
                    + " class=" + shortClassName(className)
                    + " category=" + category
                    + " pos=(" + format(position.x) + "," + format(position.y) + "," + format(position.z) + ")"
                    + " relative=" + relative
                    + " noAttenuation=" + noAttenuation
                    + " streaming=" + streaming
                    + " startSeen=" + startSeen
                    + " movingUpdateSeen=" + movingUpdateSeen
                    + " alreadyActive=" + alreadyActive
                    + " acousticDecision=" + acousticDecision
                    + " dopplerDecision=" + dopplerDecision
                    + " directCutoff=" + format(latestDirectCutoff)
                    + " directGain=" + format(latestDirectGain)
                    + " occlusion=" + format(latestOcclusion)
                    + " reverbSends=" + latestReverbSends
                    + " acousticProcessed=" + acousticProcessed
                    + " dopplerRegistered=" + dopplerRegistered
                    + " dopplerUpdated=" + dopplerUpdated
                    + " pitchDecision=" + pitchDecision
                    + " invalidated=" + invalidated
                    + " invalidReason=" + invalidationReason;
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
