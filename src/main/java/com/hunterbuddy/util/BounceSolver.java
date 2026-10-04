package com.hunterbuddy.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public final class BounceSolver {
    public static final double GLIDE_HEIGHT = 0.6;
    public static final double MAX_USEFUL_HEADROOM = 2.0;
    public static final int SCHEDULE_TICKS = 16;
    public static final int MAX_PERSIST = 40;
    private static final int GLIDE_PERSIST_TICKS = 3;
    private static final int SIM_TICKS = 1000;
    private static final int AVG_TICKS = 400;
    private static final int MIN_PITCH = 0;
    private static final int MAX_PITCH = 90;
    private static final int THOROUGH_SEEDS = 6;

    private BounceSolver() {
    }

    public static double measureHeadroom(PlayerEntity player, World world) {
        double halfWidth = Physics.DEFAULT.width / 2.0;
        Box box = new Box(
            player.getX() - halfWidth,
            player.getY(),
            player.getZ() - halfWidth,
            player.getX() + halfWidth,
            player.getY() + Physics.DEFAULT.glideHeight,
            player.getZ() + halfWidth
        );
        double free = 0.0;
        for (int step = 1; step <= 40; step++) {
            double rise = step * 0.05;
            if (rise > MAX_USEFUL_HEADROOM || !world.isSpaceEmpty(box.offset(0.0, rise, 0.0))) break;
            free = rise;
        }
        return free;
    }

    public static double simulateSpeed(float pitch, double headroom, boolean clearHeld) {
        return simulateSpeed(pitch, pitch, headroom, clearHeld);
    }

    public static double simulateSpeed(float pitchUp, float pitchDown, double headroom, boolean clearHeld) {
        return simulate((ticks, vertical) -> vertical >= 0.0 ? pitchUp : pitchDown, headroom,
            clearHeld ? 0 : GLIDE_PERSIST_TICKS, null, null, Physics.DEFAULT);
    }

    public static float solvePitch(double headroom, boolean clearHeld) {
        int persist = clearHeld ? 0 : GLIDE_PERSIST_TICKS;
        Plan plan = search(headroom, table -> simulateTable(table, headroom, persist, Physics.DEFAULT),
            persist, false, Physics.DEFAULT);
        return plan.pitchByTick[plan.pitchByTick.length - 1];
    }

    public static float[] solvePlan(double headroom, boolean clearHeld) {
        float bestUp = MAX_PITCH;
        float bestDown = MAX_PITCH;
        double bestSpeed = Double.NEGATIVE_INFINITY;
        for (float up = MIN_PITCH; up <= MAX_PITCH; up += 5.0F) {
            for (float down = MIN_PITCH; down <= MAX_PITCH; down += 5.0F) {
                double speed = simulateSpeed(up, down, headroom, clearHeld);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestUp = up;
                    bestDown = down;
                }
            }
        }
        float coarseUp = bestUp;
        float coarseDown = bestDown;
        for (float up = Math.max(MIN_PITCH, coarseUp - 4.0F); up <= Math.min(MAX_PITCH, coarseUp + 4.0F); up++) {
            for (float down = Math.max(MIN_PITCH, coarseDown - 4.0F); down <= Math.min(MAX_PITCH, coarseDown + 4.0F); down++) {
                double speed = simulateSpeed(up, down, headroom, clearHeld);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestUp = up;
                    bestDown = down;
                }
            }
        }
        return new float[]{bestUp, bestDown};
    }

    public static Plan solve(double headroom, boolean schedule) {
        return solve(headroom, schedule, Physics.DEFAULT);
    }

    public static Plan solve(double headroom, boolean schedule, Physics physics) {
        return search(headroom, table -> expectedSpeed(table, headroom, physics), 0, schedule, physics);
    }

    public static double expectedSpeed(float[] table, double headroom, Physics physics) {
        return simulateTable(table, headroom, 0, physics);
    }

    private static double simulateTable(float[] table, double headroom, int persist, Physics physics) {
        return simulate((ticks, vertical) -> table[Math.min(ticks, table.length - 1)],
            headroom, persist, null, null, physics);
    }

    public static double simulate(PitchFn fn, double headroom, int persist, float[] recordTable,
                                  int[] cycleOut, Physics physics) {
        MotionState motion = new MotionState();
        double y = Math.min(0.3, headroom);
        double z = 0.0;
        double zMark = 0.0;
        boolean onGround = false;
        boolean gliding = true;
        boolean prevKey = false;
        int queueSize = Integer.highestOneBit(MAX_PERSIST) << 1;
        int[] clears = new int[queueSize];
        int clearHead = 0;
        int clearTail = 0;
        int ticksSinceJump = 0;
        int lastLanding = -1;
        int cycleSum = 0;
        int cycleCount = 0;
        double ceilingHeight = headroom + physics.glideHeight;
        for (int tick = 0; tick < SIM_TICKS; tick++) {
            if (tick == SIM_TICKS - AVG_TICKS) zMark = z;
            while (clearHead != clearTail && clears[clearHead & (queueSize - 1)] <= tick) {
                clearHead++;
                gliding = false;
            }
            applyMovementThreshold(motion);
            boolean key = onGround || !gliding && !prevKey;
            if (key && !prevKey && !onGround && !gliding) gliding = true;
            prevKey = key;
            if (key && onGround) {
                applyJump(motion, physics);
                ticksSinceJump = 0;
            }
            float pitch = fn.pitch(ticksSinceJump, motion.vertical);
            if (recordTable != null) recordTable[Math.min(ticksSinceJump, recordTable.length - 1)] = pitch;
            double availableHeight = ceilingHeight - y;
            double hitbox = gliding ? physics.glideHeight
                : availableHeight >= physics.standingHeight ? physics.standingHeight
                : availableHeight >= physics.crouchingHeight ? physics.crouchingHeight : physics.glideHeight;
            double free = ceilingHeight - hitbox;
            boolean wasGround = onGround;
            if (gliding) {
                applyGlideMovement(motion, pitch, physics);
            } else {
                motion.forward += forwardInputAcceleration(physics, onGround);
            }
            double nextY = y + motion.vertical;
            onGround = false;
            if (nextY < 0.0) {
                nextY = 0.0;
                motion.vertical = 0.0;
                onGround = true;
            } else if (nextY > free) {
                nextY = free;
                motion.vertical = 0.0;
            }
            y = nextY;
            z += motion.forward;
            if (!gliding) applyAirFriction(motion, wasGround, physics);
            ticksSinceJump++;
            if (onGround) {
                if (persist > 0 && clearTail - clearHead < queueSize) {
                    clears[clearTail++ & (queueSize - 1)] = tick + persist;
                }
                if (!wasGround) {
                    if (lastLanding >= 0 && tick >= SIM_TICKS - AVG_TICKS) {
                        cycleSum += tick - lastLanding;
                        cycleCount++;
                    }
                    lastLanding = tick;
                }
            }
        }
        if (cycleOut != null) cycleOut[0] = cycleCount > 0 ? (int) Math.round((double) cycleSum / cycleCount) : 0;
        return (z - zMark) / AVG_TICKS * 20.0;
    }

    private static void applyMovementThreshold(MotionState motion) {
        if (motion.forward * motion.forward < 9.0E-6) motion.forward = 0.0;
        if (Math.abs(motion.vertical) < 0.003) motion.vertical = 0.0;
    }

    private static void applyJump(MotionState motion, Physics physics) {
        if (physics.jumpPower <= 1.0E-5F) return;
        if (physics.sprinting) motion.forward += 0.2;
        motion.vertical = Math.max(physics.jumpPower, motion.vertical);
    }

    private static double forwardInputAcceleration(Physics physics, boolean onGround) {
        float acceleration = onGround
            ? physics.movementSpeed * (0.21600002F / (physics.groundFriction * physics.groundFriction * physics.groundFriction))
            : physics.sprinting ? 0.025999999F : 0.02F;
        return 0.98F * acceleration;
    }

    private static double effectiveGravity(Physics physics, double vertical) {
        return vertical <= 0.0 && physics.slowFalling ? Math.min(physics.gravity, 0.01) : physics.gravity;
    }

    private static void applyAirFriction(MotionState motion, boolean startedOnGround, Physics physics) {
        float blockFriction = startedOnGround ? physics.groundFriction : 1.0F;
        motion.forward *= blockFriction * 0.91F;
        motion.vertical = (motion.vertical - effectiveGravity(physics, motion.vertical)) * 0.98F;
    }

    private static void applyGlideMovement(MotionState motion, float pitch, Physics physics) {
        float leanAngle = pitch * (float) (Math.PI / 180.0);
        double lookZ = MathHelper.cos(leanAngle);
        double lookHorizontal = Math.abs(lookZ);
        double horizontalSpeed = Math.abs(motion.forward);
        double lift = MathHelper.square(Math.cos(leanAngle));
        double vertical = motion.vertical + effectiveGravity(physics, motion.vertical) * (-1.0 + lift * 0.75);
        double forward = motion.forward;
        if (vertical < 0.0 && lookHorizontal > 0.0) {
            double convert = vertical * -0.1 * lift;
            forward += lookZ * convert / lookHorizontal;
            vertical += convert;
        }
        if (leanAngle < 0.0F && lookHorizontal > 0.0) {
            double convert = horizontalSpeed * -MathHelper.sin(leanAngle) * 0.04;
            forward -= lookZ * convert / lookHorizontal;
            vertical += convert * 3.2;
        }
        if (lookHorizontal > 0.0) forward += (lookZ / lookHorizontal * horizontalSpeed - forward) * 0.1;
        motion.forward = forward * 0.99F;
        motion.vertical = vertical * 0.98F;
    }

    private static Plan search(double headroom, ToDoubleFunction<float[]> scorer, int seedPersist,
                               boolean schedule, Physics physics) {
        float[] best = new float[SCHEDULE_TICKS];
        double bestScore = Double.NEGATIVE_INFINITY;
        List<float[]> seeds = new ArrayList<>();
        List<Double> seedScores = new ArrayList<>();
        for (int pitch = MIN_PITCH; pitch <= MAX_PITCH; pitch++) {
            float[] table = new float[SCHEDULE_TICKS];
            Arrays.fill(table, pitch);
            double score = scorer.applyAsDouble(table);
            if (score > bestScore) {
                bestScore = score;
                best = table;
            }
            if (schedule) {
                seeds.add(table);
                seedScores.add(score);
            }
        }
        if (schedule) {
            for (int up = 30; up <= MAX_PITCH; up += 5) {
                for (int down = MIN_PITCH; down <= MAX_PITCH; down += 5) {
                    float pitchUp = up;
                    float pitchDown = down;
                    float[] table = new float[SCHEDULE_TICKS];
                    Arrays.fill(table, pitchUp);
                    simulate((ticks, vertical) -> vertical >= 0.0 ? pitchUp : pitchDown,
                        headroom, seedPersist, table, null, physics);
                    double score = scorer.applyAsDouble(table);
                    if (score > bestScore) {
                        bestScore = score;
                        best = table;
                    }
                    seeds.add(table);
                    seedScores.add(score);
                }
            }
            for (int first = 3; first <= 8; first++) {
                for (int second = first + 1; second <= 13; second++) {
                    for (int up = 60; up <= MAX_PITCH; up += 10) {
                        for (int downFirst = MIN_PITCH; downFirst <= MAX_PITCH; downFirst += 15) {
                            for (int downSecond = MIN_PITCH; downSecond <= MAX_PITCH; downSecond += 15) {
                                float[] table = new float[SCHEDULE_TICKS];
                                for (int tick = 0; tick < table.length; tick++) {
                                    table[tick] = tick < first ? up : tick < second ? downFirst : downSecond;
                                }
                                double score = scorer.applyAsDouble(table);
                                if (score > bestScore) {
                                    bestScore = score;
                                    best = table;
                                }
                                seeds.add(table);
                                seedScores.add(score);
                            }
                        }
                    }
                }
            }
            Integer[] order = new Integer[seeds.size()];
            for (int index = 0; index < order.length; index++) order[index] = index;
            Arrays.sort(order, Comparator.comparingDouble(index -> -seedScores.get(index)));
            for (int index = 0; index < Math.min(THOROUGH_SEEDS, order.length); index++) {
                float[] table = seeds.get(order[index]).clone();
                double score = descend(table, seedScores.get(order[index]), scorer, 6, 3);
                if (score > bestScore) {
                    bestScore = score;
                    best = table;
                }
            }
            bestScore = descend(best, bestScore, scorer, 2, 2);
            bestScore = descend(best, bestScore, scorer, 1, 1);
        }
        float[] table = best;
        int[] cycle = new int[1];
        simulate((ticks, vertical) -> table[Math.min(ticks, table.length - 1)],
            headroom, seedPersist, null, cycle, physics);
        return new Plan(table, bestScore, cycle[0], headroom, physics);
    }

    private static double descend(float[] table, double score, ToDoubleFunction<float[]> scorer, int step, int passes) {
        for (int pass = 0; pass < passes; pass++) {
            boolean improved = false;
            for (int index = 0; index < table.length; index++) {
                float keep = table[index];
                for (int pitch = MIN_PITCH; pitch <= MAX_PITCH; pitch += step) {
                    if (pitch == keep) continue;
                    table[index] = pitch;
                    double candidate = scorer.applyAsDouble(table);
                    if (candidate > score + 1.0E-6) {
                        score = candidate;
                        keep = pitch;
                        improved = true;
                    }
                }
                table[index] = keep;
            }
            if (!improved) break;
        }
        return score;
    }

    public record Physics(
        double gravity, float jumpPower, float movementSpeed,
        float groundFriction, float airDragModifier, float frictionModifier,
        boolean slowFalling, boolean sprinting,
        float width, float standingHeight, float crouchingHeight, float glideHeight
    ) {
        public static final Physics DEFAULT = new Physics(
            0.08, 0.42F, 0.13F, 0.6F, 1.0F, 1.0F, false, true, 0.6F, 1.8F, 1.5F, 0.6F
        );
    }

    @FunctionalInterface
    public interface PitchFn {
        float pitch(int ticksSinceJump, double verticalVelocity);
    }

    public static final class Plan {
        public final float[] pitchByTick;
        public final double speed;
        public final int cycle;
        public final double headroom;
        public final Physics physics;

        Plan(float[] pitchByTick, double speed, int cycle, double headroom, Physics physics) {
            this.pitchByTick = pitchByTick;
            this.speed = speed;
            this.cycle = cycle;
            this.headroom = headroom;
            this.physics = physics;
        }

        public float pitchAt(int ticksSinceJump) {
            return pitchByTick[Math.min(Math.max(ticksSinceJump, 0), pitchByTick.length - 1)];
        }

        public float minPitch() {
            float min = Float.MAX_VALUE;
            for (int index = 0; index < Math.max(1, Math.min(cycle, pitchByTick.length)); index++) {
                min = Math.min(min, pitchByTick[index]);
            }
            return min;
        }

        public float maxPitch() {
            float max = -Float.MAX_VALUE;
            for (int index = 0; index < Math.max(1, Math.min(cycle, pitchByTick.length)); index++) {
                max = Math.max(max, pitchByTick[index]);
            }
            return max;
        }
    }

    private static final class MotionState {
        double forward;
        double vertical;
    }
}
