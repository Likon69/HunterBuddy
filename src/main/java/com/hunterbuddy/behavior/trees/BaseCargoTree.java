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
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class BaseCargoTree {
    private BaseCargoTree() {}

    public static Node take(BaseVisitContext visit, Supplier<BlockPos> source, String role, Predicate<ItemStack> filter) {
        return selectedTake(visit, source, role, contents -> {
            for (int i = 0; i < contents.size(); i++) {
                if (filter.test(contents.get(i))) {
                    return i;
                }
            }
            return -1;
        });
    }

    public static Node takeAt(BaseVisitContext visit, Supplier<BlockPos> source, String role,
                              IntSupplier slot, Predicate<ItemStack> expected) {
        return selectedTake(visit, source, role, contents -> {
            int index = slot.getAsInt();
            if (index < 0 || index >= contents.size() || !expected.test(contents.get(index))) {
                throw new IllegalStateException("exact shulker slot for " + role + " changed");
            }
            return index;
        });
    }

    private static Node selectedTake(BaseVisitContext visit, Supplier<BlockPos> source, String role,
                             ToIntFunction<List<ItemStack>> select) {
        return LogisticsOperations.unless("item " + role + " already carried", () -> visit.facts.cargoRole.equals(role),
            new Sequence(TransferPurposes.TAKE_PREFIX + role,
                LogisticsOperations.unless("cargo slot already free", () -> visit.logistics.layout.stack(8).isEmpty(),
                    new Sequence("prepare-cargo-slot", ContainerTree.close(visit.logistics.outputs),
                        EquipmentTree.prepareHeld(visit.logistics.layout, ItemStack::isEmpty, 8,
                            visit.logistics.outputs, visit.logistics.mission))), BaseOperations.open(visit, source),
                new InventoryMilestone("free-slot-preserved-" + role, ctx -> {
                    if (!visit.facts.cargo.isEmpty()) {
                        throw new IllegalStateException("another carried item is pending");
                    }
                    if (LogisticsInventory.player(visit.logistics.world).stream().filter(ItemStack::isEmpty).count() < 2) {
                        throw new IllegalStateException("two free slots required to carry an item and keep one slot free");
                    }
                }), BaseOperations.transfer(visit, source, TransferPurposes.TAKE_PREFIX + role, () -> {
                    List<ItemStack> contents = visit.logistics.outputs.inventory().containerContents();
                    if (source.get().equals(visit.logistics.facts.chest())
                        && !LogisticsInventory.equal(visit.enderContents(), contents)) {
                        throw new IllegalStateException("ender chest changed without confirmed transfer");
                    }
                    int index = select.applyAsInt(contents);
                    if (index < 0) {
                        FailureCode code = source.get().equals(visit.position(visit.facts.supply))
                            || source.get().equals(visit.position(visit.facts.empties))
                            ? FailureCode.MISSION_STOP : FailureCode.MISSING_ITEM;
                        throw new OperationFailure(code, "base has no " + role + " at chest " + source.get().toShortString());
                    }
                    return visit.logistics.outputs.inventory().takeStackToHotbar(index, contents.get(index), 8);
                }), ContainerTree.close(visit.logistics.outputs)));
    }

    public static Node send(BaseVisitContext visit, Supplier<BlockPos> target, String purpose) {
        return LogisticsOperations.unless("no item to transfer " + purpose, () -> visit.facts.cargo.isEmpty(),
            new Sequence("deposit-" + purpose, BaseOperations.open(visit, target),
                new RepeatUntil("complete-receipt-" + purpose, () -> visit.facts.cargo.isEmpty(), 72,
                    BaseOperations.transfer(visit, target, purpose, () -> {
                        ItemStack expected = BaseOperations.cargo(visit);
                        return visit.logistics.outputs.inventory().firstDeposit(
                            stack -> ItemStack.areEqual(stack, expected), FailureCode.MISSION_STOP);
                    })), ContainerTree.close(visit.logistics.outputs)));
    }
}
