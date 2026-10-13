package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
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
        return LogisticsOperations.unless("cycle-shulker-" + role,
            () -> logistics.stored(role) || !logistics.cycle(role).unavailable.isEmpty(),
            new Sequence("cycle-" + role,
                ready(logistics, role),
                available(logistics, role, new Selector("remplir-" + role,
                    new Guard(new Condition("contenu travaillé " + role, sensors -> workProven(logistics, role)),
                        new Deferred("vérifier-contenu-travaillé-" + role, () -> {
                            if (!logistics.placed(role)) return new Succeed();
                            return new Sequence("contenu-réel-confirmé-" + role,
                                LogisticsOperations.open(logistics, role, true), ContainerTree.close(logistics.outputs));
                        })), new Deferred("travailler-" + role, work))),
                available(logistics, role, TrackedEquipmentTree.recover(logistics, role)),
                available(logistics, role, store(logistics, role))));
    }

    private static Node available(LogisticsContext logistics, String role, Node operation) {
        return LogisticsOperations.unless("shulker indisponible " + role,
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

    private static Node ready(LogisticsContext logistics, String role) {
        return new Selector("shulker-prête-" + role,
            LogisticsOperations.guard("shulker suivie " + role, () -> !logistics.cycle(role).signature.isEmpty()
                && (logistics.placed(role) || logistics.returned(role) || workProven(logistics, role))),
            new Guard(new Condition("signature identifiée " + role,
                sensors -> !logistics.cycle(role).signature.isEmpty()), new ConfirmEquipmentPlacement(logistics, role)),
            new Sequence("prendre-et-poser-" + role,
                LogisticsOperations.open(logistics, role, false), new ChooseShulker(logistics, role),
                new Deferred("prendre-shulker-identifiée-" + role, () -> {
                    if (!logistics.cycle(role).unavailable.isEmpty()) return ContainerTree.close(logistics.outputs);
                    return new Sequence("sortir-shulker-" + role, take(logistics, role),
                        ContainerTree.close(logistics.outputs), TrackedEquipmentTree.place(logistics, role));
                })));
    }

    private static Node take(LogisticsContext logistics, String role) {
        return LogisticsOperations.unless("shulker retirée de l'ender chest " + role, () -> {
            ShulkerCycleFacts cycle = logistics.cycle(role);
            if (cycle.takenContents.isEmpty()) return false;
            if (!LogisticsInventory.decode(cycle.takenContents, 27, logistics.world)
                .get(cycle.sourceSlot).isEmpty()) {
                throw new IllegalStateException("taking receipt has no empty source slot");
            }
            if (logistics.world.countMatching(ShulkerSignature.decode(cycle.signature, logistics.world)::matches)
                != cycle.takeBaseline + 1) throw new IllegalStateException("taken shulker count changed before placement");
            return true;
        }, LogisticsOperations.transfer(logistics, role, false, "take", () -> {
            ShulkerCycleFacts cycle = logistics.cycle(role);
            ItemStack expected = logistics.outputs.inventory().containerContents().get(cycle.sourceSlot);
            if (!ShulkerSignature.decode(cycle.signature, logistics.world).matches(expected)) {
                throw new IllegalStateException("selected shulker source slot changed");
            }
            return logistics.outputs.inventory().takeStack(cycle.sourceSlot, expected);
        }));
    }

    private static Node store(LogisticsContext logistics, String role) {
        return LogisticsOperations.unless("shulker rangée " + role, () -> logistics.stored(role),
            new Sequence("ranger-shulker-" + role, LogisticsOperations.open(logistics, role, false),
                LogisticsOperations.unless("reçu de rangement " + role, () -> logistics.stored(role),
                    LogisticsOperations.transfer(logistics, role, false, "store", () ->
                        logistics.outputs.inventory().storeShulker(ShulkerSignature.decode(
                            logistics.cycle(role).signature, logistics.world)))),
                ContainerTree.close(logistics.outputs),
                new InventoryMilestone("rangement-confirmé-" + role, ctx -> {
                    if (!logistics.stored(role)) throw new IllegalStateException("storage receipt missing for " + role);
                    logistics.save(ctx, "rangé " + role);
                })));
    }
}
