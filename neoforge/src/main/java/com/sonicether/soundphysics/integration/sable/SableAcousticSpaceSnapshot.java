package com.sonicether.soundphysics.integration.sable;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sonicether.soundphysics.world.ClonedLevelChunk;
import com.sonicether.soundphysics.doppler.DopplerKinematicState;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

final class SableAcousticSpaceSnapshot implements SableAcousticSpace {

    private final String acousticId;
    private final UUID subLevelId;
    private final Pose3d pose;
    private final Pose3d lastPose;
    private final SableAcousticBounds worldBounds;
    private final SableAcousticBounds localBounds;
    private final Map<ChunkPos, ClonedLevelChunk> chunks;
    private final int minBuildHeight;
    private final int height;
    private final long signature;
    private final long membershipSignature;

    private SableAcousticSpaceSnapshot(
            String acousticId,
            UUID subLevelId,
            Pose3d pose,
            Pose3d lastPose,
            SableAcousticBounds worldBounds,
            SableAcousticBounds localBounds,
            Map<ChunkPos, ClonedLevelChunk> chunks,
            int minBuildHeight,
            int height,
            long signature,
            long membershipSignature
    ) {
        this.acousticId = acousticId;
        this.subLevelId = subLevelId;
        this.pose = pose;
        this.lastPose = lastPose;
        this.worldBounds = worldBounds;
        this.localBounds = localBounds;
        this.chunks = chunks;
        this.minBuildHeight = minBuildHeight;
        this.height = height;
        this.signature = signature;
        this.membershipSignature = membershipSignature;
    }

    static SableAcousticSpaceSnapshot create(ClientLevel level, ClientSubLevel subLevel, SableAcousticSpaceMembership membership) {
        UUID subLevelId = subLevel.getUniqueId();
        String acousticId = "sable:" + subLevelId;
        Pose3d pose = new Pose3d(subLevel.logicalPose());
        Pose3d lastPose = new Pose3d(subLevel.lastPose());
        BoundingBox3dc world = subLevel.boundingBox();
        SableAcousticBounds worldBounds = SableAcousticBounds.of(world.minX(), world.minY(), world.minZ(), world.maxX(), world.maxY(), world.maxZ());
        SableAcousticBounds localBounds = membership.localBounds();
        long signature = signature(subLevelId, pose, lastPose, worldBounds, localBounds, membership);
        long membershipSignature = membershipSignature(subLevelId, worldBounds, localBounds, membership);

        return new SableAcousticSpaceSnapshot(acousticId, subLevelId, pose, lastPose, worldBounds, localBounds, membership.chunks(), membership.minBuildHeight(), membership.height(), signature, membershipSignature);
    }

    UUID subLevelId() {
        return subLevelId;
    }

    long signature() {
        return signature;
    }

    long membershipSignature() {
        return membershipSignature;
    }

    int chunkCount() {
        return chunks.size();
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
        if (!containsLocalPosition(localPosition)) {
            return false;
        }

        return chunks.containsKey(new ChunkPos(BlockPos.containing(localPosition)));
    }

    @Override
    public boolean intersectsLocalSegment(Vec3 localFrom, Vec3 localTo) {
        SableAcousticBounds segmentBounds = SableAcousticBounds.segment(localFrom.x, localFrom.y, localFrom.z, localTo.x, localTo.y, localTo.z).inflate(0.125D);
        return localBounds.intersects(segmentBounds);
    }

    @Override
    public Vec3 toLocal(Vec3 worldPosition) {
        return pose.transformPositionInverse(worldPosition);
    }

    @Override
    public Vec3 toWorld(Vec3 localPosition) {
        return pose.transformPosition(localPosition);
    }

    @Override
    public Vec3 normalToWorld(Direction localDirection) {
        Vec3 transformed = pose.transformNormal(new Vec3(localDirection.step()));
        if (transformed.lengthSqr() <= 1.0E-12D) {
            return new Vec3(localDirection.step());
        }
        return transformed.normalize();
    }

    @Override
    public DopplerKinematicState pointKinematicsAtWorld(Vec3 worldPosition, long version) {
        return SablePointVelocity.state(acousticId, version, pose, lastPose, worldPosition);
    }

    private boolean containsLocalPosition(Vec3 localPosition) {
        return localBounds.contains(localPosition.x, localPosition.y, localPosition.z);
    }

