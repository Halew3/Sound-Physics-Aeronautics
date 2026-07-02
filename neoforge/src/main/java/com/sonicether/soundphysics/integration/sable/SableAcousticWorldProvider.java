package com.sonicether.soundphysics.integration.sable;

import java.util.List;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.acoustic.AcousticScene;
import com.sonicether.soundphysics.acoustic.AcousticSceneContext;
import com.sonicether.soundphysics.acoustic.AcousticWorldProvider;
import com.sonicether.soundphysics.acoustic.RootAcousticScene;
import com.sonicether.soundphysics.acoustic.RootAcousticSpace;
import com.sonicether.soundphysics.utils.LevelAccessUtils;
import com.sonicether.soundphysics.world.ClientLevelProxy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

final class SableAcousticWorldProvider implements AcousticWorldProvider {

    @Override
    @Nullable
    public AcousticScene createScene(Minecraft client, AcousticSceneContext context) {
        ClientLevelProxy levelProxy = LevelAccessUtils.getClientLevelProxy(client);
        if (levelProxy == null) {
            SableAcousticSnapshotManager.diagnostics().recordProviderRootFallback();
            return null;
        }

        RootAcousticSpace rootSpace = new RootAcousticSpace(levelProxy);
        if (SoundPhysicsMod.CONFIG == null || !SoundPhysicsMod.CONFIG.sableAcousticsEnabled.get()) {
            SableAcousticSnapshotManager.diagnostics().recordProviderRootFallback();
            return new RootAcousticScene(rootSpace);
        }

        ClientLevel clientLevel = client.level;
        SableAcousticSnapshot snapshot = SableAcousticSnapshotManager.current();
        if (clientLevel == null || snapshot == null || !snapshot.isFor(clientLevel) || !snapshot.hasSableSpaces()) {
            SableAcousticSnapshotManager.diagnostics().recordProviderRootFallback();
            return new RootAcousticScene(rootSpace);
        }

        List<SableAcousticSpace> sourceCandidates = SableAcousticSnapshotManager.sourceCandidates(snapshot, context);
        return new SableAcousticScene(
                rootSpace,
                snapshot,
                SableAcousticSnapshotManager.diagnostics(),
                context.sourcePosition(),
                context.listenerPosition(),
                sourceCandidates
        );
    }

}
