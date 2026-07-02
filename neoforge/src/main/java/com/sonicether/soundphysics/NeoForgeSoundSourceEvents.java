package com.sonicether.soundphysics;

import java.lang.reflect.Field;

import com.mojang.blaze3d.audio.Channel;
import com.sonicether.soundphysics.doppler.DopplerEngine;
import com.sonicether.soundphysics.mixin.ChannelAccessor;
import com.sonicether.soundphysics.propeller.PropellerLongRangeAudio;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import org.lwjgl.openal.AL10;

final class NeoForgeSoundSourceEvents {

    private NeoForgeSoundSourceEvents() {
    }

    static void onPlaySoundSource(PlaySoundSourceEvent event) {
        processSoundSourceEvent(event.getSound(), event.getChannel(), false);
    }

    static void onPlayStreamingSource(PlayStreamingSourceEvent event) {
        processSoundSourceEvent(event.getSound(), event.getChannel(), true);
    }

    private static void processSoundSourceEvent(SoundInstance sound, Channel channel, boolean streaming) {
        long startNanos = System.nanoTime();
        ResourceLocation soundId = sound.getLocation();
        SoundSource category = sound.getSource();
        boolean relative = sound.isRelative();
        boolean noAttenuation = sound.getAttenuation() == SoundInstance.Attenuation.NONE;
        boolean tickable = sound instanceof TickableSoundInstance;
        String soundInstanceClassName = sound.getClass().getName();
        SoundPhysics.setLastSoundContext(category, soundId, soundInstanceClassName, relative, noAttenuation, tickable);

        int sourceId = resolveSourceId(channel);
        if (sourceId <= 0) {
            Loggers.warn("SPR Aeronautics sound fallback could not resolve OpenAL source for {}", soundId);
            SoundPhysicsPerfDiagnostics.recordProcessSound(System.nanoTime() - startNanos);
            return;
        }

        long gameTime = SoundProcessingDeduper.currentGameTime();
        if (!SoundProcessingDeduper.shouldProcessStart(sourceId, gameTime, category, soundId, SoundProcessingDeduper.ProcessingPath.SOUND_ENGINE_FALLBACK)) {
            return;
        }

        Vec3 sourcePosition = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        SoundPhysicsSoundPolicy.SoundContext context = new SoundPhysicsSoundPolicy.SoundContext(soundId, category, soundInstanceClassName, relative, noAttenuation, streaming, true, tickable);
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)) {
            PropellerLongRangeAudio.applySourceRange(sourceId, sound, context, sound.getPitch(), sound.getVolume());
        }
        SoundPhysicsTrace.recordProcessingPath(SoundProcessingDeduper.ProcessingPath.SOUND_ENGINE_FALLBACK, sourceId, soundId);
        SoundPhysics.processSound(sourceId, sourcePosition.x, sourcePosition.y, sourcePosition.z, category, soundId, false, context);
        DopplerEngine.onPlaySource(sourceId, sourcePosition, category, soundId, relative, noAttenuation, soundInstanceClassName, streaming, tickable);
        SoundPhysicsPerfDiagnostics.recordProcessSound(System.nanoTime() - startNanos);
    }

    private static int resolveSourceId(Channel channel) {
        try {
            return ((ChannelAccessor) channel).getSource();
        } catch (Throwable ignored) {
            return reflectSourceId(channel);
        }
    }

    private static int reflectSourceId(Channel channel) {
        int namedSourceCandidate = -1;
        for (Field field : channel.getClass().getDeclaredFields()) {
            if (field.getType() != int.class) {
                continue;
            }

            try {
                field.setAccessible(true);
                int value = field.getInt(channel);
                if ("source".equals(field.getName())) {
                    namedSourceCandidate = value;
                }
                if (value > 0 && AL10.alIsSource(value)) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }
        return namedSourceCandidate;
    }

}
