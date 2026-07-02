package com.sonicether.soundphysics.propeller;

import java.util.Locale;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import net.minecraft.util.Mth;

final class PropellerLongRangeTuning {

    private static final double OLD_MAX_DISTANCE = 1024.0D;
    private static final double OLD_SIZE_EXPONENT = 0.65D;
    private static final double OLD_RPM_EXPONENT = 0.70D;
    private static final double OLD_REFERENCE_FRACTION = 0.04D;
    private static final double OLD_REFERENCE_MIN = 24.0D;
    private static final double OLD_REFERENCE_MAX = 48.0D;
    private static final double OLD_ROLLOFF = 1.0D;
    private static final double OLD_AIR_ABSORPTION_BONUS = 0.5D;

    private static final double BALANCED_MAX_DISTANCE = 896.0D;
    private static final double BALANCED_SIZE_EXPONENT = 1.15D;
    private static final double BALANCED_RPM_EXPONENT = 0.80D;
    private static final double BALANCED_REFERENCE_FRACTION = 0.018D;
    private static final double BALANCED_REFERENCE_MIN = 8.0D;
    private static final double BALANCED_REFERENCE_MAX = 18.0D;
    private static final double BALANCED_ROLLOFF = 1.35D;
    private static final double BALANCED_AIR_ABSORPTION_BONUS = 1.75D;

    private PropellerLongRangeTuning() {
    }

    static Profile profile() {
        return profile(SoundPhysicsMod.CONFIG);
    }

    static Profile profile(@Nullable SoundPhysicsConfig config) {
        if (config == null) {
            return Profile.BALANCED;
        }
        return Profile.fromConfig(config.propellerLongRangeProfile.get());
    }

    static String profileName() {
        return profile().configName();
    }

    static double minDistance(SoundPhysicsConfig config) {
        if (profile(config) == Profile.LEGACY) {
            return 96.0D;
        }
        return Math.max(0.0D, config.propellerLongRangeMinDistance.get());
    }

