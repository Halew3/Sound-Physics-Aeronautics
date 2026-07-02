package com.sonicether.soundphysics.doppler;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.Loggers;

import net.minecraft.client.Minecraft;

public final class DopplerKinematics {

    private static final DopplerKinematicsProvider ROOT_PROVIDER = new RootDopplerKinematicsProvider();
    private static volatile DopplerKinematicsProvider activeProvider = ROOT_PROVIDER;

    private DopplerKinematics() {
    }

    public static void setProvider(@Nullable DopplerKinematicsProvider provider) {
        activeProvider = provider == null ? ROOT_PROVIDER : provider;
    }

    public static DopplerKinematicsProvider getProvider() {
        return activeProvider;
    }

    static DopplerKinematicScene createScene(Minecraft client) {
        DopplerKinematicsProvider provider = activeProvider == null ? ROOT_PROVIDER : activeProvider;

        try {
            DopplerKinematicScene scene = provider.createScene(client);
            if (scene != null) {
                return scene;
            }
        } catch (Throwable throwable) {
            Loggers.logDoppler("Doppler kinematics provider failed; using root sampled fallback: {}", throwable.getMessage());
        }

        return ROOT_PROVIDER.createScene(client);
    }

}
