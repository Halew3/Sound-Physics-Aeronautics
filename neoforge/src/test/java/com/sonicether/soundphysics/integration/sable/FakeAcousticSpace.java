package com.sonicether.soundphysics.integration.sable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sonicether.soundphysics.doppler.DopplerKinematicState;
import com.sonicether.soundphysics.world.ClientLevelProxy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

final class FakeAcousticSpace implements SableAcousticSpace, ClientLevelProxy {

    private final String acousticId;
    private final SableAcousticBounds localBounds;
    private final SableAcousticBounds worldBounds;
    private final Vec3 offset;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<Direction, Vec3> worldNormals = new EnumMap<>(Direction.class);
    @Nullable
    private Vec3 pointVelocity;
    private boolean reliablePointVelocity;

    FakeAcousticSpace(String acousticId, SableAcousticBounds localBounds) {
        this(acousticId, localBounds, Vec3.ZERO);
    }

    FakeAcousticSpace(String acousticId, SableAcousticBounds localBounds, Vec3 offset) {
        this.acousticId = acousticId;
        this.localBounds = localBounds;
        this.offset = offset;
        this.worldBounds = SableAcousticBounds.of(
                localBounds.minX() + offset.x,
                localBounds.minY() + offset.y,
                localBounds.minZ() + offset.z,
                localBounds.maxX() + offset.x,
                localBounds.maxY() + offset.y,
                localBounds.maxZ() + offset.z
        );
    }

    FakeAcousticSpace withBlock(BlockPos pos, BlockState state) {
        blocks.put(pos, state);
        return this;
    }

    FakeAcousticSpace withStone(BlockPos pos) {
        return withBlock(pos, Blocks.STONE.defaultBlockState());
    }

    FakeAcousticSpace withWorldNormal(Direction localDirection, Vec3 worldNormal) {
        worldNormals.put(localDirection, worldNormal);
        return this;
    }

    FakeAcousticSpace withPointVelocity(Vec3 velocity) {
        pointVelocity = velocity;
        reliablePointVelocity = true;
        return this;
    }

    FakeAcousticSpace withUnreliablePointVelocity(Vec3 velocity) {
        pointVelocity = velocity;
        reliablePointVelocity = false;
        return this;
    }

    @Override
    public SableAcousticBounds worldBounds() {
        return worldBounds;
    }

    @Override
    public boolean containsWorldPosition(Vec3 worldPosition) {
        if (!worldBounds.contains(worldPosition.x, worldPosition.y, worldPosition.z)) {
            return false;
        }
        Vec3 localPosition = toLocal(worldPosition);
        return localBounds.contains(localPosition.x, localPosition.y, localPosition.z);
    }

    @Override
    public boolean intersectsLocalSegment(Vec3 localFrom, Vec3 localTo) {
        SableAcousticBounds segmentBounds = SableAcousticBounds.segment(localFrom.x, localFrom.y, localFrom.z, localTo.x, localTo.y, localTo.z).inflate(0.125D);
        return localBounds.intersects(segmentBounds);
    }

    @Override
    public Vec3 toLocal(Vec3 worldPosition) {
        return worldPosition.subtract(offset);
    }

    @Override
    public Vec3 toWorld(Vec3 localPosition) {
        return localPosition.add(offset);
    }

    @Override
    public Vec3 normalToWorld(Direction localDirection) {
        Vec3 mapped = worldNormals.get(localDirection);
        if (mapped != null) {
            return mapped.normalize();
        }
        return new Vec3(localDirection.step());
    }

    @Override
    public DopplerKinematicState pointKinematicsAtWorld(Vec3 worldPosition, long version) {
        if (pointVelocity == null || !reliablePointVelocity) {
            return DopplerKinematicState.unreliable(worldPosition, acousticId, version);
        }

        return new DopplerKinematicState(worldPosition, pointVelocity, acousticId, version, true);
    }

    @Override
    public String acousticId() {
        return acousticId;
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(@Nonnull BlockPos blockPos) {
        return null;
    }

    @Override
    public BlockState getBlockState(@Nonnull BlockPos blockPos) {
        return blocks.getOrDefault(blockPos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public FluidState getFluidState(@Nonnull BlockPos blockPos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getHeight() {
        return 384;
    }

    @Override
    public int getMinBuildHeight() {
        return -64;
    }

}
