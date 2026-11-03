package com.hunterbuddy.modules.regear.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class AimMath {
    private static final float HALF_TURN = 180.0f;

    private AimMath() {
    }

    static float yawTo(Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return 0.0f;
        return rotationsBetween(client.player.getEyePos(), target)[0];
    }

    static float pitchTo(Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return 0.0f;
        return rotationsBetween(client.player.getEyePos(), target)[1];
    }

    static float[] rotationsBetween(Vec3d source, Vec3d destination) {
        double dx = destination.x - source.x;
        double dy = destination.y - source.y;
        double dz = destination.z - source.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch)};
    }

    static float[] rotationsToEntity(Entity entity, RotationUtils.HitVector hit) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return new float[]{0.0f, 0.0f};
        return rotationsBetween(client.player.getEyePos(), hitPoint(entity, hit));
    }

    static Vec3d hitPoint(Entity entity, RotationUtils.HitVector hit) {
        Vec3d feet = entity.getEntityPos();
        Vec3d torso = feet.add(0.0, entity.getHeight() / 2.0, 0.0);
        Vec3d eyes = entity.getEyePos();
        return switch (hit) {
            case FEET -> feet;
            case TORSO -> torso;
            case EYES -> eyes;
            case CLOSEST -> closestToViewer(feet, torso, eyes);
        };
    }

    private static Vec3d closestToViewer(Vec3d feet, Vec3d torso, Vec3d eyes) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return feet;
        Vec3d view = client.player.getEyePos();
        Vec3d best = feet;
        if (torso.squaredDistanceTo(view) < best.squaredDistanceTo(view)) {
            best = torso;
        }
        return eyes.squaredDistanceTo(view) < best.squaredDistanceTo(view) ? eyes : best;
    }

    static Vec3d lookVector(float pitch, float yaw) {
        float pitchRad = pitch * MathHelper.RADIANS_PER_DEGREE;
        float yawRad = -yaw * MathHelper.RADIANS_PER_DEGREE;
        float horizontal = MathHelper.cos(pitchRad);
        return new Vec3d(MathHelper.sin(yawRad) * horizontal, -MathHelper.sin(pitchRad), MathHelper.cos(yawRad) * horizontal);
    }

    static boolean isWithinFov(Vec3d from, Vec3d to, float fov) {
        if (fov >= HALF_TURN) return true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        float toTarget = rotationsBetween(from, to)[0];
        return Math.abs(MathHelper.wrapDegrees(client.player.getYaw() - toTarget)) <= fov;
    }

    static double angleDifference(float a, float b) {
        return (((a - b) % 360.0 + 540.0) % 360.0) - HALF_TURN;
    }
}
