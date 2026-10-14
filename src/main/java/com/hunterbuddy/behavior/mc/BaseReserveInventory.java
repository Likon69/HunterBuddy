package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.util.GliderSwap;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

public final class BaseReserveInventory {
    private final BaseVisitContext visit;

    public BaseReserveInventory(BaseVisitContext visit) {
        this.visit = visit;
    }

    public boolean prepared(ItemStack stack) {
        if (!ShulkerSignature.isShulker(stack)) {
            return false;
        }
        List<ItemStack> contents = ShulkerSignature.of(stack, visit.logistics.world).contents();
        boolean pickaxe = !visit.logistics.profile.tool().equals("shovel") && !visit.logistics.profile.tool().equals("axe");
        long tools = contents.stream().filter(item -> LogisticsInventory.tool(item, visit.logistics.profile)
            && LogisticsInventory.remaining(item) >= 0.25).count();
        return tools >= (pickaxe ? 2 : 1)
            && contents.stream().anyMatch(item -> InventoryLayout.silkPickaxe(item) && LogisticsInventory.remaining(item) >= 0.25)
            && contents.stream().filter(item -> item.contains(DataComponentTypes.FOOD)).mapToInt(ItemStack::getCount).sum() >= 16
            && contents.stream().anyMatch(item -> GliderSwap.isGlider(item) && LogisticsInventory.remaining(item) >= 0.25
                && FlightPlan.remaining(item) > 16);
    }

    public int carriedWear() {
        int wear = 0;
        for (int i = 0; i < visit.logistics.world.client().player.getInventory().size(); i++) {
            ItemStack stack = visit.logistics.world.client().player.getInventory().getStack(i);
            if (GliderSwap.isGlider(stack) && stack.getCount() == 1) wear += FlightPlan.usableDurability(stack);
        }
        return wear;
    }

    public boolean canRegear() {
        int slot = slot();
        if (slot < 0) {
            return false;
        }
        List<ItemStack> contents = ShulkerSignature.of(visit.enderContents().get(slot), visit.logistics.world).contents();
        int spareWear = contents.stream().filter(item -> GliderSwap.isGlider(item) && item.getCount() == 1)
            .mapToInt(FlightPlan::usableDurability).sum();
        return (carriedWear() >= visit.facts.wearBudget || spareWear > 0)
            && LogisticsInventory.missing(visit.logistics.world, visit.logistics.profile, contents).isEmpty();
    }

    public int slot() {
        for (int i = 0; i < visit.enderContents().size(); i++) {
            if (visit.roleSlot(i, LogisticsRoles.RESERVE)) {
                return i;
            }
        }
        return -1;
    }

    public boolean ready() {
        int slot = slot();
        return slot >= 0 && prepared(visit.enderContents().get(slot));
    }
}
