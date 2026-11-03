package com.hunterbuddy.modules.caveair;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

record CellBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    static CellBox ofCells(int minX, int minY, int minZ, int cellsX, int cellsY, int cellsZ) {
        return new CellBox(minX, minY, minZ, minX + cellsX, minY + cellsY, minZ + cellsZ);
    }

    BlockPos minCorner() {
        return new BlockPos(minX, minY, minZ);
    }

    double centerX() {
        return (minX + maxX) / 2.0;
    }

    double centerZ() {
        return (minZ + maxZ) / 2.0;
    }

    double distanceSquaredTo(Vec3d point) {
        double dx = axisGap(point.x, minX, maxX);
        double dy = axisGap(point.y, minY, maxY);
        double dz = axisGap(point.z, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisGap(double value, double low, double high) {
        return Math.max(Math.max(low - value, value - high), 0.0);
    }
}
