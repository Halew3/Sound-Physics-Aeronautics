package com.sonicether.soundphysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sonicether.soundphysics.debug.RaycastRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RaysCommandTest {

    @AfterEach
    void reset() {
        RaycastRenderer.resetDiagnostics();
        RaycastRenderer.setRenderMode(RaycastRenderer.RenderMode.CONFIG);
        RaycastRenderer.setRayFilter(RaycastRenderer.RayFilter.NONE);
        RuntimeLoggingController.resetForTests();
    }

    @Test
    void clearClearsQueuedRays() {
        RaycastRenderer.clearRays();

        assertEquals(0, RaycastRenderer.queuedRaysForTests());
    }

    @Test
    void modeAndFilterCommandsChangeRuntimeState() {
        RaycastRenderer.setRenderMode(RaycastRenderer.RenderMode.BOTH);
        RaycastRenderer.setRayFilter(RaycastRenderer.RayFilter.PROPELLER);

        assertEquals(RaycastRenderer.RenderMode.BOTH, RaycastRenderer.renderMode());
        assertEquals(RaycastRenderer.RayFilter.PROPELLER, RaycastRenderer.rayFilter());
        assertTrue(RaycastRenderer.diagnosticsSummaryText().contains("mode=BOTH"));
        assertTrue(RaycastRenderer.diagnosticsSummaryText().contains("filter=PROPELLER"));
    }

    @Test
    void rayRenderControlsDoNotRequireTraceLogging() {
        RaycastRenderer.setRenderMode(RaycastRenderer.RenderMode.OCCLUSION);
        RaycastRenderer.setRayFilter(RaycastRenderer.RayFilter.LATEST);

        assertFalse(RuntimeLoggingController.verboseActive());
        assertEquals(RaycastRenderer.RenderMode.OCCLUSION, RaycastRenderer.renderMode());
        assertEquals(RaycastRenderer.RayFilter.LATEST, RaycastRenderer.rayFilter());
    }

}
