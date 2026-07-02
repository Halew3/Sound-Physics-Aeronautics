package com.sonicether.soundphysics.acoustic;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.utils.LevelAccessUtils;
import com.sonicether.soundphysics.world.ClientLevelProxy;

import net.minecraft.client.Minecraft;

public class RootAcousticWorldProvider implements AcousticWorldProvider {

    @Override
    @Nullable
    public AcousticScene createScene(Minecraft client, AcousticSceneContext context) {
        ClientLevelProxy levelProxy = LevelAccessUtils.getClientLevelProxy(client);

        if (levelProxy == null) {
            return null;
        }

        return new RootAcousticScene(new RootAcousticSpace(levelProxy));
    }

}
