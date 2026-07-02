package com.sonicether.soundphysics.mixin;

import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.AudioSourceRecovery;
import com.sonicether.soundphysics.SoundPhysics;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsPolicyDiagnostics;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.SoundProcessingDeduper;
import com.sonicether.soundphysics.SoundPhysicsTrace;
import com.sonicether.soundphysics.doppler.DopplerEngine;
import com.sonicether.soundphysics.propeller.PropellerLongRangeAudio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.Map;

@Mixin(SoundEngine.class)
public class SoundSystemMixin {

    private final Minecraft minecraft = Minecraft.getInstance();

    @Inject(method = "loadLibrary", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Listener;reset()V"))
    private void loadLibrary(CallbackInfo ci) {
        DopplerEngine.onAudioLibraryReset();
        PropellerLongRangeAudio.onAudioLibraryReset();
        SoundPhysics.init("SoundEngine.loadLibrary");
    }

    @Inject(method = "play", at = @At("HEAD"))
    private void traceSoundEnginePlayHead(SoundInstance sound, CallbackInfo ci) {
        SoundPhysicsTrace.recordSoundEnginePlayHead(sound.getLocation(), sound.getSource(), sound.getClass().getName());
        SoundPhysics.setLastSoundContext(sound.getSource(), sound.getLocation(), sound.getClass().getName(), sound.isRelative(), sound.getAttenuation() == SoundInstance.Attenuation.NONE, sound instanceof TickableSoundInstance);
    }

    @Inject(method = "play", at = @At("TAIL"))
    private void traceSoundEnginePlayTail(SoundInstance sound, CallbackInfo ci) {
        SoundPhysicsTrace.recordSoundEnginePlayTail(sound.getLocation(), sound.getSource(), sound.getClass().getName());
    }

    @Inject(method = "play", at = @At(value = "FIELD", target = "Lnet/minecraft/client/sounds/SoundEngine;instanceBySource:Lcom/google/common/collect/Multimap;"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void play(SoundInstance sound, CallbackInfo ci, WeighedSoundEvents weightedSoundSet, ResourceLocation identifier, Sound sound2, float f, float g, SoundSource soundCategory) {
        SoundPhysicsTrace.recordSoundSystemCaptureLastSound(sound.getLocation(), soundCategory);
        SoundPhysics.setLastSoundContext(soundCategory, sound.getLocation(), sound.getClass().getName(), sound.isRelative(), sound.getAttenuation() == SoundInstance.Attenuation.NONE, sound instanceof TickableSoundInstance);
    }

    @Inject(method = "tickNonPaused", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getSoundSourceVolume(Lnet/minecraft/sounds/SoundSource;)F"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void tickNonPaused(CallbackInfo ci, Iterator<?> iterator, Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> map, ChannelAccess.ChannelHandle channelHandle, SoundInstance sound) {
        if (minecraft.level != null
                && !AudioSourceRecovery.sourceUpdatesSuspended(sound.getSource())
                && DopplerEngine.shouldUpdate(minecraft.level.getGameTime(), sound)) {
            long gameTime = minecraft.level.getGameTime();
            Vec3 sourcePosition = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            SoundSource category = sound.getSource();
            ResourceLocation soundId = sound.getLocation();
            boolean relative = sound.isRelative();
            boolean noAttenuation = sound.getAttenuation() == SoundInstance.Attenuation.NONE;
            float basePitch = Mth.clamp(sound.getPitch(), 0.5F, 2.0F);
            boolean tickable = sound instanceof TickableSoundInstance;
            String soundInstanceClassName = sound.getClass().getName();

            channelHandle.execute(channel -> {
                int sourceId = ((ChannelAccessor) channel).getSource();
                DopplerEngine.updateSource(sourceId, sourcePosition, category, soundId, gameTime, relative, noAttenuation, basePitch, tickable, soundInstanceClassName);
            });
        }

        SoundPhysicsSoundPolicy.SoundContext acousticUpdateContext = new SoundPhysicsSoundPolicy.SoundContext(
                sound.getLocation(),
                sound.getSource(),
                sound.getClass().getName(),
                sound.isRelative(),
                sound.getAttenuation() == SoundInstance.Attenuation.NONE,
                false,
                false,
                sound instanceof TickableSoundInstance
        );
        boolean propellerAcousticUpdate = SoundPhysicsSoundPolicy.isKnownPropeller(acousticUpdateContext)
                && !DiagnosticRuntimeOverrides.propellerSafeMode();
        if (SoundPhysicsSoundPolicy.isKnownPropeller(acousticUpdateContext) && DiagnosticRuntimeOverrides.propellerSafeMode()) {
            SoundPhysicsPolicyDiagnostics.recordPropellerSafeSkippedAcousticUpdate();
        }
        boolean validWorldPosition = !(sound.getX() == 0.0D && sound.getY() == 0.0D && sound.getZ() == 0.0D);
        boolean recordAcousticUpdate = validWorldPosition
                && SoundPhysicsSoundPolicy.shouldUpdateRecordAcoustics(SoundPhysicsMod.CONFIG, acousticUpdateContext);

        if (AudioSourceRecovery.sourceUpdatesSuspended(sound.getSource())) {
            AudioSourceRecovery.recordMutedUpdateSkipped(sound.getSource(), sound.getLocation());
            return;
        }

        if (minecraft.level != null && SoundPhysicsSoundPolicy.isKnownPropeller(acousticUpdateContext)) {
            int rangeInterval = Math.max(SoundPhysicsMod.CONFIG.soundUpdateInterval.get(), 1);
            long gameTime = minecraft.level.getGameTime();
            if (Math.floorMod(gameTime + sound.hashCode(), rangeInterval) == 0L) {
                channelHandle.execute(channel -> {
                    int sourceId = ((ChannelAccessor) channel).getSource();
                    PropellerLongRangeAudio.applySourceRange(sourceId, sound, acousticUpdateContext, sound.getPitch(), sound.getVolume());
                });
            }
        }

        if (!SoundPhysicsMod.CONFIG.updateMovingSounds.get() && !propellerAcousticUpdate && !recordAcousticUpdate) {
            return;
        }

        int interval = recordAcousticUpdate
                ? SoundPhysicsMod.CONFIG.recordAcousticUpdateIntervalTicks.get()
                : SoundPhysicsMod.CONFIG.soundUpdateInterval.get();
        if (minecraft.level != null && (minecraft.level.getGameTime() + sound.hashCode()) % Math.max(interval, 1) == 0) {
            long gameTime = minecraft.level.getGameTime();
            channelHandle.execute(channel -> {
                int sourceId = ((ChannelAccessor) channel).getSource();
                if (!AudioSourceRecovery.safeSourceExists(sourceId, sound.getSource(), sound.getLocation(), "moving acoustic update")) {
                    return;
                }
                if (!SoundProcessingDeduper.shouldProcessMovingUpdate(sourceId, gameTime, sound.getSource(), sound.getLocation())) {
                    return;
                }
                SoundPhysicsTrace.recordProcessingPath(SoundProcessingDeduper.ProcessingPath.MOVING_SOUND_UPDATE, sourceId, sound.getLocation());
                SoundPhysics.processSound(
                        sourceId,
                        sound.getX(),
                        sound.getY(),
                        sound.getZ(),
                        sound.getSource(),
                        sound.getLocation(),
                        false,
                        acousticUpdateContext
                );
            });
        }
    }

}
