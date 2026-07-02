package com.sonicether.soundphysics;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;
import com.sonicether.soundphysics.doppler.DopplerSoundPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

public final class SoundPhysicsSoundPolicy {

    public static final ResourceLocation AERONAUTICS_PROPELLER_LARGE = ResourceLocation.fromNamespaceAndPath("aeronautics", "block.propeller_bearing.large_loop");
    public static final ResourceLocation AERONAUTICS_PROPELLER_SMALL = ResourceLocation.fromNamespaceAndPath("aeronautics", "block.propeller_bearing.small_loop");
    public static final String AERONAUTICS_PROPELLER_CLASS = "dev.eriksonn.aeronautics.content.blocks.propeller.bearing.sound.PropellerBearingSoundInstance";
    private static final Pattern MATCHER_TOKEN_SPLIT = Pattern.compile("[,;\\n\\r]+");
    private static final int PROPELLER_CANDIDATE_CACHE_MAX_SIZE = 4096;
    private static final ConcurrentMap<PropellerCandidateKey, Boolean> PROPELLER_CANDIDATE_CACHE = new ConcurrentHashMap<>();

    private SoundPhysicsSoundPolicy() {
    }

    public static Decision evaluateAcoustic(SoundPhysicsConfig config, SoundContext context) {
        if (config == null || !DiagnosticRuntimeOverrides.soundPhysicsEnabled(config)) {
            Decision decision = Decision.skip(DecisionReason.DISABLED_CONFIG);
            SoundPhysicsPolicyDiagnostics.recordAcousticDecision(context, decision);
            return decision;
        }

        recordCandidate(context);

        Decision decision = evaluateAcousticInternal(config, context);
        SoundPhysicsPolicyDiagnostics.recordAcousticDecision(context, decision);
        RecordDiagnostics.recordAcousticDecision(context, decision);
        logPolicy("acoustic", context, decision, config);
        return decision;
    }

    public static Decision evaluateDoppler(SoundPhysicsConfig config, SoundContext context) {
        if (config == null || !DiagnosticRuntimeOverrides.dopplerEnabled(config)) {
            Decision decision = Decision.skip(DecisionReason.DISABLED_CONFIG);
            SoundPhysicsPolicyDiagnostics.recordDopplerDecision(context, decision);
            return decision;
        }

        recordCandidate(context);

        Decision decision = evaluateDopplerInternal(config, context);
        SoundPhysicsPolicyDiagnostics.recordDopplerDecision(context, decision);
        RecordDiagnostics.recordDopplerDecision(context, decision);
        logPolicy("doppler", context, decision, config);
        return decision;
    }

    public static boolean isPropellerCandidate(SoundContext context) {
        PropellerCandidateKey key = new PropellerCandidateKey(context.soundId(), context.soundInstanceClassName());
        Boolean cached = PROPELLER_CANDIDATE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        boolean candidate = isPropellerCandidateUncached(key);
        if (PROPELLER_CANDIDATE_CACHE.size() >= PROPELLER_CANDIDATE_CACHE_MAX_SIZE) {
            PROPELLER_CANDIDATE_CACHE.clear();
        }
        PROPELLER_CANDIDATE_CACHE.putIfAbsent(key, candidate);
        return candidate;
    }

    public static boolean isKnownPropeller(SoundContext context) {
        return isAeronauticsPropeller(context) || isCrosswindVehiclePropeller(context);
    }

    public static boolean isAeronauticsPropeller(SoundContext context) {
        return AERONAUTICS_PROPELLER_LARGE.equals(context.soundId())
                || AERONAUTICS_PROPELLER_SMALL.equals(context.soundId())
                || AERONAUTICS_PROPELLER_CLASS.equals(context.soundInstanceClassName());
    }

    public static boolean isCrosswindWindLoop(SoundContext context) {
        ResourceLocation soundId = context.soundId();
        if (soundId == null || !"crosswind".equals(soundId.getNamespace())) {
            return false;
        }

        String path = soundId.getPath();
        if ("weather.wind.howl".equals(path)) {
            return false;
        }
        return path.startsWith("weather.wind.")
                || path.startsWith("vehicle.wind.")
                || path.contains("wind_loop");
    }

