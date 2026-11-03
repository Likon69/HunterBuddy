package com.hunterbuddy.modules.caveair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

final class GreedyMesher {
    private static final Comparator<BlockPos> SCAN_ORDER = Comparator.comparingInt(BlockPos::getY)
        .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX);

    List<CellBox> mesh(List<BlockPos> cells) {
        return new Pass(cells).run();
    }

    private static final class Pass {
        private final Set<BlockPos> available;
        private final List<BlockPos> ordered;

        Pass(List<BlockPos> cells) {
            available = new HashSet<>(cells);
            ordered = new ArrayList<>(cells);
            ordered.sort(SCAN_ORDER);
        }

        List<CellBox> run() {
            List<CellBox> boxes = new ArrayList<>();
            for (BlockPos start : ordered) {
                if (available.contains(start)) {
                    boxes.add(growBox(start));
                }
            }
            return boxes;
        }

        private CellBox growBox(BlockPos start) {
            int cellsX = 1;
            while (available.contains(start.add(cellsX, 0, 0))) {
                cellsX++;
            }
            int cellsZ = 1;
            while (isRowAvailable(start, cellsX, cellsZ)) {
                cellsZ++;
            }
            int cellsY = 1;
            while (isLayerAvailable(start, cellsX, cellsZ, cellsY)) {
                cellsY++;
            }
            consume(start, cellsX, cellsY, cellsZ);
            return CellBox.ofCells(start.getX(), start.getY(), start.getZ(), cellsX, cellsY, cellsZ);
        }

        private boolean isLayerAvailable(BlockPos start, int cellsX, int cellsZ, int yOffset) {
            for (int dz = 0; dz < cellsZ; dz++) {
                if (!isRowAvailable(start.add(0, yOffset, 0), cellsX, dz)) return false;
            }
            return true;
        }

        private boolean isRowAvailable(BlockPos start, int cellsX, int zOffset) {
            for (int dx = 0; dx < cellsX; dx++) {
                if (!available.contains(start.add(dx, 0, zOffset))) return false;
            }
            return true;
        }

        private void consume(BlockPos start, int cellsX, int cellsY, int cellsZ) {
            for (int dx = 0; dx < cellsX; dx++) {
                for (int dy = 0; dy < cellsY; dy++) {
                    for (int dz = 0; dz < cellsZ; dz++) {
                        available.remove(start.add(dx, dy, dz));
                    }
                }
            }
        }
    }
}
