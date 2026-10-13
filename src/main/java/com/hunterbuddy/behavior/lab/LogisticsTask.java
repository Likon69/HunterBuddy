package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.RegearTree;
import com.hunterbuddy.behavior.trees.StoreTree;
import java.util.Set;

public record LogisticsTask(String scenario, FarmerProfile profile, FarmerZone zone, BaritoneBridge baritone) implements LabTask {
    @Override
    public String name() {
        return scenario;
    }

    @Override
    public String target() {
        return profile.key();
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.MOVEMENT, Resource.BARITONE, Resource.MINING, Resource.INVENTORY, Resource.INTERACT);
    }

    @Override
    public void initialize(BotContext context) {}

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        MissionJournal journal = new MissionJournal(8);
        return scenario.equals("store")
            ? StoreTree.build(() -> profile, () -> zone, () -> false, () -> 0, world, baritone, outputs, journal)
            : RegearTree.build(() -> profile, () -> zone, () -> 0, world, baritone, outputs, journal);
    }
}
