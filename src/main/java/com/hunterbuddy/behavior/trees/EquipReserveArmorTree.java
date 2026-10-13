package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.ReserveInventory;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.EquipArmor;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public final class EquipReserveArmorTree {
    private EquipReserveArmorTree() {}

    public static Node build(LogisticsContext logistics) {
        ReserveInventory reserve = new ReserveInventory(logistics);
        List<Node> nodes = new ArrayList<>();
        for (EquipmentSlot slot : ReserveInventory.ARMOR) {
            nodes.add(LogisticsOperations.unless("armure portée " + slot.getName(), () -> {
                ItemStack worn = logistics.world.client().player.getEquippedStack(slot);
                return reserve.freshArmor(worn, slot)
                    || logistics.world.countMatching(stack -> reserve.freshArmor(stack, slot)) == 0;
            }, new Deferred("équiper-" + slot.getName(), () -> {
                ItemStack worn = logistics.world.client().player.getEquippedStack(slot);
                if (!logistics.world.client().player.isCreative() && EnchantmentHelper.hasAnyEnchantmentsWith(
                    worn, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) {
                    return new InventoryMilestone("armure-liée", ctx -> {
                        logistics.facts.incomplete = "armure liée " + slot.getName();
                        logistics.save(ctx, logistics.facts.incomplete);
                    });
                }
                return new Timeout(600, new Sequence("équiper-armure-fraîche",
                    LogisticsOperations.prepare(logistics, stack -> reserve.freshArmor(stack, slot), 8),
                    new WithLease(Set.of(Resource.SLOT, Resource.INTERACT), LeasePolicy.WAIT,
                        new EquipArmor(slot, logistics.world, logistics.outputs)),
                    new InventoryMilestone("armure-portée-confirmée", ctx ->
                        logistics.save(ctx, "armure portée " + slot.getName()))));
            })));
        }
        return new Sequence("équiper-armures", nodes.toArray(Node[]::new));
    }
}
