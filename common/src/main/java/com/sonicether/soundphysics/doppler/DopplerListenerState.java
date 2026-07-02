package com.sonicether.soundphysics.doppler;

import javax.annotation.Nullable;

import net.minecraft.world.phys.Vec3;

public final class DopplerListenerState {

    private static final long NO_SAMPLE = Long.MIN_VALUE;
    private static final long MAX_SAMPLE_TICK_DELTA = 20L;

    private Vec3 lastKnownPosition = Vec3.ZERO;
    private Vec3 previousPosition = Vec3.ZERO;
    private Vec3 currentPosition = Vec3.ZERO;
    private Vec3 sampledVelocity = Vec3.ZERO;
    private long lastSampleGameTick = NO_SAMPLE;
    private long lastSampleNanos;
    private long sampleCount;
    private long lastDtTicks;
    private boolean reliable;
    private VelocitySource velocitySource = VelocitySource.UNRELIABLE;
    private String rejectedReason = "not_sampled";

    DopplerKinematicState sample(Vec3 worldPosition, long gameTime, double maxSpeed, @Nullable String acousticSpaceId, long version) {
        return sample(worldPosition, gameTime, maxSpeed, acousticSpaceId, version, VelocitySource.CAMERA_DELTA);
    }

    DopplerKinematicState sample(Vec3 worldPosition, long gameTime, double maxSpeed, @Nullable String acousticSpaceId, long version, VelocitySource source) {
        DopplerKinematicState state = sampleInternal(worldPosition, gameTime, maxSpeed, acousticSpaceId, version, source);
        lastSampleNanos = System.nanoTime();
        return state;
    }

    void acceptExternal(Vec3 worldPosition, Vec3 velocity, long gameTime) {
        acceptExternal(worldPosition, velocity, gameTime, VelocitySource.SABLE);
    }

    void acceptExternal(Vec3 worldPosition, Vec3 velocity, long gameTime, VelocitySource source) {
        previousPosition = lastKnownPosition;
        currentPosition = worldPosition;
        lastKnownPosition = worldPosition;
        sampledVelocity = velocity;
        lastSampleGameTick = gameTime;
        lastSampleNanos = System.nanoTime();
        sampleCount++;
        lastDtTicks = 0L;
        reliable = true;
        velocitySource = source;
        rejectedReason = "none";
    }

    void reset() {
        lastKnownPosition = Vec3.ZERO;
        previousPosition = Vec3.ZERO;
        currentPosition = Vec3.ZERO;
        sampledVelocity = Vec3.ZERO;
        lastSampleGameTick = NO_SAMPLE;
        lastSampleNanos = 0L;
        sampleCount = 0L;
        lastDtTicks = 0L;
        reliable = false;
        velocitySource = VelocitySource.UNRELIABLE;
        rejectedReason = "reset";
    }

    Vec3 sampledVelocity() {
        return sampledVelocity;
    }

    long lastSampleGameTick() {
        return lastSampleGameTick;
    }

    long lastSampleNanos() {
        return lastSampleNanos;
    }

    boolean reliable() {
        return reliable;
    }

    Vec3 previousPosition() {
        return previousPosition;
    }

    Vec3 currentPosition() {
        return currentPosition;
    }

    long sampleCount() {
        return sampleCount;
    }

    long lastDtTicks() {
        return lastDtTicks;
    }

    VelocitySource velocitySource() {
        return velocitySource;
    }

    String rejectedReason() {
        return rejectedReason;
    }

    private DopplerKinematicState sampleInternal(Vec3 worldPosition, long gameTime, double maxSpeed, @Nullable String acousticSpaceId, long version, VelocitySource source) {
        if (lastSampleGameTick == NO_SAMPLE) {
            rememberUnreliable(worldPosition, gameTime, 0L, "initial_sample");
            return DopplerKinematicState.unreliable(worldPosition, acousticSpaceId, version);
        }

        if (gameTime <= lastSampleGameTick) {
            lastDtTicks = 0L;
            return new DopplerKinematicState(worldPosition, sampledVelocity, acousticSpaceId, version, reliable);
        }

        long tickDelta = gameTime - lastSampleGameTick;
        if (tickDelta > MAX_SAMPLE_TICK_DELTA) {
            rememberUnreliable(worldPosition, gameTime, tickDelta, "sample_gap_" + tickDelta);
            return DopplerKinematicState.unreliable(worldPosition, acousticSpaceId, version);
        }

        Vec3 velocity = worldPosition.subtract(lastKnownPosition).scale(20.0D / (double) tickDelta);
        if (!isUsableVelocity(velocity, maxSpeed)) {
            rememberUnreliable(worldPosition, gameTime, tickDelta, "velocity_outlier");
            return DopplerKinematicState.unreliable(worldPosition, acousticSpaceId, version);
        }

        previousPosition = lastKnownPosition;
        currentPosition = worldPosition;
        lastKnownPosition = worldPosition;
        sampledVelocity = velocity;
        lastSampleGameTick = gameTime;
        sampleCount++;
        lastDtTicks = tickDelta;
        reliable = true;
        velocitySource = source;
        rejectedReason = "none";
        return new DopplerKinematicState(worldPosition, velocity, acousticSpaceId, version, true);
    }

    private void rememberUnreliable(Vec3 worldPosition, long gameTime, long tickDelta, String reason) {
        previousPosition = lastKnownPosition;
        currentPosition = worldPosition;
        lastKnownPosition = worldPosition;
        sampledVelocity = Vec3.ZERO;
        lastSampleGameTick = gameTime;
        sampleCount++;
        lastDtTicks = tickDelta;
        reliable = false;
        velocitySource = VelocitySource.UNRELIABLE;
        rejectedReason = reason;
    }

    static boolean isUsableVelocity(Vec3 velocity, double maxSpeed) {
        return Double.isFinite(velocity.x)
                && Double.isFinite(velocity.y)
                && Double.isFinite(velocity.z)
                && velocity.length() <= Math.max(maxSpeed, 0.0D);
    }

    enum VelocitySource {
        SABLE,
        CAMERA_DELTA,
        PLAYER_DELTA,
        SAMPLED_POSITION,
        UNRELIABLE
    }

}
