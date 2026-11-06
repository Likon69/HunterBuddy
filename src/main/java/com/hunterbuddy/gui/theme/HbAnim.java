package com.hunterbuddy.gui.theme;

public final class HbAnim {
    private static final long START_NANOS = System.nanoTime();
    private static final double[] EASE_OUT = easeOutTable();
    private static long frameNanos = START_NANOS;
    private static double seconds;
    private static double breathPeriod;
    private static long breathFrame = -1;
    private static double breath;

    private HbAnim() {
    }

    public static void beginFrame() {
        frameNanos = System.nanoTime();
        seconds = (frameNanos - START_NANOS) / 1_000_000_000.0;
    }

    public static long now() {
        return frameNanos;
    }

    public static double breath(double periodSeconds) {
        if (breathFrame != frameNanos || breathPeriod != periodSeconds) {
            double phase = sweep(periodSeconds);
            breath = 0.78 + 0.22 * ease(phase < 0.5 ? phase * 2 : (1 - phase) * 2, 0.42, 0, 0.58, 1);
            breathFrame = frameNanos;
            breathPeriod = periodSeconds;
        }
        return breath;
    }

    public static double sweep(double periodSeconds) {
        return seconds % periodSeconds / periodSeconds;
    }

    public static double easeOut(double progress) {
        double position = Math.clamp(progress, 0, 1) * (EASE_OUT.length - 1);
        int index = (int) position;
        if (index == EASE_OUT.length - 1) return 1;
        return EASE_OUT[index] + (EASE_OUT[index + 1] - EASE_OUT[index]) * (position - index);
    }

    private static double[] easeOutTable() {
        double[] table = new double[1025];
        for (int i = 0; i < table.length; i++) table[i] = ease(i / 1024.0, 0, 0, 0.58, 1);
        return table;
    }

    private static double ease(double progress, double x1, double y1, double x2, double y2) {
        double low = 0;
        double high = 1;
        for (int i = 0; i < 24; i++) {
            double t = (low + high) / 2;
            if (bezier(t, x1, x2) < progress) low = t;
            else high = t;
        }
        return bezier((low + high) / 2, y1, y2);
    }

    private static double bezier(double t, double first, double second) {
        double inverse = 1 - t;
        return 3 * inverse * inverse * t * first + 3 * inverse * t * t * second + t * t * t;
    }
}
