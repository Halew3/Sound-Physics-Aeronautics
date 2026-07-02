package com.sonicether.soundphysics.propeller;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import net.minecraft.util.Mth;

public record PropellerFarFieldEffect(
        double distanceNorm,
        double farField,
        float directCutoffMultiplier,
        float directGainMultiplier,
        double extraGameplayGain,
        double volumeCompensation,
        double openAlEstimatedGain,
        double finalEstimatedGain,
        float effectiveCutoff,
        float airAbsorption
) {

    public static PropellerFarFieldEffect disabled(float airAbsorption) {
        return new PropellerFarFieldEffect(0.0D, 0.0D, 1.0F, 1.0F, 1.0D, 1.0D, 1.0D, 1.0D, 1.0F, airAbsorption);
    }

    public static PropellerFarFieldEffect compute(
            double distance,
            double maxDistance,
            double sourceVolume,
            PropellerLongRangeParameters parameters,
            float baseAirAbsorption
    ) {
        SoundPhysicsConfig config = SoundPhysicsMod.CONFIG;
        if (config == null || !config.propellerFarFieldEnabled.get() || maxDistance <= 0.0D) {
            return disabled(baseAirAbsorption);
        }

        double distanceNorm = Mth.clamp(distance / maxDistance, 0.0D, 1.0D);
        PropellerLongRangeTuning.Profile profile = PropellerLongRangeTuning.profile(config);
        if (profile == PropellerLongRangeTuning.Profile.LEGACY) {
            double start = Mth.clamp(config.propellerFarFieldStartFraction.get(), 0.0D, 0.99D);
            double farField = smoothstep(start, 1.0D, distanceNorm);
            double cutoffAtMax = PropellerLongRangeTuning.cutoffMin(config);
            double gainAtMax = PropellerLongRangeTuning.legacyDirectGainAtMax(config);
            float directCutoff = (float) Mth.lerp(farField, 1.0D, cutoffAtMax);
            float directGain = (float) Mth.lerp(farField, 1.0D, gainAtMax);
            float airAbsorption = (float) Math.max(0.0D, baseAirAbsorption + farField * PropellerLongRangeTuning.airAbsorptionBonus(config));
            double openAlGain = estimateOpenAlGain(distance, parameters);
            return new PropellerFarFieldEffect(
                    distanceNorm,
                    farField,
                    directCutoff,
                    directGain,
                    directGain,
                    1.0D,
                    openAlGain,
                    openAlGain * directGain,
                    directCutoff,
                    airAbsorption
            );
        }

        double absoluteFar = logProgress(
                PropellerLongRangeTuning.farFieldStartBlocks(config),
                PropellerLongRangeTuning.farFieldFullBlocks(config),
                distance
        );
        double relativeFar = logProgress(
                PropellerLongRangeTuning.farFieldStartRatio(config),
                PropellerLongRangeTuning.farFieldFullRatio(config),
                distanceNorm
        );
        double farField = Math.max(absoluteFar, relativeFar);
        float directCutoff = (float) Mth.lerp(farField, 1.0D, PropellerLongRangeTuning.cutoffMin(config));
        double extraGameplayGain = gameplayGain(distance, maxDistance, config);
        double volumeCompensation = volumeCompensation(distance, sourceVolume, config);
        float directGain = (float) Mth.clamp(extraGameplayGain * volumeCompensation, 0.0D, 1.0D);
        double openAlGain = estimateOpenAlGain(distance, parameters);
        float airAbsorption = (float) Math.max(0.0D, baseAirAbsorption + farField * PropellerLongRangeTuning.airAbsorptionBonus(config));
        return new PropellerFarFieldEffect(
                distanceNorm,
                farField,
                directCutoff,
                directGain,
                extraGameplayGain,
                volumeCompensation,
                openAlGain,
                openAlGain * extraGameplayGain * volumeCompensation,
                directCutoff,
                airAbsorption
        );
    }

    static double smoothstep(double edge0, double edge1, double value) {
        if (edge1 <= edge0) {
            return value >= edge1 ? 1.0D : 0.0D;
        }
        double x = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private static double gameplayGain(double distance, double maxDistance, SoundPhysicsConfig config) {
        double closeEnd = PropellerLongRangeTuning.closeEndBlocks(config);
        double midEnd = PropellerLongRangeTuning.midEndBlocks(config);
        double midGain = Math.max(PropellerLongRangeTuning.midGain(config), 1.0E-6D);
        double farMinGain = Math.max(PropellerLongRangeTuning.farMinGain(config), 1.0E-6D);
        if (distance <= closeEnd) {
            return 1.0D;
        }
        if (distance <= midEnd) {
            return logLerp(1.0D, midGain, logProgress(closeEnd, midEnd, distance));
        }
        double farEnd = Math.max(maxDistance, midEnd + 1.0D);
        return logLerp(midGain, farMinGain, logProgress(midEnd, farEnd, distance));
    }

    private static double volumeCompensation(double distance, double sourceVolume, SoundPhysicsConfig config) {
        if (!PropellerLongRangeTuning.compensateHighSourceVolume(config) || sourceVolume <= 1.0D) {
            return 1.0D;
        }
        double closeEnd = PropellerLongRangeTuning.closeEndBlocks(config);
        double midEnd = PropellerLongRangeTuning.midEndBlocks(config);
        if (distance <= closeEnd) {
            return 1.0D;
        }
        double target = 1.0D / Math.sqrt(Math.max(sourceVolume, 1.0D));
        double distanceBlend = smoothstep(closeEnd, midEnd, distance);
        double strength = PropellerLongRangeTuning.sourceVolumeCompensationStrength(config);
        return Mth.lerp(distanceBlend * strength, 1.0D, target);
    }

    private static double estimateOpenAlGain(double distance, PropellerLongRangeParameters parameters) {
        double maxDistance = Math.max(parameters.maxDistance(), 1.0D);
        double reference = Mth.clamp(parameters.referenceDistance(), 0.0D, maxDistance);
        if (distance <= reference) {
            return 1.0D;
        }
        if (distance >= maxDistance) {
            return 0.0D;
        }
        double denominator = Math.max(maxDistance - reference, 1.0E-6D);
        double gain = 1.0D - parameters.rolloffFactor() * (distance - reference) / denominator;
        return Mth.clamp(gain, 0.0D, 1.0D);
    }

    private static double logProgress(double edge0, double edge1, double value) {
        if (edge1 <= edge0) {
            return value >= edge1 ? 1.0D : 0.0D;
        }
        if (edge0 <= 0.0D || edge1 <= 0.0D || value <= edge0) {
            return Mth.clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        }
        double progress = Math.log(value / edge0) / Math.log(edge1 / edge0);
        return Mth.clamp(progress, 0.0D, 1.0D);
    }

    private static double logLerp(double start, double end, double t) {
        return Math.exp(Mth.lerp(Mth.clamp(t, 0.0D, 1.0D), Math.log(start), Math.log(end)));
    }
}
