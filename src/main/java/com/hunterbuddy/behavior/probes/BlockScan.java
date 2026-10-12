package com.hunterbuddy.behavior.probes;

import com.hunterbuddy.behavior.mc.WorldIdentity;
import java.util.List;
import java.util.Objects;
import net.minecraft.util.math.BlockPos;

public record BlockScan(WorldIdentity world, BlockPos origin, List<BlockPos> blocks) {
    public BlockScan {
        Objects.requireNonNull(world);
        origin = Objects.requireNonNull(origin).toImmutable();
        blocks = blocks.stream().map(BlockPos::toImmutable).toList();
    }
}
