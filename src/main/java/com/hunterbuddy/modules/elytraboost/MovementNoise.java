package com.hunterbuddy.modules.elytraboost;

import net.minecraft.util.math.Vec3d;
import java.util.Random;
/**
 * Utility for injecting subtle noise into elytra movement vectors.
 *
 * <p>Adds Gaussian-distributed perturbations to both the <b>magnitude</b>
 * (speed) and <b>direction</b> (yaw + pitch) of a movement vector, making
 * the flight pattern less mechanically perfect and harder to distinguish
 * from a human player.
 *
 * <p>All noise values are drawn from {@link Random#nextGaussian()}, which
 * produces a normal distribution (mean=0, σ=1). The sigma parameters
 * control how wide the distribution is:
 * <ul>
 *   <li>{@code speedSigma = 0.02} → 68% of ticks deviate ≤2% from target speed</li>
 *   <li>{@code directionSigmaDeg = 0.5} → 68% of ticks deviate ≤0.5° from target direction</li>
 * </ul>
 */
public final class MovementNoise {
    private MovementNoise() {}   // utility class
    /**
     * Apply Gaussian noise to a movement vector.
     *
     * @param movement          the original movement vector (direction × speed)
     * @param speedSigma        standard deviation for speed noise, as a fraction
     *                          of the current speed (e.g. 0.02 = ±2%)
     * @param directionSigmaDeg standard deviation for directional noise, in
     *                          degrees (applied to both yaw and pitch)
     * @param rng               the random number generator (reuse across ticks)
     * @return a new vector with noise applied, or the original if both sigmas are ≤0
     */
    public static Vec3d applyNoise(Vec3d movement, double speedSigma,
                                    double directionSigmaDeg, Random rng) {
        double speed = movement.length();
        if (speed < 1e-6) return movement;   // don't perturb zero vectors
        // ---- Speed noise: scale the magnitude ----
        double noisySpeed = speed;
        if (speedSigma > 0) {
            double factor = 1.0 + rng.nextGaussian() * speedSigma;
            // Clamp to avoid negative or absurdly large values (>3σ events)
            factor = Math.max(0.9, Math.min(1.1, factor));
            noisySpeed = speed * factor;
        }
        // ---- Directional noise: perturb yaw and pitch ----
        if (directionSigmaDeg > 0) {
            // Decompose the movement vector into yaw + pitch + magnitude
            double horizLength = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
            double yaw = Math.atan2(-movement.x, movement.z);   // MC yaw convention
            double pitch = Math.atan2(-movement.y, horizLength); // MC pitch convention
            // Add noise
            yaw   += Math.toRadians(rng.nextGaussian() * directionSigmaDeg);
            pitch += Math.toRadians(rng.nextGaussian() * directionSigmaDeg);
            // Clamp pitch to avoid flipping over the poles
            pitch = Math.max(-Math.PI / 2 + 0.01, Math.min(Math.PI / 2 - 0.01, pitch));
            // Rebuild the vector from noisy yaw/pitch/speed
            double cosPitch = Math.cos(pitch);
            return new Vec3d(
                -Math.sin(yaw) * cosPitch * noisySpeed,
                -Math.sin(pitch) * noisySpeed,
                 Math.cos(yaw) * cosPitch * noisySpeed
            );
        }
        // Speed noise only (no direction change): just rescale
        return movement.multiply(noisySpeed / speed);
    }
}
