package com.sonicether.soundphysics.mixin;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.propeller.PropellerAudioProfileResolver;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.resources.ResourceLocation;

@Mixin(SoundEvent.class)
public abstract class SoundEventMixin {

    @Shadow
    public abstract ResourceLocation getLocation();

    @ModifyConstant(method = "getRange", constant = @Constant(floatValue = 16F), expect = 2)
    private float allowance1(float value) {
        if (!SoundPhysicsMod.CONFIG.enabled.get()) {
            return value;
        }
        return value * SoundPhysicsMod.CONFIG.soundDistanceAllowance.get();
    }

    @Inject(method = "getRange", at = @At("RETURN"), cancellable = true)
    private void propellerLongRange(float volume, CallbackInfoReturnable<Float> cir) {
        if (SoundPhysicsMod.CONFIG == null || !SoundPhysicsMod.CONFIG.enabled.get() || !SoundPhysicsMod.CONFIG.propellerLongRangeEnabled.get()) {
            return;
        }
        SoundPhysicsSoundPolicy.SoundContext context = SoundPhysicsSoundPolicy.SoundContext.of(getLocation(), null);
        if (!SoundPhysicsSoundPolicy.isAeronauticsPropeller(context)) {
            return;
        }
        cir.setReturnValue(Math.max(cir.getReturnValueF(), (float) PropellerAudioProfileResolver.fallbackForContext(getLocation()).computedMaxDistance()));
    }

}
