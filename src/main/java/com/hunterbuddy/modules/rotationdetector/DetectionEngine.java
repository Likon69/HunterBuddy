package com.hunterbuddy.modules.rotationdetector;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public final class DetectionEngine {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final RotationDetectorOptions options;
    private final ChunkScanQueue queue = new ChunkScanQueue();
    private final DetectionCache cache = new DetectionCache();
    private final SidewaysBlockScanner scanner;

    public DetectionEngine(RotationDetectorOptions options) {
        this.options = options;
        this.scanner = new SidewaysBlockScanner(options);
    }

    DetectionCache cache() {
        return cache;
    }

    public void reset() {
        queue.clear();
        cache.clear();
    }

    public void queueViewChunks() {
        if (mc.world == null || mc.player == null) return;
        int view = mc.options.getViewDistance().getValue();
        ChunkPos centre = mc.player.getChunkPos();
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                queueIfLoaded(centre.x + dx, centre.z + dz);
            }
        }
    }

    private void queueIfLoaded(int chunkX, int chunkZ) {
        if (fullChunk(chunkX, chunkZ) != null) {
            queue.add(ChunkPos.toLong(chunkX, chunkZ));
        }
    }

    public void requeue(WorldChunk chunk) {
        long key = chunk.getPos().toLong();
        cache.remove(key);
        queue.add(key);
    }

    public void tick() {
        if (queue.isEmpty()) return;
        scanQueuedChunks();
        cache.evictDownTo(options.maxCacheSize.get());
        cache.recountBlocks();
    }

    public int blockCount() {
        return cache.totalBlocks();
    }

    private void scanQueuedChunks() {
        int scanned = 0;
        while (scanned < options.chunksPerTick.get()) {
            Long key = queue.poll();
            if (key == null) return;
            WorldChunk chunk = fullChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
            if (chunk == null) continue;
            cache.store(key, scanner.scan(chunk, mc.world));
            scanned++;
        }
    }

    private WorldChunk fullChunk(int chunkX, int chunkZ) {
        if (mc.world == null) return null;
        Chunk chunk = mc.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        return chunk instanceof WorldChunk worldChunk ? worldChunk : null;
    }
}
