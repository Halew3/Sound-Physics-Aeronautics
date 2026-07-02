package com.sonicether.soundphysics.propeller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.openal.AL10;

class PropellerOpenAlRangeApplicationTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        PropellerLongRangeAudio.clearForTests();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void appliesMaxReferenceAndRolloffToEligibleSource() {
        SoundPhysicsMod.CONFIG = config();
        MockSourceBackend backend = new MockSourceBackend(7);
        PropellerLongRangeAudio.setSourceBackendForTests(backend);

        boolean applied = PropellerLongRangeAudio.applySourceRange(7, null, aeronauticsContext(), 1.6F, 1.0F);

        assertTrue(applied);
        assertEquals(322.152F, backend.value(7, AL10.AL_MAX_DISTANCE), 0.001F);
        assertEquals(8.0F, backend.value(7, AL10.AL_REFERENCE_DISTANCE), 0.001F);
        assertEquals(1.35F, backend.value(7, AL10.AL_ROLLOFF_FACTOR), 0.001F);
        assertEquals(1, PropellerLongRangeAudio.trackedSourceCountForTests());
    }

    @Test
    void invalidOpenAlSourceIsSkippedWithoutTracking() {
        SoundPhysicsMod.CONFIG = config();
        PropellerLongRangeAudio.setSourceBackendForTests(new MockSourceBackend());

        boolean applied = PropellerLongRangeAudio.applySourceRange(8, null, aeronauticsContext(), 1.6F, 1.0F);

        assertTrue(!applied);
        assertEquals(0, PropellerLongRangeAudio.trackedSourceCountForTests());
        assertTrue(PropellerLongRangeAudio.diagnosticsSummaryText().contains("invalidSource=1"));
    }

    private SoundPhysicsSoundPolicy.SoundContext aeronauticsContext() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_SMALL,
                SoundSource.AMBIENT,
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_CLASS,
                false,
                false,
                false,
                false,
                true
        );
    }

    private SoundPhysicsConfig config() {
        return ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
    }

    private static final class MockSourceBackend implements PropellerLongRangeAudio.SourceBackend {
        private final Set<Integer> sources = new HashSet<>();
        private final Map<Integer, Map<Integer, Float>> values = new HashMap<>();

        private MockSourceBackend(int... sourceIds) {
            for (int sourceId : sourceIds) {
                sources.add(sourceId);
            }
        }

        @Override
        public boolean sourceExists(int sourceId) {
            return sources.contains(sourceId);
        }

        @Override
        public void setFloat(int sourceId, int parameter, float value) {
            values.computeIfAbsent(sourceId, ignored -> new HashMap<>()).put(parameter, value);
        }

        @Override
        public float getFloat(int sourceId, int parameter) {
            return value(sourceId, parameter);
        }

        private float value(int sourceId, int parameter) {
            return values.getOrDefault(sourceId, Map.of()).getOrDefault(parameter, Float.NaN);
        }
    }
}
