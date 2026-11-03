package com.hunterbuddy.modules.vanityesp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

final class VaultRegistry {
    private final Map<Long, Set<BlockPos>> byChunk = new HashMap<>();

    void clear() {
        byChunk.clear();
    }

    List<BlockPos> all() {
        List<BlockPos> positions = new ArrayList<>();
        byChunk.values().forEach(positions::addAll);
        return positions;
    }

    void forget(long chunkKey) {
        byChunk.remove(chunkKey);
    }

    void scan(WorldChunk chunk) {
        Set<BlockPos> found = new HashSet<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (isOminousVault(blockEntity.getCachedState())) {
                found.add(blockEntity.getPos());
            }
        }
        if (!found.isEmpty()) {
            byChunk.put(chunk.getPos().toLong(), found);
        }
    }

    void recheck(World world) {
        byChunk.values().forEach(positions -> positions.removeIf(pos -> !isOminousVault(world.getBlockState(pos))));
        byChunk.values().removeIf(Set::isEmpty);
    }

    private static boolean isOminousVault(BlockState state) {
        return state.isOf(Blocks.VAULT) && state.contains(Properties.OMINOUS) && state.get(Properties.OMINOUS);
    }
}
