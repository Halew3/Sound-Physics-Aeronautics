package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;

class SoundProcessingPolicyTest {

    @Test
    void noteBlockBlockSoundCanReachAcousticScene() {
        ResourceLocation sound = ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.pling");

        assertFalse(SoundProcessingPolicy.isAmbientFiltered(sound, false));
        assertFalse(SoundProcessingPolicy.isRecordSkippedByMovingSoundGate(SoundSource.BLOCKS, false));
        assertFalse(SoundProcessingPolicy.isTooDistant(5D, 512D));
        assertTrue(SoundProcessingPolicy.canReachAcousticScene(
                5D,
                SoundSource.BLOCKS,
                sound,
                false,
                true,
                false,
                false,
                512D
        ));
    }

}
