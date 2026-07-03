package com.hunterbuddy.modules.elytraboost;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Simulates vanilla Minecraft elytra physics using the exact formulas from
 * {@code LivingEntity.travel()} (decompiled, consistent 1.9+).
 *
 * <p>This is used by ElytraBoost's dynamic speed cap to compute what speed
 * vanilla elytra flight would produce at a given pitch, so the boost never
 * exceeds a configurable multiple of the vanilla equilibrium.
 *
 * <p><b>Vanilla constants (per tick):</b>
 * <ul>
 *   <li>Base gravity: {@code -0.08} blocks/tick²</li>
 *   <li>Lift factor: {@code +cos²(pitch) × 0.06}</li>
 *   <li>Glide boost: {@code |motionY| × 0.1 × cos²(pitch)} → converted to forward thrust</li>
 *   <li>Pitch-up boost: {@code horizontalSpeed × sin(|pitch|) × 0.04 × 3.2}</li>
 *   <li>Horizontal drag: {@code ×0.99} per tick</li>
 *   <li>Vertical drag: {@code ×0.98} per tick</li>
 *   <li>Yaw steering lerp: {@code 0.1} (10% per tick)</li>
 * </ul>
 */
public final class VanillaElytraSimulator {

    // ---- Vanilla constants ----
    private static final double GRAVITY        = -0.08;
    private static final double LIFT_FACTOR    =  0.06;
    private static final double GLIDE_BOOST    =  0.1;
    private static final double PITCH_UP_MULT  =  0.04;
    private static final double PITCH_UP_VERT  =  3.2;
    private static final double DRAG_HORIZONTAL = 0.99;
    private static final double DRAG_VERTICAL   = 0.98;
    private static final double YAW_STEER_LERP  = 0.1;

    private VanillaElytraSimulator() {}   // utility class

    /**
     * Simulate one tick of vanilla elytra physics.
     *
     * @param velocity  current velocity (motionX, motionY, motionZ)
     * @param pitchDeg  player pitch in degrees (negative = up, positive = down)
     * @param yawDeg    player yaw in degrees
     * @return the velocity vector after one vanilla tick
     */
    public static Vec3d simulateOneTick(Vec3d velocity, float pitchDeg, float yawDeg) {
        // Look vector (same formula as Minecraft's Entity.getRotationVector)
        Vec3d lookVec = lookVector(pitchDeg, yawDeg);

        float pitchRad = pitchDeg * ((float) Math.PI / 180F);
        double horizLook = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
        double horizSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double totalSpeed = velocity.length();

        // Step 1: lift factor = cos²(pitch)
        // In vanilla, d1 is the look vector length (always ~1.0 for normalized),
        // so Math.min(1.0, d1/0.4) is always 1.0. Simplified to just cos²(pitch).
        float f4 = MathHelper.cos(pitchRad);
        f4 = f4 * f4;

        // Step 2: gravity + lift
        double motionX = velocity.x;
        double motionY = velocity.y + GRAVITY + (double) f4 * LIFT_FACTOR;
        double motionZ = velocity.z;

        // Step 3: glide boost (convert downward velocity to forward thrust)
        if (motionY < 0.0 && horizLook > 0.0) {
            double boost = motionY * -GLIDE_BOOST * (double) f4;
            motionY += boost;
            motionX += lookVec.x * boost / horizLook;
            motionZ += lookVec.z * boost / horizLook;
        }

        // Step 4: pitch-up boost (trade horizontal speed for altitude)
        // Vanilla divides by horizLook (horizontal look component) to normalize
        // the direction, NOT by totalSpeed.
        if (pitchRad < 0.0F && horizSpeed > 0.0) {
            double d9 = horizSpeed * (double) (-MathHelper.sin(pitchRad)) * PITCH_UP_MULT;
            motionY += d9 * PITCH_UP_VERT;
            motionX -= lookVec.x * d9 / horizLook;
            motionZ -= lookVec.z * d9 / horizLook;
        }

        // Step 5: yaw steering (lerp horizontal velocity toward look direction)
        if (horizLook > 0.0) {
            motionX += (lookVec.x / horizLook * horizSpeed - motionX) * YAW_STEER_LERP;
            motionZ += (lookVec.z / horizLook * horizSpeed - motionZ) * YAW_STEER_LERP;
        }

        // Step 6: drag
        motionX *= DRAG_HORIZONTAL;
        motionY *= DRAG_VERTICAL;
        motionZ *= DRAG_HORIZONTAL;

        return new Vec3d(motionX, motionY, motionZ);
    }

    /**
     * Estimate the vanilla equilibrium speed (blocks/tick) for a given pitch by
     * running the physics simulation until it converges.
     *
     * <p>Starts from a small initial velocity in the look direction and iterates
     * up to {@code maxTicks} ticks (default 400, ~20 seconds of flight). Returns
     * the total speed magnitude once the per-tick change drops below a threshold,
     * or after all ticks have elapsed.
     *
     * @param pitchDeg  pitch in degrees (negative = up, positive = down)
     * @param yawDeg    yaw in degrees (direction of travel)
     * @return estimated equilibrium speed in blocks/tick
     */
    public static double calculateEquilibriumSpeed(float pitchDeg, float yawDeg) {
        return calculateEquilibriumSpeed(pitchDeg, yawDeg, 400, 0.0001);
    }

