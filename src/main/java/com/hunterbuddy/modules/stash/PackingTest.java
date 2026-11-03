package com.hunterbuddy.modules.stash;

import net.minecraft.util.math.BlockPos;

import java.util.List;

public final class PackingTest {
    private final StashFilterSettings settings;

    public PackingTest(StashFilterSettings settings) {
        this.settings = settings;
    }

    public boolean isScatteredStructure(List<BlockPos> containers) {
        if (!settings.ignoreScatteredContainers.get()) return false;
        int needed = settings.minPackedContainers.get();
        if (containers.size() < needed) return false;
        for (BlockPos center : containers) {
            if (neighborsWithin(center, containers) >= needed) return false;
        }
        return true;
    }

    private int neighborsWithin(BlockPos center, List<BlockPos> containers) {
        long radius = settings.packRadius.get();
        int count = 0;
        for (BlockPos other : containers) {
            if (center.getSquaredDistance(other) <= radius * radius) count++;
        }
        return count;
    }
}
