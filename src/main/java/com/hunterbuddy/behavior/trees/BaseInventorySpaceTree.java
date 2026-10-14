package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import net.minecraft.item.ItemStack;

public final class BaseInventorySpaceTree {
    private BaseInventorySpaceTree() {}

    public static Node build(BaseVisitContext visit) {
        return LogisticsOperations.unless("three inventory slots already free", () -> freeSlots(visit) >= 3,
            new Sequence("free-player-slots-before-base-packing",
                new RepeatUntil("three-free-slots-confirmed", () -> freeSlots(visit) >= 3, 72,
                    new Deferred("deposit-carried-material-or-miscellaneous", () -> {
                        boolean miscellaneous = visit.logistics.world.countMatching(LogisticsInventory.miscellaneous(visit.logistics.profile)) > 0;
                        return new Sequence("deposit-one-loose-stack",
                            BaseOperations.open(visit, () -> visit.position(visit.facts.returns)),
                            BaseOperations.transfer(visit, () -> visit.position(visit.facts.returns),
                                "free-inventory-slot", () -> visit.logistics.outputs.inventory().firstDeposit(miscellaneous
                                    ? LogisticsInventory.miscellaneous(visit.logistics.profile)
                                    : LogisticsInventory.accepted(visit.logistics.profile), FailureCode.MISSION_STOP)));
                    })), ContainerTree.close(visit.logistics.outputs), BaseOperations.ender(visit)));
    }

    private static long freeSlots(BaseVisitContext visit) {
        return LogisticsInventory.player(visit.logistics.world).stream().filter(ItemStack::isEmpty).count();
    }
}
