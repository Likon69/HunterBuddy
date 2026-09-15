package com.hunterbuddy.util;

import net.minecraft.util.math.Vec3d;

/**
 * Where {@code ElytraWingTipCaptureMixin} leaves the local player's real wingtip positions for
 * {@link com.hunterbuddy.modules.FlightTrail} to read - the only thing that passes between them.
 */
public final class WingTipTracker {
    /**
     * The far corner of the wing plate in the wing part's own local model space, in blocks - a little past
     * its actual edge so the trail starts just outside the mesh rather than inside it. Read off the vanilla
     * elytra model's cuboid ({@code ElytraEntityModel.getTexturedModelData()}: a 10x20x2 (in 1/16-block
     * units) plate per wing).
     */
    public static final Vec3d LEFT_LOCAL_TIP = new Vec3d(-11.0 / 16.0, 21.0 / 16.0, 3.0 / 16.0);
    public static final Vec3d RIGHT_LOCAL_TIP = new Vec3d(11.0 / 16.0, 21.0 / 16.0, 3.0 / 16.0);

    /**
     * How long a capture stays trusted before {@link com.hunterbuddy.modules.FlightTrail} falls back to its
     * own estimate. First-person view never produces a capture at all - the game does not submit the local
     * player's own model for rendering there - so this is also what makes the fallback take over cleanly in
     * that case.
     */
    public static final long FRESH_MS = 150L;

    public static volatile Vec3d leftWorld;
    public static volatile Vec3d rightWorld;
    public static volatile Vec3d leftBody;
    public static volatile Vec3d rightBody;
    private static volatile long leftCapturedAt;
    private static volatile long rightCapturedAt;

    private WingTipTracker() {}

    public static void captureLeft(Vec3d world, long now) {
        leftWorld = world;
        leftCapturedAt = now;
    }

    public static void captureRight(Vec3d world, long now) {
        rightWorld = world;
        rightCapturedAt = now;
    }

    public static boolean leftFresh(long now) {
        return leftWorld != null && now - leftCapturedAt < FRESH_MS;
    }

    public static boolean rightFresh(long now) {
        return rightWorld != null && now - rightCapturedAt < FRESH_MS;
    }

    public static void captureBody(Vec3d left, Vec3d right) {
        leftBody = left;
        rightBody = right;
    }

    public static float glidingProgress(float glidingTicks) {
        return Math.max(0.0F, Math.min(1.0F, glidingTicks * glidingTicks / 100.0F));
    }

    public static float glideAngle(float glidingProgress, float pitch) {
        return glidingProgress * (-90.0F - pitch);
    }

    public static Vec3d toBody(Vec3d offset, float bodyYaw, float glideAngle) {
        return rotateX(rotateY(offset, -(180.0 - bodyYaw)), -glideAngle);
    }

    public static Vec3d toWorld(Vec3d body, float bodyYaw, float glideAngle) {
        return rotateY(rotateX(body, glideAngle), 180.0 - bodyYaw);
    }

    private static Vec3d rotateX(Vec3d v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3d(v.x, cos * v.y - sin * v.z, sin * v.y + cos * v.z);
    }

    private static Vec3d rotateY(Vec3d v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3d(cos * v.x + sin * v.z, v.y, -sin * v.x + cos * v.z);
    }
}
