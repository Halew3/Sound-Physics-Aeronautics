package com.sonicether.soundphysics.integration.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class SableAcousticSpaceMembershipKeyTest {

    private static final SableAcousticBounds BOUNDS = SableAcousticBounds.of(0D, 0D, 0D, 16D, 16D, 16D);

    @Test
    void unchangedChunksMatchForReuse() {
        Object chunk = new Object();
        Object section = new Object();

        SableAcousticSpaceMembershipKey first = key(entry(new ChunkPos(1, 2), chunk, new Object[]{section}, 7L));
        SableAcousticSpaceMembershipKey second = key(entry(new ChunkPos(1, 2), chunk, new Object[]{section}, 7L));

        assertTrue(first.sameAs(second));
        assertEquals(first.chunkPositionSignature(), second.chunkPositionSignature());
        assertEquals(first.contentSignature(), second.contentSignature());
    }

    @Test
    void mutationVersionChangeInvalidates() {
        Object chunk = new Object();
        Object section = new Object();

        SableAcousticSpaceMembershipKey first = key(entry(new ChunkPos(1, 2), chunk, new Object[]{section}, 7L));
        SableAcousticSpaceMembershipKey second = key(entry(new ChunkPos(1, 2), chunk, new Object[]{section}, 8L));

        assertFalse(first.sameAs(second));
        assertNotEquals(first.contentSignature(), second.contentSignature());
    }

    @Test
    void sectionReferenceChangeInvalidates() {
        Object chunk = new Object();

        SableAcousticSpaceMembershipKey first = key(entry(new ChunkPos(1, 2), chunk, new Object[]{new Object()}, 7L));
        SableAcousticSpaceMembershipKey second = key(entry(new ChunkPos(1, 2), chunk, new Object[]{new Object()}, 7L));

        assertFalse(first.sameAs(second));
        assertNotEquals(first.contentSignature(), second.contentSignature());
    }

    @Test
    void chunkAddOrRemoveInvalidates() {
        Object chunkA = new Object();
        Object chunkB = new Object();
        Object sectionA = new Object();
        Object sectionB = new Object();

        SableAcousticSpaceMembershipKey first = key(entry(new ChunkPos(1, 2), chunkA, new Object[]{sectionA}, 7L));
        SableAcousticSpaceMembershipKey second = key(
                entry(new ChunkPos(1, 2), chunkA, new Object[]{sectionA}, 7L),
                entry(new ChunkPos(3, 4), chunkB, new Object[]{sectionB}, 0L)
        );

        assertFalse(first.sameAs(second));
        assertNotEquals(first.chunkPositionSignature(), second.chunkPositionSignature());
    }

    @Test
    void sortedChunkHashIsStable() {
        Object chunkA = new Object();
        Object chunkB = new Object();
        Object sectionA = new Object();
        Object sectionB = new Object();

        SableAcousticSpaceMembershipKey first = key(
                entry(new ChunkPos(3, 4), chunkB, new Object[]{sectionB}, 0L),
                entry(new ChunkPos(1, 2), chunkA, new Object[]{sectionA}, 7L)
        );
        SableAcousticSpaceMembershipKey second = key(
                entry(new ChunkPos(1, 2), chunkA, new Object[]{sectionA}, 7L),
                entry(new ChunkPos(3, 4), chunkB, new Object[]{sectionB}, 0L)
        );

        assertTrue(first.sameAs(second));
        assertEquals(first.chunkPositionSignature(), second.chunkPositionSignature());
        assertEquals(first.contentSignature(), second.contentSignature());
    }

    @Test
    void localBoundsChangeInvalidates() {
        Object chunk = new Object();
        Object section = new Object();
        SableAcousticSpaceMembershipKey.ChunkFingerprint chunkFingerprint = entry(new ChunkPos(1, 2), chunk, new Object[]{section}, 7L);

        SableAcousticSpaceMembershipKey first = SableAcousticSpaceMembershipKey.create(BOUNDS, List.of(chunkFingerprint));
        SableAcousticSpaceMembershipKey second = SableAcousticSpaceMembershipKey.create(
                SableAcousticBounds.of(0D, 0D, 0D, 32D, 16D, 16D),
                List.of(chunkFingerprint)
        );

        assertFalse(first.sameAs(second));
        assertNotEquals(first.contentSignature(), second.contentSignature());
    }

    private static SableAcousticSpaceMembershipKey key(SableAcousticSpaceMembershipKey.ChunkFingerprint... chunks) {
        return SableAcousticSpaceMembershipKey.create(BOUNDS, List.of(chunks));
    }

    private static SableAcousticSpaceMembershipKey.ChunkFingerprint entry(ChunkPos chunkPos, Object chunk, Object[] sectionRefs, long mutationVersion) {
        return SableAcousticSpaceMembershipKey.chunk(chunkPos, chunk, sectionRefs, mutationVersion);
    }

}
