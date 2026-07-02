package com.sonicether.soundphysics.integration.sable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysicsPerfDiagnostics;
import com.sonicether.soundphysics.SoundPhysicsTrace;
import com.sonicether.soundphysics.acoustic.AcousticBlockRef;
import com.sonicether.soundphysics.acoustic.AcousticRayHit;
import com.sonicether.soundphysics.acoustic.AcousticScene;
import com.sonicether.soundphysics.acoustic.RootAcousticScene;
import com.sonicether.soundphysics.acoustic.RootAcousticSpace;
import com.sonicether.soundphysics.utils.RaycastUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class SableAcousticScene implements AcousticScene {

    private final RootAcousticSpace rootSpace;
    private final RootAcousticScene rootScene;
    private final SableAcousticSnapshot snapshot;
    private final SableAcousticDiagnostics diagnostics;
    private final Map<RayCandidateKey, List<SableAcousticSpace>> candidateCache = new HashMap<>();
    private final RayCandidateKey sourceCandidateKey;
    private final Vec3 sourcePosition;
    private final Vec3 listenerPosition;
    private final List<SableAcousticSpace> sourceCandidates;

    SableAcousticScene(
            RootAcousticSpace rootSpace,
            SableAcousticSnapshot snapshot,
            SableAcousticDiagnostics diagnostics,
            Vec3 sourcePosition,
            Vec3 listenerPosition,
            List<SableAcousticSpace> sourceCandidates
    ) {
        this.rootSpace = rootSpace;
        this.rootScene = new RootAcousticScene(rootSpace);
        this.snapshot = snapshot;
        this.diagnostics = diagnostics;
        this.sourcePosition = sourcePosition;
        this.listenerPosition = listenerPosition;
        this.sourceCandidateKey = RayCandidateKey.create(sourcePosition, listenerPosition);
        this.sourceCandidates = sourceCandidates;
    }

    @Override
    public AcousticRayHit rayCast(Vec3 from, Vec3 to, @Nullable AcousticBlockRef ignore) {
        long startNanos = System.nanoTime();
        try {
        diagnostics.recordRootRay();
        AcousticRayHit bestHit = rootScene.rayCast(from, to, ignore);
        double bestDistance = bestHit.localHit().getType() == HitResult.Type.BLOCK ? from.distanceToSqr(bestHit.worldLocation()) : Double.MAX_VALUE;

        for (SableAcousticSpace space : candidatesFor(from, to)) {
            SoundPhysicsPerfDiagnostics.recordSableRayCandidateTest();
            try {
                Vec3 localFrom = space.toLocal(from);
                Vec3 localTo = space.toLocal(to);
                if (!space.intersectsLocalSegment(localFrom, localTo)) {
                    continue;
                }

                BlockPos ignoredBlock = ignore != null && ignore.space() == space ? ignore.pos() : null;
                diagnostics.recordSableRay();
                BlockHitResult localHit = RaycastUtils.rayCast(space, localFrom, localTo, ignoredBlock);
                Vec3 worldLocation = space.toWorld(localHit.getLocation());
                SoundPhysicsTrace.recordSableRay(localHit.getType(), space.acousticId(), from, to, worldLocation);
                if (localHit.getType() != HitResult.Type.BLOCK) {
                    continue;
                }

                double worldDistance = from.distanceToSqr(worldLocation);
                if (worldDistance < bestDistance) {
                    bestDistance = worldDistance;
                    bestHit = new AcousticRayHit(localHit, space, worldLocation, space.normalToWorld(localHit.getDirection()));
                }
            } catch (Throwable throwable) {
                SoundPhysicsTrace.recordSableRayFailure(space.acousticId(), throwable);
                Loggers.logDebug("Skipping Sable acoustic space {} after ray failure: {}", space.acousticId(), throwable.getMessage());
            }
        }

        return bestHit;
        } finally {
            SoundPhysicsPerfDiagnostics.recordAcousticRayCast(System.nanoTime() - startNanos);
        }
    }

    @Override
    public AcousticBlockRef blockAt(Vec3 worldPosition) {
        for (SableAcousticSpace space : snapshot.candidatesForPoint(worldPosition)) {
            try {
                if (space.containsWorldPosition(worldPosition)) {
                    return new AcousticBlockRef(space, BlockPos.containing(space.toLocal(worldPosition)));
                }
            } catch (Throwable throwable) {
                Loggers.logDebug("Skipping Sable acoustic space {} after block lookup failure: {}", space.acousticId(), throwable.getMessage());
            }
        }

        return new AcousticBlockRef(rootSpace, BlockPos.containing(worldPosition));
    }

    private List<SableAcousticSpace> candidatesFor(Vec3 from, Vec3 to) {
        diagnostics.recordCandidateQuery();
        RayCandidateKey key = RayCandidateKey.create(from, to);
        if (key.equals(sourceCandidateKey) && samePosition(from, sourcePosition) && samePosition(to, listenerPosition)) {
            diagnostics.recordCandidateCacheHit();
            return sourceCandidates;
        }

        List<SableAcousticSpace> cached = candidateCache.get(key);
        if (cached != null) {
            diagnostics.recordCandidateCacheHit();
            return cached;
        }

        List<SableAcousticSpace> candidates = List.copyOf(snapshot.candidatesForBounds(key.bounds()));
        candidateCache.put(key, candidates);
        return candidates;
    }

    private static boolean samePosition(Vec3 a, Vec3 b) {
        return a.distanceToSqr(b) <= 1.0E-12D;
    }

    private record RayCandidateKey(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static RayCandidateKey create(Vec3 from, Vec3 to) {
            int fromMinX = (int) Math.floor(from.x);
            int fromMinY = (int) Math.floor(from.y);
            int fromMinZ = (int) Math.floor(from.z);
            int toMinX = (int) Math.floor(to.x);
            int toMinY = (int) Math.floor(to.y);
            int toMinZ = (int) Math.floor(to.z);
            int fromMaxX = (int) Math.ceil(from.x);
            int fromMaxY = (int) Math.ceil(from.y);
            int fromMaxZ = (int) Math.ceil(from.z);
            int toMaxX = (int) Math.ceil(to.x);
            int toMaxY = (int) Math.ceil(to.y);
            int toMaxZ = (int) Math.ceil(to.z);

            return new RayCandidateKey(
                    Math.min(fromMinX, toMinX),
                    Math.min(fromMinY, toMinY),
                    Math.min(fromMinZ, toMinZ),
                    Math.max(fromMaxX, toMaxX),
                    Math.max(fromMaxY, toMaxY),
                    Math.max(fromMaxZ, toMaxZ)
            );
        }

        SableAcousticBounds bounds() {
            return SableAcousticBounds.of(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.125D);
        }
    }

}
