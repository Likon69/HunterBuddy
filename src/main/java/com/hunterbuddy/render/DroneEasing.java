package com.hunterbuddy.render;

public final class DroneEasing {
    private DroneEasing() {}

    public static double smootherstep(double t) {
        double x = Math.max(0.0, Math.min(1.0, t));
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    public static double smoothstep(double t) {
        double x = Math.max(0.0, Math.min(1.0, t));
        return x * x * (3.0 - 2.0 * x);
    }

    public static double follow(double dt, double tau) {
        return 1.0 - Math.exp(-dt / tau);
    }
}
