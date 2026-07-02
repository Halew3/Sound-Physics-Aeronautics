package com.sonicether.soundphysics.integration.sable;

import com.sonicether.soundphysics.doppler.DopplerKinematicState;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.Vec3;

final class SablePointVelocity {

    private static final double TICKS_PER_SECOND = 20.0D;
    private static final double MAX_RELIABLE_SPEED_BLOCKS_PER_SECOND = 2000.0D;

    private SablePointVelocity() {
    }

    static DopplerKinematicState state(String acousticId, long version, Pose3dc pose, Pose3dc lastPose, Vec3 worldPosition) {
        if (!isUsablePose(pose) || !isUsablePose(lastPose) || !isFinite(worldPosition)) {
            return DopplerKinematicState.unreliable(worldPosition, acousticId, version);
        }

        Vec3 velocity = velocityAtWorld(pose, lastPose, worldPosition);
        if (!isUsableVelocity(velocity)) {
            return DopplerKinematicState.unreliable(worldPosition, acousticId, version);
        }

        return new DopplerKinematicState(worldPosition, velocity, acousticId, version, true);
    }

    static Vec3 velocityAtWorld(Pose3dc pose, Pose3dc lastPose, Vec3 worldPosition) {
        Vec3 local = pose.transformPositionInverse(worldPosition);
        Vec3 worldNow = pose.transformPosition(local);
        Vec3 worldPrev = lastPose.transformPosition(local);
        return worldNow.subtract(worldPrev).scale(TICKS_PER_SECOND);
    }

    static boolean isUsableVelocity(Vec3 velocity) {
        return isFinite(velocity) && velocity.length() <= MAX_RELIABLE_SPEED_BLOCKS_PER_SECOND;
    }

    private static boolean isUsablePose(Pose3dc pose) {
        return pose != null
                && isFinite(pose.transformPosition(Vec3.ZERO))
                && isFinite(pose.transformNormal(new Vec3(1.0D, 0.0D, 0.0D)))
                && isFinite(pose.transformNormal(new Vec3(0.0D, 1.0D, 0.0D)))
                && isFinite(pose.transformNormal(new Vec3(0.0D, 0.0D, 1.0D)));
    }

    private static boolean isFinite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

}
