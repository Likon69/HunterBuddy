package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.GoToTree;
import java.util.Objects;
import java.util.Set;

public record GoToTask(TargetPoint point, double radius, BaritoneBridge baritone) implements LabTask {
    private static final Key<TargetPoint> TARGET = new Key<>("lab-goto-target", TargetPoint.class, Scope.OWNER, false);

    public GoToTask {
        Objects.requireNonNull(point);
        Objects.requireNonNull(baritone);
        if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("invalid arrival radius");
    }

    @Override
    public String name() {
        return "goto";
    }

    @Override
    public String target() {
        return point.toString();
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.BARITONE, Resource.MOVEMENT, Resource.LOOK, Resource.SLOT, Resource.MINING, Resource.INTERACT);
    }

    @Override
    public void initialize(BotContext context) {
        context.ticks().bb().set(context.owner(), TARGET, point);
    }

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        return GoToTree.build(TARGET, point, radius, world, baritone, outputs);
    }

    @Override
    public boolean recoverFailure() {
        return true;
    }
}
