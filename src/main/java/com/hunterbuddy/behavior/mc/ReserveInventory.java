package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.util.GliderSwap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public final class ReserveInventory {
    public static final List<EquipmentSlot> ARMOR = List.of(
        EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
    private final LogisticsContext logistics;

    public ReserveInventory(LogisticsContext logistics) {
        this.logistics = logistics;
    }

    public boolean freshArmor(ItemStack stack, EquipmentSlot slot) {
        return LogisticsInventory.armorSlot(stack) == slot && !stack.isEmpty()
            && stack.getDamage() == 0 && !GliderSwap.isGlider(stack);
    }

    public boolean used(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (GliderSwap.isGlider(stack)) {
            return LogisticsInventory.remaining(stack) <= 0.05
                && logistics.world.countMatching(item -> GliderSwap.isGlider(item) && LogisticsInventory.healthy(item)) > 0;
        }
        if (LogisticsInventory.anyTool(stack) && LogisticsInventory.remaining(stack) <= 0.05) {
            if (InventoryLayout.silkPickaxe(stack)) {
                return logistics.world.countMatching(item -> InventoryLayout.silkPickaxe(item)
                    && LogisticsInventory.healthy(item)) > 0;
            }
            return logistics.world.countMatching(item -> LogisticsInventory.tool(item, logistics.profile)
                && LogisticsInventory.healthy(item)) > 0;
        }
        EquipmentSlot slot = LogisticsInventory.armorSlot(stack);
        if (slot == null || stack.getDamage() == 0) return false;
        ItemStack worn = logistics.world.client().player.getEquippedStack(slot);
        return freshArmor(worn, slot) || logistics.world.countMatching(item -> freshArmor(item, slot)) > 0;
    }

    public boolean beforeReplacement(ItemStack stack) {
        if (stack.isEmpty() || InventoryLayout.silkPickaxe(stack) || GliderSwap.isGlider(stack)) return false;
        List<ItemStack> contents = ShulkerSignature.decode(logistics.facts.reserve.signature, logistics.world).contents();
        if (LogisticsInventory.anyTool(stack) && LogisticsInventory.remaining(stack) <= 0.05) {
            return contents.stream().anyMatch(item -> LogisticsInventory.tool(item, logistics.profile)
                && LogisticsInventory.healthy(item));
        }
        EquipmentSlot slot = LogisticsInventory.armorSlot(stack);
        return slot != null && stack.getDamage() > 0 && contents.stream().anyMatch(item -> freshArmor(item, slot));
    }

    public boolean room() {
        return LogisticsInventory.player(logistics.world).stream().filter(ItemStack::isEmpty).count() >= 2;
    }

    public int neededSlot() {
        List<ItemStack> contents = logistics.outputs.inventory().containerContents();
        List<Predicate<ItemStack>> needs = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR) {
            ItemStack worn = logistics.world.client().player.getEquippedStack(slot);
            if ((worn.isEmpty() || worn.getDamage() > 0 || GliderSwap.isGlider(worn))
                && logistics.world.countMatching(stack -> freshArmor(stack, slot)) == 0) {
                needs.add(stack -> freshArmor(stack, slot));
            }
        }

        if (LogisticsInventory.profileTools(logistics.world, logistics.profile) == 0) {
            needs.add(stack -> LogisticsInventory.tool(stack, logistics.profile) && LogisticsInventory.healthy(stack));
        }
        if (logistics.world.countMatching(stack -> InventoryLayout.silkPickaxe(stack) && LogisticsInventory.healthy(stack)) == 0) {
            needs.add(stack -> InventoryLayout.silkPickaxe(stack) && LogisticsInventory.healthy(stack));
        }
        if (logistics.world.countMatching(stack -> stack.contains(DataComponentTypes.FOOD)) < 16) {
            needs.add(stack -> stack.contains(DataComponentTypes.FOOD));
        }
        if (logistics.world.countMatching(GliderSwap::isGlider) > 0
            && logistics.world.countMatching(stack -> GliderSwap.isGlider(stack) && LogisticsInventory.healthy(stack)) == 0) {
            needs.add(stack -> GliderSwap.isGlider(stack) && LogisticsInventory.healthy(stack));
        }
        int carriedWear = FlightPlan.totalDurability(logistics.world);
        if (carriedWear < logistics.requiredFlightWear) {
            needs.add(stack -> GliderSwap.isGlider(stack) && FlightPlan.remaining(stack) > 16);
        }
        for (Predicate<ItemStack> need : needs) {
            for (int i = 0; i < contents.size(); i++) if (need.test(contents.get(i))) return i;
        }
        return -1;
    }
}
