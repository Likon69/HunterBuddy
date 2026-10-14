package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.FarmerMissionContext;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Sequence;

public final class FarmerBaseTree {
    private FarmerBaseTree() {}

    public static Node build(FarmerMissionContext mission) {
        BaseVisitContext visit = new BaseVisitContext(mission.step, mission.profile, () -> mission.zone,
            mission.settings, mission.world, mission.baritone, mission.outputs, mission.journal);
        return new Sequence("deliver-and-restock-in-one-visit",
            BaseVisitTree.build(visit),
            new InventoryMilestone("base-return-confirmed", ctx -> {
                mission.tick = ctx;
                mission.facts.baseRequested = false;
                if (mission.facts.waitAtBase) {
                    mission.facts.waitUntil = System.currentTimeMillis() + mission.settings.playerWaitMillis();
                }
                mission.journal.equipment(ctx, "logistics-facts", "");
                mission.save(ctx, "single visit completed: deliveries and stocks confirmed");
            }));
    }
}
