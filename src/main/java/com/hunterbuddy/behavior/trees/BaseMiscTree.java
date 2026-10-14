package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.farmer.TransferPurposes;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class BaseMiscTree {
    private BaseMiscTree() {}

    public static Node build(BaseVisitContext visit) {
        return new Sequence("empty-miscellaneous-shulker-on-return", BaseOperations.ender(visit),
            LogisticsOperations.unless("miscellaneous absent or empty", () -> empty(visit),
                new Sequence("base-miscellaneous-cycle", ContainerTree.close(visit.logistics.outputs),
                    new InventoryMilestone("start-or-resume-miscellaneous-unloading", ctx -> {
                        if (visit.logistics.stored(LogisticsRoles.MISCELLANEOUS)) {
                            visit.logistics.facts.divers = new ShulkerCycleFacts();
                            visit.logistics.forget(LogisticsRoles.MISCELLANEOUS);
                            visit.logistics.mission.equipment(ctx, visit.logistics.equipmentName(LogisticsRoles.MISCELLANEOUS), "");
                            visit.save(ctx, "miscellaneous unloading cycle reset after packing");
                        }
                    }),
                    ShulkerCycleTree.build(visit.logistics, LogisticsRoles.MISCELLANEOUS, () -> unload(visit), false))),
            ContainerTree.close(visit.logistics.outputs),
            new InventoryMilestone("miscellaneous-emptied-confirmed", ctx -> visit.save(ctx, "miscellaneous emptied into the returns chest")));
    }

    private static boolean empty(BaseVisitContext visit) {
        if (!visit.logistics.facts.divers.signature.isEmpty() && !visit.logistics.stored(LogisticsRoles.MISCELLANEOUS)) {
            return false;
        }
        List<ItemStack> contents = visit.enderContents();
        for (int i = 0; i < contents.size(); i++) {
            if (visit.roleSlot(i, LogisticsRoles.MISCELLANEOUS)) {
                return ShulkerSignature.of(contents.get(i), visit.logistics.world).empty();
            }
        }
        for (ItemStack stack : contents) {
            if (!ShulkerSignature.isShulker(stack)) continue;
            ShulkerSignature signature = ShulkerSignature.of(stack, visit.logistics.world);
            if (!signature.empty() && !LogisticsInventory.pure(signature, visit.logistics.profile)
                && !LogisticsInventory.reserve(signature)) {
                throw new IllegalStateException("miscellaneous shulker unidentified: verify its role before visiting");
            }
        }
        return true;
    }

    private static Node unload(BaseVisitContext visit) {
        return new Sequence("transfer-miscellaneous-contents",
            LogisticsOperations.open(visit.logistics, LogisticsRoles.MISCELLANEOUS, true),
            new RepeatUntil("empty-miscellaneous-shulker-confirmed", () -> visit.facts.cargo.isEmpty()
                && visit.logistics.outputs.inventory().containerContents().stream().allMatch(ItemStack::isEmpty), 27,
                new Sequence("transfer-one-miscellaneous-stack",
                    BaseCargoTree.take(visit, () -> visit.logistics.facts.shulker(), LogisticsRoles.MISCELLANEOUS_CONTENTS, stack -> !stack.isEmpty()),
                    BaseCargoTree.send(visit, () -> visit.position(visit.facts.returns), TransferPurposes.RETURN_MISCELLANEOUS),
                    LogisticsOperations.open(visit.logistics, LogisticsRoles.MISCELLANEOUS, true))),
            new InventoryMilestone("empty-miscellaneous-signature", ctx -> {
                visit.logistics.facts.divers.workedContents = visit.logistics.facts.divers.signature;
                visit.facts.miscEmptied = visit.logistics.facts.divers.signature;
                visit.save(ctx, "miscellaneous emptied, signature confirmed");
            }), ContainerTree.close(visit.logistics.outputs));
    }
}
