package com.hunterbuddy.modules.caveair;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;
import net.minecraft.util.math.ChunkPos;

final class ScanDispatcher {
    private static final int WORKER_THREADS = 2;
    private static final int BATCH_SIZE = 4;
    private static final String THREAD_PREFIX = "CaveAirESP-Scan-";

    private final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private final Set<Long> inProgress = ConcurrentHashMap.newKeySet();
    private final LongConsumer scanJob;
    private ExecutorService pool;

    ScanDispatcher(LongConsumer scanJob) {
        this.scanJob = scanJob;
    }

    void start() {
        AtomicInteger counter = new AtomicInteger();
        pool = Executors.newFixedThreadPool(WORKER_THREADS, task -> {
            Thread thread = new Thread(task, THREAD_PREFIX + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    void stop() {
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        clearPending();
    }

    void clearPending() {
        pending.clear();
    }

    void queue(long chunkKey) {
        if (!inProgress.contains(chunkKey)) {
            pending.add(chunkKey);
        }
    }

    boolean isScanning(long chunkKey) {
        return inProgress.contains(chunkKey);
    }

    void dispatch(ChunkPos playerChunk) {
        if (pool == null) return;
        List<Long> nearest = pending.stream()
            .sorted(Comparator.comparingDouble(key -> squaredDistance(key, playerChunk)))
            .limit(BATCH_SIZE).toList();
        for (long key : nearest) {
            if (pending.remove(key)) {
                submit(key);
            }
        }
    }

    private void submit(long chunkKey) {
        inProgress.add(chunkKey);
        pool.execute(() -> {
            try {
                scanJob.accept(chunkKey);
            } finally {
                inProgress.remove(chunkKey);
            }
        });
    }

    private static double squaredDistance(long chunkKey, ChunkPos playerChunk) {
        double dx = ChunkPos.getPackedX(chunkKey) - playerChunk.x;
        double dz = ChunkPos.getPackedZ(chunkKey) - playerChunk.z;
        return dx * dx + dz * dz;
    }
}
