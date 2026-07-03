package com.sonicether.soundphysics;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.acoustic.AcousticBlockRef;
import com.sonicether.soundphysics.acoustic.AcousticRayHit;
import com.sonicether.soundphysics.acoustic.AcousticScene;
import com.sonicether.soundphysics.acoustic.AcousticSceneContext;
import com.sonicether.soundphysics.acoustic.AcousticScenes;
import com.sonicether.soundphysics.utils.SoundRateManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import com.sonicether.soundphysics.config.ReverbParams;
import com.sonicether.soundphysics.debug.RaycastRenderer;
import com.sonicether.soundphysics.integration.dh.DistantHorizonsAudioBridge;
import com.sonicether.soundphysics.integration.dh.DistantTerrainOcclusionResult;
import com.sonicether.soundphysics.propeller.PropellerFarFieldEffect;
import com.sonicether.soundphysics.propeller.PropellerLongRangeAudio;
import com.sonicether.soundphysics.profiling.TaskProfiler;
import com.sonicether.soundphysics.profiling.TaskProfiler.TaskProfilerHandle;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;

public class SoundPhysics {

    private static final float PHI = 1.618033988F;
    private static final double OCCLUSION_EPSILON = 1.0E-4D;
    private static final double RAY_ORIGIN_ADVANCE_EPSILON = 1.0E-4D;
    private static final int MIN_OPEN_VARIATION_SAMPLES_TO_OVERRIDE_BLOCKED_DIRECT = 3;

