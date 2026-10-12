package com.hunterbuddy.util;

public final class TrailHeading {
    public enum Verdict { OUTSIDE, BEHIND, SIDE, AHEAD }

    private double steady;
    private double wanted;

    public static double wrap(double yaw) {
        return ((yaw % 360.0) + 360.0) % 360.0;
    }

    public void reset(double yaw) {
        steady = wrap(yaw);
        wanted = steady;
    }

    public void setWanted(double yaw) {
        wanted = wrap(yaw);
    }

    public double steady() {
        return steady;
    }

    public double wanted() {
        return wanted;
    }

    public double turn(double maxDegPerTick) {
        double diff = TrailUtils.angleDifference(wanted, steady);
        if (Math.abs(diff) <= maxDegPerTick) {
            steady = wanted;
        } else {
            steady = wrap(steady + Math.copySign(maxDegPerTick, diff));
        }
        return steady;
    }

    public double offset(double yaw) {
        return TrailUtils.angleDifference(steady, yaw);
    }

    public Verdict judge(double chunkYaw, double maxDeviation, boolean ignoreBehind, double behindAngle) {
        double a = Math.abs(offset(chunkYaw));
        if (a > maxDeviation) return Verdict.OUTSIDE;
        if (ignoreBehind && a > behindAngle) return Verdict.BEHIND;
        if (a <= 90.0) return Verdict.AHEAD;
        return Verdict.SIDE;
    }

    public static double weight(double offsetAbs, double strength, double distance) {
        if (offsetAbs > 90.0) return 0.0;
        if (strength <= 0.01) return 1.0;
        return (1.0 - strength + strength * (1.0 - offsetAbs / 180.0)) * (0.5 + 0.5 / (1.0 + distance / 256.0));
    }
}
