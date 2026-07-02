package com.sonicether.soundphysics.acoustic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public record AcousticRayHit(
        BlockHitResult localHit,
        AcousticSpace space,
        Vec3 worldLocation,
        Vec3 worldNormal
) {

    public AcousticBlockRef blockRef() {
        return new AcousticBlockRef(space, localHit.getBlockPos());
    }

    public BlockPos blockPos() {
        return localHit.getBlockPos();
    }

    public Direction direction() {
        return localHit.getDirection();
    }

}
