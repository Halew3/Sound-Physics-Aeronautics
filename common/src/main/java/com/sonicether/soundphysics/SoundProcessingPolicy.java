package com.sonicether.soundphysics;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

public final class SoundProcessingPolicy {

    private SoundProcessingPolicy() {
    }

    public static boolean isTooDistant(double distance, double maxSoundProcessingDistance) {
        return distance > maxSoundProcessingDistance;
    }

    public static boolean isRecordSkippedByMovingSoundGate(SoundSource category, boolean updateMovingSounds) {
        return !updateMovingSounds && category == SoundSource.RECORDS;
    }

    public static boolean isAmbientFiltered(@Nullable ResourceLocation sound, boolean evaluateAmbientSounds) {
        return sound != null && !evaluateAmbientSounds && SoundPhysics.isAmbientSound(sound);
    }

    public static boolean canReachAcousticScene(
            double distance,
            SoundSource category,
            @Nullable ResourceLocation sound,
            boolean updateMovingSounds,
            boolean worldInitialized,
            boolean soundRateLimited,
            boolean evaluateAmbientSounds,
            double maxSoundProcessingDistance
    ) {
        return !isTooDistant(distance, maxSoundProcessingDistance)
                && !isRecordSkippedByMovingSoundGate(category, updateMovingSounds)
                && worldInitialized
                && !soundRateLimited
                && !isAmbientFiltered(sound, evaluateAmbientSounds);
    }

}
