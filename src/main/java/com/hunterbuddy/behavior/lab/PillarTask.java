package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.PillarUp;
import com.hunterbuddy.behavior.nodes.ProgressGuard;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;

public record PillarTask(double targetY) implements LabTask {
    public PillarTask {
        if (!Double.isFinite(targetY)) throw new IllegalArgumentException("invalid pillar target Y");
    }

    @Override
    public String name() {
        return "pillar";
    }

    @Override
    public String target() {
        return "Y=" + targetY;
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.SLOT, Resource.INTERACT);
    }

    @Override
    public void initialize(BotContext context) {
        context.ticks().bb().set(context.owner(), TickContext.CURRENT_TARGET, target());
    }

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        return new Timeout(1_800, new ProgressGuard(sensors -> sensors.y(), 1, 200,
            new WithLease(resources(), LeasePolicy.WAIT, new PillarUp(targetY, world, outputs))).keyedBy(ctx -> target()));
    }
}
