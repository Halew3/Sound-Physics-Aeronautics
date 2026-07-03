package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.acoustic.AcousticBlockRef;
import com.sonicether.soundphysics.acoustic.RootAcousticSpace;
import com.sonicether.soundphysics.config.OcclusionConfig;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;
import com.sonicether.soundphysics.world.ClientLevelProxy;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundPhysicsOcclusionTest {

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
        SoundPhysicsMod.CONFIG = ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
        SoundPhysicsMod.OCCLUSION_CONFIG = new OcclusionConfig(tempDir.resolve("occlusion.properties"));
    }

    @AfterEach
    void tearDown() {
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
    void multipleZeroVariationsCanOpenBlockedDirectRay() {
        SoundPhysics.NonStrictOcclusionSelection selection = SoundPhysics.selectNonStrictOcclusion(
                1.5D,
                new double[]{0.0D, 0.0D, 0.0D, 0.0D, 1.5D, 1.5D, 1.5D, 1.5D}
        );

        assertEquals(0.0D, selection.selectedOcclusion(), 1.0E-6D);
        assertEquals(4, selection.zeroSamples());
        assertEquals(4, selection.positiveSamples());
        assertEquals("zero_override", selection.decisionKind());
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
