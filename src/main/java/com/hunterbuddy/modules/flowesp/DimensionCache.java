package com.hunterbuddy.modules.flowesp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.math.ChunkPos;

final class DimensionCache {
    private static final int INITIAL_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private final Map<Long, FlowChunk> chunks;
    private final Set<Long> scanned = new HashSet<>();

    DimensionCache(int capacity) {
        chunks = new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, FlowChunk> eldest) {
                boolean overflow = size() > capacity;
                if (overflow) {
                    scanned.remove(eldest.getKey());
                }
                return overflow;
            }
        };
    }

    synchronized boolean isScanned(long chunkKey) {
        return scanned.contains(chunkKey);
    }

    synchronized void add(FlowChunk chunk) {
        long key = chunk.pos().toLong();
        chunks.put(key, chunk);
        scanned.add(key);
    }

    synchronized List<FlowChunk> snapshot() {
        return new ArrayList<>(chunks.values());
    }

    synchronized int size() {
        return chunks.size();
    }

    synchronized void evictBeyond(ChunkPos centre, double maxChunkDistance) {
        chunks.values().removeIf(chunk -> chunkDistance(chunk, centre) > maxChunkDistance);
    }

    synchronized void clear() {
        chunks.clear();
        scanned.clear();
    }

    private static double chunkDistance(FlowChunk chunk, ChunkPos centre) {
        return Math.hypot(chunk.pos().x - centre.x, chunk.pos().z - centre.z);
    }
}
