package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public final class ChunkScanner {
    private final SpawnGate spawnGate;
    private final BlockEntityClassifier classifier;
    private final SupportBlockFilter supportFilter;
    private final PackingTest packingTest;

    public ChunkScanner(
        SpawnGate spawnGate, BlockEntityClassifier classifier, SupportBlockFilter supportFilter, PackingTest packingTest
    ) {
        this.spawnGate = spawnGate;
        this.classifier = classifier;
        this.supportFilter = supportFilter;
        this.packingTest = packingTest;
    }

    public StashChunk scan(WorldChunk chunk) {
        if (MansionDetector.isMansion(chunk) || spawnGate.isTooCloseToSpawn(chunk.getPos())) return null;
        StashChunk result = new StashChunk(chunk.getPos());
        result.dimension = StashDimensions.current();
        List<BlockPos> containers = new ArrayList<>();
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            tally(chunk, entity, result, containers);
        }
        return packingTest.isScatteredStructure(containers) ? null : result;
    }

    private void tally(WorldChunk chunk, BlockEntity entity, StashChunk result, List<BlockPos> containers) {
        if (supportFilter.isNearBlacklisted(chunk.getWorld(), entity)) return;
        StashType type = classifier.classify(entity);
        if (type == null) return;
        result.increment(type);
        if (type.isContainer()) containers.add(entity.getPos());
    }
}
