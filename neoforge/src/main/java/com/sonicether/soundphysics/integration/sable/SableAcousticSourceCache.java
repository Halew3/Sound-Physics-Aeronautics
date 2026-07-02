package com.sonicether.soundphysics.integration.sable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.sonicether.soundphysics.acoustic.AcousticSceneContext;

record SableAcousticSourceCache(ConcurrentMap<Integer, Entry> entries) {

    private static final int CELL_SIZE = 8;
    private static final long TTL_TICKS = 5L;
    private static final int MAX_ENTRIES = 256;

    SableAcousticSourceCache() {
        this(new ConcurrentHashMap<>());
    }

    List<SableAcousticSpace> candidatesFor(SableAcousticSnapshot snapshot, AcousticSceneContext context, SableAcousticDiagnostics diagnostics) {
        prune(snapshot.gameTime());

        Key key = Key.create(snapshot, context);
        Entry entry = entries.get(context.sourceId());

        if (entry != null && entry.key().equals(key) && entry.expiresAfterGameTime() >= snapshot.gameTime()) {
            diagnostics.recordSourceCacheHit();
            return snapshot.spacesByIds(entry.candidateIds());
        }

        diagnostics.recordSourceCacheMiss();
        List<SableAcousticSpace> candidates = List.copyOf(snapshot.candidatesForBounds(key.candidateBounds()));
        List<String> candidateIds = candidates.stream()
                .map(SableAcousticSpace::acousticId)
                .toList();
        entries.put(context.sourceId(), new Entry(key, snapshot.gameTime() + TTL_TICKS, List.copyOf(candidateIds)));
        enforceMaxEntries();
        return candidates;
    }

    void clear() {
        entries.clear();
    }

    private void prune(long gameTime) {
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAfterGameTime() < gameTime);
    }

    private void enforceMaxEntries() {
        int overflow = entries.size() - MAX_ENTRIES;
        if (overflow <= 0) {
            return;
        }

        List<Map.Entry<Integer, Entry>> sortedEntries = new ArrayList<>(entries.entrySet());
        sortedEntries.sort(Comparator.comparingLong(entry -> entry.getValue().expiresAfterGameTime()));
        for (int i = 0; i < overflow && i < sortedEntries.size(); i++) {
            Map.Entry<Integer, Entry> entry = sortedEntries.get(i);
            entries.remove(entry.getKey(), entry.getValue());
        }
    }

    private record Entry(Key key, long expiresAfterGameTime, List<String> candidateIds) {
    }

    record Key(
            long membershipVersion,
            int sourceId,
            String sound,
            String category,
            int sourceCellX,
            int sourceCellY,
            int sourceCellZ,
            int listenerCellX,
            int listenerCellY,
            int listenerCellZ
    ) {

        static Key create(SableAcousticSnapshot snapshot, AcousticSceneContext context) {
            return new Key(
                    snapshot.membershipVersion(),
                    context.sourceId(),
                    context.sound().toString(),
                    context.category().getName(),
                    cell(context.sourcePosition().x),
                    cell(context.sourcePosition().y),
                    cell(context.sourcePosition().z),
                    cell(context.listenerPosition().x),
                    cell(context.listenerPosition().y),
                    cell(context.listenerPosition().z)
            );
        }

        SableAcousticBounds candidateBounds() {
            return SableAcousticBounds.of(
                    Math.min(cellMin(sourceCellX), cellMin(listenerCellX)),
                    Math.min(cellMin(sourceCellY), cellMin(listenerCellY)),
                    Math.min(cellMin(sourceCellZ), cellMin(listenerCellZ)),
                    Math.max(cellMax(sourceCellX), cellMax(listenerCellX)),
                    Math.max(cellMax(sourceCellY), cellMax(listenerCellY)),
                    Math.max(cellMax(sourceCellZ), cellMax(listenerCellZ))
            ).inflate(0.125D);
        }

        private static int cell(double value) {
            return Math.floorDiv((int) Math.floor(value), CELL_SIZE);
        }

        private static double cellMin(int cell) {
            return (double) cell * CELL_SIZE;
        }

        private static double cellMax(int cell) {
            return cellMin(cell) + CELL_SIZE;
        }

    }

}
