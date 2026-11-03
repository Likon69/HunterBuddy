package com.hunterbuddy.modules.regear.arealoader;

import net.minecraft.util.math.BlockPos;

record RecoveryPoint(int x, int z) {
    private static final int PATH_Y = 64;

    BlockPos atPathLevel() {
        return new BlockPos(x, PATH_Y, z);
    }
}
