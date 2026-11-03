package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaLoaderMode.CoveragePreview;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.math.BlockPos;

final class SpiralPreview {
    private static final int MIN_GAP = 16;
    private static final int MAX_BACKWARD_LEGS = 60;
    private static final int FORECAST_CORNERS = 4;
    private static final String LABEL = "spiral";

    private final SpiralLeg current;
    private final BlockPos pendingCorner;
    private final int gap;

    SpiralPreview(SpiralLeg current, BlockPos pendingCorner, int blockGap) {
        this.current = current;
        this.pendingCorner = pendingCorner;
        this.gap = Math.max(MIN_GAP, blockGap);
    }

    CoveragePreview build(double playerX, double playerZ, boolean recovering) {
        List<double[]> points = flownPoints();
        int flown = points.size() - 1;
        BlockPos nextCorner = nextCorner();
        points.addAll(forecastPoints(nextCorner));
        double nextTurn = Math.hypot(nextCorner.getX() - playerX, nextCorner.getZ() - playerZ);
        String detail = current.width() + "x" + current.height();
        return new CoveragePreview(points, flown, playerX, playerZ, LABEL, detail, nextTurn, recovering);
    }

    private BlockPos nextCorner() {
        return pendingCorner != null ? pendingCorner : current.end(gap);
    }

    private List<double[]> flownPoints() {
        List<double[]> points = new ArrayList<>();
        points.add(point(current.start()));
        SpiralLeg leg = current;
        for (int step = 0; step < MAX_BACKWARD_LEGS; step++) {
            SpiralLeg previous = leg.previous(gap).orElse(null);
            if (previous == null) break;
            points.add(point(previous.start()));
            leg = previous;
        }
        Collections.reverse(points);
        return points;
    }

    private List<double[]> forecastPoints(BlockPos nextCorner) {
        List<double[]> points = new ArrayList<>();
        points.add(point(nextCorner));
        SpiralLeg ahead = current.next(gap);
        for (int corner = 1; corner < FORECAST_CORNERS; corner++) {
            points.add(point(ahead.end(gap)));
            ahead = ahead.next(gap);
        }
        return points;
    }

    private static double[] point(BlockPos pos) {
        return new double[]{pos.getX(), pos.getZ()};
    }
}
