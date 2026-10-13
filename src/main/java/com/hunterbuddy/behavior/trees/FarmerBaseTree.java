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
        return new Sequence("livrer-et-ravitailler-en-une-visite",
            BaseVisitTree.build(visit),
            new InventoryMilestone("retour-de-base-confirmé", ctx -> {
                mission.facts.baseRequested = false;
                if (mission.facts.waitAtBase) {
                    mission.facts.waitUntil = System.currentTimeMillis() + mission.settings.playerWaitTicks() * 50L;
                }
                mission.journal.equipment(ctx, "logistics-facts", "");
                mission.save(ctx, "visite unique terminée : livraisons et stocks confirmés");
            }));
    }
}
