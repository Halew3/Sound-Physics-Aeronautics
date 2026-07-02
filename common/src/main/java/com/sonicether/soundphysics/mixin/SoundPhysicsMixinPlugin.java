package com.sonicether.soundphysics.mixin;

import java.util.List;
import java.util.Set;

import com.sonicether.soundphysics.SoundPhysicsTrace;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class SoundPhysicsMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        SoundPhysicsTrace.recordMixinConfigLoaded(mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        SoundPhysicsTrace.recordMixinShouldApply(mixinClassName, targetClassName);
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        SoundPhysicsTrace.recordMixinPreApply(mixinClassName, targetClassName);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        SoundPhysicsTrace.recordMixinPostApply(mixinClassName, targetClassName);
    }

}
