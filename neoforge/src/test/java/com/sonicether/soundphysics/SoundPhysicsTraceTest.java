package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SoundPhysicsTraceTest {

    private static final ResourceLocation SOUND = ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.pling");
    private static final ResourceLocation CHICKEN = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.chicken.step");
    private static final ResourceLocation COD = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.cod.ambient");
    private static final Vec3 SOURCE_POS = new Vec3(-52.75D, 76.0D, -373.625D);

    private com.sonicether.soundphysics.config.SoundPhysicsConfig previousConfig;

    @BeforeEach
    void reset() {
        previousConfig = SoundPhysicsMod.CONFIG;
        SoundPhysicsMod.CONFIG = null;
        SoundPhysicsTrace.reset();
    }

    @AfterEach
    void restore() {
        SoundPhysicsMod.CONFIG = previousConfig;
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
        SoundPhysicsTrace.recordSoundEngineFallbackSkippedRecentSourceMixin(21, SOUND);
        SoundPhysicsTrace.recordSourceBlockSelfOcclusionApplied();
        SoundPhysicsTrace.recordSourceBlockSelfOcclusionSkippedBlockSound();
        SoundPhysicsTrace.recordSourceBlockSelfOcclusionSkippedStepOrBlockEvent();
        SoundPhysicsTrace.recordSourceBlockSelfOcclusionSkippedBoundary();
        SoundPhysicsTrace.recordPreplayRawFilterWarning();
        SoundPhysicsTrace.recordPreplayFallbackApplied();
        SoundPhysicsTrace.recordPreplayFallbackSkippedNoSnapshot();
        SoundPhysicsTrace.recordSourceFilterReadbackBeforePlay(true, false);
        SoundPhysicsTrace.recordSourceFilterReadbackBeforePlay(false, true);
        SoundPhysicsTrace.recordOverloadFallbackReadback(true, false);
        SoundPhysicsTrace.recordOverloadFallbackReadback(false, true);

        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("soundEnginePlayHead=1"));
        assertTrue(summary.contains("soundEnginePlayTail=1"));
        assertTrue(summary.contains("soundSystemCaptureLastSound=1"));
        assertTrue(summary.contains("channelPlayHead=1"));
        assertTrue(summary.contains("channelSetSelfPosition=1"));
        assertTrue(summary.contains("processPaths(sourceMixin=1, soundEngineFallback=1, movingSoundUpdate=1"));
        assertTrue(summary.contains("duplicateSkips(total=1"));
        assertTrue(summary.contains("soundEngineFallbackSkippedRecentSourceMixin=1"));
        assertTrue(summary.contains("sourceBlockSelfOcclusion(applied=1, skippedBlockSound=1, skippedStepOrBlockEvent=1, skippedBoundary=1)"));
        assertTrue(summary.contains("preplayRawFilterWarnings=1"));
        assertTrue(summary.contains("preplayFallbackApplied=1"));
        assertTrue(summary.contains("preplayFallbackSkippedNoSnapshot=1"));
        assertTrue(summary.contains("sourceFilterReadbackRawBeforePlay=1"));
        assertTrue(summary.contains("sourceFilterReadbackMuffledBeforePlay=1"));
        assertTrue(summary.contains("overloadFallbackReadbackRaw=1"));
        assertTrue(summary.contains("overloadFallbackReadbackMuffled=1"));

        SoundPhysicsTrace.reset();
        String resetSummary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(resetSummary.contains("soundEnginePlayHead=0"));
        assertTrue(resetSummary.contains("processPaths(sourceMixin=0, soundEngineFallback=0, movingSoundUpdate=0"));
        assertTrue(resetSummary.contains("duplicateSkips(total=0"));
        assertTrue(resetSummary.contains("soundEngineFallbackSkippedRecentSourceMixin=0"));
        assertTrue(resetSummary.contains("sourceContextMismatches=0"));
        assertTrue(resetSummary.contains("sourceBlockSelfOcclusion(applied=0, skippedBlockSound=0, skippedStepOrBlockEvent=0, skippedBoundary=0)"));
        assertTrue(resetSummary.contains("preplayRawFilterWarnings=0"));
        assertTrue(resetSummary.contains("sourceFilterReadbackRawBeforePlay=0"));
        assertTrue(resetSummary.contains("overloadFallbackReadbackMuffled=0"));
    }

    @Test
    void nonStrictOcclusionCountersSummarizeAndReset() {
        SoundPhysicsTrace.recordNonStrictZeroOutlierIgnored(2);
        SoundPhysicsTrace.recordNonStrictSelectedDirect();
        SoundPhysicsTrace.recordNonStrictSelectedMedianOrPositive();

        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("nonStrictZeroOutlierIgnored=2"));
        assertTrue(summary.contains("nonStrictSelectedDirect=1"));
        assertTrue(summary.contains("nonStrictSelectedMedianOrPositive=1"));

        SoundPhysicsTrace.reset();
        String resetSummary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(resetSummary.contains("nonStrictZeroOutlierIgnored=0"));
        assertTrue(resetSummary.contains("nonStrictSelectedDirect=0"));
        assertTrue(resetSummary.contains("nonStrictSelectedMedianOrPositive=0"));
    }

    @Test
    void explicitSourceMixinContextDoesNotUseOverwrittenGlobalLastSound() {
        SoundPhysics.setLastSoundContext(SoundSource.NEUTRAL, CHICKEN, "test.ChickenSound", false, false, false);
        SoundPhysicsSoundPolicy.SoundContext sourceMixinContext = SoundPhysics.getLastSoundContext();
        SoundPhysics.setLastSoundContext(SoundSource.AMBIENT, COD, "test.CodSound", false, false, false);

        SoundPhysicsTrace.recordSourceMixinProcessExpected(1, SOURCE_POS, sourceMixinContext.category(), sourceMixinContext.soundId());
        SoundPhysics.onPlaySound(SOURCE_POS.x, SOURCE_POS.y, SOURCE_POS.z, 1, sourceMixinContext.category(), sourceMixinContext.soundId(), sourceMixinContext);

        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("processSound=1"));
        assertTrue(summary.contains("sourceContextMismatches=0"));
    }

    @Test
    void legacyGlobalLastSoundRaceIsCountedAsSourceContextMismatch() {
        SoundPhysics.setLastSoundContext(SoundSource.NEUTRAL, CHICKEN, "test.ChickenSound", false, false, false);
        SoundPhysicsSoundPolicy.SoundContext sourceMixinContext = SoundPhysics.getLastSoundContext();
        SoundPhysics.setLastSoundContext(SoundSource.AMBIENT, COD, "test.CodSound", false, false, false);

        SoundPhysicsTrace.recordSourceMixinProcessExpected(1, SOURCE_POS, sourceMixinContext.category(), sourceMixinContext.soundId());
        SoundPhysics.onPlaySound(SOURCE_POS.x, SOURCE_POS.y, SOURCE_POS.z, 1);

        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("processSound=1"));
        assertTrue(summary.contains("sourceContextMismatches=1"));
    }

}
