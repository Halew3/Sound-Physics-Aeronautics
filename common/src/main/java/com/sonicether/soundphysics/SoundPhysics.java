package com.sonicether.soundphysics;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.acoustic.AcousticBlockRef;
import com.sonicether.soundphysics.acoustic.AcousticRayHit;
import com.sonicether.soundphysics.acoustic.AcousticScene;
import com.sonicether.soundphysics.acoustic.AcousticSceneContext;
import com.sonicether.soundphysics.acoustic.AcousticScenes;
import com.sonicether.soundphysics.utils.SoundRateManager;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SoundPhysics {

    private static final float PHI = 1.618033988F;
    private static final double OCCLUSION_EPSILON = 1.0E-4D;
    private static final double RAY_ORIGIN_ADVANCE_EPSILON = 1.0E-4D;
    private static final float RAW_DIRECT_FILTER_THRESHOLD = 0.99F;
    private static final int PREPLAY_FALLBACK_CACHE_TTL_TICKS = 5;
    private static final double PREPLAY_FALLBACK_CACHE_RADIUS = 1.0D;
    private static final double PREPLAY_FALLBACK_CACHE_RADIUS_SQR = PREPLAY_FALLBACK_CACHE_RADIUS * PREPLAY_FALLBACK_CACHE_RADIUS;
    private static final float PREPLAY_FALLBACK_DIRECT_GAIN_FLOOR = 0.6F;
    private static final int ACOUSTIC_ENVIRONMENT_CACHE_MAX_SIZE = 64;
    private static final int ACOUSTIC_ENVIRONMENT_CACHE_TTL_TICKS = 20;
    private static final double ACOUSTIC_ENVIRONMENT_CACHE_RADIUS = 3.0D;
    private static final double ACOUSTIC_ENVIRONMENT_CACHE_RADIUS_SQR = ACOUSTIC_ENVIRONMENT_CACHE_RADIUS * ACOUSTIC_ENVIRONMENT_CACHE_RADIUS;
    private static final long FRESH_FULL_ENVIRONMENT_WINDOW_NANOS = 250_000_000L;
    private static final long FRESH_FULL_ENVIRONMENT_WINDOW_TICKS = 2L;
    private static final double FRESH_FULL_ENVIRONMENT_POSITION_TOLERANCE = 1.0D / 32.0D;
    private static final double FRESH_FULL_ENVIRONMENT_POSITION_TOLERANCE_SQR =
            FRESH_FULL_ENVIRONMENT_POSITION_TOLERANCE * FRESH_FULL_ENVIRONMENT_POSITION_TOLERANCE;

    private static final Pattern AMBIENT_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.]+:ambient\\..*$");
    private static final Pattern BLOCK_PATTERN = Pattern.compile(".*block..*");
    private static final Deque<AcousticEnvironmentSnapshot> ACOUSTIC_ENVIRONMENT_SNAPSHOTS = new ArrayDeque<>();
    private static final ConcurrentMap<Integer, SourceStartState> SOURCE_START_STATES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, AttachedDirectFilter> ATTACHED_DIRECT_FILTERS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, SourceEfxState> SOURCE_EFX_STATES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, LastAppliedEnvironment> LAST_APPLIED_ENVIRONMENTS = new ConcurrentHashMap<>();
    private static final EnvironmentBackend OPEN_AL_ENVIRONMENT_BACKEND = SoundPhysics::applyOpenAlEnvironment;
    private static final SourceFilterReadbackBackend OPEN_AL_SOURCE_FILTER_READBACK_BACKEND = SoundPhysics::readOpenAlSourceFilter;
    private static final DirectFilterBackend OPEN_AL_DIRECT_FILTER_BACKEND = new OpenAlDirectFilterBackend();
    private static EnvironmentBackend environmentBackend = OPEN_AL_ENVIRONMENT_BACKEND;
    private static SourceFilterReadbackBackend sourceFilterReadbackBackend = OPEN_AL_SOURCE_FILTER_READBACK_BACKEND;
    private static DirectFilterBackend directFilterBackend = OPEN_AL_DIRECT_FILTER_BACKEND;

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
    private static final AtomicLong perSourceDirectFiltersCreated = new AtomicLong();
    private static final AtomicLong perSourceDirectFiltersDeleted = new AtomicLong();
    private static final AtomicLong sharedDirectFilterWrites = new AtomicLong();
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
            deleteAllSourceEfxStates();
            deleteFilter(directFilter0);
            deleteFilter(sendFilter0);
            deleteFilter(sendFilter1);
            deleteFilter(sendFilter2);
            deleteFilter(sendFilter3);
        } else {
            SOURCE_EFX_STATES.clear();
        }
        ATTACHED_DIRECT_FILTERS.clear();

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
        return "SPRA_PATCH_ID=" + SoundPhysicsMod.SPRA_PATCH_ID
                + ", efxInitialized=" + efxInitialized
                + ", efxInitCount=" + efxInitCount.get()
                + ", efxDestroyCount=" + efxDestroyCount.get()
                + ", efxLastInitReason=" + efxLastInitReason
                + ", efxLastDestroyReason=" + efxLastDestroyReason
                + ", efxActiveAuxSlots=" + activeAuxSlotCount()
                + ", auxSlots=(" + auxFXSlot0 + "," + auxFXSlot1 + "," + auxFXSlot2 + "," + auxFXSlot3 + ")"
                + ", effects=(" + reverb0 + "," + reverb1 + "," + reverb2 + "," + reverb3 + ")"
                + ", filters=(direct=" + directFilter0 + ", sends=" + sendFilter0 + "," + sendFilter1 + "," + sendFilter2 + "," + sendFilter3 + ")"
                + ", activePerSourceDirectFilters=" + SOURCE_EFX_STATES.size()
                + ", perSourceDirectFiltersCreated=" + perSourceDirectFiltersCreated.get()
                + ", perSourceDirectFiltersDeleted=" + perSourceDirectFiltersDeleted.get()
                + ", sharedDirectFilterWrites=" + sharedDirectFilterWrites.get()
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
        SOURCE_EFX_STATES.clear();
        ATTACHED_DIRECT_FILTERS.clear();
        LAST_APPLIED_ENVIRONMENTS.clear();
        perSourceDirectFiltersCreated.set(0L);
        perSourceDirectFiltersDeleted.set(0L);
        sharedDirectFilterWrites.set(0L);
        directFilterBackend = OPEN_AL_DIRECT_FILTER_BACKEND;
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
                || sendFilter3 != 0
                || !SOURCE_EFX_STATES.isEmpty();
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

    private static void deleteAllSourceEfxStates() {
        for (SourceEfxState state : SOURCE_EFX_STATES.values()) {
            deleteSourceDirectFilter(state.directFilter());
        }
        SOURCE_EFX_STATES.clear();
        ATTACHED_DIRECT_FILTERS.clear();
    }

    private static void deleteSourceEfxState(int sourceID) {
        SourceEfxState state = SOURCE_EFX_STATES.remove(sourceID);
        ATTACHED_DIRECT_FILTERS.remove(sourceID);
        if (state != null) {
            deleteSourceDirectFilter(state.directFilter());
        }
    }

    private static void deleteSourceDirectFilter(int filter) {
        if (filter == 0) {
            return;
        }
        try {
            directFilterBackend.deleteFilter(filter);
            perSourceDirectFiltersDeleted.incrementAndGet();
        } catch (Throwable throwable) {
            Loggers.warn("Failed deleting per-source direct filter {}: {}", filter, throwable.getMessage());
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

    public static void onPlaySound(
            double posX,
            double posY,
            double posZ,
            int sourceID,
            SoundSource category,
            ResourceLocation sound,
            SoundPhysicsSoundPolicy.SoundContext context
    ) {
        SoundPhysicsTrace.recordOnPlaySound(sourceID, posX, posY, posZ, category, sound);
        processSound(sourceID, posX, posY, posZ, category, sound, false, context);
    }

    /**
     * The old method signature of soundphysics to stay compatible
     */
    public static void onPlayReverb(double posX, double posY, double posZ, int sourceID) {
        processSound(sourceID, posX, posY, posZ, lastSoundCategory, lastSound, true);
    }

    public static void beforeProcessSourceStart(
            int sourceID,
            Vec3 position,
            SoundSource category,
            ResourceLocation sound,
            SoundPhysicsSoundPolicy.SoundContext context
    ) {
        SoundPhysicsSoundPolicy.SoundContext safeContext = context == null
                ? SoundPhysicsSoundPolicy.SoundContext.of(sound, category)
                : context;
        SOURCE_START_STATES.put(sourceID, SourceStartState.create(position, category, sound, safeContext));
        logSourceFilterReadbackIfActive("beforeProcess", sourceID, category, sound);
    }

    public static void beforeAlSourcePlay(
            int sourceID,
            @Nullable Vec3 position,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            @Nullable SoundPhysicsSoundPolicy.SoundContext context
    ) {
        SoundPhysicsSoundPolicy.SoundContext safeContext = context == null
                ? SoundPhysicsSoundPolicy.SoundContext.of(sound, category)
                : context;
        if (position != null && category != null && sound != null) {
            SOURCE_START_STATES.putIfAbsent(sourceID, SourceStartState.create(position, category, sound, safeContext));
        }

        if (!isFocusedEntityStart(category, sound)) {
            return;
        }

        SourceFilterReadback initialReadback = readSourceFilterReadback(sourceID, category, sound);
        @Nullable EnvironmentParameters preplayEnvironment = null;
        if (initialReadback.raw() && sourceStartDiagnosticsActive()) {
            SoundPhysicsTrace.recordPreplayRawFilterWarning();
            Loggers.warn(
                    "SPRA PREPLAY_RAW_FILTER_WARNING source={} sound={} category={} directFilter={} gain={} gainHF={} sourceState={} readbackReliable=false",
                    sourceID,
                    sound,
                    category,
                    initialReadback.directFilter(),
                    initialReadback.gain(),
                    initialReadback.gainHF(),
                    initialReadback.sourceState()
            );
        }
        if (isPreplayFallbackEnabledFor(sourceID, position, category, sound, safeContext)) {
            preplayEnvironment = applyPreplayFallback(sourceID, position, category, sound);
        }

        SourceFilterReadback finalReadback = preplayEnvironment == null
                ? initialReadback
                : readSourceFilterReadback(sourceID, category, sound);
        if (finalReadback.available()) {
            SoundPhysicsTrace.recordSourceFilterReadbackBeforePlay(finalReadback.raw(), finalReadback.muffled());
        }
        logSourceFilterReadbackIfActive("beforeAlSourcePlay", sourceID, category, sound, finalReadback);
        logPositionalStartTraceIfActive(sourceID, position, category, sound, preplayEnvironment, finalReadback);
    }

    public static void afterAlSourcePlay(
            int sourceID,
            @Nullable Vec3 position,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            @Nullable SoundPhysicsSoundPolicy.SoundContext context
    ) {
        logSourceFilterReadbackIfActive("afterAlSourcePlay", sourceID, category, sound);
        SOURCE_START_STATES.remove(sourceID);
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
            return skipEnvironment(sourceID, soundPos, sound, category, "missing player/level or zero position", auxOnly, context, reason, evaluateStartNanos);
        }
        double distance = player.position().distanceTo(soundPos);
        double maxProcessingDistance = PropellerLongRangeAudio.effectiveProcessingDistance(
                sourceID,
                context,
                SoundPhysicsMod.CONFIG.maxSoundProcessingDistance.get()
        );
        if (SoundProcessingPolicy.isTooDistant(distance, maxProcessingDistance)) {
            Loggers.logDebug("Sound {} is too far away from player ({} blocks)", sound, distance);
            return skipEnvironment(sourceID, soundPos, sound, category, "distance", auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_DISTANCE, evaluateStartNanos);
        }

        SoundPhysicsSoundPolicy.Decision policyDecision = SoundPhysicsSoundPolicy.evaluateAcoustic(SoundPhysicsMod.CONFIG, context);
        if (!policyDecision.apply()) {
            return skipEnvironment(sourceID, soundPos, sound, category, "sound policy " + policyDecision.reason(), auxOnly, context, policyDecision.reason(), evaluateStartNanos);
        }

        if (!SoundRateManager.isWorldInitialized()) {
            Loggers.logDebug("Sound {} skipped because the world is not initialized yet", sound);
            return skipEnvironment(sourceID, soundPos, sound, category, "world not initialized", auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_WORLD_NOT_INITIALIZED, evaluateStartNanos);
        }

        if (!SoundPhysicsSoundPolicy.isSoundRateLimitExempt(context) && SoundRateManager.incrementAndCheckLimit(sound)) {
            Loggers.logDebug("Sound {} skipped due to sound rate limit", sound);
            return skipEnvironment(sourceID, soundPos, sound, category, "sound rate limit", auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_RATE_LIMIT, evaluateStartNanos);
        }

        long gameTime = level.getGameTime();
        if (context.startEvent()
                && !SoundPhysicsSoundPolicy.isStartThrottleExempt(context)
                && !SoundPhysicsPerfDiagnostics.recordSoundStart(gameTime, SoundPhysicsMod.CONFIG.soundPhysicsMaxSoundStartsPerTick.get())) {
            return skipEnvironment(sourceID, soundPos, sound, category, SoundPhysicsPerfDiagnostics.soundStartThrottleReason(sound), auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_THROTTLE, evaluateStartNanos);
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
            return skipEnvironment(sourceID, soundPos, sound, category, "impact burst dedupe", auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE, evaluateStartNanos);
        }

        float directCutoff;
        float absorptionCoeff = (float) (SoundPhysicsMod.CONFIG.blockAbsorption.get() * 3D);

        // Direct sound occlusion

        Vec3 playerPos = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 normalToPlayer = playerPos.subtract(soundPos).normalize();

        AcousticScene scene = AcousticScenes.createScene(minecraft, new AcousticSceneContext(sourceID, soundPos, playerPos, category, sound));
        if (scene == null) {
            return skipEnvironment(sourceID, soundPos, sound, category, "null acoustic scene", auxOnly, context, SoundPhysicsSoundPolicy.DecisionReason.SKIP_NULL_SCENE, evaluateStartNanos);
        }

        AcousticBlockRef soundBlock = scene.blockAt(soundPos);
        FluidState soundFluidState = soundBlock.fluidState();
        boolean sourceIsUnderwater = soundFluidState.is(FluidTags.WATER);

        Loggers.logDebug("Player pos: {}, {}, {} \tSound Pos: {}, {}, {} \tTo player vector: {}, {}, {}", playerPos.x, playerPos.y, playerPos.z, soundPos.x, soundPos.y, soundPos.z, normalToPlayer.x, normalToPlayer.y, normalToPlayer.z);

        RaycastRenderer.setCurrentSoundContext(context);
        double occlusionAccumulation = calculateOcclusion(sourceID, scene, soundPos, playerPos, category, sound);

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
            setSoundPos(sourceID, newSoundPos, category, sound);
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
        EnvironmentParameters environment = new EnvironmentParameters(sendGain0, sendGain1, sendGain2, sendGain3, sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3, directCutoff, directGain, airAbsorption);
        if (applyEnvironmentToSource(sourceID, environment, category, sound)) {
            storeAcousticEnvironmentSnapshot(environment, actualSoundPos, sound, category, level.getGameTime());
            recordLastAppliedEnvironment(sourceID, environment, actualSoundPos, category, sound, level.getGameTime(), EnvironmentApplicationKind.FULL_ENVIRONMENT);
        }
        RaycastRenderer.clearCurrentSoundContext();
        SoundPhysicsPolicyDiagnostics.recordProcessedNormally(context);

        SoundPhysicsPerfDiagnostics.recordEvaluateEnvironment(System.nanoTime() - evaluateStartNanos);
        return newSoundPos;
    }

    @Nullable
    private static Vec3 skipEnvironment(
            int sourceID,
            Vec3 soundPos,
            @Nullable ResourceLocation sound,
            @Nullable SoundSource category,
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
            logOverloadSkipCaughtIfNeeded(sourceID, soundPos, sound, decisionReason);
            if (!applyOverloadFallback(sourceID, soundPos, category, sound, auxOnly, context, decisionReason)) {
                SoundPhysicsPolicyDiagnostics.recordOverloadFallbackFailed();
                SoundPhysicsPolicyDiagnostics.recordEnvironmentUntouched(context);
                Loggers.warn("SPRA OVERLOAD_FALLBACK_FAILED source={} sound={} reason={}", sourceID, sound, decisionReason);
            }
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

    private static void logOverloadSkipCaughtIfNeeded(int sourceID, Vec3 soundPos, @Nullable ResourceLocation sound, SoundPhysicsSoundPolicy.DecisionReason decisionReason) {
        if (decisionReason != SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE || !isSuspiciousRawOcclusionSound(sound, null)) {
            return;
        }
        Loggers.warn("SPRA OVERLOAD_SKIP_CAUGHT source={} sound={} pos={} reason={}", sourceID, sound, soundPos, decisionReason);
    }

    private static boolean applyOverloadFallback(
            int sourceID,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            boolean auxOnly,
            SoundPhysicsSoundPolicy.SoundContext context,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason
    ) {
        long gameTime = currentClientGameTime();
        if (isMinecraftBlockInteractionImpactDedupe(category, sound, decisionReason)) {
            if (preserveFreshFullEnvironmentBeforeDirectOnlyFallback(sourceID, soundPos, category, sound, decisionReason, gameTime)) {
                return true;
            }
            if (applyExactCachedOverloadFallback(sourceID, soundPos, category, sound, context, decisionReason, gameTime)) {
                return true;
            }
            if (skipBlockEventFallbackForRecentSourceMixin(sourceID, soundPos, category, sound)) {
                return true;
            }
            SoundPhysicsPolicyDiagnostics.recordBlockEventDirectOnlyLastResort();
            Loggers.warn("SPRA BLOCK_EVENT_DIRECT_ONLY_LAST_RESORT source={} sound={} reason={} fallback=direct_only", sourceID, sound, decisionReason);
            return applyDirectOnlyOverloadFallback(sourceID, soundPos, category, sound, auxOnly, context, decisionReason, gameTime);
        }
        if (!shouldBypassCachedOverloadFallback(category, sound)
                && applyCachedOverloadFallback(sourceID, soundPos, category, sound, context, decisionReason, gameTime)) {
            return true;
        }
        return applyDirectOnlyOverloadFallback(sourceID, soundPos, category, sound, auxOnly, context, decisionReason, gameTime);
    }

    private static boolean applyCachedOverloadFallback(
            int sourceID,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            SoundPhysicsSoundPolicy.SoundContext context,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason,
            long gameTime
    ) {
        @Nullable SnapshotMatch match = findNearestEnvironmentSnapshot(soundPos, category, sound, gameTime);
        if (match == null) {
            return false;
        }

        AcousticEnvironmentSnapshot snapshot = match.snapshot();
        EnvironmentParameters environment = snapshot.environment();
        if (!applyEnvironmentToSource(sourceID, environment, category, sound)) {
            return false;
        }

        storeAcousticEnvironmentSnapshot(environment, soundPos, sound, category, gameTime);
        recordLastAppliedEnvironment(sourceID, environment, soundPos, category, sound, gameTime, EnvironmentApplicationKind.OVERLOAD_NEAREST);
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackNearestApplied();
        Loggers.log(
                "SPRA OVERLOAD_FALLBACK_APPLIED source={} sound={} reason={} fallback=nearest cachedSound={} distance={} ageTicks={} directCutoff={} directGain={} sendGain0={} sendGain1={} sendGain2={} sendGain3={} airAbsorption={}",
                sourceID,
                sound,
                decisionReason,
                snapshot.sound(),
                match.distance(),
                match.ageTicks(),
                environment.directCutoff(),
                environment.directGain(),
                environment.sendGain0(),
                environment.sendGain1(),
                environment.sendGain2(),
                environment.sendGain3(),
                environment.airAbsorption()
        );
        recordOverloadFallbackReadback(sourceID, category, sound);
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)
                && (environment.directCutoff() < 0.99F || environment.directGain() < 0.99F
                || environment.sendGain0() > 0.0F || environment.sendGain1() > 0.0F || environment.sendGain2() > 0.0F || environment.sendGain3() > 0.0F)) {
            SoundPhysicsPolicyDiagnostics.recordPropellerMuffledOrFiltered();
        }
        return true;
    }

    private static boolean applyExactCachedOverloadFallback(
            int sourceID,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            SoundPhysicsSoundPolicy.SoundContext context,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason,
            long gameTime
    ) {
        @Nullable SnapshotMatch match = findNearestExactEnvironmentSnapshot(soundPos, category, sound, gameTime);
        if (match == null) {
            return false;
        }

        AcousticEnvironmentSnapshot snapshot = match.snapshot();
        EnvironmentParameters environment = snapshot.environment();
        if (!applyEnvironmentToSource(sourceID, environment, category, sound)) {
            return false;
        }

        storeAcousticEnvironmentSnapshot(environment, soundPos, sound, category, gameTime);
        recordLastAppliedEnvironment(sourceID, environment, soundPos, category, sound, gameTime, EnvironmentApplicationKind.OVERLOAD_NEAREST);
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackNearestApplied();
        Loggers.log(
                "SPRA OVERLOAD_FALLBACK_APPLIED source={} sound={} reason={} fallback=nearest cachedSound={} distance={} ageTicks={} directCutoff={} directGain={} sendGain0={} sendGain1={} sendGain2={} sendGain3={} airAbsorption={}",
                sourceID,
                sound,
                decisionReason,
                snapshot.sound(),
                match.distance(),
                match.ageTicks(),
                environment.directCutoff(),
                environment.directGain(),
                environment.sendGain0(),
                environment.sendGain1(),
                environment.sendGain2(),
                environment.sendGain3(),
                environment.airAbsorption()
        );
        recordOverloadFallbackReadback(sourceID, category, sound);
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)
                && (environment.directCutoff() < 0.99F || environment.directGain() < 0.99F
                || environment.sendGain0() > 0.0F || environment.sendGain1() > 0.0F || environment.sendGain2() > 0.0F || environment.sendGain3() > 0.0F)) {
            SoundPhysicsPolicyDiagnostics.recordPropellerMuffledOrFiltered();
        }
        return true;
    }

    private static boolean applyDirectOnlyOverloadFallback(
            int sourceID,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            boolean auxOnly,
            SoundPhysicsSoundPolicy.SoundContext context,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason,
            long gameTime
    ) {
        try {
            Vec3 playerPos = minecraft.gameRenderer.getMainCamera().getPosition();
            AcousticScene scene = AcousticScenes.createScene(minecraft, new AcousticSceneContext(sourceID, soundPos, playerPos, category, sound));
            if (scene == null) {
                return false;
            }
            return applyDirectOnlyOverloadFallback(sourceID, soundPos, playerPos, category, sound, auxOnly, context, decisionReason, gameTime, scene);
        } catch (Throwable throwable) {
            Loggers.warn("SPRA OVERLOAD_FALLBACK_FAILED source={} sound={} reason={} error={}", sourceID, sound, decisionReason, throwable.getMessage());
            return false;
        }
    }

    static boolean applyDirectOnlyOverloadFallbackForTests(
            int sourceID,
            Vec3 soundPos,
            Vec3 playerPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            boolean auxOnly,
            SoundPhysicsSoundPolicy.SoundContext context,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason,
            long gameTime,
            AcousticScene scene
    ) {
        return applyDirectOnlyOverloadFallback(sourceID, soundPos, playerPos, category, sound, auxOnly, context, decisionReason, gameTime, scene);
    }

    static boolean applyOverloadFallbackForTests(
            int sourceID,
            Vec3 soundPos,
            Vec3 playerPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            boolean auxOnly,
            SoundPhysicsSoundPolicy.SoundContext context,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason,
            long gameTime,
            AcousticScene scene
    ) {
        if (isMinecraftBlockInteractionImpactDedupe(category, sound, decisionReason)) {
            if (preserveFreshFullEnvironmentBeforeDirectOnlyFallback(sourceID, soundPos, category, sound, decisionReason, gameTime)) {
                return true;
            }
            if (applyExactCachedOverloadFallback(sourceID, soundPos, category, sound, context, decisionReason, gameTime)) {
                return true;
            }
            if (skipBlockEventFallbackForRecentSourceMixin(sourceID, soundPos, category, sound)) {
                return true;
            }
            SoundPhysicsPolicyDiagnostics.recordBlockEventDirectOnlyLastResort();
            Loggers.warn("SPRA BLOCK_EVENT_DIRECT_ONLY_LAST_RESORT source={} sound={} reason={} fallback=direct_only", sourceID, sound, decisionReason);
            return applyDirectOnlyOverloadFallback(sourceID, soundPos, playerPos, category, sound, auxOnly, context, decisionReason, gameTime, scene);
        }
        if (!shouldBypassCachedOverloadFallback(category, sound)
                && applyCachedOverloadFallback(sourceID, soundPos, category, sound, context, decisionReason, gameTime)) {
            return true;
        }
        return applyDirectOnlyOverloadFallback(sourceID, soundPos, playerPos, category, sound, auxOnly, context, decisionReason, gameTime, scene);
    }

    private static boolean applyDirectOnlyOverloadFallback(
            int sourceID,
            Vec3 soundPos,
            Vec3 playerPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            boolean auxOnly,
            SoundPhysicsSoundPolicy.SoundContext context,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason,
            long gameTime,
            AcousticScene scene
    ) {
        if (preserveFreshFullEnvironmentBeforeDirectOnlyFallback(sourceID, soundPos, category, sound, decisionReason, gameTime)) {
            return true;
        }

        double occlusion = Math.min(runOcclusion(scene, soundPos, playerPos, category, sound), SoundPhysicsMod.CONFIG.maxOcclusion.get());
        float absorptionCoeff = (float) (SoundPhysicsMod.CONFIG.blockAbsorption.get() * 3D);
        float directCutoff = (float) Math.exp(-occlusion * absorptionCoeff);
        float directGain = auxOnly ? 0F : (float) Math.pow(directCutoff, 0.1D);
        AcousticBlockRef soundBlock = scene.blockAt(soundPos);
        boolean sourceIsUnderwater = soundBlock.fluidState().is(FluidTags.WATER);
        if ((minecraft != null && minecraft.player != null && minecraft.player.isUnderWater()) || sourceIsUnderwater) {
            directCutoff *= 1F - SoundPhysicsMod.CONFIG.underwaterFilter.get();
        }

        EnvironmentParameters environment = new EnvironmentParameters(
                0F,
                0F,
                0F,
                0F,
                1F,
                1F,
                1F,
                1F,
                directCutoff,
                directGain,
                SoundPhysicsMod.CONFIG.airAbsorption.get()
        );
        if (!applyEnvironmentToSource(sourceID, environment, category, sound)) {
            return false;
        }

        storeAcousticEnvironmentSnapshot(environment, soundPos, sound, category, gameTime);
        recordLastAppliedEnvironment(sourceID, environment, soundPos, category, sound, gameTime, EnvironmentApplicationKind.OVERLOAD_DIRECT_ONLY);
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackDirectOnlyApplied();
        if (isMinecraftBlockInteractionImpactDedupe(category, sound, decisionReason)) {
            SoundPhysicsPolicyDiagnostics.recordBlockEventDirectOnlyFallbackApplied();
        }
        Loggers.log(
                "SPRA OVERLOAD_FALLBACK_APPLIED source={} sound={} reason={} fallback=direct_only occlusion={} directCutoff={} directGain={} sendGain0={} sendGain1={} sendGain2={} sendGain3={} airAbsorption={}",
                sourceID,
                sound,
                decisionReason,
                occlusion,
                directCutoff,
                directGain,
                environment.sendGain0(),
                environment.sendGain1(),
                environment.sendGain2(),
                environment.sendGain3(),
                environment.airAbsorption()
        );
        recordOverloadFallbackReadback(sourceID, category, sound);
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context) && (directCutoff < 0.99F || directGain < 0.99F)) {
            SoundPhysicsPolicyDiagnostics.recordPropellerMuffledOrFiltered();
        }
        return true;
    }

    static boolean applyCachedOverloadFallbackForTests(
            int sourceID,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            SoundPhysicsSoundPolicy.SoundContext context,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason,
            long gameTime
    ) {
        return applyCachedOverloadFallback(sourceID, soundPos, category, sound, context, decisionReason, gameTime);
    }

    static boolean applyFullEnvironmentForTests(
            int sourceID,
            EnvironmentParameters environment,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            long gameTime
    ) {
        if (!applyEnvironmentToSource(sourceID, environment, category, sound)) {
            return false;
        }
        recordLastAppliedEnvironment(sourceID, environment, soundPos, category, sound, gameTime, EnvironmentApplicationKind.FULL_ENVIRONMENT);
        return true;
    }

    private static boolean preserveFreshFullEnvironmentBeforeDirectOnlyFallback(
            int sourceID,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason,
            long gameTime
    ) {
        @Nullable LastAppliedEnvironment existing = findFreshFullEnvironment(sourceID, soundPos, category, sound, gameTime, System.nanoTime());
        if (existing == null) {
            return false;
        }

        EnvironmentParameters environment = existing.environment();
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackPreservedExistingEnvironment();
        if (hasNonZeroSends(environment)) {
            SoundPhysicsPolicyDiagnostics.recordDuplicateFallbackWouldOverwriteReverb();
            Loggers.warn(
                    "SPRA DUPLICATE_FALLBACK_WOULD_OVERWRITE_REVERB source={} sound={} path=soundEngineFallback existingSends=({}, {}, {}, {}) attemptedFallback=direct_only",
                    sourceID,
                    sound,
                    environment.sendGain0(),
                    environment.sendGain1(),
                    environment.sendGain2(),
                    environment.sendGain3()
            );
        }
        Loggers.log(
                "SPRA OVERLOAD_FALLBACK_PRESERVED_EXISTING_ENV source={} sound={} reason={} existingSendGain0={} existingSendGain1={} existingSendGain2={} existingSendGain3={}",
                sourceID,
                sound,
                decisionReason,
                environment.sendGain0(),
                environment.sendGain1(),
                environment.sendGain2(),
                environment.sendGain3()
        );
        return true;
    }

    private static boolean skipBlockEventFallbackForRecentSourceMixin(
            int sourceID,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        if (!SoundProcessingDeduper.wasRecentlyProcessedBySourceMixin(sourceID, sound, category, soundPos, System.nanoTime())) {
            return false;
        }

        SoundPhysicsTrace.recordDuplicateProcessingSkip(SoundProcessingDeduper.ProcessingPath.SOUND_ENGINE_FALLBACK, sourceID, sound);
        SoundPhysicsTrace.recordSoundEngineFallbackSkippedRecentSourceMixin(sourceID, sound);
        Loggers.logTrace("Skipped block event overload fallback because SourceMixin recently processed source={} sound={}", sourceID, sound);
        return true;
    }

    private static boolean isMinecraftBlockInteractionImpactDedupe(
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            SoundPhysicsSoundPolicy.DecisionReason decisionReason
    ) {
        return decisionReason == SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE
                && isMinecraftBlockSound(sound)
                && isStepOrBlockEventSound(sound);
    }

    private static boolean hasNonZeroSends(EnvironmentParameters environment) {
        return environment.sendGain0() > 0.0F
                || environment.sendGain1() > 0.0F
                || environment.sendGain2() > 0.0F
                || environment.sendGain3() > 0.0F;
    }

    @Nullable
    private static LastAppliedEnvironment findFreshFullEnvironment(
            int sourceID,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            long gameTime,
            long nowNanos
    ) {
        @Nullable LastAppliedEnvironment existing = LAST_APPLIED_ENVIRONMENTS.get(sourceID);
        if (existing == null || existing.kind() != EnvironmentApplicationKind.FULL_ENVIRONMENT) {
            return null;
        }
        if (!existing.matches(soundPos, category, sound)) {
            return null;
        }
        return existing.fresh(gameTime, nowNanos) ? existing : null;
    }

    private static void recordLastAppliedEnvironment(
            int sourceID,
            EnvironmentParameters environment,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            long gameTime,
            EnvironmentApplicationKind kind
    ) {
        LAST_APPLIED_ENVIRONMENTS.put(sourceID, new LastAppliedEnvironment(
                environment,
                soundPos,
                category,
                sound,
                gameTime,
                System.nanoTime(),
                kind
        ));
    }

    private static long currentClientGameTime() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : Long.MIN_VALUE;
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

    private static double calculateOcclusion(int sourceID, AcousticScene scene, Vec3 soundPos, Vec3 playerPos, SoundSource category, ResourceLocation sound) {
        long startNanos = System.nanoTime();
        SoundPhysicsTrace.recordCalculateOcclusion(soundPos, playerPos, category, sound);
        try {
            if (SoundPhysicsMod.CONFIG.strictOcclusion.get()) {
                return Math.min(runOcclusion(scene, soundPos, playerPos, category, sound), SoundPhysicsMod.CONFIG.maxOcclusion.get());
            }
            boolean isBlock = category == SoundSource.BLOCKS || (sound != null && BLOCK_PATTERN.matcher(sound.toString()).matches());
            double variationFactor = SoundPhysicsMod.CONFIG.occlusionVariation.get();

            if (isBlock) {
                variationFactor = Math.max(variationFactor, 0.49D);
            }

            double directOcclusion = runOcclusion(scene, soundPos, playerPos, category, sound);

            if (directOcclusion <= OCCLUSION_EPSILON) {
                NonStrictOcclusionSelection selection = selectNonStrictOcclusion(directOcclusion, new double[0]);
                SoundPhysicsTrace.recordNonStrictSelectedDirect();
                logNonStrictOcclusionDecision(sound, category, directOcclusion, selection.selectedOcclusion(), new double[0], selection, variationFactor);
                logRawOcclusionTraceIfSuspicious(sourceID, scene, sound, category, soundPos, playerPos, directOcclusion, selection.selectedOcclusion(), new double[0], selection);
                return 0.0D;
            }

            double[] variationSamples = sampleNonStrictVariationOcclusion(scene, soundPos, playerPos, variationFactor, category, sound);

            NonStrictOcclusionSelection selection = selectNonStrictOcclusion(directOcclusion, variationSamples);
            recordNonStrictOcclusionSelection(selection);
            double selectedOcclusion = Math.min(selection.selectedOcclusion(), SoundPhysicsMod.CONFIG.maxOcclusion.get());
            logNonStrictOcclusionDecision(sound, category, directOcclusion, selectedOcclusion, variationSamples, selection, variationFactor);
            logOcclusionInvariantIfNeeded(sourceID, scene, sound, category, soundPos, playerPos, directOcclusion, selectedOcclusion, variationSamples, selection);
            logRawOcclusionTraceIfSuspicious(sourceID, scene, sound, category, soundPos, playerPos, directOcclusion, selectedOcclusion, variationSamples, selection);
            return selectedOcclusion;
        } finally {
            SoundPhysicsPerfDiagnostics.recordCalculateOcclusion(System.nanoTime() - startNanos);
        }
    }

    static double[] sampleNonStrictVariationOcclusion(AcousticScene scene, Vec3 soundPos, Vec3 playerPos, double variationFactor) {
        return sampleNonStrictVariationOcclusion(scene, soundPos, playerPos, variationFactor, null, null);
    }

    static double[] sampleNonStrictVariationOcclusion(
            AcousticScene scene,
            Vec3 soundPos,
            Vec3 playerPos,
            double variationFactor,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        if (variationFactor <= 0D) {
            return new double[0];
        }

        double[] variationSamples = new double[8];
        int sampleIndex = 0;
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    Vec3 offset = new Vec3(x, y, z).scale(variationFactor);
                    variationSamples[sampleIndex] = runOcclusion(scene, soundPos.add(offset), playerPos, category, sound);
                    sampleIndex++;
                }
            }
        }
        return variationSamples;
    }

    static double runOcclusion(AcousticScene scene, Vec3 soundPos, Vec3 playerPos) {
        return runOcclusion(scene, soundPos, playerPos, null, null);
    }

    static double runOcclusion(
            AcousticScene scene,
            Vec3 soundPos,
            Vec3 playerPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        long startNanos = System.nanoTime();
        SoundPhysicsTrace.recordRunOcclusion(soundPos, playerPos);
        try {
            double occlusionAccumulation = 0D;
            Vec3 rayOrigin = soundPos;

            AcousticBlockRef sourceBlock = scene.blockAt(soundPos);
            recordSourceBlockSelfOcclusionSkipped(scene, sourceBlock, soundPos, category, sound);
            AcousticBlockRef lastBlock = sourceBlock;

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

        Arrays.sort(positiveSamples, 0, positiveSamplesIncludingDirect);
        double median = medianSorted(positiveSamples, positiveSamplesIncludingDirect);
        double selectedOcclusion = Math.max(OCCLUSION_EPSILON, Math.min(directOcclusion, median));
        String decisionKind;
        if (positiveVariationSamples == 0) {
            decisionKind = "direct_blocked_authoritative";
        } else {
            decisionKind = selectedOcclusion == directOcclusion ? "direct" : "median_positive";
        }
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
        return getConfiguredBlockOcclusion(sourceBlockState) <= OCCLUSION_EPSILON;
    }

    private static void recordSourceBlockSelfOcclusionSkipped(
            AcousticScene scene,
            AcousticBlockRef sourceBlock,
            Vec3 soundPos,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        if (shouldIgnoreSourceBlock(sourceBlock)) {
            return;
        }
        float sourceBlockOcclusion = getConfiguredBlockOcclusion(sourceBlock.blockState());
        if (sourceBlockOcclusion <= OCCLUSION_EPSILON) {
            return;
        }

        boolean blockSound = isMinecraftBlockSound(sound);
        boolean stepOrBlockEvent = isStepOrBlockEventSource(category, sound);
        boolean strictInside = pointInsideCollisionShape(scene, sourceBlock, soundPos);

        if (blockSound) {
            SoundPhysicsTrace.recordSourceBlockSelfOcclusionSkippedBlockSound();
        }
        if (stepOrBlockEvent) {
            SoundPhysicsTrace.recordSourceBlockSelfOcclusionSkippedStepOrBlockEvent();
        }
        if (!strictInside) {
            SoundPhysicsTrace.recordSourceBlockSelfOcclusionSkippedBoundary();
        }

        Loggers.logOcclusion(
                "Source block self-occlusion skipped block={} occlusion={} sound={} category={} soundPos={} blockSound={} stepOrBlockEvent={} strictInside={}",
                blockId(sourceBlock.blockState()),
                sourceBlockOcclusion,
                sound,
                category,
                soundPos,
                blockSound,
                stepOrBlockEvent,
                strictInside
        );
    }

    static boolean shouldBypassCachedOverloadFallback(@Nullable SoundSource category, @Nullable ResourceLocation sound) {
        if (isMinecraftBlockSound(sound) && isStepOrBlockEventSound(sound)) {
            return true;
        }
        return category == SoundSource.PLAYERS && isPlayerStepOrFallSound(sound);
    }

    private static boolean isStepOrBlockEventSource(@Nullable SoundSource category, @Nullable ResourceLocation sound) {
        return isStepOrBlockEventSound(sound) || (category == SoundSource.PLAYERS && isPlayerStepOrFallSound(sound));
    }

    private static boolean isMinecraftBlockSound(@Nullable ResourceLocation sound) {
        return sound != null && "minecraft".equals(sound.getNamespace()) && sound.getPath().startsWith("block.");
    }

    private static boolean isStepOrBlockEventSound(@Nullable ResourceLocation sound) {
        if (sound == null) {
            return false;
        }
        String soundId = sound.toString().toLowerCase(Locale.ROOT);
        return soundId.contains(".step")
                || soundId.contains(".place")
                || soundId.contains(".break")
                || soundId.contains(".hit")
                || soundId.contains(".fall");
    }

    private static boolean isPlayerStepOrFallSound(@Nullable ResourceLocation sound) {
        if (sound == null) {
            return false;
        }
        String soundId = sound.toString().toLowerCase(Locale.ROOT);
        return soundId.contains(".step") || soundId.contains(".fall");
    }

    private static void recordNonStrictOcclusionSelection(NonStrictOcclusionSelection selection) {
        if (selection.zeroSamples() > 0) {
            SoundPhysicsTrace.recordNonStrictZeroOutlierIgnored(selection.zeroSamples());
        }

        if ("direct".equals(selection.decisionKind())
                || "direct_open".equals(selection.decisionKind())
                || "direct_blocked_authoritative".equals(selection.decisionKind())) {
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

    private static void logOcclusionInvariantIfNeeded(int sourceID, AcousticScene scene, @Nullable ResourceLocation sound, @Nullable SoundSource category, Vec3 soundPos, Vec3 playerPos, double directOcclusion, double selectedOcclusion, double[] variationSamples, NonStrictOcclusionSelection selection) {
        if (directOcclusion <= OCCLUSION_EPSILON || selectedOcclusion > OCCLUSION_EPSILON) {
            return;
        }

        SourceBlockDiagnostics sourceBlock = sourceBlockDiagnostics(scene, soundPos);
        Loggers.error(
                "SPRA OCCLUSION INVARIANT VIOLATION: blocked direct selected open source={} sound={} category={} soundPos={} playerPos={} direct={} selected={} variationSamples={} sourceBlock={} sourceBlockOcclusion={} decision={}",
                sourceID,
                sound,
                category,
                soundPos,
                playerPos,
                directOcclusion,
                selectedOcclusion,
                Arrays.toString(variationSamples),
                sourceBlock.blockId(),
                sourceBlock.occlusion(),
                selection.decisionKind()
        );
    }

    private static void logRawOcclusionTraceIfSuspicious(int sourceID, AcousticScene scene, @Nullable ResourceLocation sound, @Nullable SoundSource category, Vec3 soundPos, Vec3 playerPos, double directOcclusion, double selectedOcclusion, double[] variationSamples, NonStrictOcclusionSelection selection) {
        if (selectedOcclusion > OCCLUSION_EPSILON) {
            return;
        }
        if (soundPos.distanceToSqr(playerPos) <= 1D) {
            return;
        }
        if (!DiagnosticRuntimeOverrides.traceLoggingEnabled(SoundPhysicsMod.CONFIG)) {
            return;
        }
        if (!isSuspiciousRawOcclusionSound(sound, category)) {
            return;
        }

        SourceBlockDiagnostics sourceBlock = sourceBlockDiagnostics(scene, soundPos);
        Loggers.warn(
                "SPRA RAW_OCCLUSION_TRACE sound={} category={} source={} direct={} selected={} samples={} sourceBlock={} sourceBlockOcclusion={} playerPos={} soundPos={} decision={}",
                sound,
                category,
                sourceID,
                directOcclusion,
                selectedOcclusion,
                Arrays.toString(variationSamples),
                sourceBlock.blockId(),
                sourceBlock.occlusion(),
                playerPos,
                soundPos,
                selection.decisionKind()
        );
    }

    private static boolean isSuspiciousRawOcclusionSound(@Nullable ResourceLocation sound, @Nullable SoundSource category) {
        if (category == SoundSource.NEUTRAL) {
            return true;
        }
        if (sound == null) {
            return false;
        }
        String soundId = sound.toString();
        return soundId.contains("chicken") || soundId.contains(":entity.");
    }

    private static SourceBlockDiagnostics sourceBlockDiagnostics(AcousticScene scene, Vec3 soundPos) {
        AcousticBlockRef sourceBlock = scene.blockAt(soundPos);
        BlockState sourceBlockState = sourceBlock.blockState();
        return new SourceBlockDiagnostics(blockId(sourceBlockState), getConfiguredBlockOcclusion(sourceBlockState));
    }

    private static String blockId(BlockState blockState) {
        return BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
    }

    private static float getConfiguredBlockOcclusion(BlockState blockState) {
        if (SoundPhysicsMod.OCCLUSION_CONFIG == null) {
            return 0F;
        }
        return SoundPhysicsMod.OCCLUSION_CONFIG.getBlockDefinitionValue(blockState);
    }

    static boolean pointInsideCollisionShape(AcousticScene scene, AcousticBlockRef blockRef, Vec3 worldPosition) {
        VoxelShape shape = blockRef.blockState().getCollisionShape(blockRef.space(), blockRef.pos(), CollisionContext.empty());
        if (shape.isEmpty()) {
            return false;
        }

        Vec3 localPosition = scene.toLocalPosition(blockRef, worldPosition);
        double shapeX = localPosition.x - blockRef.pos().getX();
        double shapeY = localPosition.y - blockRef.pos().getY();
        double shapeZ = localPosition.z - blockRef.pos().getZ();
        for (AABB box : shape.toAabbs()) {
            if (containsWithEpsilon(box, shapeX, shapeY, shapeZ)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWithEpsilon(AABB box, double x, double y, double z) {
        return x > box.minX + RAY_ORIGIN_ADVANCE_EPSILON
                && x < box.maxX - RAY_ORIGIN_ADVANCE_EPSILON
                && y > box.minY + RAY_ORIGIN_ADVANCE_EPSILON
                && y < box.maxY - RAY_ORIGIN_ADVANCE_EPSILON
                && z > box.minZ + RAY_ORIGIN_ADVANCE_EPSILON
                && z < box.maxZ - RAY_ORIGIN_ADVANCE_EPSILON;
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

    private record SourceBlockDiagnostics(String blockId, float occlusion) {
    }

    static record EnvironmentParameters(
            float sendGain0,
            float sendGain1,
            float sendGain2,
            float sendGain3,
            float sendCutoff0,
            float sendCutoff1,
            float sendCutoff2,
            float sendCutoff3,
            float directCutoff,
            float directGain,
            float airAbsorption
    ) {
    }

    static record SourceFilterReadback(
            boolean available,
            int directFilter,
            int filterType,
            float gain,
            float gainHF,
            int sourceState
    ) {
        static SourceFilterReadback unavailable() {
            return new SourceFilterReadback(false, 0, 0, 1F, 1F, 0);
        }

        static SourceFilterReadback noFilter(int sourceState) {
            return new SourceFilterReadback(true, 0, 0, 1F, 1F, sourceState);
        }

        static SourceFilterReadback lowpass(int directFilter, float gain, float gainHF, int sourceState) {
            return new SourceFilterReadback(true, directFilter, EXTEfx.AL_FILTER_LOWPASS, gain, gainHF, sourceState);
        }

        boolean raw() {
            return available
                    && (directFilter == 0
                    || filterType != EXTEfx.AL_FILTER_LOWPASS
                    || (gain >= RAW_DIRECT_FILTER_THRESHOLD && gainHF >= RAW_DIRECT_FILTER_THRESHOLD));
        }

        boolean muffled() {
            return available
                    && directFilter != 0
                    && filterType == EXTEfx.AL_FILTER_LOWPASS
                    && (gain < RAW_DIRECT_FILTER_THRESHOLD || gainHF < RAW_DIRECT_FILTER_THRESHOLD);
        }
    }

    private record SourceStartState(
            Vec3 position,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            SoundPhysicsSoundPolicy.SoundContext context,
            @Nullable EnvironmentParameters appliedEnvironment
    ) {
        static SourceStartState create(
                Vec3 position,
                @Nullable SoundSource category,
                @Nullable ResourceLocation sound,
                SoundPhysicsSoundPolicy.SoundContext context
        ) {
            return new SourceStartState(position, category, sound, context, null);
        }

        SourceStartState withAppliedEnvironment(EnvironmentParameters environment) {
            return new SourceStartState(position, category, sound, context, environment);
        }

        boolean matches(@Nullable SoundSource requestedCategory, @Nullable ResourceLocation requestedSound) {
            return category == requestedCategory && Objects.equals(sound, requestedSound);
        }
    }

    private enum EnvironmentApplicationKind {
        FULL_ENVIRONMENT,
        OVERLOAD_NEAREST,
        OVERLOAD_DIRECT_ONLY
    }

    private record LastAppliedEnvironment(
            EnvironmentParameters environment,
            Vec3 position,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            long gameTime,
            long createdNanos,
            EnvironmentApplicationKind kind
    ) {
        boolean matches(Vec3 requestedPosition, @Nullable SoundSource requestedCategory, @Nullable ResourceLocation requestedSound) {
            return category == requestedCategory
                    && Objects.equals(sound, requestedSound)
                    && position.distanceToSqr(requestedPosition) <= FRESH_FULL_ENVIRONMENT_POSITION_TOLERANCE_SQR;
        }

        boolean fresh(long nowGameTime, long nowNanos) {
            return freshByNanos(nowNanos) || freshByGameTime(nowGameTime);
        }

        private boolean freshByNanos(long nowNanos) {
            long ageNanos = nowNanos - createdNanos;
            return ageNanos >= 0L && ageNanos <= FRESH_FULL_ENVIRONMENT_WINDOW_NANOS;
        }

        private boolean freshByGameTime(long nowGameTime) {
            if (gameTime == Long.MIN_VALUE || nowGameTime == Long.MIN_VALUE) {
                return false;
            }
            long ageTicks = nowGameTime - gameTime;
            return ageTicks >= 0L && ageTicks <= FRESH_FULL_ENVIRONMENT_WINDOW_TICKS;
        }
    }

    private record AttachedDirectFilter(int directFilter, EnvironmentParameters environment) {
    }

    private record SourceEfxState(int directFilter) {
    }

    private record AcousticEnvironmentSnapshot(
            float sendGain0,
            float sendGain1,
            float sendGain2,
            float sendGain3,
            float sendCutoff0,
            float sendCutoff1,
            float sendCutoff2,
            float sendCutoff3,
            float directCutoff,
            float directGain,
            float airAbsorption,
            Vec3 sourcePosition,
            @Nullable ResourceLocation sound,
            @Nullable SoundSource category,
            long gameTime,
            long createdNanos
    ) {
        static AcousticEnvironmentSnapshot create(
                EnvironmentParameters environment,
                Vec3 sourcePosition,
                @Nullable ResourceLocation sound,
                @Nullable SoundSource category,
                long gameTime
        ) {
            return new AcousticEnvironmentSnapshot(
                    environment.sendGain0(),
                    environment.sendGain1(),
                    environment.sendGain2(),
                    environment.sendGain3(),
                    environment.sendCutoff0(),
                    environment.sendCutoff1(),
                    environment.sendCutoff2(),
                    environment.sendCutoff3(),
                    environment.directCutoff(),
                    environment.directGain(),
                    environment.airAbsorption(),
                    sourcePosition,
                    sound,
                    category,
                    gameTime,
                    System.nanoTime()
            );
        }

        EnvironmentParameters environment() {
            return new EnvironmentParameters(sendGain0, sendGain1, sendGain2, sendGain3, sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3, directCutoff, directGain, airAbsorption);
        }

        boolean matchesExact(@Nullable ResourceLocation requestedSound, @Nullable SoundSource requestedCategory) {
            return matchesCategory(requestedCategory) && (sound == null ? requestedSound == null : sound.equals(requestedSound));
        }

        boolean matchesCategory(@Nullable SoundSource requestedCategory) {
            return category == requestedCategory;
        }

        long ageTicks(long nowGameTime) {
            if (gameTime == Long.MIN_VALUE || nowGameTime == Long.MIN_VALUE) {
                return 0L;
            }
            return nowGameTime - gameTime;
        }
    }

    private record SnapshotMatch(AcousticEnvironmentSnapshot snapshot, double distance, long ageTicks) {
    }

    interface EnvironmentBackend {
        boolean apply(int sourceID, EnvironmentParameters environment, @Nullable SoundSource category, @Nullable ResourceLocation sound);
    }

    interface SourceFilterReadbackBackend {
        SourceFilterReadback read(int sourceID, @Nullable SoundSource category, @Nullable ResourceLocation sound);
    }

    interface DirectFilterBackend {
        int createLowpassFilter();

        void setLowpass(int filter, float gain, float gainHF);

        void attachDirectFilter(int sourceID, int filter);

        void deleteFilter(int filter);

        SourceFilterReadback readFilterObject(int directFilter, int sourceState);
    }

    private static final class OpenAlDirectFilterBackend implements DirectFilterBackend {

        @Override
        public int createLowpassFilter() {
            int filter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            Loggers.logALError("Create per-source direct filter:");
            Loggers.logDebug("per-source direct filter: {}", filter);
            return filter;
        }

        @Override
        public void setLowpass(int filter, float gain, float gainHF) {
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gainHF);
        }

        @Override
        public void attachDirectFilter(int sourceID, int filter) {
            AL11.alSourcei(sourceID, EXTEfx.AL_DIRECT_FILTER, filter);
        }

        @Override
        public void deleteFilter(int filter) {
            SoundPhysics.deleteFilter(filter);
        }

        @Override
        public SourceFilterReadback readFilterObject(int directFilter, int sourceState) {
            int filterType = EXTEfx.alGetFilteri(directFilter, EXTEfx.AL_FILTER_TYPE);
            Loggers.logALError("Read direct filter type");
            float gain = 1F;
            float gainHF = 1F;
            if (filterType == EXTEfx.AL_FILTER_LOWPASS) {
                gain = EXTEfx.alGetFilterf(directFilter, EXTEfx.AL_LOWPASS_GAIN);
                Loggers.logALError("Read direct filter gain");
                gainHF = EXTEfx.alGetFilterf(directFilter, EXTEfx.AL_LOWPASS_GAINHF);
                Loggers.logALError("Read direct filter gainHF");
            }
            return new SourceFilterReadback(true, directFilter, filterType, gain, gainHF, sourceState);
        }
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

    private static void storeAcousticEnvironmentSnapshot(
            EnvironmentParameters environment,
            Vec3 sourcePosition,
            @Nullable ResourceLocation sound,
            @Nullable SoundSource category,
            long gameTime
    ) {
        AcousticEnvironmentSnapshot snapshot = AcousticEnvironmentSnapshot.create(environment, sourcePosition, sound, category, gameTime);
        synchronized (ACOUSTIC_ENVIRONMENT_SNAPSHOTS) {
            ACOUSTIC_ENVIRONMENT_SNAPSHOTS.addLast(snapshot);
            while (ACOUSTIC_ENVIRONMENT_SNAPSHOTS.size() > ACOUSTIC_ENVIRONMENT_CACHE_MAX_SIZE) {
                ACOUSTIC_ENVIRONMENT_SNAPSHOTS.removeFirst();
            }
        }
    }

    static void storeEnvironmentSnapshotForTests(
            EnvironmentParameters environment,
            Vec3 sourcePosition,
            @Nullable ResourceLocation sound,
            @Nullable SoundSource category,
            long gameTime
    ) {
        storeAcousticEnvironmentSnapshot(environment, sourcePosition, sound, category, gameTime);
    }

    @Nullable
    private static SnapshotMatch findNearestEnvironmentSnapshot(Vec3 sourcePosition, @Nullable SoundSource category, @Nullable ResourceLocation sound, long gameTime) {
        @Nullable SnapshotMatch exact = null;
        @Nullable SnapshotMatch categoryMatch = null;
        synchronized (ACOUSTIC_ENVIRONMENT_SNAPSHOTS) {
            for (AcousticEnvironmentSnapshot snapshot : ACOUSTIC_ENVIRONMENT_SNAPSHOTS) {
                @Nullable SnapshotMatch match = matchSnapshot(snapshot, sourcePosition, category, gameTime);
                if (match == null) {
                    continue;
                }
                if (snapshot.matchesExact(sound, category)) {
                    exact = nearestSnapshot(exact, match);
                    continue;
                }
                if (snapshot.matchesCategory(category)) {
                    categoryMatch = nearestSnapshot(categoryMatch, match);
                }
            }
        }
        return exact != null ? exact : categoryMatch;
    }

    @Nullable
    private static SnapshotMatch findNearestExactEnvironmentSnapshot(Vec3 sourcePosition, @Nullable SoundSource category, @Nullable ResourceLocation sound, long gameTime) {
        @Nullable SnapshotMatch exact = null;
        synchronized (ACOUSTIC_ENVIRONMENT_SNAPSHOTS) {
            for (AcousticEnvironmentSnapshot snapshot : ACOUSTIC_ENVIRONMENT_SNAPSHOTS) {
                if (!snapshot.matchesExact(sound, category)) {
                    continue;
                }
                @Nullable SnapshotMatch match = matchSnapshotByDistance(snapshot, sourcePosition, gameTime);
                if (match != null) {
                    exact = nearestSnapshot(exact, match);
                }
            }
        }
        return exact;
    }

    @Nullable
    private static SnapshotMatch matchSnapshot(AcousticEnvironmentSnapshot snapshot, Vec3 sourcePosition, @Nullable SoundSource category, long gameTime) {
        if (!snapshot.matchesCategory(category)) {
            return null;
        }
        return matchSnapshotByDistance(snapshot, sourcePosition, gameTime);
    }

    @Nullable
    private static SnapshotMatch matchSnapshotByDistance(AcousticEnvironmentSnapshot snapshot, Vec3 sourcePosition, long gameTime) {
        long ageTicks = snapshot.ageTicks(gameTime);
        if (ageTicks < 0L || ageTicks > ACOUSTIC_ENVIRONMENT_CACHE_TTL_TICKS) {
            return null;
        }
        double distanceSqr = snapshot.sourcePosition().distanceToSqr(sourcePosition);
        if (distanceSqr > ACOUSTIC_ENVIRONMENT_CACHE_RADIUS_SQR) {
            return null;
        }
        return new SnapshotMatch(snapshot, Math.sqrt(distanceSqr), ageTicks);
    }

    @Nullable
    private static SnapshotMatch findNearestExactPreplayFallbackSnapshot(
            Vec3 sourcePosition,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            long gameTime
    ) {
        @Nullable SnapshotMatch nearest = null;
        synchronized (ACOUSTIC_ENVIRONMENT_SNAPSHOTS) {
            for (AcousticEnvironmentSnapshot snapshot : ACOUSTIC_ENVIRONMENT_SNAPSHOTS) {
                if (!snapshot.matchesExact(sound, category) || !isMuffledEnvironment(snapshot.environment())) {
                    continue;
                }
                long ageTicks = snapshot.ageTicks(gameTime);
                if (ageTicks < 0L || ageTicks > PREPLAY_FALLBACK_CACHE_TTL_TICKS) {
                    continue;
                }
                double distanceSqr = snapshot.sourcePosition().distanceToSqr(sourcePosition);
                if (distanceSqr > PREPLAY_FALLBACK_CACHE_RADIUS_SQR) {
                    continue;
                }
                nearest = nearestSnapshot(nearest, new SnapshotMatch(snapshot, Math.sqrt(distanceSqr), ageTicks));
            }
        }
        return nearest;
    }

    private static SnapshotMatch nearestSnapshot(@Nullable SnapshotMatch current, SnapshotMatch candidate) {
        if (current == null || candidate.distance() < current.distance()) {
            return candidate;
        }
        if (candidate.distance() == current.distance() && candidate.ageTicks() < current.ageTicks()) {
            return candidate;
        }
        return current;
    }

    private static void recordSourceStartEnvironmentApplied(
            int sourceID,
            EnvironmentParameters environment,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        @Nullable SourceStartState state = SOURCE_START_STATES.get(sourceID);
        if (state == null || !state.matches(category, sound)) {
            return;
        }

        SOURCE_START_STATES.put(sourceID, state.withAppliedEnvironment(environment));
        logSourceFilterReadbackIfActive("afterSetEnvironment", sourceID, category, sound);
    }

    @Nullable
    private static EnvironmentParameters applyPreplayFallback(
            int sourceID,
            Vec3 position,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        long gameTime = currentClientGameTime();
        @Nullable SnapshotMatch match = findNearestExactPreplayFallbackSnapshot(position, category, sound, gameTime);
        if (match == null) {
            SoundPhysicsTrace.recordPreplayFallbackSkippedNoSnapshot();
            return null;
        }

        EnvironmentParameters environment = preplayFallbackEnvironment(match.snapshot().environment());
        if (applyEnvironmentToSource(sourceID, environment, category, sound)) {
            SoundPhysicsTrace.recordPreplayFallbackApplied();
            return environment;
        }
        return null;
    }

    private static EnvironmentParameters preplayFallbackEnvironment(EnvironmentParameters cached) {
        return new EnvironmentParameters(
                cached.sendGain0(),
                cached.sendGain1(),
                cached.sendGain2(),
                cached.sendGain3(),
                cached.sendCutoff0(),
                cached.sendCutoff1(),
                cached.sendCutoff2(),
                cached.sendCutoff3(),
                cached.directCutoff(),
                Math.max(cached.directGain(), PREPLAY_FALLBACK_DIRECT_GAIN_FLOOR),
                cached.airAbsorption()
        );
    }

    private static boolean isPreplayGuardEligible(
            @Nullable Vec3 position,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            SoundPhysicsSoundPolicy.SoundContext context
    ) {
        if (position == null || category == null || sound == null) {
            return false;
        }
        if (!DiagnosticRuntimeOverrides.soundPhysicsEnabled(SoundPhysicsMod.CONFIG)) {
            return false;
        }
        if (!context.startEvent()
                || context.relative()
                || context.noAttenuation()
                || context.streaming()
                || context.tickable()) {
            return false;
        }
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context) || SoundPhysicsSoundPolicy.isRecord(context)) {
            return false;
        }
        if (category == SoundSource.MUSIC
                || category == SoundSource.RECORDS
                || category == SoundSource.MASTER
                || category == SoundSource.VOICE
                || category == SoundSource.AMBIENT) {
            return false;
        }
        return isFocusedEntityStart(category, sound);
    }

    private static boolean isPreplayFallbackEnabledFor(
            int sourceID,
            @Nullable Vec3 position,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            SoundPhysicsSoundPolicy.SoundContext context
    ) {
        if (!preplayFallbackEnabled() || !sourceStartDiagnosticsActive()) {
            return false;
        }
        if (!isPreplayGuardEligible(position, category, sound, context) || !isChickenPreplayFallbackSound(sound)) {
            return false;
        }
        @Nullable SourceStartState state = SOURCE_START_STATES.get(sourceID);
        return state != null
                && state.matches(category, sound)
                && state.appliedEnvironment() == null;
    }

    private static boolean isMuffledEnvironment(@Nullable EnvironmentParameters environment) {
        return environment != null
                && (environment.directCutoff() < RAW_DIRECT_FILTER_THRESHOLD
                || environment.directGain() < RAW_DIRECT_FILTER_THRESHOLD);
    }

    private static boolean preplayFallbackEnabled() {
        return SoundPhysicsMod.CONFIG != null && SoundPhysicsMod.CONFIG.soundPhysicsPreplayFallbackEnabled.get();
    }

    private static boolean isChickenPreplayFallbackSound(@Nullable ResourceLocation sound) {
        return sound != null && sound.toString().toLowerCase(Locale.ROOT).contains("minecraft:entity.chicken");
    }

    private static boolean isFocusedEntityStart(@Nullable SoundSource category, @Nullable ResourceLocation sound) {
        String soundText = sound == null ? "" : sound.toString().toLowerCase(Locale.ROOT);
        return soundText.contains("chicken")
                || soundText.contains("entity.")
                || category == SoundSource.NEUTRAL
                || category == SoundSource.HOSTILE
                || category == SoundSource.PLAYERS;
    }

    private static boolean sourceStartDiagnosticsActive() {
        return DiagnosticRuntimeOverrides.isRootDebug() || DiagnosticRuntimeOverrides.traceLoggingEnabled(SoundPhysicsMod.CONFIG);
    }

    private static boolean shouldLogSourceFilterReadback(@Nullable SoundSource category, @Nullable ResourceLocation sound) {
        return sourceStartDiagnosticsActive() && isFocusedEntityStart(category, sound);
    }

    private static void logSourceFilterReadbackIfActive(
            String phase,
            int sourceID,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        if (!shouldLogSourceFilterReadback(category, sound)) {
            return;
        }
        logSourceFilterReadbackIfActive(phase, sourceID, category, sound, readSourceFilterReadback(sourceID, category, sound));
    }

    private static void logSourceFilterReadbackIfActive(
            String phase,
            int sourceID,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            SourceFilterReadback readback
    ) {
        if (!shouldLogSourceFilterReadback(category, sound)) {
            return;
        }
        Loggers.log(
                "SPRA SOURCE_FILTER_READBACK phase={} source={} sound={} directFilter={} gain={} gainHF={} sourceState={} readbackReliable=false",
                phase,
                sourceID,
                sound,
                readback.directFilter(),
                readback.gain(),
                readback.gainHF(),
                readback.sourceState()
        );
    }

    private static void logPositionalStartTraceIfActive(
            int sourceID,
            @Nullable Vec3 position,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound,
            @Nullable EnvironmentParameters preplayEnvironment,
            SourceFilterReadback readback
    ) {
        if (position == null || !shouldLogSourceFilterReadback(category, sound)) {
            return;
        }

        Vec3 listenerPos = Vec3.ZERO;
        Vec3 listenerForward = Vec3.ZERO;
        Vec3 listenerRight = Vec3.ZERO;
        if (minecraft != null && minecraft.gameRenderer != null) {
            var camera = minecraft.gameRenderer.getMainCamera();
            if (camera != null) {
                listenerPos = camera.getPosition();
                listenerForward = new Vec3(camera.getLookVector()).normalize();
                listenerRight = new Vec3(camera.getLeftVector()).scale(-1D).normalize();
            }
        }

        Vec3 toSource = position.subtract(listenerPos);
        double distance = Math.sqrt(toSource.lengthSqr());
        double rightDot = distance <= 1.0E-12D || listenerRight.lengthSqr() <= 1.0E-12D
                ? 0.0D
                : toSource.normalize().dot(listenerRight);
        float directCutoff = preplayEnvironment == null ? readback.gainHF() : preplayEnvironment.directCutoff();
        float directGain = preplayEnvironment == null ? readback.gain() : preplayEnvironment.directGain();
        Loggers.log(
                "SPRA POSITIONAL_START_TRACE source={} sound={} pos={} listenerPos={} listenerForward={} listenerRight={} rightDot={} distance={} fallbackApplied={} directCutoff={} directGain={}",
                sourceID,
                sound,
                position,
                listenerPos,
                listenerForward,
                listenerRight,
                rightDot,
                distance,
                preplayEnvironment != null,
                directCutoff,
                directGain
        );
    }

    private static SourceFilterReadback readSourceFilterReadback(
            int sourceID,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        SourceFilterReadback trackedReadback = trackedSourceFilterReadback(sourceID);
        if (trackedReadback.available()) {
            return trackedReadback;
        }

        try {
            SourceFilterReadback readback = sourceFilterReadbackBackend.read(sourceID, category, sound);
            if (readback != null && readback.available()) {
                return readback;
            }
        } catch (Throwable throwable) {
            Loggers.logTrace("Source filter readback failed source={} sound={} error={}", sourceID, sound, throwable.getMessage());
        }

        return SourceFilterReadback.unavailable();
    }

    private static SourceFilterReadback trackedSourceFilterReadback(int sourceID) {
        @Nullable AttachedDirectFilter attached = ATTACHED_DIRECT_FILTERS.get(sourceID);
        if (attached == null || attached.environment() == null) {
            return SourceFilterReadback.unavailable();
        }
        if (attached.directFilter() > 0) {
            SourceFilterReadback trackedFilterReadback = readOpenAlFilterObject(attached.directFilter(), 0);
            if (trackedFilterReadback.available()) {
                return trackedFilterReadback;
            }
        }

        int trackedFilter = attached.directFilter() == 0 ? -1 : attached.directFilter();
        return SourceFilterReadback.lowpass(
                trackedFilter,
                attached.environment().directGain(),
                attached.environment().directCutoff(),
                0
        );
    }

    private static SourceFilterReadback readOpenAlSourceFilter(
            int sourceID,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        if (!AudioSourceRecovery.safeSourceExists(sourceID, category, sound, "read source direct filter")) {
            deleteSourceEfxState(sourceID);
            return SourceFilterReadback.unavailable();
        }

        try {
            int directFilter = AL10.alGetSourcei(sourceID, EXTEfx.AL_DIRECT_FILTER);
            Loggers.logALError("Read source direct filter");
            int sourceState = AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE);
            Loggers.logALError("Read source state");
            if (directFilter == 0) {
                return SourceFilterReadback.noFilter(sourceState);
            }

            return readOpenAlFilterObject(directFilter, sourceState);
        } catch (Throwable throwable) {
            Loggers.logTrace("OpenAL source filter readback failed source={} sound={} error={}", sourceID, sound, throwable.getMessage());
            return SourceFilterReadback.unavailable();
        }
    }

    private static SourceFilterReadback readOpenAlFilterObject(int directFilter, int sourceState) {
        try {
            return directFilterBackend.readFilterObject(directFilter, sourceState);
        } catch (Throwable throwable) {
            Loggers.logTrace("OpenAL direct filter object readback failed filter={} error={}", directFilter, throwable.getMessage());
            return SourceFilterReadback.unavailable();
        }
    }

    private static void recordOverloadFallbackReadback(
            int sourceID,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        SourceFilterReadback readback = readSourceFilterReadback(sourceID, category, sound);
        if (readback.available()) {
            SoundPhysicsTrace.recordOverloadFallbackReadback(readback.raw(), readback.muffled());
        }
        Loggers.log(
                "SPRA OVERLOAD_FALLBACK_READBACK source={} sound={} directFilter={} gain={} gainHF={} sourceState={} readbackReliable=false",
                sourceID,
                sound,
                readback.directFilter(),
                readback.gain(),
                readback.gainHF(),
                readback.sourceState()
        );
    }

    private static boolean applyEnvironmentToSource(
            int sourceID,
            EnvironmentParameters environment,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        boolean applied = environmentBackend.apply(sourceID, environment, category, sound);
        if (applied) {
            ATTACHED_DIRECT_FILTERS.put(sourceID, new AttachedDirectFilter(directFilterForSource(sourceID), environment));
            recordSourceStartEnvironmentApplied(sourceID, environment, category, sound);
        }
        return applied;
    }

    static void setEnvironmentBackendForTests(EnvironmentBackend backend) {
        environmentBackend = backend == null ? OPEN_AL_ENVIRONMENT_BACKEND : backend;
    }

    static void resetEnvironmentBackendForTests() {
        environmentBackend = OPEN_AL_ENVIRONMENT_BACKEND;
    }

    static void setSourceFilterReadbackBackendForTests(SourceFilterReadbackBackend backend) {
        sourceFilterReadbackBackend = backend == null ? OPEN_AL_SOURCE_FILTER_READBACK_BACKEND : backend;
    }

    static void resetSourceFilterReadbackBackendForTests() {
        sourceFilterReadbackBackend = OPEN_AL_SOURCE_FILTER_READBACK_BACKEND;
        SOURCE_START_STATES.clear();
        ATTACHED_DIRECT_FILTERS.clear();
        LAST_APPLIED_ENVIRONMENTS.clear();
    }

    static void setDirectFilterBackendForTests(DirectFilterBackend backend) {
        directFilterBackend = backend == null ? OPEN_AL_DIRECT_FILTER_BACKEND : backend;
    }

    static void resetDirectFilterBackendForTests() {
        SOURCE_EFX_STATES.clear();
        ATTACHED_DIRECT_FILTERS.clear();
        directFilterBackend = OPEN_AL_DIRECT_FILTER_BACKEND;
        perSourceDirectFiltersCreated.set(0L);
        perSourceDirectFiltersDeleted.set(0L);
        sharedDirectFilterWrites.set(0L);
    }

    static SourceFilterReadback readSourceFilterReadbackForTests(int sourceID) {
        return readSourceFilterReadback(sourceID, null, null);
    }

    static boolean applyDirectFilterForTests(int sourceID, EnvironmentParameters environment) {
        boolean applied = applyPerSourceDirectFilter(sourceID, environment, null, null);
        if (applied) {
            ATTACHED_DIRECT_FILTERS.put(sourceID, new AttachedDirectFilter(directFilterForSource(sourceID), environment));
        }
        return applied;
    }

    static void resetOverloadFallbackForTests() {
        synchronized (ACOUSTIC_ENVIRONMENT_SNAPSHOTS) {
            ACOUSTIC_ENVIRONMENT_SNAPSHOTS.clear();
        }
        SOURCE_START_STATES.clear();
        ATTACHED_DIRECT_FILTERS.clear();
        LAST_APPLIED_ENVIRONMENTS.clear();
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
        applyEnvironmentToSource(sourceID, new EnvironmentParameters(sendGain0, sendGain1, sendGain2, sendGain3, sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3, directCutoff, directGain, airAbsorption), lastSoundCategory, lastSound);
    }

    private static int directFilterForSource(int sourceID) {
        SourceEfxState state = SOURCE_EFX_STATES.get(sourceID);
        return state == null ? 0 : state.directFilter();
    }

    private static SourceEfxState getOrCreateSourceEfxState(int sourceID) {
        return SOURCE_EFX_STATES.computeIfAbsent(sourceID, ignored -> {
            int directFilter = directFilterBackend.createLowpassFilter();
            if (directFilter == 0) {
                throw new IllegalStateException("OpenAL returned direct filter 0 for source " + sourceID);
            }
            perSourceDirectFiltersCreated.incrementAndGet();
            return new SourceEfxState(directFilter);
        });
    }

    private static boolean applyPerSourceDirectFilter(
            int sourceID,
            EnvironmentParameters environment,
            @Nullable SoundSource category,
            @Nullable ResourceLocation sound
    ) {
        try {
            SourceEfxState state = getOrCreateSourceEfxState(sourceID);
            int directFilter = state.directFilter();
            directFilterBackend.setLowpass(directFilter, environment.directGain(), environment.directCutoff());
            directFilterBackend.attachDirectFilter(sourceID, directFilter);
            Loggers.logALError("Set environment per-source direct filter:");
            return true;
        } catch (Throwable throwable) {
            Loggers.warn("Failed setting per-source direct filter source={} sound={} category={} error={}", sourceID, sound, category, throwable.getMessage());
            deleteSourceEfxState(sourceID);
            return false;
        }
    }

    private static boolean applyOpenAlEnvironment(int sourceID, EnvironmentParameters environment, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
        if (!DiagnosticRuntimeOverrides.soundPhysicsEnabled(SoundPhysicsMod.CONFIG)) {
            return false;
        }
        if (!AudioSourceRecovery.safeSourceExists(sourceID, category, sound, "setEnvironment")) {
            RecordDiagnostics.markSourceInvalidated(sourceID, "source invalidated after volume/audio change; waiting for new source");
            deleteSourceEfxState(sourceID);
            return false;
        }
        // Set reverb send filter values and set source to send to all reverb fx slots

        if (maxAuxSends >= 4) {
            EXTEfx.alFilterf(sendFilter0, EXTEfx.AL_LOWPASS_GAIN, environment.sendGain0());
            EXTEfx.alFilterf(sendFilter0, EXTEfx.AL_LOWPASS_GAINHF, environment.sendCutoff0());
            AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot0, 3, sendFilter0);
            Loggers.logALError("Set environment filter0:");
        }

        if (maxAuxSends >= 3) {
            EXTEfx.alFilterf(sendFilter1, EXTEfx.AL_LOWPASS_GAIN, environment.sendGain1());
            EXTEfx.alFilterf(sendFilter1, EXTEfx.AL_LOWPASS_GAINHF, environment.sendCutoff1());
            AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot1, 2, sendFilter1);
            Loggers.logALError("Set environment filter1:");
        }

        if (maxAuxSends >= 2) {
            EXTEfx.alFilterf(sendFilter2, EXTEfx.AL_LOWPASS_GAIN, environment.sendGain2());
            EXTEfx.alFilterf(sendFilter2, EXTEfx.AL_LOWPASS_GAINHF, environment.sendCutoff2());
            AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot2, 1, sendFilter2);
            Loggers.logALError("Set environment filter2:");
        }

        if (maxAuxSends >= 1) {
            EXTEfx.alFilterf(sendFilter3, EXTEfx.AL_LOWPASS_GAIN, environment.sendGain3());
            EXTEfx.alFilterf(sendFilter3, EXTEfx.AL_LOWPASS_GAINHF, environment.sendCutoff3());
            AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot3, 0, sendFilter3);
            Loggers.logALError("Set environment filter3:");
        }

        if (!applyPerSourceDirectFilter(sourceID, environment, category, sound)) {
            return false;
        }

        AL11.alSourcef(sourceID, EXTEfx.AL_AIR_ABSORPTION_FACTOR, Math.max(0.0F, environment.airAbsorption()));
        Loggers.logALError("Set environment airAbsorption:");
        return true;
    }

    private static void setSoundPos(int sourceID, Vec3 pos, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
        AudioSourceRecovery.safeSetSource3f(sourceID, AL11.AL_POSITION, (float) pos.x, (float) pos.y, (float) pos.z, category, sound, "set sound position");
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
