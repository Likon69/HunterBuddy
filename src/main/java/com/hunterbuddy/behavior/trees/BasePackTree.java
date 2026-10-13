package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatShulkerCycle;
import com.hunterbuddy.behavior.nodes.Sequence;
import net.minecraft.item.ItemStack;

public final class BasePackTree {
    private BasePackTree() {}

    public static Node resumeMaterial(BaseVisitContext visit) {
        return LogisticsOperations.unless("aucune shulker matériau de base interrompue",
            () -> visit.logistics.facts.material.signature.isEmpty() || visit.logistics.stored("material"),
            ShulkerCycleTree.build(visit.logistics, "material", () -> MaterialWorkTree.build(visit.logistics, "material")));
    }

    public static Node build(BaseVisitContext visit) {
        return new Sequence("ranger-les-objets-ramenés-sans-abri-de-terrain",
            LogisticsOperations.unless("no loose accepted material",
                () -> visit.logistics.world.countMatching(LogisticsInventory.accepted(visit.logistics.profile)) == 0,
                cycle(visit, "material")),
            LogisticsOperations.unless("aucun objet divers ramené",
                () -> visit.logistics.world.countMatching(LogisticsInventory.miscellaneous(visit.logistics.profile)) == 0,
                cycle(visit, "divers")), ContainerTree.close(visit.logistics.outputs),
            new InventoryMilestone("objets-ramenés-rangés-confirmés", ctx -> {
                if (visit.logistics.world.countMatching(LogisticsInventory.accepted(visit.logistics.profile)) > 0) {
                    throw new OperationFailure(FailureCode.MISSION_STOP, "base has no empty material shulker for the returned items: "
                        + visit.logistics.facts.material.unavailable);
                }
                if (visit.logistics.world.countMatching(LogisticsInventory.miscellaneous(visit.logistics.profile)) > 0) {
                    throw new OperationFailure(FailureCode.NO_OPTION, "miscellaneous shulker has no confirmed capacity for returned items: "
                        + visit.logistics.facts.divers.unavailable);
                }
                visit.save(ctx, "objets ramenés rangés par transferts confirmés");
            }));
    }

    private static Node cycle(BaseVisitContext visit, String role) {
        return new RepeatShulkerCycle(visit.logistics, role, new Sequence("pack-with-empty-shulker-supply",
            BaseOperations.ender(visit),
            new InventoryMilestone("retry-packing-with-observed-stock", ctx -> {
                if (visit.logistics.cycle(role).signature.isEmpty()) {
                    visit.logistics.cycle(role).unavailable = "";
                }
            }),
            LogisticsOperations.unless("packing shulker capacity available", () -> capacity(visit, role),
                BaseEmptyShulkersTree.loadOne(visit)),
            ShulkerCycleTree.build(visit.logistics, role, () -> MaterialWorkTree.build(visit.logistics, role))));
    }

    private static boolean capacity(BaseVisitContext visit, String role) {
        for (int i = 0; i < visit.enderContents().size(); i++) {
            ItemStack stack = visit.enderContents().get(i);
            if (!ShulkerSignature.isShulker(stack) || visit.roleSlot(i, "reserve")) continue;
            if (role.equals("material") && visit.roleSlot(i, "divers")) continue;
            ShulkerSignature signature = ShulkerSignature.of(stack, visit.logistics.world);
            if (signature.empty() || (role.equals("material") ? LogisticsInventory.pure(signature, visit.logistics.profile)
                : visit.roleSlot(i, "divers")) && signature.contents().stream()
                .anyMatch(item -> item.isEmpty() || item.getCount() < item.getMaxCount())) return true;
        }
        return false;
    }
}
