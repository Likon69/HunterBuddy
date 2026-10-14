package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.farmer.TransferPurposes;
import com.hunterbuddy.behavior.mc.BaseStockPlan;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import net.minecraft.item.ItemStack;

public final class BaseEmptyShulkersTree {
    private BaseEmptyShulkersTree() {}

    public static Node loadOne(BaseVisitContext visit) {
        return new Sequence("load-empty-shulker-for-base-packing",
            BaseCargoTree.take(visit, () -> visit.position(visit.facts.empties), LogisticsRoles.EMPTIES, stack -> empty(visit, stack)),
            BaseCargoTree.send(visit, () -> visit.logistics.facts.chest(), TransferPurposes.STORE_EMPTIES),
            BaseOperations.ender(visit));
    }

    public static Node build(BaseVisitContext visit) {
        return new Sequence("load-planned-empties", BaseOperations.ender(visit),
            LogisticsOperations.unless("miscellaneous role present", () -> {
                for (int i = 0; i < visit.enderContents().size(); i++) {
                    if (visit.roleSlot(i, LogisticsRoles.MISCELLANEOUS)) {
                        return true;
                    }
                }
                return false;
            }, new Deferred("assign-miscellaneous-by-signature", () -> {
                int slot = visit.emptySlot();
                if (slot < 0) {
                    return new Sequence("prepare-miscellaneous-shulker",
                        BaseCargoTree.take(visit, () -> visit.position(visit.facts.empties),
                            LogisticsRoles.EMPTY_MISCELLANEOUS, stack -> empty(visit, stack)),
                        BaseCargoTree.send(visit, () -> visit.logistics.facts.chest(), TransferPurposes.STORE_MISCELLANEOUS), BaseOperations.ender(visit));
                }
                return new InventoryMilestone("assign-empty-to-miscellaneous-role", ctx -> {
                    ShulkerSignature signature = ShulkerSignature.of(visit.enderContents().get(slot), visit.logistics.world);
                    visit.logistics.mission.equipment(ctx, "logistics-miscellaneous", signature.encoded());
                    visit.logistics.mission.equipment(ctx, "logistics-miscellaneous-slot", Integer.toString(slot));
                    visit.save(ctx, "empty miscellaneous shulker identified in the ender chest");
                });
            })),
            new InventoryMilestone("plan-remaining-empties", ctx -> BaseStockPlan.empties(ctx, visit)),
            new RepeatUntil("no-unnecessary-empty", () -> visit.emptyCount() <= visit.facts.emptyTarget, 27,
                new Deferred("return-one-excess-empty", () -> {
                    int slot = visit.emptySlot();
                    ShulkerSignature signature = ShulkerSignature.of(visit.enderContents().get(slot), visit.logistics.world);
                    return new Sequence("return-excess-empty",
                        BaseCargoTree.takeAt(visit, () -> visit.logistics.facts.chest(),
                            LogisticsRoles.EXCESS_EMPTIES, () -> slot, signature::matches),
                        BaseCargoTree.send(visit, () -> visit.position(visit.facts.empties), TransferPurposes.RETURN_EMPTIES), BaseOperations.ender(visit));
                })),
            new RepeatUntil("enough-planned-empties", () -> visit.emptyCount() >= visit.facts.emptyTarget, 25,
                new Sequence("load-one-empty",
                    BaseCargoTree.take(visit, () -> visit.position(visit.facts.empties), LogisticsRoles.EMPTIES, stack -> empty(visit, stack)),
                    BaseCargoTree.send(visit, () -> visit.logistics.facts.chest(), TransferPurposes.STORE_EMPTIES), BaseOperations.ender(visit))),
            new InventoryMilestone("empties-confirmed", ctx -> visit.save(ctx, "planned empties present in the ender chest")),
            ContainerTree.close(visit.logistics.outputs));
    }

    private static boolean empty(BaseVisitContext visit, ItemStack stack) {
        return ShulkerSignature.isShulker(stack) && ShulkerSignature.of(stack, visit.logistics.world).empty();
    }
}
