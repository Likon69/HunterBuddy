package com.hunterbuddy.modules.signs;

import net.minecraft.util.math.BlockPos;

public record SignKey(String dimension, int x, int y, int z) {
    public static SignKey of(String dimension, BlockPos pos) {
        return new SignKey(dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }
}
