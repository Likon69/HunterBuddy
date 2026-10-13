package com.hunterbuddy.behavior.farmer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class FarmerZone {
    private final List<BlockPos> hull;

    private FarmerZone(List<BlockPos> hull) {
        this.hull = hull;
    }

    public static FarmerZone build(List<BlockPos> corners) {
        if (corners.size() != 4) throw new IllegalArgumentException("a zone needs exactly 4 corners, found " + corners.size());
        List<BlockPos> hull = convexHull(corners);
        if (hull.size() < 3) throw new IllegalArgumentException("the 4 corners are aligned");
        return new FarmerZone(hull);
    }

    public List<BlockPos> corners() {
        return hull;
    }

    public static FarmerZone restore(List<BlockPos> corners) {
        List<BlockPos> points = new ArrayList<>(corners);
        if (points.size() < 3 || points.size() > 4) throw new IllegalStateException("invalid recorded zone");
        while (points.size() < 4) points.add(points.getFirst());
        return build(points);
    }

    public boolean sameFootprint(FarmerZone other) {
        if (other == null || hull.size() != other.hull.size()) return false;
        for (int i = 0; i < hull.size(); i++) {
            BlockPos a = hull.get(i);
            BlockPos b = other.hull.get(i);
            if (a.getX() != b.getX() || a.getZ() != b.getZ()) return false;
        }
        return true;
    }

    public double area() {
        double sum = 0;
        for (int i = 0; i < hull.size(); i++) {
            BlockPos a = hull.get(i);
            BlockPos b = hull.get((i + 1) % hull.size());
            sum += (double) a.getX() * b.getZ() - (double) b.getX() * a.getZ();
        }
        return Math.abs(sum) / 2;
    }

    public boolean contains(double x, double z) {
        return containsWithMargin(x, z, 0);
    }

    public boolean containsWithMargin(double x, double z, double margin) {
        int n = hull.size();
        for (int i = 0; i < n; i++) {
            BlockPos a = hull.get(i);
            BlockPos b = hull.get((i + 1) % n);
            double edgeX = b.getX() - a.getX();
            double edgeZ = b.getZ() - a.getZ();
            double length = Math.hypot(edgeX, edgeZ);
            if (length == 0) continue;
            double cross = (edgeX * (z - a.getZ()) - edgeZ * (x - a.getX())) / length;
            if (cross < margin) return false;
        }
        return true;
    }

    private static List<BlockPos> convexHull(List<BlockPos> points) {
        List<BlockPos> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> a.getX() != b.getX() ? Integer.compare(a.getX(), b.getX()) : Integer.compare(a.getZ(), b.getZ()));

        List<BlockPos> lower = new ArrayList<>();
        for (BlockPos point : sorted) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= 0) {
                lower.remove(lower.size() - 1);
            }
            lower.add(point);
        }

        List<BlockPos> upper = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            BlockPos point = sorted.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= 0) {
                upper.remove(upper.size() - 1);
            }
            upper.add(point);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return List.copyOf(lower);
    }

    private static long cross(BlockPos o, BlockPos a, BlockPos b) {
        return (long) (a.getX() - o.getX()) * (b.getZ() - o.getZ()) - (long) (a.getZ() - o.getZ()) * (b.getX() - o.getX());
    }
}
