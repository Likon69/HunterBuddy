package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaLoaderMode.CoveragePreview;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;

final class RectanglePreview {
    private static final int MIN_GAP = 16;
    private static final int MAX_ROWS = 400;
    private static final int SEGMENTS_PER_ROW = 2;
    private static final String LABEL = "rect";

    private final Rectangle.PathingDataRectangle data;
    private final int gap;

    RectanglePreview(Rectangle.PathingDataRectangle data, int blockGap) {
        this.data = data;
        this.gap = Math.max(MIN_GAP, blockGap);
    }

    boolean isComplete() {
        return data.initialPos != null && data.targetPos != null && data.currPos != null;
    }

    CoveragePreview build(double playerX, double playerZ, double nextTurnDistance, boolean recovering) {
        List<double[]> points = polyline();
        int flown = Math.min(points.size() - 1, rowsCompleted() * SEGMENTS_PER_ROW);
        return new CoveragePreview(points, flown, playerX, playerZ, LABEL, detail(), nextTurnDistance, recovering);
    }

    private int rowsCompleted() {
        return Math.abs(data.lastCompleteRowZ - data.initialPos.getZ()) / gap;
    }

    private String detail() {
        int width = Math.abs(data.targetPos.getX() - data.initialPos.getX());
        int depth = Math.abs(data.targetPos.getZ() - data.initialPos.getZ());
        return width + "x" + depth;
    }

    private List<double[]> polyline() {
        BlockPos start = data.initialPos;
        int farX = data.targetPos.getX();
        int direction = data.targetPos.getZ() >= start.getZ() ? 1 : -1;
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{start.getX(), start.getZ()});
        int z = start.getZ();
        int x = start.getX();
        for (int row = 0; row < MAX_ROWS; row++) {
            x = x == start.getX() ? farX : start.getX();
            points.add(new double[]{x, z});
            if (hasReachedEnd(z, direction)) break;
            z += direction * gap;
            points.add(new double[]{x, z});
        }
        return points;
    }

    private boolean hasReachedEnd(int z, int direction) {
        int end = data.targetPos.getZ();
        return direction > 0 ? z >= end : z <= end;
    }
}
