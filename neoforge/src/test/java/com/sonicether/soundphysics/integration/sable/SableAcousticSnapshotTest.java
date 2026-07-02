package com.sonicether.soundphysics.integration.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

class SableAcousticSnapshotTest {

    @Test
    void indexedCandidatesMatchLinearFiltering() {
        FakeAcousticSpace first = space("first", SableAcousticBounds.of(0D, 0D, 0D, 8D, 8D, 8D));
        FakeAcousticSpace second = space("second", SableAcousticBounds.of(20D, 0D, 0D, 28D, 8D, 8D));
        FakeAcousticSpace third = space("third", SableAcousticBounds.of(80D, 0D, 0D, 88D, 8D, 8D));
        List<SableAcousticSpace> spaces = List.of(first, second, third);
        SableAcousticBounds query = SableAcousticBounds.of(-1D, -1D, -1D, 25D, 4D, 4D);

        assertEquals(linearCandidates(spaces, query), snapshot(spaces).candidatesForBounds(query));
    }

    @Test
    void spanningSpaceIsReturnedOnce() {
        FakeAcousticSpace spanning = space("spanning", SableAcousticBounds.of(0D, 0D, 0D, 64D, 16D, 16D));

        List<SableAcousticSpace> candidates = snapshot(List.of(spanning))
                .candidatesForBounds(SableAcousticBounds.of(31D, 1D, 1D, 33D, 2D, 2D));

        assertEquals(List.of(spanning), candidates);
    }

    @Test
    void negativeCoordinateCellsAreIndexed() {
        FakeAcousticSpace negative = space("negative", SableAcousticBounds.of(-40D, -8D, -8D, -20D, 8D, 8D));

        List<SableAcousticSpace> candidates = snapshot(List.of(negative))
                .candidatesForBounds(SableAcousticBounds.of(-33D, 0D, 0D, -31D, 1D, 1D));

        assertEquals(List.of(negative), candidates);
    }

    @Test
    void boundaryTouchingBoundsRemainCandidates() {
        FakeAcousticSpace touching = space("touching", SableAcousticBounds.of(0D, 0D, 0D, 16D, 16D, 16D));

        List<SableAcousticSpace> candidates = snapshot(List.of(touching))
                .candidatesForBounds(SableAcousticBounds.of(16D, 4D, 4D, 16D, 5D, 5D));

        assertEquals(List.of(touching), candidates);
    }

    @Test
    void largeQueriesFallBackToLinearFiltering() {
        FakeAcousticSpace inside = space("inside", SableAcousticBounds.of(250D, 250D, 250D, 260D, 260D, 260D));
        FakeAcousticSpace outside = space("outside", SableAcousticBounds.of(300D, 300D, 300D, 320D, 320D, 320D));
        List<SableAcousticSpace> spaces = List.of(inside, outside);
        SableAcousticBounds query = SableAcousticBounds.of(0D, 0D, 0D, 256D, 256D, 256D);

        assertEquals(linearCandidates(spaces, query), snapshot(spaces).candidatesForBounds(query));
    }

    @Test
    void candidateOrderFollowsSnapshotOrder() {
        FakeAcousticSpace far = space("far", SableAcousticBounds.of(80D, 0D, 0D, 88D, 8D, 8D));
        FakeAcousticSpace near = space("near", SableAcousticBounds.of(0D, 0D, 0D, 8D, 8D, 8D));

        List<SableAcousticSpace> candidates = snapshot(List.of(far, near))
                .candidatesForBounds(SableAcousticBounds.of(0D, 0D, 0D, 88D, 8D, 8D));

        assertEquals(List.of(far, near), candidates);
    }

    @Test
    void spacesByIdsUsesFirstMatchingSnapshotSpace() {
        FakeAcousticSpace firstDuplicate = space("duplicate", SableAcousticBounds.of(0D, 0D, 0D, 8D, 8D, 8D));
        FakeAcousticSpace secondDuplicate = space("duplicate", SableAcousticBounds.of(20D, 0D, 0D, 28D, 8D, 8D));
        FakeAcousticSpace other = space("other", SableAcousticBounds.of(40D, 0D, 0D, 48D, 8D, 8D));

        List<SableAcousticSpace> resolved = snapshot(List.of(firstDuplicate, secondDuplicate, other))
                .spacesByIds(List.of("duplicate", "other", "missing"));

        assertEquals(2, resolved.size());
        assertSame(firstDuplicate, resolved.get(0));
        assertSame(other, resolved.get(1));
    }

    private static SableAcousticSnapshot snapshot(List<SableAcousticSpace> spaces) {
        return new SableAcousticSnapshot(null, 0L, 1L, 1L, spaces, spaces.size(), 0, 0, 0);
    }

    private static FakeAcousticSpace space(String acousticId, SableAcousticBounds bounds) {
        return new FakeAcousticSpace(acousticId, bounds);
    }

    private static List<SableAcousticSpace> linearCandidates(List<SableAcousticSpace> spaces, SableAcousticBounds bounds) {
        return spaces.stream()
                .filter(space -> space.intersectsWorldBounds(bounds))
                .toList();
    }

}
