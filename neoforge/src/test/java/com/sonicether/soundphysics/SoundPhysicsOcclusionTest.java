package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        SoundPhysicsPolicyDiagnostics.reset();
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
        SoundPhysicsPolicyDiagnostics.reset();
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
    void runOcclusionCountsSolidSourceBlockWhenRaycastMisses() {
        Vec3 soundPos = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 playerPos = new Vec3(5.0D, 0.5D, 0.5D);
        RecordingMissScene scene = new RecordingMissScene(new FakeClientLevelProxy()
                .withBlock(BlockPos.ZERO, Blocks.WHITE_WOOL.defaultBlockState()));

        double occlusion = SoundPhysics.runOcclusion(scene, soundPos, playerPos);

        assertTrue(occlusion > 0.0D);
        assertEquals(1, scene.rayToPositions.size());
        assertVecEquals(playerPos, scene.rayToPositions.get(0));
    }

    @Test
    void overloadFallbackUsesNearbyProcessedSnapshot() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
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
    }

    @Test
    void overloadFallbackUsesDirectOnlyWhenNoSnapshotExists() {
        RecordingEnvironmentBackend backend = new RecordingEnvironmentBackend();
        SoundPhysics.setEnvironmentBackendForTests(backend);
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
        assertTrue(backend.environment.directCutoff() < 1F);
        assertTrue(backend.environment.directGain() > 0F);
        assertTrue(backend.environment.directGain() < 1F);
        assertTrue(SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText().contains("overloadFallback(nearestApplied=0, directOnlyApplied=1, failed=0, untouchedSkipped=0)"));
    }

    @Test
    void overloadFallbackCountersSummarizeAndReset() {
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackNearestApplied();
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackDirectOnlyApplied();
        SoundPhysicsPolicyDiagnostics.recordOverloadFallbackFailed();

        String summary = SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText();
        assertTrue(summary.contains("overloadFallback(nearestApplied=1, directOnlyApplied=1, failed=1, untouchedSkipped=1)"));

        SoundPhysicsPolicyDiagnostics.reset();
        String resetSummary = SoundPhysicsPolicyDiagnostics.diagnosticsSummaryText();
        assertTrue(resetSummary.contains("overloadFallback(nearestApplied=0, directOnlyApplied=0, failed=0, untouchedSkipped=0)"));
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

    private static final class RecordingEnvironmentBackend implements SoundPhysics.EnvironmentBackend {
        private int calls;
        private int sourceID;
        private SoundPhysics.EnvironmentParameters environment;
        @Nullable
        private SoundSource category;
        @Nullable
        private ResourceLocation sound;

        @Override
        public boolean apply(int sourceID, SoundPhysics.EnvironmentParameters environment, @Nullable SoundSource category, @Nullable ResourceLocation sound) {
            this.calls++;
            this.sourceID = sourceID;
            this.environment = environment;
            this.category = category;
            this.sound = sound;
            return true;
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
