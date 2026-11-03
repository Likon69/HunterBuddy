package com.hunterbuddy.modules.regear.arealoader.modes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.math.BlockPos;

final class SpiralLegSearch {
    static final int PATH_Y = 64;
    static final float START_YAW = 270.0f;

    private static final int MIN_LEGS = 500;
    private static final long MAX_LEGS = 500_000;
    private static final int LEGS_PER_RING = 4;
    private static final double CANDIDATE_RADIUS_FACTOR = 1.5;
    private static final double LEG_TAIL_FRACTION = 0.95;
    private static final double FAR_PICK_FACTOR = 2.0;
    private static final double FAR_PICK_MARGIN = 50.0;
    private static final double CLOSE_MARGIN = 10.0;

    private final BlockPos origin;
    private final int gap;
    private final Consumer<String> log;

    SpiralLegSearch(BlockPos origin, int gap, Consumer<String> log) {
        this.origin = origin;
        this.gap = gap;
        this.log = log;
    }

    List<Spiral.SpiralLegInfo> generate(int count) {
        List<Spiral.SpiralLegInfo> legs = new ArrayList<>(count);
        SpiralLeg leg = new SpiralLeg(new BlockPos(origin.getX(), PATH_Y, origin.getZ()), 0, 0, START_YAW, true);
        for (int number = 0; number < count; number++) {
            legs.add(new Spiral.SpiralLegInfo(number, leg, gap));
            leg = leg.next(gap);
        }
        return legs;
    }

    Spiral.SpiralLegInfo findClosest(BlockPos target) {
        List<Spiral.SpiralLegInfo> legs = generate(legCount(target));
        double closest = legs.stream().mapToDouble(leg -> distance(leg, target)).min().orElse(Double.MAX_VALUE);
        log.accept("Searched " + legs.size() + " legs, closest possible distance " + Math.round(closest));
        List<Spiral.SpiralLegInfo> candidates = legs.stream()
            .filter(leg -> distance(leg, target) <= CANDIDATE_RADIUS_FACTOR * gap).toList();
        if (candidates.isEmpty()) return nearest(legs, target);
        Spiral.SpiralLegInfo pick = latestBeforeTail(candidates, target);
        if (distance(pick, target) <= FAR_PICK_FACTOR * closest + FAR_PICK_MARGIN) return pick;
        return firstWithin(legs, target, closest + CLOSE_MARGIN);
    }

    private int legCount(BlockPos target) {
        long span = Math.max(Math.abs(target.getX() - origin.getX()), Math.abs(target.getZ() - origin.getZ()));
        long count = (span / gap + 1) * LEGS_PER_RING + MIN_LEGS;
        return (int) Math.max(MIN_LEGS, Math.min(MAX_LEGS, count));
    }

    private static double distance(Spiral.SpiralLegInfo leg, BlockPos target) {
        return leg.segment().distanceTo(target.getX(), target.getZ());
    }

    private Spiral.SpiralLegInfo nearest(List<Spiral.SpiralLegInfo> legs, BlockPos target) {
        log.accept("No leg within reach of the target, using the nearest one");
        return legs.stream().min(Comparator.comparingDouble(leg -> distance(leg, target))).orElseThrow();
    }

    private Spiral.SpiralLegInfo latestBeforeTail(List<Spiral.SpiralLegInfo> candidates, BlockPos target) {
        List<Spiral.SpiralLegInfo> beforeTail = candidates.stream()
            .filter(leg -> leg.segment().fractionNearest(target.getX(), target.getZ()) < LEG_TAIL_FRACTION).toList();
        List<Spiral.SpiralLegInfo> pool = beforeTail.isEmpty() ? candidates : beforeTail;
        return pool.stream().max(Comparator.comparingInt(leg -> leg.legNumber)).orElseThrow();
    }

    private Spiral.SpiralLegInfo firstWithin(List<Spiral.SpiralLegInfo> legs, BlockPos target, double limit) {
        log.accept("Latest candidate is too far from the target, using the first close leg");
        return legs.stream().filter(leg -> distance(leg, target) <= limit).findFirst().orElseThrow();
    }
}
