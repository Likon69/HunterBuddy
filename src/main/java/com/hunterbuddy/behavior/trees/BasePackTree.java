package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatShulkerCycle;
import com.hunterbuddy.behavior.nodes.Sequence;

public final class BasePackTree {
    private BasePackTree() {}

    public static Node resumeMaterial(BaseVisitContext visit) {
        return LogisticsOperations.unless("aucune shulker matériau de base interrompue",
            () -> visit.logistics.facts.material.signature.isEmpty() || visit.logistics.stored("material"),
            ShulkerCycleTree.build(visit.logistics, "material", () -> MaterialWorkTree.build(visit.logistics, "material")));
    }

    public static Node build(BaseVisitContext visit) {
        return new Sequence("ranger-les-objets-ramenés-sans-abri-de-terrain",
            LogisticsOperations.unless("matériau absent ou étape déjà livrée",
                () -> visit.delivered() >= visit.facts.target
                    || visit.logistics.world.countMatching(LogisticsInventory.accepted(visit.logistics.profile)) == 0,
                cycle(visit, "material")),
            LogisticsOperations.unless("aucun objet divers ramené",
                () -> visit.logistics.world.countMatching(LogisticsInventory.miscellaneous(visit.logistics.profile)) == 0,
                cycle(visit, "divers")), ContainerTree.close(visit.logistics.outputs),
            new InventoryMilestone("objets-ramenés-rangés-confirmés", ctx -> {
                if (visit.delivered() < visit.facts.target
                    && visit.logistics.world.countMatching(LogisticsInventory.accepted(visit.logistics.profile)) > 0) {
                    throw new IllegalStateException("base sans shulker matériau disponible pour les objets ramenés : "
                        + visit.logistics.facts.material.unavailable);
                }
                if (visit.logistics.world.countMatching(LogisticsInventory.miscellaneous(visit.logistics.profile)) > 0) {
                    throw new IllegalStateException("shulker divers sans capacité confirmée pour les objets ramenés : "
                        + visit.logistics.facts.divers.unavailable);
                }
                visit.save(ctx, "objets ramenés rangés par transferts confirmés");
            }));
    }

    private static Node cycle(BaseVisitContext visit, String role) {
        return new RepeatShulkerCycle(visit.logistics, role, ShulkerCycleTree.build(visit.logistics, role,
            () -> MaterialWorkTree.build(visit.logistics, role)));
    }
}
