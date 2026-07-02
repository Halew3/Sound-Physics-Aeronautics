package com.sonicether.soundphysics.integration.sable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SableAcousticBoundsTest {

    @Test
    void normalizesCoordinateOrder() {
        SableAcousticBounds bounds = SableAcousticBounds.of(10D, 8D, 6D, -2D, -4D, -6D);

        assertTrue(bounds.contains(0D, 0D, 0D));
        assertFalse(bounds.contains(12D, 0D, 0D));
    }

    @Test
    void intersectsTouchingBounds() {
        SableAcousticBounds a = SableAcousticBounds.of(0D, 0D, 0D, 1D, 1D, 1D);
        SableAcousticBounds b = SableAcousticBounds.of(1D, 1D, 1D, 2D, 2D, 2D);

        assertTrue(a.intersects(b));
    }

    @Test
    void rejectsSeparatedBounds() {
        SableAcousticBounds a = SableAcousticBounds.of(0D, 0D, 0D, 1D, 1D, 1D);
        SableAcousticBounds b = SableAcousticBounds.of(2D, 0D, 0D, 3D, 1D, 1D);

        assertFalse(a.intersects(b));
    }

    @Test
    void inflatesForNarrowRayCorridors() {
        SableAcousticBounds ray = SableAcousticBounds.segment(0D, 0D, 0D, 0D, 0D, 10D).inflate(0.25D);

        assertTrue(ray.intersects(SableAcousticBounds.of(0.2D, 0D, 5D, 0.3D, 1D, 6D)));
        assertFalse(ray.intersects(SableAcousticBounds.of(1D, 0D, 5D, 2D, 1D, 6D)));
    }

    @Test
    void containsNegativeCoordinates() {
        SableAcousticBounds bounds = SableAcousticBounds.of(-16D, -8D, -4D, -1D, -2D, -0.5D);

        assertTrue(bounds.contains(-8D, -4D, -1D));
        assertFalse(bounds.contains(0D, -4D, -1D));
    }

    @Test
    void veryThinSegmentsStillIntersectTouchingBounds() {
        SableAcousticBounds segment = SableAcousticBounds.segment(4D, 4D, 4D, 4D, 4D, 4.001D);
        SableAcousticBounds touching = SableAcousticBounds.of(4D, 4D, 4D, 5D, 5D, 5D);

        assertTrue(segment.intersects(touching));
    }

    @Test
    void inflatedBoundsExpandInAllDirections() {
        SableAcousticBounds bounds = SableAcousticBounds.of(1D, 1D, 1D, 2D, 2D, 2D).inflate(0.5D);

        assertTrue(bounds.contains(0.5D, 1D, 1D));
        assertTrue(bounds.contains(2.5D, 2D, 2D));
        assertFalse(bounds.contains(0.49D, 1D, 1D));
    }

    @Test
    void rejectsSeparatedDiagonalBounds() {
        SableAcousticBounds a = SableAcousticBounds.of(-4D, -4D, -4D, -2D, -2D, -2D);
        SableAcousticBounds b = SableAcousticBounds.of(-1.9D, -1.9D, -1.9D, 0D, 0D, 0D);

        assertFalse(a.intersects(b));
    }

}
