package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.mc.BaseSite;
import com.hunterbuddy.behavior.mc.BaseStockPlan;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.FarmerFlightBudget;
import com.hunterbuddy.behavior.nodes.BaseVisitScope;
import com.hunterbuddy.behavior.nodes.ChooseShulker;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.TaskScope;
import java.util.List;

public final class BaseVisitTree {
    private BaseVisitTree() {}

    public static Node build(BaseVisitContext visit) {
        return new TaskScope(new BaseVisitScope(visit, new Sequence("visite-unique-de-base",
            new InventoryMilestone("charger-visite-base", visit::initialize),
            LogisticsOperations.unless("ender chest de visite déjà repris",
                () -> visit.logistics.facts.site != null && visit.logistics.returned("chest"), work(visit)),
            BaseDepartureInventoryTree.build(visit),
            new InventoryMilestone("visite-base-terminée", ctx -> {
                if (!visit.logistics.returned("chest")) {
                    throw new IllegalStateException("ender chest non revenu dans l'inventaire");
                }
                if (FarmerFlightBudget.returnDue(ctx, visit.position(visit.facts.delivery),
                    visit.logistics.world, visit.logistics.outputs)) {
                    throw new OperationFailure(FailureCode.MISSION_STOP, "base has insufficient rockets or elytra durability");
                }
                visit.facts.recoveredAt = LogisticsFacts.coordinates(visit.logistics.world.feet());
                visit.logistics.facts.departure = visit.facts.recoveredAt;
                visit.save(ctx, "ender chest repris, visite complète");
            }))));
    }

    private static Node work(BaseVisitContext visit) {
        return new Sequence("jalons-de-la-visite",
            LogisticsOperations.unless("base rejointe près du matériel suivi", () -> {
                if (visit.logistics.facts.site == null || !visit.logistics.world.client().player.isOnGround()) {
                    return false;
                }
                return Math.hypot(visit.logistics.world.client().player.getX() - visit.logistics.facts.origin().getX(),
                    visit.logistics.world.client().player.getZ() - visit.logistics.facts.origin().getZ()) <= 12;
            }, new Deferred("vol-jusqu'à-la-base", () -> FlyToTree.build(
                () -> visit.logistics.facts.site == null ? visit.position(visit.facts.delivery) : visit.logistics.facts.origin(),
                () -> visit.position(visit.facts.delivery), true, visit.logistics.world,
                visit.logistics.baritone, visit.logistics.outputs))),
            LogisticsOperations.unless("emplacement du matériel de base suivi", () -> visit.logistics.facts.site != null,
                new InventoryMilestone("base-rejointe-emplacement-choisi", ctx -> {
                    visit.logistics.facts.site = LogisticsFacts.coordinates(
                        BaseSite.choose(visit.position(visit.facts.delivery), List.of(
                            visit.position(visit.facts.delivery), visit.position(visit.facts.returns),
                            visit.position(visit.facts.empties), visit.position(visit.facts.supply)), visit.logistics.world));
                    visit.save(ctx, "base rejointe, emplacement choisi");
                })),
            new Deferred("rejoindre-matériel-base", () -> GroundTravel.build(visit.logistics.facts.origin(), 0,
                visit.logistics.baritone, visit.logistics.outputs)),
            LogisticsOperations.unless("ender chest cassé en ramassage", () -> visit.logistics.equipment("chest").breakRequested()
                && visit.logistics.world.cleared(visit.logistics.facts.chest()), TrackedEquipmentTree.place(visit.logistics, "chest")),
            LogisticsOperations.unless("ramassage ender chest à terminer", () -> visit.logistics.equipment("chest").breakRequested(),
                new Sequence("livraisons-et-ravitaillement",
                    BaseOperations.pending(visit), LogisticsOperations.pendingTransfer(visit.logistics),
                    BasePackTree.resumeMaterial(visit),
                    BaseCargoResumeTree.build(visit), BaseOperations.ender(visit),
                    BaseInventorySpaceTree.build(visit),
                    new ChooseShulker(visit.logistics, "reserve"),
                    new InventoryMilestone("rôles-base-identifiés", ctx -> {
                        if (visit.logistics.facts.reserve.takenContents.isEmpty()
                            && (visit.logistics.facts.reserve.signature.isEmpty()
                                || !visit.logistics.equipment("reserve").placementRequested())) {
                            visit.logistics.facts.reserve = new ShulkerCycleFacts();
                            visit.logistics.forget("reserve");
                        }
                        visit.save(ctx, "rôles réserve et divers identifiés par contenu et journal");
                    }), ContainerTree.close(visit.logistics.outputs),
                    BasePackTree.build(visit),
                    BaseDeliveryTree.build(visit), BaseSurplusTree.build(visit), BaseMiscTree.build(visit),
                    new InventoryMilestone("mesurer-budget-de-départ", ctx -> BaseStockPlan.measure(ctx, visit)),
                    BaseFuelTree.recoverStock(visit),
                    BaseEmptyShulkersTree.build(visit),
                    BaseReserveTree.build(visit), BaseFuelTree.build(visit))),
            new Deferred("retour-au-matériel-base", () -> GroundTravel.build(visit.logistics.facts.origin(), 0,
                visit.logistics.baritone, visit.logistics.outputs)),
            TrackedEquipmentTree.recover(visit.logistics, "chest"));
    }
}
