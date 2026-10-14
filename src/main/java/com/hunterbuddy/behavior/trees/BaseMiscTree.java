package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
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
        return new Sequence("vider-shulker-divers-au-retour", BaseOperations.ender(visit),
            LogisticsOperations.unless("divers absent ou vide", () -> empty(visit),
                new Sequence("cycle-divers-base", ContainerTree.close(visit.logistics.outputs),
                    new InventoryMilestone("start-or-resume-miscellaneous-unloading", ctx -> {
                        if (visit.logistics.stored("divers")) {
                            visit.logistics.facts.divers = new ShulkerCycleFacts();
                            visit.logistics.forget("divers");
                            visit.logistics.mission.equipment(ctx, visit.logistics.equipmentName("divers"), "");
                            visit.save(ctx, "miscellaneous unloading cycle reset after packing");
                        }
                    }),
                    ShulkerCycleTree.build(visit.logistics, "divers", () -> unload(visit), false))),
            ContainerTree.close(visit.logistics.outputs),
            new InventoryMilestone("divers-vidés-confirmés", ctx -> visit.save(ctx, "divers vidés au coffre retour")));
    }

    private static boolean empty(BaseVisitContext visit) {
        if (!visit.logistics.facts.divers.signature.isEmpty() && !visit.logistics.stored("divers")) {
            return false;
        }
        List<ItemStack> contents = visit.enderContents();
        for (int i = 0; i < contents.size(); i++) {
            if (visit.roleSlot(i, "divers")) {
                return ShulkerSignature.of(contents.get(i), visit.logistics.world).empty();
            }
        }
        for (ItemStack stack : contents) {
            if (!ShulkerSignature.isShulker(stack)) continue;
            ShulkerSignature signature = ShulkerSignature.of(stack, visit.logistics.world);
            if (!signature.empty() && !LogisticsInventory.pure(signature, visit.logistics.profile)
                && !LogisticsInventory.reserve(signature)) {
                throw new IllegalStateException("shulker divers non identifiée : vérifier son rôle avant la visite");
            }
        }
        return true;
    }

    private static Node unload(BaseVisitContext visit) {
        return new Sequence("transférer-contenu-divers",
            LogisticsOperations.open(visit.logistics, "divers", true),
            new RepeatUntil("shulker-divers-vide-confirmée", () -> visit.facts.cargo.isEmpty()
                && visit.logistics.outputs.inventory().containerContents().stream().allMatch(ItemStack::isEmpty), 27,
                new Sequence("transférer-une-pile-divers",
                    BaseCargoTree.take(visit, () -> visit.logistics.facts.shulker(), "divers-contenu", stack -> !stack.isEmpty()),
                    BaseCargoTree.send(visit, () -> visit.position(visit.facts.returns), "retour-divers"),
                    LogisticsOperations.open(visit.logistics, "divers", true))),
            new InventoryMilestone("signature-divers-vide", ctx -> {
                visit.logistics.facts.divers.workedContents = visit.logistics.facts.divers.signature;
                visit.facts.miscEmptied = visit.logistics.facts.divers.signature;
                visit.save(ctx, "divers vidés, signature confirmée");
            }), ContainerTree.close(visit.logistics.outputs));
    }
}
