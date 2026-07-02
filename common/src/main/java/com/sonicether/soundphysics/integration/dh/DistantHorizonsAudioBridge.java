package com.sonicether.soundphysics.integration.dh;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;
import com.sonicether.soundphysics.propeller.PropellerLongRangeAudio;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DistantHorizonsAudioBridge {

    private static final ConcurrentMap<Integer, DistantTerrainOcclusionState> STATES = new ConcurrentHashMap<>();
    private static final AtomicLong queried = new AtomicLong();
    private static final AtomicLong hit = new AtomicLong();
    private static final AtomicLong miss = new AtomicLong();
    private static final AtomicLong fail = new AtomicLong();

    @Nullable
    private static volatile Availability availability;
    private static volatile String latestReason = "none";

    private DistantHorizonsAudioBridge() {
    }

    public static DistantTerrainOcclusionResult computeFarPropellerOcclusion(
            int sourceId,
            @Nullable ClientLevel level,
            SoundPhysicsSoundPolicy.SoundContext context,
            Vec3 listenerPos,
            Vec3 sourcePos,
            double sourceDistance,
            double distanceNorm
    ) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null || !config.distantHorizonsFarPropellerOcclusionEnabled.get()) {
            latestReason = "disabled_config";
            return DistantTerrainOcclusionResult.none("disabled_config");
        }
        if (!PropellerLongRangeAudio.isEligible(context)) {
            latestReason = "not_propeller";
            return DistantTerrainOcclusionResult.none("not_propeller");
        }
        if (level == null || listenerPos == null || sourcePos == null || !finite(listenerPos) || !finite(sourcePos) || !Double.isFinite(sourceDistance)) {
            latestReason = "invalid_input";
            return DistantTerrainOcclusionResult.none("invalid_input");
        }
        if (sourceDistance < config.distantHorizonsFarPropellerMinDistance.get()) {
            latestReason = "too_close";
            STATES.remove(sourceId);
            return DistantTerrainOcclusionResult.identity(true, false, sourceDistance, "too_close");
        }

        DistantTerrainOcclusionState state = STATES.computeIfAbsent(sourceId, DistantTerrainOcclusionState::new);
        long gameTime = level.getGameTime();
        double gainAtFullOcclusion = config.distantHorizonsFarPropellerGainAtFullOcclusion.get();
        double cutoffAtFullOcclusion = config.distantHorizonsFarPropellerCutoffAtFullOcclusion.get();
        if (!state.shouldProbe(gameTime, listenerPos, sourcePos, config.distantHorizonsFarPropellerProbeIntervalTicks.get())) {
            latestReason = "not_due";
            return state.currentResult("not_due", sourceDistance, gainAtFullOcclusion, cutoffAtFullOcclusion);
        }

        Availability requiredApi = requiredApi();
        if (!requiredApi.available()) {
            fail.incrementAndGet();
            latestReason = requiredApi.reason();
            return state.recordFailure(gameTime, listenerPos, sourcePos, sourceDistance, requiredApi.reason(), gainAtFullOcclusion, cutoffAtFullOcclusion);
        }

        int maxRayLength = maxRayLength(config, sourceDistance);
        if (maxRayLength <= 0) {
            latestReason = "invalid_ray";
            return state.recordFailure(gameTime, listenerPos, sourcePos, sourceDistance, "invalid_ray", gainAtFullOcclusion, cutoffAtFullOcclusion);
        }

        DistantHorizonsRaycast raycast = RuntimeHolder.raycast(level, listenerPos, sourcePos, maxRayLength);
        if (!raycast.queried()) {
            fail.incrementAndGet();
            latestReason = raycast.reason();
            DistantTerrainOcclusionResult result = state.recordFailure(gameTime, listenerPos, sourcePos, sourceDistance, raycast.reason(), gainAtFullOcclusion, cutoffAtFullOcclusion);
            logProbe(config, sourceId, context, listenerPos, sourcePos, sourceDistance, raycast, result);
            return result;
        }

        queried.incrementAndGet();
        if (!raycast.hit()) {
            miss.incrementAndGet();
            latestReason = raycast.reason();
            DistantTerrainOcclusionResult result = state.recordMiss(gameTime, listenerPos, sourcePos, sourceDistance, raycast.reason(), gainAtFullOcclusion, cutoffAtFullOcclusion);
            logProbe(config, sourceId, context, listenerPos, sourcePos, sourceDistance, raycast, result);
            return result;
        }
        if (raycast.hitDistance() >= sourceDistance - 8.0D) {
            miss.incrementAndGet();
            latestReason = "hit_at_or_beyond_source";
            DistantHorizonsRaycast adjusted = new DistantHorizonsRaycast(
                    true,
                    false,
                    raycast.hitDistance(),
                    raycast.detailLevel(),
                    raycast.dimension(),
                    "hit_at_or_beyond_source",
                    raycast.message()
            );
            DistantTerrainOcclusionResult result = state.recordMiss(gameTime, listenerPos, sourcePos, sourceDistance, "hit_at_or_beyond_source", gainAtFullOcclusion, cutoffAtFullOcclusion);
            logProbe(config, sourceId, context, listenerPos, sourcePos, sourceDistance, adjusted, result);
            return result;
        }

        hit.incrementAndGet();
        latestReason = "ray_hit";
        double strength = DistantTerrainOcclusionState.computeStrength(
                config.distantHorizonsFarPropellerMinDistance.get(),
                config.distantHorizonsFarPropellerMaxRayLength.get(),
                config.distantHorizonsFarPropellerMaxStrength.get(),
                sourceDistance,
                raycast.hitDistance(),
                raycast.detailLevel()
        );
        DistantTerrainOcclusionResult result = state.recordHit(
                gameTime,
                listenerPos,
                sourcePos,
                sourceDistance,
                raycast.hitDistance(),
                raycast.detailLevel(),
                strength,
                gainAtFullOcclusion,
                cutoffAtFullOcclusion
        );
        logProbe(config, sourceId, context, listenerPos, sourcePos, sourceDistance, raycast, result);
        return result;
    }

    public static String diagnosticsSummaryText() {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        boolean enabled = config != null && config.distantHorizonsFarPropellerOcclusionEnabled.get();
        Availability requiredApi = enabled ? requiredApi() : new Availability(false, "disabled_config");
        boolean cache = requiredApi.available() && RuntimeHolder.cacheCreated();
        return "enabled=" + enabled
                + ", available=" + requiredApi.available()
                + ", cache=" + cache
                + ", tracked=" + STATES.size()
                + ", queried=" + queried.get()
                + ", hit=" + hit.get()
                + ", miss=" + miss.get()
                + ", fail=" + fail.get()
                + ", latestReason=" + latestReason
                + ", apiReason=" + requiredApi.reason();
    }

    public static String diagnosticsFieldsForSource(int sourceId) {
        DistantTerrainOcclusionState state = STATES.get(sourceId);
        if (state == null) {
            return new DistantTerrainOcclusionState(sourceId).diagnosticsFields();
        }
        return state.diagnosticsFields();
    }

    public static int trackedSourceCountForTests() {
        return STATES.size();
    }

    public static void clearAudioStateForRecovery(String reason) {
        STATES.clear();
        latestReason = reason;
        if (requiredApi().available()) {
            RuntimeHolder.clearCache();
        }
    }

    public static void onLevelUnload() {
        clearAudioStateForRecovery("level unload");
    }

    public static void onAudioLibraryReset() {
        clearAudioStateForRecovery("audio library reset");
    }

    public static void onConfigReload() {
        clearAudioStateForRecovery("config reload");
    }

    public static void clearForTests() {
        STATES.clear();
        resetDiagnostics();
        availability = null;
        try {
            if (requiredApi().available()) {
                RuntimeHolder.clearCache();
            }
        } catch (Throwable ignored) {
        }
        availability = null;
    }

    public static void resetDiagnostics() {
        queried.set(0L);
        hit.set(0L);
        miss.set(0L);
        fail.set(0L);
        latestReason = "none";
    }

    static Availability checkApiAvailability(ClassLoader classLoader) {
        try {
            Class.forName("com.seibel.distanthorizons.api.DhApi", false, classLoader);
            Class<?> terrainRepoClass = Class.forName("com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo", false, classLoader);
            Method createSoftCache = terrainRepoClass.getMethod("createSoftCache");
            if (createSoftCache == null) {
                return new Availability(false, "dh_api_too_old");
            }
            return new Availability(true, "ok");
        } catch (ClassNotFoundException throwable) {
            return new Availability(false, "dh_not_loaded");
        } catch (NoSuchMethodException throwable) {
            return new Availability(false, "dh_api_too_old");
        } catch (LinkageError throwable) {
            return new Availability(false, "dh_api_too_old");
        } catch (Throwable throwable) {
            return new Availability(false, "exception");
        }
    }

    private static Availability requiredApi() {
        Availability current = availability;
        if (current != null) {
            return current;
        }
        Availability checked = checkApiAvailability(DistantHorizonsAudioBridge.class.getClassLoader());
        availability = checked;
        return checked;
    }

    private static int maxRayLength(SoundPhysicsConfig config, double sourceDistance) {
        double length = Math.min(Math.max(sourceDistance - 4.0D, 0.0D), config.distantHorizonsFarPropellerMaxRayLength.get());
        if (!Double.isFinite(length) || length <= 0.0D) {
            return 0;
        }
        return Math.max(1, (int) Math.floor(length));
    }

    private static boolean finite(Vec3 pos) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z);
    }

    private static void logProbe(
            SoundPhysicsConfig config,
            int sourceId,
            SoundPhysicsSoundPolicy.SoundContext context,
            Vec3 listenerPos,
            Vec3 sourcePos,
            double sourceDistance,
            DistantHorizonsRaycast raycast,
            DistantTerrainOcclusionResult result
    ) {
        if (!config.distantHorizonsFarPropellerDebugLogging.get()) {
            return;
        }
        Loggers.log(
                "DH prop occ source={} sound={} listener={} sourcePos={} distance={} dim={} result={} hitDistance={} detail={} strength={} gainMul={} cutoffMul={} message={}",
                sourceId,
                context.soundId(),
                formatVec(listenerPos),
                formatVec(sourcePos),
                format(sourceDistance),
                raycast.dimension(),
                result.reason(),
                result.hitDistance() < 0.0D ? "unknown" : format(result.hitDistance()),
                result.detailLevel() < 0 ? "unknown" : Byte.toString(result.detailLevel()),
                format(result.occlusionStrength()),
                format(result.directGainMultiplier()),
                format(result.directCutoffMultiplier()),
                raycast.message()
        );
    }

    private static String formatVec(Vec3 pos) {
        return format(pos.x) + "," + format(pos.y) + "," + format(pos.z);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static final class RuntimeHolder {

        private RuntimeHolder() {
        }

        static DistantHorizonsRaycast raycast(ClientLevel level, Vec3 listenerPos, Vec3 sourcePos, int maxRayLength) {
            return DistantHorizonsApiRuntime.raycast(level, listenerPos, sourcePos, maxRayLength);
        }

        static boolean cacheCreated() {
            return DistantHorizonsApiRuntime.cacheCreated();
        }

        static void clearCache() {
            DistantHorizonsApiRuntime.clearCache();
        }
    }

    record Availability(boolean available, String reason) {
    }

}
