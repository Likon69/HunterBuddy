package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.FarmerCatalog;
import com.hunterbuddy.behavior.farmer.FarmerStepConfig;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.ResolveMineSiteLab;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.StopBot;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.MineSiteTree;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public record MineSiteTask(FarmerCatalog catalog, FarmerZoneLookup lookup, List<FarmerStepConfig> steps,
                           CompletableFuture<Map<Key<?>, Object>> mission, String worldKey, BaritoneBridge baritone) implements LabTask {
    @Override
    public String name() {
        return "farmer minesite";
    }

    @Override
    public String target() {
        return "verified site of current FarmerBuddy step, up to 1728 blocks, then return by carved access";
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.MOVEMENT, Resource.BARITONE, Resource.MINING, Resource.INVENTORY, Resource.INTERACT);
    }

    @Override
    public void initialize(BotContext context) {}

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        MineSiteLabContext lab = new MineSiteLabContext(catalog, lookup, steps, mission, worldKey, world, baritone, outputs);
        return new Sequence("mine-verified-site-lab", new ResolveMineSiteLab(lab),
            new Deferred("mine-site-by-catalog-strategy", () -> MineSiteTree.build(lab.mine)),
            StopBot.withReason(ExitReason.COMPLETED, ctx -> "Mining " + lab.mine.profile.label() + ": " + lab.mine.facts.mined
                + " blocks, " + lab.mine.facts.excavated + " access blocks; return confirmed. " + lab.mine.facts.outcome));
    }
}
