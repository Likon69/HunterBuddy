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
}
