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
            nodes.add(LogisticsOperations.unless("armor worn " + slot.getName(), () -> {
                ItemStack worn = logistics.world.client().player.getEquippedStack(slot);
                return reserve.freshArmor(worn, slot)
                    || logistics.world.countMatching(stack -> reserve.freshArmor(stack, slot)) == 0;
            }, new Deferred("equip-" + slot.getName(), () -> {
                ItemStack worn = logistics.world.client().player.getEquippedStack(slot);
                if (!logistics.world.client().player.isCreative() && EnchantmentHelper.hasAnyEnchantmentsWith(
                    worn, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) {
                    return new InventoryMilestone("bound-armor", ctx -> {
                        logistics.facts.armorBlocked = true;
                        logistics.facts.incomplete = "armor bound by Curse of Binding: " + slot.getName();
                        logistics.save(ctx, logistics.facts.incomplete);
                    });
                }
                return new Timeout(600, new Sequence("equip-fresh-armor",
                    LogisticsOperations.prepare(logistics, stack -> reserve.freshArmor(stack, slot), 8),
                    new WithLease(Set.of(Resource.SLOT, Resource.INTERACT), LeasePolicy.WAIT,
                        new EquipArmor(slot, logistics.world, logistics.outputs)),
                    new InventoryMilestone("worn-armor-confirmed", ctx ->
                        logistics.save(ctx, "armor worn " + slot.getName()))));
            })));
        }
        return new Sequence("equip-armor", nodes.toArray(Node[]::new));
    }
}