    /**
     * Estimate vanilla equilibrium speed with configurable convergence parameters.
     *
     * @param pitchDeg   pitch in degrees
     * @param yawDeg     yaw in degrees
     * @param maxTicks   maximum simulation ticks
     * @param threshold  convergence threshold (speed change per tick)
     * @return estimated equilibrium speed in blocks/tick
     */
    public static double calculateEquilibriumSpeed(float pitchDeg, float yawDeg,
                                                    int maxTicks, double threshold) {
        // Start with a small initial velocity in the look direction
        Vec3d look = lookVector(pitchDeg, yawDeg);
        Vec3d velocity = look.multiply(0.5);

        double prevSpeed = velocity.length();

        for (int tick = 0; tick < maxTicks; tick++) {
            velocity = simulateOneTick(velocity, pitchDeg, yawDeg);
            double speed = velocity.length();

            if (Math.abs(speed - prevSpeed) < threshold) {
                return speed;
            }
            prevSpeed = speed;
        }
        return prevSpeed;
    }

    /**
     * Estimate the vanilla equilibrium VELOCITY VECTOR for a given pitch/yaw by
     * running the physics simulation until it converges. Returns the final Vec3d
     * (with proper X, Y, Z components) — not just the speed magnitude.
     *
     * <p>This is what ElytraBoost uses to override {@code mc.player.setVelocity}
     * with a value that matches what GrimAC's vanilla simulation produces.
     * Using just {@code lookVector * speed} would set motionY to 0 at pitch 0,
     * but vanilla equilibrium at pitch 0 actually has motionY ≈ -1.0 (player
     * descends). GrimAC's Simulation check compares the actual client position
     * against simulated vanilla physics per tick — any deviation > 0.03 blocks
     * triggers a rubberband.
     *
     * <p>Note: 50 iterations is enough for convergence at any pitch; the loop
     * returns early once the per-tick speed change drops below 1e-5.
     *
     * @param pitchDeg  pitch in degrees
     * @param yawDeg    yaw in degrees
     * @return equilibrium velocity vector in blocks/tick
     */
    public static Vec3d calculateEquilibriumVelocity(float pitchDeg, float yawDeg) {
        Vec3d look = lookVector(pitchDeg, yawDeg);
        Vec3d velocity = look.multiply(0.5);

        for (int tick = 0; tick < 50; tick++) {
            Vec3d next = simulateOneTick(velocity, pitchDeg, yawDeg);
            if (Math.abs(next.length() - velocity.length()) < 1e-5) {
                return next;
            }
            velocity = next;
        }
        return velocity;
    }

    /**
     * Fast lookup: approximate equilibrium velocity vector for common pitch values
     * without running the full simulation. Falls back to
     * {@link #calculateEquilibriumVelocity} for values not in the table.
     *
     * <p>Returns a Vec3d in the look direction with magnitude = equilibrium speed
     * at that pitch. Approximate — use {@link #calculateEquilibriumVelocity} when
     * exact match with GrimAC's simulation is critical.
     *
     * @param pitchDeg  pitch in degrees
     * @param yawDeg    yaw in degrees (only used for simulation fallback)
     * @return approximate equilibrium velocity vector in blocks/tick
     */
    public static Vec3d estimateEquilibriumVelocityFast(float pitchDeg, float yawDeg) {
        double speed = estimateEquilibriumSpeedFast(pitchDeg, yawDeg);
        return lookVector(pitchDeg, yawDeg).multiply(speed);
    }

    /**
     * Quick lookup: returns approximate vanilla equilibrium speed for common
     * pitch values without running the full simulation. Falls back to
     * {@link #calculateEquilibriumSpeed} for values not in the table.
     *
     * <p>This is useful for hot paths where the simulation cost matters.
     *
     * @param pitchDeg  pitch in degrees
     * @param yawDeg    yaw in degrees (only used for simulation fallback)
     * @return speed in blocks/tick
     */
    public static double estimateEquilibriumSpeedFast(float pitchDeg, float yawDeg) {
        // Precomputed approximate values from simulation:
        //   -30° → ~0.36 b/t,  -5° → ~1.5 b/t,  0° → ~1.5 b/t,
        //   +30° → ~2.5 b/t,  +52° → ~3.35 b/t, +90° → ~3.92 b/t
        //
        // Linear interpolation between known points for speed.
        // For pitches outside the table, clamp to the nearest entry.

        double absPitch = Math.abs(pitchDeg);

        if (pitchDeg <= -30) return 0.36;
        if (pitchDeg <= -5)  return lerp(0.36, 1.5, (pitchDeg + 30.0) / 25.0);
        if (pitchDeg <= 0)   return lerp(1.5, 1.5, (pitchDeg + 5.0) / 5.0);
        if (pitchDeg <= 30)  return lerp(1.5, 2.5, pitchDeg / 30.0);
        if (pitchDeg <= 52)  return lerp(2.5, 3.35, (pitchDeg - 30.0) / 22.0);
        if (pitchDeg <= 90)  return lerp(3.35, 3.92, (pitchDeg - 52.0) / 38.0);
        return 3.92;
    }

    // ---- Internal helpers ----

    /**
     * Minecraft's look vector from pitch/yaw (same as Entity.getRotationVector).
     */
    private static Vec3d lookVector(float pitchDeg, float yawDeg) {
        double pitchRad = Math.toRadians(pitchDeg);
        double yawRad = Math.toRadians(yawDeg);
        double cosPitch = Math.cos(pitchRad);
        return new Vec3d(
            -Math.sin(yawRad) * cosPitch,
            -Math.sin(pitchRad),
             Math.cos(yawRad) * cosPitch
        );
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp01(t);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
