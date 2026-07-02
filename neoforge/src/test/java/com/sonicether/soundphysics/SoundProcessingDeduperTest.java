package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundProcessingDeduperTest {

    private static final ResourceLocation SOUND = ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.pling");

    @TempDir
    Path tempDir;

    @BeforeEach
    void reset() {
        SoundPhysicsTrace.reset();
        SoundProcessingDeduper.reset();
        SoundPhysicsPerfDiagnostics.reset();
    }

    @Test
    void sourceMixinThenFallbackSameSourceSoundTickDoesNotDoubleProcess() {
        assertTrue(SoundProcessingDeduper.shouldProcessStart(11, 100L, SoundSource.BLOCKS, SOUND, SoundProcessingDeduper.ProcessingPath.SOURCE_MIXIN));
        assertFalse(SoundProcessingDeduper.shouldProcessStart(11, 100L, SoundSource.BLOCKS, SOUND, SoundProcessingDeduper.ProcessingPath.SOUND_ENGINE_FALLBACK));

        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("duplicateSkips(total=1"));
        assertTrue(summary.contains("soundEngineFallback=1"));
    }

    @Test
    void fallbackAloneProcessesOnce() {
        assertTrue(SoundProcessingDeduper.shouldProcessStart(12, 100L, SoundSource.BLOCKS, SOUND, SoundProcessingDeduper.ProcessingPath.SOUND_ENGINE_FALLBACK));
        assertFalse(SoundProcessingDeduper.shouldProcessStart(12, 100L, SoundSource.BLOCKS, SOUND, SoundProcessingDeduper.ProcessingPath.SOUND_ENGINE_FALLBACK));
    }

    @Test
    void movingUpdateCanReevaluateLater() {
        assertTrue(SoundProcessingDeduper.shouldProcessStart(13, 100L, SoundSource.BLOCKS, SOUND, SoundProcessingDeduper.ProcessingPath.SOURCE_MIXIN));
        assertTrue(SoundProcessingDeduper.shouldProcessMovingUpdate(13, 105L, SoundSource.BLOCKS, SOUND));
    }

    @Test
    void impactBurstDedupeOnlyCoalescesNearRepeatsInsideWindow() {
        assertTrue(SoundProcessingDeduper.shouldProcessImpactBurst(100L, SoundSource.BLOCKS, SOUND, new Vec3(0D, 0D, 0D), 1.5D, 2));
        assertFalse(SoundProcessingDeduper.shouldProcessImpactBurst(101L, SoundSource.BLOCKS, SOUND, new Vec3(0.2D, 0D, 0D), 1.5D, 2));
        assertTrue(SoundProcessingDeduper.shouldProcessImpactBurst(101L, SoundSource.BLOCKS, SOUND, new Vec3(4D, 0D, 0D), 1.5D, 2));
        assertTrue(SoundProcessingDeduper.shouldProcessImpactBurst(104L, SoundSource.BLOCKS, SOUND, new Vec3(0D, 0D, 0D), 1.5D, 2));

        assertTrue(SoundPhysicsPerfDiagnostics.summaryText().contains("impactDeduped=1"));
    }

    @Test
    void policyExemptsContinuousLoopsFromImpactBurstDedupeByDefault() {
        SoundPhysicsConfig config = config();

        assertTrue(SoundPhysicsSoundPolicy.isImpactBurstDedupeExempt(config, knownPropeller()));
        assertTrue(SoundPhysicsSoundPolicy.isImpactBurstDedupeExempt(config, tickableLoop()));
        assertTrue(SoundPhysicsSoundPolicy.isImpactBurstDedupeExempt(config, recordLoop()));
    }

    @Test
    void policyDoesNotExemptOrdinaryOneShotImpactSounds() {
        SoundPhysicsConfig config = config();

        assertFalse(SoundPhysicsSoundPolicy.isImpactBurstDedupeExempt(config, oneShotBlock()));
        assertFalse(SoundPhysicsSoundPolicy.isStartThrottleExempt(oneShotBlock()));
    }

    private SoundPhysicsSoundPolicy.SoundContext knownPropeller() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE,
                SoundSource.AMBIENT,
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS,
                false,
                false,
                false,
                true,
                true
        );
    }

    private SoundPhysicsSoundPolicy.SoundContext tickableLoop() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("example", "machine.loop"),
                SoundSource.BLOCKS,
                "example.MachineLoopSound",
                false,
                false,
                false,
                true,
                true
        );
    }

    private SoundPhysicsSoundPolicy.SoundContext recordLoop() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                ResourceLocation.fromNamespaceAndPath("minecraft", "music_disc.cat"),
                SoundSource.RECORDS,
                "example.RecordSound",
                false,
                false,
                true,
                true,
                false
        );
    }

    private SoundPhysicsSoundPolicy.SoundContext oneShotBlock() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                SOUND,
                SoundSource.BLOCKS,
                "example.BlockImpactSound",
                false,
                false,
                false,
                true,
                false
        );
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

}
