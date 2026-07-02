package com.sonicether.soundphysics.integration.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import com.sonicether.soundphysics.acoustic.AcousticSceneContext;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SableAcousticSourceCacheTest {

    private static final ResourceLocation SOUND = ResourceLocation.fromNamespaceAndPath("sound_physics_test", "sound");

    @Test
    void hitsWithSameSnapshotAndContextCell() {
        SableAcousticSourceCache cache = new SableAcousticSourceCache();
        SableAcousticDiagnostics diagnostics = new SableAcousticDiagnostics();
        FakeAcousticSpace space = space("space");
        SableAcousticSnapshot snapshot = snapshot(0L, 1L, space);
        AcousticSceneContext context = context(1, new Vec3(1D, 1D, 1D), new Vec3(2D, 1D, 1D));

        assertSame(space, cache.candidatesFor(snapshot, context, diagnostics).getFirst());
        assertSame(space, cache.candidatesFor(snapshot, context, diagnostics).getFirst());

        SableAcousticDiagnosticsSummary summary = diagnostics.summary();
        assertEquals(1L, summary.sourceCacheHits());
        assertEquals(1L, summary.sourceCacheMisses());
    }

    @Test
    void hitsWhenOnlyPoseVersionChanges() {
        SableAcousticSourceCache cache = new SableAcousticSourceCache();
        SableAcousticDiagnostics diagnostics = new SableAcousticDiagnostics();
        FakeAcousticSpace space = space("space");
        AcousticSceneContext context = context(1, new Vec3(1D, 1D, 1D), new Vec3(2D, 1D, 1D));

        cache.candidatesFor(snapshot(0L, 1L, 100L, space), context, diagnostics);
        cache.candidatesFor(snapshot(1L, 2L, 100L, space), context, diagnostics);

        SableAcousticDiagnosticsSummary summary = diagnostics.summary();
        assertEquals(1L, summary.sourceCacheHits());
        assertEquals(1L, summary.sourceCacheMisses());
    }

    @Test
    void missesWhenMembershipVersionChanges() {
        SableAcousticSourceCache cache = new SableAcousticSourceCache();
        SableAcousticDiagnostics diagnostics = new SableAcousticDiagnostics();
        FakeAcousticSpace space = space("space");
        AcousticSceneContext context = context(1, new Vec3(1D, 1D, 1D), new Vec3(2D, 1D, 1D));

        cache.candidatesFor(snapshot(0L, 1L, 100L, space), context, diagnostics);
        cache.candidatesFor(snapshot(1L, 2L, 101L, space), context, diagnostics);

        SableAcousticDiagnosticsSummary summary = diagnostics.summary();
        assertEquals(0L, summary.sourceCacheHits());
        assertEquals(2L, summary.sourceCacheMisses());
    }

    @Test
    void missesWhenSourceOrListenerCellChanges() {
        SableAcousticSourceCache cache = new SableAcousticSourceCache();
        SableAcousticDiagnostics diagnostics = new SableAcousticDiagnostics();
        FakeAcousticSpace space = space("space");
        SableAcousticSnapshot snapshot = snapshot(0L, 1L, space);

        cache.candidatesFor(snapshot, context(1, new Vec3(1D, 1D, 1D), new Vec3(2D, 1D, 1D)), diagnostics);
        cache.candidatesFor(snapshot, context(1, new Vec3(9D, 1D, 1D), new Vec3(2D, 1D, 1D)), diagnostics);
        cache.candidatesFor(snapshot, context(1, new Vec3(9D, 1D, 1D), new Vec3(17D, 1D, 1D)), diagnostics);

        SableAcousticDiagnosticsSummary summary = diagnostics.summary();
        assertEquals(0L, summary.sourceCacheHits());
        assertEquals(3L, summary.sourceCacheMisses());
    }

    @Test
    void missesAfterTtlExpiry() {
        SableAcousticSourceCache cache = new SableAcousticSourceCache();
        SableAcousticDiagnostics diagnostics = new SableAcousticDiagnostics();
        FakeAcousticSpace space = space("space");
        AcousticSceneContext context = context(1, new Vec3(1D, 1D, 1D), new Vec3(2D, 1D, 1D));

        cache.candidatesFor(snapshot(10L, 1L, space), context, diagnostics);
        cache.candidatesFor(snapshot(16L, 1L, space), context, diagnostics);

        SableAcousticDiagnosticsSummary summary = diagnostics.summary();
        assertEquals(0L, summary.sourceCacheHits());
        assertEquals(2L, summary.sourceCacheMisses());
    }

    @Test
    void cachedIdsResolveToCurrentSnapshotSpaces() {
        SableAcousticSourceCache cache = new SableAcousticSourceCache();
        SableAcousticDiagnostics diagnostics = new SableAcousticDiagnostics();
        FakeAcousticSpace oldSpace = space("space");
        FakeAcousticSpace currentSpace = space("space");
        AcousticSceneContext context = context(1, new Vec3(1D, 1D, 1D), new Vec3(2D, 1D, 1D));

        assertSame(oldSpace, cache.candidatesFor(snapshot(0L, 7L, oldSpace), context, diagnostics).getFirst());
        assertSame(currentSpace, cache.candidatesFor(snapshot(1L, 7L, currentSpace), context, diagnostics).getFirst());

        SableAcousticDiagnosticsSummary summary = diagnostics.summary();
        assertEquals(1L, summary.sourceCacheHits());
        assertEquals(1L, summary.sourceCacheMisses());
    }

    private static AcousticSceneContext context(int sourceId, Vec3 source, Vec3 listener) {
        return new AcousticSceneContext(sourceId, source, listener, SoundSource.MASTER, SOUND);
    }

    private static SableAcousticSnapshot snapshot(long gameTime, long version, SableAcousticSpace... spaces) {
        return new SableAcousticSnapshot(null, gameTime, version, version, List.of(spaces), spaces.length, 0, 0, 0);
    }

    private static SableAcousticSnapshot snapshot(long gameTime, long version, long membershipVersion, SableAcousticSpace... spaces) {
        return new SableAcousticSnapshot(null, gameTime, version, version, membershipVersion, membershipVersion, List.of(spaces), spaces.length, 0, 0, 0);
    }

    private static FakeAcousticSpace space(String id) {
        return new FakeAcousticSpace(id, SableAcousticBounds.of(0D, 0D, 0D, 16D, 16D, 16D));
    }

}
