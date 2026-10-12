package com.hunterbuddy.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public final class HopPlanner {
    public record Body(double gravity, float jumpPower, boolean sprinting, boolean slowFalling, float width, float glideHeight) {
        public static final Body DEFAULT = new Body(0.08, 0.42F, true, false, 0.6F, 0.6F);
    }

    public static final class Route {
        public final double speed;
        public final double headroom;
        public final int cycle;
        private final float[] angles;

        private Route(double speed, double headroom, int cycle, int[] table) {
            this.speed = speed;
            this.headroom = headroom;
            this.cycle = cycle;
            this.angles = new float[TABLE_SIZE];
            for (int i = 0; i < TABLE_SIZE; i++) angles[i] = table[i];
        }

        public float pitchAt(int ticksSinceJump) {
            return angles[Math.max(0, Math.min(TABLE_SIZE - 1, ticksSinceJump))];
        }

        public float lowestPitch() {
            float best = Float.MAX_VALUE;
            for (int i = 0; i < span(); i++) best = Math.min(best, angles[i]);
            return best;
        }

        public float highestPitch() {
            float best = -Float.MAX_VALUE;
            for (int i = 0; i < span(); i++) best = Math.max(best, angles[i]);
            return best;
        }

        private int span() {
            return Math.max(1, Math.min(cycle, TABLE_SIZE));
        }
    }

    private static final int TABLE_SIZE = 16;
    private static final int TOTAL_TICKS = 1000;
    private static final int WINDOW = 400;
    private static final int MAX_SIMULATIONS = 20000;
    private static final double STEP = 0.05;
    private static final double MAX_HEADROOM = 2.0;

    private HopPlanner() {
    }

    public static double headroomAbove(PlayerEntity player, World world) {
        Body body = Body.DEFAULT;
        double half = body.width() / 2.0;
        Box base = new Box(player.getX() - half, player.getY(), player.getZ() - half,
                player.getX() + half, player.getY() + body.glideHeight(), player.getZ() + half);
        int steps = 0;
        while ((steps + 1) * STEP <= MAX_HEADROOM && world.isSpaceEmpty(base.offset(0, (steps + 1) * STEP, 0))) {
            steps++;
        }
        return steps * STEP;
    }

    public static Route plan(double headroom, boolean perTick, Body body) {
        Search search = new Search(headroom, body);
        int[] best = new int[TABLE_SIZE];
        Outcome bestOutcome = null;
        for (int angle = 0; angle <= 90; angle++) {
            int[] table = constant(angle);
            Outcome outcome = search.run(table);
            if (bestOutcome == null || outcome.speed > bestOutcome.speed) {
                bestOutcome = outcome;
                best = table;
            }
        }
        if (perTick) {
            int[] seed = best.clone();
            Outcome seedOutcome = bestOutcome;
            int[] lengths = {1, 2, 4, 8};
            for (int first = 0; first <= 90; first += 15) {
                for (int second = 0; second <= 90; second += 15) {
                    if (first == second) continue;
                    for (int length : lengths) {
                        int[] table = new int[TABLE_SIZE];
                        for (int i = 0; i < TABLE_SIZE; i++) table[i] = i < length ? first : second;
                        Outcome outcome = search.run(table);
                        if (outcome.speed > seedOutcome.speed) {
                            seedOutcome = outcome;
                            seed = table;
                        }
                    }
                }
            }
            int[] current = seed.clone();
            Outcome currentOutcome = seedOutcome;
            int[] steps = {8, 4, 2, 1};
            for (int step : steps) {
                boolean gained = true;
                while (gained && search.hasBudget()) {
                    gained = false;
                    for (int i = 0; i < TABLE_SIZE && search.hasBudget(); i++) {
                        for (int sign = -1; sign <= 1; sign += 2) {
                            int candidate = current[i] + sign * step;
                            if (candidate < 0 || candidate > 90 || !search.hasBudget()) continue;
                            int[] table = current.clone();
                            table[i] = candidate;
                            Outcome outcome = search.run(table);
                            if (outcome.speed > currentOutcome.speed) {
                                currentOutcome = outcome;
                                current = table;
                                gained = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (currentOutcome.speed > bestOutcome.speed) {
                bestOutcome = currentOutcome;
                best = current;
            }
        }
        return new Route(bestOutcome.speed, headroom, bestOutcome.cycle, best);
    }

    private static int[] constant(int angle) {
        int[] table = new int[TABLE_SIZE];
        java.util.Arrays.fill(table, angle);
        return table;
    }

    private record Outcome(double speed, int cycle) {
    }

    private static final class Search {
        private final double headroom;
        private final Body body;
        private int used;

        Search(double headroom, Body body) {
            this.headroom = headroom;
            this.body = body;
        }

        boolean hasBudget() {
            return used < MAX_SIMULATIONS;
        }

        Outcome run(int[] table) {
            used++;
            double y = Math.min(0.3, headroom);
            double f = 0;
            double v = 0;
            boolean grounded = false;
            int since = 0;
            double advance = 0;
            int lastLanding = -1;
            int gapSum = 0;
            int gapCount = 0;
            for (int t = 0; t < TOTAL_TICKS; t++) {
                if (f * f < 9.0E-6) f = 0;
                if (Math.abs(v) < 0.003) v = 0;
                if (grounded) {
                    if (body.sprinting()) f += 0.2;
                    v = Math.max(body.jumpPower(), v);
                    since = 0;
                }
                float pitch = table[Math.min(since, TABLE_SIZE - 1)];
                float r = pitch * (float) Math.PI / 180.0F;
                float c = MathHelper.cos(r);
                double cosR = Math.cos(r);
                double lift = cosR * cosR;
                double g = body.slowFalling() && v <= 0 ? Math.min(body.gravity(), 0.01) : body.gravity();
                double h0 = Math.abs(f);
                v += g * (-1.0 + 0.75 * lift);
                if (v < 0 && c > 0) {
                    double k = v * -0.1 * lift;
                    f += k;
                    v += k;
                }
                if (r < 0 && c > 0) {
                    double k = h0 * -MathHelper.sin(r) * 0.04;
                    f -= k;
                    v += 3.2 * k;
                }
                if (c > 0) f += (h0 - f) * 0.1;
                f *= 0.99;
                v *= 0.98;
                double next = y + v;
                boolean nowGrounded;
                if (next < 0) {
                    next = 0;
                    v = 0;
                    nowGrounded = true;
                } else if (next > headroom) {
                    next = headroom;
                    v = 0;
                    nowGrounded = false;
                } else {
                    nowGrounded = false;
                }
                y = next;
                if (t >= TOTAL_TICKS - WINDOW) {
                    advance += f;
                    if (!grounded && nowGrounded) {
                        if (lastLanding >= 0) {
                            gapSum += t - lastLanding;
                            gapCount++;
                        }
                        lastLanding = t;
                    }
                }
                grounded = nowGrounded;
                since++;
            }
            int cycle = gapCount == 0 ? 0 : (int) Math.round((double) gapSum / gapCount);
            return new Outcome(advance / WINDOW * 20.0, cycle);
        }
    }
}
