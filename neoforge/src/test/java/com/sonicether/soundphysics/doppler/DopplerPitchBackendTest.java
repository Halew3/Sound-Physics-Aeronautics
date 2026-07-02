package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DopplerPitchBackendTest {

    private static final ResourceLocation SOUND = ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.pling");

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        DopplerEngine.clearForTests();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void forceMultiplierAppliesAndRestoresPitch() {
        MockPitchBackend backend = new MockPitchBackend();
        backend.pitchBySource.put(19, 1.0F);
        DopplerEngine.setPitchBackendForTests(backend);
        SoundPhysicsMod.CONFIG = config();
        DopplerEngine.onPlaySource(19, Vec3.ZERO, SoundSource.BLOCKS, SOUND, false, false, "example.Sound", false, false);

        String forced = DopplerEngine.forceSourceMultiplierText(19, 1.5D, 5);

        assertTrue(forced.contains("setPitch=true"));
        assertEquals(1.5F, backend.pitchBySource.get(19), 1.0E-6F);

        String restored = DopplerEngine.audibleTestOffText();

        assertTrue(restored.contains("Restored forced Doppler pitch"));
        assertEquals(1.0F, backend.pitchBySource.get(19), 1.0E-6F);
    }

    @Test
    void forceMultiplierReportsMissingSource() {
        DopplerEngine.setPitchBackendForTests(new MockPitchBackend());

        String result = DopplerEngine.forceSourceMultiplierText(404, 1.5D, 5);

        assertTrue(result.contains("No tracked Doppler source exists"));
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    private static final class MockPitchBackend implements DopplerEngine.PitchBackend {
        private final Map<Integer, Float> pitchBySource = new HashMap<>();

        @Override
        public float getPitch(int sourceId) {
            return pitchBySource.getOrDefault(sourceId, 1.0F);
        }

        @Override
        public void setPitch(int sourceId, float pitch) {
            pitchBySource.put(sourceId, pitch);
        }

        @Override
        public boolean sourceExists(int sourceId) {
            return pitchBySource.containsKey(sourceId);
        }
    }

}
