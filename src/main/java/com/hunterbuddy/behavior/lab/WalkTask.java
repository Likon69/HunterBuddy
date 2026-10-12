package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.mc.Conditions;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.ProgressGuard;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WalkTowards;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Objects;
import java.util.Set;

public record WalkTask(TargetPoint point, double radius) implements LabTask {
    private static final Key<TargetPoint> TARGET = new Key<>("lab-walk-target", TargetPoint.class, Scope.OWNER, false);
    private static final int LIMIT_TICKS = 1_200;
    private static final int PROGRESS_WINDOW_TICKS = 100;
    private static final double MIN_GAIN = 1;

    public WalkTask {
        Objects.requireNonNull(point);
        if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("invalid lab arrival radius");
    }

    @Override
    public String name() {
        return "walk";
    }

    @Override
    public String target() {
        return point.toString();
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.MOVEMENT, Resource.LOOK);
    }

    @Override
    public void initialize(BotContext context) {
        context.ticks().bb().set(context.owner(), TARGET, point);
    }

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        return new Timeout(Math.min(LIMIT_TICKS, context.config().defaultActionLimitTicks()),
            new ProgressGuard(sensors -> -Conditions.distance(sensors, point), MIN_GAIN, PROGRESS_WINDOW_TICKS,
                new WithLease(resources(), LeasePolicy.WAIT, new WalkTowards(TARGET, radius, world, outputs)))
                .keyedBy(ctx -> target()));
    }
}
