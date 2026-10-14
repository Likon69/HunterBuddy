package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.farmer.TransferPurposes;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.util.Rockets;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class BaseFuelTree {
    private BaseFuelTree() {}

    public static Node recoverStock(BaseVisitContext visit) {
        return new Sequence("prepare-already-carried-rockets", BaseOperations.ender(visit),
            new RepeatUntil("budget-ready-or-ender-stock-exhausted", () -> ready(visit)
                || visit.enderContents().stream().noneMatch(Rockets::isPlain), 128,
                take(visit, () -> visit.logistics.facts.chest(), TransferPurposes.CARRIED_FUEL)),
            ContainerTree.close(visit.logistics.outputs));
    }

    public static Node build(BaseVisitContext visit) {
        return new Sequence("departure-fuel-fill-confirmed",
            LogisticsOperations.unless("departure budget already ready", () -> ready(visit),
                new Sequence("complete-measured-fuel",
                    BaseOperations.open(visit, () -> visit.position(visit.facts.supply)),
                    new RepeatUntil("budget-ready-for-takeoff", () -> ready(visit), 512,
                        take(visit, () -> visit.position(visit.facts.supply), TransferPurposes.RESTOCK_ROCKETS)),
                    ContainerTree.close(visit.logistics.outputs))),
            new InventoryMilestone("fuel-fill-confirmed", ctx -> visit.save(ctx, "rockets confirmed for the measured round trip")));
    }

    private static boolean ready(BaseVisitContext visit) {
        return visit.logistics.world.countMatching(Rockets::isPlain) >= visit.facts.rocketBudget;
    }

    private static Node take(BaseVisitContext visit, Supplier<BlockPos> source, String purpose) {
        return new Sequence("take-one-rocket-stack",
            BaseOperations.transfer(visit, source, purpose, () -> {
                List<ItemStack> contents = visit.logistics.outputs.inventory().containerContents();
                int needed = visit.facts.rocketBudget - visit.logistics.world.countMatching(Rockets::isPlain);
                long free = LogisticsInventory.player(visit.logistics.world).stream().filter(ItemStack::isEmpty).count();
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 0; i < contents.size(); i++) {
                        if (!Rockets.isPlain(contents.get(i))) continue;
                        boolean merge = visit.logistics.outputs.inventory().mergesWithoutNewSlot(contents.get(i), needed);
                        if (pass == 0 && merge || pass == 1 && free >= 3) {
                            return visit.logistics.outputs.inventory().takeStackUpTo(i, contents.get(i), needed);
                        }
                    }
                }
                if (contents.stream().anyMatch(Rockets::isPlain)) {
                    throw new OperationFailure(FailureCode.BUDGET, "rocket stock needs space while preserving ender chest pickup and the free slot");
                }
                throw new OperationFailure(FailureCode.MISSION_STOP, "base has no plain rockets in the supply chest");
            }, 1200));
    }
}
