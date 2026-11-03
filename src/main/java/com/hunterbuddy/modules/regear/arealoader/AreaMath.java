package com.hunterbuddy.modules.regear.arealoader;

import net.minecraft.util.math.BlockPos;

public final class AreaMath {
    private AreaMath() {
    }

    public static double horizontalDistance(BlockPos pos, double x, double z) {
        return Math.hypot(pos.getX() - x, pos.getZ() - z);
    }

    public static double horizontalDistance(BlockPos a, BlockPos b) {
        return horizontalDistance(a, b.getX(), b.getZ());
    }
}
