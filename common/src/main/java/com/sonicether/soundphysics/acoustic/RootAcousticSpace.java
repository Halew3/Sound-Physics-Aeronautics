package com.sonicether.soundphysics.acoustic;

import javax.annotation.Nonnull;

import com.sonicether.soundphysics.world.ClientLevelProxy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class RootAcousticSpace implements AcousticSpace {

    private static final String ROOT_ID = "root";

    private final ClientLevelProxy levelProxy;

    public RootAcousticSpace(ClientLevelProxy levelProxy) {
        this.levelProxy = levelProxy;
    }

    @Override
    public String acousticId() {
        return ROOT_ID;
    }

    @Override
    public BlockEntity getBlockEntity(@Nonnull BlockPos blockPos) {
        return levelProxy.getBlockEntity(blockPos);
    }

    @Override
    public BlockState getBlockState(@Nonnull BlockPos blockPos) {
        return levelProxy.getBlockState(blockPos);
    }

    @Override
    public FluidState getFluidState(@Nonnull BlockPos blockPos) {
        return levelProxy.getFluidState(blockPos);
    }

    @Override
    public int getHeight() {
        return levelProxy.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return levelProxy.getMinBuildHeight();
    }

}
