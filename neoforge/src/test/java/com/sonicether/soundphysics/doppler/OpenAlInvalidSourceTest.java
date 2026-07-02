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

class OpenAlInvalidSourceTest {

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
    void invalidTrackedSourceIsPrunedAndDiagnosed() {
        DopplerEngine.setPitchBackendForTests(new MockPitchBackend());
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerDebug();
        DopplerEngine.onPlaySource(12, Vec3.ZERO, SoundSource.AMBIENT, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL, false, false, SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS, false, true);

        String result = DopplerEngine.forceSourceMultiplierText(12, 1.5D, 5);

        assertTrue(result.contains("OpenAL source is not live"));
        assertEquals(0, DopplerEngine.trackedSourceCount());
        assertTrue(AudioSourceRecovery.statusText().contains("invalidOpenAlSourceCount="));
        assertTrue(AudioSourceRecovery.statusText().contains("lastInvalidSourceSound=aeronautics:block.propeller_bearing.small_loop"));
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    private static final class MockPitchBackend implements DopplerEngine.PitchBackend {
        @Override
        public float getPitch(int sourceId) {
            return 1.0F;
        }

        @Override
        public void setPitch(int sourceId, float pitch) {
        }

        @Override
        public boolean sourceExists(int sourceId) {
            return false;
        }
    }
}
