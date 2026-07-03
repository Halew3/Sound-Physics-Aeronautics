package com.sonicether.soundphysics;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SoundPhysicsTrace {

    private static final AtomicLong sourceMixinPlayCalls = new AtomicLong();
    private static final AtomicLong sourceMixinPlayMissingPosition = new AtomicLong();
    private static final AtomicLong onPlaySoundCalls = new AtomicLong();
    private static final AtomicLong processSoundCalls = new AtomicLong();
    private static final AtomicLong processSoundDisabledSkips = new AtomicLong();
    private static final AtomicLong evaluateEnvironmentCalls = new AtomicLong();
    private static final AtomicLong evaluateEnvironmentSkips = new AtomicLong();
    private static final AtomicLong acousticSceneRoot = new AtomicLong();
    private static final AtomicLong acousticSceneSable = new AtomicLong();
    private static final AtomicLong acousticSceneNull = new AtomicLong();
    private static final AtomicLong acousticSceneForcedRoot = new AtomicLong();
    private static final AtomicLong acousticProviderFailures = new AtomicLong();
    private static final AtomicLong calculateOcclusionCalls = new AtomicLong();
    private static final AtomicLong runOcclusionCalls = new AtomicLong();
    private static final AtomicLong nonStrictZeroOutlierIgnored = new AtomicLong();
    private static final AtomicLong nonStrictZeroOutlierAccepted = new AtomicLong();
    private static final AtomicLong nonStrictSelectedDirect = new AtomicLong();
    private static final AtomicLong nonStrictSelectedMedianOrPositive = new AtomicLong();
    private static final AtomicLong rootRayHits = new AtomicLong();
    private static final AtomicLong rootRayMisses = new AtomicLong();
    private static final AtomicLong sableRayHits = new AtomicLong();
    private static final AtomicLong sableRayMisses = new AtomicLong();
    private static final AtomicLong sableRayFailures = new AtomicLong();
    private static final AtomicLong mixinConfigLoads = new AtomicLong();
    private static final AtomicLong mixinShouldApplyCalls = new AtomicLong();
    private static final AtomicLong mixinPreApplyCalls = new AtomicLong();
    private static final AtomicLong mixinPostApplyCalls = new AtomicLong();
    private static final AtomicLong soundEnginePlayHeadCalls = new AtomicLong();
    private static final AtomicLong soundEnginePlayTailCalls = new AtomicLong();
    private static final AtomicLong soundSystemCaptureLastSoundCalls = new AtomicLong();
    private static final AtomicLong channelPlayHeadCalls = new AtomicLong();
    private static final AtomicLong channelSetSelfPositionCalls = new AtomicLong();
    private static final AtomicLong sourceMixinProcessCalls = new AtomicLong();
    private static final AtomicLong soundEngineFallbackProcessCalls = new AtomicLong();
    private static final AtomicLong movingSoundUpdateProcessCalls = new AtomicLong();
    private static final AtomicLong duplicateProcessingSkips = new AtomicLong();
    private static final AtomicLong sourceMixinDuplicateSkips = new AtomicLong();
    private static final AtomicLong soundEngineFallbackDuplicateSkips = new AtomicLong();
    private static final AtomicLong movingSoundUpdateDuplicateSkips = new AtomicLong();

    private SoundPhysicsTrace() {
    }

    public static void recordMixinConfigLoaded(String mixinPackage) {
        mixinConfigLoads.incrementAndGet();
        Loggers.log("SPR Aeronautics mixin config loaded: {}", mixinPackage);
    }

    public static void recordMixinShouldApply(String mixinClassName, String targetClassName) {
        mixinShouldApplyCalls.incrementAndGet();
        Loggers.log("SPR Aeronautics mixin shouldApply: {} -> {}", mixinClassName, targetClassName);
    }

    public static void recordMixinPreApply(String mixinClassName, String targetClassName) {
        mixinPreApplyCalls.incrementAndGet();
        Loggers.log("SPR Aeronautics mixin preApply: {} -> {}", mixinClassName, targetClassName);
    }

    public static void recordMixinPostApply(String mixinClassName, String targetClassName) {
        mixinPostApplyCalls.incrementAndGet();
        Loggers.log("SPR Aeronautics mixin postApply: {} -> {}", mixinClassName, targetClassName);
    }

    public static void recordSoundEnginePlayHead(@Nullable ResourceLocation sound, @Nullable SoundSource category, String soundInstanceClassName) {
        soundEnginePlayHeadCalls.incrementAndGet();
        Loggers.logTrace("SoundEngine.play HEAD sound={} category={} instance={}", sound, category, soundInstanceClassName);
    }

    public static void recordSoundEnginePlayTail(@Nullable ResourceLocation sound, @Nullable SoundSource category, String soundInstanceClassName) {
        soundEnginePlayTailCalls.incrementAndGet();
        Loggers.logTrace("SoundEngine.play TAIL sound={} category={} instance={}", sound, category, soundInstanceClassName);
    }

    public static void recordSoundSystemCaptureLastSound(@Nullable ResourceLocation sound, @Nullable SoundSource category) {
        soundSystemCaptureLastSoundCalls.incrementAndGet();
        Loggers.logTrace("SoundSystemMixin captured last sound={} category={}", sound, category);
    }

    public static void recordChannelSetSelfPosition(int sourceId, Vec3 position) {
        channelSetSelfPositionCalls.incrementAndGet();
        Loggers.logTrace("Channel.setSelfPosition source={} pos={}", sourceId, position);
    }

    public static void recordChannelPlayHead(int sourceId, @Nullable Vec3 position, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
        channelPlayHeadCalls.incrementAndGet();
        Loggers.logTrace("Channel.play HEAD source={} pos={} category={} sound={}", sourceId, position, category, sound);
    }

    public static void recordProcessingPath(SoundProcessingDeduper.ProcessingPath path, int sourceId, @Nullable ResourceLocation sound) {
        switch (path) {
            case SOURCE_MIXIN -> sourceMixinProcessCalls.incrementAndGet();
            case SOUND_ENGINE_FALLBACK -> soundEngineFallbackProcessCalls.incrementAndGet();
            case MOVING_SOUND_UPDATE -> movingSoundUpdateProcessCalls.incrementAndGet();
        }
        Loggers.logTrace("Sound processing path={} source={} sound={}", path.diagnosticName(), sourceId, sound);
    }

    public static void recordDuplicateProcessingSkip(SoundProcessingDeduper.ProcessingPath path, int sourceId, @Nullable ResourceLocation sound) {
        duplicateProcessingSkips.incrementAndGet();
        switch (path) {
            case SOURCE_MIXIN -> sourceMixinDuplicateSkips.incrementAndGet();
            case SOUND_ENGINE_FALLBACK -> soundEngineFallbackDuplicateSkips.incrementAndGet();
            case MOVING_SOUND_UPDATE -> movingSoundUpdateDuplicateSkips.incrementAndGet();
        }
        Loggers.logTrace("Duplicate sound processing skipped path={} source={} sound={}", path.diagnosticName(), sourceId, sound);
    }

    public static void recordSourceMixinPlay(int sourceId, @Nullable Vec3 position, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
        sourceMixinPlayCalls.incrementAndGet();
        if (position == null) {
            sourceMixinPlayMissingPosition.incrementAndGet();
            Loggers.logTrace("SourceMixin.play called source={} position=null category={} sound={}", sourceId, category, sound);
            return;
        }

        Loggers.logTrace("SourceMixin.play called source={} pos={} category={} sound={}", sourceId, position, category, sound);
    }

    public static void recordOnPlaySound(int sourceId, double posX, double posY, double posZ, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
        onPlaySoundCalls.incrementAndGet();
        Loggers.logTrace("SoundPhysics.onPlaySound called source={} pos=({}, {}, {}) category={} sound={}", sourceId, posX, posY, posZ, category, sound);
    }

    public static void recordProcessSound(int sourceId, double posX, double posY, double posZ, @Nullable SoundSource category, @Nullable ResourceLocation sound, boolean auxOnly) {
        processSoundCalls.incrementAndGet();
        RuntimeLoggingController.recordSound(sound);
        Loggers.logTrace("SoundPhysics.processSound entered source={} pos=({}, {}, {}) category={} sound={} auxOnly={}", sourceId, posX, posY, posZ, category, sound, auxOnly);
    }

    public static void recordProcessSoundDisabled(int sourceId, @Nullable ResourceLocation sound) {
        processSoundDisabledSkips.incrementAndGet();
        Loggers.logTrace("SoundPhysics.processSound skipped disabled source={} sound={}", sourceId, sound);
    }

    public static void recordEvaluateEnvironment(int sourceId, Vec3 soundPos, @Nullable SoundSource category, @Nullable ResourceLocation sound, boolean auxOnly) {
        evaluateEnvironmentCalls.incrementAndGet();
        Loggers.logTrace("evaluateEnvironment entered source={} soundPos={} category={} sound={} auxOnly={}", sourceId, soundPos, category, sound, auxOnly);
    }

    public static void recordEvaluateEnvironmentSkip(int sourceId, @Nullable ResourceLocation sound, String reason) {
        evaluateEnvironmentSkips.incrementAndGet();
        Loggers.logTrace("evaluateEnvironment skipped source={} sound={} reason={}", sourceId, sound, reason);
    }

    public static void recordProviderFailure(Throwable throwable) {
        acousticProviderFailures.incrementAndGet();
        Loggers.logTrace("Acoustic provider failure: {}", throwable.getMessage());
    }

    public static void recordAcousticScene(@Nullable Object scene, boolean forcedRoot) {
        if (forcedRoot) {
            acousticSceneForcedRoot.incrementAndGet();
        }

        String type = sceneType(scene);
        switch (type) {
            case "root" -> acousticSceneRoot.incrementAndGet();
            case "sable" -> acousticSceneSable.incrementAndGet();
            case "null" -> acousticSceneNull.incrementAndGet();
            default -> {
            }
        }

        Loggers.logTrace("Acoustic scene: {}{}", type, forcedRoot ? " forcedRoot=true" : "");
    }

    public static void recordCalculateOcclusion(Vec3 soundPos, Vec3 playerPos, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
        calculateOcclusionCalls.incrementAndGet();
        Loggers.logTrace("calculateOcclusion called soundPos={} playerPos={} category={} sound={}", soundPos, playerPos, category, sound);
    }

    public static void recordRunOcclusion(Vec3 soundPos, Vec3 playerPos) {
        runOcclusionCalls.incrementAndGet();
        Loggers.logTrace("runOcclusion called soundPos={} playerPos={}", soundPos, playerPos);
    }

    public static void recordNonStrictZeroOutlierIgnored(int count) {
        if (count <= 0) {
            return;
        }
        nonStrictZeroOutlierIgnored.addAndGet(count);
    }

    public static void recordNonStrictZeroOutlierAccepted(int count) {
        if (count <= 0) {
            return;
        }
        nonStrictZeroOutlierAccepted.addAndGet(count);
    }

    public static void recordNonStrictSelectedDirect() {
        nonStrictSelectedDirect.incrementAndGet();
    }

    public static void recordNonStrictSelectedMedianOrPositive() {
        nonStrictSelectedMedianOrPositive.incrementAndGet();
    }

    public static void recordRootRay(HitResult.Type type, Vec3 from, Vec3 to, Vec3 hitLocation) {
        boolean hit = type == HitResult.Type.BLOCK;
        if (hit) {
            rootRayHits.incrementAndGet();
        } else {
            rootRayMisses.incrementAndGet();
        }
        Loggers.logTrace("Root ray {} from={} to={} result={}", hit ? "hit" : "miss", from, to, hitLocation);
    }

    public static void recordSableRay(HitResult.Type type, String acousticId, Vec3 from, Vec3 to, Vec3 hitLocation) {
        boolean hit = type == HitResult.Type.BLOCK;
        if (hit) {
            sableRayHits.incrementAndGet();
        } else {
            sableRayMisses.incrementAndGet();
        }
        Loggers.logTrace("Sable ray {} space={} from={} to={} result={}", hit ? "hit" : "miss", acousticId, from, to, hitLocation);
    }

    public static void recordSableRayFailure(String acousticId, Throwable throwable) {
        sableRayFailures.incrementAndGet();
        Loggers.logTrace("Sable ray failure space={} error={}", acousticId, throwable.getMessage());
    }

    public static String diagnosticsSummaryText() {
        return "mixin(pluginLoaded=" + mixinConfigLoads.get()
                + ", shouldApply=" + mixinShouldApplyCalls.get()
                + ", preApply=" + mixinPreApplyCalls.get()
                + ", postApply=" + mixinPostApplyCalls.get() + ")"
                + ", hooks(soundEnginePlayHead=" + soundEnginePlayHeadCalls.get()
                + ", soundEnginePlayTail=" + soundEnginePlayTailCalls.get()
                + ", soundSystemCaptureLastSound=" + soundSystemCaptureLastSoundCalls.get()
                + ", channelPlayHead=" + channelPlayHeadCalls.get()
                + ", channelSetSelfPosition=" + channelSetSelfPositionCalls.get() + ")"
                + ", sourceMixinPlay=" + sourceMixinPlayCalls.get()
                + ", sourceMixinPlayNoPos=" + sourceMixinPlayMissingPosition.get()
                + ", processPaths(sourceMixin=" + sourceMixinProcessCalls.get()
                + ", soundEngineFallback=" + soundEngineFallbackProcessCalls.get()
                + ", movingSoundUpdate=" + movingSoundUpdateProcessCalls.get()
                + ", duplicateSkips(total=" + duplicateProcessingSkips.get()
                + ", sourceMixin=" + sourceMixinDuplicateSkips.get()
                + ", soundEngineFallback=" + soundEngineFallbackDuplicateSkips.get()
                + ", movingSoundUpdate=" + movingSoundUpdateDuplicateSkips.get() + "))"
                + ", onPlaySound=" + onPlaySoundCalls.get()
                + ", processSound=" + processSoundCalls.get()
                + ", processSoundDisabledSkips=" + processSoundDisabledSkips.get()
                + ", evaluateEnvironment=" + evaluateEnvironmentCalls.get()
                + ", evaluateEnvironmentSkips=" + evaluateEnvironmentSkips.get()
                + ", scenes(root=" + acousticSceneRoot.get()
                + ", sable=" + acousticSceneSable.get()
                + ", null=" + acousticSceneNull.get()
                + ", forcedRoot=" + acousticSceneForcedRoot.get()
                + ", providerFailures=" + acousticProviderFailures.get() + ")"
                + ", calculateOcclusion=" + calculateOcclusionCalls.get()
                + ", runOcclusion=" + runOcclusionCalls.get()
                + ", nonStrictOcclusion(nonStrictZeroOutlierIgnored=" + nonStrictZeroOutlierIgnored.get()
                + ", nonStrictZeroOutlierAccepted=" + nonStrictZeroOutlierAccepted.get()
                + ", nonStrictSelectedDirect=" + nonStrictSelectedDirect.get()
                + ", nonStrictSelectedMedianOrPositive=" + nonStrictSelectedMedianOrPositive.get() + ")"
                + ", rootRays(hit=" + rootRayHits.get() + ", miss=" + rootRayMisses.get() + ")"
                + ", sableRays(hit=" + sableRayHits.get() + ", miss=" + sableRayMisses.get() + ", failures=" + sableRayFailures.get() + ")";
    }

    public static void reset() {
        sourceMixinPlayCalls.set(0L);
        sourceMixinPlayMissingPosition.set(0L);
        onPlaySoundCalls.set(0L);
        processSoundCalls.set(0L);
        processSoundDisabledSkips.set(0L);
        evaluateEnvironmentCalls.set(0L);
        evaluateEnvironmentSkips.set(0L);
        acousticSceneRoot.set(0L);
        acousticSceneSable.set(0L);
        acousticSceneNull.set(0L);
        acousticSceneForcedRoot.set(0L);
        acousticProviderFailures.set(0L);
        calculateOcclusionCalls.set(0L);
        runOcclusionCalls.set(0L);
        nonStrictZeroOutlierIgnored.set(0L);
        nonStrictZeroOutlierAccepted.set(0L);
        nonStrictSelectedDirect.set(0L);
        nonStrictSelectedMedianOrPositive.set(0L);
        rootRayHits.set(0L);
        rootRayMisses.set(0L);
        sableRayHits.set(0L);
        sableRayMisses.set(0L);
        sableRayFailures.set(0L);
        soundEnginePlayHeadCalls.set(0L);
        soundEnginePlayTailCalls.set(0L);
        soundSystemCaptureLastSoundCalls.set(0L);
        channelPlayHeadCalls.set(0L);
        channelSetSelfPositionCalls.set(0L);
        sourceMixinProcessCalls.set(0L);
        soundEngineFallbackProcessCalls.set(0L);
        movingSoundUpdateProcessCalls.set(0L);
        duplicateProcessingSkips.set(0L);
        sourceMixinDuplicateSkips.set(0L);
        soundEngineFallbackDuplicateSkips.set(0L);
        movingSoundUpdateDuplicateSkips.set(0L);
    }

    private static String sceneType(@Nullable Object scene) {
        if (scene == null) {
            return "null";
        }
        String className = scene.getClass().getName();
        if (className.endsWith(".RootAcousticScene")) {
            return "root";
        }
        if (className.contains(".integration.sable.") || className.endsWith(".SableAcousticScene")) {
            return "sable";
        }
        return scene.getClass().getSimpleName();
    }

}
