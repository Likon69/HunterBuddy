package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import net.minecraft.item.ItemStack;

public final class BaseSurplusTree {
    private BaseSurplusTree() {}

    public static Node build(BaseVisitContext visit) {
        return LogisticsOperations.unless("étape encore à livrer",
            () -> visit.delivered() < visit.facts.target,
            new Sequence("retourner-le-surplus-de-l'étape-terminée", BaseOperations.ender(visit),
                new RepeatUntil("aucune-shulker-partielle-inutile", () -> slot(visit) < 0, 27,
                    new Sequence("retourner-la-shulker-partielle-identifiée",
                        new Deferred("prendre-le-surplus-identifié", () -> {
                            ShulkerSignature signature = ShulkerSignature.of(visit.enderContents().get(slot(visit)),
                                visit.logistics.world);
                            return BaseCargoTree.takeAt(visit, () -> visit.logistics.facts.chest(),
                                "surplus-matériau", () -> slot(visit), signature::matches);
                        }),
                        BaseCargoTree.send(visit, () -> visit.position(visit.facts.returns), "retour-surplus"),
                        BaseOperations.ender(visit))), ContainerTree.close(visit.logistics.outputs)));
    }

    private static int slot(BaseVisitContext visit) {
        for (int i = 0; i < visit.enderContents().size(); i++) {
            ItemStack stack = visit.enderContents().get(i);
            if (!ShulkerSignature.isShulker(stack) || visit.roleSlot(i, "reserve") || visit.roleSlot(i, "divers")) continue;
            ShulkerSignature signature = ShulkerSignature.of(stack, visit.logistics.world);
            if (!signature.empty() && LogisticsInventory.pure(signature, visit.logistics.profile)) return i;
        }
        return -1;
    }
}
