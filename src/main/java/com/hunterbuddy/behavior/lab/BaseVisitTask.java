package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.FarmerCatalog;
import com.hunterbuddy.behavior.farmer.FarmerSettings;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.ResolveBaseVisitLab;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.StopBot;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.BaseVisitTree;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public record BaseVisitTask(FarmerCatalog catalog, FarmerZoneLookup lookup, FarmerSettings settings,
                            CompletableFuture<Map<Key<?>, Object>> mission, String worldKey, BaritoneBridge baritone) implements LabTask {
    @Override
    public String name() {
        return "farmer basevisit";
    }

    @Override
    public String target() {
        return "visite complète de la base de l'étape FarmerBuddy courante";
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.MOVEMENT, Resource.BARITONE, Resource.MINING, Resource.INVENTORY, Resource.INTERACT, Resource.ROCKETS);
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
        BaseVisitLabContext lab = new BaseVisitLabContext(catalog, lookup, settings, mission, worldKey, world, baritone, outputs);
        return new Sequence("labo-visite-base", new Timeout(100, new ResolveBaseVisitLab(lab)),
            new Deferred("visiter-base-en-un-passage", () -> BaseVisitTree.build(lab.visit)).resumable(),
            StopBot.withReason(ExitReason.COMPLETED, ctx -> "Base visitée, livraisons confirmées : "
                + lab.visit.logistics.mission.delivered(ctx, lab.visit.facts.step) + "; réserve et fusées prêtes, ender chest repris."));
    }
}
