package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.sonicether.soundphysics.AudioSourceRecovery;
import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForcedPitchPropellerAllTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        DopplerEngine.clearForTests();
        AudioSourceRecovery.resetForTests();
        DiagnosticRuntimeOverrides.clear();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void propellerAllTargetsAllKnownPropellerSources() {
        MockPitchBackend backend = backendWithSources();
        DopplerEngine.setPitchBackendForTests(backend);
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        registerTwoPropellers();

        String result = DopplerEngine.forceAllPropellersMultiplierText(1.5D, 5);

        assertTrue(result.contains("Forced 2 propeller sources"));
        assertEquals(1.5F, backend.pitchBySource.get(12), 1.0E-6F);
        assertEquals(1.5F, backend.pitchBySource.get(13), 1.0E-6F);
    }

    @Test
    void plainPropellerWarnsWhenMultipleSourcesTracked() {
        MockPitchBackend backend = backendWithSources();
        DopplerEngine.setPitchBackendForTests(backend);
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        registerTwoPropellers();

        String result = DopplerEngine.forceLatestPropellerMultiplierText(1.5D, 5);

        assertTrue(result.contains("Warning: 2 propeller sources are tracked"));
        assertTrue(result.contains("Use propeller_all"));
    }

    private void registerTwoPropellers() {
        DopplerEngine.onPlaySource(12, Vec3.ZERO, SoundSource.AMBIENT, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL, false, false, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS, false, true);
        DopplerEngine.onPlaySource(13, Vec3.ZERO, SoundSource.AMBIENT, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE, false, false, "dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate", false, true);
    }

    private MockPitchBackend backendWithSources() {
        MockPitchBackend backend = new MockPitchBackend();
        backend.pitchBySource.put(12, 1.0F);
        backend.pitchBySource.put(13, 1.0F);
        return backend;
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
