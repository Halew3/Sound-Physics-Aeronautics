package com.sonicether.soundphysics.acoustic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class AcousticOverrideTest {

    private static final AcousticSceneContext CONTEXT = new AcousticSceneContext(
            1,
            new Vec3(0D, 0D, 0D),
            new Vec3(1D, 0D, 0D),
            SoundSource.BLOCKS,
            ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.pling")
    );

    @TempDir
    Path tempDir;

    @Nullable
    private SoundPhysicsConfig previousConfig;

    @BeforeEach
    void captureConfig() {
        previousConfig = SoundPhysicsMod.CONFIG;
        DiagnosticRuntimeOverrides.clear();
    }

    @AfterEach
    void reset() {
        AcousticScenes.setProvider(null);
        DiagnosticRuntimeOverrides.clear();
        SoundPhysicsMod.CONFIG = previousConfig;
    }

    @Test
    void rootDebugOverrideForcesRootProvider() {
        SoundPhysicsMod.CONFIG = config();
        FakeScene activeScene = new FakeScene();
        FakeScene rootScene = new FakeScene();
        AcousticWorldProvider provider = (client, context) -> activeScene;
        AcousticWorldProvider rootProvider = (client, context) -> rootScene;

        DiagnosticRuntimeOverrides.enableRootDebug();

        assertSame(rootScene, AcousticScenes.createScene(null, CONTEXT, provider, rootProvider));
        assertTrue(DiagnosticRuntimeOverrides.soundPhysicsEnabled(SoundPhysicsMod.CONFIG));
        assertFalse(DiagnosticRuntimeOverrides.sableAcousticsEnabled(SoundPhysicsMod.CONFIG));
        assertFalse(DiagnosticRuntimeOverrides.dopplerEnabled(SoundPhysicsMod.CONFIG));
        assertTrue(DiagnosticRuntimeOverrides.renderOcclusion(SoundPhysicsMod.CONFIG));
    }

    @Test
    void normalClearsOverride() {
        SoundPhysicsMod.CONFIG = config();
        FakeScene activeScene = new FakeScene();
        FakeScene rootScene = new FakeScene();
        AcousticWorldProvider provider = (client, context) -> activeScene;
        AcousticWorldProvider rootProvider = (client, context) -> rootScene;

        DiagnosticRuntimeOverrides.enableRootDebug();
        DiagnosticRuntimeOverrides.clear();

        assertSame(activeScene, AcousticScenes.createScene(null, CONTEXT, provider, rootProvider));
        assertTrue(DiagnosticRuntimeOverrides.sableAcousticsEnabled(SoundPhysicsMod.CONFIG));
        assertTrue(DiagnosticRuntimeOverrides.dopplerEnabled(SoundPhysicsMod.CONFIG));
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    private static final class FakeScene implements AcousticScene {
        @Override
        public AcousticRayHit rayCast(Vec3 from, Vec3 to, @Nullable AcousticBlockRef ignore) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public AcousticBlockRef blockAt(Vec3 worldPosition) {
            throw new UnsupportedOperationException("not used");
        }
    }

}
