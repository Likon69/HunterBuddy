package com.hunterbuddy.render;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class DroneClimb {
    private static final double SIDE_REACH = 5.0;
    private static final double HEIGHT = 1.5;
    private static final double HORIZONTAL_TAU = 1.2;
    private static final double VERTICAL_TAU = 0.4;
    private static final double MAX_HORIZONTAL_LAG = 1.5;
    private static final double MAX_VERTICAL_LAG = 2.0;
    private static final double ENTRY_SECONDS = 3.0;
    private static final List<Vec3d> AXES = List.of(
        new Vec3d(1.0, 0.0, 0.0), new Vec3d(0.0, 0.0, 1.0), new Vec3d(-1.0, 0.0, 0.0), new Vec3d(0.0, 0.0, -1.0)
    );

    private Vec3d pivot = Vec3d.ZERO;
    private Vec3d axis = AXES.get(0);
    private Vec3d remembered;
    private Vec3d entry = Vec3d.ZERO;
    private double age;

    public void reset() {
        remembered = null;
        entry = Vec3d.ZERO;
        age = 0.0;
    }

    public void begin(World world, Entity entity, Vec3d focus, Vec3d cameraPosition) {
        pivot = focus;
        axis = openestAxis(world, entity, focus);
        remembered = axis;
        age = 0.0;
        entry = cameraPosition == null ? Vec3d.ZERO : cameraPosition.subtract(place());
    }

    public Vec3d pivot() {
        return pivot;
    }

    public void follow(Vec3d focus, double dt) {
        double horizontal = DroneEasing.follow(dt, HORIZONTAL_TAU);
        double vertical = DroneEasing.follow(dt, VERTICAL_TAU);
        double x = pivot.x + (focus.x - pivot.x) * horizontal;
        double y = pivot.y + (focus.y - pivot.y) * vertical;
        double z = pivot.z + (focus.z - pivot.z) * horizontal;
        pivot = new Vec3d(
            limited(x, focus.x, MAX_HORIZONTAL_LAG),
            limited(y, focus.y, MAX_VERTICAL_LAG),
            limited(z, focus.z, MAX_HORIZONTAL_LAG)
        );
    }

    public Vec3d anchor(double dt) {
        age += dt;
        double remaining = 1.0 - DroneEasing.smootherstep(age / ENTRY_SECONDS);
        return place().add(entry.multiply(remaining));
    }

    private Vec3d place() {
        return pivot.add(axis.multiply(SIDE_REACH)).add(0.0, HEIGHT, 0.0);
    }

    private Vec3d openestAxis(World world, Entity entity, Vec3d focus) {
        Vec3d best = null;
        double bestReach = -1.0;

        for (Vec3d candidate : candidates()) {
            Vec3d target = focus.add(candidate.multiply(SIDE_REACH)).add(0.0, HEIGHT, 0.0);
            double hit = DroneTerrain.hitDistance(world, entity, focus, target);
            double reach = hit < 0.0 ? focus.distanceTo(target) : hit;
            if (reach > bestReach) {
                best = candidate;
                bestReach = reach;
            }
        }

        return best;
    }

    private List<Vec3d> candidates() {
        int first = remembered == null ? 0 : AXES.indexOf(remembered);
        return List.of(AXES.get(first), AXES.get((first + 1) % 4), AXES.get((first + 2) % 4), AXES.get((first + 3) % 4));
    }

    private static double limited(double value, double around, double lag) {
        return Math.max(around - lag, Math.min(around + lag, value));
    }
}
