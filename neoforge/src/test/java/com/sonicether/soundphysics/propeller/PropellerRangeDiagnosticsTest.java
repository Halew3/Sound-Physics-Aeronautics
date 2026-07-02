package com.sonicether.soundphysics.propeller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.config.SoundPhysicsConfig;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PropellerRangeDiagnosticsTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        PropellerLongRangeAudio.clearForTests();
        DiagnosticRuntimeOverrides.clear();
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void emptyDiagnosticsExplainMissingDistantSourceAndSafeMode() {
        SoundPhysicsMod.CONFIG = config();
        DiagnosticRuntimeOverrides.enablePropellerSafe();

        String joined = String.join("\n", PropellerLongRangeAudio.rangeDiagnosticsLines(8));

        assertTrue(joined.contains("propellerLongRange("));
        assertTrue(joined.contains("safe mode: acoustic/Doppler bypass active; long-range source distance applied when sources exist"));
        assertTrue(joined.contains("No tracked propeller long-range sources."));
        assertTrue(joined.contains("Separate Sable Render Distance"));
    }

    @Test
    void trackedDiagnosticsIncludeProfileFarFieldAndOpenAlReadback() {
        SoundPhysicsMod.CONFIG = config();
        MockSourceBackend backend = new MockSourceBackend(9);
        PropellerLongRangeAudio.setSourceBackendForTests(backend);
        SoundPhysicsSoundPolicy.SoundContext context = largeContext();

        PropellerLongRangeAudio.applySourceRange(9, null, context, 1.6F, 1.0F);
        String unknown = String.join("\n", PropellerLongRangeAudio.rangeDiagnosticsLines(8));
        assertTrue(unknown.contains("profile=balanced"));
        assertTrue(unknown.contains("distanceKnown=false"));
        assertTrue(unknown.contains("reason=distance_unknown"));
        assertTrue(unknown.contains("farField=unknown"));
        assertTrue(unknown.contains("directGainMultiplier=unknown"));

        PropellerLongRangeAudio.computeFarField(9, context, 120.0D, 0.1F);

        String joined = String.join("\n", PropellerLongRangeAudio.rangeDiagnosticsLines(8));

        assertTrue(joined.contains("source=9"));
        assertTrue(joined.contains("profile=balanced"));
        assertTrue(joined.contains("sailCount=48"));
        assertTrue(joined.contains("rpm=192.000"));
        assertTrue(joined.contains("computedRange=896.000"));
        assertTrue(joined.contains("distanceKnown=true"));
        assertTrue(joined.contains("distanceBlocks=120.000"));
        assertTrue(joined.contains("openAlEstimatedGain="));
        assertTrue(joined.contains("extraGameplayGain="));
        assertTrue(joined.contains("sourceVolume=1.000"));
        assertTrue(joined.contains("volumeCompensation="));
        assertTrue(joined.contains("finalEstimatedGain="));
        assertTrue(joined.contains("farField="));
        assertTrue(joined.contains("directCutoffMultiplier="));
        assertTrue(joined.contains("directGainMultiplier="));
        assertTrue(joined.contains("effectiveCutoff="));
        assertTrue(joined.contains("openAlMaxDistance=896.000"));
        assertTrue(joined.contains("openAlReferenceDistance=16.128"));
        assertTrue(joined.contains("openAlRolloff=1.350"));
    }

    private SoundPhysicsSoundPolicy.SoundContext largeContext() {
        return new SoundPhysicsSoundPolicy.SoundContext(
                SoundPhysicsSoundPolicy.AERONAUTICS_PROPELLER_LARGE,
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
            return values.getOrDefault(sourceId, Map.of()).getOrDefault(parameter, Float.NaN);
        }
    }
}
