package com.sonicether.soundphysics.acoustic;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;
import com.sonicether.soundphysics.utils.RaycastUtils;
import com.sonicether.soundphysics.world.ClientLevelProxy;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcousticScenesTest {

    private static final AcousticSceneContext CONTEXT = new AcousticSceneContext(
            1,
            new Vec3(0D, 0D, 0D),
            new Vec3(1D, 0D, 0D),
            SoundSource.MASTER,
            ResourceLocation.fromNamespaceAndPath("sound_physics_test", "sound")
    );

    @TempDir
    Path tempDir;

    @Nullable
    private SoundPhysicsConfig previousConfig;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void captureConfig() {
        previousConfig = SoundPhysicsMod.CONFIG;
    }

    @AfterEach
    void resetProvider() {
        AcousticScenes.setProvider(null);
        SoundPhysicsMod.CONFIG = previousConfig;
    }

    @Test
    void rootProviderIsDefault() {
        AcousticScenes.setProvider(null);

        assertInstanceOf(RootAcousticWorldProvider.class, AcousticScenes.getProvider());
    }

    @Test
    void nullProviderResetsToRoot() {
        AcousticScenes.setProvider((client, context) -> FakeScene.INSTANCE);
        AcousticScenes.setProvider(null);

        assertInstanceOf(RootAcousticWorldProvider.class, AcousticScenes.getProvider());
    }

    @Test
    void nullSceneFallsBackToRootProvider() {
        FakeScene rootScene = new FakeScene();
        AcousticWorldProvider provider = (client, context) -> null;
        AcousticWorldProvider rootProvider = (client, context) -> rootScene;

        assertSame(rootScene, AcousticScenes.createScene(null, CONTEXT, provider, rootProvider));
    }

    @Test
    void providerExceptionFallsBackToRootProvider() {
        FakeScene rootScene = new FakeScene();
        AcousticWorldProvider provider = (client, context) -> {
            throw new IllegalStateException("boom");
        };
        AcousticWorldProvider rootProvider = (client, context) -> rootScene;

        assertSame(rootScene, AcousticScenes.createScene(null, CONTEXT, provider, rootProvider));
    }

    @Test
    void forceRootProviderBypassesActiveProvider() {
        SoundPhysicsMod.CONFIG = config();
        SoundPhysicsMod.CONFIG.forceRootAcousticProvider.set(true);
        FakeScene activeScene = new FakeScene();
        FakeScene rootScene = new FakeScene();
        AcousticWorldProvider provider = (client, context) -> activeScene;
        AcousticWorldProvider rootProvider = (client, context) -> rootScene;

        assertSame(rootScene, AcousticScenes.createScene(null, CONTEXT, provider, rootProvider));
    }

    @Test
    void rootSceneRayCastMatchesRaycastUtils() {
        FakeClientLevelProxy levelProxy = new FakeClientLevelProxy().withStone(new BlockPos(2, 0, 0));
        RootAcousticSpace rootSpace = new RootAcousticSpace(levelProxy);
        RootAcousticScene scene = new RootAcousticScene(rootSpace);
        Vec3 from = new Vec3(0D, 0.5D, 0.5D);
        Vec3 to = new Vec3(5D, 0.5D, 0.5D);

        BlockHitResult expected = RaycastUtils.rayCast(rootSpace, from, to, null);
        AcousticRayHit actual = scene.rayCast(from, to, null);

        assertEquals(expected.getType(), actual.localHit().getType());
        assertEquals(expected.getBlockPos(), actual.localHit().getBlockPos());
        assertEquals(expected.getLocation().x, actual.worldLocation().x, 1.0E-6D);
        assertSame(rootSpace, actual.space());
    }

    @Test
    void rootSceneBlockAtReturnsRootSpaceBlockRef() {
        FakeClientLevelProxy levelProxy = new FakeClientLevelProxy();
        RootAcousticSpace rootSpace = new RootAcousticSpace(levelProxy);
        RootAcousticScene scene = new RootAcousticScene(rootSpace);

        AcousticBlockRef ref = scene.blockAt(new Vec3(1.2D, 2.8D, 3.1D));

        assertSame(rootSpace, ref.space());
        assertEquals(new BlockPos(1, 2, 3), ref.pos());
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    private static final class FakeScene implements AcousticScene {
        private static final FakeScene INSTANCE = new FakeScene();

        @Override
        public AcousticRayHit rayCast(Vec3 from, Vec3 to, @Nullable AcousticBlockRef ignore) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public AcousticBlockRef blockAt(Vec3 worldPosition) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static final class FakeClientLevelProxy implements ClientLevelProxy {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        FakeClientLevelProxy withStone(BlockPos pos) {
            blocks.put(pos, Blocks.STONE.defaultBlockState());
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