    @Override
    public String acousticId() {
        return acousticId;
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(@Nonnull BlockPos blockPos) {
        ClonedLevelChunk chunk = getChunk(blockPos);
        return chunk == null ? null : chunk.getBlockEntity(blockPos);
    }

    @Override
    public BlockState getBlockState(@Nonnull BlockPos blockPos) {
        if (isOutsideBuildHeight(blockPos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }

        ClonedLevelChunk chunk = getChunk(blockPos);
        return chunk == null ? Blocks.VOID_AIR.defaultBlockState() : chunk.getBlockState(blockPos);
    }

    @Override
    public FluidState getFluidState(@Nonnull BlockPos blockPos) {
        if (isOutsideBuildHeight(blockPos)) {
            return Fluids.EMPTY.defaultFluidState();
        }

        ClonedLevelChunk chunk = getChunk(blockPos);
        return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(blockPos);
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinBuildHeight() {
        return minBuildHeight;
    }

    private @Nullable ClonedLevelChunk getChunk(BlockPos blockPos) {
        return chunks.get(new ChunkPos(blockPos));
    }

    private static long signature(UUID subLevelId, Pose3dc pose, Pose3dc lastPose, SableAcousticBounds worldBounds, SableAcousticBounds localBounds, SableAcousticSpaceMembership membership) {
        long hash = 1125899906842597L;
        hash = mix(hash, subLevelId.getMostSignificantBits());
        hash = mix(hash, subLevelId.getLeastSignificantBits());
        hash = mixPose(hash, pose);
        hash = mixPose(hash, lastPose);
        hash = mixBounds(hash, worldBounds);
        hash = mixBounds(hash, localBounds);
        hash = mix(hash, membership.chunkCount());
        hash = mix(hash, membership.chunkPositionSignature());
        return mix(hash, membership.contentSignature());
    }

    private static long membershipSignature(UUID subLevelId, SableAcousticBounds worldBounds, SableAcousticBounds localBounds, SableAcousticSpaceMembership membership) {
        long hash = 1469598103934665603L;
        hash = mix(hash, subLevelId.getMostSignificantBits());
        hash = mix(hash, subLevelId.getLeastSignificantBits());
        hash = mixQuantizedBounds(hash, worldBounds);
        hash = mixQuantizedBounds(hash, localBounds);
        hash = mix(hash, membership.chunkCount());
        hash = mix(hash, membership.chunkPositionSignature());
        return mix(hash, membership.contentSignature());
    }

    private static long mixPose(long hash, Pose3dc pose) {
        Vector3dc position = pose.position();
        Vector3dc rotationPoint = pose.rotationPoint();
        Vector3dc scale = pose.scale();
        Quaterniondc orientation = pose.orientation();

        hash = mixVector(hash, position);
        hash = mixQuaternion(hash, orientation);
        hash = mixVector(hash, rotationPoint);
        return mixVector(hash, scale);
    }

    private static long mixVector(long hash, Vector3dc vector) {
        hash = mix(hash, Double.doubleToLongBits(vector.x()));
        hash = mix(hash, Double.doubleToLongBits(vector.y()));
        return mix(hash, Double.doubleToLongBits(vector.z()));
    }

    private static long mixQuaternion(long hash, Quaterniondc quaternion) {
        hash = mix(hash, Double.doubleToLongBits(quaternion.x()));
        hash = mix(hash, Double.doubleToLongBits(quaternion.y()));
        hash = mix(hash, Double.doubleToLongBits(quaternion.z()));
        return mix(hash, Double.doubleToLongBits(quaternion.w()));
    }

    private static long mixBounds(long hash, SableAcousticBounds bounds) {
        hash = mix(hash, Double.doubleToLongBits(bounds.minX()));
        hash = mix(hash, Double.doubleToLongBits(bounds.minY()));
        hash = mix(hash, Double.doubleToLongBits(bounds.minZ()));
        hash = mix(hash, Double.doubleToLongBits(bounds.maxX()));
        hash = mix(hash, Double.doubleToLongBits(bounds.maxY()));
        return mix(hash, Double.doubleToLongBits(bounds.maxZ()));
    }

    private static long mixQuantizedBounds(long hash, SableAcousticBounds bounds) {
        hash = mix(hash, (long) Math.floor(bounds.minX()));
        hash = mix(hash, (long) Math.floor(bounds.minY()));
        hash = mix(hash, (long) Math.floor(bounds.minZ()));
        hash = mix(hash, (long) Math.ceil(bounds.maxX()));
        hash = mix(hash, (long) Math.ceil(bounds.maxY()));
        return mix(hash, (long) Math.ceil(bounds.maxZ()));
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 1099511628211L;
    }

}
