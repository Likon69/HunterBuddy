package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;

public final class MaterialWorkTree {
    private MaterialWorkTree() {}

    public static Node build(LogisticsContext logistics, String role) {
        Predicate<ItemStack> filter = role.equals(LogisticsRoles.MATERIAL)
            ? LogisticsInventory.accepted(logistics.profile) : LogisticsInventory.miscellaneous(logistics.profile);
        return new Sequence("fill-shulker-" + role, DepositLogisticsTree.build(logistics, role, filter),
            new InventoryMilestone("final-contents-verified-" + role, ctx -> {
                logistics.cycle(role).workedContents = logistics.cycle(role).signature;
                logistics.save(ctx, "contents confirmed " + role);
            }), ContainerTree.close(logistics.outputs));
    }
}
