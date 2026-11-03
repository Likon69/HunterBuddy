package com.hunterbuddy.render;

import net.minecraft.util.math.Vec3d;

public final class DroneBypass {
    private DroneBypass() {}

    public static Vec3d axis(Vec3d from, Vec3d to, Vec3d fallback) {
        Vec3d way = to.subtract(from);
        double flat = Math.hypot(way.x, way.z);
        Vec3d axis = flat > 1.0e-3 ? new Vec3d(-way.z / flat, 0.0, way.x / flat) : fallback;

        double span = way.lengthSquared();
        double along = span > 1.0e-9 ? Math.max(0.0, Math.min(1.0, -from.dotProduct(way) / span)) : 0.0;
        Vec3d closest = from.add(way.multiply(along));
        return closest.dotProduct(axis) < 0.0 ? axis.multiply(-1.0) : axis;
    }

    public static Vec3d apply(Vec3d point, Vec3d axis, double minimum) {
        double length = point.length();
        return length >= minimum ? point : point.add(axis.multiply(minimum - length));
    }
}
