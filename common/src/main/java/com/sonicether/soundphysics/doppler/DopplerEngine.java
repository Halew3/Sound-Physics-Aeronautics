package com.sonicether.soundphysics.doppler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.AudioSourceRecovery;
import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.RecordDiagnostics;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsPolicyDiagnostics;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;

public final class DopplerEngine {

    private static final float PITCH_UPDATE_EPSILON = 0.0025F;
    private static final double ACTIVE_MULTIPLIER_EPSILON = 0.0005D;
    private static final long STALE_SOURCE_TICKS = 200L;
    private static final long DEBUG_SUMMARY_INTERVAL_TICKS = 100L;

    private static final ConcurrentMap<Integer, DopplerSourceState> SOURCES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, ForcedPitch> FORCED_PITCHES = new ConcurrentHashMap<>();
    private static final DopplerListenerState LISTENER_STATE = new DopplerListenerState();
    private static final DopplerDiagnostics DIAGNOSTICS = new DopplerDiagnostics();
    private static final PitchBackend OPENAL_PITCH_BACKEND = new OpenAlPitchBackend();

    private static volatile PitchBackend pitchBackend = OPENAL_PITCH_BACKEND;
    private static long lastDebugSummaryGameTime = Long.MIN_VALUE;
    private static volatile String latestSkipReason = "none";
    private static volatile String latestForcedPitchMessage = "none";
    private static long cachedListenerGameTime = Long.MIN_VALUE;
    private static DopplerKinematicState cachedListenerKinematics = DopplerKinematicState.unreliable(Vec3.ZERO, null, 0L);

    private DopplerEngine() {
    }

    public static void onPlaySource(int sourceId, Vec3 sourcePosition, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
        onPlaySource(sourceId, sourcePosition, category, sound, false, false, null, false, false);
    }

