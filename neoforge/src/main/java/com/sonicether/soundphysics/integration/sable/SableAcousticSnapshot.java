package com.sonicether.soundphysics.integration.sable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

record SableAcousticSnapshot(
        ClientLevel level,
        long gameTime,
        long version,
        long signature,
        long membershipVersion,
        long membershipSignature,
        List<SableAcousticSpace> spaces,
        Map<String, SableAcousticSpace> byAcousticId,
        SableAcousticSpaceIndex spaceIndex,
        int chunkCount,
        int skippedUnfinalized,
        int skippedRemoved,
        int skippedFailed
) {

    SableAcousticSnapshot {
        spaces = List.copyOf(spaces);
        byAcousticId = Map.copyOf(byAcousticId);
    }

    SableAcousticSnapshot(
            ClientLevel level,
            long gameTime,
            long version,
            long signature,
            long membershipVersion,
            long membershipSignature,
            List<SableAcousticSpace> spaces,
            int chunkCount,
            int skippedUnfinalized,
            int skippedRemoved,
            int skippedFailed
    ) {
        this(
                level,
                gameTime,
                version,
                signature,
                membershipVersion,
                membershipSignature,
                spaces,
                byAcousticId(spaces),
                SableAcousticSpaceIndex.create(spaces),
                chunkCount,
                skippedUnfinalized,
                skippedRemoved,
                skippedFailed
        );
    }

    SableAcousticSnapshot(
            ClientLevel level,
            long gameTime,
            long version,
            long signature,
            List<SableAcousticSpace> spaces,
            int chunkCount,
            int skippedUnfinalized,
            int skippedRemoved,
            int skippedFailed
    ) {
        this(level, gameTime, version, signature, version, signature, spaces, chunkCount, skippedUnfinalized, skippedRemoved, skippedFailed);
    }

    boolean isFor(ClientLevel candidateLevel) {
        return level == candidateLevel;
    }

    boolean hasSableSpaces() {
        return !spaces.isEmpty();
    }

    List<SableAcousticSpace> candidatesForSegment(Vec3 from, Vec3 to) {
        SableAcousticBounds segmentBounds = SableAcousticBounds.segment(from.x, from.y, from.z, to.x, to.y, to.z).inflate(0.125D);
        return candidatesForBounds(segmentBounds);
    }

    List<SableAcousticSpace> candidatesForPoint(Vec3 point) {
        return candidatesForBounds(SableAcousticBounds.of(point.x, point.y, point.z, point.x, point.y, point.z));
    }

    List<SableAcousticSpace> candidatesForBounds(SableAcousticBounds segmentBounds) {
        return spaceIndex.candidatesForBounds(segmentBounds);
    }

    List<SableAcousticSpace> spacesByIds(Collection<String> acousticIds) {
        if (acousticIds.isEmpty() || spaces.isEmpty()) {
            return List.of();
        }

        List<SableAcousticSpace> resolved = new ArrayList<>(acousticIds.size());
        for (String acousticId : acousticIds) {
            SableAcousticSpace space = byAcousticId.get(acousticId);
            if (space != null) {
                resolved.add(space);
            }
        }
        return List.copyOf(resolved);
    }

    private static Map<String, SableAcousticSpace> byAcousticId(List<SableAcousticSpace> spaces) {
        Map<String, SableAcousticSpace> byId = new HashMap<>();
        for (SableAcousticSpace space : spaces) {
            byId.putIfAbsent(space.acousticId(), space);
        }
        return byId;
    }

}
