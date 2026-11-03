package com.hunterbuddy.modules.caveair;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

record ClusterBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    static ClusterBounds of(Set<BlockPos> cells) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos cell : cells) {
            minX = Math.min(minX, cell.getX());
            minY = Math.min(minY, cell.getY());
            minZ = Math.min(minZ, cell.getZ());
            maxX = Math.max(maxX, cell.getX());
            maxY = Math.max(maxY, cell.getY());
            maxZ = Math.max(maxZ, cell.getZ());
        }
        return new ClusterBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    int extentX() {
        return maxX - minX + 1;
    }

    int extentY() {
        return maxY - minY + 1;
    }

    int extentZ() {
        return maxZ - minZ + 1;
    }

    boolean isFlatWall() {
        return extentX() == 1 || extentZ() == 1;
    }

    int wallWidth() {
        return extentX() == 1 ? extentZ() : extentX();
    }

    CellBox toBox() {
        return new CellBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    Set<BlockPos> corners() {
        Set<BlockPos> corners = new HashSet<>();
        for (int x : new int[]{minX, maxX}) {
            addCornersAt(corners, x);
        }
        return corners;
    }

    private void addCornersAt(Set<BlockPos> corners, int x) {
        for (int y : new int[]{minY, maxY}) {
            corners.add(new BlockPos(x, y, minZ));
            corners.add(new BlockPos(x, y, maxZ));
        }
    }
}
