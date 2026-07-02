package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.sonicether.soundphysics.config.SoundPhysicsConfig;
import com.sonicether.soundphysics.debug.RaycastRenderer;

import de.maxhenkel.configbuilder.ConfigBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetCommandTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        DiagnosticRuntimeOverrides.clear();
        RuntimeLoggingController.resetForTests();
        RaycastRenderer.resetDiagnostics();
        RaycastRenderer.setRenderMode(RaycastRenderer.RenderMode.CONFIG);
        RaycastRenderer.setRayFilter(RaycastRenderer.RayFilter.NONE);
        SoundPhysicsMod.CONFIG = null;
    }

    @Test
    void quietPresetBuildingBlocksDisableLoggingAndRays() {
        SoundPhysicsConfig config = config();
        config.debugLogging.set(true);
        config.dopplerDebugLogging.set(true);
        DiagnosticRuntimeOverrides.enablePropellerDebug();

        DiagnosticRuntimeOverrides.clear();
        RuntimeLoggingController.quiet(config);
        RaycastRenderer.setRenderMode(RaycastRenderer.RenderMode.OFF);
        RaycastRenderer.clearRays();

        assertFalse(config.debugLogging.get());
        assertFalse(config.dopplerDebugLogging.get());
        assertFalse(RuntimeLoggingController.verboseActive());
        assertEquals("normal", DiagnosticRuntimeOverrides.mode().commandName());
        assertEquals(RaycastRenderer.RenderMode.OFF, RaycastRenderer.renderMode());
    }

    @Test
    void propellerAndRecordPresetFiltersUseExpectedRayFilters() {
        RaycastRenderer.setRayFilter(RaycastRenderer.RayFilter.PROPELLER);
        assertEquals(RaycastRenderer.RayFilter.PROPELLER, RaycastRenderer.rayFilter());

        RaycastRenderer.setRayFilter(RaycastRenderer.RayFilter.RECORDS);
        assertEquals(RaycastRenderer.RayFilter.RECORDS, RaycastRenderer.rayFilter());
    }

    @Test
    void dopplerPresetUsesPropellerDebugMode() {
        DiagnosticRuntimeOverrides.enablePropellerDebug();

        assertTrue(DiagnosticRuntimeOverrides.propellerDebugMode());
        assertEquals("propeller_debug", DiagnosticRuntimeOverrides.mode().commandName());
    }

    private SoundPhysicsConfig config() {
        SoundPhysicsConfig config = ConfigBuilder.builder(SoundPhysicsConfig::new)
                .path(tempDir.resolve("soundphysics.properties"))
                .saveAfterBuild(false)
                .build();
        SoundPhysicsMod.CONFIG = config;
        return config;
    }
}
