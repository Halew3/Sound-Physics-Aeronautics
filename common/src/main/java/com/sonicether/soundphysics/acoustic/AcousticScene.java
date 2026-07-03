package com.sonicether.soundphysics.acoustic;

import javax.annotation.Nullable;

import net.minecraft.world.phys.Vec3;

public interface AcousticScene {

    AcousticRayHit rayCast(Vec3 from, Vec3 to, @Nullable AcousticBlockRef ignore);

    AcousticBlockRef blockAt(Vec3 worldPosition);

    default Vec3 toLocalPosition(AcousticBlockRef blockRef, Vec3 worldPosition) {
        return worldPosition;
    }

}
