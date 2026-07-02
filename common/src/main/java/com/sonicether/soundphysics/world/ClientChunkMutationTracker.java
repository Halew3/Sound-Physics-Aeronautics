package com.sonicether.soundphysics.world;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

public final class ClientChunkMutationTracker {

    private static final Map<ClientLevel, Map<Long, Long>> MUTATIONS_BY_LEVEL = new WeakHashMap<>();
    private static long nextMutationVersion = 1L;

    private ClientChunkMutationTracker() {
    }

    public static synchronized void recordChanged(ClientLevel level, ChunkPos chunkPos) {
        Map<Long, Long> levelMutations = MUTATIONS_BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
        levelMutations.put(chunkPos.toLong(), nextMutationVersion++);
    }

    public static synchronized long version(ClientLevel level, ChunkPos chunkPos) {
        Map<Long, Long> levelMutations = MUTATIONS_BY_LEVEL.get(level);
        if (levelMutations == null) {
            return 0L;
        }

        return levelMutations.getOrDefault(chunkPos.toLong(), 0L);
    }

    public static synchronized void clear(ClientLevel level) {
        MUTATIONS_BY_LEVEL.remove(level);
    }

}
