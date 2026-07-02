package com.sonicether.soundphysics.integration.sable;

import java.util.concurrent.atomic.AtomicLong;

import com.sonicether.soundphysics.Loggers;

final class SableAcousticDiagnostics {

    private final AtomicLong snapshotsBuilt = new AtomicLong();
    private final AtomicLong snapshotSpaces = new AtomicLong();
    private final AtomicLong snapshotChunks = new AtomicLong();
    private final AtomicLong latestSnapshotSpaces = new AtomicLong();
    private final AtomicLong latestSnapshotChunks = new AtomicLong();
    private final AtomicLong skippedUnfinalized = new AtomicLong();
    private final AtomicLong skippedRemoved = new AtomicLong();
    private final AtomicLong skippedFailed = new AtomicLong();
    private final AtomicLong rootRays = new AtomicLong();
    private final AtomicLong sableRays = new AtomicLong();
    private final AtomicLong candidateQueries = new AtomicLong();
    private final AtomicLong candidateCacheHits = new AtomicLong();
    private final AtomicLong sourceCacheHits = new AtomicLong();
    private final AtomicLong sourceCacheMisses = new AtomicLong();
    private final AtomicLong providerRootFallbacks = new AtomicLong();

    void recordSnapshot(SableAcousticSnapshot snapshot) {
        snapshotsBuilt.incrementAndGet();
        snapshotSpaces.addAndGet(snapshot.spaces().size());
        snapshotChunks.addAndGet(snapshot.chunkCount());
        latestSnapshotSpaces.set(snapshot.spaces().size());
        latestSnapshotChunks.set(snapshot.chunkCount());
        skippedUnfinalized.addAndGet(snapshot.skippedUnfinalized());
        skippedRemoved.addAndGet(snapshot.skippedRemoved());
        skippedFailed.addAndGet(snapshot.skippedFailed());
    }

    void recordRootRay() {
        rootRays.incrementAndGet();
    }

    void recordSableRay() {
        sableRays.incrementAndGet();
    }

    void recordCandidateQuery() {
        candidateQueries.incrementAndGet();
    }

    void recordCandidateCacheHit() {
        candidateCacheHits.incrementAndGet();
    }

    void recordSourceCacheHit() {
        sourceCacheHits.incrementAndGet();
    }

    void recordSourceCacheMiss() {
        sourceCacheMisses.incrementAndGet();
    }

    void recordProviderRootFallback() {
        providerRootFallbacks.incrementAndGet();
    }

    void logSnapshot(SableAcousticSnapshot snapshot) {
        Loggers.logSableAcoustic(
                "Sable acoustic snapshot poseV={} membershipV={}: {} spaces, {} chunks, {} unfinalized skipped, {} removed skipped, {} failed skipped",
                snapshot.version(),
                snapshot.membershipVersion(),
                snapshot.spaces().size(),
                snapshot.chunkCount(),
                snapshot.skippedUnfinalized(),
                snapshot.skippedRemoved(),
                snapshot.skippedFailed()
        );
    }

    void logTotals() {
        SableAcousticDiagnosticsSummary summary = summary();
        Loggers.logSableAcoustic(
                "Sable acoustic totals: snapshots={}, latestSpaces={}, avgSpaces={}, latestChunks={}, avgChunks={}, skippedUnfinalized={}, skippedRemoved={}, skippedFailed={}, rootRays={}, sableRays={}, candidateQueries={}, candidateHits={}, sourceHits={}, sourceMisses={}, rootFallbacks={}",
                summary.snapshotsBuilt(),
                summary.latestSnapshotSpaces(),
                summary.averageSnapshotSpaces(),
                summary.latestSnapshotChunks(),
                summary.averageSnapshotChunks(),
                summary.skippedUnfinalized(),
                summary.skippedRemoved(),
                summary.skippedFailed(),
                summary.rootRays(),
                summary.sableRays(),
                summary.candidateQueries(),
                summary.candidateCacheHits(),
                summary.sourceCacheHits(),
                summary.sourceCacheMisses(),
                summary.providerRootFallbacks()
        );
    }

    void reset() {
        snapshotsBuilt.set(0L);
        snapshotSpaces.set(0L);
        snapshotChunks.set(0L);
        latestSnapshotSpaces.set(0L);
        latestSnapshotChunks.set(0L);
        skippedUnfinalized.set(0L);
        skippedRemoved.set(0L);
        skippedFailed.set(0L);
        rootRays.set(0L);
        sableRays.set(0L);
        candidateQueries.set(0L);
        candidateCacheHits.set(0L);
        sourceCacheHits.set(0L);
        sourceCacheMisses.set(0L);
        providerRootFallbacks.set(0L);
    }

    SableAcousticDiagnosticsSummary summary() {
        long snapshotCount = snapshotsBuilt.get();
        return new SableAcousticDiagnosticsSummary(
                snapshotCount,
                latestSnapshotSpaces.get(),
                latestSnapshotChunks.get(),
                average(snapshotSpaces.get(), snapshotCount),
                average(snapshotChunks.get(), snapshotCount),
                skippedUnfinalized.get(),
                skippedRemoved.get(),
                skippedFailed.get(),
                rootRays.get(),
                sableRays.get(),
                candidateQueries.get(),
                candidateCacheHits.get(),
                sourceCacheHits.get(),
                sourceCacheMisses.get(),
                providerRootFallbacks.get()
        );
    }

    private static long average(long total, long count) {
        return count == 0L ? 0L : total / count;
    }

}

record SableAcousticDiagnosticsSummary(
        long snapshotsBuilt,
        long latestSnapshotSpaces,
        long latestSnapshotChunks,
        long averageSnapshotSpaces,
        long averageSnapshotChunks,
        long skippedUnfinalized,
        long skippedRemoved,
        long skippedFailed,
        long rootRays,
        long sableRays,
        long candidateQueries,
        long candidateCacheHits,
        long sourceCacheHits,
        long sourceCacheMisses,
        long providerRootFallbacks
) {
}
