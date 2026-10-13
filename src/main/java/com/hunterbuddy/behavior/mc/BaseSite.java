package com.hunterbuddy.behavior.mc;

import net.minecraft.util.math.BlockPos;
import java.util.List;

public final class BaseSite {
    private BaseSite() {}

    public static BlockPos choose(BlockPos anchor, List<BlockPos> chests, WorldReader world) {
        BlockPos landing = world.feet();
        if (usable(landing, chests, world)) {
            return landing;
        }
        for (int radius = 1; radius <= 64; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    BlockPos pos = world.surface(anchor.getX() + x, anchor.getZ() + z);
                    if (pos != null && usable(pos, chests, world)) {
                        return pos;
                    }
                }
            }
        }
        throw new IllegalStateException("aucun sol sec libre près des coffres pour le matériel temporaire");
    }

    private static boolean usable(BlockPos pos, List<BlockPos> chests, WorldReader world) {
        return !chests.contains(pos) && !chests.contains(pos.east()) && !chests.contains(pos.south())
            && clear(pos, world) && clear(pos.east(), world) && clear(pos.south(), world);
    }

    private static boolean clear(BlockPos pos, WorldReader world) {
        return world.loaded(pos) && world.inBounds(pos.up()) && world.state(pos).isAir() && world.state(pos.up()).isAir()
            && MineGeometry.floor(world, pos.down())
            && world.state(pos.down()).getFluidState().isEmpty() && !world.state(pos.down()).hasBlockEntity();
    }
}
