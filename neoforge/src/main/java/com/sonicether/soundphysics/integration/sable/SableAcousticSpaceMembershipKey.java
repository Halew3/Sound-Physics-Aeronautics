package com.sonicether.soundphysics.integration.sable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.level.ChunkPos;

final class SableAcousticSpaceMembershipKey {

    private final SableAcousticBounds localBounds;
    private final List<ChunkFingerprint> chunks;
    private final long chunkPositionSignature;
    private final long contentSignature;

    private SableAcousticSpaceMembershipKey(SableAcousticBounds localBounds, List<ChunkFingerprint> chunks) {
        this.localBounds = localBounds;
        this.chunks = chunks;
        this.chunkPositionSignature = chunkPositionSignature(chunks);
        this.contentSignature = contentSignature(localBounds, chunks);
    }

    static SableAcousticSpaceMembershipKey create(SableAcousticBounds localBounds, List<ChunkFingerprint> chunks) {
        List<ChunkFingerprint> sortedChunks = new ArrayList<>(chunks);
        sortedChunks.sort(Comparator.comparingLong(ChunkFingerprint::chunkPosLong));
        return new SableAcousticSpaceMembershipKey(localBounds, List.copyOf(sortedChunks));
    }

    static ChunkFingerprint chunk(ChunkPos chunkPos, Object chunkIdentity, @Nullable Object[] sectionRefs, long mutationVersion) {
        return new ChunkFingerprint(chunkPos.toLong(), chunkIdentity, sectionRefs == null ? new Object[0] : sectionRefs.clone(), mutationVersion);
    }

    boolean sameAs(@Nullable SableAcousticSpaceMembershipKey other) {
        if (other == null || !localBounds.equals(other.localBounds) || chunks.size() != other.chunks.size()) {
            return false;
        }

        for (int i = 0; i < chunks.size(); i++) {
            if (!chunks.get(i).sameAs(other.chunks.get(i))) {
                return false;
            }
        }

        return true;
    }

    SableAcousticBounds localBounds() {
        return localBounds;
    }

    int chunkCount() {
        return chunks.size();
    }

    long chunkPositionSignature() {
        return chunkPositionSignature;
    }

    long contentSignature() {
        return contentSignature;
    }

    private static long chunkPositionSignature(List<ChunkFingerprint> chunks) {
        long hash = 1469598103934665603L;
        hash = mix(hash, chunks.size());
        for (ChunkFingerprint chunk : chunks) {
            hash = mix(hash, chunk.chunkPosLong());
        }
        return hash;
    }

    private static long contentSignature(SableAcousticBounds localBounds, List<ChunkFingerprint> chunks) {
        long hash = 1125899906842597L;
        hash = mixBounds(hash, localBounds);
        hash = mix(hash, chunks.size());
        for (ChunkFingerprint chunk : chunks) {
            hash = mix(hash, chunk.chunkPosLong());
            hash = mix(hash, System.identityHashCode(chunk.chunkIdentity()));
            hash = mix(hash, chunk.mutationVersion());
            hash = mix(hash, chunk.sectionRefs().length);
            for (Object sectionRef : chunk.sectionRefs()) {
                hash = mix(hash, System.identityHashCode(sectionRef));
            }
        }
        return hash;
    }

    private static long mixBounds(long hash, SableAcousticBounds bounds) {
        hash = mix(hash, Double.doubleToLongBits(bounds.minX()));
        hash = mix(hash, Double.doubleToLongBits(bounds.minY()));
        hash = mix(hash, Double.doubleToLongBits(bounds.minZ()));
        hash = mix(hash, Double.doubleToLongBits(bounds.maxX()));
        hash = mix(hash, Double.doubleToLongBits(bounds.maxY()));
        return mix(hash, Double.doubleToLongBits(bounds.maxZ()));
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 1099511628211L;
    }

    static final class ChunkFingerprint {

        private final long chunkPosLong;
        private final Object chunkIdentity;
        private final Object[] sectionRefs;
        private final long mutationVersion;

        private ChunkFingerprint(long chunkPosLong, Object chunkIdentity, Object[] sectionRefs, long mutationVersion) {
            this.chunkPosLong = chunkPosLong;
            this.chunkIdentity = chunkIdentity;
            this.sectionRefs = sectionRefs;
            this.mutationVersion = mutationVersion;
        }

        private boolean sameAs(ChunkFingerprint other) {
            if (chunkPosLong != other.chunkPosLong
                    || chunkIdentity != other.chunkIdentity
                    || mutationVersion != other.mutationVersion
                    || sectionRefs.length != other.sectionRefs.length) {
                return false;
            }

            for (int i = 0; i < sectionRefs.length; i++) {
                if (sectionRefs[i] != other.sectionRefs[i]) {
                    return false;
                }
            }

            return true;
        }

        private long chunkPosLong() {
            return chunkPosLong;
        }

        private Object chunkIdentity() {
            return chunkIdentity;
        }

        private Object[] sectionRefs() {
            return sectionRefs;
        }

        private long mutationVersion() {
            return mutationVersion;
        }

    }

}
