package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.resource.LookIntent;

public final class Conditions {
    public static final float LOOK_TOLERANCE = 2;

    private Conditions() {
    }

    public static double distance(Sensors sensors, TargetPoint point) {
        return point.distance(sensors.x(), sensors.y(), sensors.z());
    }

    public static boolean near(Sensors sensors, TargetPoint point, double radius) {
        return distance(sensors, point) <= radius;
    }

    public static float wrap(float degrees) {
        float wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    public static boolean facing(Sensors sensors, LookIntent target) {
        return Math.abs(wrap(wrap(target.yaw()) - wrap(sensors.yaw()))) <= LOOK_TOLERANCE
            && Math.abs(target.pitch() - sensors.pitch()) <= LOOK_TOLERANCE;
    }

    public static LookIntent towards(TargetPoint from, TargetPoint target) {
        double dx = target.x() - from.x();
        double dy = target.y() - from.y();
        double dz = target.z() - from.z();
        return new LookIntent((float) Math.toDegrees(Math.atan2(-dx, dz)),
            (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