    static double maxDistance(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> migratedBalanced(
                    config.propellerLongRangeMaxDistance.get(),
                    OLD_MAX_DISTANCE,
                    BALANCED_MAX_DISTANCE
            );
            case LOUD -> 1024.0D;
            case LEGACY -> OLD_MAX_DISTANCE;
        };
    }

    static double absoluteCap(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> Math.max(1.0D, config.propellerLongRangeAbsoluteCapBlocks.get());
            case LOUD, LEGACY -> OLD_MAX_DISTANCE;
        };
    }

    static double sizeReference(SoundPhysicsConfig config) {
        return Math.max(config.propellerLongRangeSizeReferenceSails.get(), 1.0D);
    }

    static double rpmReference(SoundPhysicsConfig config) {
        return Math.max(config.propellerLongRangeRpmReference.get(), 1.0D);
    }

    static double sizeExponent(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> migratedBalanced(
                    config.propellerLongRangeSizeExponent.get(),
                    OLD_SIZE_EXPONENT,
                    BALANCED_SIZE_EXPONENT
            );
            case LOUD -> 0.85D;
            case LEGACY -> OLD_SIZE_EXPONENT;
        };
    }

    static double rpmExponent(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> migratedBalanced(
                    config.propellerLongRangeRpmExponent.get(),
                    OLD_RPM_EXPONENT,
                    BALANCED_RPM_EXPONENT
            );
            case LOUD, LEGACY -> OLD_RPM_EXPONENT;
        };
    }

    static double referenceFraction(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> migratedBalanced(
                    config.propellerLongRangeReferenceDistanceFraction.get(),
                    OLD_REFERENCE_FRACTION,
                    BALANCED_REFERENCE_FRACTION
            );
            case LOUD -> 0.03D;
            case LEGACY -> OLD_REFERENCE_FRACTION;
        };
    }

    static double referenceMin(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> migratedBalanced(
                    config.propellerLongRangeReferenceDistanceMin.get(),
                    OLD_REFERENCE_MIN,
                    BALANCED_REFERENCE_MIN
            );
            case LOUD -> 12.0D;
            case LEGACY -> OLD_REFERENCE_MIN;
        };
    }

    static double referenceMax(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> migratedBalanced(
                    config.propellerLongRangeReferenceDistanceMax.get(),
                    OLD_REFERENCE_MAX,
                    BALANCED_REFERENCE_MAX
            );
            case LOUD -> 32.0D;
            case LEGACY -> OLD_REFERENCE_MAX;
        };
    }

    static double rolloff(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> migratedBalanced(
                    config.propellerLongRangeRolloffFactor.get(),
                    OLD_ROLLOFF,
                    BALANCED_ROLLOFF
            );
            case LOUD -> 1.10D;
            case LEGACY -> OLD_ROLLOFF;
        };
    }

    static double closeEndBlocks(SoundPhysicsConfig config) {
        return profile(config) == Profile.LOUD ? 64.0D : Math.max(1.0D, config.propellerLongRangeCloseEndBlocks.get());
    }

    static double midEndBlocks(SoundPhysicsConfig config) {
        return profile(config) == Profile.LOUD ? 256.0D : Math.max(closeEndBlocks(config) + 1.0D, config.propellerLongRangeMidEndBlocks.get());
    }

    static double midGain(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> Mth.clamp(config.propellerLongRangeMidGain.get(), 0.0D, 1.0D);
            case LOUD -> 0.45D;
            case LEGACY -> 1.0D;
        };
    }

    static double farMinGain(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> Mth.clamp(config.propellerLongRangeFarMinGain.get(), 0.0D, 1.0D);
            case LOUD -> 0.08D;
            case LEGACY -> 1.0D;
        };
    }

    static double farFieldStartBlocks(SoundPhysicsConfig config) {
        return profile(config) == Profile.LOUD ? 96.0D : Math.max(0.0D, config.propellerFarFieldStartBlocks.get());
    }

    static double farFieldFullBlocks(SoundPhysicsConfig config) {
        return profile(config) == Profile.LOUD ? 384.0D : Math.max(farFieldStartBlocks(config), config.propellerFarFieldFullBlocks.get());
    }

    static double farFieldStartRatio(SoundPhysicsConfig config) {
        return profile(config) == Profile.LOUD ? 0.10D : Mth.clamp(config.propellerFarFieldStartRatio.get(), 0.0D, 1.0D);
    }

    static double farFieldFullRatio(SoundPhysicsConfig config) {
        return profile(config) == Profile.LOUD ? 0.45D : Mth.clamp(config.propellerFarFieldFullRatio.get(), 0.0D, 1.0D);
    }

    static double cutoffMin(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> Mth.clamp(config.propellerFarCutoffMin.get(), 0.0D, 1.0D);
            case LOUD -> 0.40D;
            case LEGACY -> Mth.clamp(config.propellerFarFieldDirectCutoffAtMax.get(), 0.0D, 1.0D);
        };
    }

    static double legacyDirectGainAtMax(SoundPhysicsConfig config) {
        return Mth.clamp(config.propellerFarFieldDirectGainAtMax.get(), 0.0D, 1.0D);
    }

    static double airAbsorptionBonus(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> migratedBalanced(
                    config.propellerFarFieldAirAbsorptionBonus.get(),
                    OLD_AIR_ABSORPTION_BONUS,
                    BALANCED_AIR_ABSORPTION_BONUS
            );
            case LOUD -> 0.90D;
            case LEGACY -> OLD_AIR_ABSORPTION_BONUS;
        };
    }

    static boolean compensateHighSourceVolume(SoundPhysicsConfig config) {
        return profile(config) != Profile.LEGACY && config.propellerFarCompensateHighSourceVolume.get();
    }

    static double sourceVolumeCompensationStrength(SoundPhysicsConfig config) {
        return switch (profile(config)) {
            case BALANCED -> Mth.clamp(config.propellerFarSourceVolumeCompensationStrength.get(), 0.0D, 1.0D);
            case LOUD -> 0.50D;
            case LEGACY -> 0.0D;
        };
    }

    private static double migratedBalanced(double value, double oldDefault, double balancedDefault) {
        return approximately(value, oldDefault) ? balancedDefault : value;
    }

    private static boolean approximately(double left, double right) {
        return Math.abs(left - right) < 1.0E-9D;
    }

    enum Profile {
        BALANCED("balanced"),
        LOUD("loud"),
        LEGACY("legacy");

        private final String configName;

        Profile(String configName) {
            this.configName = configName;
        }

        String configName() {
            return configName;
        }

        static Profile fromConfig(@Nullable String raw) {
            if (raw == null) {
                return BALANCED;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "loud" -> LOUD;
                case "legacy" -> LEGACY;
                default -> BALANCED;
            };
        }
    }
}
