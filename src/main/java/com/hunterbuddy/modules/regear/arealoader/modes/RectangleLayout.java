package com.hunterbuddy.modules.regear.arealoader.modes;

import net.minecraft.util.math.BlockPos;

final class RectangleLayout {
    private final BlockPos initial;
    private final BlockPos target;
    private final int blockGap;

    RectangleLayout(BlockPos initial, BlockPos target, int blockGap) {
        this.initial = initial;
        this.target = target;
        this.blockGap = blockGap;
    }

    int rowEndX(float yaw) {
        return yaw == RectangleYaw.EAST ? Math.max(initial.getX(), target.getX()) : Math.min(initial.getX(), target.getX());
    }

    boolean hasPassedRowEnd(float yaw, double x) {
        if (yaw == RectangleYaw.EAST) return x >= rowEndX(yaw);
        return yaw == RectangleYaw.WEST && x <= rowEndX(yaw);
    }

    float yawAfterRowEnd(double z) {
        return z < target.getZ() ? RectangleYaw.SOUTH : RectangleYaw.NORTH;
    }

    float yawForNextRow(double x) {
        return initial.getX() > x ? RectangleYaw.EAST : RectangleYaw.WEST;
    }

    int nextRowZ(float yaw, int lastCompleteRowZ) {
        return yaw == RectangleYaw.SOUTH ? lastCompleteRowZ + blockGap : lastCompleteRowZ - blockGap;
    }

    double estimateSeconds(int currentZ, double speed) {
        int rowsRemaining = Math.abs(currentZ - target.getZ()) / blockGap;
        double blocks = (double) rowsRemaining * (Math.abs(initial.getX() - target.getX()) + blockGap);
        return blocks / speed;
    }
}
