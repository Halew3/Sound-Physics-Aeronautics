package com.sonicether.soundphysics.doppler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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

class DopplerProvenanceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        DopplerEngine.clearForTests();
        DiagnosticRuntimeOverrides.clear();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void forcedCommandAndRestoreExposePitchControlSource() {
        MockPitchBackend backend = new MockPitchBackend();
        backend.pitchBySource.put(19, 1.6F);
        DopplerEngine.setPitchBackendForTests(backend);
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        DopplerEngine.onPlaySource(19, Vec3.ZERO, SoundSource.AMBIENT, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE, false, false, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS, false, true);

        String forced = DopplerEngine.forceSourceMultiplierText(19, 1.5D, 5);
        String activeStatus = DopplerEngine.forceStatusText();
        String forcedSources = String.join("\n", DopplerEngine.sourcesDiagnosticsLines(DopplerEngine.SourceQuery.propeller(12)));

        assertTrue(forced.contains("finalPitch=2.400"));
        assertTrue(forced.contains("exceeds normal pitch range"));
        assertTrue(activeStatus.contains("Forced Doppler pitch active"));
        assertEquals(2.4F, backend.pitchBySource.get(19), 1.0E-6F);
        assertTrue(forcedSources.contains("pitchControlSource=FORCED_COMMAND"));
        assertTrue(forcedSources.contains("forcedPitchActive=true"));

        String restored = DopplerEngine.audibleTestOffText();
        String restoredSources = String.join("\n", DopplerEngine.sourcesDiagnosticsLines(DopplerEngine.SourceQuery.propeller(12)));

        assertTrue(restored.contains("basePitch=1.600"));
        assertTrue(restored.contains("observedPitch=1.600"));
        assertEquals(1.6F, backend.pitchBySource.get(19), 1.0E-6F);
        assertTrue(restoredSources.contains("pitchControlSource=RESTORED"));
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
