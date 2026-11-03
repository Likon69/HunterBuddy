package com.hunterbuddy.render;

import java.util.List;
import net.minecraft.util.math.Vec3d;

public final class DroneRoomLoop {
    private static final double SAMPLE_STEP = 0.5;

    private final List<Vec3d> corners;
    private final double length;

    public DroneRoomLoop(List<Vec3d> corners) {
        this.corners = corners;
        double total = 0.0;
        for (int i = 0; i < corners.size(); i++) total += corners.get(i).distanceTo(corners.get((i + 1) % corners.size()));
        this.length = total;
    }

    public Vec3d at(double along) {
        double remaining = ((along % length) + length) % length;

        for (int i = 0; i < corners.size(); i++) {
            Vec3d from = corners.get(i);
            Vec3d to = corners.get((i + 1) % corners.size());
            double edge = from.distanceTo(to);
            if (remaining <= edge && edge > 1.0e-9) return from.lerp(to, remaining / edge);
            remaining -= edge;
        }

        return corners.get(0);
    }

    public double farthestFrom(Vec3d point) {
        double best = 0.0;
        double bestDistance = -1.0;

        for (double along = 0.0; along < length; along += SAMPLE_STEP) {
            double distance = at(along).squaredDistanceTo(point);
            if (distance > bestDistance) {
                bestDistance = distance;
                best = along;
            }
        }

        return best;
    }

    public Vec3d centre() {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Vec3d corner : corners) {
            x += corner.x;
            y += corner.y;
            z += corner.z;
        }
        return new Vec3d(x / corners.size(), y / corners.size(), z / corners.size());
    }
}
