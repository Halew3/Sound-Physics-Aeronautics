package com.sonicether.soundphysics.integration.sable;

import com.sonicether.soundphysics.acoustic.AcousticSpace;
import com.sonicether.soundphysics.doppler.DopplerKinematicState;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

interface SableAcousticSpace extends AcousticSpace {

    SableAcousticBounds worldBounds();

    default boolean intersectsWorldBounds(SableAcousticBounds segmentBounds) {
        return worldBounds().intersects(segmentBounds);
    }

    boolean containsWorldPosition(Vec3 worldPosition);

    boolean intersectsLocalSegment(Vec3 localFrom, Vec3 localTo);

    Vec3 toLocal(Vec3 worldPosition);

    Vec3 toWorld(Vec3 localPosition);

    Vec3 normalToWorld(Direction localDirection);

    default DopplerKinematicState pointKinematicsAtWorld(Vec3 worldPosition, long version) {
        return DopplerKinematicState.unreliable(worldPosition, acousticId(), version);
    }

}
