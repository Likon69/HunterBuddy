package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import net.minecraft.util.math.ChunkPos;

public final class EntityDetections {
    private final StashLedger ledger;
    private final StashRecorder recorder;
    private final SpawnGate spawnGate;
    private final OldChunkGate oldChunkGate;

    public EntityDetections(StashLedger ledger, StashRecorder recorder, SpawnGate spawnGate, OldChunkGate oldChunkGate) {
        this.ledger = ledger;
        this.recorder = recorder;
        this.spawnGate = spawnGate;
        this.oldChunkGate = oldChunkGate;
    }

    public void record(ChunkPos chunk, StashType type, int count) {
        if (spawnGate.isTooCloseToSpawn(chunk)) return;
        if (oldChunkGate.defersScans() && !oldChunkGate.isOld(chunk.x, chunk.z)) return;
        StashChunk existing = ledger.find(chunk);
        StashChunk before = existing == null ? null : existing.copyOfCounters();
        StashChunk target = existing != null ? existing : freshChunk(chunk);
        target.setCount(type, Math.max(target.countOf(type), count));
        recorder.commit(target, before);
    }

    private static StashChunk freshChunk(ChunkPos chunk) {
        StashChunk created = new StashChunk(chunk);
        created.dimension = StashDimensions.current();
        return created;
    }
}
