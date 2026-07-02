package com.sonicether.soundphysics.integration.sable;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.doppler.DopplerKinematicScene;
import com.sonicether.soundphysics.doppler.DopplerKinematicState;
import com.sonicether.soundphysics.doppler.DopplerKinematicsProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

final class SableDopplerKinematicsProvider implements DopplerKinematicsProvider {

    @Override
    public DopplerKinematicScene createScene(Minecraft client) {
        if (SoundPhysicsMod.CONFIG == null
                || !SoundPhysicsMod.CONFIG.sableAcousticsEnabled.get()
                || SoundPhysicsMod.CONFIG.forceRootAcousticProvider.get()) {
            return new Scene(client.level, null);
        }
        return new Scene(client.level, SableAcousticSnapshotManager.current());
    }

    private static final class Scene implements DopplerKinematicScene {

        private final ClientLevel level;
        private final SableAcousticSnapshot snapshot;

        private Scene(ClientLevel level, SableAcousticSnapshot snapshot) {
            this.level = level;
            this.snapshot = snapshot;
        }

        @Override
        public DopplerKinematicState listener(Vec3 worldPosition, long gameTime) {
            return stateFor(level, snapshot, worldPosition);
        }

        @Override
        public DopplerKinematicState source(int sourceId, Vec3 worldPosition, ResourceLocation sound, SoundSource category, long gameTime) {
            return stateFor(level, snapshot, worldPosition);
        }

    }

    static DopplerKinematicState stateFor(ClientLevel level, SableAcousticSnapshot snapshot, Vec3 worldPosition) {
        if (snapshot == null || !snapshot.isFor(level) || !snapshot.hasSableSpaces()) {
            return DopplerKinematicState.unreliable(worldPosition, "root", 0L);
        }

            for (SableAcousticSpace space : snapshot.candidatesForPoint(worldPosition)) {
                try {
                    if (space.containsWorldPosition(worldPosition)) {
                        return space.pointKinematicsAtWorld(worldPosition, snapshot.version());
                    }
                } catch (Throwable throwable) {
                    Loggers.logDebug("Skipping Sable Doppler space {} after kinematic lookup failure: {}", space.acousticId(), throwable.getMessage());
                }
            }

        return DopplerKinematicState.unreliable(worldPosition, "root", snapshot.version());
    }

}
