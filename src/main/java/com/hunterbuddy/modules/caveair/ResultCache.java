package com.hunterbuddy.modules.caveair;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.ChunkPos;

final class ResultCache {
    private final Map<Long, ChunkResult> results = new ConcurrentHashMap<>();

    void put(long chunkKey, ChunkResult result) {
        results.put(chunkKey, result);
    }

    void remove(long chunkKey) {
        results.remove(chunkKey);
    }

    void clear() {
        results.clear();
    }

    int size() {
        return results.size();
    }

    int portalCount() {
        return results.values().stream().mapToInt(result -> result.portals().size()).sum();
    }

    Collection<ChunkResult> all() {
        return results.values();
    }

    int evictBeyond(ChunkPos centre, double maxChunkDistance) {
        int before = results.size();
        results.values().removeIf(result -> chunkDistance(result, centre) > maxChunkDistance);
        return before - results.size();
    }

    private static double chunkDistance(ChunkResult result, ChunkPos centre) {
        return Math.hypot(result.chunkX() - centre.x, result.chunkZ() - centre.z);
    }
}
