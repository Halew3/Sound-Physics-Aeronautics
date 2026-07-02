package com.sonicether.soundphysics.integration.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sonicether.soundphysics.doppler.DopplerKinematicState;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class SablePointVelocityTest {

    @Test
    void pureTranslationProducesPointVelocity() {
        Pose3d lastPose = pose(0.0D, 0.0D, 0.0D);
        Pose3d currentPose = pose(0.5D, 0.0D, 0.0D);

        Vec3 velocity = SablePointVelocity.velocityAtWorld(currentPose, lastPose, new Vec3(5.5D, 0.0D, 0.0D));

        assertEquals(10.0D, velocity.x, 1.0E-6D);
        assertEquals(0.0D, velocity.y, 1.0E-6D);
        assertEquals(0.0D, velocity.z, 1.0E-6D);
    }

    @Test
    void rotationProducesTangentialPointVelocity() {
        Pose3d lastPose = pose(0.0D, 0.0D, 0.0D);
        Pose3d currentPose = pose(0.0D, 0.0D, 0.0D, new Quaterniond().rotationY(Math.toRadians(90.0D)));
        Vec3 localPoint = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 worldPoint = currentPose.transformPosition(localPoint);
        Vec3 expected = worldPoint.subtract(lastPose.transformPosition(localPoint)).scale(20.0D);

        Vec3 velocity = SablePointVelocity.velocityAtWorld(currentPose, lastPose, worldPoint);

        assertEquals(expected.x, velocity.x, 1.0E-6D);
        assertEquals(expected.y, velocity.y, 1.0E-6D);
        assertEquals(expected.z, velocity.z, 1.0E-6D);
        assertTrue(velocity.length() > 20.0D);
    }

    @Test
    void identicalPoseProducesReliableZeroVelocity() {
        Pose3d pose = pose(0.0D, 0.0D, 0.0D);

        DopplerKinematicState state = SablePointVelocity.state("sable:test", 7L, pose, pose, new Vec3(4.0D, 0.0D, 0.0D));

        assertTrue(state.reliable());
        assertEquals(0.0D, state.worldVelocity().lengthSqr(), 1.0E-12D);
    }

    @Test
    void hugeVelocityIsUnreliable() {
        Pose3d lastPose = pose(0.0D, 0.0D, 0.0D);
        Pose3d currentPose = pose(200.0D, 0.0D, 0.0D);

        DopplerKinematicState state = SablePointVelocity.state("sable:test", 7L, currentPose, lastPose, new Vec3(201.0D, 0.0D, 0.0D));

        assertFalse(state.reliable());
    }

    @Test
    void nonFiniteWorldPositionIsUnreliable() {
        Pose3d pose = pose(0.0D, 0.0D, 0.0D);

        DopplerKinematicState state = SablePointVelocity.state("sable:test", 7L, pose, pose, new Vec3(Double.NaN, 0.0D, 0.0D));

        assertFalse(state.reliable());
    }

    private static Pose3d pose(double x, double y, double z) {
        return pose(x, y, z, new Quaterniond());
    }

    private static Pose3d pose(double x, double y, double z, Quaterniond orientation) {
        return new Pose3d(
                new Vector3d(x, y, z),
                orientation,
                new Vector3d(0.0D, 0.0D, 0.0D),
                new Vector3d(1.0D, 1.0D, 1.0D)
        );
    }

}
