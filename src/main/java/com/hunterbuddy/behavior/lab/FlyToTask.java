package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.StopBot;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.FlyToTree;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public record FlyToTask(BlockPos destination, BlockPos home, String homeWorld, BaritoneBridge baritone) implements LabTask {
    @Override
    public String name() {
        return "flyto";
    }

    @Override
    public String target() {
        return destination.getX() + " " + destination.getZ() + " and back to " + home.toShortString();
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.MOVEMENT, Resource.BARITONE, Resource.INVENTORY, Resource.MINING, Resource.ROCKETS, Resource.INTERACT);
    }

    @Override
    public void initialize(BotContext context) {
    }

    @Override
    public boolean flightAware() {
        return true;
    }

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        return new Selector("flight-round-trip-with-return-fallback",
            new Sequence("outbound-and-home", leg(destination, false, world, outputs), leg(home, true, world, outputs)),
            new Sequence("return-after-flight-failure", leg(home, true, world, outputs),
                StopBot.withReason(ExitReason.FAILED, ctx -> "Flight lab returned home: "
                    + ctx.bb().get(ctx.owner(), FlightPlan.LAST_FAILURE).orElse("journey incomplete"))));
    }

    private Node leg(BlockPos target, boolean returning, WorldReader world, Outputs outputs) {
        return new Guard(new Condition("flight home world unchanged", sensors -> homeWorld.equals(world.storageKey())),
            FlyToTree.build(() -> target, () -> home, returning, world, baritone, outputs));
    }
}