    public static void onPlaySource(
            int sourceId,
            Vec3 sourcePosition,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            boolean relative,
            boolean noAttenuation,
            @Nullable String soundInstanceClassName,
            boolean streaming,
            boolean tickable
    ) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null || category == null || sound == null || !isDopplerRuntimeEnabled(config)) {
            return;
        }

        SoundPhysicsSoundPolicy.SoundContext context = new SoundPhysicsSoundPolicy.SoundContext(sound, category, soundInstanceClassName, relative, noAttenuation, streaming, true, tickable);
        SoundPhysicsSoundPolicy.Decision policy = SoundPhysicsSoundPolicy.evaluateDoppler(config, context);
        if (!policy.apply()) {
            recordPolicySkip(policy.reason(), sound, soundInstanceClassName);
            return;
        }

        registerSource(sourceId, sourcePosition, category, sound, soundInstanceClassName, false);
        RecordDiagnostics.recordDopplerRegistered(sourceId, sourcePosition, context);
    }

    public static boolean shouldUpdate(long gameTime, SoundInstance sound) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null) {
            return false;
        }
        if (!isDopplerRuntimeEnabled(config)) {
            return !SOURCES.isEmpty();
        }

        int interval = Math.max(config.dopplerUpdateIntervalTicks.get(), 1);
        return Math.floorMod(gameTime + sound.hashCode(), interval) == 0L;
    }

    public static void updateSource(
            int sourceId,
            Vec3 sourcePosition,
            SoundSource category,
            ResourceLocation sound,
            long gameTime,
            boolean relative,
            boolean noAttenuation,
            float currentMinecraftPitch,
            boolean tickable,
            String soundInstanceClassName
    ) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null) {
            return;
        }
        if (!isDopplerRuntimeEnabled(config)) {
            DIAGNOSTICS.recordSkippedByDisabledConfig();
            clearAll(true, "disabled config");
            return;
        }

        SoundPhysicsSoundPolicy.SoundContext context = new SoundPhysicsSoundPolicy.SoundContext(sound, category, soundInstanceClassName, relative, noAttenuation, false, false, tickable);
        SoundPhysicsSoundPolicy.Decision policy = SoundPhysicsSoundPolicy.evaluateDoppler(config, context);
        if (!policy.apply()) {
            recordPolicySkip(policy.reason(), sound, soundInstanceClassName);
            markSkippedSource(sourceId, policy.reason());
            removeSkippedSource(sourceId, category, sound);
            return;
        }

        DopplerSourceState sourceState = SOURCES.get(sourceId);
        boolean registeredFromUpdate = false;
        if (sourceState == null || !sourceState.matches(sound, category)) {
            sourceState = registerSource(sourceId, sourcePosition, category, sound, soundInstanceClassName, true);
            registeredFromUpdate = true;
        }
        if (registeredFromUpdate) {
            RecordDiagnostics.recordDopplerRegistered(sourceId, sourcePosition, context);
        }

        sourceState.observeMinecraftPitch(currentMinecraftPitch, tickable, gameTime);
        if (!sourceState.hasBasePitchCaptured()) {
            sourceState.observeOpenAlPitchFallback(safeGetPitch(sourceId, category, sound, "capture OpenAL pitch fallback"), gameTime);
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }

        Vec3 listenerPosition = client.gameRenderer.getMainCamera().getPosition();
        DopplerKinematicScene scene = DopplerKinematics.createScene(client);
        DopplerKinematicState listenerKinematics = resolveListener(client, scene, listenerPosition, gameTime, config);
        DopplerKinematicState sourceKinematics = resolveSource(scene, sourceState, sourcePosition, sound, category, gameTime, tickable, config);

        double targetMultiplier = 1.0D;
        double radialVelocity = 0.0D;
        double relativeSpeedAlongLineOfSight = 0.0D;
        DopplerSourceState.PitchDecision neutralSkipReason = null;
        if (listenerKinematics.reliable() && sourceKinematics.reliable()) {
            targetMultiplier = DopplerMath.computeMultiplier(
                    sourceKinematics.worldPosition(),
                    sourceKinematics.worldVelocity(),
                    listenerKinematics.worldPosition(),
                    listenerKinematics.worldVelocity(),
                    config.dopplerSpeedOfSound.get(),
                    DiagnosticRuntimeOverrides.effectiveDopplerStrength(config),
                    DiagnosticRuntimeOverrides.effectiveDopplerMinPitchMultiplier(config),
                    DiagnosticRuntimeOverrides.effectiveDopplerMaxPitchMultiplier(config)
            );
            RadialMotion radialMotion = radialMotion(sourceKinematics, listenerKinematics);
            radialVelocity = radialMotion.radialVelocity();
            relativeSpeedAlongLineOfSight = radialMotion.relativeSpeedAlongLineOfSight();
        } else if (!listenerKinematics.reliable()) {
            neutralSkipReason = DopplerSourceState.PitchDecision.SKIPPED_UNRELIABLE_LISTENER;
        } else {
                neutralSkipReason = DopplerSourceState.PitchDecision.SKIPPED_UNRELIABLE_SOURCE;
        }

        sourceState.rememberTargetMultiplier(targetMultiplier);
        sourceState.rememberRadialMotion(radialVelocity, relativeSpeedAlongLineOfSight, targetMultiplier - 1.0D);
        double smoothedMultiplier = DopplerMath.smoothMultiplier(
                sourceState.smoothedMultiplier(),
                targetMultiplier,
                sourceState.smoothingDeltaSeconds(gameTime),
                DiagnosticRuntimeOverrides.effectiveDopplerSmoothingTimeMs(config) / 1000.0D
        );

        sourceState.rememberAcousticSpace(sourceKinematics.acousticSpaceId(), sourceKinematics.version());

        DIAGNOSTICS.recordSourceUpdated(gameTime);
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)) {
            SoundPhysicsPolicyDiagnostics.recordPropellerProcessedDoppler();
        }
        DIAGNOSTICS.recordMultiplier(smoothedMultiplier);
        applyPitchIfNeeded(sourceId, sourceState, smoothedMultiplier, gameTime, neutralSkipReason);
        RecordDiagnostics.recordDopplerUpdated(sourceId, sourceState.latestPitchDecision().name());
        pruneStaleSources(gameTime);
        logSummaryIfNeeded(config, gameTime);
    }

    public static void onLevelUnload() {
        clearAll(true, "level unload");
    }

    public static void onAudioLibraryReset() {
        clearAll(false, "audio library reset");
    }

    public static void onConfigReload() {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config != null && !isDopplerRuntimeEnabled(config)) {
            clearAll(true, "config reload disabled Doppler");
        }
    }

    public static DopplerDiagnosticsSummary diagnosticsSummary() {
        return DIAGNOSTICS.summary(SOURCES.size());
    }

    public static String diagnosticsSummaryText() {
        return diagnosticsSummary().toString();
    }

    public static String debugStatusText() {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null) {
            return "config=not_initialized";
        }
        return "mode=" + DiagnosticRuntimeOverrides.mode().commandName()
                + ", enabled=" + DiagnosticRuntimeOverrides.dopplerEnabled(config)
                + ", strength=" + DiagnosticRuntimeOverrides.effectiveDopplerStrength(config)
                + ", minPitch=" + DiagnosticRuntimeOverrides.effectiveDopplerMinPitchMultiplier(config)
                + ", maxPitch=" + DiagnosticRuntimeOverrides.effectiveDopplerMaxPitchMultiplier(config)
                + ", smoothingMs=" + DiagnosticRuntimeOverrides.effectiveDopplerSmoothingTimeMs(config)
                + ", tracked=" + SOURCES.size()
                + ", latestSkip=" + latestSkipReason
                + ", diagnostics=" + diagnosticsSummaryText();
    }

    public static String sourcesDiagnosticsText(int limit) {
        return String.join(" ", sourcesDiagnosticsLines(SourceQuery.all(limit)));
    }

    public static List<String> sourcesDiagnosticsLines(SourceQuery query) {
        if (SOURCES.isEmpty()) {
            if (query.filter() == SourceFilter.PROPELLER) {
                return noTrackedPropellerSourcesLines();
            }
            return List.of("tracked sources: none");
        }

        int safeLimit = Math.max(query.limit(), 1);
        List<DopplerSourceState> states = new ArrayList<>(SOURCES.values());
        states.sort(Comparator.comparingLong(DopplerSourceState::lastUpdateNanos).reversed());

        List<String> lines = new ArrayList<>();
        lines.add("tracked sources: total=" + SOURCES.size()
                + ", filter=" + query.filter()
                + (query.soundContains() == null ? "" : ", soundContains=" + query.soundContains())
                + ", showing=" + safeLimit);
        int added = 0;
        for (DopplerSourceState state : states) {
            if (!query.matches(state)) {
                continue;
            }
            if (added >= safeLimit) {
                lines.add("... " + (states.size() - added) + " more tracked sources not shown");
                break;
            }
            lines.add(sourceDiagnosticsLine(state));
            added++;
        }
        if (added == 0) {
            if (query.filter() == SourceFilter.PROPELLER) {
                lines.addAll(noTrackedPropellerSourcesLines());
            } else {
                lines.add("no tracked sources match filter");
            }
        }
        if (query.filter() == SourceFilter.PROPELLER) {
            lines.add(propellerSourcesSummary(states));
        }
        return List.copyOf(lines);
    }

    public static String simulateText(boolean approach) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        double speedOfSound = config == null ? 343.0D : config.dopplerSpeedOfSound.get();
        double strength = DiagnosticRuntimeOverrides.effectiveDopplerStrength(config);
        double minPitch = DiagnosticRuntimeOverrides.effectiveDopplerMinPitchMultiplier(config);
        double maxPitch = DiagnosticRuntimeOverrides.effectiveDopplerMaxPitchMultiplier(config);
        double speed = 20.0D;
        Vec3 sourcePosition = new Vec3(20.0D, 0.0D, 0.0D);
        Vec3 sourceVelocity = approach ? new Vec3(-speed, 0.0D, 0.0D) : new Vec3(speed, 0.0D, 0.0D);
        Vec3 listenerPosition = Vec3.ZERO;
        Vec3 listenerVelocity = Vec3.ZERO;
        double multiplier = DopplerMath.computeMultiplier(
                sourcePosition,
                sourceVelocity,
                listenerPosition,
                listenerVelocity,
                speedOfSound,
                strength,
                minPitch,
                maxPitch
        );
        return (approach ? "approach" : "recede")
                + " sourceVelocity=" + formatVec(sourceVelocity)
                + " listenerVelocity=" + formatVec(listenerVelocity)
                + " speedOfSound=" + formatDouble(speedOfSound)
                + " strength=" + formatDouble(strength)
                + " computedMultiplier=" + formatDouble(multiplier)
                + " expected=" + (approach ? ">1 higher pitch" : "<1 lower pitch");
    }

    public static String forceLatestPropellerMultiplierText(double multiplier, int seconds) {
        DopplerSourceState state = latestState(true);
        if (state == null) {
            return "No tracked propeller source exists; start a propeller and run /spr_aero propeller sources first.";
        }
        String result = forceMultiplier(state, multiplier, seconds);
        long propellerCount = SOURCES.values().stream().filter(DopplerEngine::isPropeller).count();
        if (propellerCount > 1L) {
            result += " Warning: " + propellerCount + " propeller sources are tracked; this command forced only source "
                    + state.sourceId() + ". Use propeller_all to test all audible loops.";
        }
        return result;
    }

    public static String forceAllPropellersMultiplierText(double multiplier, int seconds) {
        List<DopplerSourceState> propellers = SOURCES.values().stream()
                .filter(DopplerEngine::isPropeller)
                .sorted(Comparator.comparingInt(DopplerSourceState::sourceId))
                .toList();
        if (propellers.isEmpty()) {
            return "No tracked propeller source exists; start a propeller and run /spr_aero propeller sources first.";
        }

        List<Integer> forcedIds = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (DopplerSourceState state : propellers) {
            ForceResult result = forceMultiplierInternal(state, multiplier, seconds);
            if (result.success()) {
                forcedIds.add(state.sourceId());
            } else {
                failures.add("source " + state.sourceId() + ": " + result.message());
            }
        }

        if (forcedIds.isEmpty()) {
            return "Failed to force propeller sources: " + String.join("; ", failures);
        }

        int clampedSeconds = clampSeconds(seconds);
        double clampedMultiplier = clampMultiplier(multiplier);
        String failureText = failures.isEmpty() ? "" : " failures=(" + String.join("; ", failures) + ")";
        return "Forced " + forcedIds.size()
                + " propeller sources: ids=" + forcedIds
                + " multiplier=" + formatDouble(clampedMultiplier)
                + " duration=" + clampedSeconds + "s"
                + failureText;
    }

    public static String forceLatestMultiplierText(double multiplier, int seconds) {
        DopplerSourceState state = latestState(false);
        if (state == null) {
            return "No tracked Doppler source exists.";
        }
        return forceMultiplier(state, multiplier, seconds);
    }

    public static String forceSourceMultiplierText(int sourceId, double multiplier, int seconds) {
        DopplerSourceState state = SOURCES.get(sourceId);
        if (state == null) {
            return "No tracked Doppler source exists for source " + sourceId + ".";
        }
        return forceMultiplier(state, multiplier, seconds);
    }

    public static String audibleTestOnText() {
        DopplerSourceState state = latestState(true);
        if (state == null) {
            state = latestState(false);
        }
        if (state == null) {
            return "No tracked Doppler source exists for audible_test.";
        }
        return forceMultiplier(state, 1.5D, 10);
    }

    public static String audibleTestOffText() {
        if (FORCED_PITCHES.isEmpty()) {
            return "No active forced Doppler pitch.";
        }
        String restored = restoreAllForcedPitches("manual off");
        latestForcedPitchMessage = restored;
        return restored;
    }

    public static String forceStatusText() {
        expireForcedPitches(System.nanoTime());
        if (FORCED_PITCHES.isEmpty()) {
            return "No active forced Doppler pitch. latestRestore=" + latestForcedPitchMessage;
        }
        List<String> parts = new ArrayList<>();
        long now = System.nanoTime();
        for (ForcedPitch active : FORCED_PITCHES.values().stream().sorted(Comparator.comparingInt(ForcedPitch::sourceId)).toList()) {
            long remainingSeconds = Math.max(1L, (active.endNanos() - now + 999_999_999L) / 1_000_000_000L);
            float observedPitch = safeGetPitch(active.sourceId(), active.category(), active.sound(), "forced pitch status");
            boolean drift = Math.abs(observedPitch - active.finalPitch()) > PITCH_UPDATE_EPSILON;
            active.lastObservedPitch = observedPitch;
            active.lastDrift = drift;
            parts.add("source=" + active.sourceId()
                    + " sound=" + active.sound()
                    + " multiplier=" + formatDouble(active.multiplier())
                    + " basePitch=" + formatDouble(active.basePitch())
                    + " finalPitch=" + formatDouble(active.finalPitch())
                    + " expected=" + formatDouble(active.finalPitch())
                    + " observedPitch=" + formatDouble(observedPitch)
                    + " drift=" + drift
                    + " reapplied=" + active.reapplied()
                    + " remainingSeconds=" + remainingSeconds);
        }
        return "Forced Doppler pitch active: total=" + FORCED_PITCHES.size() + " | " + String.join(" | ", parts);
    }

    public static void tick() {
        long now = System.nanoTime();
        expireForcedPitches(now);
        for (ForcedPitch active : FORCED_PITCHES.values()) {
            reapplyForcedPitchIfNeeded(active, currentGameTime());
        }
    }

    public static void resetDiagnostics() {
        DIAGNOSTICS.reset();
        lastDebugSummaryGameTime = Long.MIN_VALUE;
        latestSkipReason = "none";
        latestForcedPitchMessage = "none";
    }

    public static int clearAudioStateForRecovery(String reason) {
        int cleared = SOURCES.size();
        clearAll(false, reason);
        FORCED_PITCHES.clear();
        LISTENER_STATE.reset();
        cachedListenerGameTime = Long.MIN_VALUE;
        cachedListenerKinematics = DopplerKinematicState.unreliable(Vec3.ZERO, null, 0L);
        if (cleared > 0) {
            AudioSourceRecovery.recordStaleSourcesPruned(cleared, reason);
        }
        latestForcedPitchMessage = "cleared by audio recovery: " + reason;
        return cleared;
    }

    public static int trackedSourceCount() {
        return SOURCES.size();
    }

    public static long trackedPropellerSourceCount() {
        return SOURCES.values().stream().filter(DopplerEngine::isPropeller).count();
    }

    public static boolean forcedPitchActive() {
        expireForcedPitches(System.nanoTime());
        return !FORCED_PITCHES.isEmpty();
    }

    static void clearForTests() {
        SOURCES.clear();
        LISTENER_STATE.reset();
        DIAGNOSTICS.reset();
        pitchBackend = OPENAL_PITCH_BACKEND;
        lastDebugSummaryGameTime = Long.MIN_VALUE;
        latestSkipReason = "none";
        latestForcedPitchMessage = "none";
        cachedListenerGameTime = Long.MIN_VALUE;
        cachedListenerKinematics = DopplerKinematicState.unreliable(Vec3.ZERO, null, 0L);
        FORCED_PITCHES.clear();
        DopplerKinematics.setProvider(null);
    }

    static void setPitchBackendForTests(PitchBackend backend) {
        pitchBackend = backend == null ? OPENAL_PITCH_BACKEND : backend;
    }

    static void rememberAcousticSpaceForTests(int sourceId, @Nullable String acousticSpaceId, long version) {
        DopplerSourceState state = SOURCES.get(sourceId);
        if (state != null) {
            state.rememberAcousticSpace(acousticSpaceId, version);
        }
    }

    private static DopplerSourceState registerSource(int sourceId, Vec3 sourcePosition, SoundSource category, ResourceLocation sound, @Nullable String soundInstanceClassName, boolean fromUpdate) {
        DopplerSourceState state = new DopplerSourceState(sourceId, sound, category, sourcePosition, soundInstanceClassName);

        DopplerSourceState previous = SOURCES.put(sourceId, state);
        if (previous != null && !previous.matches(sound, category)) {
            DIAGNOSTICS.recordSourceReuseReset();
        }

        DIAGNOSTICS.recordSourceRegistered();
        if (!fromUpdate) {
            Loggers.logDoppler("Registered Doppler source {} sound={} category={}", sourceId, sound, category);
        }
        return state;
    }

    private static DopplerKinematicState resolveListener(Minecraft client, DopplerKinematicScene scene, Vec3 listenerPosition, long gameTime, SoundPhysicsConfig config) {
        if (cachedListenerGameTime == gameTime) {
            return cachedListenerKinematics;
        }

        DopplerKinematicState provided = safeListener(scene, listenerPosition, gameTime);
        if (provided.reliable() && DopplerListenerState.isUsableVelocity(provided.worldVelocity(), config.dopplerMaxListenerSpeed.get())) {
            LISTENER_STATE.acceptExternal(provided.worldPosition(), provided.worldVelocity(), gameTime, DopplerListenerState.VelocitySource.SABLE);
            return cacheListener(gameTime, provided);
        }

        DopplerKinematicState sampled = LISTENER_STATE.sample(listenerPosition, gameTime, config.dopplerMaxListenerSpeed.get(), provided.acousticSpaceId(), provided.version(), DopplerListenerState.VelocitySource.CAMERA_DELTA);
        if (sampled.reliable()) {
            return cacheListener(gameTime, sampled);
        }

        Vec3 playerVelocity = client.player == null ? Vec3.ZERO : client.player.getDeltaMovement().scale(20.0D);
        if (DopplerListenerState.isUsableVelocity(playerVelocity, config.dopplerMaxListenerSpeed.get())) {
            LISTENER_STATE.acceptExternal(listenerPosition, playerVelocity, gameTime, DopplerListenerState.VelocitySource.PLAYER_DELTA);
            return cacheListener(gameTime, new DopplerKinematicState(listenerPosition, playerVelocity, provided.acousticSpaceId(), provided.version(), true));
        }

        if (!sampled.reliable()) {
            DIAGNOSTICS.recordUnreliableListenerVelocity();
        }
        return cacheListener(gameTime, sampled);
    }

    private static DopplerKinematicState resolveSource(
            DopplerKinematicScene scene,
            DopplerSourceState state,
            Vec3 sourcePosition,
            ResourceLocation sound,
            SoundSource category,
            long gameTime,
            boolean tickable,
            SoundPhysicsConfig config
    ) {
        DopplerKinematicState provided = safeSource(scene, state.sourceId(), sourcePosition, sound, category, gameTime);
        if (provided.reliable() && DopplerListenerState.isUsableVelocity(provided.worldVelocity(), config.dopplerMaxSourceSpeed.get())) {
            state.acceptExternalVelocity(provided.worldPosition(), provided.worldVelocity(), gameTime, DopplerSourceState.VelocitySource.SABLE_SPACE);
            return provided;
        }

        DopplerSourceState.VelocitySource fallbackSource = tickable
                ? DopplerSourceState.VelocitySource.TICKABLE_POSITION_DELTA
                : DopplerSourceState.VelocitySource.SOUND_POSITION_DELTA;
        DopplerKinematicState sampled = state.sampleVelocity(sourcePosition, gameTime, config.dopplerMaxSourceSpeed.get(), provided.acousticSpaceId(), provided.version(), fallbackSource);
        if (!sampled.reliable()) {
            DIAGNOSTICS.recordUnreliableSourceVelocity();
        }
        return sampled;
    }

    private static DopplerKinematicState cacheListener(long gameTime, DopplerKinematicState state) {
        cachedListenerGameTime = gameTime;
        cachedListenerKinematics = state;
        return state;
    }

    private static DopplerKinematicState safeListener(DopplerKinematicScene scene, Vec3 listenerPosition, long gameTime) {
        try {
            return scene.listener(listenerPosition, gameTime);
        } catch (Throwable throwable) {
            Loggers.logDoppler("Doppler listener kinematics failed: {}", throwable.getMessage());
            return DopplerKinematicState.unreliable(listenerPosition, null, 0L);
        }
    }

    private static DopplerKinematicState safeSource(
            DopplerKinematicScene scene,
            int sourceId,
            Vec3 sourcePosition,
            ResourceLocation sound,
            SoundSource category,
            long gameTime
    ) {
        try {
            return scene.source(sourceId, sourcePosition, sound, category, gameTime);
        } catch (Throwable throwable) {
            Loggers.logDoppler("Doppler source kinematics failed for {}: {}", sound, throwable.getMessage());
            return DopplerKinematicState.unreliable(sourcePosition, null, 0L);
        }
    }

    private static void applyPitchIfNeeded(
            int sourceId,
            DopplerSourceState state,
            double smoothedMultiplier,
            long gameTime,
            @Nullable DopplerSourceState.PitchDecision neutralSkipReason
    ) {
        ForcedPitch activeForcedPitch = activeForcedPitchFor(sourceId);
        if (activeForcedPitch != null) {
            reapplyForcedPitchIfNeeded(activeForcedPitch, gameTime);
            state.markPitchApplied(activeForcedPitch.finalPitch(), activeForcedPitch.multiplier(), gameTime, DopplerSourceState.PitchControlSource.FORCED_COMMAND);
            return;
        }

        if (!state.hasBasePitchCaptured()) {
            state.markPitchSkipped(smoothedMultiplier, gameTime, DopplerSourceState.PitchDecision.SKIPPED_NO_BASE_PITCH);
            DIAGNOSTICS.recordPitchUpdateSkippedUnchanged();
            return;
        }

        boolean needsPitchControl = state.hasAppliedDoppler() || Math.abs(smoothedMultiplier - 1.0D) > ACTIVE_MULTIPLIER_EPSILON;
        if (!needsPitchControl) {
            state.markPitchSkipped(
                    smoothedMultiplier,
                    gameTime,
                    neutralSkipReason == null ? DopplerSourceState.PitchDecision.SKIPPED_MULTIPLIER_NEAR_ONE : neutralSkipReason
            );
            DIAGNOSTICS.recordPitchUpdateSkippedUnchanged();
            return;
        }

        float finalPitch = state.pitchForMultiplier(smoothedMultiplier);
        if (!Float.isFinite(finalPitch) || finalPitch <= 0.0F) {
            state.markPitchSkipped(1.0D, gameTime, DopplerSourceState.PitchDecision.SKIPPED_NO_BASE_PITCH);
            DIAGNOSTICS.recordPitchUpdateSkippedUnchanged();
            return;
        }

        if (!state.shouldApplyPitch(finalPitch, PITCH_UPDATE_EPSILON)) {
            state.markPitchSkipped(smoothedMultiplier, gameTime, DopplerSourceState.PitchDecision.SKIPPED_MULTIPLIER_NEAR_ONE);
            DIAGNOSTICS.recordPitchUpdateSkippedUnchanged();
            return;
        }

        if (!safeSourceExists(sourceId, state.category(), state.sound(), "apply Doppler pitch")) {
            state.markPitchSkipped(smoothedMultiplier, gameTime, DopplerSourceState.PitchDecision.SKIPPED_SOURCE_GONE);
            pruneInvalidSource(sourceId, state, "apply Doppler pitch source gone");
            return;
        }

        if (safeSetPitch(sourceId, finalPitch, state.category(), state.sound(), "apply Doppler pitch")) {
            state.markPitchApplied(finalPitch, smoothedMultiplier, gameTime);
            DIAGNOSTICS.recordPitchUpdateApplied();
        } else {
            state.markPitchSkipped(smoothedMultiplier, gameTime, DopplerSourceState.PitchDecision.SKIPPED_OPENAL_SET_FAILED);
        }
    }

    private static void removeSkippedSource(int sourceId, SoundSource category, ResourceLocation sound) {
        DopplerSourceState state = SOURCES.get(sourceId);
        if (state == null) {
            return;
        }

        boolean restore = state.matches(sound, category);
        removeSource(sourceId, restore);
    }

    private static void markSkippedSource(int sourceId, SoundPhysicsSoundPolicy.DecisionReason reason) {
        DopplerSourceState state = SOURCES.get(sourceId);
        if (state == null) {
            return;
        }
        if (reason == SoundPhysicsSoundPolicy.DecisionReason.SABLE_DELEGATE
                || reason == SoundPhysicsSoundPolicy.DecisionReason.PROPELLER_SKIPPED_SABLE_DELEGATE) {
            long gameTime = currentGameTime();
            state.markSableDelegateSkipped(gameTime == Long.MIN_VALUE ? 0L : gameTime);
        }
    }

    private static void removeSource(int sourceId, boolean restore) {
        DopplerSourceState state = SOURCES.remove(sourceId);
        if (state == null || !restore) {
            return;
        }

        restorePitch(state);
    }

    private static void clearAll(boolean restorePitch, String reason) {
        if (!FORCED_PITCHES.isEmpty()) {
            if (restorePitch) {
                latestForcedPitchMessage = restoreAllForcedPitches(reason);
            } else {
                FORCED_PITCHES.clear();
            }
        }
        if (SOURCES.isEmpty()) {
            LISTENER_STATE.reset();
            cachedListenerGameTime = Long.MIN_VALUE;
            cachedListenerKinematics = DopplerKinematicState.unreliable(Vec3.ZERO, null, 0L);
            return;
        }

        if (restorePitch) {
            for (DopplerSourceState state : SOURCES.values()) {
                restorePitch(state);
            }
        }

        SOURCES.clear();
        LISTENER_STATE.reset();
        cachedListenerGameTime = Long.MIN_VALUE;
        cachedListenerKinematics = DopplerKinematicState.unreliable(Vec3.ZERO, null, 0L);
        Loggers.logDoppler("Cleared Doppler state: {}", reason);
    }

    private static void restorePitch(DopplerSourceState state) {
        if (!state.hasAppliedDoppler() || !state.hasBasePitchCaptured()) {
            return;
        }
        if (!safeSourceExists(state.sourceId(), state.category(), state.sound(), "restore Doppler pitch")) {
            return;
        }
        if (safeSetPitch(state.sourceId(), state.basePitch(), state.category(), state.sound(), "restore Doppler pitch")) {
            state.markRestored();
        }
    }

    private static String forceMultiplier(DopplerSourceState state, double requestedMultiplier, int requestedSeconds) {
        return forceMultiplierInternal(state, requestedMultiplier, requestedSeconds).message();
    }

    private static ForceResult forceMultiplierInternal(DopplerSourceState state, double requestedMultiplier, int requestedSeconds) {
        if (!safeSourceExists(state.sourceId(), state.category(), state.sound(), "force multiplier")) {
            pruneInvalidSource(state.sourceId(), state, "force multiplier source missing");
            return ForceResult.failure("Tracked source " + state.sourceId() + " exists but OpenAL source is not live.");
        }

        int seconds = clampSeconds(requestedSeconds);
        double multiplier = clampMultiplier(requestedMultiplier);
        float basePitch = state.hasBasePitchCaptured() ? state.basePitch() : safeGetPitch(state.sourceId(), state.category(), state.sound(), "force multiplier base pitch");
        long gameTime = currentGameTime();
        if (!state.hasBasePitchCaptured()) {
            state.observeOpenAlPitchFallback(basePitch, gameTime == Long.MIN_VALUE ? 0L : gameTime);
        }
        float finalPitch = basePitch * (float) multiplier;
        if (!Float.isFinite(finalPitch) || finalPitch <= 0.0F) {
            return ForceResult.failure("Cannot force source " + state.sourceId() + " because computed pitch is invalid.");
        }

        if (!safeSetPitch(state.sourceId(), finalPitch, state.category(), state.sound(), "force multiplier set pitch")) {
            return ForceResult.failure("Failed to force OpenAL pitch for source " + state.sourceId() + ".");
        }

        float observedPitch = safeGetPitch(state.sourceId(), state.category(), state.sound(), "force multiplier observed pitch");
        ForcedPitch forced = new ForcedPitch(
                state.sourceId(),
                state.sound(),
                state.category(),
                basePitch,
                finalPitch,
                multiplier,
                System.nanoTime() + seconds * 1_000_000_000L
        );
        forced.lastObservedPitch = observedPitch;
        FORCED_PITCHES.put(state.sourceId(), forced);
        state.markPitchApplied(finalPitch, multiplier, gameTime == Long.MIN_VALUE ? 0L : gameTime, DopplerSourceState.PitchControlSource.FORCED_COMMAND);
        String warning = finalPitch > 2.0F
                ? " warning=Diagnostic final pitch " + formatDouble(finalPitch) + " exceeds normal pitch range; this is intentional for audible testing."
                : "";
        return ForceResult.success("Forcing source " + state.sourceId()
                + " " + state.sound()
                + " to multiplier " + formatDouble(multiplier)
                + " for " + seconds + "s. basePitch=" + formatDouble(basePitch)
                + " finalPitch=" + formatDouble(finalPitch)
                + " setPitch=true observedPitch=" + formatDouble(observedPitch)
                + warning);
    }

    private static String restoreAllForcedPitches(String reason) {
        List<ForcedPitch> active = new ArrayList<>(FORCED_PITCHES.values());
        active.sort(Comparator.comparingInt(ForcedPitch::sourceId));
        FORCED_PITCHES.clear();
        if (active.isEmpty()) {
            return "No active forced Doppler pitch.";
        }
        List<String> restored = new ArrayList<>();
        for (ForcedPitch forced : active) {
            restored.add(restoreForcedPitch(forced, reason));
        }
        return String.join(" | ", restored);
    }

    private static String restoreForcedPitch(ForcedPitch active, String reason) {
        if (!safeSourceExists(active.sourceId(), active.category(), active.sound(), "restore forced pitch")) {
            DopplerSourceState state = SOURCES.get(active.sourceId());
            if (state != null) {
                pruneInvalidSource(active.sourceId(), state, "restore forced pitch source missing");
            }
            return "Forced Doppler pitch source " + active.sourceId()
                    + " sound=" + active.sound()
                    + " ended before restore because OpenAL source is not live.";
        }
        safeSetPitch(active.sourceId(), active.basePitch(), active.category(), active.sound(), "restore forced pitch");
        float observedPitch = safeGetPitch(active.sourceId(), active.category(), active.sound(), "restore forced pitch observed");
        DopplerSourceState state = SOURCES.get(active.sourceId());
        if (state != null) {
            state.markRestored();
        }
        return "Restored forced Doppler pitch for source " + active.sourceId()
                + " sound=" + active.sound()
                + " basePitch=" + formatDouble(active.basePitch())
                + " observedPitch=" + formatDouble(observedPitch)
                + " reason=" + reason;
    }

    private static void expireForcedPitches(long nowNanos) {
        List<ForcedPitch> expired = new ArrayList<>();
        Iterator<Map.Entry<Integer, ForcedPitch>> iterator = FORCED_PITCHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ForcedPitch> entry = iterator.next();
            if (nowNanos >= entry.getValue().endNanos()) {
                expired.add(entry.getValue());
                iterator.remove();
            }
        }
        if (!expired.isEmpty()) {
            expired.sort(Comparator.comparingInt(ForcedPitch::sourceId));
            List<String> restored = new ArrayList<>();
            for (ForcedPitch pitch : expired) {
                restored.add(restoreForcedPitch(pitch, "expired"));
            }
            latestForcedPitchMessage = String.join(" | ", restored);
        }
    }

    private static boolean reapplyForcedPitchIfNeeded(ForcedPitch active, long gameTime) {
        if (!safeSourceExists(active.sourceId(), active.category(), active.sound(), "reapply forced pitch")) {
            DopplerSourceState state = SOURCES.get(active.sourceId());
            if (state != null) {
                pruneInvalidSource(active.sourceId(), state, "forced pitch source missing");
            }
            FORCED_PITCHES.remove(active.sourceId());
            return false;
        }
        float observedPitch = safeGetPitch(active.sourceId(), active.category(), active.sound(), "reapply forced pitch observed");
        boolean drift = Math.abs(observedPitch - active.finalPitch()) > PITCH_UPDATE_EPSILON;
        active.lastObservedPitch = observedPitch;
        active.lastDrift = drift;
        if (!drift) {
            return true;
        }
        if (safeSetPitch(active.sourceId(), active.finalPitch(), active.category(), active.sound(), "reapply forced pitch")) {
            active.reapplied++;
            active.lastObservedPitch = safeGetPitch(active.sourceId(), active.category(), active.sound(), "reapply forced pitch verify");
            DopplerSourceState state = SOURCES.get(active.sourceId());
            if (state != null) {
                state.markPitchApplied(active.finalPitch(), active.multiplier(), gameTime == Long.MIN_VALUE ? 0L : gameTime, DopplerSourceState.PitchControlSource.FORCED_COMMAND);
            }
            return true;
        }
        return false;
    }

    @Nullable
    private static DopplerSourceState latestState(boolean propellerOnly) {
        return SOURCES.values().stream()
                .filter(state -> !propellerOnly || isPropeller(state))
                .max(Comparator.comparingLong(DopplerSourceState::lastUpdateNanos))
                .orElse(null);
    }

    private static boolean forcedPitchActiveFor(int sourceId) {
        return activeForcedPitchFor(sourceId) != null;
    }

    @Nullable
    private static ForcedPitch activeForcedPitchFor(int sourceId) {
        ForcedPitch active = FORCED_PITCHES.get(sourceId);
        if (active == null) {
            return null;
        }
        if (System.nanoTime() >= active.endNanos()) {
            FORCED_PITCHES.remove(sourceId);
            latestForcedPitchMessage = restoreForcedPitch(active, "expired");
            return null;
        }
        return active;
    }

    private static void pruneInvalidSource(int sourceId, DopplerSourceState state, String reason) {
        SOURCES.remove(sourceId);
        FORCED_PITCHES.remove(sourceId);
        AudioSourceRecovery.recordStaleSourcesPruned(1, reason);
        AudioSourceRecovery.recordInvalidSource(sourceId, state.category(), state.sound(), reason);
        RecordDiagnostics.markSourceInvalidated(sourceId, "source invalidated after volume/audio change; waiting for new source");
    }

    private static int clampSeconds(int requestedSeconds) {
        return Math.max(1, Math.min(requestedSeconds, 60));
    }

    private static double clampMultiplier(double requestedMultiplier) {
        return Math.max(0.05D, Math.min(requestedMultiplier, 4.0D));
    }

    private static boolean isDopplerRuntimeEnabled(SoundPhysicsConfig config) {
        return DiagnosticRuntimeOverrides.dopplerEnabled(config);
    }

    private static void recordPolicySkip(SoundPhysicsSoundPolicy.DecisionReason reason, @Nullable ResourceLocation sound, @Nullable String soundInstanceClassName) {
        latestSkipReason = reason + " sound=" + sound + " class=" + shortClassName(soundInstanceClassName);
        switch (reason) {
            case DISABLED_CONFIG -> DIAGNOSTICS.recordSkippedByDisabledConfig();
            case RELATIVE,
                    NO_ATTENUATION,
                    PROPELLER_START_DEFERRED,
                    PROPELLER_SKIPPED_RELATIVE,
                    PROPELLER_SKIPPED_NO_ATTENUATION,
                    RECORD_SKIPPED_RELATIVE,
                    RECORD_SKIPPED_NO_ATTENUATION -> DIAGNOSTICS.recordSkippedByRelativeOrNoAttenuation();
            case SABLE_DELEGATE, PROPELLER_SKIPPED_SABLE_DELEGATE -> DIAGNOSTICS.recordSkippedBySableDelegate();
            case POSITIONAL_AMBIENT, AMBIENT_POLICY -> DIAGNOSTICS.recordSkippedByPositionalAmbientPolicy();
            case CATEGORY,
                    DENYLIST,
                    CROSSWIND_WIND,
                    RECORDS,
                    MUSIC,
                    MASTER,
                    VOICE,
                    PROPELLER_SAFE_MODE,
                    PROPELLER_SKIPPED_NO_SOURCE,
                    RECORD_SKIPPED_POLICY -> DIAGNOSTICS.recordSkippedByCategory();
            default -> {
            }
        }
    }

    private static String sourceDiagnosticsLine(DopplerSourceState state) {
        long ageTicks = currentGameTime() == Long.MIN_VALUE || state.lastDopplerUpdateGameTick() == Long.MIN_VALUE
                ? -1L
                : Math.max(0L, currentGameTime() - state.lastDopplerUpdateGameTick());
        SoundPhysicsSoundPolicy.SoundContext context = contextFor(state);
        boolean knownPropeller = SoundPhysicsSoundPolicy.isKnownPropeller(context);
        boolean sableDelegated = SoundPhysicsSoundPolicy.isSableDelegated(context);
        SublevelClassification sublevelClassification = sublevelClassification(state, sableDelegated);
        String isOnSableSublevel = switch (sublevelClassification) {
            case SUBLEVEL_CONFIRMED -> "true";
            case ROOT_STATIC -> "false";
            case SABLE_DELEGATED_UNRESOLVED, UNKNOWN -> "unknown";
        };
        boolean forcedPitchActive = forcedPitchActiveFor(state.sourceId());
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        boolean manualDopplerAllowed = config != null
                && (!sableDelegated || DiagnosticRuntimeOverrides.propellerDebugMode() || config.dopplerApplyToSableDelegatedSounds.get());
        boolean sableDelegateSkipWouldApplyInNormal = sableDelegated
                && config != null
                && !config.dopplerApplyToSableDelegatedSounds.get();
        String nearOneExplanation = state.reliableVelocity()
                && LISTENER_STATE.reliable()
                && Math.abs(state.multiplierDelta()) <= ACTIVE_MULTIPLIER_EPSILON
                ? " source/listener reliable but relative radial velocity is near zero; no pitch shift expected"
                : "none";
        return "source=" + state.sourceId()
                + " sound=" + state.sound()
                + " category=" + state.category()
                + " class=" + shortClassName(state.soundInstanceClassName())
                + " isKnownPropeller=" + knownPropeller
                + " isSableDelegated=" + sableDelegated
                + " sublevelClassification=" + sublevelClassification
                + " isOnSableSublevel=" + isOnSableSublevel
                + " ageTicks=" + (ageTicks < 0L ? "unknown" : ageTicks)
                + " sourceWorldPos=" + formatVec(state.lastKnownSourcePosition())
                + " sourceVel=" + formatVec(state.sampledSourceVelocity())
                + " sourceVelocitySource=" + state.velocitySource()
                + " sourceSamples=" + state.velocitySampleCount()
                + " sourceDtTicks=" + state.lastVelocityDtTicks()
                + " sourceRejectedReason=" + state.rejectedVelocityReason()
                + " listenerVel=" + formatVec(LISTENER_STATE.sampledVelocity())
                + " listenerVelocitySource=" + LISTENER_STATE.velocitySource()
                + " listenerSamples=" + LISTENER_STATE.sampleCount()
                + " listenerDtTicks=" + LISTENER_STATE.lastDtTicks()
                + " listenerRejectedReason=" + LISTENER_STATE.rejectedReason()
                + " sourceReliable=" + state.reliableVelocity()
                + " listenerReliable=" + LISTENER_STATE.reliable()
                + " acousticSpace=" + (state.acousticSpaceId() == null ? "root/unknown" : state.acousticSpaceId())
                + "@" + state.acousticSpaceVersion()
                + " radialVelocity=" + formatDouble(state.radialVelocity())
                + " relativeSpeedAlongLineOfSight=" + formatDouble(state.relativeSpeedAlongLineOfSight())
                + " multiplierDelta=" + formatSignedDouble(state.multiplierDelta())
                + " targetMultiplier=" + formatDouble(state.targetMultiplier())
                + " smoothedMultiplier=" + formatDouble(state.smoothedMultiplier())
                + " appliedPitch=" + (Float.isNaN(state.lastAppliedPitch()) ? "none" : formatDouble(state.lastAppliedPitch()))
                + " pitchDecision=" + state.latestPitchDecision()
                + " pitchControlSource=" + state.pitchControlSource()
                + " forcedPitchActive=" + forcedPitchActive
                + " manualPitchApplied=" + (state.hasAppliedDoppler() || forcedPitchActive)
                + " sableVelocityMayApply=" + sableDelegated
                + " manualDopplerAllowed=" + manualDopplerAllowed
                + " sableDelegateSkipWouldApplyInNormal=" + sableDelegateSkipWouldApplyInNormal
                + " dopplerExplanation=" + nearOneExplanation;
    }

    private static List<String> noTrackedPropellerSourcesLines() {
        List<String> lines = new ArrayList<>();
        lines.add("No tracked propeller Doppler sources.");
        if (DiagnosticRuntimeOverrides.propellerSafeMode()) {
            lines.add("Current mode=propeller_safe bypasses propeller Doppler tracking; this is expected.");
            lines.add("Use /spr_aero mode propeller_debug to track/process propellers.");
        } else {
            lines.add("Current mode=" + DiagnosticRuntimeOverrides.mode().commandName() + "; start a propeller or use /spr_aero mode propeller_debug for focused tracking.");
        }
        lines.add(SoundPhysicsPolicyDiagnostics.latestPropellerObservationText());
        lines.add("Latest Doppler skip: " + latestSkipReason);
        return lines;
    }

    private static String propellerSourcesSummary(List<DopplerSourceState> states) {
        long trackedPropellers = 0L;
        long staticRootPropellers = 0L;
        long sableDelegatedPropellers = 0L;
        String latestRootDecision = "none";
        String latestSableDecision = "none";
        long latestRootNanos = Long.MIN_VALUE;
        long latestSableNanos = Long.MIN_VALUE;
        for (DopplerSourceState state : states) {
            SoundPhysicsSoundPolicy.SoundContext context = contextFor(state);
            if (!SoundPhysicsSoundPolicy.isKnownPropeller(context)) {
                continue;
            }
            trackedPropellers++;
            boolean sableDelegated = SoundPhysicsSoundPolicy.isSableDelegated(context);
            SublevelClassification classification = sublevelClassification(state, sableDelegated);
            if (classification == SublevelClassification.SUBLEVEL_CONFIRMED
                    || classification == SublevelClassification.SABLE_DELEGATED_UNRESOLVED) {
                sableDelegatedPropellers++;
                if (state.lastUpdateNanos() > latestSableNanos) {
                    latestSableNanos = state.lastUpdateNanos();
                    latestSableDecision = state.latestPitchDecision().name();
                }
            } else {
                staticRootPropellers++;
                if (state.lastUpdateNanos() > latestRootNanos) {
                    latestRootNanos = state.lastUpdateNanos();
                    latestRootDecision = state.latestPitchDecision().name();
                }
            }
        }
        return "propeller summary: staticRootPropellers=" + staticRootPropellers
                + ", sableDelegatedPropellers=" + sableDelegatedPropellers
                + ", trackedPropellerSources=" + trackedPropellers
                + ", latestRootPropellerPitchDecision=" + latestRootDecision
                + ", latestSablePropellerPitchDecision=" + latestSableDecision;
    }

    private static boolean isPropeller(DopplerSourceState state) {
        return SoundPhysicsSoundPolicy.isKnownPropeller(contextFor(state));
    }

    private static SublevelClassification sublevelClassification(DopplerSourceState state, boolean sableDelegated) {
        String acousticSpaceId = state.acousticSpaceId();
        if (acousticSpaceId != null && !acousticSpaceId.isBlank() && !"root".equalsIgnoreCase(acousticSpaceId)) {
            return SublevelClassification.SUBLEVEL_CONFIRMED;
        }
        if (sableDelegated) {
            return SublevelClassification.SABLE_DELEGATED_UNRESOLVED;
        }
        if (acousticSpaceId == null || acousticSpaceId.isBlank() || "root".equalsIgnoreCase(acousticSpaceId)) {
            return SublevelClassification.ROOT_STATIC;
        }
        return SublevelClassification.UNKNOWN;
    }

    private static boolean isRecord(DopplerSourceState state) {
        return SoundPhysicsSoundPolicy.isRecord(contextFor(state));
    }

    private static SoundPhysicsSoundPolicy.SoundContext contextFor(DopplerSourceState state) {
        return new SoundPhysicsSoundPolicy.SoundContext(
                state.sound(),
                state.category(),
                state.soundInstanceClassName(),
                false,
                false,
                false,
                false,
                false
        );
    }

    private static long currentGameTime() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
    }

    private static String shortClassName(@Nullable String className) {
        if (className == null || className.isBlank()) {
            return "unknown";
        }
        int index = className.lastIndexOf('.');
        return index < 0 ? className : className.substring(index + 1);
    }

    private static String formatVec(Vec3 vec) {
        return "(" + formatDouble(vec.x) + "," + formatDouble(vec.y) + "," + formatDouble(vec.z) + ")";
    }

    private static String formatDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static String formatSignedDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%+.3f", value);
    }

    private static RadialMotion radialMotion(DopplerKinematicState sourceKinematics, DopplerKinematicState listenerKinematics) {
        double relativeSpeedAlongLineOfSight = DopplerMath.relativeSpeedAlongLineOfSight(
                sourceKinematics.worldPosition(),
                sourceKinematics.worldVelocity(),
                listenerKinematics.worldPosition(),
                listenerKinematics.worldVelocity()
        );
        return new RadialMotion(-relativeSpeedAlongLineOfSight, relativeSpeedAlongLineOfSight);
    }

    public record SourceQuery(SourceFilter filter, int limit, @Nullable String soundContains) {

        public static SourceQuery all(int limit) {
            return new SourceQuery(SourceFilter.ALL, limit, null);
        }

        public static SourceQuery propeller(int limit) {
            return new SourceQuery(SourceFilter.PROPELLER, limit, null);
        }

        public static SourceQuery records(int limit) {
            return new SourceQuery(SourceFilter.RECORDS, limit, null);
        }

        public static SourceQuery latest(int limit) {
            return new SourceQuery(SourceFilter.LATEST, limit, null);
        }

        public static SourceQuery moving(int limit) {
            return new SourceQuery(SourceFilter.MOVING, limit, null);
        }

        public static SourceQuery unreliable(int limit) {
            return new SourceQuery(SourceFilter.UNRELIABLE, limit, null);
        }

        public static SourceQuery sound(String soundContains, int limit) {
            return new SourceQuery(SourceFilter.SOUND, limit, soundContains);
        }

        boolean matches(DopplerSourceState state) {
            return switch (filter) {
                case ALL, LATEST -> true;
                case PROPELLER -> isPropeller(state);
                case RECORDS -> isRecord(state);
                case MOVING -> state.sampledSourceVelocity().lengthSqr() > 1.0E-6D;
                case UNRELIABLE -> !state.reliableVelocity() || !LISTENER_STATE.reliable();
                case SOUND -> soundContains != null
                        && state.sound().toString().toLowerCase(Locale.ROOT).contains(soundContains.toLowerCase(Locale.ROOT));
            };
        }
    }

    public enum SourceFilter {
        ALL,
        PROPELLER,
        RECORDS,
        SOUND,
        LATEST,
        MOVING,
        UNRELIABLE
    }

    private enum SublevelClassification {
        SUBLEVEL_CONFIRMED,
        SABLE_DELEGATED_UNRESOLVED,
        ROOT_STATIC,
        UNKNOWN
    }

    private static float safeGetPitch(int sourceId, @Nullable SoundSource category, @Nullable ResourceLocation sound, String reason) {
        try {
            if (!pitchBackend.sourceExists(sourceId)) {
                AudioSourceRecovery.recordInvalidSource(sourceId, category, sound, reason);
                return 1.0F;
            }
            float pitch = pitchBackend.getPitch(sourceId);
            return Float.isFinite(pitch) && pitch > 0.0F ? pitch : 1.0F;
        } catch (Throwable throwable) {
            AudioSourceRecovery.recordInvalidSource(sourceId, category, sound, reason + ": " + throwable.getMessage());
            Loggers.logDoppler("Failed to read base pitch for OpenAL source {}: {}", sourceId, throwable.getMessage());
            return 1.0F;
        }
    }

    private static boolean safeSetPitch(int sourceId, float pitch, @Nullable SoundSource category, @Nullable ResourceLocation sound, String reason) {
        try {
            if (!pitchBackend.sourceExists(sourceId)) {
                AudioSourceRecovery.recordInvalidSource(sourceId, category, sound, reason);
                return false;
            }
            pitchBackend.setPitch(sourceId, pitch);
            return true;
        } catch (Throwable throwable) {
            AudioSourceRecovery.recordInvalidSource(sourceId, category, sound, reason + ": " + throwable.getMessage());
            Loggers.logDoppler("Failed to set Doppler pitch for OpenAL source {}: {}", sourceId, throwable.getMessage());
            return false;
        }
    }

    private static boolean safeSourceExists(int sourceId, @Nullable SoundSource category, @Nullable ResourceLocation sound, String reason) {
        try {
            boolean exists = pitchBackend.sourceExists(sourceId);
            if (!exists) {
                AudioSourceRecovery.recordInvalidSource(sourceId, category, sound, reason);
            }
            return exists;
        } catch (Throwable throwable) {
            AudioSourceRecovery.recordInvalidSource(sourceId, category, sound, reason + ": " + throwable.getMessage());
            return false;
        }
    }

    private static void pruneStaleSources(long gameTime) {
        Iterator<Map.Entry<Integer, DopplerSourceState>> iterator = SOURCES.entrySet().iterator();
        int pruned = 0;
        while (iterator.hasNext()) {
            Map.Entry<Integer, DopplerSourceState> entry = iterator.next();
            long lastUpdate = entry.getValue().lastDopplerUpdateGameTick();
            if (lastUpdate != Long.MIN_VALUE && gameTime - lastUpdate > STALE_SOURCE_TICKS) {
                iterator.remove();
                FORCED_PITCHES.remove(entry.getKey());
                pruned++;
            }
        }
        AudioSourceRecovery.recordStaleSourcesPruned(pruned, "Doppler stale source timeout");
    }

    private static void logSummaryIfNeeded(SoundPhysicsConfig config, long gameTime) {
        if (!config.dopplerDebugLogging.get()) {
            return;
        }
        if (lastDebugSummaryGameTime != Long.MIN_VALUE && gameTime - lastDebugSummaryGameTime < DEBUG_SUMMARY_INTERVAL_TICKS) {
            return;
        }

        lastDebugSummaryGameTime = gameTime;
        Loggers.logDoppler("Doppler summary: {}", diagnosticsSummary());
    }

    private static final class ForcedPitch {
        private final int sourceId;
        private final ResourceLocation sound;
        private final SoundSource category;
        private final float basePitch;
        private final float finalPitch;
        private final double multiplier;
        private final long endNanos;
        private long reapplied;
        private float lastObservedPitch;
        private boolean lastDrift;

        private ForcedPitch(
                int sourceId,
                ResourceLocation sound,
                SoundSource category,
                float basePitch,
                float finalPitch,
                double multiplier,
                long endNanos
        ) {
            this.sourceId = sourceId;
            this.sound = sound;
            this.category = category;
            this.basePitch = basePitch;
            this.finalPitch = finalPitch;
            this.multiplier = multiplier;
            this.endNanos = endNanos;
            this.lastObservedPitch = finalPitch;
        }

        int sourceId() {
            return sourceId;
        }

        ResourceLocation sound() {
            return sound;
        }

        SoundSource category() {
            return category;
        }

        float basePitch() {
            return basePitch;
        }

        float finalPitch() {
            return finalPitch;
        }

        double multiplier() {
            return multiplier;
        }

        long endNanos() {
            return endNanos;
        }

        long reapplied() {
            return reapplied;
        }
    }

    private record ForceResult(boolean success, String message) {
        static ForceResult success(String message) {
            return new ForceResult(true, message);
        }

        static ForceResult failure(String message) {
            return new ForceResult(false, message);
        }
    }

    private record RadialMotion(
            double radialVelocity,
            double relativeSpeedAlongLineOfSight
    ) {
    }

    interface PitchBackend {

        float getPitch(int sourceId);

        void setPitch(int sourceId, float pitch);

        boolean sourceExists(int sourceId);

    }

    private static final class OpenAlPitchBackend implements PitchBackend {

        @Override
        public float getPitch(int sourceId) {
            return AL10.alGetSourcef(sourceId, AL10.AL_PITCH);
        }

        @Override
        public void setPitch(int sourceId, float pitch) {
            AL10.alSourcef(sourceId, AL10.AL_PITCH, pitch);
            Loggers.logALError("Set Doppler source pitch");
        }

        @Override
        public boolean sourceExists(int sourceId) {
            return AL10.alIsSource(sourceId);
        }

    }

}
