package com.sonicether.soundphysics.doppler;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class RootDopplerKinematicsProvider implements DopplerKinematicsProvider {

    private static final DopplerKinematicScene ROOT_SCENE = new RootDopplerKinematicScene();

    @Override
    public DopplerKinematicScene createScene(Minecraft client) {
        return ROOT_SCENE;
    }

    private static final class RootDopplerKinematicScene implements DopplerKinematicScene {

        @Override
        public DopplerKinematicState listener(Vec3 worldPosition, long gameTime) {
            return DopplerKinematicState.unreliable(worldPosition, "root", 0L);
        }

        @Override
        public DopplerKinematicState source(int sourceId, Vec3 worldPosition, ResourceLocation sound, SoundSource category, long gameTime) {
            return DopplerKinematicState.unreliable(worldPosition, "root", 0L);
        }

    }

}
