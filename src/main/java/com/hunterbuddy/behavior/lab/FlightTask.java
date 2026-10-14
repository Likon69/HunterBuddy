package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.ExitWaterTree;
import java.util.Objects;
import java.util.Set;

public record FlightTask(String name, BaritoneBridge bridge) implements LabTask {
    public FlightTask {
        if (!name.equals("exitwater") && !name.equals("takeoff")) throw new IllegalArgumentException("invalid flight laboratory scenario");
        Objects.requireNonNull(bridge);
    }
    @Override
    public String target() { return name + ": current position"; }
    @Override
    public Set<Resource> resources() { return Set.of(Resource.BARITONE, Resource.MOVEMENT, Resource.MINING, Resource.ROCKETS, Resource.INTERACT); }
    @Override
    public void initialize(BotContext context) { Objects.requireNonNull(context); }
    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) { return ExitWaterTree.build(world, bridge, outputs); }
    @Override
    public boolean flightAware() { return true; }
}
