package com.sonicether.soundphysics;

import com.sonicether.soundphysics.debug.RaycastRenderer;
import com.sonicether.soundphysics.utils.RenderTypeUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class NeoForgeRaycastRenderEvents {

    private NeoForgeRaycastRenderEvents() {
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cameraPosition = event.getCamera().getPosition();
        RaycastRenderer.renderRays(
                RaycastRenderer.RenderHook.NEOFORGE_RENDER_STAGE,
                event.getPoseStack(),
                bufferSource,
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z
        );
        bufferSource.endBatch(RenderTypeUtils.DEBUG_LINE_STRIP);
        bufferSource.endBatch(RenderTypeUtils.DEBUG_LINE_STRIP_SEETHROUGH);
    }

}
