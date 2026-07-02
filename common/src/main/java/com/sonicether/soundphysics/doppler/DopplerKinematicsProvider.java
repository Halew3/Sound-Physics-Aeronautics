package com.sonicether.soundphysics.doppler;

import net.minecraft.client.Minecraft;

public interface DopplerKinematicsProvider {

    DopplerKinematicScene createScene(Minecraft client);

}
