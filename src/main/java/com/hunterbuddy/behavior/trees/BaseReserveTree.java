package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.farmer.TransferPurposes;
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
        return new Sequence("base-reserve-and-regear",
            LogisticsOperations.unless("no interrupted reserve cycle",
                () -> visit.logistics.facts.reserve.signature.isEmpty() || visit.logistics.stored(LogisticsRoles.RESERVE),
                BaseReserveWorkTree.build(visit)),
            BaseOperations.ender(visit),
            LogisticsOperations.unless("previous regear unnecessary or reserve unable to supply it",
                () -> (!LogisticsInventory.needsRegear(visit.logistics.world, visit.logistics.profile)
                    && inventory.carriedWear() >= visit.facts.wearBudget) || !inventory.canRegear(),
                BaseReserveWorkTree.build(visit)),
            new RepeatUntil("equipment-and-durability-ready", () -> inventory.carriedWear() >= visit.facts.wearBudget
                && LogisticsInventory.missing(visit.logistics.world, visit.logistics.profile, List.of()).isEmpty(), 36,
                new Sequence("restock-carried-equipment", refresh(visit, inventory),
                    new InventoryMilestone("durability-before-regear", ctx -> visit.wearBeforeRegear = inventory.carriedWear()),
                    BaseReserveWorkTree.build(visit),
                    new InventoryMilestone("carried-equipment-verified", ctx -> {
                        List<String> missing = LogisticsInventory.missing(visit.logistics.world, visit.logistics.profile, List.of());
                        if (!missing.isEmpty()) {
                            throw new IllegalStateException("regear not confirmed: " + String.join(", ", missing)
                                + " " + visit.logistics.facts.incomplete);
                        }
                        if (inventory.carriedWear() < visit.facts.wearBudget
                            && inventory.carriedWear() <= visit.wearBeforeRegear) {
                            throw new IllegalStateException("no additional durability received: "
                                + "check free inventory slots");
                        }
                    }))),
            refresh(visit, inventory),
            new InventoryMilestone("base-reserve-confirmed", ctx -> {
                if (!inventory.ready()) {
                    throw new OperationFailure(FailureCode.MISSION_STOP, "base has no complete prepared reserve");
                }
                List<String> missing = LogisticsInventory.missing(visit.logistics.world, visit.logistics.profile, List.of());
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("regear not confirmed: " + String.join(", ", missing)
                        + " " + visit.logistics.facts.incomplete);
                }
                visit.logistics.facts.incomplete = "";
                if (inventory.carriedWear() < visit.facts.wearBudget) {
                    throw new OperationFailure(FailureCode.MISSION_STOP, "base has insufficient elytra durability for the measured round trip");
                }
                visit.logistics.mission.equipment(ctx, "reserve-status", "ready");
                visit.save(ctx, "prepared reserve stored in the ender chest");
            }));
    }

    private static Node refresh(BaseVisitContext visit, BaseReserveInventory inventory) {
        return new Sequence("verify-and-replace-reserve", BaseOperations.ender(visit),
            LogisticsOperations.unless("reserve ready in the ender chest", inventory::ready,
                new Sequence("replace-used-reserve",
                    LogisticsOperations.unless("no used reserve to return", () -> inventory.slot() < 0,
                        new Deferred("identify-used-reserve", () -> {
                            int sourceSlot = inventory.slot();
                            ShulkerSignature signature = ShulkerSignature.of(
                                visit.enderContents().get(inventory.slot()), visit.logistics.world);
                            return new Sequence("return-used-reserve",
                                BaseCargoTree.takeAt(visit, () -> visit.logistics.facts.chest(),
                                    LogisticsRoles.USED_RESERVE, () -> sourceSlot, signature::matches),
                                BaseCargoTree.send(visit, () -> visit.position(visit.facts.returns), TransferPurposes.RETURN_RESERVE));
                        })),
                    BaseCargoTree.take(visit, () -> visit.position(visit.facts.supply), LogisticsRoles.PREPARED_RESERVE, inventory::prepared),
                    BaseCargoTree.send(visit, () -> visit.logistics.facts.chest(), TransferPurposes.STORE_RESERVE),
                    BaseOperations.ender(visit))),
            ContainerTree.close(visit.logistics.outputs));
    }
}