    public static boolean isSableDelegated(SoundContext context) {
        return DopplerSoundPolicy.isSableDelegatedSound(context.soundInstanceClassName());
    }

    public static boolean isRecord(SoundContext context) {
        return context.category() == SoundSource.RECORDS;
    }

    public static boolean isContinuousLoop(SoundContext context) {
        return context.tickable()
                || isKnownPropeller(context)
                || context.category() == SoundSource.RECORDS
                || context.category() == SoundSource.MUSIC;
    }

    public static boolean isImpactBurstDedupeExempt(SoundPhysicsConfig config, SoundContext context) {
        if (isKnownPropeller(context)) {
            SoundPhysicsPolicyDiagnostics.recordPropellerExemptImpactDedupe();
            return true;
        }
        if (context.category() == SoundSource.RECORDS || context.category() == SoundSource.MUSIC) {
            return true;
        }
        return context.tickable() && !config.soundPhysicsImpactBurstDedupeApplyToTickableSounds.get();
    }

    public static boolean isStartThrottleExempt(SoundContext context) {
        if (isKnownPropeller(context)) {
            SoundPhysicsPolicyDiagnostics.recordPropellerExemptStartThrottle();
            return true;
        }
        return isContinuousLoop(context);
    }

    public static boolean isSoundRateLimitExempt(SoundContext context) {
        return isKnownPropeller(context) || isContinuousLoop(context);
    }

    public static boolean shouldUpdateRecordAcoustics(SoundPhysicsConfig config, SoundContext context) {
        if (config == null || !isRecord(context) || context.relative()) {
            return false;
        }
        if (context.noAttenuation() && !DiagnosticRuntimeOverrides.recordTestUnsafeMode()) {
            return false;
        }
        return DiagnosticRuntimeOverrides.recordTestMode()
                || DiagnosticRuntimeOverrides.recordTestUnsafeMode()
                || config.soundPhysicsApplyToRecords.get();
    }

    public static boolean shouldLeaveSourceUntouchedOnSkip(SoundContext context, DecisionReason reason) {
        if (!isKnownPropeller(context) && !isRecord(context) && !context.tickable()) {
            return false;
        }

        return switch (reason) {
            case RELATIVE,
                    NO_ATTENUATION,
                    CATEGORY,
                    DENYLIST,
                    AMBIENT_POLICY,
                    POSITIONAL_AMBIENT,
                    RECORDS,
                    MUSIC,
                    PROPELLER_ALLOWED,
                    PROPELLER_START_DEFERRED,
                    PROPELLER_SKIPPED_RELATIVE,
                    PROPELLER_SKIPPED_NO_SOURCE,
                    PROPELLER_SKIPPED_NO_ATTENUATION,
                    PROPELLER_SKIPPED_SABLE_DELEGATE,
                    PROPELLER_SAFE_MODE,
                    RECORD_SKIPPED_RELATIVE,
                    RECORD_SKIPPED_NO_ATTENUATION,
                    RECORD_SKIPPED_ZERO_POSITION,
                    RECORD_SKIPPED_POLICY,
                    SKIP_DISTANCE,
                    SKIP_WORLD_NOT_INITIALIZED,
                    SKIP_NULL_SCENE,
                    SKIP_RATE_LIMIT,
                    SKIP_THROTTLE,
                    SKIP_IMPACT_DEDUPE -> true;
            default -> false;
        };
    }

    public static boolean isAllowlisted(@Nullable String rawList, SoundContext context) {
        return SoundMatcher.compile(rawList).matches(context);
    }

    public static boolean isDenylisted(@Nullable String rawList, SoundContext context) {
        return SoundMatcher.compile(rawList).matches(context);
    }

