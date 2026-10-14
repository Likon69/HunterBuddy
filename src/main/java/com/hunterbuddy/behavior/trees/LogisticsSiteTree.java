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
        return LogisticsOperations.unless("selected-site-valid-and-protected", logistics::protectedSite,
            new RetryLogisticsSite(logistics, new Sequence("choose-and-protect-location",
                new ChooseLogisticsSite(logistics), LogisticsOperations.prepare(logistics, InventoryLayout::silkPickaxe, 1),
                new Deferred("reach-location", () ->
                    logistics.facts.reusedShelter ? ShelterEntryTree.build(logistics)
                        : GroundTravel.build(logistics.facts.origin(), 2, logistics.baritone, logistics.outputs)),
                LogisticsOperations.unless("reuse-existing-shelter-space", () -> logistics.facts.reusedShelter,
                    new Deferred("prepare-location", () -> new PrepareLogisticsSite(
                        logistics.facts, logistics.world, logistics.baritone, logistics.outputs))),
                new Deferred("build-central-floor", () -> new ProtectionBuilder(logistics.facts, logistics.memory,
                    logistics.world, logistics.baritone, logistics.outputs, true, logistics.profile, logistics.mission)),
                new Deferred("reach-center", () ->
                    GroundTravel.build(logistics.facts.origin(), 0, logistics.baritone, logistics.outputs)),
                new Deferred("build-shelter", () -> new ProtectionBuilder(logistics.facts, logistics.memory,
                    logistics.world, logistics.baritone, logistics.outputs, false, logistics.profile, logistics.mission)))));
    }
}
