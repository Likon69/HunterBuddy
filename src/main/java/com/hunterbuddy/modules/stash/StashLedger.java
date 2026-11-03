package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public final class StashLedger {
    private final StashFileStore store = new StashFileStore();
    private List<StashChunk> chunks = new ArrayList<>();
    private boolean loaded;

    public List<StashChunk> chunks() {
        return chunks;
    }

    public List<StashChunk> reset() {
        chunks = new ArrayList<>();
        loaded = false;
        return chunks;
    }

    public void ensureLoaded() {
        if (loaded) return;
        List<StashChunk> merged = StashFileStore.mergeByPosition(store.read(), chunks);
        chunks.clear();
        chunks.addAll(merged);
        loaded = true;
    }

    public StashChunk find(ChunkPos position) {
        for (StashChunk chunk : chunks) {
            if (chunk.chunkPos.equals(position)) return chunk;
        }
        return null;
    }

    public void put(StashChunk chunk) {
        int index = chunks.indexOf(chunk);
        if (index >= 0) chunks.set(index, chunk);
        else chunks.add(chunk);
    }

    public void remove(StashChunk chunk) {
        chunks.remove(chunk);
    }

    public void clearAll() {
        chunks.clear();
        loaded = true;
    }

    public void save() {
        List<StashChunk> snapshot = new ArrayList<>(chunks);
        if (loaded) store.writeComplete(snapshot);
        else store.writeMergedWithDisk(snapshot);
    }
}
