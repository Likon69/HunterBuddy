package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.nodes.ChooseLogisticsSite;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.PrepareLogisticsSite;
import com.hunterbuddy.behavior.nodes.ProtectionBuilder;
import com.hunterbuddy.behavior.nodes.RetryLogisticsSite;
import com.hunterbuddy.behavior.nodes.Sequence;

public final class LogisticsSiteTree {
    private LogisticsSiteTree() {}

    public static Node build(LogisticsContext logistics) {
        return LogisticsOperations.unless("site choisi valide et protégé", logistics::protectedSite,
            new RetryLogisticsSite(logistics, new Sequence("choisir-et-protéger-emplacement",
                new ChooseLogisticsSite(logistics), LogisticsOperations.prepare(logistics, InventoryLayout::silkPickaxe, 1),
                new Deferred("rejoindre-emplacement", () ->
                    GroundTravel.build(logistics.facts.origin(), 2, logistics.baritone, logistics.outputs)),
                new Deferred("préparer-emplacement", () -> new PrepareLogisticsSite(
                    logistics.facts, logistics.world, logistics.baritone, logistics.outputs)),
                new Deferred("construire-sol-central", () -> new ProtectionBuilder(logistics.facts, logistics.memory,
                    logistics.world, logistics.baritone, logistics.outputs, true, logistics.profile, logistics.mission)),
                new Deferred("rejoindre-centre", () ->
                    GroundTravel.build(logistics.facts.origin(), 0, logistics.baritone, logistics.outputs)),
                new Deferred("construire-protection", () -> new ProtectionBuilder(logistics.facts, logistics.memory,
                    logistics.world, logistics.baritone, logistics.outputs, false, logistics.profile, logistics.mission)))));
    }
}
