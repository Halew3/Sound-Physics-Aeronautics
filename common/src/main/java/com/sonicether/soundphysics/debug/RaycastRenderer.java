package com.sonicether.soundphysics.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sonicether.soundphysics.DiagnosticRuntimeOverrides;
import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.SoundPhysicsSoundPolicy;
import com.sonicether.soundphysics.SoundPhysicsPerfDiagnostics;
import com.sonicether.soundphysics.utils.RenderTypeUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class RaycastRenderer {

    private static final List<Ray> rays = Collections.synchronizedList(new ArrayList<>());
    private static final Minecraft mc = Minecraft.getInstance();
    private static final AtomicLong soundBounceRayAddRequests = new AtomicLong();
    private static final AtomicLong occlusionRayAddRequests = new AtomicLong();
    private static final AtomicLong raysAdded = new AtomicLong();
    private static final AtomicLong raysFilteredByConfig = new AtomicLong();
    private static final AtomicLong raysFilteredByDistance = new AtomicLong();
    private static final AtomicLong raysFilteredMissingClient = new AtomicLong();
    private static final AtomicLong renderCalls = new AtomicLong();
    private static final AtomicLong debugRendererRenderCalls = new AtomicLong();
    private static final AtomicLong externalRenderCalls = new AtomicLong();
    private static final AtomicLong renderSkippedMissingLevel = new AtomicLong();
    private static final AtomicLong renderSkippedDisabledConfig = new AtomicLong();
    private static final AtomicLong raysExpired = new AtomicLong();
    private static final AtomicLong raysDrawn = new AtomicLong();
    private static final AtomicLong raysFilteredByRayFilter = new AtomicLong();
    private static final AtomicLong contextSequence = new AtomicLong();
    private static final ThreadLocal<RayContext> CURRENT_CONTEXT = ThreadLocal.withInitial(() -> new RayContext(RayTag.GENERAL, 0L));
    private static volatile boolean externalRenderHookInstalled;
    private static volatile RenderMode renderMode = RenderMode.CONFIG;
    private static volatile RayFilter rayFilter = RayFilter.NONE;
    private static volatile long latestSequence;

    public static void renderRays(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double x, double y, double z) {
        renderRays(RenderHook.DEBUG_RENDERER, poseStack, bufferSource, x, y, z);
    }

    public static void renderRays(RenderHook hook, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double x, double y, double z) {
        long startNanos = System.nanoTime();
        renderCalls.incrementAndGet();
        if (hook == RenderHook.DEBUG_RENDERER) {
            debugRendererRenderCalls.incrementAndGet();
        } else {
            externalRenderCalls.incrementAndGet();
        }

        if (mc.level == null) {
            renderSkippedMissingLevel.incrementAndGet();
            Loggers.logTrace("RaycastRenderer.renderRays hook={} skipped missing level", hook);
            return;
        }
        if (!shouldRenderAny()) {
            synchronized (rays) {
                renderSkippedDisabledConfig.incrementAndGet();
                raysExpired.addAndGet(rays.size());
                rays.clear();
            }
            Loggers.logTrace("RaycastRenderer.renderRays hook={} skipped disabled render config", hook);
            return;
        }

        long gameTime = mc.level.getGameTime();
        long drawnThisCall = 0L;
        synchronized (rays) {
            int beforeRemove = rays.size();
            rays.removeIf(ray -> (gameTime - ray.tickCreated) > ray.lifespan || (gameTime - ray.tickCreated) < 0L);
            raysExpired.addAndGet(beforeRemove - rays.size());

            for (Ray ray : rays) {
                if (!filterAllows(ray)) {
                    continue;
                }
                renderRay(ray, poseStack, bufferSource, x, y, z);
                drawnThisCall++;
            }
        }
        raysDrawn.addAndGet(drawnThisCall);
        SoundPhysicsPerfDiagnostics.recordDebugRaysDrawn(drawnThisCall);
        SoundPhysicsPerfDiagnostics.recordRayRender(System.nanoTime() - startNanos);
        Loggers.logTrace("RaycastRenderer.renderRays hook={} queued={} drawn={}", hook, queuedRayCount(), drawnThisCall);
    }

    public static void addSoundBounceRay(Vec3 start, Vec3 end, int color) {
        soundBounceRayAddRequests.incrementAndGet();
        if (!shouldRenderBounce()) {
            raysFilteredByConfig.incrementAndGet();
            return;
        }

        addRay(start, end, color, false);
    }

    public static void addOcclusionRay(Vec3 start, Vec3 end, int color) {
        occlusionRayAddRequests.incrementAndGet();
        if (!shouldRenderOcclusion()) {
            raysFilteredByConfig.incrementAndGet();
            return;
        }

        addRay(start, end, color, true);
    }

    public static void addRay(Vec3 start, Vec3 end, int color, boolean throughWalls) {
        if (mc.player == null) {
            raysFilteredMissingClient.incrementAndGet();
            Loggers.logTrace("RaycastRenderer.addRay skipped missing player start={} end={}", start, end);
            return;
        }
        if (mc.player.position().distanceTo(start) > 32D && mc.player.position().distanceTo(end) > 32D) {
            raysFilteredByDistance.incrementAndGet();
            Loggers.logTrace("RaycastRenderer.addRay filtered by distance start={} end={}", start, end);
            return;
        }
        if (SoundPhysicsMod.CONFIG != null && !SoundPhysicsPerfDiagnostics.recordDebugRayQueued(SoundPhysicsMod.CONFIG.soundPhysicsMaxDebugRaysPerTick.get())) {
            Loggers.logTrace("RaycastRenderer.addRay throttled start={} end={}", start, end);
            return;
        }

        RayContext context = CURRENT_CONTEXT.get();
        if (!filterAllows(context.tag(), context.sequence())) {
            raysFilteredByRayFilter.incrementAndGet();
            return;
        }

        synchronized (rays) {
            rays.add(new Ray(start, end, color, throughWalls, context.tag(), context.sequence()));
        }
        raysAdded.incrementAndGet();
        Loggers.logTrace("RaycastRenderer.addRay start={} end={} color={} throughWalls={}", start, end, color, throughWalls);
    }

    public static void setExternalRenderHookInstalled(boolean installed) {
        externalRenderHookInstalled = installed;
    }

    public static boolean isExternalRenderHookInstalled() {
        return externalRenderHookInstalled;
    }

    public static String diagnosticsSummaryText() {
        return "requests(soundBounce=" + soundBounceRayAddRequests.get()
                + ", occlusion=" + occlusionRayAddRequests.get()
                + "), added=" + raysAdded.get()
                + ", queued=" + queuedRayCount()
                + ", mode=" + renderMode
                + ", filter=" + rayFilter
                + ", filtered(config=" + raysFilteredByConfig.get()
                + ", distance=" + raysFilteredByDistance.get()
                + ", missingClient=" + raysFilteredMissingClient.get()
                + ", rayFilter=" + raysFilteredByRayFilter.get() + ")"
                + ", renderCalls(total=" + renderCalls.get()
                + ", debugRenderer=" + debugRendererRenderCalls.get()
                + ", external=" + externalRenderCalls.get()
                + ", missingLevel=" + renderSkippedMissingLevel.get()
                + ", disabledConfig=" + renderSkippedDisabledConfig.get() + ")"
                + ", expired=" + raysExpired.get()
                + ", drawn=" + raysDrawn.get()
                + ", externalHookInstalled=" + externalRenderHookInstalled;
    }

    public static void setRenderMode(RenderMode mode) {
        renderMode = mode == null ? RenderMode.CONFIG : mode;
    }

    public static RenderMode renderMode() {
        return renderMode;
    }

    public static void setRayFilter(RayFilter filter) {
        rayFilter = filter == null ? RayFilter.NONE : filter;
    }

    public static RayFilter rayFilter() {
        return rayFilter;
    }

    public static void clearRays() {
        synchronized (rays) {
            raysExpired.addAndGet(rays.size());
            rays.clear();
        }
    }

    public static int queuedRaysForTests() {
        return queuedRayCount();
    }

    public static void setCurrentSoundContext(SoundPhysicsSoundPolicy.SoundContext context) {
        RayTag tag = RayTag.GENERAL;
        if (SoundPhysicsSoundPolicy.isKnownPropeller(context)) {
            tag = RayTag.PROPELLER;
        } else if (SoundPhysicsSoundPolicy.isRecord(context)) {
            tag = RayTag.RECORD;
        }
        long sequence = contextSequence.incrementAndGet();
        latestSequence = sequence;
        CURRENT_CONTEXT.set(new RayContext(tag, sequence));
    }

    public static void clearCurrentSoundContext() {
        CURRENT_CONTEXT.set(new RayContext(RayTag.GENERAL, 0L));
    }

    public static void resetDiagnostics() {
        soundBounceRayAddRequests.set(0L);
        occlusionRayAddRequests.set(0L);
        raysAdded.set(0L);
        raysFilteredByConfig.set(0L);
        raysFilteredByDistance.set(0L);
        raysFilteredMissingClient.set(0L);
        renderCalls.set(0L);
        debugRendererRenderCalls.set(0L);
        externalRenderCalls.set(0L);
        renderSkippedMissingLevel.set(0L);
        renderSkippedDisabledConfig.set(0L);
        raysExpired.set(0L);
        raysDrawn.set(0L);
        raysFilteredByRayFilter.set(0L);
        synchronized (rays) {
            rays.clear();
        }
    }

    private static int queuedRayCount() {
        synchronized (rays) {
            return rays.size();
        }
    }

    private static boolean shouldRenderAny() {
        return shouldRenderBounce() || shouldRenderOcclusion();
    }

    private static boolean shouldRenderBounce() {
        return switch (renderMode) {
            case CONFIG -> DiagnosticRuntimeOverrides.renderSoundBounces(SoundPhysicsMod.CONFIG);
            case OFF, OCCLUSION -> false;
            case BOUNCE, BOTH -> true;
        };
    }

    private static boolean shouldRenderOcclusion() {
        return switch (renderMode) {
            case CONFIG -> DiagnosticRuntimeOverrides.renderOcclusion(SoundPhysicsMod.CONFIG);
            case OFF, BOUNCE -> false;
            case OCCLUSION, BOTH -> true;
        };
    }

    private static boolean filterAllows(Ray ray) {
        return filterAllows(ray.tag, ray.sequence);
    }

    private static boolean filterAllows(RayTag tag, long sequence) {
        return switch (rayFilter) {
            case NONE -> true;
            case PROPELLER -> tag == RayTag.PROPELLER;
            case RECORDS -> tag == RayTag.RECORD;
            case LATEST -> sequence == latestSequence;
        };
    }

    public static void renderRay(Ray ray, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double x, double y, double z) {
        poseStack.pushPose();
        int red = getRed(ray.color);
        int green = getGreen(ray.color);
        int blue = getBlue(ray.color);

        VertexConsumer consumer;
        if (ray.throughWalls) {
            consumer = bufferSource.getBuffer(RenderTypeUtils.DEBUG_LINE_STRIP_SEETHROUGH);
        } else {
            consumer = bufferSource.getBuffer(RenderTypeUtils.DEBUG_LINE_STRIP);
        }

        Matrix4f matrix4f = poseStack.last().pose();

        consumer.addVertex(matrix4f, (float) (ray.start.x - x), (float) (ray.start.y - y), (float) (ray.start.z - z)).setColor(red, green, blue, 255);
        consumer.addVertex(matrix4f, (float) (ray.end.x - x), (float) (ray.end.y - y), (float) (ray.end.z - z)).setColor(red, green, blue, 255);

        poseStack.popPose();
    }

    private static int getRed(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int getGreen(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int getBlue(int argb) {
        return argb & 0xFF;
    }

    private static class Ray {
        private final Vec3 start;
        private final Vec3 end;
        private final int color;
        private final long tickCreated;
        private final long lifespan;
        private final boolean throughWalls;
        private final RayTag tag;
        private final long sequence;

        public Ray(Vec3 start, Vec3 end, int color, boolean throughWalls, RayTag tag, long sequence) {
            this.start = start;
            this.end = end;
            this.color = color;
            this.throughWalls = throughWalls;
            this.tag = tag;
            this.sequence = sequence;
            this.tickCreated = mc.level.getGameTime();
            this.lifespan = 20 * 2;
        }
    }

    public enum RenderHook {
        DEBUG_RENDERER,
        NEOFORGE_RENDER_STAGE
    }

    public enum RenderMode {
        CONFIG,
        OFF,
        OCCLUSION,
        BOUNCE,
        BOTH
    }

    public enum RayFilter {
        NONE,
        PROPELLER,
        RECORDS,
        LATEST
    }

    private enum RayTag {
        GENERAL,
        PROPELLER,
        RECORD
    }

    private record RayContext(RayTag tag, long sequence) {
    }

}
