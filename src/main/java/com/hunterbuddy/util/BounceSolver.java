package com.hunterbuddy.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Ported from the NEW-SRC drop of BepHax, mechanically — package, Yarn
 * mappings ({@code Mth} to {@code MathHelper}, {@code AABB} to {@code Box},
 * {@code Level.noCollision} to {@code World.isSpaceEmpty}, {@code Player} to
 * {@code PlayerEntity}, {@code box.move} to {@code box.offset}) — with the
 * logic and every constant left exactly as written there: a vanilla elytra
 * bounce, simulated tick by tick, glide clearing three ticks after the last
 * ground contact, to find whichever pitch keeps it in the air longest under
 * a given ceiling. That three-tick clear is BepHax's pre-v0.5.2 model, from
 * before their glide-clear-timing rebuild; see {@code ElytraBounce}'s own
 * doc for where our copy of this solver diverges from it once that rebuild
 * lands here too.
 */
public final class BounceSolver {
    private static final double GRAVITY = 0.08;
    private static final float JUMP_POWER = 0.42F;
    public static final double GLIDE_HEIGHT = 0.6;
    private static final int GLIDE_PERSIST_TICKS = 3;
    private static final int SIM_TICKS = 600;
    private static final float MIN_PITCH = 25.0F;
    private static final float MAX_PITCH = 89.0F;
    private static final double MAX_USEFUL_HEADROOM = 2.0;

    private BounceSolver() {
    }

    public static double measureHeadroom(PlayerEntity player, World world) {
        Box box = new Box(
            player.getX() - 0.3,
            player.getY(),
            player.getZ() - 0.3,
            player.getX() + 0.3,
            player.getY() + 0.6,
            player.getZ() + 0.3
        );
        double free = 0.0;

        for (double rise = 0.05; rise <= 2.0 && world.isSpaceEmpty(box.offset(0.0, rise, 0.0)); rise += 0.05) {
            free = rise;
        }

        return free;
    }

    /**
     * {@code clearHeld} models ElytraBounce's {@code hold-glide-clear}: with
     * it, the server's glide clear never actually reaches the client mid-hop
     * any more — it's held until the tick the next takeoff departs anyway,
     * so the client's own glide flag never toggles off in between. Without
     * it (the pre-rebuild regime this solver was written for), the flag
     * clears itself here 3 ticks after the last ground contact, same as the
     * reference always modelled. Added rather than assumed true, since
     * {@code hold-glide-clear} is itself a setting a bounce can disable.
     */
    public static double simulateSpeed(float pitch, double headroom, boolean clearHeld) {
        double vx = 0.0;
        double vy = 0.0;
        double vz = 0.0;
        double y = 0.0;
        boolean onGround = true;
        boolean gliding = false;
        boolean prevJump = false;
        int noJumpDelay = 0;
        int sinceGround = Integer.MAX_VALUE;

        for (int t = 0; t < 600; t++) {
            if (noJumpDelay > 0) {
                noJumpDelay--;
            }

            boolean jump = onGround || !gliding && !prevJump;
            if (jump && !prevJump && !onGround && !gliding) {
                gliding = true;
            }

            prevJump = jump;
            if (jump) {
                if (onGround && noJumpDelay == 0) {
                    vy = Math.max(0.42F, vy);
                    vz += 0.2;
                    noJumpDelay = 10;
                }
            } else {
                noJumpDelay = 0;
            }

            if (gliding) {
                double f = pitch * (float) (Math.PI / 180.0);
                double lookZ = MathHelper.cos(f);
                double d = Math.abs(lookZ);
                double e = Math.sqrt(vx * vx + vz * vz);
                double h = MathHelper.square(Math.cos(f));
                vy += 0.08 * (-1.0 + h * 0.75);
                if (vy < 0.0 && d > 0.0) {
                    double i = vy * -0.1 * h;
                    vz += lookZ * i / d;
                    vy += i;
                }

                if (d > 0.0) {
                    vz += (lookZ / d * e - vz) * 0.1;
                }

                vx *= 0.99F;
                vy *= 0.98F;
                vz *= 0.99F;
            } else {
                double fric = onGround ? 0.546 : 0.91;
                vx *= fric;
                vz *= fric;
                vy = (vy - 0.08) * 0.98;
            }

            double ceilingH = headroom + 0.6;
            double hitbox = !gliding && !(ceilingH < 1.8) ? 1.8 : 0.6;
            double free = headroom >= 2.0 ? Double.MAX_VALUE : ceilingH - hitbox;
            double ny = y + vy;
            onGround = false;
            if (ny <= 0.0) {
                ny = 0.0;
                vy = 0.0;
                onGround = true;
            } else if (ny >= free) {
                ny = free;
                vy = 0.0;
            }

            y = ny;
            if (onGround) {
                sinceGround = 0;
            } else if (sinceGround != Integer.MAX_VALUE) {
                sinceGround++;
            }

            if (!clearHeld && gliding && sinceGround >= 3 && sinceGround != Integer.MAX_VALUE) {
                gliding = false;
                sinceGround = Integer.MAX_VALUE;
            }
        }

        return Math.sqrt(vx * vx + vz * vz) * 20.0;
    }

    public static float solvePitch(double headroom, boolean clearHeld) {
        float best = 89.0F;
        double bestSpeed = -1.0;

        for (float p = 25.0F; p <= 89.0F; p++) {
            double s = simulateSpeed(p, headroom, clearHeld);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = p;
            }
        }

        return best;
    }
}
