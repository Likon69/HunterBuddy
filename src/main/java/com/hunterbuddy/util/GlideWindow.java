package com.hunterbuddy.util;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class GlideWindow {
    public static final double ANTI_TICK_SKIPPING = 0.05;

    private GlideWindow() {
    }

    public static Vec3d applyGlide(Vec3d velocity, Vec3d look, float pitch, double gravity) {
        float pitchRad = pitch * 0.017453292F;
        double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        double speedHorizontal = velocity.horizontalLength();
        double cosSquared = MathHelper.square(Math.cos(pitchRad));

        double x = velocity.x;
        double y = velocity.y + gravity * (-1.0 + cosSquared * 0.75);
        double z = velocity.z;

        if (y < 0.0 && lookHorizontal > 0.0) {
            double fall = y * -0.1 * cosSquared;
            x += look.x * fall / lookHorizontal;
            y += fall;
            z += look.z * fall / lookHorizontal;
        }

        if (pitchRad < 0.0F && lookHorizontal > 0.0) {
            double climb = speedHorizontal * -MathHelper.sin(pitchRad) * 0.04;
            x += -look.x * climb / lookHorizontal;
            y += climb * 3.2;
            z += -look.z * climb / lookHorizontal;
        }

        if (lookHorizontal > 0.0) {
            x += (look.x / lookHorizontal * speedHorizontal - x) * 0.1;
            z += (look.z / lookHorizontal * speedHorizontal - z) * 0.1;
        }

        return new Vec3d(x * 0.99, y * 0.98, z * 0.99);
    }

    public static void bounds(Vec3d previous, Vec3d look, Vec3d pairLook, float pitch, double gravity, double scale, double[] lo, double[] hi) {
        Vec3d predicted = applyGlide(previous, look, pitch, gravity);

        for (int axis = 0; axis < 3; axis++) {
            double dir = axisOf(look, axis);
            double pair = axisOf(pairLook, axis);
            double boxMin = Math.max(
                -scale,
                (Math.min(-ANTI_TICK_SKIPPING, dir) + Math.min(-ANTI_TICK_SKIPPING, pair)) * scale
            );
            double boxMax = Math.min(
                scale,
                (Math.max(ANTI_TICK_SKIPPING, dir) + Math.max(ANTI_TICK_SKIPPING, pair)) * scale
            );
            double had = axisOf(previous, axis);
            double centre = axisOf(predicted, axis);

            lo[axis] = centre + Math.min(0.0, boxMin - had);
            hi[axis] = centre + Math.max(0.0, boxMax - had);
        }
    }

    public static Vec3d ride(double[] lo, double[] hi, Vec3d look, float pitchDeg, double toleranceDeg, double cap) {
        if (!Double.isFinite(cap) || cap <= 0.0) return null;

        double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
        if (hLen < 1e-9) return null;
        double hx = look.x / hLen;
        double hz = look.z / hLen;

        double horizontal = Double.POSITIVE_INFINITY;
        boolean limited = false;

        if (hx > ANTI_TICK_SKIPPING) {
            horizontal = Math.min(horizontal, hi[0] / hx);
            limited = true;
        } else if (hx < -ANTI_TICK_SKIPPING) {
            horizontal = Math.min(horizontal, lo[0] / hx);
            limited = true;
        }

        if (hz > ANTI_TICK_SKIPPING) {
            horizontal = Math.min(horizontal, hi[2] / hz);
            limited = true;
        } else if (hz < -ANTI_TICK_SKIPPING) {
            horizontal = Math.min(horizontal, lo[2] / hz);
            limited = true;
        }

        if (!limited || !(horizontal > 0.0)) return null;

        double vertical;
        if (look.y < -ANTI_TICK_SKIPPING) {
            vertical = lo[1];
        } else if (look.y > ANTI_TICK_SKIPPING) {
            vertical = hi[1];
        } else {
            vertical = Math.max(lo[1], Math.min(hi[1], 0.0));
        }

        double cornerPitch = Math.toDegrees(-Math.atan2(vertical, horizontal));
        double ridePitch = pitchDeg + Math.max(-toleranceDeg, Math.min(toleranceDeg, cornerPitch - pitchDeg));
        ridePitch = Math.max(-89.9, Math.min(89.9, ridePitch));

        double rad = Math.toRadians(ridePitch);
        double cos = Math.cos(rad);
        double[] dir = {hx * cos, -Math.sin(rad), hz * cos};

        double reach = Double.POSITIVE_INFINITY;
        boolean any = false;

        for (int axis = 0; axis < 3; axis++) {
            double d = dir[axis];
            if (Math.abs(d) <= ANTI_TICK_SKIPPING) continue;

            double t;
            if (d > 0.0) {
                t = hi[axis] <= 0.0 ? 0.0 : hi[axis] / d;
            } else {
                t = lo[axis] >= 0.0 ? 0.0 : lo[axis] / d;
            }

            reach = Math.min(reach, t);
            any = true;
        }

        if (!any || reach <= 1e-9) return null;
        reach = Math.min(reach, cap);

        double vx = Math.max(lo[0], Math.min(hi[0], dir[0] * reach));
        double vy = Math.max(lo[1], Math.min(hi[1], dir[1] * reach));
        double vz = Math.max(lo[2], Math.min(hi[2], dir[2] * reach));

        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (length > cap * (1.0 + 1e-9)) return null;

        return new Vec3d(vx, vy, vz);
    }

    private static double axisOf(Vec3d vector, int axis) {
        return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z;
    }
}
