package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.FarmerCatalog;
import com.hunterbuddy.behavior.farmer.FarmerStepConfig;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.SurveyContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.SurveyTree;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.math.BlockPos;

public record SurveyTask(FarmerCatalog catalog, FarmerZoneLookup lookup, List<FarmerStepConfig> steps,
                         CompletableFuture<Map<Key<?>, Object>> mission, BlockPos home, String homeWorld,
                         BaritoneBridge baritone) implements LabTask {
    @Override
    public String name() {
        return "farmer survey";
    }

    @Override
    public String target() {
        return "current enabled FarmerBuddy step, Xaero zone, connected material sites";
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.MOVEMENT, Resource.BARITONE, Resource.INVENTORY, Resource.MINING, Resource.ROCKETS, Resource.INTERACT);
    }

    @Override
    public void initialize(BotContext context) {
        context.ticks().outputs().flight().requestPilot(context.owner());
    }

    @Override
    public boolean flightAware() {
        return true;
    }

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        SurveyContext survey = new SurveyContext(world, baritone, outputs, catalog, lookup, steps, mission, home, homeWorld);
        return SurveyTree.build(survey);
    }
}
