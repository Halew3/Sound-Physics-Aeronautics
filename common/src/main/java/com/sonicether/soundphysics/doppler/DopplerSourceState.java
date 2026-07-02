package com.sonicether.soundphysics.doppler;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class DopplerSourceState {

    private static final long NO_SAMPLE = Long.MIN_VALUE;
    private static final long MAX_SAMPLE_TICK_DELTA = 20L;

    private final int sourceId;
    private ResourceLocation sound;
    private SoundSource category;
    private String soundInstanceClassName;
    private float basePitch;
    private double targetMultiplier;
    private double smoothedMultiplier;
    private float lastAppliedPitch;
    private Vec3 previousSourcePosition;
    private Vec3 currentSourcePosition;
    private Vec3 lastKnownSourcePosition;
    private Vec3 sampledSourceVelocity;
    private long lastVelocitySampleGameTick;
    private long lastDopplerUpdateGameTick;
    private long lastUpdateNanos;
    private long basePitchGameTick;
    private long velocitySampleCount;
    private long lastVelocityDtTicks;
    private double radialVelocity;
    private double relativeSpeedAlongLineOfSight;
    private double multiplierDelta;
    private boolean hasAppliedDoppler;
    private boolean reliableVelocity;
    private boolean basePitchCaptured;
    private BasePitchSource basePitchSource;
    private PitchDecision latestPitchDecision;
    private PitchControlSource pitchControlSource;
    private VelocitySource velocitySource;
    private String rejectedVelocityReason;
    @Nullable
    private String acousticSpaceId;
    private long acousticSpaceVersion;

    public DopplerSourceState(int sourceId, ResourceLocation sound, SoundSource category, Vec3 initialPosition) {
        this(sourceId, sound, category, initialPosition, null);
    }

    public DopplerSourceState(int sourceId, ResourceLocation sound, SoundSource category, Vec3 initialPosition, @Nullable String soundInstanceClassName) {
        this.sourceId = sourceId;
        resetForReuse(sound, category, initialPosition, soundInstanceClassName);
    }

    public int sourceId() {
        return sourceId;
    }

    public ResourceLocation sound() {
        return sound;
    }

    public SoundSource category() {
        return category;
    }

    public String soundInstanceClassName() {
        return soundInstanceClassName == null ? "" : soundInstanceClassName;
    }

    public float basePitch() {
        return basePitch;
    }

    public double smoothedMultiplier() {
        return smoothedMultiplier;
    }

    public double targetMultiplier() {
        return targetMultiplier;
    }

    public float lastAppliedPitch() {
        return lastAppliedPitch;
    }

    public Vec3 lastKnownSourcePosition() {
        return lastKnownSourcePosition;
    }

    public Vec3 previousSourcePosition() {
        return previousSourcePosition;
    }

    public Vec3 currentSourcePosition() {
        return currentSourcePosition;
    }

    public Vec3 sampledSourceVelocity() {
        return sampledSourceVelocity;
    }

    public long lastVelocitySampleGameTick() {
        return lastVelocitySampleGameTick;
    }

    public long lastDopplerUpdateGameTick() {
        return lastDopplerUpdateGameTick;
    }

    public long lastUpdateNanos() {
        return lastUpdateNanos;
    }

    public long velocitySampleCount() {
        return velocitySampleCount;
    }

    public long lastVelocityDtTicks() {
        return lastVelocityDtTicks;
    }

    public double radialVelocity() {
        return radialVelocity;
    }

    public double relativeSpeedAlongLineOfSight() {
        return relativeSpeedAlongLineOfSight;
    }

    public double multiplierDelta() {
        return multiplierDelta;
    }

    public boolean hasAppliedDoppler() {
        return hasAppliedDoppler;
    }

    public boolean reliableVelocity() {
        return reliableVelocity;
    }

    public VelocitySource velocitySource() {
        return velocitySource;
    }

    public String rejectedVelocityReason() {
        return rejectedVelocityReason;
    }

    boolean hasBasePitchCaptured() {
        return basePitchCaptured;
    }

    PitchDecision latestPitchDecision() {
        return latestPitchDecision;
    }

    PitchControlSource pitchControlSource() {
        return pitchControlSource;
    }

    boolean matches(ResourceLocation candidateSound, SoundSource candidateCategory) {
        return sound.equals(candidateSound) && category == candidateCategory;
    }

    void resetForReuse(ResourceLocation newSound, SoundSource newCategory, Vec3 initialPosition) {
        resetForReuse(newSound, newCategory, initialPosition, null);
    }

    void resetForReuse(ResourceLocation newSound, SoundSource newCategory, Vec3 initialPosition, @Nullable String newSoundInstanceClassName) {
        sound = newSound;
        category = newCategory;
        soundInstanceClassName = newSoundInstanceClassName;
        basePitch = 1.0F;
        targetMultiplier = 1.0D;
        smoothedMultiplier = 1.0D;
        lastAppliedPitch = Float.NaN;
        previousSourcePosition = initialPosition;
        currentSourcePosition = initialPosition;
        lastKnownSourcePosition = initialPosition;
        sampledSourceVelocity = Vec3.ZERO;
        lastVelocitySampleGameTick = NO_SAMPLE;
        lastDopplerUpdateGameTick = NO_SAMPLE;
        lastUpdateNanos = 0L;
        basePitchGameTick = NO_SAMPLE;
        velocitySampleCount = 0L;
        lastVelocityDtTicks = 0L;
        radialVelocity = 0.0D;
        relativeSpeedAlongLineOfSight = 0.0D;
        multiplierDelta = 0.0D;
        hasAppliedDoppler = false;
        reliableVelocity = false;
        basePitchCaptured = false;
        basePitchSource = BasePitchSource.NONE;
        latestPitchDecision = PitchDecision.NEVER_EVALUATED;
        pitchControlSource = PitchControlSource.NONE;
        velocitySource = VelocitySource.UNRELIABLE;
        rejectedVelocityReason = "reset";
        acousticSpaceId = null;
        acousticSpaceVersion = 0L;
    }

    boolean observeMinecraftPitch(float pitch, boolean tickable, long gameTime) {
        if (!isUsablePitch(pitch)) {
            return false;
        }

        if (tickable || !basePitchCaptured || basePitchSource == BasePitchSource.OPENAL_FALLBACK) {
            basePitch = pitch;
            basePitchCaptured = true;
            basePitchSource = tickable ? BasePitchSource.MINECRAFT_TICKABLE : BasePitchSource.MINECRAFT_INITIAL;
            basePitchGameTick = gameTime;
            return true;
        }

        return false;
    }

    boolean observeOpenAlPitchFallback(float pitch, long gameTime) {
        if (!isUsablePitch(pitch) || hasAppliedDoppler || basePitchCaptured) {
            return false;
        }

        basePitch = pitch;
        basePitchCaptured = true;
        basePitchSource = BasePitchSource.OPENAL_FALLBACK;
        basePitchGameTick = gameTime;
        return true;
    }

    DopplerKinematicState sampleVelocity(Vec3 worldPosition, long gameTime, double maxSpeed, @Nullable String acousticSpaceId, long version) {
        return sampleVelocity(worldPosition, gameTime, maxSpeed, acousticSpaceId, version, VelocitySource.SOUND_POSITION_DELTA);
    }

    DopplerKinematicState sampleVelocity(Vec3 worldPosition, long gameTime, double maxSpeed, @Nullable String acousticSpaceId, long version, VelocitySource source) {
        DopplerKinematicState state = sampleVelocityInternal(worldPosition, gameTime, maxSpeed, acousticSpaceId, version, source);
        lastUpdateNanos = System.nanoTime();
        return state;
    }

    void acceptExternalVelocity(Vec3 worldPosition, Vec3 velocity, long gameTime) {
        acceptExternalVelocity(worldPosition, velocity, gameTime, VelocitySource.SABLE_SPACE);
    }

    void acceptExternalVelocity(Vec3 worldPosition, Vec3 velocity, long gameTime, VelocitySource source) {
        previousSourcePosition = lastKnownSourcePosition;
        currentSourcePosition = worldPosition;
        lastKnownSourcePosition = worldPosition;
        sampledSourceVelocity = velocity;
        lastVelocitySampleGameTick = gameTime;
        lastUpdateNanos = System.nanoTime();
        velocitySampleCount++;
        lastVelocityDtTicks = 0L;
        reliableVelocity = true;
        velocitySource = source;
        rejectedVelocityReason = "none";
    }

    void rememberAcousticSpace(@Nullable String newAcousticSpaceId, long version) {
        acousticSpaceId = newAcousticSpaceId;
        acousticSpaceVersion = version;
    }

    void rememberRadialMotion(double newRadialVelocity, double newRelativeSpeedAlongLineOfSight, double newMultiplierDelta) {
        radialVelocity = Double.isFinite(newRadialVelocity) ? newRadialVelocity : 0.0D;
        relativeSpeedAlongLineOfSight = Double.isFinite(newRelativeSpeedAlongLineOfSight) ? newRelativeSpeedAlongLineOfSight : 0.0D;
        multiplierDelta = Double.isFinite(newMultiplierDelta) ? newMultiplierDelta : 0.0D;
    }

    double smoothingDeltaSeconds(long gameTime) {
        if (lastDopplerUpdateGameTick == NO_SAMPLE || gameTime <= lastDopplerUpdateGameTick) {
            return 0.05D;
        }

        return Math.max(1L, gameTime - lastDopplerUpdateGameTick) / 20.0D;
    }

    float pitchForMultiplier(double multiplier) {
        return basePitch * (float) multiplier;
    }

    boolean shouldApplyPitch(float pitch, float threshold) {
        return !hasAppliedDoppler || Float.isNaN(lastAppliedPitch) || Math.abs(pitch - lastAppliedPitch) > threshold;
    }

    void markPitchApplied(float pitch, double multiplier, long gameTime) {
        markPitchApplied(pitch, multiplier, gameTime, PitchControlSource.NATURAL_DOPPLER);
    }

    void markPitchApplied(float pitch, double multiplier, long gameTime, PitchControlSource source) {
        targetMultiplier = multiplier;
        lastAppliedPitch = pitch;
        smoothedMultiplier = multiplier;
        lastDopplerUpdateGameTick = gameTime;
        lastUpdateNanos = System.nanoTime();
        hasAppliedDoppler = true;
        latestPitchDecision = PitchDecision.APPLIED;
        pitchControlSource = source == null ? PitchControlSource.NATURAL_DOPPLER : source;
    }

    void markPitchSkipped(double multiplier, long gameTime) {
        markPitchSkipped(multiplier, gameTime, PitchDecision.SKIPPED_MULTIPLIER_NEAR_ONE);
    }

    void markPitchSkipped(double multiplier, long gameTime, PitchDecision decision) {
        targetMultiplier = multiplier;
        smoothedMultiplier = multiplier;
        lastDopplerUpdateGameTick = gameTime;
        lastUpdateNanos = System.nanoTime();
        latestPitchDecision = decision;
        if (!hasAppliedDoppler) {
            pitchControlSource = PitchControlSource.NONE;
        }
    }

    void markSableDelegateSkipped(long gameTime) {
        lastDopplerUpdateGameTick = gameTime;
        lastUpdateNanos = System.nanoTime();
        latestPitchDecision = PitchDecision.SKIPPED_POLICY;
        pitchControlSource = PitchControlSource.SABLE_DELEGATE_SKIPPED;
    }

    void markRestored() {
        targetMultiplier = 1.0D;
        smoothedMultiplier = 1.0D;
        lastAppliedPitch = basePitch;
        lastUpdateNanos = System.nanoTime();
        hasAppliedDoppler = false;
        latestPitchDecision = PitchDecision.APPLIED;
        pitchControlSource = PitchControlSource.RESTORED;
    }

    BasePitchSource basePitchSource() {
        return basePitchSource;
    }

    long basePitchGameTick() {
        return basePitchGameTick;
    }

    @Nullable
    String acousticSpaceId() {
        return acousticSpaceId;
    }

    long acousticSpaceVersion() {
        return acousticSpaceVersion;
    }

    void rememberTargetMultiplier(double multiplier) {
        targetMultiplier = multiplier;
    }

    private DopplerKinematicState sampleVelocityInternal(Vec3 worldPosition, long gameTime, double maxSpeed, @Nullable String acousticSpaceId, long version, VelocitySource source) {
        if (lastVelocitySampleGameTick == NO_SAMPLE) {
            rememberUnreliable(worldPosition, gameTime, 0L, "initial_sample");
            return DopplerKinematicState.unreliable(worldPosition, acousticSpaceId, version);
        }

        if (gameTime <= lastVelocitySampleGameTick) {
            lastVelocityDtTicks = 0L;
            return new DopplerKinematicState(worldPosition, sampledSourceVelocity, acousticSpaceId, version, reliableVelocity);
        }

        long tickDelta = gameTime - lastVelocitySampleGameTick;
        if (tickDelta > MAX_SAMPLE_TICK_DELTA) {
            rememberUnreliable(worldPosition, gameTime, tickDelta, "sample_gap_" + tickDelta);
            return DopplerKinematicState.unreliable(worldPosition, acousticSpaceId, version);
        }

        Vec3 velocity = worldPosition.subtract(lastKnownSourcePosition).scale(20.0D / (double) tickDelta);
        if (!DopplerListenerState.isUsableVelocity(velocity, maxSpeed)) {
            rememberUnreliable(worldPosition, gameTime, tickDelta, "velocity_outlier");
            return DopplerKinematicState.unreliable(worldPosition, acousticSpaceId, version);
        }

        previousSourcePosition = lastKnownSourcePosition;
        currentSourcePosition = worldPosition;
        lastKnownSourcePosition = worldPosition;
        sampledSourceVelocity = velocity;
        lastVelocitySampleGameTick = gameTime;
        velocitySampleCount++;
        lastVelocityDtTicks = tickDelta;
        reliableVelocity = true;
        velocitySource = source;
        rejectedVelocityReason = "none";
        return new DopplerKinematicState(worldPosition, velocity, acousticSpaceId, version, true);
    }

    private void rememberUnreliable(Vec3 worldPosition, long gameTime, long tickDelta, String reason) {
        previousSourcePosition = lastKnownSourcePosition;
        currentSourcePosition = worldPosition;
        lastKnownSourcePosition = worldPosition;
        sampledSourceVelocity = Vec3.ZERO;
        lastVelocitySampleGameTick = gameTime;
        velocitySampleCount++;
        lastVelocityDtTicks = tickDelta;
        reliableVelocity = false;
        velocitySource = VelocitySource.UNRELIABLE;
        rejectedVelocityReason = reason;
    }

    private static boolean isUsablePitch(float pitch) {
        return Float.isFinite(pitch) && pitch > 0.0F;
    }

    enum BasePitchSource {
        NONE,
        MINECRAFT_INITIAL,
        MINECRAFT_TICKABLE,
        OPENAL_FALLBACK
    }

    enum PitchDecision {
        NEVER_EVALUATED,
        APPLIED,
        SKIPPED_NO_BASE_PITCH,
        SKIPPED_MULTIPLIER_NEAR_ONE,
        SKIPPED_UNRELIABLE_LISTENER,
        SKIPPED_UNRELIABLE_SOURCE,
        SKIPPED_POLICY,
        SKIPPED_SOURCE_GONE,
        SKIPPED_OPENAL_SET_FAILED
    }

    enum PitchControlSource {
        NATURAL_DOPPLER,
        FORCED_COMMAND,
        RESTORED,
        SABLE_DELEGATE_SKIPPED,
        NONE
    }

    public enum VelocitySource {
        SABLE_SPACE,
        SOUND_POSITION_DELTA,
        TICKABLE_POSITION_DELTA,
        UNRELIABLE
    }

}
