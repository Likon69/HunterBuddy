package com.hunterbuddy.modules.regear.util;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

final class YawMath {
    private static final double FULL_TURN = 360.0;
    private static final double AXIS_STEP = 45.0;

    private YawMath() {
    }

    static Vec3d yawToDirection(double yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3d(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    static Vec3d positionInDirection(Vec3d position, double yaw, double distance) {
        return position.add(yawToDirection(yaw).multiply(distance));
    }

    static double distancePointToDirection(Vec3d point, Vec3d direction, @Nullable Vec3d start) {
        Vec3d unit = direction.normalize();
        Vec3d offset = point.subtract(start == null ? Vec3d.ZERO : start);
        return offset.subtract(unit.multiply(offset.dotProduct(unit))).length();
    }

    static double angleOnAxis(double yaw) {
        double positive = yaw < 0.0 ? yaw + FULL_TURN : yaw;
        return Math.round(positive / AXIS_STEP) * AXIS_STEP;
    }
}
