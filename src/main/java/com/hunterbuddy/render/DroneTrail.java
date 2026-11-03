package com.hunterbuddy.render;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Predicate;
import net.minecraft.util.math.Vec3d;

public final class DroneTrail {
    private static final double MIN_STEP = 0.3;
    private static final double MAX_LENGTH = 12.0;
    private static final int MIN_POINTS = 2;

    private final ArrayDeque<Vec3d> points = new ArrayDeque<>();

    public void clear() {
        points.clear();
    }

    public boolean usable() {
        return points.size() >= MIN_POINTS;
    }

    public void record(Vec3d focus) {
        if (!points.isEmpty() && points.peekLast().distanceTo(focus) <= MIN_STEP) return;

        points.addLast(focus);
        while (points.size() > MIN_POINTS && length() > MAX_LENGTH) points.removeFirst();
    }

    public Vec3d farthest(Vec3d focus) {
        Vec3d best = focus;
        for (Vec3d point : points) {
            if (point.squaredDistanceTo(focus) > best.squaredDistanceTo(focus)) best = point;
        }
        return best;
    }

    public Vec3d atLeast(Vec3d focus, double straightDistance, Predicate<Vec3d> accepted) {
        Iterator<Vec3d> newestFirst = points.descendingIterator();

        while (newestFirst.hasNext()) {
            Vec3d point = newestFirst.next();
            if (point.distanceTo(focus) >= straightDistance && accepted.test(point)) return point;
        }

        return null;
    }

    private double length() {
        double total = 0.0;
        Vec3d previous = null;
        for (Vec3d point : points) {
            if (previous != null) total += previous.distanceTo(point);
            previous = point;
        }
        return total;
    }
}
