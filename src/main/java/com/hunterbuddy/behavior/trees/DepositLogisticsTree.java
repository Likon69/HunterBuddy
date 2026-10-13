package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;

public final class DepositLogisticsTree {
    private DepositLogisticsTree() {}

    public static Node build(LogisticsContext logistics, String role, Predicate<ItemStack> filter) {
        return new Sequence("déposer-" + role,
            LogisticsOperations.open(logistics, role, true), MoveLogisticsOffhandTree.build(logistics, role, filter),
            LogisticsOperations.open(logistics, role, true),
            new RepeatUntil("transferts-confirmés-" + role,
                () -> !logistics.outputs.inventory().hasMatchingItem(filter)
                    || !logistics.outputs.inventory().containerCapacity(filter), 72,
                LogisticsOperations.transfer(logistics, role, true, "work",
                    () -> logistics.outputs.inventory().firstDeposit(filter))));
    }
}