    private static Decision evaluateAcousticInternal(SoundPhysicsConfig config, SoundContext context) {
        if (isKnownPropeller(context)) {
            return evaluateKnownPropellerAcoustic(config, context);
        }
        if (isRecord(context)) {
            return evaluateRecordAcoustic(config, context);
        }
        if (isCrosswindWindLoop(context) && !config.soundPhysicsApplyToCrosswindWind.get()) {
            return Decision.skip(DecisionReason.CROSSWIND_WIND);
        }
        if (context.relative()) {
            return Decision.skip(DecisionReason.RELATIVE);
        }
        if (context.noAttenuation()) {
            return Decision.skip(DecisionReason.NO_ATTENUATION);
        }
        if (config.soundPhysicsSoundDenyMatcher().matches(context)) {
            return Decision.skip(DecisionReason.DENYLIST);
        }
        if (config.soundPhysicsSoundAllowMatcher().matches(context)) {
            return Decision.apply(DecisionReason.ALLOWLIST);
        }

        SoundSource category = context.category();
        if (category == null) {
            return Decision.skip(DecisionReason.CATEGORY);
        }

        return switch (category) {
            case BLOCKS, HOSTILE, NEUTRAL, PLAYERS -> Decision.apply(DecisionReason.CATEGORY);
            case WEATHER -> config.soundPhysicsApplyToWeatherSounds.get()
                    ? Decision.apply(DecisionReason.CATEGORY)
                    : Decision.skip(DecisionReason.CATEGORY);
            case AMBIENT -> evaluateAmbientAcoustic(config, context);
            case RECORDS -> DiagnosticRuntimeOverrides.recordTestMode() || config.soundPhysicsApplyToRecords.get()
                    ? Decision.apply(DecisionReason.RECORDS)
                    : Decision.skip(DecisionReason.RECORDS);
            case MUSIC -> config.soundPhysicsApplyToMusic.get()
                    ? Decision.apply(DecisionReason.MUSIC)
                    : Decision.skip(DecisionReason.MUSIC);
            case VOICE -> Decision.skip(DecisionReason.VOICE);
            case MASTER -> Decision.skip(DecisionReason.MASTER);
        };
    }

    private static Decision evaluateDopplerInternal(SoundPhysicsConfig config, SoundContext context) {
        if (isKnownPropeller(context)) {
            return evaluateKnownPropellerDoppler(config, context);
        }
        if (isRecord(context)) {
            return evaluateRecordDoppler(config, context);
        }
        if (isCrosswindWindLoop(context) && !config.dopplerApplyToCrosswindWind.get()) {
            return Decision.skip(DecisionReason.CROSSWIND_WIND);
        }
        if (context.relative()) {
            return Decision.skip(DecisionReason.RELATIVE);
        }
        if (context.noAttenuation()) {
            return Decision.skip(DecisionReason.NO_ATTENUATION);
        }
        if (isSableDelegated(context) && !config.dopplerApplyToSableDelegatedSounds.get() && !DiagnosticRuntimeOverrides.dopplerDebugMode()) {
            return Decision.skip(DecisionReason.SABLE_DELEGATE);
        }
        if (config.dopplerSoundDenyMatcher().matches(context)) {
            return Decision.skip(DecisionReason.DENYLIST);
        }
        if (config.dopplerSoundAllowMatcher().matches(context)) {
            return Decision.apply(DecisionReason.ALLOWLIST);
        }
        SoundSource category = context.category();
        if (category == null) {
            return Decision.skip(DecisionReason.CATEGORY);
        }

        return switch (category) {
            case BLOCKS -> config.dopplerApplyToBlockSounds.get()
                    ? Decision.apply(DecisionReason.CATEGORY)
                    : Decision.skip(DecisionReason.CATEGORY);
            case HOSTILE, NEUTRAL, PLAYERS -> config.dopplerApplyToEntitySounds.get()
                    ? Decision.apply(DecisionReason.CATEGORY)
                    : Decision.skip(DecisionReason.CATEGORY);
            case WEATHER -> config.dopplerApplyToWeatherSounds.get()
                    ? Decision.apply(DecisionReason.CATEGORY)
                    : Decision.skip(DecisionReason.CATEGORY);
            case AMBIENT -> evaluateAmbientDoppler(config, context);
            case RECORDS -> DiagnosticRuntimeOverrides.recordTestMode() || config.dopplerApplyToRecords.get()
                    ? Decision.apply(DecisionReason.RECORDS)
                    : Decision.skip(DecisionReason.RECORDS);
            case MUSIC -> config.dopplerApplyToMusic.get()
                    ? Decision.apply(DecisionReason.MUSIC)
                    : Decision.skip(DecisionReason.MUSIC);
            case VOICE -> Decision.skip(DecisionReason.VOICE);
            case MASTER -> Decision.skip(DecisionReason.MASTER);
        };
    }

