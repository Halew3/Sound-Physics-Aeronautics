package com.sonicether.soundphysics;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import net.minecraft.resources.ResourceLocation;

public final class RuntimeLoggingController {

    private static final int DEFAULT_MAX_LINES_PER_SECOND = 40;
    private static final int MAX_TRACKED_KEYS = 256;

    private static final Map<LogKey, WindowCounter> WINDOWS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LogKey, WindowCounter> eldest) {
            return size() > MAX_TRACKED_KEYS;
        }
    };
    private static final Map<String, AtomicLong> SOUND_COUNTS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AtomicLong> eldest) {
            return size() > MAX_TRACKED_KEYS;
        }
    };
    private static final Map<String, AtomicLong> SKIP_REASON_COUNTS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AtomicLong> eldest) {
            return size() > MAX_TRACKED_KEYS;
        }
    };

    private static volatile boolean traceEnabled;
    private static volatile long captureUntilNanos;
    private static volatile int maxLinesPerSecond = DEFAULT_MAX_LINES_PER_SECOND;
    private static final AtomicLong allowedLines = new AtomicLong();
    private static final AtomicLong suppressedLines = new AtomicLong();
    private static final AtomicLong expiredCaptures = new AtomicLong();

    private RuntimeLoggingController() {
    }

    public static boolean shouldLog(Category category, String messageTemplate) {
        if (!verboseActive() && !configuredTraceActive(category)) {
            return false;
        }

        long now = System.nanoTime();
        long second = now / 1_000_000_000L;
        LogKey key = new LogKey(category, messageTemplate == null ? "" : messageTemplate);
        synchronized (WINDOWS) {
            WindowCounter counter = WINDOWS.computeIfAbsent(key, ignored -> new WindowCounter());
            if (counter.second != second) {
                counter.second = second;
                counter.count = 0;
            }
            counter.count++;
            if (maxLinesPerSecond > 0 && counter.count > maxLinesPerSecond) {
                suppressedLines.incrementAndGet();
                return false;
            }
        }

        allowedLines.incrementAndGet();
        return true;
    }

    public static boolean traceLoggingEnabled(@Nullable SoundPhysicsConfig config) {
        return verboseActive() || (config != null && config.soundPhysicsTraceLogging.get());
    }

    public static boolean verboseActive() {
        return traceEnabled || captureActive();
    }

    public static boolean captureActive() {
        long until = captureUntilNanos;
        return until > 0L && System.nanoTime() < until;
    }

    public static void enableTrace() {
        traceEnabled = true;
    }

    public static void disableTrace() {
        traceEnabled = false;
    }

    public static int startCaptureSeconds(int seconds) {
        int clamped = Math.max(1, Math.min(seconds, 60));
        captureUntilNanos = System.nanoTime() + clamped * 1_000_000_000L;
        return clamped;
    }

    public static void stopCapture() {
        captureUntilNanos = 0L;
    }

    public static void quiet(@Nullable SoundPhysicsConfig config) {
        traceEnabled = false;
        captureUntilNanos = 0L;
        if (config != null) {
            config.debugLogging.set(false);
            config.occlusionLogging.set(false);
            config.environmentLogging.set(false);
            config.performanceLogging.set(false);
            config.dopplerDebugLogging.set(false);
            config.soundPhysicsPolicyDebugLogging.set(false);
            config.sableAcousticDebugLogging.set(false);
            config.soundPhysicsTraceLogging.set(false);
            config.openAlErrorChecks.set(false);
        }
    }

    public static void tick() {
        long until = captureUntilNanos;
        if (until > 0L && System.nanoTime() >= until) {
            captureUntilNanos = 0L;
            expiredCaptures.incrementAndGet();
        }
    }

    public static void recordSound(@Nullable ResourceLocation sound) {
        if (sound == null) {
            return;
        }
        record(SOUND_COUNTS, sound.toString());
    }

    public static void recordSkipReason(@Nullable Enum<?> reason) {
        if (reason == null) {
            return;
        }
        record(SKIP_REASON_COUNTS, reason.name());
    }

    public static String statusText(@Nullable SoundPhysicsConfig config) {
        long remainingSeconds = remainingCaptureSeconds();
        return "trace=" + traceEnabled
                + ", captureActive=" + captureActive()
                + ", captureRemainingSeconds=" + remainingSeconds
                + ", maxLinesPerSecond=" + maxLinesPerSecond
                + ", allowedLines=" + allowedLines.get()
                + ", suppressedLines=" + suppressedLines.get()
                + ", expiredCaptures=" + expiredCaptures.get()
                + ", legacyFlags=" + legacyFlagsText(config)
                + ", topSounds=" + topText(SOUND_COUNTS)
                + ", topSkips=" + topText(SKIP_REASON_COUNTS);
    }

    public static void resetDiagnostics() {
        synchronized (WINDOWS) {
            WINDOWS.clear();
        }
        synchronized (SOUND_COUNTS) {
            SOUND_COUNTS.clear();
        }
        synchronized (SKIP_REASON_COUNTS) {
            SKIP_REASON_COUNTS.clear();
        }
        allowedLines.set(0L);
        suppressedLines.set(0L);
        expiredCaptures.set(0L);
    }

    static void setMaxLinesPerSecondForTests(int maxLines) {
        maxLinesPerSecond = maxLines;
    }

    static void forceCaptureExpiredForTests() {
        captureUntilNanos = System.nanoTime() - 1L;
        tick();
    }

    static void resetForTests() {
        traceEnabled = false;
        captureUntilNanos = 0L;
        maxLinesPerSecond = DEFAULT_MAX_LINES_PER_SECOND;
        resetDiagnostics();
    }

    private static void record(Map<String, AtomicLong> counts, String key) {
        synchronized (counts) {
            counts.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        }
    }

    private static long remainingCaptureSeconds() {
        long until = captureUntilNanos;
        if (until <= 0L) {
            return 0L;
        }
        long remainingNanos = until - System.nanoTime();
        if (remainingNanos <= 0L) {
            return 0L;
        }
        return Math.max(1L, (remainingNanos + 999_999_999L) / 1_000_000_000L);
    }

    private static String legacyFlagsText(@Nullable SoundPhysicsConfig config) {
        if (config == null) {
            return "config=not_initialized";
        }
        return "debug=" + config.debugLogging.get()
                + ", occlusion=" + config.occlusionLogging.get()
                + ", environment=" + config.environmentLogging.get()
                + ", performance=" + config.performanceLogging.get()
                + ", doppler=" + config.dopplerDebugLogging.get()
                + ", policy=" + config.soundPhysicsPolicyDebugLogging.get()
                + ", sable=" + config.sableAcousticDebugLogging.get()
                + ", trace=" + config.soundPhysicsTraceLogging.get()
                + ", openAlErrorChecks=" + config.openAlErrorChecks.get();
    }

    private static boolean configuredTraceActive(Category category) {
        return category == Category.TRACE
                && SoundPhysicsMod.CONFIG != null
                && SoundPhysicsMod.CONFIG.soundPhysicsTraceLogging.get();
    }

    private static String topText(Map<String, AtomicLong> counts) {
        synchronized (counts) {
            if (counts.isEmpty()) {
                return "none";
            }
            return counts.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, AtomicLong>>comparingLong(entry -> entry.getValue().get()).reversed())
                    .limit(10)
                    .map(entry -> entry.getKey() + "=" + entry.getValue().get())
                    .reduce((left, right) -> left + "|" + right)
                    .orElse("none");
        }
    }

    private record LogKey(Category category, String messageTemplate) {
    }

    private static final class WindowCounter {
        private long second = Long.MIN_VALUE;
        private int count;
    }

    public enum Category {
        DEBUG,
        PROFILING,
        ENVIRONMENT,
        OCCLUSION,
        DOPPLER,
        TRACE,
        SABLE;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

}
