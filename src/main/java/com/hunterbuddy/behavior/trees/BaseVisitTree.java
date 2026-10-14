package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
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
import net.minecraft.util.math.BlockPos;

public final class BaseVisitTree {
    private BaseVisitTree() {}

    public static Node build(BaseVisitContext visit) {
        return new TaskScope(new BaseVisitScope(visit, new Sequence("single-base-visit",
            new InventoryMilestone("load-base-visit", visit::initialize),
            LogisticsOperations.unless("base visit ender chest already recovered",
                () -> visit.logistics.facts.site != null && visit.logistics.returned(LogisticsRoles.CHEST), work(visit)),
            BaseDepartureInventoryTree.build(visit),
            new InventoryMilestone("base-visit-completed", ctx -> {
                if (!visit.logistics.returned(LogisticsRoles.CHEST)) {
                    throw new IllegalStateException("ender chest not returned to inventory");
                }
                if (FarmerFlightBudget.returnDue(ctx, visit.position(visit.facts.delivery),
                    visit.logistics.world, visit.logistics.outputs)) {
                    throw new OperationFailure(FailureCode.MISSION_STOP, "base has insufficient rockets or elytra durability");
                }
                visit.facts.recoveredAt = LogisticsFacts.coordinates(visit.logistics.world.feet());
                visit.logistics.facts.departure = visit.facts.recoveredAt;
                visit.save(ctx, "ender chest recovered, visit complete");
            }))));
    }

    private static Node work(BaseVisitContext visit) {
        return new Sequence("base-visit-milestones",
            LogisticsOperations.unless("base reached near tracked equipment", () -> {
                if (visit.logistics.facts.site == null || !visit.logistics.world.client().player.isOnGround()) {
                    return false;
                }
                return Math.hypot(visit.logistics.world.client().player.getX() - visit.logistics.facts.origin().getX(),
                    visit.logistics.world.client().player.getZ() - visit.logistics.facts.origin().getZ()) <= 12;
            }, new Deferred("reach-base", () -> {
                BlockPos delivery = visit.position(visit.facts.delivery);
                BlockPos destination = visit.logistics.facts.site == null ? delivery : visit.logistics.facts.origin();
                if (visit.logistics.world.client().player.isOnGround() && Math.hypot(
                    visit.logistics.world.client().player.getX() - delivery.getX(),
                    visit.logistics.world.client().player.getZ() - delivery.getZ()) <= 32) {
                    return GroundTravel.build(destination, visit.logistics.facts.site == null ? 2 : 0,
                        visit.logistics.baritone, visit.logistics.outputs);
                }
                return FlyToTree.build(() -> destination, () -> delivery, true, visit.logistics.world,
                    visit.logistics.baritone, visit.logistics.outputs);
            })),
            LogisticsOperations.unless("base equipment location tracked", () -> visit.logistics.facts.site != null,
                new InventoryMilestone("base-reached-location-selected", ctx -> {
                    visit.logistics.facts.site = LogisticsFacts.coordinates(
                        BaseSite.choose(visit.position(visit.facts.delivery), List.of(
                            visit.position(visit.facts.delivery), visit.position(visit.facts.returns),
                            visit.position(visit.facts.empties), visit.position(visit.facts.supply)), visit.logistics.world));
                    visit.save(ctx, "base reached, location selected");
                })),
            new Deferred("reach-base-equipment", () -> GroundTravel.build(visit.logistics.facts.origin(), 0,
                visit.logistics.baritone, visit.logistics.outputs)),
            LogisticsOperations.unless("broken ender chest being picked up", () -> visit.logistics.equipment(LogisticsRoles.CHEST).breakRequested()
                && visit.logistics.world.cleared(visit.logistics.facts.chest()), TrackedEquipmentTree.place(visit.logistics, LogisticsRoles.CHEST)),
            LogisticsOperations.unless("ender chest pickup must be completed", () -> visit.logistics.equipment(LogisticsRoles.CHEST).breakRequested(),
                new Sequence("deliveries-and-restocking",
                    BaseOperations.pending(visit), LogisticsOperations.pendingTransfer(visit.logistics),
                    BasePackTree.resumeMaterial(visit),
                    BaseCargoResumeTree.build(visit), BaseOperations.ender(visit),
                    BaseInventorySpaceTree.build(visit),
                    new ChooseShulker(visit.logistics, LogisticsRoles.RESERVE),
                    new InventoryMilestone("base-roles-identified", ctx -> {
                        if (visit.logistics.facts.reserve.takenContents.isEmpty()
                            && (visit.logistics.facts.reserve.signature.isEmpty()
                                || !visit.logistics.equipment(LogisticsRoles.RESERVE).placementRequested())) {
                            visit.logistics.facts.reserve = new ShulkerCycleFacts();
                            visit.logistics.forget(LogisticsRoles.RESERVE);
                        }
                        visit.save(ctx, "reserve and miscellaneous roles identified by contents and journal");
                    }), ContainerTree.close(visit.logistics.outputs),
                    BaseDeliveryTree.build(visit), BasePackTree.build(visit),
                    BaseDeliveryTree.build(visit), BaseSurplusTree.build(visit), BaseMiscTree.build(visit),
                    new InventoryMilestone("measure-departure-budget", ctx -> BaseStockPlan.measure(ctx, visit)),
                    BaseFuelTree.recoverStock(visit),
                    BaseEmptyShulkersTree.build(visit),
                    BaseReserveTree.build(visit), BaseShelterStockTree.build(visit), BaseFuelTree.build(visit))),
            new Deferred("return-to-base-equipment", () -> GroundTravel.build(visit.logistics.facts.origin(), 0,
                visit.logistics.baritone, visit.logistics.outputs)),
            TrackedEquipmentTree.recover(visit.logistics, LogisticsRoles.CHEST));
    }
}
