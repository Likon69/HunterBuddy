package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ReserveInventory;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class ReserveWorkTree {
    private ReserveWorkTree() {}

    public static Node build(LogisticsContext logistics) {
        ReserveInventory reserve = new ReserveInventory(logistics);
        return new Sequence("regear-sous-protection",
            DepositLogisticsTree.build(logistics, "reserve", reserve::beforeReplacement),
            new RepeatUntil("prendre-équipement-disponible", () -> reserve.neededSlot() < 0, 72,
                LogisticsOperations.transfer(logistics, "reserve", true, "work", () -> {
                    int slot = reserve.neededSlot();
                    if (slot < 0) throw new IllegalStateException("reserve equipment source changed");
                    ItemStack expected = logistics.outputs.inventory().containerContents().get(slot).copy();
                    return logistics.outputs.inventory().takeStack(slot, expected);
                })),
            ContainerTree.close(logistics.outputs), EquipReserveArmorTree.build(logistics),
            DepositLogisticsTree.build(logistics, "reserve", reserve::used),
            new InventoryMilestone("réserve-vérifiée", ctx -> {
                List<String> missing = new ArrayList<>(LogisticsInventory.missing(logistics.world, logistics.profile, List.of()));
                if (logistics.world.countMatching(reserve::used) > 0) {
                    missing.add("réserve pleine pour les équipements usés");
                }
                if (logistics.facts.incomplete.startsWith("armure liée")) missing.add(logistics.facts.incomplete);
                String status = missing.isEmpty() ? "ready" : "réserve incomplète: " + String.join(", ", missing);
                if (!missing.isEmpty()) logistics.facts.incomplete = status;
                logistics.mission.equipment(ctx, "reserve-status", status);
                logistics.facts.reserve.workedContents = logistics.facts.reserve.signature;
                logistics.save(ctx, "regear confirmé: " + status);
            }), ContainerTree.close(logistics.outputs));
    }
}
