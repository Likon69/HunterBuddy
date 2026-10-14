package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.farmer.TransferPurposes;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;

public final class BaseDeliveryTree {
    private BaseDeliveryTree() {}

    public static Node build(BaseVisitContext visit) {
        return new Sequence("deliver-all-full-shulkers", BaseOperations.ender(visit),
            new RepeatUntil("no-full-shulker-to-deliver", () -> visit.fullSlot() < 0 && !visit.facts.cargoRole.equals(LogisticsRoles.DELIVERY), 27,
                new Sequence("deliver-one-confirmed-shulker",
                    LogisticsOperations.unless("delivery shulker carried", () -> visit.facts.cargoRole.equals(LogisticsRoles.DELIVERY),
                        new Deferred("take-identified-full-shulker", () -> {
                            ShulkerSignature signature = ShulkerSignature.of(
                                visit.enderContents().get(visit.fullSlot()), visit.logistics.world);
                            return BaseCargoTree.takeAt(visit, () -> visit.logistics.facts.chest(),
                                LogisticsRoles.DELIVERY, visit::fullSlot, signature::matches);
                        })),
                    BaseCargoTree.send(visit, () -> visit.position(visit.facts.delivery), TransferPurposes.DELIVER), BaseOperations.ender(visit))),
            new InventoryMilestone("deliveries-confirmed", ctx -> visit.save(ctx, "all full shulkers delivered")),
            ContainerTree.close(visit.logistics.outputs));
    }
}
