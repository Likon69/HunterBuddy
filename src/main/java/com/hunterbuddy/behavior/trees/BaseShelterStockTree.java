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
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class BaseShelterStockTree {
    private BaseShelterStockTree() {}

    public static Node build(BaseVisitContext visit) {
        return new Sequence("supply-disposable-shelter-blocks",
            LogisticsOperations.unless("shelter stock already ready", () -> stock(visit) >= 128,
                new Sequence("top-up-shelter-stock-to-128",
                    BaseOperations.open(visit, () -> visit.position(visit.facts.supply)),
                    new RepeatUntil("shelter-stock-confirmed", () -> complete(visit), 64,
                        BaseOperations.transfer(visit, () -> visit.position(visit.facts.supply), TransferPurposes.SHELTER_STOCK, () -> {
                            List<ItemStack> contents = visit.logistics.outputs.inventory().containerContents();
                            int needed = 128 - stock(visit);
                            int obsidian = visit.logistics.world.count(Items.OBSIDIAN)
                                + contents.stream().filter(stack -> stack.isOf(Items.OBSIDIAN)).mapToInt(ItemStack::getCount).sum();
                            long free = LogisticsInventory.player(visit.logistics.world).stream().filter(ItemStack::isEmpty).count();
                            for (int pass = 0; pass < 2; pass++) {
                                for (int slot = 0; slot < contents.size(); slot++) {
                                    ItemStack stack = contents.get(slot);
                                    if (!LogisticsInventory.shelterBlock(stack) && !(stack.isOf(Items.OBSIDIAN) && obsidian > 25)) continue;
                                    int amount = needed + (stack.isOf(Items.OBSIDIAN)
                                        ? Math.max(0, 25 - visit.logistics.world.count(Items.OBSIDIAN)) : 0);
                                    boolean merge = visit.logistics.outputs.inventory().mergesWithoutNewSlot(stack, amount);
                                    if (pass == 0 && merge || pass == 1 && free >= 3) {
                                        return visit.logistics.outputs.inventory().takeStackUpTo(slot, stack, amount);
                                    }
                                }
                            }
                            if (contents.stream().anyMatch(stack -> LogisticsInventory.shelterBlock(stack)
                                || stack.isOf(Items.OBSIDIAN) && obsidian > 25)) {
                                throw new OperationFailure(FailureCode.BUDGET, "shelter stock needs space while preserving ender chest pickup and the free slot");
                            }
                            throw new OperationFailure(FailureCode.MISSION_STOP, "base has fewer than 64 usable shelter blocks above the obsidian reserve");
                        }, 1200)), ContainerTree.close(visit.logistics.outputs))),
            new InventoryMilestone("shelter-stock-confirmed", ctx -> visit.save(ctx, "disposable shelter stock ready")));
    }

    private static int stock(BaseVisitContext visit) {
        return visit.logistics.world.countMatching(LogisticsInventory::shelterBlock)
            + Math.max(0, visit.logistics.world.count(Items.OBSIDIAN) - 25);
    }

    private static boolean complete(BaseVisitContext visit) {
        int carried = stock(visit);
        if (carried >= 128) return true;
        if (carried >= 64 && LogisticsInventory.player(visit.logistics.world).stream().filter(ItemStack::isEmpty).count() < 3) return true;
        List<ItemStack> contents = visit.logistics.outputs.inventory().containerContents();
        int obsidian = visit.logistics.world.count(Items.OBSIDIAN);
        int supplyObsidian = contents.stream().filter(stack -> stack.isOf(Items.OBSIDIAN)).mapToInt(ItemStack::getCount).sum();
        int available = contents.stream().filter(LogisticsInventory::shelterBlock).mapToInt(ItemStack::getCount).sum()
            + Math.max(0, obsidian + supplyObsidian - 25) - Math.max(0, obsidian - 25);
        return carried >= 64 && available == 0;
    }
}
