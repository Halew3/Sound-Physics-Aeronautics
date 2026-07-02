package com.sonicether.soundphysics.acoustic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public record AcousticSceneContext(
        int sourceId,
        Vec3 sourcePosition,
        Vec3 listenerPosition,
        SoundSource category,
        ResourceLocation sound
) {
}
