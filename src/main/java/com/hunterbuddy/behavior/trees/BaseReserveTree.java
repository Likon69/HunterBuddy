package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.mc.BaseReserveInventory;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.List;

public final class BaseReserveTree {
    private BaseReserveTree() {}

    public static Node build(BaseVisitContext visit) {
        BaseReserveInventory inventory = new BaseReserveInventory(visit);
        return new Sequence("réserve-et-regear-base",
            LogisticsOperations.unless("aucun cycle de réserve interrompu",
                () -> visit.logistics.facts.reserve.signature.isEmpty() || visit.logistics.stored("reserve"),
                BaseReserveWorkTree.build(visit)),
            BaseOperations.ender(visit),
            LogisticsOperations.unless("ancien regear inutile ou réserve incapable de le fournir",
                () -> (!LogisticsInventory.needsRegear(visit.logistics.world, visit.logistics.profile)
                    && inventory.carriedWear() >= visit.facts.wearBudget) || !inventory.canRegear(),
                BaseReserveWorkTree.build(visit)),
            new RepeatUntil("équipement-et-durabilité-prêts", () -> inventory.carriedWear() >= visit.facts.wearBudget
                && LogisticsInventory.missing(visit.logistics.world, visit.logistics.profile, List.of()).isEmpty(), 36,
                new Sequence("ravitailler-équipement-porté", refresh(visit, inventory),
                    new InventoryMilestone("durabilité-avant-regear", ctx -> visit.wearBeforeRegear = inventory.carriedWear()),
                    BaseReserveWorkTree.build(visit),
                    new InventoryMilestone("équipement-porté-vérifié", ctx -> {
                        List<String> missing = LogisticsInventory.missing(visit.logistics.world, visit.logistics.profile, List.of());
                        if (!missing.isEmpty()) {
                            throw new IllegalStateException("regear non confirmé : " + String.join(", ", missing)
                                + " " + visit.logistics.facts.incomplete);
                        }
                        if (inventory.carriedWear() < visit.facts.wearBudget
                            && inventory.carriedWear() <= visit.wearBeforeRegear) {
                            throw new IllegalStateException("aucune durabilité supplémentaire reçue : "
                                + "vérifier les slots libres de l'inventaire");
                        }
                    }))),
            refresh(visit, inventory),
            new InventoryMilestone("réserve-base-confirmée", ctx -> {
                if (!inventory.ready()) {
                    throw new OperationFailure(FailureCode.MISSION_STOP, "base has no complete prepared reserve");
                }
                List<String> missing = LogisticsInventory.missing(visit.logistics.world, visit.logistics.profile, List.of());
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("regear non confirmé : " + String.join(", ", missing)
                        + " " + visit.logistics.facts.incomplete);
                }
                visit.logistics.facts.incomplete = "";
                if (inventory.carriedWear() < visit.facts.wearBudget) {
                    throw new OperationFailure(FailureCode.MISSION_STOP, "base has insufficient elytra durability for the measured round trip");
                }
                visit.logistics.mission.equipment(ctx, "reserve-status", "ready");
                visit.save(ctx, "réserve préparée rangée dans l'ender chest");
            }));
    }

    private static Node refresh(BaseVisitContext visit, BaseReserveInventory inventory) {
        return new Sequence("vérifier-et-remplacer-réserve", BaseOperations.ender(visit),
            LogisticsOperations.unless("réserve prête dans l'ender chest", inventory::ready,
                new Sequence("remplacer-la-réserve-usée",
                    LogisticsOperations.unless("aucune réserve usée à rapporter", () -> inventory.slot() < 0,
                        new Deferred("identifier-réserve-usée", () -> {
                            int sourceSlot = inventory.slot();
                            ShulkerSignature signature = ShulkerSignature.of(
                                visit.enderContents().get(inventory.slot()), visit.logistics.world);
                            return new Sequence("retour-réserve-usée",
                                BaseCargoTree.takeAt(visit, () -> visit.logistics.facts.chest(),
                                    "réserve-usée", () -> sourceSlot, signature::matches),
                                BaseCargoTree.send(visit, () -> visit.position(visit.facts.returns), "retour-réserve"));
                        })),
                    BaseCargoTree.take(visit, () -> visit.position(visit.facts.supply), "réserve-préparée", inventory::prepared),
                    BaseCargoTree.send(visit, () -> visit.logistics.facts.chest(), "ranger-réserve"),
                    BaseOperations.ender(visit))),
            ContainerTree.close(visit.logistics.outputs));
    }
}
