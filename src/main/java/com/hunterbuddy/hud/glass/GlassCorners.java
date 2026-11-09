package com.hunterbuddy.hud.glass;

public final class GlassCorners {
    private static final double[][] COS = new double[65][];
    private static final double[][] SIN = new double[65][];

    static {
        for (int segments = 8; segments <= 64; segments++) {
            COS[segments] = new double[segments + 1];
            SIN[segments] = new double[segments + 1];
            for (int i = 0; i <= segments; i++) {
                double angle = Math.PI * i / (2 * segments);
                COS[segments][i] = Math.cos(angle);
                SIN[segments][i] = Math.sin(angle);
            }
        }
    }

    private GlassCorners() { }

    public static int segments(double radius) {
        if (radius <= 0.04) return 8;
        return Math.clamp((int) Math.ceil(Math.PI / (2 * Math.acos(1 - 0.04 / radius))), 8, 64);
    }

    public static double x(int corner, int segments, int index) {
        return switch (corner) {
            case 0 -> -COS[segments][index];
            case 1 -> SIN[segments][index];
            case 2 -> COS[segments][index];
            default -> -SIN[segments][index];
        };
    }

    public static double y(int corner, int segments, int index) {
        return switch (corner) {
            case 0 -> -SIN[segments][index];
            case 1 -> -COS[segments][index];
            case 2 -> SIN[segments][index];
            default -> COS[segments][index];
        };
    }
}
