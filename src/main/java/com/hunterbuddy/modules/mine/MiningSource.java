package com.hunterbuddy.modules.mine;

import java.util.function.Predicate;
import net.minecraft.util.math.BlockPos;

public interface MiningSource {
    boolean ready();

    boolean accepts(BlockPos pos);

    BlockPos next(Predicate<BlockPos> unavailable);

    boolean shouldRender();
}
