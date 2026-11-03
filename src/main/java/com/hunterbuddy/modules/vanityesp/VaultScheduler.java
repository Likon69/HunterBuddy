package com.hunterbuddy.modules.vanityesp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

final class VaultScheduler {
    private static final int NEW_CHUNK_DELAY_TICKS = 10;
    private static final long RECHECK_PERIOD_MS = 4000;
    private static final long RESCAN_PERIOD_MS = 3000;
    private static final int INITIAL_SCAN_TICKS = 70;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final VaultRegistry registry;
    private final Deque<Long> initialQueue = new ArrayDeque<>();
    private final Map<Long, Integer> pending = new HashMap<>();
    private Set<Long> lastLoaded = new HashSet<>();
    private int quota = 1;
    private boolean initialScanActive;
    private long lastRecheckAt;
    private long lastRescanAt;

    VaultScheduler(VaultRegistry registry) {
        this.registry = registry;
    }

    void reset() {
        registry.clear();
        initialQueue.clear();
        pending.clear();
        lastLoaded.clear();
        initialScanActive = false;
    }

    void startInitialScan() {
        if (mc.world == null || mc.player == null) return;
        initialQueue.clear();
        initialQueue.addAll(viewSquare());
        quota = Math.max(1, (int) Math.ceil(initialQueue.size() / (double) INITIAL_SCAN_TICKS));
        initialScanActive = true;
    }

    void tick() {
        if (mc.world == null || mc.player == null) return;
        runInitialScan();
        Set<Long> square = viewSquare();
        forgetLeftChunks(square);
        scheduleNewChunks(square);
        lastLoaded = square;
        scanDueChunks();
        runPeriodicChecks();
    }

    private void runInitialScan() {
        if (!initialScanActive) return;
        for (int scanned = 0; scanned < quota && !initialQueue.isEmpty(); scanned++) {
            scanIfLoaded(initialQueue.poll());
        }
        initialScanActive = !initialQueue.isEmpty();
    }

    private Set<Long> viewSquare() {
        int view = mc.options.getViewDistance().getValue();
        ChunkPos centre = mc.player.getChunkPos();
        Set<Long> square = new HashSet<>();
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                square.add(ChunkPos.toLong(centre.x + dx, centre.z + dz));
            }
        }
        return square;
    }

    private void forgetLeftChunks(Set<Long> square) {
        for (long key : lastLoaded) {
            if (!square.contains(key)) {
                registry.forget(key);
                pending.remove(key);
            }
        }
    }

    private void scheduleNewChunks(Set<Long> square) {
        for (long key : square) {
            if (!lastLoaded.contains(key)) {
                pending.put(key, NEW_CHUNK_DELAY_TICKS);
            }
        }
    }

    private void scanDueChunks() {
        Iterator<Map.Entry<Long, Integer>> entries = pending.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, Integer> entry = entries.next();
            entry.setValue(entry.getValue() - 1);
            if (entry.getValue() <= 0) {
                scanIfLoaded(entry.getKey());
                entries.remove();
            }
        }
    }

    private void runPeriodicChecks() {
        long now = System.currentTimeMillis();
        if (now - lastRecheckAt >= RECHECK_PERIOD_MS) {
            lastRecheckAt = now;
            registry.recheck(mc.world);
        }
        if (now - lastRescanAt >= RESCAN_PERIOD_MS) {
            lastRescanAt = now;
            lastLoaded.forEach(this::scanIfLoaded);
        }
    }

    private void scanIfLoaded(long chunkKey) {
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(chunkKey), ChunkPos.getPackedZ(chunkKey));
        if (chunk != null) {
            registry.scan(chunk);
        }
    }
}
