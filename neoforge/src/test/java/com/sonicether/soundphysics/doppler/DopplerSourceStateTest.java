package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DopplerSourceStateTest {

    private static final ResourceLocation SOUND = ResourceLocation.fromNamespaceAndPath("sound_physics_test", "source");
    private static final ResourceLocation REUSED_SOUND = ResourceLocation.fromNamespaceAndPath("sound_physics_test", "reused");

    @Test
    void basePitchIsPreserved() {
        DopplerSourceState state = state();
        state.observeMinecraftPitch(1.25F, false, 1L);

        assertEquals(1.50F, state.pitchForMultiplier(1.2D), 1.0E-6F);
    }

    @Test
    void repeatedUpdateDoesNotCompoundPitch() {
        DopplerSourceState state = state();
        state.observeMinecraftPitch(1.2F, false, 1L);

        float firstPitch = state.pitchForMultiplier(1.1D);
        state.markPitchApplied(firstPitch, 1.1D, 1L);
        float secondPitch = state.pitchForMultiplier(1.1D);

        assertEquals(firstPitch, secondPitch, 1.0E-6F);
        assertEquals(1.32F, secondPitch, 1.0E-6F);
    }

    @Test
    void sourceIdReuseResetsState() {
        DopplerSourceState state = state();
        state.observeMinecraftPitch(1.3F, false, 1L);
        state.markPitchApplied(state.pitchForMultiplier(1.2D), 1.2D, 1L);

        state.resetForReuse(REUSED_SOUND, SoundSource.BLOCKS, new Vec3(5.0D, 0.0D, 0.0D));

        assertEquals(1.0F, state.basePitch(), 1.0E-6F);
        assertEquals(1.0D, state.smoothedMultiplier(), 1.0E-9D);
        assertFalse(state.hasAppliedDoppler());
        assertFalse(state.hasBasePitchCaptured());
        assertEquals(REUSED_SOUND, state.sound());
    }

    @Test
    void firstActiveTickEstablishesBasePitchFromMinecraftPitch() {
        DopplerSourceState state = state();

        assertTrue(state.observeMinecraftPitch(1.35F, false, 42L));

        assertEquals(1.35F, state.basePitch(), 1.0E-6F);
        assertEquals(DopplerSourceState.BasePitchSource.MINECRAFT_INITIAL, state.basePitchSource());
        assertEquals(42L, state.basePitchGameTick());
    }

    @Test
    void nonTickableMinecraftPitchDoesNotOverwriteCapturedBasePitch() {
        DopplerSourceState state = state();
        state.observeMinecraftPitch(1.10F, false, 1L);
        state.markPitchApplied(state.pitchForMultiplier(1.2D), 1.2D, 1L);

        assertFalse(state.observeMinecraftPitch(1.70F, false, 2L));

        assertEquals(1.10F, state.basePitch(), 1.0E-6F);
        assertEquals(1.32F, state.pitchForMultiplier(1.2D), 1.0E-6F);
    }

    @Test
    void tickableMinecraftPitchCanChangeBasePitchAfterDopplerApplied() {
        DopplerSourceState state = state();
        state.observeMinecraftPitch(1.00F, true, 1L);
        state.markPitchApplied(state.pitchForMultiplier(1.25D), 1.25D, 1L);

        assertTrue(state.observeMinecraftPitch(1.40F, true, 2L));

        assertEquals(1.40F, state.basePitch(), 1.0E-6F);
        assertEquals(DopplerSourceState.BasePitchSource.MINECRAFT_TICKABLE, state.basePitchSource());
        assertEquals(1.75F, state.pitchForMultiplier(1.25D), 1.0E-6F);
    }

    @Test
    void minecraftPitchOverridesOpenAlFallbackBeforeDopplerApplies() {
        DopplerSourceState state = state();
        assertTrue(state.observeOpenAlPitchFallback(0.85F, 1L));

        assertTrue(state.observeMinecraftPitch(1.15F, false, 2L));

        assertEquals(1.15F, state.basePitch(), 1.0E-6F);
        assertEquals(DopplerSourceState.BasePitchSource.MINECRAFT_INITIAL, state.basePitchSource());
    }

    @Test
    void firstSampleIsUnreliableAndNeutral() {
        DopplerSourceState state = state();

        DopplerKinematicState sample = state.sampleVelocity(new Vec3(10.0D, 0.0D, 0.0D), 42L, 350.0D, "root", 0L);

        assertFalse(sample.reliable());
        assertEquals(0.0D, sample.worldVelocity().lengthSqr(), 1.0E-12D);
    }

    private static DopplerSourceState state() {
        return new DopplerSourceState(7, SOUND, SoundSource.BLOCKS, Vec3.ZERO);
    }

}
