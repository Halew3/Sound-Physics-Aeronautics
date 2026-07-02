package com.sonicether.soundphysics.doppler;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public interface DopplerKinematicScene {

    DopplerKinematicState listener(Vec3 worldPosition, long gameTime);

    DopplerKinematicState source(int sourceId, Vec3 worldPosition, ResourceLocation sound, SoundSource category, long gameTime);

}
