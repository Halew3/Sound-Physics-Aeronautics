package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SoundPhysicsTraceTest {

    private static final ResourceLocation SOUND = ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.pling");

    @BeforeEach
    void reset() {
        SoundPhysicsTrace.reset();
    }

    @Test
    void newCountersSummarizeAndReset() {
        SoundPhysicsTrace.recordSoundEnginePlayHead(SOUND, SoundSource.BLOCKS, "test.SoundInstance");
        SoundPhysicsTrace.recordSoundEnginePlayTail(SOUND, SoundSource.BLOCKS, "test.SoundInstance");
        SoundPhysicsTrace.recordSoundSystemCaptureLastSound(SOUND, SoundSource.BLOCKS);
        SoundPhysicsTrace.recordChannelSetSelfPosition(21, new Vec3(1D, 2D, 3D));
        SoundPhysicsTrace.recordChannelPlayHead(21, new Vec3(1D, 2D, 3D), SoundSource.BLOCKS, SOUND);
        SoundPhysicsTrace.recordProcessingPath(SoundProcessingDeduper.ProcessingPath.SOURCE_MIXIN, 21, SOUND);
        SoundPhysicsTrace.recordProcessingPath(SoundProcessingDeduper.ProcessingPath.SOUND_ENGINE_FALLBACK, 21, SOUND);
        SoundPhysicsTrace.recordProcessingPath(SoundProcessingDeduper.ProcessingPath.MOVING_SOUND_UPDATE, 21, SOUND);
        SoundPhysicsTrace.recordDuplicateProcessingSkip(SoundProcessingDeduper.ProcessingPath.SOUND_ENGINE_FALLBACK, 21, SOUND);

        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("soundEnginePlayHead=1"));
        assertTrue(summary.contains("soundEnginePlayTail=1"));
        assertTrue(summary.contains("soundSystemCaptureLastSound=1"));
        assertTrue(summary.contains("channelPlayHead=1"));
        assertTrue(summary.contains("channelSetSelfPosition=1"));
        assertTrue(summary.contains("processPaths(sourceMixin=1, soundEngineFallback=1, movingSoundUpdate=1"));
        assertTrue(summary.contains("duplicateSkips(total=1"));

        SoundPhysicsTrace.reset();
        String resetSummary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(resetSummary.contains("soundEnginePlayHead=0"));
        assertTrue(resetSummary.contains("processPaths(sourceMixin=0, soundEngineFallback=0, movingSoundUpdate=0"));
        assertTrue(resetSummary.contains("duplicateSkips(total=0"));
    }

    @Test
    void nonStrictOcclusionCountersSummarizeAndReset() {
        SoundPhysicsTrace.recordNonStrictZeroOutlierIgnored(2);
        SoundPhysicsTrace.recordNonStrictZeroOutlierAccepted(3);
        SoundPhysicsTrace.recordNonStrictSelectedDirect();
        SoundPhysicsTrace.recordNonStrictSelectedMedianOrPositive();

        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("nonStrictZeroOutlierIgnored=2"));
        assertTrue(summary.contains("nonStrictZeroOutlierAccepted=3"));
        assertTrue(summary.contains("nonStrictSelectedDirect=1"));
        assertTrue(summary.contains("nonStrictSelectedMedianOrPositive=1"));

        SoundPhysicsTrace.reset();
        String resetSummary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(resetSummary.contains("nonStrictZeroOutlierIgnored=0"));
        assertTrue(resetSummary.contains("nonStrictZeroOutlierAccepted=0"));
        assertTrue(resetSummary.contains("nonStrictSelectedDirect=0"));
        assertTrue(resetSummary.contains("nonStrictSelectedMedianOrPositive=0"));
    }

}
