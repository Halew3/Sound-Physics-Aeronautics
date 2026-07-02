package com.sonicether.soundphysics.doppler;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import net.minecraft.sounds.SoundSource;

public final class DopplerSoundPolicy {

    public static final String SABLE_MOVING_SOUND_INSTANCE_DELEGATE = "dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate";

    private DopplerSoundPolicy() {
    }

    public static Result evaluate(
            SoundPhysicsConfig config,
            SoundSource category,
            @Nullable String soundInstanceClassName,
            boolean relative,
            boolean noAttenuation
    ) {
        if (config == null || !config.enabled.get() || !config.dopplerEnabled.get()) {
            return Result.skip(SkipReason.DISABLED_CONFIG);
        }
        if (relative) {
            return Result.skip(SkipReason.RELATIVE);
        }
        if (noAttenuation) {
            return Result.skip(SkipReason.NO_ATTENUATION);
        }
        if (isSableDelegatedSound(soundInstanceClassName) && !config.dopplerApplyToSableDelegatedSounds.get()) {
            return Result.skip(SkipReason.SABLE_DELEGATE);
        }
        if (!isCategoryAllowed(category, config)) {
            return Result.skip(SkipReason.CATEGORY);
        }
        if (category == SoundSource.AMBIENT && !config.dopplerApplyToPositionalAmbientSounds.get()) {
            return Result.skip(SkipReason.POSITIONAL_AMBIENT);
        }

        return Result.APPLY;
    }

    public static boolean isSableDelegatedSound(@Nullable String soundInstanceClassName) {
        return SABLE_MOVING_SOUND_INSTANCE_DELEGATE.equals(soundInstanceClassName);
    }

    public static boolean isCategoryAllowed(SoundSource category, SoundPhysicsConfig config) {
        return switch (category) {
            case BLOCKS -> config.dopplerApplyToBlockSounds.get();
            case HOSTILE, NEUTRAL, PLAYERS -> config.dopplerApplyToEntitySounds.get();
            case WEATHER -> config.dopplerApplyToWeatherSounds.get();
            case AMBIENT -> config.dopplerApplyToAmbientSounds.get();
            case MUSIC -> config.dopplerApplyToMusic.get();
            case RECORDS -> config.dopplerApplyToRecords.get();
            case MASTER, VOICE -> false;
        };
    }

    public record Result(boolean apply, SkipReason skipReason) {
        private static final Result APPLY = new Result(true, SkipReason.NONE);

        private static Result skip(SkipReason reason) {
            return new Result(false, reason);
        }
    }

    public enum SkipReason {
        NONE,
        DISABLED_CONFIG,
        RELATIVE,
        NO_ATTENUATION,
        SABLE_DELEGATE,
        CATEGORY,
        POSITIONAL_AMBIENT
    }

}
