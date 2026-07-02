package com.sonicether.soundphysics.acoustic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public record AcousticBlockRef(AcousticSpace space, BlockPos pos) {

    public BlockState blockState() {
        return space.getBlockState(pos);
    }

    public FluidState fluidState() {
        return space.getFluidState(pos);
    }

    public boolean isFaceSturdy(Direction side) {
        return blockState().isFaceSturdy(space, pos, side);
    }

}