    private static Decision evaluateKnownPropellerAcoustic(SoundPhysicsConfig config, SoundContext context) {
        if (context.soundId() == null) {
            return Decision.skip(DecisionReason.PROPELLER_SKIPPED_NO_SOURCE);
        }
        if (DiagnosticRuntimeOverrides.propellerSafeMode()) {
            return Decision.skip(DecisionReason.PROPELLER_SAFE_MODE);
        }
        if (context.relative()) {
            return Decision.skip(DecisionReason.PROPELLER_SKIPPED_RELATIVE);
        }
        if (context.noAttenuation()) {
            return Decision.skip(context.startEvent() ? DecisionReason.PROPELLER_START_DEFERRED : DecisionReason.PROPELLER_SKIPPED_NO_ATTENUATION);
        }
        if (config.soundPhysicsApplyToPositionalAmbientMachinery.get() || DiagnosticRuntimeOverrides.propellerDebugMode()) {
            return Decision.apply(DecisionReason.PROPELLER_ALLOWED);
        }
        return Decision.skip(DecisionReason.CATEGORY);
    }

    private static Decision evaluateKnownPropellerDoppler(SoundPhysicsConfig config, SoundContext context) {
        if (context.soundId() == null) {
            return Decision.skip(DecisionReason.PROPELLER_SKIPPED_NO_SOURCE);
        }
        if (DiagnosticRuntimeOverrides.propellerSafeMode()) {
            return Decision.skip(DecisionReason.PROPELLER_SAFE_MODE);
        }
        if (context.relative()) {
            return Decision.skip(DecisionReason.PROPELLER_SKIPPED_RELATIVE);
        }
        if (context.noAttenuation()) {
            return Decision.skip(context.startEvent() ? DecisionReason.PROPELLER_START_DEFERRED : DecisionReason.PROPELLER_SKIPPED_NO_ATTENUATION);
        }
        boolean aeronauticsPropellerAllowed =
                isAeronauticsPropeller(context) && config.dopplerApplyToAeronauticsPropellers.get();
        if (isSableDelegated(context)
                && !aeronauticsPropellerAllowed
                && !DiagnosticRuntimeOverrides.propellerDebugMode()
                && !config.dopplerApplyToSableDelegatedSounds.get()) {
            return Decision.skip(DecisionReason.PROPELLER_SKIPPED_SABLE_DELEGATE);
        }
        if (aeronauticsPropellerAllowed
                || config.dopplerApplyToPositionalAmbientMachinery.get()
                || DiagnosticRuntimeOverrides.propellerDebugMode()) {
            return Decision.apply(DecisionReason.PROPELLER_ALLOWED);
        }
        return Decision.skip(DecisionReason.CATEGORY);
    }

    private static Decision evaluateRecordAcoustic(SoundPhysicsConfig config, SoundContext context) {
        if (context.relative()) {
            return Decision.skip(DecisionReason.RECORD_SKIPPED_RELATIVE);
        }
        if (context.noAttenuation()) {
            return DiagnosticRuntimeOverrides.recordTestUnsafeMode()
                    ? Decision.apply(DecisionReason.RECORD_ALLOWED_UNSAFE)
                    : Decision.skip(DecisionReason.RECORD_SKIPPED_NO_ATTENUATION);
        }
        if (DiagnosticRuntimeOverrides.recordTestMode()) {
            return Decision.apply(DecisionReason.RECORD_ALLOWED_BY_TEST_MODE);
        }
        return config.soundPhysicsApplyToRecords.get()
                ? Decision.apply(DecisionReason.RECORD_ALLOWED_BY_CONFIG)
                : Decision.skip(DecisionReason.RECORD_SKIPPED_POLICY);
    }

    private static Decision evaluateRecordDoppler(SoundPhysicsConfig config, SoundContext context) {
        if (context.relative()) {
            return Decision.skip(DecisionReason.RECORD_SKIPPED_RELATIVE);
        }
        if (context.noAttenuation()) {
            return DiagnosticRuntimeOverrides.recordTestUnsafeMode()
                    ? Decision.apply(DecisionReason.RECORD_ALLOWED_UNSAFE)
                    : Decision.skip(DecisionReason.RECORD_SKIPPED_NO_ATTENUATION);
        }
        if (DiagnosticRuntimeOverrides.recordTestMode()) {
            return Decision.apply(DecisionReason.RECORD_ALLOWED_BY_TEST_MODE);
        }
        return config.dopplerApplyToRecords.get()
                ? Decision.apply(DecisionReason.RECORD_ALLOWED_BY_CONFIG)
                : Decision.skip(DecisionReason.RECORD_SKIPPED_POLICY);
    }

