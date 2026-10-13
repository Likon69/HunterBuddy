package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
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
            BaseCargoTree.take(visit, () -> visit.position(visit.facts.empties), "vides", stack -> empty(visit, stack)),
            BaseCargoTree.send(visit, () -> visit.logistics.facts.chest(), "ranger-vides"),
            BaseOperations.ender(visit));
    }

    public static Node build(BaseVisitContext visit) {
        return new Sequence("charger-vides-prévues", BaseOperations.ender(visit),
            LogisticsOperations.unless("rôle divers présent", () -> {
                for (int i = 0; i < visit.enderContents().size(); i++) {
                    if (visit.roleSlot(i, "divers")) {
                        return true;
                    }
                }
                return false;
            }, new Deferred("affecter-divers-par-signature", () -> {
                int slot = visit.emptySlot();
                if (slot < 0) {
                    return new Sequence("préparer-shulker-divers",
                        BaseCargoTree.take(visit, () -> visit.position(visit.facts.empties),
                            "divers-vide", stack -> empty(visit, stack)),
                        BaseCargoTree.send(visit, () -> visit.logistics.facts.chest(), "ranger-divers"), BaseOperations.ender(visit));
                }
                return new InventoryMilestone("affecter-vide-au-rôle-divers", ctx -> {
                    ShulkerSignature signature = ShulkerSignature.of(visit.enderContents().get(slot), visit.logistics.world);
                    visit.logistics.mission.equipment(ctx, "logistics-divers", signature.encoded());
                    visit.logistics.mission.equipment(ctx, "logistics-divers-slot", Integer.toString(slot));
                    visit.save(ctx, "shulker divers vide identifiée dans l'ender chest");
                });
            })),
            new InventoryMilestone("planifier-vides-restantes", ctx -> BaseStockPlan.empties(ctx, visit)),
            new RepeatUntil("aucune-vide-inutile", () -> visit.emptyCount() <= visit.facts.emptyTarget, 27,
                new Deferred("retourner-une-vide-excédentaire", () -> {
                    int slot = visit.emptySlot();
                    ShulkerSignature signature = ShulkerSignature.of(visit.enderContents().get(slot), visit.logistics.world);
                    return new Sequence("retour-vide-excédentaire",
                        BaseCargoTree.takeAt(visit, () -> visit.logistics.facts.chest(),
                            "vides-excédentaires", () -> slot, signature::matches),
                        BaseCargoTree.send(visit, () -> visit.position(visit.facts.empties), "retour-vides"), BaseOperations.ender(visit));
                })),
            new RepeatUntil("assez-de-vides-prévues", () -> visit.emptyCount() >= visit.facts.emptyTarget, 25,
                new Sequence("charger-une-vide",
                    BaseCargoTree.take(visit, () -> visit.position(visit.facts.empties), "vides", stack -> empty(visit, stack)),
                    BaseCargoTree.send(visit, () -> visit.logistics.facts.chest(), "ranger-vides"), BaseOperations.ender(visit))),
            new InventoryMilestone("vides-confirmées", ctx -> visit.save(ctx, "vides prévues présentes dans l'ender chest")),
            ContainerTree.close(visit.logistics.outputs));
    }

    private static boolean empty(BaseVisitContext visit, ItemStack stack) {
        return ShulkerSignature.isShulker(stack) && ShulkerSignature.of(stack, visit.logistics.world).empty();
    }
}
