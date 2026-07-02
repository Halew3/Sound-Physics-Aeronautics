package com.sonicether.soundphysics.acoustic;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

public interface AcousticWorldProvider {

    @Nullable
    AcousticScene createScene(Minecraft client, AcousticSceneContext context);

}
