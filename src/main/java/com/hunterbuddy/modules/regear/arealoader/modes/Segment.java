package com.hunterbuddy.modules.regear.arealoader.modes;

record Segment(double startX, double startZ, double endX, double endZ) {
    double fractionNearest(double x, double z) {
        double dx = endX - startX;
        double dz = endZ - startZ;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared == 0.0) return 0.0;
        double fraction = ((x - startX) * dx + (z - startZ) * dz) / lengthSquared;
        return Math.max(0.0, Math.min(1.0, fraction));
    }

    double[] pointAt(double fraction) {
        return new double[]{startX + (endX - startX) * fraction, startZ + (endZ - startZ) * fraction};
    }

    double distanceTo(double x, double z) {
        double[] nearest = pointAt(fractionNearest(x, z));
        return Math.hypot(x - nearest[0], z - nearest[1]);
    }
}