    private static Decision evaluateAmbientAcoustic(SoundPhysicsConfig config, SoundContext context) {
        if (isKnownPropeller(context) && config.soundPhysicsApplyToPositionalAmbientMachinery.get()) {
            return Decision.apply(DecisionReason.AMBIENT_MACHINERY);
        }
        if (SoundPhysics.isAmbientSound(context.soundId()) && !config.evaluateAmbientSounds.get()) {
            return Decision.skip(DecisionReason.AMBIENT_POLICY);
        }
        return config.soundPhysicsApplyToPositionalAmbientSounds.get()
                ? Decision.apply(DecisionReason.POSITIONAL_AMBIENT)
                : Decision.skip(DecisionReason.POSITIONAL_AMBIENT);
    }

    private static Decision evaluateAmbientDoppler(SoundPhysicsConfig config, SoundContext context) {
        if (!config.dopplerApplyToAmbientSounds.get() && !DiagnosticRuntimeOverrides.dopplerDebugMode()) {
            return Decision.skip(DecisionReason.CATEGORY);
        }
        return config.dopplerApplyToPositionalAmbientSounds.get() || DiagnosticRuntimeOverrides.dopplerDebugMode()
                ? Decision.apply(DecisionReason.POSITIONAL_AMBIENT)
                : Decision.skip(DecisionReason.POSITIONAL_AMBIENT);
    }

    public static boolean isCrosswindVehiclePropeller(SoundContext context) {
        ResourceLocation soundId = context.soundId();
        return soundId != null && "crosswind".equals(soundId.getNamespace()) && soundId.getPath().startsWith("vehicle.propeller.");
    }

