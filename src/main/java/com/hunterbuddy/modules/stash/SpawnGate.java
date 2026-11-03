package com.hunterbuddy.modules.stash;

import net.minecraft.util.math.ChunkPos;

public final class SpawnGate {
    private static final int CHUNK_SIZE = 16;

    private final StashGeneralSettings settings;

    public SpawnGate(StashGeneralSettings settings) {
        this.settings = settings;
    }

    public boolean isTooCloseToSpawn(ChunkPos chunk) {
        double distance = Math.hypot(Math.abs(chunk.x * CHUNK_SIZE), Math.abs(chunk.z * CHUNK_SIZE));
        return distance < settings.minimumDistance.get();
    }
}
