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

class ForcedPitchDriftTest {

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
    void forcedPitchStatusShowsDriftAndTickReapplies() {
        MockPitchBackend backend = new MockPitchBackend();
        backend.pitchBySource.put(19, 1.6F);
        DopplerEngine.setPitchBackendForTests(backend);
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        DopplerEngine.onPlaySource(19, Vec3.ZERO, SoundSource.AMBIENT, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE, false, false, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS, false, true);

        DopplerEngine.forceSourceMultiplierText(19, 1.5D, 5);
        backend.pitchBySource.put(19, 1.6F);

        String driftStatus = DopplerEngine.forceStatusText();
        DopplerEngine.tick();
        String reappliedStatus = DopplerEngine.forceStatusText();

        assertTrue(driftStatus.contains("expected=2.400"));
        assertTrue(driftStatus.contains("drift=true"));
        assertEquals(2.4F, backend.pitchBySource.get(19), 1.0E-6F);
        assertTrue(reappliedStatus.contains("reapplied=1"));
    }

    @Test
    void forcedPitchRestoreWorks() {
        MockPitchBackend backend = new MockPitchBackend();
        backend.pitchBySource.put(19, 1.0F);
        DopplerEngine.setPitchBackendForTests(backend);
        SoundPhysicsMod.CONFIG = config();
        DopplerEngine.onPlaySource(19, Vec3.ZERO, SoundSource.BLOCKS, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL, false, false, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS, false, true);

        DopplerEngine.forceSourceMultiplierText(19, 1.5D, 5);
        String restored = DopplerEngine.audibleTestOffText();

        assertTrue(restored.contains("Restored forced Doppler pitch"));
        assertEquals(1.0F, backend.pitchBySource.get(19), 1.0E-6F);
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
