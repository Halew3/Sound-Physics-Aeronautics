package com.sonicether.soundphysics.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.sonicether.soundphysics.AudioSourceRecovery;
import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysics;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.SoundProcessingDeduper;
import com.sonicether.soundphysics.SoundPhysicsTrace;
import com.sonicether.soundphysics.doppler.DopplerEngine;
import com.sonicether.soundphysics.propeller.PropellerLongRangeAudio;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public class SourceMixin {

    @Shadow
    @Final
    private int source;

    @Unique
    private Vec3 pos;

    @Inject(method = "setSelfPosition", at = @At("HEAD"))
    private void setSelfPosition(Vec3 poss, CallbackInfo ci) {
        this.pos = poss;
        SoundPhysicsTrace.recordChannelSetSelfPosition(source, poss);
    }

    @Inject(method = "play", at = @At("HEAD"))
    private void play(CallbackInfo ci) {
        var context = SoundPhysics.getLastSoundContext();
        var category = context.category();
        var sound = context.soundId();
        SoundPhysicsTrace.recordChannelPlayHead(source, pos, category, sound);
        SoundPhysicsTrace.recordSourceMixinPlay(source, pos, category, sound);
        if (pos == null) {
            return;
        }
        if (category == null || sound == null) {
            Loggers.warn("SourceMixin.play skipped source {} because the last sound context was not captured", source);
            return;
        }
        if (!SoundProcessingDeduper.shouldProcessStart(
                source,
                SoundProcessingDeduper.currentGameTime(),
                category,
                sound,
                SoundProcessingDeduper.ProcessingPath.SOURCE_MIXIN
        )) {
            return;
        }
        SoundPhysicsTrace.recordProcessingPath(SoundProcessingDeduper.ProcessingPath.SOURCE_MIXIN, source, sound);
        SoundPhysicsTrace.recordSourceMixinProcessExpected(source, pos, category, sound);
        SoundPhysics.onPlaySound(pos.x, pos.y, pos.z, source, category, sound, context);
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)) {
            PropellerLongRangeAudio.applyFallbackSourceRange(source, context, 1.6F, 1.0F);
        }
        DopplerEngine.onPlaySource(source, pos, category, sound, context.relative(), context.noAttenuation(), context.soundInstanceClassName(), context.streaming(), context.tickable());
        Loggers.logALError("Sound play injector");
    }

    @ModifyVariable(method = "linearAttenuation", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float injected(float attenuation) {
        if (!SoundPhysicsMod.CONFIG.enabled.get()) {
            return attenuation;
        }
        return attenuation / SoundPhysicsMod.CONFIG.attenuationFactor.get();
    }

    @Inject(method = "linearAttenuation", at = @At("RETURN"))
    private void linearAttenuation2(float attenuation, CallbackInfo ci) {
        AudioSourceRecovery.safeSetSourceFloat(source, AL10.AL_REFERENCE_DISTANCE, attenuation / 2F, null, null, "set reference distance");
    }

}
