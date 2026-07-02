package com.sonicether.soundphysics.integration.sable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SableAcousticSpaceIndex {

    private static final int CELL_SIZE = 16;
    private static final long MAX_QUERY_CELLS = 4096L;

    private final List<SableAcousticSpace> spaces;
    private final Map<CellKey, int[]> cells;

    private SableAcousticSpaceIndex(List<SableAcousticSpace> spaces, Map<CellKey, int[]> cells) {
        this.spaces = spaces;
        this.cells = cells;
    }

    static SableAcousticSpaceIndex create(List<SableAcousticSpace> spaces) {
        if (spaces.isEmpty()) {
            return new SableAcousticSpaceIndex(List.of(), Map.of());
        }

        Map<CellKey, List<Integer>> mutableCells = new HashMap<>();
        for (int ordinal = 0; ordinal < spaces.size(); ordinal++) {
            CellRange range = CellRange.of(spaces.get(ordinal).worldBounds());
            for (int x = range.minX(); x <= range.maxX(); x++) {
                for (int y = range.minY(); y <= range.maxY(); y++) {
                    for (int z = range.minZ(); z <= range.maxZ(); z++) {
                        mutableCells.computeIfAbsent(new CellKey(x, y, z), ignored -> new ArrayList<>()).add(ordinal);
                    }
                }
            }
        }

        Map<CellKey, int[]> indexedCells = new HashMap<>(mutableCells.size());
        for (Map.Entry<CellKey, List<Integer>> entry : mutableCells.entrySet()) {
            List<Integer> ordinals = entry.getValue();
            int[] packedOrdinals = new int[ordinals.size()];
            for (int i = 0; i < ordinals.size(); i++) {
                packedOrdinals[i] = ordinals.get(i);
            }
            indexedCells.put(entry.getKey(), packedOrdinals);
        }

        return new SableAcousticSpaceIndex(List.copyOf(spaces), Map.copyOf(indexedCells));
    }

    List<SableAcousticSpace> candidatesForBounds(SableAcousticBounds bounds) {
        if (spaces.isEmpty()) {
            return List.of();
        }

        CellRange range = CellRange.of(bounds);
        if (range.exceedsMaxQueryCells()) {
            return linearCandidatesForBounds(bounds);
        }

        BitSet candidateOrdinals = new BitSet(spaces.size());
        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                for (int z = range.minZ(); z <= range.maxZ(); z++) {
                    int[] ordinals = cells.get(new CellKey(x, y, z));
                    if (ordinals == null) {
                        continue;
                    }
                    for (int ordinal : ordinals) {
                        candidateOrdinals.set(ordinal);
                    }
                }
            }
        }

        if (candidateOrdinals.isEmpty()) {
            return List.of();
        }

        List<SableAcousticSpace> candidates = new ArrayList<>(candidateOrdinals.cardinality());
        for (int ordinal = candidateOrdinals.nextSetBit(0); ordinal >= 0; ordinal = candidateOrdinals.nextSetBit(ordinal + 1)) {
            SableAcousticSpace space = spaces.get(ordinal);
            if (space.intersectsWorldBounds(bounds)) {
                candidates.add(space);
            }
        }
        return List.copyOf(candidates);
    }

    private List<SableAcousticSpace> linearCandidatesForBounds(SableAcousticBounds bounds) {
        List<SableAcousticSpace> candidates = new ArrayList<>();
        for (SableAcousticSpace space : spaces) {
            if (space.intersectsWorldBounds(bounds)) {
                candidates.add(space);
            }
        }
        return List.copyOf(candidates);
    }

    private record CellRange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static CellRange of(SableAcousticBounds bounds) {
            return new CellRange(
                    cell(bounds.minX()),
                    cell(bounds.minY()),
                    cell(bounds.minZ()),
                    cell(bounds.maxX()),
                    cell(bounds.maxY()),
                    cell(bounds.maxZ())
            );
        }

        boolean exceedsMaxQueryCells() {
            long xCells = (long) maxX - minX + 1L;
            long yCells = (long) maxY - minY + 1L;
            long zCells = (long) maxZ - minZ + 1L;

            if (xCells > MAX_QUERY_CELLS || yCells > MAX_QUERY_CELLS || zCells > MAX_QUERY_CELLS) {
                return true;
            }

            long xyCells = xCells * yCells;
            return xyCells > MAX_QUERY_CELLS || xyCells * zCells > MAX_QUERY_CELLS;
        }
    }

    private record CellKey(int x, int y, int z) {
    }

    private static int cell(double value) {
        return Math.floorDiv((int) Math.floor(value), CELL_SIZE);
    }

}
