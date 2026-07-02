package com.sonicether.soundphysics.doppler;

import javax.annotation.Nullable;

import net.minecraft.world.phys.Vec3;

public record DopplerKinematicState(
        Vec3 worldPosition,
        Vec3 worldVelocity,
        @Nullable String acousticSpaceId,
        long version,
        boolean reliable
) {

    public static DopplerKinematicState unreliable(Vec3 worldPosition, @Nullable String acousticSpaceId, long version) {
        return new DopplerKinematicState(worldPosition, Vec3.ZERO, acousticSpaceId, version, false);
    }

    public DopplerKinematicState withVelocity(Vec3 velocity, boolean reliable) {
        return new DopplerKinematicState(worldPosition, velocity, acousticSpaceId, version, reliable);
    }

}
