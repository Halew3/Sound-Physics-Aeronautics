package com.sonicether.soundphysics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class SoundProcessingDeduper {

    private static final int MAX_KEYS = 2048;
    static final long RECENT_SOURCE_MIXIN_START_WINDOW_NANOS = 250_000_000L;
    static final double RECENT_SOURCE_MIXIN_START_POSITION_TOLERANCE = 1.0D / 32.0D;
    private static final double RECENT_SOURCE_MIXIN_START_POSITION_TOLERANCE_SQR =
            RECENT_SOURCE_MIXIN_START_POSITION_TOLERANCE * RECENT_SOURCE_MIXIN_START_POSITION_TOLERANCE;
    private static final Map<Key, Boolean> PROCESSED = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Boolean> eldest) {
            return size() > MAX_KEYS;
        }
    };
    private static final Map<ImpactBurstKey, Long> IMPACT_BURSTS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ImpactBurstKey, Long> eldest) {
            return size() > MAX_KEYS;
        }
    };
    private static final Map<RecentSourceMixinStartKey, RecentSourceMixinStart> RECENT_SOURCE_MIXIN_STARTS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RecentSourceMixinStartKey, RecentSourceMixinStart> eldest) {
            return size() > MAX_KEYS;
        }
    };

    private SoundProcessingDeduper() {
    }

    public static boolean shouldProcessStart(int sourceId, long gameTime, @Nullable SoundSource category, @Nullable ResourceLocation sound, ProcessingPath path) {
        return shouldProcess(sourceId, gameTime, category, sound, ProcessingPhase.START, path);
    }

    public static boolean shouldProcessMovingUpdate(int sourceId, long gameTime, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
        return shouldProcess(sourceId, gameTime, category, sound, ProcessingPhase.MOVING_UPDATE, ProcessingPath.MOVING_SOUND_UPDATE);
    }

    public static boolean shouldProcessImpactBurst(long gameTime, @Nullable SoundSource category, @Nullable ResourceLocation sound, Vec3 position, double radius, int ticks) {
        if (ticks <= 0 || radius <= 0.0D) {
            return true;
        }

        ImpactBurstKey key = ImpactBurstKey.create(category, sound, position, radius);
        synchronized (IMPACT_BURSTS) {
            Long previousTick = IMPACT_BURSTS.get(key);
            IMPACT_BURSTS.put(key, gameTime);
            if (previousTick != null && gameTime - previousTick >= 0L && gameTime - previousTick <= ticks) {
                SoundPhysicsPerfDiagnostics.recordImpactBurstDeduped();
                return false;
            }
            return true;
        }
    }

    public static void recordSourceMixinStartProcessed(
            int sourceId,
            @Nullable ResourceLocation sound,
            @Nullable SoundSource category,
            Vec3 position,
            long nowNanos
    ) {
        RecentSourceMixinStart start = new RecentSourceMixinStart(sourceId, sound, category, position, nowNanos);
        RecentSourceMixinStartKey key = RecentSourceMixinStartKey.create(sourceId, sound, category, position);
        synchronized (RECENT_SOURCE_MIXIN_STARTS) {
            pruneRecentSourceMixinStarts(nowNanos);
            RECENT_SOURCE_MIXIN_STARTS.put(key, start);
        }
    }

    public static boolean wasRecentlyProcessedBySourceMixin(
            int sourceId,
            @Nullable ResourceLocation sound,
            @Nullable SoundSource category,
            Vec3 position,
            long nowNanos
    ) {
        synchronized (RECENT_SOURCE_MIXIN_STARTS) {
            pruneRecentSourceMixinStarts(nowNanos);
            for (RecentSourceMixinStart start : RECENT_SOURCE_MIXIN_STARTS.values()) {
                if (start.matches(sourceId, sound, category, position, nowNanos)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static long currentGameTime() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
    }

    public static void reset() {
        synchronized (PROCESSED) {
            PROCESSED.clear();
        }
        synchronized (IMPACT_BURSTS) {
            IMPACT_BURSTS.clear();
        }
        synchronized (RECENT_SOURCE_MIXIN_STARTS) {
            RECENT_SOURCE_MIXIN_STARTS.clear();
        }
    }

    private static boolean shouldProcess(int sourceId, long gameTime, @Nullable SoundSource category, @Nullable ResourceLocation sound, ProcessingPhase phase, ProcessingPath path) {
        Key key = new Key(sourceId, gameTime, category, sound, phase);
        synchronized (PROCESSED) {
            if (PROCESSED.containsKey(key)) {
                SoundPhysicsTrace.recordDuplicateProcessingSkip(path, sourceId, sound);
                return false;
            }
            PROCESSED.put(key, Boolean.TRUE);
            return true;
        }
    }

    public enum ProcessingPath {
        SOURCE_MIXIN("sourceMixin"),
        SOUND_ENGINE_FALLBACK("soundEngineFallback"),
        MOVING_SOUND_UPDATE("movingSoundUpdate");

        private final String diagnosticName;

        ProcessingPath(String diagnosticName) {
            this.diagnosticName = diagnosticName;
        }

        public String diagnosticName() {
            return diagnosticName;
        }
    }

    private enum ProcessingPhase {
        START,
        MOVING_UPDATE
    }

    private record Key(
            int sourceId,
            long gameTime,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            ProcessingPhase phase
    ) {
        private Key {
            Objects.requireNonNull(phase, "phase");
        }
    }

    private record ImpactBurstKey(
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            long x,
            long y,
            long z
    ) {
        static ImpactBurstKey create(@Nullable SoundSource category, @Nullable ResourceLocation sound, Vec3 position, double radius) {
            double safeRadius = Math.max(radius, 0.1D);
            return new ImpactBurstKey(
                    category,
                    sound,
                    Math.round(position.x / safeRadius),
                    Math.round(position.y / safeRadius),
                    Math.round(position.z / safeRadius)
            );
        }
    }

    private record RecentSourceMixinStartKey(
            int sourceId,
            @Nullable ResourceLocation sound,
            @Nullable SoundSource category,
            long x,
            long y,
            long z
    ) {
        static RecentSourceMixinStartKey create(
                int sourceId,
                @Nullable ResourceLocation sound,
                @Nullable SoundSource category,
                Vec3 position
        ) {
            return new RecentSourceMixinStartKey(
                    sourceId,
                    sound,
                    category,
                    Math.round(position.x / RECENT_SOURCE_MIXIN_START_POSITION_TOLERANCE),
                    Math.round(position.y / RECENT_SOURCE_MIXIN_START_POSITION_TOLERANCE),
                    Math.round(position.z / RECENT_SOURCE_MIXIN_START_POSITION_TOLERANCE)
            );
        }
    }

    private record RecentSourceMixinStart(
            int sourceId,
            @Nullable ResourceLocation sound,
            @Nullable SoundSource category,
            Vec3 position,
            long createdNanos
    ) {
        boolean matches(
                int requestedSourceId,
                @Nullable ResourceLocation requestedSound,
                @Nullable SoundSource requestedCategory,
                Vec3 requestedPosition,
                long nowNanos
        ) {
            if (!fresh(nowNanos)) {
                return false;
            }
            return sourceId == requestedSourceId
                    && category == requestedCategory
                    && Objects.equals(sound, requestedSound)
                    && position.distanceToSqr(requestedPosition) <= RECENT_SOURCE_MIXIN_START_POSITION_TOLERANCE_SQR;
        }

        boolean fresh(long nowNanos) {
            long age = nowNanos - createdNanos;
            return age >= 0L && age <= RECENT_SOURCE_MIXIN_START_WINDOW_NANOS;
        }
    }

    private static void pruneRecentSourceMixinStarts(long nowNanos) {
        RECENT_SOURCE_MIXIN_STARTS.entrySet().removeIf(entry -> !entry.getValue().fresh(nowNanos));
    }

}