    private static SoundMatcher parseMatcher(@Nullable String rawList) {
        if (rawList == null || rawList.isBlank()) {
            return SoundMatcher.empty();
        }

        Set<ResourceLocation> exactSounds = new HashSet<>();
        Set<String> exactClasses = new HashSet<>();
        List<String> soundContains = new ArrayList<>();
        List<String> classContains = new ArrayList<>();
        for (String token : MATCHER_TOKEN_SPLIT.split(rawList)) {
            String normalized = lower(token.trim());
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.startsWith("class:")) {
                classContains.add(normalized.substring("class:".length()));
            } else if (normalized.startsWith("sound:")) {
                soundContains.add(normalized.substring("sound:".length()));
            } else {
                ResourceLocation soundId = parseExactSound(normalized);
                if (soundId != null) {
                    exactSounds.add(soundId);
                } else {
                    exactClasses.add(normalized);
                }
            }
        }
        return new SoundMatcher(exactSounds, exactClasses, soundContains, classContains);
    }

    @Nullable
    private static ResourceLocation parseExactSound(String normalized) {
        if (!normalized.contains(":")) {
            return null;
        }
        return ResourceLocation.tryParse(normalized);
    }

    private static boolean isPropellerCandidateUncached(PropellerCandidateKey key) {
        String sound = lower(key.soundId());
        String className = lower(key.soundInstanceClassName());
        return containsPropellerCandidateText(sound) || containsPropellerCandidateText(className);
    }

    private static boolean containsPropellerCandidateText(String text) {
        return text.contains("propeller")
                || text.contains("bearing")
                || text.contains("aeronautics")
                || text.contains("simulated")
                || text.contains("create_aeronautics");
    }

    private static void recordCandidate(SoundContext context) {
        if (isPropellerCandidate(context)) {
            SoundPhysicsPolicyDiagnostics.recordPropellerCandidate();
        }
    }

    private static void logPolicy(String type, SoundContext context, Decision decision, SoundPhysicsConfig config) {
        if (!config.soundPhysicsPolicyDebugLogging.get() && !DiagnosticRuntimeOverrides.traceLoggingEnabled(config)) {
            return;
        }
        if (!isPropellerCandidate(context) && !isCrosswindWindLoop(context) && decision.apply()) {
            return;
        }
        Loggers.logTrace(
                "Sound policy {} sound={} category={} class={} relative={} noAttenuation={} tickable={} apply={} reason={}",
                type,
                context.soundId(),
                context.category(),
                context.soundInstanceClassName(),
                context.relative(),
                context.noAttenuation(),
                context.tickable(),
                decision.apply(),
                decision.reason()
        );
    }

    private static String lower(@Nullable Object value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.ROOT);
    }

    private record PropellerCandidateKey(
            @Nullable ResourceLocation soundId,
            @Nullable String soundInstanceClassName
    ) {
    }

    public record SoundMatcher(
            Set<ResourceLocation> exactSounds,
            Set<String> exactClasses,
            List<String> soundContains,
            List<String> classContains
    ) {
        private static final SoundMatcher EMPTY = new SoundMatcher(Set.of(), Set.of(), List.of(), List.of());

        public SoundMatcher {
            exactSounds = Set.copyOf(exactSounds);
            exactClasses = Set.copyOf(exactClasses);
            soundContains = List.copyOf(soundContains);
            classContains = List.copyOf(classContains);
        }

        public static SoundMatcher empty() {
            return EMPTY;
        }

        public static SoundMatcher compile(@Nullable String rawList) {
            return parseMatcher(rawList);
        }

        public boolean matches(SoundContext context) {
            if (this == EMPTY) {
                return false;
            }

            ResourceLocation soundId = context.soundId();
            if (soundId != null && exactSounds.contains(soundId)) {
                return true;
            }

            String className = lower(context.soundInstanceClassName());
            if (exactClasses.contains(className)) {
                return true;
            }

            String sound = lower(soundId);
            for (String token : soundContains) {
                if (sound.contains(token)) {
                    return true;
                }
            }
            for (String token : classContains) {
                if (className.contains(token)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record SoundContext(
            @Nullable ResourceLocation soundId,
            @Nullable SoundSource category,
            @Nullable String soundInstanceClassName,
            boolean relative,
            boolean noAttenuation,
            boolean streaming,
            boolean startEvent,
            boolean tickable
    ) {
        public static SoundContext of(@Nullable ResourceLocation soundId, @Nullable SoundSource category) {
            return new SoundContext(soundId, category, null, false, false, false, true, false);
        }

        public SoundContext withStartEvent(boolean startEvent) {
            return new SoundContext(soundId, category, soundInstanceClassName, relative, noAttenuation, streaming, startEvent, tickable);
        }
    }

    public record Decision(boolean apply, DecisionReason reason) {
        public static Decision apply(DecisionReason reason) {
            return new Decision(true, reason);
        }

        public static Decision skip(DecisionReason reason) {
            return new Decision(false, reason);
        }
    }

    public enum DecisionReason {
        APPLY,
        DISABLED_CONFIG,
        RELATIVE,
        NO_ATTENUATION,
        CATEGORY,
        DENYLIST,
        ALLOWLIST,
        CROSSWIND_WIND,
        SABLE_DELEGATE,
        AMBIENT_POLICY,
        POSITIONAL_AMBIENT,
        AMBIENT_MACHINERY,
        RECORDS,
        MUSIC,
        VOICE,
        MASTER,
        PROPELLER_ALLOWED,
        PROPELLER_START_DEFERRED,
        PROPELLER_SKIPPED_RELATIVE,
        PROPELLER_SKIPPED_NO_SOURCE,
        PROPELLER_SKIPPED_NO_ATTENUATION,
        PROPELLER_SKIPPED_SABLE_DELEGATE,
        PROPELLER_SAFE_MODE,
        RECORD_ALLOWED_BY_CONFIG,
        RECORD_ALLOWED_BY_TEST_MODE,
        RECORD_ALLOWED_UNSAFE,
        RECORD_SKIPPED_RELATIVE,
        RECORD_SKIPPED_NO_ATTENUATION,
        RECORD_SKIPPED_ZERO_POSITION,
        RECORD_SKIPPED_POLICY,
        SKIP_RATE_LIMIT,
        SKIP_THROTTLE,
        SKIP_IMPACT_DEDUPE,
        SKIP_DISTANCE,
        SKIP_WORLD_NOT_INITIALIZED,
        SKIP_NULL_SCENE
    }

}
