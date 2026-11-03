package com.hunterbuddy.render;

import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class DroneClimbDetector {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double RISE_TAU = 0.3;
    private static final double ENTER_RATE = 1.2;
    private static final double KEEP_RATE = 0.4;
    private static final double GLIDE_ENTER_RATE = 4.0;
    private static final double GLIDE_KEEP_RATE = 2.0;
    private static final double ENTER_SECONDS = 0.4;
    private static final double RELEASE_SECONDS = 1.5;
    private static final double GLIDE_RELEASE_SECONDS = 0.4;
    private static final double WALL_REACH = 2.5;
    private static final int SHAFT_WALLS = 3;
    private static final List<Vec3d> WALLS = List.of(
        new Vec3d(1.0, 0.0, 0.0), new Vec3d(-1.0, 0.0, 0.0), new Vec3d(0.0, 0.0, 1.0), new Vec3d(0.0, 0.0, -1.0)
    );

    private double rise;
    private double enterSeconds;
    private double releaseSeconds;
    private boolean active;

    public void reset() {
        rise = 0.0;
        enterSeconds = 0.0;
        releaseSeconds = 0.0;
        active = false;
    }

    public boolean update(World world, LivingEntity player, Vec3d focus, DroneScene scene, double dt) {
        double measured = (player.getY() - player.lastY) * TICKS_PER_SECOND;
        rise += (measured - rise) * DroneEasing.follow(dt, RISE_TAU);

        boolean gliding = player.isGliding();
        boolean raw = gliding ? glidingInShaft(world, player, focus, scene) : onFootOrSwimming(player, scene);

        if (raw) {
            releaseSeconds = 0.0;
            enterSeconds += dt;
            if (enterSeconds >= ENTER_SECONDS) active = true;
        } else {
            enterSeconds = 0.0;
            releaseSeconds += dt;
            if (releaseSeconds >= (gliding ? GLIDE_RELEASE_SECONDS : RELEASE_SECONDS)) active = false;
        }

        return active;
    }

    private boolean onFootOrSwimming(LivingEntity player, DroneScene scene) {
        boolean water = player.isTouchingWater() || player.isSubmergedInWater();
        return scene == DroneScene.EXIT || (water && rise > (active ? KEEP_RATE : ENTER_RATE));
    }

    private boolean glidingInShaft(World world, LivingEntity player, Vec3d focus, DroneScene scene) {
        if (scene != DroneScene.EXIT && scene != DroneScene.TAKEOFF) return false;
        if (rise <= (active ? GLIDE_KEEP_RATE : GLIDE_ENTER_RATE)) return false;

        int walls = 0;
        for (Vec3d way : WALLS) {
            if (DroneTerrain.hitDistance(world, player, focus, focus.add(way.multiply(WALL_REACH))) >= 0.0) walls++;
        }
        return walls >= SHAFT_WALLS;
    }
}
