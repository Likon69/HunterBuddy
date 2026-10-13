package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Sequence;

public final class BaseReserveWorkTree {
    private BaseReserveWorkTree() {}

    public static Node build(BaseVisitContext visit) {
        return new Sequence("regear-base-par-cycle-réutilisé", ContainerTree.close(visit.logistics.outputs),
            new InventoryMilestone("reprendre-ou-commencer-cycle-réserve", ctx -> {
                if (visit.logistics.stored("reserve")) {
                    visit.logistics.facts.reserve = new ShulkerCycleFacts();
                    visit.logistics.forget("reserve");
                    visit.logistics.mission.equipment(ctx, visit.logistics.equipmentName("reserve"), "");
                    visit.save(ctx, "nouveau besoin de regear, réserve identifiée dans l'ender chest");
                }
            }), ShulkerCycleTree.build(visit.logistics, "reserve", () -> ReserveWorkTree.build(visit.logistics)));
    }
}
