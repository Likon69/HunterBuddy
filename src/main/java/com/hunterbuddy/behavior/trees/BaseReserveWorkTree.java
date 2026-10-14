package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Sequence;

public final class BaseReserveWorkTree {
    private BaseReserveWorkTree() {}

    public static Node build(BaseVisitContext visit) {
        return new Sequence("base-regear-through-reused-cycle", ContainerTree.close(visit.logistics.outputs),
            new InventoryMilestone("resume-or-start-reserve-cycle", ctx -> {
                if (visit.logistics.stored(LogisticsRoles.RESERVE)) {
                    visit.logistics.facts.reserve = new ShulkerCycleFacts();
                    visit.logistics.forget(LogisticsRoles.RESERVE);
                    visit.logistics.mission.equipment(ctx, visit.logistics.equipmentName(LogisticsRoles.RESERVE), "");
                    visit.save(ctx, "new regear needed, reserve identified in the ender chest");
                }
            }), ShulkerCycleTree.build(visit.logistics, LogisticsRoles.RESERVE, () -> ReserveWorkTree.build(visit.logistics)));
    }
}
