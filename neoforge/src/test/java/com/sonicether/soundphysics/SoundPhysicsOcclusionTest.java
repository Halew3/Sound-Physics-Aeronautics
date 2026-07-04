package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.acoustic.AcousticBlockRef;
import com.sonicether.soundphysics.acoustic.AcousticRayHit;
import com.sonicether.soundphysics.acoustic.AcousticScene;
import com.sonicether.soundphysics.acoustic.RootAcousticSpace;
import com.sonicether.soundphysics.config.OcclusionConfig;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;
import com.sonicether.soundphysics.world.ClientLevelProxy;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundPhysicsOcclusionTest {

    private static final ResourceLocation CHICKEN = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.chicken.step");
    private static final ResourceLocation COW = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.cow.ambient");
    private static final ResourceLocation COW_STEP = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.cow.step");
    private static final ResourceLocation FISH = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.fish.swim");
    private static final ResourceLocation SAND_STEP = ResourceLocation.fromNamespaceAndPath("minecraft", "block.sand.step");
    private static final ResourceLocation STONE_PLACE = ResourceLocation.fromNamespaceAndPath("minecraft", "block.stone.place");
    private static final ResourceLocation STONE_BREAK = ResourceLocation.fromNamespaceAndPath("minecraft", "block.stone.break");
    private static final ResourceLocation STONE_HIT = ResourceLocation.fromNamespaceAndPath("minecraft", "block.stone.hit");
    private static final ResourceLocation STONE_STEP = ResourceLocation.fromNamespaceAndPath("minecraft", "block.stone.step");
    private static final ResourceLocation STONE_FALL = ResourceLocation.fromNamespaceAndPath("minecraft", "block.stone.fall");
    private static final ResourceLocation CHICKEN_AMBIENT = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.chicken.ambient");
    private static final ResourceLocation PLAYER_STEP = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.player.step");
    private static final ResourceLocation SQUID = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.squid.ambient");
    private static final ResourceLocation GENERIC_SWIM = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.generic.swim");
    private static final ResourceLocation GENERIC_SPLASH = ResourceLocation.fromNamespaceAndPath("minecraft", "entity.generic.splash");
    private static final ResourceLocation MUSIC = ResourceLocation.fromNamespaceAndPath("minecraft", "music.game");
    private static final ResourceLocation RECORD = ResourceLocation.fromNamespaceAndPath("minecraft", "music_disc.cat");
    private static final ResourceLocation AMBIENT = ResourceLocation.fromNamespaceAndPath("minecraft", "ambient.cave");
    private static final ResourceLocation UI = ResourceLocation.fromNamespaceAndPath("minecraft", "ui.button.click");

    @TempDir
    Path tempDir;

    @Nullable
    private SoundPhysicsConfig previousConfig;
    @Nullable
    private OcclusionConfig previousOcclusionConfig;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() {
        previousConfig = SoundPhysicsMod.CONFIG;
        previousOcclusionConfig = SoundPhysicsMod.OCCLUSION_CONFIG;
        SoundPhysics.resetOverloadFallbackForTests();
        SoundPhysics.resetEnvironmentBackendForTests();
        SoundPhysics.resetSourceFilterReadbackBackendForTests();
        SoundPhysics.resetDirectFilterBackendForTests();
        SoundPhysicsPolicyDiagnostics.reset();
        SoundPhysicsTrace.reset();
        SoundProcessingDeduper.reset();
        SoundPhysicsMod.CONFIG = ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
        SoundPhysicsMod.OCCLUSION_CONFIG = new OcclusionConfig(tempDir.resolve("occlusion.properties"));
    }

    @AfterEach
    void tearDown() {
        SoundPhysics.resetOverloadFallbackForTests();
        SoundPhysics.resetEnvironmentBackendForTests();
        SoundPhysics.resetSourceFilterReadbackBackendForTests();
        SoundPhysics.resetDirectFilterBackendForTests();
        SoundPhysicsPolicyDiagnostics.reset();
        SoundPhysicsTrace.reset();
        SoundProcessingDeduper.reset();
        SoundPhysicsMod.CONFIG = previousConfig;
        SoundPhysicsMod.OCCLUSION_CONFIG = previousOcclusionConfig;
    }

    @Test
    void singleZeroVariationCannotOpenBlockedDirectRay() {
        SoundPhysics.NonStrictOcclusionSelection selection = SoundPhysics.selectNonStrictOcclusion(
                1.5D,
                new double[]{1.5D, 1.5D, 0.0D, 1.5D, 1.5D, 1.5D, 1.5D, 1.5D}
        );

        assertEquals(1.5D, selection.selectedOcclusion(), 1.0E-6D);
        assertEquals(1, selection.zeroSamples());
        assertEquals(7, selection.positiveSamples());
        assertEquals("direct", selection.decisionKind());
    }

    @Test
    void allZeroVariationsCannotOpenBlockedDirectRay() {
        SoundPhysics.NonStrictOcclusionSelection selection = SoundPhysics.selectNonStrictOcclusion(
                1.5D,
                new double[]{0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D}
        );

        assertEquals(1.5D, selection.selectedOcclusion(), 1.0E-6D);
        assertEquals(8, selection.zeroSamples());
        assertEquals(0, selection.positiveSamples());
        assertEquals("direct_blocked_authoritative", selection.decisionKind());
    }

    @Test
    void mixedZeroAndPositiveVariationsCannotOpenBlockedDirectRay() {
        SoundPhysics.NonStrictOcclusionSelection selection = SoundPhysics.selectNonStrictOcclusion(
                1.5D,
                new double[]{0.0D, 0.0D, 0.0D, 1.5D, 1.5D, 1.5D, 1.5D, 1.5D}
        );

        assertTrue(selection.selectedOcclusion() > 0.0D);
        assertEquals(3, selection.zeroSamples());
        assertEquals(5, selection.positiveSamples());
    }

    @Test
    void lowerPositiveVariationMaySoftenBlockedDirectRayWithoutOpeningIt() {
        SoundPhysics.NonStrictOcclusionSelection selection = SoundPhysics.selectNonStrictOcclusion(
                1.5D,
                new double[]{0.5D, 0.5D, 0.5D, 0.5D, 0.0D, 0.0D, 1.5D, 1.5D}
        );

        assertEquals(0.5D, selection.selectedOcclusion(), 1.0E-6D);
        assertTrue(selection.selectedOcclusion() > 0.0D);
        assertEquals("median_positive", selection.decisionKind());
    }

    @Test
    void directOpenRayStaysOpenEvenWhenVariationsAreBlocked() {
        SoundPhysics.NonStrictOcclusionSelection selection = SoundPhysics.selectNonStrictOcclusion(
                0.0D,
                new double[]{1.5D, 1.5D, 1.5D, 1.5D}
        );

        assertEquals(0.0D, selection.selectedOcclusion(), 1.0E-6D);
        assertEquals("direct_open", selection.decisionKind());
    }

    @Test
    void positiveMedianIsCappedByDirectOcclusion() {
        SoundPhysics.NonStrictOcclusionSelection selection = SoundPhysics.selectNonStrictOcclusion(
                1.5D,
                new double[]{3.0D, 1.5D, 1.5D, 1.5D}
        );

        assertEquals(1.5D, selection.selectedOcclusion(), 1.0E-6D);
        assertEquals("direct", selection.decisionKind());
    }

    @Test
    void positiveMedianCanReduceBlockedDirectOcclusionWithoutOpeningIt() {
        SoundPhysics.NonStrictOcclusionSelection selection = SoundPhysics.selectNonStrictOcclusion(
                2.0D,
                new double[]{1.0D, 1.0D, 1.0D, 1.0D}
        );

        assertEquals(1.0D, selection.selectedOcclusion(), 1.0E-6D);
        assertEquals("median_positive", selection.decisionKind());
    }

    @Test
    void blockedDirectWithoutVariationsStaysBlocked() {
        SoundPhysics.NonStrictOcclusionSelection selection = SoundPhysics.selectNonStrictOcclusion(
                1.5D,
                new double[0]
        );

        assertEquals(1.5D, selection.selectedOcclusion(), 1.0E-6D);
        assertEquals("direct_blocked_authoritative", selection.decisionKind());
    }

    @Test
    void whiteWoolSourceBlockIsNotIgnored() {
        BlockPos sourcePos = BlockPos.ZERO;
        RootAcousticSpace space = new RootAcousticSpace(new FakeClientLevelProxy()
                .withBlock(sourcePos, Blocks.WHITE_WOOL.defaultBlockState()));

        assertFalse(SoundPhysics.shouldIgnoreSourceBlock(new AcousticBlockRef(space, sourcePos)));
    }

    @Test
    void airSourceBlockIsIgnored() {
        BlockPos sourcePos = BlockPos.ZERO;
        RootAcousticSpace space = new RootAcousticSpace(new FakeClientLevelProxy());

        assertTrue(SoundPhysics.shouldIgnoreSourceBlock(new AcousticBlockRef(space, sourcePos)));
    }

    @Test
    void variationSamplesKeepOriginalPlayerEndpoint() {
        Vec3 soundPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.25D, 1.0D, -2.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy());

        SoundPhysics.sampleNonStrictVariationOcclusion(scene, soundPos, playerPos, 0.49D);

        assertEquals(8, scene.rayToPositions.size());
        for (Vec3 rayTo : scene.rayToPositions) {
            assertVecEquals(playerPos, rayTo);
        }
    }

    @Test
    void runOcclusionIgnoresSolidSourceBlockWhenRaycastMisses() {
        Vec3 soundPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 0.5D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy()
                .withBlock(BlockPos.ZERO, Blocks.WHITE_WOOL.defaultBlockState()));

        double occlusion = SoundPhysics.runOcclusion(scene, soundPos, playerPos);

        assertEquals(0.0D, occlusion, 1.0E-6D);
        assertEquals(1, scene.rayToPositions.size());
        assertVecEquals(playerPos, scene.rayToPositions.get(0));
    }

    @Test
    void sandStepOnTopFaceDoesNotSelfOcclude() {
        Vec3 soundPos = new Vec3(0.5D, 1.0D - 5.0E-5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 1.0D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy()
                .withBlock(BlockPos.ZERO, Blocks.SAND.defaultBlockState()));

        double occlusion = SoundPhysics.runOcclusion(scene, soundPos, playerPos, SoundSource.BLOCKS, SAND_STEP);

        assertEquals(0.0D, occlusion, 1.0E-6D);
        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("sourceBlockSelfOcclusion(applied=0"));
        assertTrue(summary.contains("skippedBlockSound=1"));
        assertTrue(summary.contains("skippedStepOrBlockEvent=1"));
        assertTrue(summary.contains("skippedBoundary=1"));
    }

    @Test
    void stonePlaceAndBreakAtBlockCenterDoNotSelfOcclude() {
        Vec3 soundPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 0.5D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy()
                .withBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState()));

        assertEquals(0.0D, SoundPhysics.runOcclusion(scene, soundPos, playerPos, SoundSource.BLOCKS, STONE_PLACE), 1.0E-6D);
        assertEquals(0.0D, SoundPhysics.runOcclusion(scene, soundPos, playerPos, SoundSource.BLOCKS, STONE_BREAK), 1.0E-6D);

        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("sourceBlockSelfOcclusion(applied=0"));
        assertTrue(summary.contains("skippedBlockSound=2"));
        assertTrue(summary.contains("skippedStepOrBlockEvent=2"));
    }

    @Test
    void chickenAmbientInAirDoesNotSelfOcclude() {
        Vec3 soundPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 0.5D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy());

        double occlusion = SoundPhysics.runOcclusion(scene, soundPos, playerPos, SoundSource.NEUTRAL, CHICKEN_AMBIENT);

        assertEquals(0.0D, occlusion, 1.0E-6D);
        assertTrue(SoundPhysicsTrace.diagnosticsSummaryText().contains("sourceBlockSelfOcclusion(applied=0, skippedBlockSound=0, skippedStepOrBlockEvent=0, skippedBoundary=0)"));
    }

    @Test
    void pointInsideCollisionShapeUsesStrictInteriorOnly() {
        RootAcousticSpace space = new RootAcousticSpace(new FakeClientLevelProxy()
                .withBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState()));
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy()
                .withBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState()));
        AcousticBlockRef blockRef = new AcousticBlockRef(space, BlockPos.ZERO);

        assertFalse(SoundPhysics.pointInsideCollisionShape(scene, blockRef, new Vec3(0.5D, 1.0D, 0.5D)));
        assertTrue(SoundPhysics.pointInsideCollisionShape(scene, blockRef, new Vec3(0.5D, 0.5D, 0.5D)));
    }

    @Test
    void overloadFallbackUsesNearbyProcessedSnapshot() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        Vec3 originalPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 skippedPos = new Vec3(1.5D, 0.5D, 0.5D);
        SoundPhysics.EnvironmentParameters snapshot = new SoundPhysics.EnvironmentParameters(
                0.11F,
                0.12F,
                0.13F,
                0.14F,
                0.51F,
                0.52F,
                0.53F,
                0.54F,
                0.25F,
                0.75F,
                0.05F
        );

        SoundPhysics.storeEnvironmentSnapshotForTests(snapshot, originalPos, CHICKEN, SoundSource.NEUTRAL, 100L);
        boolean applied = SoundPhysics.applyCachedOverloadFallbackForTests(
                22,
                skippedPos,
                SoundSource.NEUTRAL,
                CHICKEN,
                SoundPhysicsSoundPolicy.SoundContext.of(CHICKEN, SoundSource.NEUTRAL),
                SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE,
                105L
        );

        assertTrue(applied);
        assertEquals(1, backend.calls);
        assertEquals(22, backend.sourceID);
        assertEquals(CHICKEN, backend.sound);
        assertEquals(SoundSource.NEUTRAL, backend.category);
        assertEnvironmentEquals(snapshot, backend.environment);
        assertFalse(backend.environment.directCutoff() == 1F && backend.environment.directGain() == 1F);
        assertTrue(SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText().contains("overloadFallback(nearestApplied=1, directOnlyApplied=0, failed=0, untouchedSkipped=0)"));
        assertTrue(SoundPhysicsTrace.diagnosticsSummaryText().contains("overloadFallbackReadbackRaw=0"));
        assertTrue(SoundPhysicsTrace.diagnosticsSummaryText().contains("overloadFallbackReadbackMuffled=1"));
    }

    @Test
    void overloadFallbackUsesDirectOnlyWhenNoSnapshotExists() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        Vec3 soundPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 0.5D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy()
                .withBlock(BlockPos.ZERO, Blocks.WHITE_WOOL.defaultBlockState()));

        boolean applied = SoundPhysics.applyDirectOnlyOverloadFallbackForTests(
                23,
                soundPos,
                playerPos,
                SoundSource.NEUTRAL,
                CHICKEN,
                false,
                SoundPhysicsSoundPolicy.SoundContext.of(CHICKEN, SoundSource.NEUTRAL),
                SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE,
                200L,
                scene
        );

        assertTrue(applied);
        assertEquals(1, backend.calls);
        assertEquals(0F, backend.environment.sendGain0(), 1.0E-6F);
        assertEquals(0F, backend.environment.sendGain1(), 1.0E-6F);
        assertEquals(0F, backend.environment.sendGain2(), 1.0E-6F);
        assertEquals(0F, backend.environment.sendGain3(), 1.0E-6F);
        assertEquals(1F, backend.environment.sendCutoff0(), 1.0E-6F);
        assertEquals(1F, backend.environment.directCutoff(), 1.0E-6F);
        assertEquals(1F, backend.environment.directGain(), 1.0E-6F);
        assertTrue(SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText().contains("overloadFallback(nearestApplied=0, directOnlyApplied=1, failed=0, untouchedSkipped=0)"));
    }

    @Test
    void overloadFallbackUsesExactCachedSnapshotForBlockStepEventsBeforeDirectOnly() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        Vec3 originalPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 skippedPos = new Vec3(1.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 0.5D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy());
        SoundPhysics.EnvironmentParameters badStepSnapshot = new SoundPhysics.EnvironmentParameters(
                0.11F,
                0.12F,
                0.13F,
                0.14F,
                0.51F,
                0.52F,
                0.53F,
                0.54F,
                0.05F,
                0.40F,
                0.05F
        );

        SoundPhysics.storeEnvironmentSnapshotForTests(badStepSnapshot, originalPos, SAND_STEP, SoundSource.BLOCKS, 300L);
        boolean applied = SoundPhysics.applyOverloadFallbackForTests(
                24,
                skippedPos,
                playerPos,
                SoundSource.BLOCKS,
                SAND_STEP,
                false,
                SoundPhysicsSoundPolicy.SoundContext.of(SAND_STEP, SoundSource.BLOCKS),
                SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE,
                301L,
                scene
        );

        assertTrue(applied);
        assertEquals(1, backend.calls);
        assertEnvironmentEquals(badStepSnapshot, backend.environment);
        assertTrue(SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText().contains("overloadFallback(nearestApplied=1, directOnlyApplied=0, failed=0, untouchedSkipped=0)"));
        assertTrue(SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText().contains("blockEventDirectOnlyFallbackApplied=0"));
    }

    @Test
    void directOnlyOverloadFallbackPreservesFreshFullEnvironment() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        Vec3 soundPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 0.5D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy());
        SoundPhysics.EnvironmentParameters fullEnvironment = reverbEnvironment();

        assertTrue(SoundPhysics.applyFullEnvironmentForTests(14, fullEnvironment, soundPos, SoundSource.BLOCKS, STONE_PLACE, 400L));
        boolean applied = SoundPhysics.applyDirectOnlyOverloadFallbackForTests(
                14,
                soundPos,
                playerPos,
                SoundSource.BLOCKS,
                STONE_PLACE,
                false,
                SoundPhysicsSoundPolicy.SoundContext.of(STONE_PLACE, SoundSource.BLOCKS),
                SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE,
                401L,
                scene
        );

        assertTrue(applied);
        assertEquals(1, backend.calls);
        assertEnvironmentEquals(fullEnvironment, backend.environment);
        String summary = SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText();
        assertTrue(summary.contains("overloadFallback(nearestApplied=0, directOnlyApplied=0, failed=0, untouchedSkipped=0)"));
        assertTrue(summary.contains("preservedExistingEnvironment=1"));
        assertTrue(summary.contains("duplicateFallbackWouldOverwriteReverb=1"));
        assertTrue(summary.contains("blockEventDirectOnlyFallbackApplied=0"));
    }

    @Test
    void blockEventDirectOnlyGuardPreservesFreshEnvironmentForInteractionSounds() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        Vec3 soundPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 0.5D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy());
        List<ResourceLocation> blockEvents = List.of(STONE_PLACE, STONE_BREAK, STONE_HIT, STONE_STEP, STONE_FALL);
        SoundPhysics.EnvironmentParameters fullEnvironment = reverbEnvironment();

        int source = 50;
        for (ResourceLocation sound : blockEvents) {
            assertTrue(SoundPhysics.applyFullEnvironmentForTests(source, fullEnvironment, soundPos, SoundSource.BLOCKS, sound, 500L));
            assertTrue(SoundPhysics.applyOverloadFallbackForTests(
                    source,
                    soundPos,
                    playerPos,
                    SoundSource.BLOCKS,
                    sound,
                    false,
                    SoundPhysicsSoundPolicy.SoundContext.of(sound, SoundSource.BLOCKS),
                    SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE,
                    501L,
                    scene
            ));
            source++;
        }

        assertEquals(blockEvents.size(), backend.calls);
        assertEnvironmentEquals(fullEnvironment, backend.environment);
        String summary = SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText();
        assertTrue(summary.contains("preservedExistingEnvironment=5"));
        assertTrue(summary.contains("blockEventDirectOnlyLastResort=0"));
        assertTrue(summary.contains("blockEventDirectOnlyFallbackApplied=0"));
    }

    @Test
    void blockEventFallbackSkipsTouchingSourceWhenRecentSourceMixinRecordExists() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        Vec3 soundPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 0.5D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy());

        SoundProcessingDeduper.recordSourceMixinStartProcessed(61, STONE_PLACE, SoundSource.BLOCKS, soundPos, System.nanoTime());
        boolean applied = SoundPhysics.applyOverloadFallbackForTests(
                61,
                soundPos,
                playerPos,
                SoundSource.BLOCKS,
                STONE_PLACE,
                false,
                SoundPhysicsSoundPolicy.SoundContext.of(STONE_PLACE, SoundSource.BLOCKS),
                SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE,
                600L,
                scene
        );

        assertTrue(applied);
        assertEquals(0, backend.calls);
        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("duplicateSkips(total=1"));
        assertTrue(summary.contains("soundEngineFallback=1"));
        assertTrue(summary.contains("soundEngineFallbackSkippedRecentSourceMixin=1"));
        assertTrue(SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText().contains("blockEventDirectOnlyFallbackApplied=0"));
    }

    @Test
    void blockEventDirectOnlyLastResortIsCountedWhenNoPreserveOrSnapshotExists() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        Vec3 soundPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 0.5D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy()
                .withBlock(BlockPos.ZERO, Blocks.WHITE_WOOL.defaultBlockState()));

        boolean applied = SoundPhysics.applyOverloadFallbackForTests(
                62,
                soundPos,
                playerPos,
                SoundSource.BLOCKS,
                STONE_BREAK,
                false,
                SoundPhysicsSoundPolicy.SoundContext.of(STONE_BREAK, SoundSource.BLOCKS),
                SoundPhysicsSoundPolicy.DecisionReason.SKIP_IMPACT_DEDUPE,
                700L,
                scene
        );

        assertTrue(applied);
        assertEquals(1, backend.calls);
        assertEquals(0F, backend.environment.sendGain0(), 1.0E-6F);
        assertEquals(0F, backend.environment.sendGain1(), 1.0E-6F);
        assertEquals(0F, backend.environment.sendGain2(), 1.0E-6F);
        assertEquals(0F, backend.environment.sendGain3(), 1.0E-6F);
        String summary = SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText();
        assertTrue(summary.contains("overloadFallback(nearestApplied=0, directOnlyApplied=1, failed=0, untouchedSkipped=0)"));
        assertTrue(summary.contains("blockEventDirectOnlyLastResort=1"));
        assertTrue(summary.contains("blockEventDirectOnlyFallbackApplied=1"));
    }

    @Test
    void overloadFallbackBypassesCachedSnapshotForPlayerStepEvents() {
        assertTrue(SoundPhysics.shouldBypassCachedOverloadFallback(SoundSource.PLAYERS, PLAYER_STEP));
    }

    @Test
    void perSourceDirectFiltersDoNotShareMutableState() {
        FakeDirectFilterBackend directFilters = new FakeDirectFilterBackend();
        SoundPhysics.setDirectFilterBackendForTests(directFilters);
        SoundPhysics.EnvironmentParameters muffled = new SoundPhysics.EnvironmentParameters(
                0F, 0F, 0F, 0F,
                1F, 1F, 1F, 1F,
                0.01F,
                0.40F,
                0.0F
        );
        SoundPhysics.EnvironmentParameters open = new SoundPhysics.EnvironmentParameters(
                0F, 0F, 0F, 0F,
                1F, 1F, 1F, 1F,
                1.0F,
                1.0F,
                0.0F
        );

        assertTrue(SoundPhysics.applyDirectFilterForTests(1, muffled));
        assertTrue(SoundPhysics.applyDirectFilterForTests(2, open));

        SoundPhysics.SourceFilterReadback source1 = SoundPhysics.readSourceFilterReadbackForTests(1);
        SoundPhysics.SourceFilterReadback source2 = SoundPhysics.readSourceFilterReadbackForTests(2);
        assertNotEquals(source1.directFilter(), source2.directFilter());
        assertEquals(0.40F, source1.gain(), 1.0E-6F);
        assertEquals(0.01F, source1.gainHF(), 1.0E-6F);
        assertEquals(1.0F, source2.gain(), 1.0E-6F);
        assertEquals(1.0F, source2.gainHF(), 1.0E-6F);

        SoundPhysics.EnvironmentParameters reopened = new SoundPhysics.EnvironmentParameters(
                0F, 0F, 0F, 0F,
                1F, 1F, 1F, 1F,
                0.95F,
                0.95F,
                0.0F
        );
        assertTrue(SoundPhysics.applyDirectFilterForTests(2, reopened));

        source1 = SoundPhysics.readSourceFilterReadbackForTests(1);
        source2 = SoundPhysics.readSourceFilterReadbackForTests(2);
        assertEquals(0.40F, source1.gain(), 1.0E-6F);
        assertEquals(0.01F, source1.gainHF(), 1.0E-6F);
        assertEquals(0.95F, source2.gain(), 1.0E-6F);
        assertEquals(0.95F, source2.gainHF(), 1.0E-6F);
        String status = SoundPhysics.audioStatusText();
        assertTrue(status.contains("activePerSourceDirectFilters=2"));
        assertTrue(status.contains("perSourceDirectFiltersCreated=2"));
        assertTrue(status.contains("sharedDirectFilterWrites=0"));
    }

    @Test
    void preplayFallbackDisabledByDefaultDoesNotMutateSourceEvenWithSnapshot() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        Vec3 originalPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 skippedPos = new Vec3(1.5D, 0.5D, 0.5D);
        SoundPhysics.EnvironmentParameters snapshot = new SoundPhysics.EnvironmentParameters(
                0.21F,
                0.22F,
                0.23F,
                0.24F,
                0.61F,
                0.62F,
                0.63F,
                0.64F,
                0.05F,
                0.70F,
                0.03F
        );
        SoundPhysicsSoundPolicy.SoundContext context = SoundPhysicsSoundPolicy.SoundContext.of(CHICKEN, SoundSource.NEUTRAL);

        SoundPhysics.storeEnvironmentSnapshotForTests(snapshot, originalPos, CHICKEN, SoundSource.NEUTRAL, Long.MIN_VALUE);
        SoundPhysics.beforeProcessSourceStart(31, skippedPos, SoundSource.NEUTRAL, CHICKEN, context);
        SoundPhysics.beforeAlSourcePlay(31, skippedPos, SoundSource.NEUTRAL, CHICKEN, context);

        assertEquals(0, backend.calls);
        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("preplayFallbackApplied=0"));
        assertTrue(summary.contains("sourceFilterReadbackRawBeforePlay=1"));
        assertTrue(summary.contains("sourceFilterReadbackMuffledBeforePlay=0"));
    }

    @Test
    void preplayFallbackUsesExactChickenSnapshotOnlyWhenEnabledForDebugging() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        SoundPhysicsMod.CONFIG.soundPhysicsPreplayFallbackEnabled.set(true);
        SoundPhysicsMod.CONFIG.soundPhysicsTraceLogging.set(true);
        Vec3 originalPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 skippedPos = new Vec3(1.0D, 0.5D, 0.5D);
        SoundPhysics.EnvironmentParameters snapshot = new SoundPhysics.EnvironmentParameters(
                0.21F,
                0.22F,
                0.23F,
                0.24F,
                0.61F,
                0.62F,
                0.63F,
                0.64F,
                0.05F,
                0.40F,
                0.03F
        );
        SoundPhysicsSoundPolicy.SoundContext context = SoundPhysicsSoundPolicy.SoundContext.of(CHICKEN, SoundSource.NEUTRAL);

        SoundPhysics.storeEnvironmentSnapshotForTests(snapshot, originalPos, CHICKEN, SoundSource.NEUTRAL, Long.MIN_VALUE);
        SoundPhysics.beforeProcessSourceStart(32, skippedPos, SoundSource.NEUTRAL, CHICKEN, context);
        SoundPhysics.beforeAlSourcePlay(32, skippedPos, SoundSource.NEUTRAL, CHICKEN, context);

        assertEquals(1, backend.calls);
        assertEquals(snapshot.sendGain0(), backend.environment.sendGain0(), 1.0E-6F);
        assertEquals(snapshot.sendGain1(), backend.environment.sendGain1(), 1.0E-6F);
        assertEquals(snapshot.sendGain2(), backend.environment.sendGain2(), 1.0E-6F);
        assertEquals(snapshot.sendGain3(), backend.environment.sendGain3(), 1.0E-6F);
        assertEquals(snapshot.sendCutoff0(), backend.environment.sendCutoff0(), 1.0E-6F);
        assertEquals(snapshot.directCutoff(), backend.environment.directCutoff(), 1.0E-6F);
        assertEquals(0.60F, backend.environment.directGain(), 1.0E-6F);
        assertEquals(snapshot.airAbsorption(), backend.environment.airAbsorption(), 1.0E-6F);
        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("preplayFallbackApplied=1"));
        assertTrue(summary.contains("preplayRawFilterWarnings=1"));
        assertTrue(summary.contains("sourceFilterReadbackRawBeforePlay=0"));
        assertTrue(summary.contains("sourceFilterReadbackMuffledBeforePlay=1"));
    }

    @Test
    void rawReadbackAloneDoesNotApplyPreplayFallback() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        SoundPhysicsMod.CONFIG.soundPhysicsPreplayFallbackEnabled.set(true);
        SoundPhysicsMod.CONFIG.soundPhysicsTraceLogging.set(true);
        Vec3 startPos = new Vec3(1.0D, 0.5D, 0.5D);
        SoundPhysicsSoundPolicy.SoundContext context = SoundPhysicsSoundPolicy.SoundContext.of(CHICKEN, SoundSource.NEUTRAL);

        SoundPhysics.beforeProcessSourceStart(33, startPos, SoundSource.NEUTRAL, CHICKEN, context);
        SoundPhysics.beforeAlSourcePlay(33, startPos, SoundSource.NEUTRAL, CHICKEN, context);

        assertEquals(0, backend.calls);
        String summary = SoundPhysicsTrace.diagnosticsSummaryText();
        assertTrue(summary.contains("preplayFallbackApplied=0"));
        assertTrue(summary.contains("preplayFallbackSkippedNoSnapshot=1"));
        assertTrue(summary.contains("sourceFilterReadbackRawBeforePlay=1"));
    }

    @Test
    void preplayGuardSkipsIneligibleStarts() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
        SoundPhysics.setSourceFilterReadbackBackendForTests(backend);
        SoundPhysicsMod.CONFIG.soundPhysicsPreplayFallbackEnabled.set(true);
        SoundPhysicsMod.CONFIG.soundPhysicsTraceLogging.set(true);
        Vec3 originalPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 startPos = new Vec3(1.5D, 0.5D, 0.5D);
        SoundPhysics.EnvironmentParameters snapshot = new SoundPhysics.EnvironmentParameters(
                0.21F,
                0.22F,
                0.23F,
                0.24F,
                0.61F,
                0.62F,
                0.63F,
                0.64F,
                0.05F,
                0.70F,
                0.03F
        );

        List<SoundPhysicsSoundPolicy.SoundContext> contexts = List.of(
                new SoundPhysicsSoundPolicy.SoundContext(CHICKEN, SoundSource.NEUTRAL, "test.RelativeChickenSound", true, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(CHICKEN, SoundSource.NEUTRAL, "test.NoAttenuationChickenSound", false, true, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(MUSIC, SoundSource.MUSIC, "test.MusicSound", false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(RECORD, SoundSource.RECORDS, "test.RecordSound", false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(UI, SoundSource.MASTER, "test.UiSound", false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(AMBIENT, SoundSource.AMBIENT, "test.AmbientSound", false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE, SoundSource.BLOCKS, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS, false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(CHICKEN, SoundSource.NEUTRAL, "test.StreamingChickenSound", false, false, true, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(CHICKEN, SoundSource.NEUTRAL, "test.TickableChickenSound", false, false, false, true, true),
                new SoundPhysicsSoundPolicy.SoundContext(COW, SoundSource.NEUTRAL, "test.CowSound", false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(COW_STEP, SoundSource.NEUTRAL, "test.CowStepSound", false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(FISH, SoundSource.NEUTRAL, "test.FishSwimSound", false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(SAND_STEP, SoundSource.BLOCKS, "test.SandStepSound", false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(SQUID, SoundSource.NEUTRAL, "test.SquidAmbientSound", false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(GENERIC_SWIM, SoundSource.PLAYERS, "test.GenericSwimSound", false, false, false, true, false),
                new SoundPhysicsSoundPolicy.SoundContext(GENERIC_SPLASH, SoundSource.PLAYERS, "test.GenericSplashSound", false, false, false, true, false)
        );
        for (SoundPhysicsSoundPolicy.SoundContext context : contexts) {
            SoundPhysics.storeEnvironmentSnapshotForTests(snapshot, originalPos, context.soundId(), context.category(), Long.MIN_VALUE);
        }

        int source = 40;
        for (SoundPhysicsSoundPolicy.SoundContext context : contexts) {
            SoundPhysics.beforeProcessSourceStart(source, startPos, context.category(), context.soundId(), context);
            SoundPhysics.beforeAlSourcePlay(source, startPos, context.category(), context.soundId(), context);
            source++;
        }

        assertEquals(0, backend.calls);
    }

    @Test
    void overloadFallbackCountersSummarizeAndReset() {
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackNearestApplied();
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackDirectOnlyApplied();
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackFailed();
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackPreservedExistingEnvironment();
        SoundPhysicsPolicyDiagnostics.recordBlockEventDirectOnlyLastResort();
        SoundPhysicsPolicyDiagnostics.recordBlockEventDirectOnlyFallbackApplied();
        SoundPhysicsPolicyDiagnostics.recordDuplicateFallbackWouldOverwriteReverb();

        String summary = SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText();
        assertTrue(summary.contains("overloadFallback(nearestApplied=1, directOnlyApplied=1, failed=1, untouchedSkipped=1)"));
        assertTrue(summary.contains("overloadFallbackGuards(preservedExistingEnvironment=1, blockEventDirectOnlyLastResort=1, blockEventDirectOnlyFallbackApplied=1, duplicateFallbackWouldOverwriteReverb=1)"));

        SoundPhysicsPolicyDiagnostics.reset();
        String resetSummary = SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText();
        assertTrue(resetSummary.contains("overloadFallback(nearestApplied=0, directOnlyApplied=0, failed=0, untouchedSkipped=0)"));
        assertTrue(resetSummary.contains("overloadFallbackGuards(preservedExistingEnvironment=0, blockEventDirectOnlyLastResort=0, blockEventDirectOnlyFallbackApplied=0, duplicateFallbackWouldOverwriteReverb=0)"));
    }

    private static SoundPhysics.EnvironmentParameters reverbEnvironment() {
        return new SoundPhysics.EnvironmentParameters(
                0.31F,
                0.42F,
                0.53F,
                0.64F,
                0.71F,
                0.72F,
                0.73F,
                0.74F,
                0.81F,
                0.91F,
                0.02F
        );
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-6D);
        assertEquals(expected.y, actual.y, 1.0E-6D);
        assertEquals(expected.z, actual.z, 1.0E-6D);
    }

    private static void assertEnvironmentEquals(SoundPhysics.EnvironmentParameters expected, SoundPhysics.EnvironmentParameters actual) {
        assertEquals(expected.sendGain0(), actual.sendGain0(), 1.0E-6F);
        assertEquals(expected.sendGain1(), actual.sendGain1(), 1.0E-6F);
        assertEquals(expected.sendGain2(), actual.sendGain2(), 1.0E-6F);
        assertEquals(expected.sendGain3(), actual.sendGain3(), 1.0E-6F);
        assertEquals(expected.sendCutoff0(), actual.sendCutoff0(), 1.0E-6F);
        assertEquals(expected.sendCutoff1(), actual.sendCutoff1(), 1.0E-6F);
        assertEquals(expected.sendCutoff2(), actual.sendCutoff2(), 1.0E-6F);
        assertEquals(expected.sendCutoff3(), actual.sendCutoff3(), 1.0E-6F);
        assertEquals(expected.directCutoff(), actual.directCutoff(), 1.0E-6F);
        assertEquals(expected.directGain(), actual.directGain(), 1.0E-6F);
        assertEquals(expected.airAbsorption(), actual.airAbsorption(), 1.0E-6F);
    }

    private static final class FakeDirectFilterBackend implements SoundPhysics.DirectFilterBackend {
        private int nextFilter = 100;
        private final Map<Integer, float[]> filters = new HashMap<>();
        private final Map<Integer, Integer> sourceFilters = new HashMap<>();

        @Override
        public int createLowpassFilter() {
            int filter = nextFilter;
            nextFilter++;
            filters.put(filter, new float[]{1F, 1F});
            return filter;
        }

        @Override
        public void setLowpass(int filter, float gain, float gainHF) {
            filters.put(filter, new float[]{gain, gainHF});
        }

        @Override
        public void attachDirectFilter(int sourceID, int filter) {
            sourceFilters.put(sourceID, filter);
        }

        @Override
        public void deleteFilter(int filter) {
            filters.remove(filter);
        }

        @Override
        public SoundPhysics.SourceFilterReadback readFilterObject(int directFilter, int sourceState) {
            float[] values = filters.get(directFilter);
            if (values == null) {
                return SoundPhysics.SourceFilterReadback.unavailable();
            }
            return SoundPhysics.SourceFilterReadback.lowpass(directFilter, values[0], values[1], sourceState);
        }
    }

    private static final class RecordingEnvironmentBackend implements SoundPhysics.EnvironmentBackend, SoundPhysics.SourceFilterReadbackBackend {
        private int calls;
        private int sourceID;
        private SoundPhysics.EnvironmentParameters environment;
        private final Map<Integer, SoundPhysics.EnvironmentParameters> environments = new HashMap<>();
        @Nullable
        private SoundSource category;
        @Nullable
        private ResourceLocation sound;

        @Override
        public boolean apply(int sourceID, SoundPhysics.EnvironmentParameters environment, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
            this.calls++;
            this.sourceID = sourceID;
            this.environment = environment;
            this.environments.put(sourceID, environment);
            this.category = category;
            this.sound = sound;
            return true;
        }

        @Override
        public SoundPhysics.SourceFilterReadback read(int sourceID, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
            SoundPhysics.EnvironmentParameters environment = environments.get(sourceID);
            if (environment == null) {
                return SoundPhysics.SourceFilterReadback.noFilter(0);
            }
            return SoundPhysics.SourceFilterReadback.lowpass(sourceID + 1000, environment.directGain(), environment.directCutoff(), 0);
        }
    }

    private static final class RecordingMissScene implements AcousticScene {
        private final RootAcousticSpace space;
        private final List<Vec3> rayToPositions = new ArrayList<>();

        private RecordingMissScene(FakeClientLevelProxy levelProxy) {
            space = new RootAcousticSpace(levelProxy);
        }

        @Override
        public AcousticRayHit rayCast(Vec3 from, Vec3 to, @Nullable AcousticBlockRef ignore) {
            rayToPositions.add(to);
            BlockHitResult miss = BlockHitResult.miss(to, Direction.getNearest(from.subtract(to)), BlockPos.containing(to));
            return new AcousticRayHit(miss, space, to, Vec3.ZERO);
        }

        @Override
        public AcousticBlockRef blockAt(Vec3 worldPosition) {
            return new AcousticBlockRef(space, BlockPos.containing(worldPosition));
        }
    }

    private static final class FakeClientLevelProxy implements ClientLevelProxy {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        FakeClientLevelProxy withBlock(BlockPos pos, BlockState state) {
            blocks.put(pos, state);
            return this;
        }

        @Override
        @Nullable
        public BlockEntity getBlockEntity(BlockPos blockPos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos blockPos) {
            return blocks.getOrDefault(blockPos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos blockPos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

}
