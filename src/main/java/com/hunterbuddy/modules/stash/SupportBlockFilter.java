package com.hunterbuddy.modules.stash;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;

public final class SupportBlockFilter {
    private static final int DEEP_SUPPORT_OFFSET = 2;

    private final StashFilterSettings settings;

    public SupportBlockFilter(StashFilterSettings settings) {
        this.settings = settings;
    }

    public boolean isNearBlacklisted(World world, BlockEntity entity) {
        List<Block> blacklist = settings.blacklistedSupportBlocks.get();
        if (blacklist.isEmpty()) return false;
        BlockPos position = entity.getPos();
        if (isListed(world, position.down(), blacklist) || isListed(world, position.down(DEEP_SUPPORT_OFFSET), blacklist)) {
            return true;
        }
        return isWallMounted(entity) && hasListedNeighbor(world, position, blacklist);
    }

    private static boolean isWallMounted(BlockEntity entity) {
        return entity instanceof BannerBlockEntity || entity instanceof SignBlockEntity;
    }

    private static boolean hasListedNeighbor(World world, BlockPos position, List<Block> blacklist) {
        for (Direction side : Direction.Type.HORIZONTAL) {
            if (isListed(world, position.offset(side), blacklist)) return true;
        }
        return false;
    }

    private static boolean isListed(World world, BlockPos position, List<Block> blacklist) {
        return blacklist.contains(world.getBlockState(position).getBlock());
    }
}
