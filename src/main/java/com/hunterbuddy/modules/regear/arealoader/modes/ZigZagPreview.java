package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaLoaderMode.CoveragePreview;
import com.hunterbuddy.modules.regear.arealoader.Heading;
import java.util.ArrayList;
import java.util.List;

final class ZigZagPreview {
    private static final int FORECAST_LEGS = 4;
    private static final int SEGMENTS_PER_LEG_PAIR = 2;
    private static final float REVERSE_TURN = 180.0f;
    private static final String LABEL = "zigzag";

    private final ZigZag.PathingDataZigZag data;
    private final int legLength;
    private final int rowGap;

    ZigZagPreview(ZigZag.PathingDataZigZag data, int legLength, int rowGap) {
        this.data = data;
        this.legLength = legLength;
        this.rowGap = rowGap;
    }

    boolean isComplete() {
        return data.initialPos != null && data.currPos != null;
    }

    CoveragePreview build(double playerX, double playerZ, double nextTurnDistance, boolean recovering) {
        List<double[]> points = polyline();
        int flown = Math.min(points.size() - 1, data.legsCompleted * SEGMENTS_PER_LEG_PAIR);
        return new CoveragePreview(points, flown, playerX, playerZ, LABEL, legLength + "/" + rowGap,
            nextTurnDistance, recovering, data.legsCompleted);
    }

    private List<double[]> polyline() {
        double[] start = {data.initialPos.getX(), data.initialPos.getZ()};
        List<double[]> points = new ArrayList<>(List.of(start));
        Walker walker = new Walker(start);
        int legPairs = (data.legsCompleted + FORECAST_LEGS) * SEGMENTS_PER_LEG_PAIR / 2;
        for (int pair = 0; pair < legPairs; pair++) {
            points.add(walker.walkMainLeg());
            points.add(walker.walkSideLeg());
        }
        return points;
    }

    private final class Walker {
        private double x;
        private double z;
        private boolean forward = true;

        Walker(double[] start) {
            x = start[0];
            z = start[1];
        }

        double[] walkMainLeg() {
            float yaw = forward ? data.mainYaw : data.mainYaw + REVERSE_TURN;
            return move(yaw, legLength);
        }

        double[] walkSideLeg() {
            double[] end = move(data.sideYaw, rowGap);
            forward = !forward;
            return end;
        }

        private double[] move(float yaw, int length) {
            Heading heading = Heading.of(yaw);
            x += heading.stepX() * length;
            z += heading.stepZ() * length;
            return new double[]{x, z};
        }
    }
}
