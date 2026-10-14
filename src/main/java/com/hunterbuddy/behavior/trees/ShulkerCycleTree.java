package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.farmer.TransferPurposes;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.nodes.ChooseShulker;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.ConfirmEquipmentPlacement;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Succeed;
import java.util.function.Supplier;
import net.minecraft.item.ItemStack;

public final class ShulkerCycleTree {
    private ShulkerCycleTree() {}

    public static Node build(LogisticsContext logistics, String role, Supplier<Node> work) {
        return build(logistics, role, work, true);
    }

    public static Node build(LogisticsContext logistics, String role, Supplier<Node> work, boolean requireCapacity) {
        return LogisticsOperations.unless("cycle-shulker-" + role,
            () -> logistics.stored(role) || !logistics.cycle(role).unavailable.isEmpty(),
            new Sequence("cycle-" + role,
                ready(logistics, role, requireCapacity),
                available(logistics, role, new Selector("fill-" + role,
                    new Guard(new Condition("contents processed " + role, sensors -> workProven(logistics, role)),
                        new Deferred("verify-processed-contents-" + role, () -> {
                            if (!logistics.placed(role)) return new Succeed();
                            return new Sequence("actual-contents-confirmed-" + role,
                                LogisticsOperations.open(logistics, role, true), ContainerTree.close(logistics.outputs));
                        })), new Deferred("process-" + role, work)).propagateFailures()),
                available(logistics, role, TrackedEquipmentTree.recover(logistics, role)),
                available(logistics, role, store(logistics, role))));
    }

    private static Node available(LogisticsContext logistics, String role, Node operation) {
        return LogisticsOperations.unless("shulker unavailable " + role,
            () -> !logistics.cycle(role).unavailable.isEmpty(), operation);
    }

    private static boolean workProven(LogisticsContext logistics, String role) {
        ShulkerCycleFacts cycle = logistics.cycle(role);
        if (cycle.workedContents.isEmpty()) return false;
        if (!ShulkerSignature.decode(cycle.workedContents, logistics.world)
            .same(ShulkerSignature.decode(cycle.signature, logistics.world))) {
            throw new IllegalStateException("worked shulker signature differs from its contents receipt");
        }
        if (logistics.placed(role) || logistics.returned(role)) return true;
        return logistics.equipment(role).breakRequested() && logistics.world.cleared(logistics.facts.shulker());
    }

    private static Node ready(LogisticsContext logistics, String role, boolean requireCapacity) {
        return new Selector("shulker-ready-" + role,
            LogisticsOperations.guard("tracked shulker " + role, () -> !logistics.cycle(role).signature.isEmpty()
                && (logistics.placed(role) || logistics.returned(role) || workProven(logistics, role))),
            new Guard(new Condition("signature identified " + role,
                sensors -> !logistics.cycle(role).signature.isEmpty()), new ConfirmEquipmentPlacement(logistics, role)),
            new Sequence("take-and-place-" + role,
                LogisticsOperations.open(logistics, role, false), new ChooseShulker(logistics, role, requireCapacity),
                new Deferred("take-identified-shulker-" + role, () -> {
                    if (!logistics.cycle(role).unavailable.isEmpty()) return ContainerTree.close(logistics.outputs);
                    return new Sequence("take-out-shulker-" + role, take(logistics, role),
                        ContainerTree.close(logistics.outputs), TrackedEquipmentTree.place(logistics, role));
                }))).propagateFailures();
    }

    private static Node take(LogisticsContext logistics, String role) {
        return LogisticsOperations.unless("shulker removed from the ender chest " + role, () -> {
            ShulkerCycleFacts cycle = logistics.cycle(role);
            if (cycle.takenContents.isEmpty()) return false;
            if (!LogisticsInventory.decode(cycle.takenContents, 27, logistics.world)
                .get(cycle.sourceSlot).isEmpty()) {
                throw new IllegalStateException("taking receipt has no empty source slot");
            }
            if (logistics.world.countMatching(ShulkerSignature.decode(cycle.signature, logistics.world)::matches)
                != cycle.takeBaseline + 1) throw new IllegalStateException("taken shulker count changed before placement");
            return true;
        }, LogisticsOperations.transfer(logistics, role, false, TransferPurposes.TAKE, () -> {
            ShulkerCycleFacts cycle = logistics.cycle(role);
            ItemStack expected = logistics.outputs.inventory().containerContents().get(cycle.sourceSlot);
            if (!ShulkerSignature.decode(cycle.signature, logistics.world).matches(expected)) {
                throw new IllegalStateException("selected shulker source slot changed");
            }
            return logistics.outputs.inventory().takeStack(cycle.sourceSlot, expected);
        }));
    }

    private static Node store(LogisticsContext logistics, String role) {
        return LogisticsOperations.unless("shulker stored " + role, () -> logistics.stored(role),
            new Sequence("store-shulker-" + role, LogisticsOperations.open(logistics, role, false),
                LogisticsOperations.unless("storage receipt " + role, () -> logistics.stored(role),
                    LogisticsOperations.transfer(logistics, role, false, TransferPurposes.STORE, () ->
                        logistics.outputs.inventory().storeShulker(ShulkerSignature.decode(
                            logistics.cycle(role).signature, logistics.world)))),
                ContainerTree.close(logistics.outputs),
                new InventoryMilestone("storage-confirmed-" + role, ctx -> {
                    if (!logistics.stored(role)) throw new IllegalStateException("storage receipt missing for " + role);
                    logistics.save(ctx, "stored " + role);
                })));
    }
}
