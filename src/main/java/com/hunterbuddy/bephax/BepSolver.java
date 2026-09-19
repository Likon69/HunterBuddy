package com.hunterbuddy.bephax;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class BepSolver {
    private static final double GRAVITY = 0.08;
    private static final int SIM_TICKS = 1200;
    private static final int SEARCH_TICKS = 600;
    private static final float MIN_PITCH = 2.0F;
    private static final float MAX_PITCH = 70.0F;
    private static final double NO_CAP = Double.MAX_VALUE;
    private static final int SLOTS = 16;
    private static final double[] keyHeight = new double[16];
    private static final float[] keyYaw = new float[16];
    private static final double[] keyAlignment = new double[16];
    private static final double[] keyThreshold = new double[16];
    private static final boolean[] keyAsymmetric = new boolean[16];
    private static final boolean[] keyPumped = new boolean[16];
    private static final float[][] valLegs = new float[16][6];
    private static final double[] valSpeed = new double[16];
    private static int used = 0;
    private static int next = 0;
    private static double lastSpeed = 0.0;

    private BepSolver() {
    }

    public static float solvePitch(double height, float yaw, double alignmentDeg, double threshold) {
        return solveLegs(height, yaw, alignmentDeg, threshold, false, false)[0];
    }

    public static float[] solveLegs(double height, float yaw, double alignmentDeg, double threshold, boolean asymmetric) {
        return solveLegs(height, yaw, alignmentDeg, threshold, asymmetric, false);
    }

    /**
     * The leg plan for a swing: {dive steep, climb steep, dive flat, climb
     * flat, dive switch fraction, climb switch fraction}. Without
     * {@code pumped} the flat halves equal the steep ones and the fractions
     * are 1, which is the single angle per leg this used to return.
     *
     * <p>Pumped legs exist because a look angle does two incompatible jobs. A
     * steep one charges the vertical - the window hands back a fall to
     * convert, and on the way up the climb term turns horizontal into
     * vertical. A flat one spends it: the conversion term is worth
     * {@code cos squared} of the angle, so it pays most near level, and a
     * climb near level coasts on the vertical already bought instead of
     * paying the climb term again. One angle has to compromise; two do not.
     */
    public static float[] solveLegs(double height, float yaw, double alignmentDeg, double threshold, boolean asymmetric, boolean pumped) {
        float key = MathHelper.floorMod(yaw, 90.0F);

        for (int i = 0; i < used; i++) {
            if (Math.abs(height - keyHeight[i]) < 0.5
                && Math.abs(key - keyYaw[i]) < 5.625F
                && Math.abs(alignmentDeg - keyAlignment[i]) < 0.5
                && Math.abs(threshold - keyThreshold[i]) < 0.005
                && asymmetric == keyAsymmetric[i]
                && pumped == keyPumped[i]) {
                lastSpeed = valSpeed[i];
                return valLegs[i].clone();
            }
        }

        float centre = (MathHelper.floor(key / 11.25F) + 0.5F) * 11.25F;
        float best = 26.5F;
        double bestSpeed = -1.0;

        for (float p = MIN_PITCH; p <= MAX_PITCH; p += 4.0F) {
            double s = simulateSpeed(p, p, height, centre, alignmentDeg, threshold, SIM_TICKS);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = p;
            }
        }

        float lo = Math.max(MIN_PITCH, best - 4.0F);
        float hi = Math.min(MAX_PITCH, best + 4.0F);

        for (float p = lo; p <= hi; p += 0.5F) {
            double s = simulateSpeed(p, p, height, centre, alignmentDeg, threshold, SIM_TICKS);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = p;
            }
        }

        float[] legs = new float[]{best, best, best, best, 1.0F, 1.0F};

        if (asymmetric) {
            float[] pair = descend(best, height, centre, alignmentDeg, threshold);
            double score = simulateSpeed(pair[0], pair[1], height, centre, alignmentDeg, threshold, SIM_TICKS);
            if (score > bestSpeed) {
                bestSpeed = score;
                legs = new float[]{pair[0], pair[1], pair[0], pair[1], 1.0F, 1.0F};
            }
        }

        if (pumped) {
            float[] pump = pump(legs, height, centre, alignmentDeg, threshold);
            double score = simulateSpeed(pump, height, centre, alignmentDeg, threshold, SIM_TICKS);
            if (score > bestSpeed) {
                bestSpeed = score;
                legs = pump;
            }
        }

        int slot = next;
        next = (next + 1) % SLOTS;
        if (used < SLOTS) {
            used++;
        }

        keyHeight[slot] = height;
        keyYaw[slot] = centre;
        keyAlignment[slot] = alignmentDeg;
        keyThreshold[slot] = threshold;
        keyAsymmetric[slot] = asymmetric;
        keyPumped[slot] = pumped;
        valLegs[slot] = legs.clone();
        valSpeed[slot] = bestSpeed;
        lastSpeed = bestSpeed;
        return legs.clone();
    }

    /** Coordinate descent on the four pumped parameters, seeded from the flat plan. */
    private static float[] pump(float[] seed, double height, float yaw, double alignmentDeg, double threshold) {
        float[] legs = new float[]{seed[0], seed[1], seed[0], seed[1], 0.5F, 0.5F};
        double best = simulateSpeed(legs, height, yaw, alignmentDeg, threshold, SEARCH_TICKS);

        for (int round = 0; round < 2; round++) {
            for (int axis = 0; axis < 4; axis++) {
                for (float p = MIN_PITCH; p <= MAX_PITCH; p += 5.0F) {
                    float keep = legs[axis];
                    legs[axis] = p;
                    double s = simulateSpeed(legs, height, yaw, alignmentDeg, threshold, SEARCH_TICKS);
                    if (s > best) {
                        best = s;
                    } else {
                        legs[axis] = keep;
                    }
                }

                float around = legs[axis];

                for (float p = Math.max(MIN_PITCH, around - 5.0F); p <= Math.min(MAX_PITCH, around + 5.0F); p += 1.0F) {
                    float keep = legs[axis];
                    legs[axis] = p;
                    double s = simulateSpeed(legs, height, yaw, alignmentDeg, threshold, SEARCH_TICKS);
                    if (s > best) {
                        best = s;
                    } else {
                        legs[axis] = keep;
                    }
                }
            }

            for (int axis = 4; axis < 6; axis++) {
                for (float f = 0.1F; f <= 0.9F; f += 0.1F) {
                    float keep = legs[axis];
                    legs[axis] = f;
                    double s = simulateSpeed(legs, height, yaw, alignmentDeg, threshold, SEARCH_TICKS);
                    if (s > best) {
                        best = s;
                    } else {
                        legs[axis] = keep;
                    }
                }
            }
        }

        return legs;
    }

    private static float[] descend(float seed, double height, float yaw, double alignmentDeg, double threshold) {
        float dive = seed;
        float climb = seed;
        double best = simulateSpeed(dive, climb, height, yaw, alignmentDeg, threshold, SEARCH_TICKS);

        for (int round = 0; round < 2; round++) {
            for (float c = MIN_PITCH; c <= MAX_PITCH; c += 5.0F) {
                double s = simulateSpeed(dive, c, height, yaw, alignmentDeg, threshold, SEARCH_TICKS);
                if (s > best) {
                    best = s;
                    climb = c;
                }
            }

            for (float c = Math.max(MIN_PITCH, climb - 5.0F); c <= Math.min(MAX_PITCH, climb + 5.0F); c += 0.5F) {
                double s = simulateSpeed(dive, c, height, yaw, alignmentDeg, threshold, SEARCH_TICKS);
                if (s > best) {
                    best = s;
                    climb = c;
                }
            }

            for (float d = MIN_PITCH; d <= MAX_PITCH; d += 5.0F) {
                double s = simulateSpeed(d, climb, height, yaw, alignmentDeg, threshold, SEARCH_TICKS);
                if (s > best) {
                    best = s;
                    dive = d;
                }
            }

            for (float d = Math.max(MIN_PITCH, dive - 5.0F); d <= Math.min(MAX_PITCH, dive + 5.0F); d += 0.5F) {
                double s = simulateSpeed(d, climb, height, yaw, alignmentDeg, threshold, SEARCH_TICKS);
                if (s > best) {
                    best = s;
                    dive = d;
                }
            }
        }

        return new float[]{dive, climb};
    }

    public static double predictedSpeed() {
        return lastSpeed;
    }

    public static double simulateSpeed(float pitchMag, double height, float yaw, double alignmentDeg, double threshold) {
        return simulateSpeed(pitchMag, pitchMag, height, yaw, alignmentDeg, threshold, SIM_TICKS);
    }

    public static double simulateSpeed(float diveMag, float climbMag, double height, float yaw, double alignmentDeg, double threshold, int ticks) {
        return simulateSpeed(new float[]{diveMag, climbMag, diveMag, climbMag, 1.0F, 1.0F}, height, yaw, alignmentDeg, threshold, ticks);
    }

    /** The angle in force at a point of a leg, by how far through the leg the altitude already is. */
    public static float legPitch(float[] legs, boolean diving, double progress) {
        if (diving) {
            return progress < legs[4] ? legs[0] : legs[2];
        }

        return progress < legs[5] ? legs[1] : legs[3];
    }

    public static double simulateSpeed(float[] legs, double height, float yaw, double alignmentDeg, double threshold, int ticks) {
        double anti = BepBoost.antiTickSkipping();
        boolean diving = true;
        float pitch = legs[0];
        Vec3d look = Vec3d.fromPolar(pitch, yaw);
        Vec3d v = look.multiply(0.5);
        double alt = 0.0;
        double travelledX = 0.0;
        double travelledZ = 0.0;

        for (int t = 0; t < ticks; t++) {
            if (alt <= -height / 2.0) {
                diving = false;
            } else if (alt >= height / 2.0) {
                diving = true;
            }

            double progress = diving ? (height / 2.0 - alt) / height : (alt + height / 2.0) / height;
            progress = MathHelper.clamp(progress, 0.0, 1.0);
            float magnitude = legPitch(legs, diving, progress);
            float wanted = diving ? magnitude : -magnitude;
            Vec3d lastLook;
            if (wanted != pitch) {
                pitch = wanted;
                lastLook = look;
                look = Vec3d.fromPolar(wanted, yaw);
            } else {
                lastLook = look;
            }

            Vec3d next = BepBoost.rideWindow(v, look, lastLook, pitch, GRAVITY, threshold, anti, alignmentDeg, NO_CAP);
            if (next == null) {
                next = vanillaGlide(v, look, pitch);
            }

            v = next;
            alt += v.y;
            travelledX += v.x;
            travelledZ += v.z;
        }

        return Math.sqrt(travelledX * travelledX + travelledZ * travelledZ) / ticks * 20.0;
    }

    private static Vec3d vanillaGlide(Vec3d v, Vec3d look, float pitch) {
        Vec3d pushed = v.add(
            look.x * 0.1 + (look.x * 1.5 - v.x) * 0.5,
            look.y * 0.1 + (look.y * 1.5 - v.y) * 0.5,
            look.z * 0.1 + (look.z * 1.5 - v.z) * 0.5
        );
        return BepBoost.predictGliding(pushed, look, pitch, GRAVITY);
    }
}
