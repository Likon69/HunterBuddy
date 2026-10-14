package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.farmer.TransferPurposes;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ReserveInventory;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.util.GliderSwap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class ReserveWorkTree {
    private ReserveWorkTree() {}

    public static Node build(LogisticsContext logistics) {
        ReserveInventory reserve = new ReserveInventory(logistics);
        return new Sequence("regear-under-protection",
            new InventoryMilestone("refresh-armor-binding-state", ctx -> logistics.facts.armorBlocked = false),
            DepositLogisticsTree.build(logistics, LogisticsRoles.RESERVE, reserve::beforeReplacement),
            ContainerTree.close(logistics.outputs), EquipReserveArmorTree.build(logistics),
            DepositLogisticsTree.build(logistics, LogisticsRoles.RESERVE, reserve::used),
            new RepeatUntil("take-equip-and-recheck-reserve-needs", () -> reserve.neededSlot() < 0 || !reserve.room(), 72,
                new Sequence("replace-one-reserve-item",
                    LogisticsOperations.transfer(logistics, LogisticsRoles.RESERVE, true, TransferPurposes.WORK, () -> {
                        int slot = reserve.neededSlot();
                        if (slot < 0) throw new IllegalStateException("reserve equipment source changed");
                        ItemStack expected = logistics.outputs.inventory().containerContents().get(slot).copy();
                        return logistics.outputs.inventory().takeStack(slot, expected);
                    }), ContainerTree.close(logistics.outputs), EquipReserveArmorTree.build(logistics),
                    DepositLogisticsTree.build(logistics, LogisticsRoles.RESERVE, reserve::used))),
            DepositLogisticsTree.build(logistics, LogisticsRoles.RESERVE, reserve::used),
            new InventoryMilestone("reserve-verified", ctx -> {
                List<ItemStack> contents = logistics.outputs.inventory().containerContents();
                List<String> missing = new ArrayList<>(LogisticsInventory.missing(logistics.world, logistics.profile, contents));
                if (FlightPlan.totalDurability(logistics.world) < logistics.requiredFlightWear
                    && contents.stream().noneMatch(stack -> GliderSwap.isGlider(stack) && FlightPlan.usableDurability(stack) > 0)) {
                    missing.add("usable elytra durability");
                }
                boolean blocked = !reserve.room() && reserve.neededSlot() >= 0
                    || logistics.world.countMatching(reserve::used) > 0 || logistics.facts.armorBlocked;
                String status = !missing.isEmpty() ? "reserve incomplete: " + String.join(", ", missing)
                    : blocked ? "reserve available but inventory space or armor exchange is blocked" : "ready";
                logistics.facts.baseRequested = blocked || !missing.isEmpty();
                if (logistics.facts.baseRequested) logistics.facts.incomplete = status;
                else logistics.facts.incomplete = "";
                logistics.mission.equipment(ctx, "reserve-status", status);
                logistics.facts.reserve.workedContents = logistics.facts.reserve.signature;
                logistics.save(ctx, "regear confirmed: " + status);
            }), ContainerTree.close(logistics.outputs));
    }
}
