package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import net.minecraft.item.ItemStack;

public final class BaseInventorySpaceTree {
    private BaseInventorySpaceTree() {}

    public static Node build(BaseVisitContext visit) {
        return LogisticsOperations.unless("three inventory slots already free", () -> freeSlots(visit) >= 3,
            new Sequence("deposit-loose-material-before-base-packing",
                BaseOperations.open(visit, () -> visit.position(visit.facts.returns)),
                new RepeatUntil("three-free-slots-confirmed", () -> freeSlots(visit) >= 3, 72,
                    BaseOperations.transfer(visit, () -> visit.position(visit.facts.returns), "unload-loose-material",
                        () -> visit.logistics.outputs.inventory().firstDeposit(
                            LogisticsInventory.accepted(visit.logistics.profile)))),
                ContainerTree.close(visit.logistics.outputs), BaseOperations.ender(visit)));
    }

    private static long freeSlots(BaseVisitContext visit) {
        return LogisticsInventory.player(visit.logistics.world).stream().filter(ItemStack::isEmpty).count();
    }
}
