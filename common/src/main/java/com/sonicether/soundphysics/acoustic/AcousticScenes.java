package com.sonicether.soundphysics.acoustic;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsTrace;

import net.minecraft.client.Minecraft;

public class AcousticScenes {

    private static final AcousticWorldProvider ROOT_PROVIDER = new RootAcousticWorldProvider();
    private static AcousticWorldProvider activeProvider = ROOT_PROVIDER;

    private AcousticScenes() {
    }

    public static void setProvider(@Nullable AcousticWorldProvider provider) {
        activeProvider = provider == null ? ROOT_PROVIDER : provider;
    }

    public static AcousticWorldProvider getProvider() {
        return activeProvider;
    }

    @Nullable
    public static AcousticScene createScene(Minecraft client, AcousticSceneContext context) {
        return createScene(client, context, activeProvider, ROOT_PROVIDER);
    }

    @Nullable
    static AcousticScene createScene(Minecraft client, AcousticSceneContext context, AcousticWorldProvider provider, AcousticWorldProvider rootProvider) {
        if (forceRootAcousticProvider()) {
            AcousticScene forcedRootScene = createSceneSafely(client, context, rootProvider);
            SoundPhysicsTrace.recordAcousticScene(forcedRootScene, true);
            return forcedRootScene;
        }

        AcousticWorldProvider sceneProvider = provider == null ? rootProvider : provider;
        AcousticScene scene = createSceneSafely(client, context, sceneProvider);

        if (scene != null || sceneProvider == rootProvider) {
            SoundPhysicsTrace.recordAcousticScene(scene, false);
            return scene;
        }

        AcousticScene rootScene = createSceneSafely(client, context, rootProvider);
        SoundPhysicsTrace.recordAcousticScene(rootScene, false);
        return rootScene;
    }

    @Nullable
    private static AcousticScene createSceneSafely(Minecraft client, AcousticSceneContext context, AcousticWorldProvider provider) {
        try {
            return provider.createScene(client, context);
        } catch (Throwable throwable) {
            SoundPhysicsTrace.recordProviderFailure(throwable);
            Loggers.warn("Acoustic world provider failed; using root acoustic world when possible: {}", throwable.getMessage());
            return null;
        }
    }

    private static boolean forceRootAcousticProvider() {
        return DiagnosticRuntimeOverrides.forceRootAcousticProvider(SoundPhysicsMod.CONFIG);
    }

}
