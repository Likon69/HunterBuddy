package com.hunterbuddy.modules.rotationdetector;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.BlockPos;

final class DetectionCache {
    private final Map<Long, Set<BlockPos>> chunks = new ConcurrentHashMap<>();
    private int total;

    void clear() {
        chunks.clear();
        total = 0;
    }

    void store(long chunkKey, Set<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            chunks.remove(chunkKey);
        } else {
            chunks.put(chunkKey, blocks);
        }
    }

    void remove(long chunkKey) {
        chunks.remove(chunkKey);
    }

    void evictDownTo(int maxChunks) {
        Iterator<Long> keys = chunks.keySet().iterator();
        while (chunks.size() > maxChunks && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    void recountBlocks() {
        total = chunks.values().stream().mapToInt(Set::size).sum();
    }

    int totalBlocks() {
        return total;
    }

    Iterable<Set<BlockPos>> chunkBlocks() {
        return chunks.values();
    }
}
