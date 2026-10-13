package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;

public final class BaseDeliveryTree {
    private BaseDeliveryTree() {}

    public static Node build(BaseVisitContext visit) {
        return new Sequence("livrer-toutes-les-pleines", BaseOperations.ender(visit),
            new RepeatUntil("aucune-pleine-à-livrer", () -> visit.fullSlot() < 0 && !visit.facts.cargoRole.equals("livraison"), 27,
                new Sequence("livrer-une-shulker-confirmée",
                    LogisticsOperations.unless("shulker de livraison transportée", () -> visit.facts.cargoRole.equals("livraison"),
                        new Deferred("sortir-la-pleine-identifiée", () -> {
                            ShulkerSignature signature = ShulkerSignature.of(
                                visit.enderContents().get(visit.fullSlot()), visit.logistics.world);
                            return BaseCargoTree.takeAt(visit, () -> visit.logistics.facts.chest(),
                                "livraison", visit::fullSlot, signature::matches);
                        })),
                    BaseCargoTree.send(visit, () -> visit.position(visit.facts.delivery), "livrer"), BaseOperations.ender(visit))),
            new InventoryMilestone("livraisons-confirmées", ctx -> visit.save(ctx, "toutes les pleines livrées")),
            ContainerTree.close(visit.logistics.outputs));
    }
}
