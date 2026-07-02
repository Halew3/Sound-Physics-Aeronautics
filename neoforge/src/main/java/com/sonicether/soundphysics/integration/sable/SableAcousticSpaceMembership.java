package com.sonicether.soundphysics.integration.sable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.sonicether.soundphysics.world.ClientChunkMutationTracker;
import com.sonicether.soundphysics.world.ClonedLevelChunk;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

final class SableAcousticSpaceMembership {

    private final SableAcousticSpaceMembershipKey key;
    private final Map<ChunkPos, ClonedLevelChunk> chunks;
    private final int minBuildHeight;
    private final int height;

    private SableAcousticSpaceMembership(
            SableAcousticSpaceMembershipKey key,
            Map<ChunkPos, ClonedLevelChunk> chunks,
            int minBuildHeight,
            int height
    ) {
        this.key = key;
        this.chunks = Map.copyOf(chunks);
        this.minBuildHeight = minBuildHeight;
        this.height = height;
    }

    static SableAcousticSpaceMembership create(ClientLevel level, ClientSubLevel subLevel, @Nullable SableAcousticSpaceMembership previous) {
        Scan scan = scan(level, subLevel.getPlot());
        if (previous != null && previous.key.sameAs(scan.key())) {
            return previous;
        }

        Map<ChunkPos, ClonedLevelChunk> clonedChunks = new HashMap<>();
        for (ScannedChunk chunk : scan.chunks()) {
            clonedChunks.put(chunk.chunkPos(), new ClonedLevelChunk(level, chunk.chunkPos(), chunk.sections()));
        }

        return new SableAcousticSpaceMembership(scan.key(), clonedChunks, level.getMinBuildHeight(), level.getHeight());
    }

    SableAcousticBounds localBounds() {
        return key.localBounds();
    }

    Map<ChunkPos, ClonedLevelChunk> chunks() {
        return chunks;
    }

    int minBuildHeight() {
        return minBuildHeight;
    }

    int height() {
        return height;
    }

    int chunkCount() {
        return key.chunkCount();
    }

    long chunkPositionSignature() {
        return key.chunkPositionSignature();
    }

    long contentSignature() {
        return key.contentSignature();
    }

    private static Scan scan(ClientLevel level, LevelPlot plot) {
        SableAcousticBounds localBounds = localBounds(plot);
        List<SableAcousticSpaceMembershipKey.ChunkFingerprint> fingerprints = new ArrayList<>();
        List<ScannedChunk> chunks = new ArrayList<>();

        for (PlotChunkHolder holder : plot.getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null) {
                continue;
            }

            ChunkPos chunkPos = holder.getPos();
            LevelChunkSection[] sections = chunk.getSections();
            fingerprints.add(SableAcousticSpaceMembershipKey.chunk(
                    chunkPos,
                    chunk,
                    sections,
                    ClientChunkMutationTracker.version(level, chunkPos)
            ));
            chunks.add(new ScannedChunk(chunkPos, sections));
        }

        return new Scan(SableAcousticSpaceMembershipKey.create(localBounds, fingerprints), List.copyOf(chunks));
    }

    private static SableAcousticBounds localBounds(LevelPlot plot) {
        BoundingBox3ic local = plot.getBoundingBox();
        return SableAcousticBounds.of(local.minX(), local.minY(), local.minZ(), local.maxX() + 1D, local.maxY() + 1D, local.maxZ() + 1D);
    }

    private record Scan(SableAcousticSpaceMembershipKey key, List<ScannedChunk> chunks) {
    }

    private record ScannedChunk(ChunkPos chunkPos, LevelChunkSection[] sections) {
    }

}