    private static final Pattern AMBIENT_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.]+:ambient\\..*$");
    private static final Pattern BLOCK_PATTERN = Pattern.compile(".*block..*");

    private static int auxFXSlot0;
    private static int auxFXSlot1;
    private static int auxFXSlot2;
    private static int auxFXSlot3;
    private static int reverb0;
    private static int reverb1;
    private static int reverb2;
    private static int reverb3;
    private static int directFilter0;
    private static int sendFilter0;
    private static int sendFilter1;
    private static int sendFilter2;
    private static int sendFilter3;

    private static Minecraft minecraft;
    private static TaskProfiler profiler;

    private static SoundSource lastSoundCategory;
    private static ResourceLocation lastSound;
    private static String lastSoundInstanceClassName;
    private static boolean lastSoundRelative;
    private static boolean lastSoundNoAttenuation;
    private static boolean lastSoundTickable;
    private static int maxAuxSends;
    private static boolean efxInitialized;
    private static final AtomicLong efxInitCount = new AtomicLong();
    private static final AtomicLong efxDestroyCount = new AtomicLong();
    private static String efxLastInitReason = "none";
    private static String efxLastDestroyReason = "none";

    public static void init() {
        init("unspecified");
    }

    public static synchronized void init(String reason) {
        Loggers.log("Initializing Sound Physics");
        setupEFX(reason);
        Loggers.log("EFX ready");

        minecraft = Minecraft.getInstance();
        profiler = new TaskProfiler("Sound Physics");
    }

    public static void syncReverbParams() {
        if (auxFXSlot0 != 0) {
            //Set the global reverb parameters and apply them to the effect and effectslot
            setReverbParams(ReverbParams.getReverb0(), auxFXSlot0, reverb0);
            setReverbParams(ReverbParams.getReverb1(), auxFXSlot1, reverb1);
            setReverbParams(ReverbParams.getReverb2(), auxFXSlot2, reverb2);
            setReverbParams(ReverbParams.getReverb3(), auxFXSlot3, reverb3);
        }
    }

    static synchronized void setupEFX() {
        setupEFX("unspecified");
    }

    static synchronized void setupEFX(String reason) {
        if (efxInitialized || hasEfxResourceIds()) {
            destroyEFX("reinitializing before " + reason);
        }

        //Get current context and device
        long currentContext = ALC10.alcGetCurrentContext();
        if (currentContext == 0L) {
            Loggers.error("OpenAL context not available. Aborting EFX setup.");
            efxLastInitReason = reason + " (missing OpenAL context)";
            return;
        }
        long currentDevice = ALC10.alcGetContextsDevice(currentContext);

        if (ALC10.alcIsExtensionPresent(currentDevice, "ALC_EXT_EFX")) {
            Loggers.log("EFX Extension recognized");
        } else {
            Loggers.error("EFX Extension not found on current device. Aborting.");
            return;
        }

        maxAuxSends = ALC10.alcGetInteger(currentDevice, EXTEfx.ALC_MAX_AUXILIARY_SENDS);
        Loggers.log("Max auxiliary sends: {}", maxAuxSends);

        // Create auxiliary effect slots
        auxFXSlot0 = EXTEfx.alGenAuxiliaryEffectSlots();
        Loggers.log("Aux slot {} created", auxFXSlot0);
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot0, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE);

        auxFXSlot1 = EXTEfx.alGenAuxiliaryEffectSlots();
        Loggers.log("Aux slot {} created", auxFXSlot1);
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot1, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE);

        auxFXSlot2 = EXTEfx.alGenAuxiliaryEffectSlots();
        Loggers.log("Aux slot {} created", auxFXSlot2);
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot2, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE);

        auxFXSlot3 = EXTEfx.alGenAuxiliaryEffectSlots();
        Loggers.log("Aux slot {} created", auxFXSlot3);
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot3, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE);
        Loggers.logALErrorAlways("Failed creating auxiliary effect slots");

        reverb0 = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(reverb0, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        Loggers.logALErrorAlways("Failed creating reverb effect slot 0");
        reverb1 = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(reverb1, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        Loggers.logALErrorAlways("Failed creating reverb effect slot 1");
        reverb2 = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(reverb2, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        Loggers.logALErrorAlways("Failed creating reverb effect slot 2");
        reverb3 = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(reverb3, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        Loggers.logALErrorAlways("Failed creating reverb effect slot 3");

        directFilter0 = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(directFilter0, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        Loggers.logDebug("directFilter0: {}", directFilter0);

        sendFilter0 = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(sendFilter0, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        Loggers.logDebug("filter0: {}", sendFilter0);

        sendFilter1 = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(sendFilter1, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        Loggers.logDebug("filter1: {}", sendFilter1);

        sendFilter2 = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(sendFilter2, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        Loggers.logDebug("filter2: {}", sendFilter2);

        sendFilter3 = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(sendFilter3, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        Loggers.logDebug("filter3: {}", sendFilter3);
        Loggers.logALErrorAlways("Error creating lowpass filters");

        syncReverbParams();
        efxInitialized = true;
        efxInitCount.incrementAndGet();
        efxLastInitReason = reason;
    }

    public static synchronized void destroyEFX(String reason) {
        destroyEFX(reason, true);
    }

    private static synchronized void destroyEFX(String reason, boolean deleteOpenAlResources) {
        if (!efxInitialized && !hasEfxResourceIds()) {
            return;
        }

        if (deleteOpenAlResources) {
            deleteAuxiliaryEffectSlot(auxFXSlot0);
            deleteAuxiliaryEffectSlot(auxFXSlot1);
            deleteAuxiliaryEffectSlot(auxFXSlot2);
            deleteAuxiliaryEffectSlot(auxFXSlot3);
            deleteEffect(reverb0);
            deleteEffect(reverb1);
            deleteEffect(reverb2);
            deleteEffect(reverb3);
            deleteFilter(directFilter0);
            deleteFilter(sendFilter0);
            deleteFilter(sendFilter1);
            deleteFilter(sendFilter2);
            deleteFilter(sendFilter3);
        }

        auxFXSlot0 = 0;
        auxFXSlot1 = 0;
        auxFXSlot2 = 0;
        auxFXSlot3 = 0;
        reverb0 = 0;
        reverb1 = 0;
        reverb2 = 0;
        reverb3 = 0;
        directFilter0 = 0;
        sendFilter0 = 0;
        sendFilter1 = 0;
        sendFilter2 = 0;
        sendFilter3 = 0;
        maxAuxSends = 0;
        efxInitialized = false;
        efxDestroyCount.incrementAndGet();
        efxLastDestroyReason = reason;
        Loggers.log("Destroyed Sound Physics EFX resources: {}", reason);
    }

    public static String audioStatusText() {
        return "efxInitialized=" + efxInitialized
                + ", efxInitCount=" + efxInitCount.get()
                + ", efxDestroyCount=" + efxDestroyCount.get()
                + ", efxLastInitReason=" + efxLastInitReason
                + ", efxLastDestroyReason=" + efxLastDestroyReason
                + ", efxActiveAuxSlots=" + activeAuxSlotCount()
                + ", auxSlots=(" + auxFXSlot0 + "," + auxFXSlot1 + "," + auxFXSlot2 + "," + auxFXSlot3 + ")"
                + ", effects=(" + reverb0 + "," + reverb1 + "," + reverb2 + "," + reverb3 + ")"
                + ", filters=(direct=" + directFilter0 + ", sends=" + sendFilter0 + "," + sendFilter1 + "," + sendFilter2 + "," + sendFilter3 + ")"
                + ", maxAuxSends=" + maxAuxSends
                + ", " + AudioSourceRecovery.statusText();
    }

    public static synchronized boolean efxHealthy() {
        return efxInitialized && activeAuxSlotCount() == 4 && directFilter0 != 0 && sendFilter0 != 0 && sendFilter1 != 0 && sendFilter2 != 0 && sendFilter3 != 0;
    }

    public static synchronized void refreshEfxIfNeeded(String reason) {
        if (efxHealthy()) {
            return;
        }
        setupEFX(reason);
    }

    static synchronized void resetEfxLifecycleForTests() {
        auxFXSlot0 = 0;
        auxFXSlot1 = 0;
        auxFXSlot2 = 0;
        auxFXSlot3 = 0;
        reverb0 = 0;
        reverb1 = 0;
        reverb2 = 0;
        reverb3 = 0;
        directFilter0 = 0;
        sendFilter0 = 0;
        sendFilter1 = 0;
        sendFilter2 = 0;
        sendFilter3 = 0;
        maxAuxSends = 0;
        efxInitialized = false;
        efxInitCount.set(0L);
        efxDestroyCount.set(0L);
        efxLastInitReason = "none";
        efxLastDestroyReason = "none";
    }

    static synchronized void markEfxInitializedForTests(String reason) {
        auxFXSlot0 = 1;
        auxFXSlot1 = 2;
        auxFXSlot2 = 3;
        auxFXSlot3 = 4;
        reverb0 = 5;
        reverb1 = 6;
        reverb2 = 7;
        reverb3 = 8;
        directFilter0 = 9;
        sendFilter0 = 10;
        sendFilter1 = 11;
        sendFilter2 = 12;
        sendFilter3 = 13;
        maxAuxSends = 4;
        efxInitialized = true;
        efxInitCount.incrementAndGet();
        efxLastInitReason = reason;
    }

    static synchronized void destroyEfxForTests(String reason) {
        destroyEFX(reason, false);
    }

    private static boolean hasEfxResourceIds() {
        return auxFXSlot0 != 0
                || auxFXSlot1 != 0
                || auxFXSlot2 != 0
                || auxFXSlot3 != 0
                || reverb0 != 0
                || reverb1 != 0
                || reverb2 != 0
                || reverb3 != 0
                || directFilter0 != 0
                || sendFilter0 != 0
                || sendFilter1 != 0
                || sendFilter2 != 0
                || sendFilter3 != 0;
    }

    private static int activeAuxSlotCount() {
        int count = 0;
        count += auxFXSlot0 == 0 ? 0 : 1;
        count += auxFXSlot1 == 0 ? 0 : 1;
        count += auxFXSlot2 == 0 ? 0 : 1;
        count += auxFXSlot3 == 0 ? 0 : 1;
        return count;
    }

    private static void deleteAuxiliaryEffectSlot(int slot) {
        if (slot == 0) {
            return;
        }
        try {
            EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
            Loggers.logALErrorAlways("Delete auxiliary effect slot");
        } catch (Throwable throwable) {
            Loggers.warn("Failed deleting auxiliary effect slot {}: {}", slot, throwable.getMessage());
        }
    }

    private static void deleteEffect(int effect) {
        if (effect == 0) {
            return;
        }
        try {
            EXTEfx.alDeleteEffects(effect);
            Loggers.logALErrorAlways("Delete EFX effect");
        } catch (Throwable throwable) {
            Loggers.warn("Failed deleting EFX effect {}: {}", effect, throwable.getMessage());
        }
    }

    private static void deleteFilter(int filter) {
        if (filter == 0) {
            return;
        }
        try {
            EXTEfx.alDeleteFilters(filter);
            Loggers.logALErrorAlways("Delete EFX filter");
        } catch (Throwable throwable) {
            Loggers.warn("Failed deleting EFX filter {}: {}", filter, throwable.getMessage());
        }
    }

    public static void setLastSoundCategoryAndName(SoundSource sc, ResourceLocation id) {
        setLastSoundContext(sc, id, null, false, false, false);
    }

    public static void setLastSoundContext(SoundSource sc, ResourceLocation id, @Nullable String soundInstanceClassName, boolean relative, boolean noAttenuation) {
        setLastSoundContext(sc, id, soundInstanceClassName, relative, noAttenuation, false);
    }

    public static void setLastSoundContext(SoundSource sc, ResourceLocation id, @Nullable String soundInstanceClassName, boolean relative, boolean noAttenuation, boolean tickable) {
        lastSoundCategory = sc;
        lastSound = id;
        lastSoundInstanceClassName = soundInstanceClassName;
        lastSoundRelative = relative;
        lastSoundNoAttenuation = noAttenuation;
        lastSoundTickable = tickable;
    }

    public static SoundSource getLastSoundCategory() {
        return lastSoundCategory;
    }

    public static ResourceLocation getLastSound() {
        return lastSound;
    }

    public static SoundPhysicsSoundPolicy.SoundContext getLastSoundContext() {
        return new SoundPhysicsSoundPolicy.SoundContext(lastSound, lastSoundCategory, lastSoundInstanceClassName, lastSoundRelative, lastSoundNoAttenuation, false, true, lastSoundTickable);
    }

    /**
     * The old method signature of soundphysics to stay compatible
     */
    public static void onPlaySound(double posX, double posY, double posZ, int sourceID) {
        SoundPhysicsTrace.recordOnPlaySound(sourceID, posX, posY, posZ, lastSoundCategory, lastSound);
        processSound(sourceID, posX, posY, posZ, lastSoundCategory, lastSound, false);
    }

    /**
     * The old method signature of soundphysics to stay compatible
     */
    public static void onPlayReverb(double posX, double posY, double posZ, int sourceID) {
        processSound(sourceID, posX, posY, posZ, lastSoundCategory, lastSound, true);
    }

    /**
     * Processes the current sound
     *
     * @return The new sound origin or null if it didn't change
     */
    public static Vec3 processSound(int source, double posX, double posY, double posZ, SoundSource category, ResourceLocation sound) {
        return processSound(source, posX, posY, posZ, category, sound, false);
    }

    /**
     * Processes the current sound
     *
     * @return The new sound origin or null if it didn't change
     */
    @Nullable
    public static Vec3 processSound(int source, double posX, double posY, double posZ, SoundSource category, ResourceLocation sound, boolean auxOnly) {
        SoundPhysicsSoundPolicy.SoundContext context = lastSound != null && lastSound.equals(sound) && lastSoundCategory == category
                ? getLastSoundContext()
                : SoundPhysicsSoundPolicy.SoundContext.of(sound, category);
        return processSound(source, posX, posY, posZ, category, sound, auxOnly, context);
    }

    @Nullable
    public static Vec3 processSound(
            int source,
            double posX,
            double posY,
            double posZ,
            SoundSource category,
            ResourceLocation sound,
            boolean auxOnly,
            SoundPhysicsSoundPolicy.SoundContext context
    ) {
        long processStartNanos = System.nanoTime();
        SoundPhysicsTrace.recordProcessSound(source, posX, posY, posZ, category, sound, auxOnly);
        if (!DiagnosticRuntimeOverrides.soundPhysicsEnabled(SoundPhysicsMod.CONFIG)) {
            SoundPhysicsTrace.recordProcessSoundDisabled(source, sound);
            SoundPhysicsPerfDiagnostics.recordProcessSound(System.nanoTime() - processStartNanos);
            return null;
        }

        Loggers.logDebug("Playing sound with source id '{}', position x:{}, y:{}, z:{}, \tcategory: '{}' \tname: '{}'", source, posX, posY, posZ, category, sound);

        TaskProfilerHandle profile = profiler.profile();
        @Nullable Vec3 newPos = evaluateEnvironment(source, posX, posY, posZ, category, sound, auxOnly, context);
        profile.finish();
        SoundPhysicsPerfDiagnostics.recordProcessSound(System.nanoTime() - processStartNanos);

        Loggers.logProfiling("Evaluated environment for sound {} in {} ms", sound, profile.getDuration());
        profiler.onTally(() -> profiler.logResults());

        return newPos;
    }

    @Nullable
    private static Vec3 evaluateEnvironment(
            int sourceID,
            double posX,
            double posY,
            double posZ,
            SoundSource category,
            ResourceLocation sound,
            boolean auxOnly,
            SoundPhysicsSoundPolicy.SoundContext context
    ) {
        long evaluateStartNanos = System.nanoTime();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        Vec3 soundPos = new Vec3(posX, posY, posZ);
        Vec3 actualSoundPos = soundPos;
        SoundPhysicsTrace.recordEvaluateEnvironment(sourceID, soundPos, category, sound, auxOnly);
        SoundPhysicsPolicyDiagnostics.recordContextObserved(context);
        RecordDiagnostics.observeSource(sourceID, soundPos, context);

        if (AudioSourceRecovery.sourceUpdatesSuspended(category)) {
            AudioSourceRecovery.recordMutedUpdateSkipped(category, sound);
            SoundPhysicsPerfDiagnostics.recordEvaluateEnvironment(System.nanoTime() - evaluateStartNanos);
            return null;
        }

        if (!AudioSourceRecovery.safeSourceExists(sourceID, category, sound, "evaluate environment")) {
            RecordDiagnostics.markSourceInvalidated(sourceID, "source invalidated after volume/audio change; waiting for new source");
            SoundPhysicsPerfDiagnostics.recordEvaluateEnvironment(System.nanoTime() - evaluateStartNanos);
            return null;
        }

        if (player == null || level == null || (posX == 0D && posY == 0D && posZ == 0D)) {
            SoundPhysicsSoundPolicy.DecisionReason reason = SoundPhysicsSoundPolicy.isRecord(context) && (posX == 0D && posY == 0D && posZ == 0D)
                    ? SoundPhysicsSoundPolicy.DecisionReason.RECORD_SKIPPED_ZERO_POSITION
                    : SoundPhysicsSoundPolicy.DecisionReason.SKIP_WORLD_NOT_INITIALIZED;
            return skipEnvironment(sourceID, sound, "missing player/level or zero position", auxOnly, context, reason, evaluateStartNanos);
        }
        double distance = player.position().distanceTo(soundPos);
        double maxProcessingDistance = PropellerLongRangeAudio.effectiveProcessingDistance(
                sourceID,
                context,
                SoundPhysicsMod.CONFIG.maxSoundProcessingDistance.get()
        );
        if (SoundProcessingPolicy.isTooDistant(distance, maxProcessingDistance)) {
            Loggers.logDebug("Sound {} is too far away from player ({} blocks)", sound, distance);
            return skipEnvironment(sourceID, sound, "distance", auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_DISTANCE, evaluateStartNanos);
        }

        SoundPhysicsSoundPolicy.Decision policyDecision = SoundPhysicsSoundPolicy.evaluateAcoustic(SoundPhysicsMod.CONFIG, context);
        if (!policyDecision.apply()) {
            return skipEnvironment(sourceID, sound, "sound policy " + policyDecision.reason(), auxOnly, context, policyDecision.reason(), evaluateStartNanos);
        }

        if (!SoundRateManager.isWorldInitialized()) {
            Loggers.logDebug("Sound {} skipped because the world is not initialized yet", sound);
            return skipEnvironment(sourceID, sound, "world not initialized", auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_WORLD_NOT_INITIALIZED, evaluateStartNanos);
        }

        if (!SoundPhysicsSoundPolicy.isSoundRateLimitExempt(context) && SoundRateManager.incrementAndCheckLimit(sound)) {
            Loggers.logDebug("Sound {} skipped due to sound rate limit", sound);
            return skipEnvironment(sourceID, sound, "sound rate limit", auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_RATE_LIMIT, evaluateStartNanos);
        }

        long gameTime = level.getGameTime();
        if (context.startEvent()
                && !SoundPhysicsSoundPolicy.isStartThrottleExempt(context)
                && !SoundPhysicsPerfDiagnostics.recordSoundStart(gameTime, SoundPhysicsMod.CONFIG.soundPhysicsMaxSoundStartsPerTick.get())) {
            return skipEnvironment(sourceID, sound, SoundPhysicsPerfDiagnostics.soundStartThrottleReason(sound), auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_THROTTLE, evaluateStartNanos);
        }

        if (context.startEvent()
                && SoundPhysicsMod.CONFIG.soundPhysicsImpactBurstDedupeEnabled.get()
                && !SoundPhysicsSoundPolicy.isImpactBurstDedupeExempt(SoundPhysicsMod.CONFIG, context)
                && !SoundProcessingDeduper.shouldProcessImpactBurst(
                gameTime,
                category,
                sound,
                soundPos,
                SoundPhysicsMod.CONFIG.soundPhysicsImpactBurstDedupeRadius.get(),
                SoundPhysicsMod.CONFIG.soundPhysicsImpactBurstDedupeTicks.get()
        )) {
            return skipEnvironment(sourceID, sound, "impact burst dedupe", auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE, evaluateStartNanos);
        }

        float directCutoff;
        float absorptionCoeff = (float) (SoundPhysicsMod.CONFIG.blockAbsorption.get() * 3D);

        // Direct sound occlusion

        Vec3 playerPos = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 normalToPlayer = playerPos.subtract(soundPos).normalize();

        AcousticScene scene = AcousticScenes.createScene(minecraft, new AcousticSceneContext(sourceID, soundPos, playerPos, category, sound));
        if (scene == null) {
            return skipEnvironment(sourceID, sound, "null acoustic scene", auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_NULL_SCENE, evaluateStartNanos);
        }

        AcousticBlockRef soundBlock = scene.blockAt(soundPos);
        FluidState soundFluidState = soundBlock.fluidState();
        boolean sourceIsUnderwater = soundFluidState.is(FluidTags.WATER);

        Loggers.logDebug("Player pos: {}, {}, {} \tSound Pos: {}, {}, {} \tTo player vector: {}, {}, {}", playerPos.x, playerPos.y, playerPos.z, soundPos.x, soundPos.y, soundPos.z, normalToPlayer.x, normalToPlayer.y, normalToPlayer.z);

        RaycastRenderer.setCurrentSoundContext(context);
        double occlusionAccumulation = calculateOcclusion(scene, soundPos, playerPos, category, sound);

        directCutoff = (float) Math.exp(-occlusionAccumulation * absorptionCoeff);
        float directGain = auxOnly ? 0F : (float) Math.pow(directCutoff, 0.1D);

        Loggers.logOcclusion("Direct cutoff: {}, direct gain: {}", directCutoff, directGain);

        // Calculate reverb parameters

        float sendGain0 = 0F;
        float sendGain1 = 0F;
        float sendGain2 = 0F;
        float sendGain3 = 0F;

        float sendCutoff0 = 1F;
        float sendCutoff1 = 1F;
        float sendCutoff2 = 1F;
        float sendCutoff3 = 1F;

        if (minecraft.player.isUnderWater() || sourceIsUnderwater) {
            directCutoff *= 1F - SoundPhysicsMod.CONFIG.underwaterFilter.get();
        }

        // Shoot rays around sound

        float maxDistance = 256F;

        AdaptiveReflectionBudget.Budget reflectionBudget = AdaptiveReflectionBudget.resolve(SoundPhysicsMod.CONFIG, context, distance);
        SoundPhysicsPerfDiagnostics.recordAdaptiveReflectionBudget(reflectionBudget);
        Loggers.logEnvironment(
                "Reflection budget reason={} rays={}->{} bounces={}->{} distance={} sound={} category={} tickable={} startEvent={}",
                reflectionBudget.reason().diagnosticName(),
                reflectionBudget.configuredRays(),
                reflectionBudget.rays(),
                reflectionBudget.configuredBounces(),
                reflectionBudget.bounces(),
                distance,
                sound,
                category,
                context.tickable(),
                context.startEvent()
        );
        int numRays = reflectionBudget.rays();
        int rayBounces = reflectionBudget.bounces();
        boolean skipPropellerReverb = PropellerLongRangeAudio.shouldSkipReverb(distance, context);

        ReflectedAudio audioDirection = new ReflectedAudio(occlusionAccumulation, sound);

        float[] bounceReflectivityRatio = new float[rayBounces];

        float rcpTotalRays = 1F / (numRays * rayBounces);

        float gAngle = PHI * (float) Math.PI * 2F;

        if (!skipPropellerReverb) {
            Vec3 directSharedAirspaceVector = getSharedAirspace(scene, soundPos, playerPos);

            if (directSharedAirspaceVector != null) {
                audioDirection.addDirectAirspace(directSharedAirspaceVector);
            }

            for (int i = 0; i < numRays; i++) {
                float fiN = (float) i / numRays;
                float longitude = gAngle * (float) i * 1F;
                float latitude = (float) Math.asin(fiN * 2F - 1F);

                Vec3 rayDir = new Vec3(Math.cos(latitude) * Math.cos(longitude), Math.cos(latitude) * Math.sin(longitude), Math.sin(latitude));

                Vec3 rayEnd = new Vec3(soundPos.x + rayDir.x * maxDistance, soundPos.y + rayDir.y * maxDistance, soundPos.z + rayDir.z * maxDistance);

                AcousticRayHit rayHit = scene.rayCast(soundPos, rayEnd, soundBlock);

                if (rayHit.localHit().getType() == HitResult.Type.BLOCK) {
                    double rayLength = soundPos.distanceTo(rayHit.worldLocation());

                    // Additional bounces
                    AcousticBlockRef lastHitBlock = rayHit.blockRef();
                    Vec3 lastHitPos = rayHit.worldLocation();
                    Vec3 lastHitNormal = rayHit.worldNormal();
                    Vec3 lastRayDir = rayDir;

                    float totalRayDistance = (float) rayLength;

                    RaycastRenderer.addSoundBounceRay(soundPos, rayHit.worldLocation(), ChatFormatting.GREEN.getColor());

                    Vec3 firstSharedAirspaceVector = getSharedAirspace(scene, rayHit, playerPos);
                    if (firstSharedAirspaceVector != null) {
                        audioDirection.addSharedAirspace(firstSharedAirspaceVector, totalRayDistance);
                    }

                    // Secondary ray bounces
                    for (int j = 0; j < rayBounces; j++) {
                        Vec3 newRayDir = reflect(lastRayDir, lastHitNormal);
                        Vec3 newRayStart = lastHitPos;
                        Vec3 newRayEnd = new Vec3(newRayStart.x + newRayDir.x * maxDistance, newRayStart.y + newRayDir.y * maxDistance, newRayStart.z + newRayDir.z * maxDistance);

                        AcousticRayHit newRayHit = scene.rayCast(newRayStart, newRayEnd, lastHitBlock);

                        float blockReflectivity = getBlockReflectivity(lastHitBlock);
                        float energyTowardsPlayer = 0.25F * (blockReflectivity * 0.75F + 0.25F);

                        if (newRayHit.localHit().getType() == HitResult.Type.MISS) {
                            totalRayDistance += lastHitPos.distanceTo(playerPos);

                            RaycastRenderer.addSoundBounceRay(newRayStart, newRayEnd, ChatFormatting.RED.getColor());
                        } else {
                            Vec3 newRayHitPos = newRayHit.worldLocation();

                            RaycastRenderer.addSoundBounceRay(newRayStart, newRayHitPos, ChatFormatting.BLUE.getColor());

                            double newRayLength = lastHitPos.distanceTo(newRayHitPos);

                            bounceReflectivityRatio[j] += blockReflectivity;

                            totalRayDistance += newRayLength;

                            lastHitPos = newRayHitPos;
                            lastHitNormal = newRayHit.worldNormal();
                            lastRayDir = newRayDir;
                            lastHitBlock = newRayHit.blockRef();

                            Vec3 sharedAirspaceVector = getSharedAirspace(scene, newRayHit, playerPos);
                            if (sharedAirspaceVector != null) {
                                audioDirection.addSharedAirspace(sharedAirspaceVector, totalRayDistance);
                            }
                        }
                        // Bandaid solution for distance based attenuation
                        if (totalRayDistance < SoundPhysicsMod.CONFIG.reverbAttenuationDistance.get()) {
                            continue;
                        }

                        float reflectionDelay = (float) Math.max(totalRayDistance, 0D) * 0.12F * blockReflectivity;

                        float cross0 = 1F - Mth.clamp(Math.abs(reflectionDelay - 0F), 0F, 1F);
                        float cross1 = 1F - Mth.clamp(Math.abs(reflectionDelay - 1F), 0F, 1F);
                        float cross2 = 1F - Mth.clamp(Math.abs(reflectionDelay - 2F), 0F, 1F);
                        float cross3 = Mth.clamp(reflectionDelay - 2F, 0F, 1F);

                        sendGain0 += cross0 * energyTowardsPlayer * 6.4F * rcpTotalRays;
                        sendGain1 += cross1 * energyTowardsPlayer * 12.8F * rcpTotalRays;
                        sendGain2 += cross2 * energyTowardsPlayer * 12.8F * rcpTotalRays;
                        sendGain3 += cross3 * energyTowardsPlayer * 12.8F * rcpTotalRays;

                        // Nowhere to bounce off of, stop bouncing!
                        if (newRayHit.localHit().getType() == HitResult.Type.MISS) {
                            break;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < bounceReflectivityRatio.length; i++) {
            bounceReflectivityRatio[i] = bounceReflectivityRatio[i] / numRays;
            Loggers.logEnvironment("Bounce reflectivity {}: {}", i, bounceReflectivityRatio[i]);
        }

        @Nullable Vec3 newSoundPos = skipPropellerReverb ? null : audioDirection.evaluateSoundPosition(soundPos, playerPos);

        if (newSoundPos != null) {
            setSoundPos(sourceID, newSoundPos);
            soundPos = newSoundPos;
        }

        float sharedAirspace = audioDirection.getSharedAirspaces() * 64F * rcpTotalRays;

        Loggers.logEnvironment("Shared airspace: {} ({})", sharedAirspace, audioDirection.getSharedAirspaces());

        float sharedAirspaceWeight0 = Mth.clamp(sharedAirspace / 20F, 0F, 1F);
        float sharedAirspaceWeight1 = Mth.clamp(sharedAirspace / 15F, 0F, 1F);
        float sharedAirspaceWeight2 = Mth.clamp(sharedAirspace / 10F, 0F, 1F);
        float sharedAirspaceWeight3 = Mth.clamp(sharedAirspace / 10F, 0F, 1F);

        sendCutoff0 = (float) Math.exp(-occlusionAccumulation * absorptionCoeff * 1F) * (1F - sharedAirspaceWeight0) + sharedAirspaceWeight0;
        sendCutoff1 = (float) Math.exp(-occlusionAccumulation * absorptionCoeff * 1F) * (1F - sharedAirspaceWeight1) + sharedAirspaceWeight1;
        sendCutoff2 = (float) Math.exp(-occlusionAccumulation * absorptionCoeff * 1F) * (1F - sharedAirspaceWeight2) + sharedAirspaceWeight2;
        sendCutoff3 = (float) Math.exp(-occlusionAccumulation * absorptionCoeff * 1F) * (1F - sharedAirspaceWeight3) + sharedAirspaceWeight3;

        // Attempt to preserve directionality when airspace is shared by allowing some of the dry signal through but filtered
        float averageSharedAirspace = (sharedAirspaceWeight0 + sharedAirspaceWeight1 + sharedAirspaceWeight2 + sharedAirspaceWeight3) * 0.25F;
        directCutoff = Math.max((float) Math.pow(averageSharedAirspace, 0.5D) * 0.2F, directCutoff);
        directGain = auxOnly ? 0F : (float) Math.pow(directCutoff, 0.1D);

        sendGain1 *= bounceReflectivityRatio[1];

        if (bounceReflectivityRatio.length > 2) {
            sendGain2 *= (float) Math.pow(bounceReflectivityRatio[2], 3D);
        }
        if (bounceReflectivityRatio.length > 3) {
            sendGain3 *= (float) Math.pow(bounceReflectivityRatio[3], 4D);
        }

        sendGain0 = Mth.clamp(sendGain0, 0F, 1F);
        sendGain1 = Mth.clamp(sendGain1, 0F, 1F);
        sendGain2 = Mth.clamp(sendGain2 * 1.05F - 0.05F, 0F, 1F);
        sendGain3 = Mth.clamp(sendGain3 * 1.05F - 0.05F, 0F, 1F);

        sendGain0 *= (float) Math.pow(sendCutoff0, 0.1D);
        sendGain1 *= (float) Math.pow(sendCutoff1, 0.1D);
        sendGain2 *= (float) Math.pow(sendCutoff2, 0.1D);
        sendGain3 *= (float) Math.pow(sendCutoff3, 0.1D);

        // I don't know how else to fix reverb not being attenuated by distance
        // We should look into this
        float soundDistance = (float) playerPos.distanceTo(soundPos);
        float maxSoundDistance = AudioSourceRecovery.safeGetSourceFloat(sourceID, AL10.AL_MAX_DISTANCE, 16.0F, category, sound, "get max sound distance");
        float sendGainMultiplier = 1F - Math.min(soundDistance / (maxSoundDistance * SoundPhysicsMod.CONFIG.reverbDistance.get()), 1F);
        sendGain0 = sendGainMultiplier * sendGain0;
        sendGain1 = sendGainMultiplier * sendGain1;
        sendGain2 = sendGainMultiplier * sendGain2;
        sendGain3 = sendGainMultiplier * sendGain3;

        Loggers.logEnvironment("Final environment settings: {}, {}, {}, {}", sendGain0, sendGain1, sendGain2, sendGain3);

        assert minecraft.player != null;
        if (minecraft.player.isUnderWater() || sourceIsUnderwater) {
            sendCutoff0 *= 0.4F;
            sendCutoff1 *= 0.4F;
            sendCutoff2 *= 0.4F;
            sendCutoff3 *= 0.4F;
        }

        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)
                && (directCutoff < 0.99F || directGain < 0.99F || sendGain0 > 0.0F || sendGain1 > 0.0F || sendGain2 > 0.0F || sendGain3 > 0.0F)) {
            SoundPhysicsPolicyDiagnostics.recordPropellerMuffledOrFiltered();
        }
        float airAbsorption = SoundPhysicsMod.CONFIG.airAbsorption.get();
        if (PropellerLongRangeAudio.isEligible(context)) {
            PropellerFarFieldEffect effect = PropellerLongRangeAudio.computeFarField(sourceID, context, soundDistance, airAbsorption);
            double actualSoundDistance = playerPos.distanceTo(actualSoundPos);
            DistantTerrainOcclusionResult dh = DistantHorizonsAudioBridge.computeFarPropellerOcclusion(
                    sourceID,
                    level,
                    context,
                    playerPos,
                    actualSoundPos,
                    actualSoundDistance,
                    effect.distanceNorm()
            );
            directCutoff *= effect.directCutoffMultiplier();
            directGain *= effect.directGainMultiplier();
            directCutoff *= dh.directCutoffMultiplier();
            directGain *= dh.directGainMultiplier();
            airAbsorption = effect.airAbsorption();
        }
        if (SoundPhysicsSoundPolicy.isRecord(context)) {
            RecordDiagnostics.recordAcousticProcessed(sourceID, occlusionAccumulation, directCutoff, directGain, sendGain0, sendGain1, sendGain2, sendGain3);
        }
        setEnvironment(sourceID, sendGain0, sendGain1, sendGain2, sendGain3, sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3, directCutoff, directGain, airAbsorption);
        RaycastRenderer.clearCurrentSoundContext();
        SoundPhysicsPolicyDiagnostics.recordProcessedNormally(context);

        SoundPhysicsPerfDiagnostics.recordEvaluateEnvironment(System.nanoTime() - evaluateStartNanos);
        return newSoundPos;
    }

    @Nullable
    private static Vec3 skipEnvironment(
            int sourceID,
            @Nullable ResourceLocation sound,
            String traceReason,
            boolean auxOnly,
            SoundPhysicsSoundPolicy.SoundContext context,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason,
            long evaluateStartNanos
    ) {
        SoundPhysicsTrace.recordEvaluateEnvironmentSkip(sourceID, sound, traceReason);
        if (decisionReason == SoundPhysicsSoundPolicy.DecisionReason.RECORD_SKIPPED_ZERO_POSITION) {
            SoundPhysicsPolicyDiagnostics.recordAcousticDecision(SoundPhysicsSoundPolicy.Decision.skip(decisionReason));
        }

        if (isOverloadSkip(decisionReason)) {
            SoundPhysicsPolicyDiagnostics.recordEnvironmentUntouched(context);
            Loggers.logTrace("Skipped acoustic processing without resetting source={} sound={} reason={}", sourceID, sound, decisionReason);
            SoundPhysicsPerfDiagnostics.recordEvaluateEnvironment(System.nanoTime() - evaluateStartNanos);
            return null;
        }

        if (SoundPhysicsSoundPolicy.shouldLeaveSourceUntouchedOnSkip(context, decisionReason)) {
            SoundPhysicsPolicyDiagnostics.recordEnvironmentUntouched(context);
            Loggers.logTrace("Skipped acoustic processing without touching source={} sound={} reason={}", sourceID, sound, decisionReason);
        } else {
            SoundPhysicsPolicyDiagnostics.recordEnvironmentReset();
            setDefaultEnvironment(sourceID, auxOnly);
        }
        SoundPhysicsPerfDiagnostics.recordEvaluateEnvironment(System.nanoTime() - evaluateStartNanos);
        return null;
    }

    private static boolean isOverloadSkip(SoundPhysicsSoundPolicy.DecisionReason reason) {
        return reason == SoundPhysicsSoundPolicy.DecisionReason.SKIP_RATE_LIMIT
                || reason == SoundPhysicsSoundPolicy.DecisionReason.SKIP_THROTTLE
                || reason == SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE;
    }

    public static boolean isAmbientSound(@Nullable ResourceLocation sound) {
        return sound != null && AMBIENT_PATTERN.matcher(sound.toString()).matches();
    }

    private static float getBlockReflectivity(AcousticBlockRef blockRef) {
        BlockState blockState = blockRef.blockState();
        return SoundPhysicsMod.REFLECTIVITY_CONFIG.getBlockDefinitionValue(blockState);
    }

    private static Vec3 reflect(Vec3 dir, Vec3 normal) {
        //dir - 2.0 * dot(normal, dir) * normal
        double dot = dir.dot(normal) * 2D;

        double x = dir.x - dot * normal.x;
        double y = dir.y - dot * normal.y;
        double z = dir.z - dot * normal.z;

        return new Vec3(x, y, z);
    }

    private static double calculateOcclusion(AcousticScene scene, Vec3 soundPos, Vec3 playerPos, SoundSource category, ResourceLocation sound) {
        long startNanos = System.nanoTime();
        SoundPhysicsTrace.recordCalculateOcclusion(soundPos, playerPos, category, sound);
        try {
            if (SoundPhysicsMod.CONFIG.strictOcclusion.get()) {
                return Math.min(runOcclusion(scene, soundPos, playerPos), SoundPhysicsMod.CONFIG.maxOcclusion.get());
            }
            boolean isBlock = category == SoundSource.BLOCKS || (sound != null && BLOCK_PATTERN.matcher(sound.toString()).matches());
            double variationFactor = SoundPhysicsMod.CONFIG.occlusionVariation.get();

            if (isBlock) {
                variationFactor = Math.max(variationFactor, 0.49D);
            }

            double directOcclusion = runOcclusion(scene, soundPos, playerPos);

            if (directOcclusion <= OCCLUSION_EPSILON) {
                NonStrictOcclusionSelection selection = selectNonStrictOcclusion(directOcclusion, new double[0]);
                SoundPhysicsTrace.recordNonStrictSelectedDirect();
                logNonStrictOcclusionDecision(sound, category, directOcclusion, selection.selectedOcclusion(), new double[0], selection, variationFactor);
                return 0.0D;
            }

            double[] variationSamples = variationFactor > 0D ? new double[8] : new double[0];
            int sampleIndex = 0;
            if (variationFactor > 0D) {
                for (int x = -1; x <= 1; x += 2) {
                    for (int y = -1; y <= 1; y += 2) {
                        for (int z = -1; z <= 1; z += 2) {
                            Vec3 offset = new Vec3(x, y, z).scale(variationFactor);
                            variationSamples[sampleIndex] = runOcclusion(scene, soundPos.add(offset), playerPos);
                            sampleIndex++;
                        }
                    }
                }
            }

            NonStrictOcclusionSelection selection = selectNonStrictOcclusion(directOcclusion, variationSamples);
            recordNonStrictOcclusionSelection(selection);
            double selectedOcclusion = Math.min(selection.selectedOcclusion(), SoundPhysicsMod.CONFIG.maxOcclusion.get());
            logNonStrictOcclusionDecision(sound, category, directOcclusion, selectedOcclusion, variationSamples, selection, variationFactor);
            return selectedOcclusion;
        } finally {
            SoundPhysicsPerfDiagnostics.recordCalculateOcclusion(System.nanoTime() - startNanos);
        }
    }

    private static double runOcclusion(AcousticScene scene, Vec3 soundPos, Vec3 playerPos) {
        long startNanos = System.nanoTime();
        SoundPhysicsTrace.recordRunOcclusion(soundPos, playerPos);
        try {
            double occlusionAccumulation = 0D;
            Vec3 rayOrigin = soundPos;

            AcousticBlockRef sourceBlock = scene.blockAt(soundPos);
            AcousticBlockRef lastBlock = shouldIgnoreSourceBlock(sourceBlock) ? sourceBlock : null;

            for (int i = 0; i < SoundPhysicsMod.CONFIG.maxOcclusionRays.get(); i++) {
                AcousticRayHit rayHit = scene.rayCast(rayOrigin, playerPos, lastBlock);

                lastBlock = rayHit.blockRef();

                if (rayHit.localHit().getType() == HitResult.Type.MISS) {
                    RaycastRenderer.addOcclusionRay(rayOrigin, playerPos.add(0D, -0.1D, 0D), Mth.hsvToRgb(1F / 3F * (1F - Math.min(1F, (float) occlusionAccumulation / 12F)), 1F, 1F));
                    break;
                }

                RaycastRenderer.addOcclusionRay(rayOrigin, rayHit.worldLocation(), Mth.hsvToRgb(1F / 3F * (1F - Math.min(1F, (float) occlusionAccumulation / 12F)), 1F, 1F));

                AcousticBlockRef blockRef = rayHit.blockRef();
                Vec3 hitLocation = rayHit.worldLocation();
                boolean samePositionHit = rayOrigin.distanceToSqr(hitLocation) <= RAY_ORIGIN_ADVANCE_EPSILON * RAY_ORIGIN_ADVANCE_EPSILON;
                rayOrigin = rayHit.worldLocation();

                BlockState blockHit = blockRef.blockState();
                float blockOcclusion = SoundPhysicsMod.OCCLUSION_CONFIG.getBlockDefinitionValue(blockHit);

                // Regardless to whether we hit from inside or outside
                Vec3 localHitPos = rayHit.localHit().getLocation();
                Vec3 dirVec = localHitPos.subtract(blockRef.pos().getX() + 0.5D, blockRef.pos().getY() + 0.5D, blockRef.pos().getZ() + 0.5D);
                Direction sideHit = Direction.getNearest(dirVec.x, dirVec.y, dirVec.z);

                if (!blockRef.isFaceSturdy(sideHit)) {
                    blockOcclusion *= SoundPhysicsMod.CONFIG.nonFullBlockOcclusionFactor.get();
                }

                Loggers.logOcclusion("{} \t{},{},{}", blockHit.getBlock().getDescriptionId(), rayOrigin.x, rayOrigin.y, rayOrigin.z);

                // Accumulate density
                occlusionAccumulation += blockOcclusion;

                if (occlusionAccumulation > SoundPhysicsMod.CONFIG.maxOcclusion.get()) {
                    Loggers.logOcclusion("Max occlusion reached after {} steps", i + 1);
                    break;
                }

                if (samePositionHit) {
                    Vec3 toPlayer = playerPos.subtract(rayOrigin);
                    if (toPlayer.lengthSqr() > 1.0E-12D) {
                        rayOrigin = rayOrigin.add(toPlayer.normalize().scale(RAY_ORIGIN_ADVANCE_EPSILON));
                    }
                }
            }

            return occlusionAccumulation;
        } finally {
            SoundPhysicsPerfDiagnostics.recordRunOcclusion(System.nanoTime() - startNanos);
        }
    }

    static NonStrictOcclusionSelection selectNonStrictOcclusion(double directOcclusion, double[] variationSamples) {
        int zeroSamples = 0;
        int positiveVariationSamples = 0;
        double[] positiveSamples = new double[variationSamples.length + 1];

        if (directOcclusion <= OCCLUSION_EPSILON) {
            return new NonStrictOcclusionSelection(0.0D, 0, 0, "direct_open");
        }

        positiveSamples[0] = directOcclusion;
        int positiveSamplesIncludingDirect = 1;

        for (double sample : variationSamples) {
            if (sample <= OCCLUSION_EPSILON) {
                zeroSamples++;
                continue;
            }
            positiveSamples[positiveSamplesIncludingDirect] = sample;
            positiveSamplesIncludingDirect++;
            positiveVariationSamples++;
        }

        if (zeroSamples >= MIN_OPEN_VARIATION_SAMPLES_TO_OVERRIDE_BLOCKED_DIRECT) {
            return new NonStrictOcclusionSelection(0.0D, zeroSamples, positiveVariationSamples, "zero_override");
        }

        Arrays.sort(positiveSamples, 0, positiveSamplesIncludingDirect);
        double median = medianSorted(positiveSamples, positiveSamplesIncludingDirect);
        double selectedOcclusion = Math.min(directOcclusion, median);
        String decisionKind = selectedOcclusion == directOcclusion ? "direct" : "median_positive";
        return new NonStrictOcclusionSelection(selectedOcclusion, zeroSamples, positiveVariationSamples, decisionKind);
    }

    static boolean shouldIgnoreSourceBlock(AcousticBlockRef sourceBlock) {
        BlockState sourceBlockState = sourceBlock.blockState();
        if (sourceBlockState.isAir()) {
            return true;
        }
        boolean emptyCollision = sourceBlockState.getCollisionShape(sourceBlock.space(), sourceBlock.pos(), CollisionContext.empty()).isEmpty();
        FluidState sourceFluidState = sourceBlock.fluidState();
        if (!sourceFluidState.isEmpty() && emptyCollision) {
            return true;
        }
        if (emptyCollision) {
            return true;
        }
        if (SoundPhysicsMod.OCCLUSION_CONFIG == null) {
            return false;
        }
        return SoundPhysicsMod.OCCLUSION_CONFIG.getBlockDefinitionValue(sourceBlockState) <= OCCLUSION_EPSILON;
    }

    private static void recordNonStrictOcclusionSelection(NonStrictOcclusionSelection selection) {
        if (selection.zeroSamples() >= MIN_OPEN_VARIATION_SAMPLES_TO_OVERRIDE_BLOCKED_DIRECT) {
            SoundPhysicsTrace.recordNonStrictZeroOutlierAccepted(selection.zeroSamples());
        } else if (selection.zeroSamples() > 0) {
            SoundPhysicsTrace.recordNonStrictZeroOutlierIgnored(selection.zeroSamples());
        }

        if ("direct".equals(selection.decisionKind()) || "direct_open".equals(selection.decisionKind())) {
            SoundPhysicsTrace.recordNonStrictSelectedDirect();
        } else if ("median_positive".equals(selection.decisionKind())) {
            SoundPhysicsTrace.recordNonStrictSelectedMedianOrPositive();
        }
    }

    private static void logNonStrictOcclusionDecision(@Nullable ResourceLocation sound, @Nullable SoundSource category, double directOcclusion, double selectedOcclusion, double[] variationSamples, NonStrictOcclusionSelection selection, double variationFactor) {
        Loggers.logOcclusion(
                "Non-strict occlusion sound={} category={} direct={} selected={} samples={} zeroSamples={} positiveSamples={} strict=false variation={} decision={}",
                sound,
                category,
                directOcclusion,
                selectedOcclusion,
                Arrays.toString(variationSamples),
                selection.zeroSamples(),
                selection.positiveSamples(),
                variationFactor,
                selection.decisionKind()
        );
    }

    private static double medianSorted(double[] sortedValues, int count) {
        int midpoint = count / 2;
        if (count % 2 == 1) {
            return sortedValues[midpoint];
        }
        return (sortedValues[midpoint - 1] + sortedValues[midpoint]) * 0.5D;
    }

    static record NonStrictOcclusionSelection(double selectedOcclusion, int zeroSamples, int positiveSamples, String decisionKind) {
    }

    /**
     * Checks if the hit shares the same airspace with the listener
     *
     * @param hit              the hit position
     * @param listenerPosition the position of the listener
     * @return the vector between the hit and the listener or null if there is no shared airspace
     */
    @Nullable
    private static Vec3 getSharedAirspace(AcousticScene scene, AcousticRayHit hit, Vec3 listenerPosition) {
        Vec3 hitNormal = hit.worldNormal();
        Vec3 hitLocation = hit.worldLocation();
        Vec3 rayStart = new Vec3(hitLocation.x + hitNormal.x * 0.001D, hitLocation.y + hitNormal.y * 0.001D, hitLocation.z + hitNormal.z * 0.001D);
        return getSharedAirspace(scene, rayStart, listenerPosition);
    }

    /**
     * Checks if the hit shares the same airspace with the listener
     *
     * @param soundPosition    the sound position
     * @param listenerPosition the position of the listener
     * @return the vector between the hit and the listener or null if there is no shared airspace
     */
    @Nullable
    private static Vec3 getSharedAirspace(AcousticScene scene, Vec3 soundPosition, Vec3 listenerPosition) {
        AcousticRayHit finalRayHit = scene.rayCast(soundPosition, listenerPosition, null);
        if (finalRayHit.localHit().getType() == HitResult.Type.MISS) {
            RaycastRenderer.addSoundBounceRay(soundPosition, listenerPosition.add(0D, -0.1D, 0D), ChatFormatting.WHITE.getColor());
            return soundPosition.subtract(listenerPosition);
        }
        return null;
    }

    public static void setDefaultEnvironment(int sourceID) {
        setDefaultEnvironment(sourceID, false);
    }

    public static void setDefaultEnvironment(int sourceID, boolean auxOnly) {
        setEnvironment(sourceID, 0F, 0F, 0F, 0F, 1F, 1F, 1F, 1F, 1F, auxOnly ? 0F : 1F);
    }

    public static void setEnvironment(int sourceID, float sendGain0, float sendGain1, float sendGain2, float sendGain3, float sendCutoff0, float sendCutoff1, float sendCutoff2, float sendCutoff3, float directCutoff, float directGain) {
        setEnvironment(sourceID, sendGain0, sendGain1, sendGain2, sendGain3, sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3, directCutoff, directGain, SoundPhysicsMod.CONFIG.airAbsorption.get());
    }

    public static void setEnvironment(int sourceID, float sendGain0, float sendGain1, float sendGain2, float sendGain3, float sendCutoff0, float sendCutoff1, float sendCutoff2, float sendCutoff3, float directCutoff, float directGain, float airAbsorption) {
        if (!DiagnosticRuntimeOverrides.soundPhysicsEnabled(SoundPhysicsMod.CONFIG)) {
            return;
        }
        if (!AudioSourceRecovery.safeSourceExists(sourceID, lastSoundCategory, lastSound, "setEnvironment")) {
            RecordDiagnostics.markSourceInvalidated(sourceID, "source invalidated after volume/audio change; waiting for new source");
            return;
        }
        // Set reverb send filter values and set source to send to all reverb fx slots

        if (maxAuxSends >= 4) {
            EXTEfx.alFilterf(sendFilter0, EXTEfx.AL_LOWPASS_GAIN, sendGain0);
            EXTEfx.alFilterf(sendFilter0, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff0);
            AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot0, 3, sendFilter0);
            Loggers.logALError("Set environment filter0:");
        }

        if (maxAuxSends >= 3) {
            EXTEfx.alFilterf(sendFilter1, EXTEfx.AL_LOWPASS_GAIN, sendGain1);
            EXTEfx.alFilterf(sendFilter1, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff1);
            AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot1, 2, sendFilter1);
            Loggers.logALError("Set environment filter1:");
        }

        if (maxAuxSends >= 2) {
            EXTEfx.alFilterf(sendFilter2, EXTEfx.AL_LOWPASS_GAIN, sendGain2);
            EXTEfx.alFilterf(sendFilter2, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff2);
            AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot2, 1, sendFilter2);
            Loggers.logALError("Set environment filter2:");
        }

        if (maxAuxSends >= 1) {
            EXTEfx.alFilterf(sendFilter3, EXTEfx.AL_LOWPASS_GAIN, sendGain3);
            EXTEfx.alFilterf(sendFilter3, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff3);
            AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot3, 0, sendFilter3);
            Loggers.logALError("Set environment filter3:");
        }

        EXTEfx.alFilterf(directFilter0, EXTEfx.AL_LOWPASS_GAIN, directGain);
        EXTEfx.alFilterf(directFilter0, EXTEfx.AL_LOWPASS_GAINHF, directCutoff);
        AL11.alSourcei(sourceID, EXTEfx.AL_DIRECT_FILTER, directFilter0);
        Loggers.logALError("Set environment directFilter0:");

        AL11.alSourcef(sourceID, EXTEfx.AL_AIR_ABSORPTION_FACTOR, Math.max(0.0F, airAbsorption));
        Loggers.logALError("Set environment airAbsorption:");
    }

    private static void setSoundPos(int sourceID, Vec3 pos) {
        AudioSourceRecovery.safeSetSource3f(sourceID, AL11.AL_POSITION, (float) pos.x, (float) pos.y, (float) pos.z, lastSoundCategory, lastSound, "set sound position");
    }

    /*
     * Applies the parameters in the enum ReverbParams to the main reverb effect.
     */
    protected static void setReverbParams(ReverbParams r, int auxFXSlot, int reverbSlot) {
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DENSITY, r.density);
        Loggers.logALErrorAlways("Error while assigning reverb density: " + r.density);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DIFFUSION, r.diffusion);
        Loggers.logALErrorAlways("Error while assigning reverb diffusion: " + r.diffusion);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_GAIN, r.gain);
        Loggers.logALErrorAlways("Error while assigning reverb gain: " + r.gain);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_GAINHF, r.gainHF);
        Loggers.logALErrorAlways("Error while assigning reverb gainHF: " + r.gainHF);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DECAY_TIME, r.decayTime);
        Loggers.logALErrorAlways("Error while assigning reverb decayTime: " + r.decayTime);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, r.decayHFRatio);
        Loggers.logALErrorAlways("Error while assigning reverb decayHFRatio: " + r.decayHFRatio);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, r.reflectionsGain);
        Loggers.logALErrorAlways("Error while assigning reverb reflectionsGain: " + r.reflectionsGain);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, r.lateReverbGain);
        Loggers.logALErrorAlways("Error while assigning reverb lateReverbGain: " + r.lateReverbGain);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, r.lateReverbDelay);
        Loggers.logALErrorAlways("Error while assigning reverb lateReverbDelay: " + r.lateReverbDelay);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, r.airAbsorptionGainHF);
        Loggers.logALErrorAlways("Error while assigning reverb airAbsorptionGainHF: " + r.airAbsorptionGainHF);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, r.roomRolloffFactor);
        Loggers.logALErrorAlways("Error while assigning reverb roomRolloffFactor: " + r.roomRolloffFactor);

        // Attach updated effect object
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverbSlot);
    }

}
