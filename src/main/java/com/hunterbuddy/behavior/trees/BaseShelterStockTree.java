package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class BaseShelterStockTree {
    private BaseShelterStockTree() {}

    public static Node build(BaseVisitContext visit) {
        return new Sequence("supply-disposable-shelter-blocks",
            LogisticsOperations.unless("shelter stock already ready", () -> stock(visit) >= 64,
                new Sequence("top-up-shelter-stock-to-64",
                    BaseOperations.open(visit, () -> visit.position(visit.facts.supply)),
                    new RepeatUntil("64-shelter-blocks-confirmed", () -> stock(visit) >= 64, 64,
                        BaseOperations.transfer(visit, () -> visit.position(visit.facts.supply), "shelter-stock", () -> {
                            List<ItemStack> contents = visit.logistics.outputs.inventory().containerContents();
                            int needed = 64 - stock(visit);
                            long free = LogisticsInventory.player(visit.logistics.world).stream().filter(ItemStack::isEmpty).count();
                            for (int pass = 0; pass < 2; pass++) {
                                for (int slot = 0; slot < contents.size(); slot++) {
                                    if (!LogisticsInventory.shelterBlock(contents.get(slot))) continue;
                                    boolean merge = visit.logistics.outputs.inventory().mergesWithoutNewSlot(contents.get(slot), needed);
                                    if (pass == 0 && merge || pass == 1 && free >= 3) {
                                        return visit.logistics.outputs.inventory().takeStackUpTo(slot, contents.get(slot), needed);
                                    }
                                }
                            }
                            if (contents.stream().anyMatch(LogisticsInventory::shelterBlock)) {
                                throw new OperationFailure(FailureCode.BUDGET, "shelter stock needs space while preserving ender chest pickup and the free slot");
                            }
                            throw new OperationFailure(FailureCode.MISSION_STOP, "base has no shelter blocks in the supply chest");
                        }, 1200)), ContainerTree.close(visit.logistics.outputs))),
            new InventoryMilestone("shelter-stock-confirmed", ctx -> visit.save(ctx, "disposable shelter stock ready")));
    }

    private static int stock(BaseVisitContext visit) {
        return visit.logistics.world.countMatching(LogisticsInventory::shelterBlock);
    }
}
