package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.Supplier;
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
                throw new IllegalStateException("case exacte de la shulker " + role + " modifiée");
            }
            return index;
        });
    }

    private static Node selectedTake(BaseVisitContext visit, Supplier<BlockPos> source, String role,
                             ToIntFunction<List<ItemStack>> select) {
        return LogisticsOperations.unless("objet " + role + " déjà transporté", () -> visit.facts.cargoRole.equals(role),
            new Sequence("prendre-" + role,
                LogisticsOperations.unless("slot de transport déjà libre", () -> visit.logistics.layout.stack(8).isEmpty(),
                    new Sequence("préparer-slot-de-transport", ContainerTree.close(visit.logistics.outputs),
                        EquipmentTree.prepareHeld(visit.logistics.layout, ItemStack::isEmpty, 8,
                            visit.logistics.outputs, visit.logistics.mission))), BaseOperations.open(visit, source),
                new InventoryMilestone("slot-libre-conservé-" + role, ctx -> {
                    if (!visit.facts.cargo.isEmpty()) {
                        throw new IllegalStateException("autre objet transporté en attente");
                    }
                    if (LogisticsInventory.player(visit.logistics.world).stream().filter(ItemStack::isEmpty).count() < 2) {
                        throw new IllegalStateException("deux slots libres requis pour transporter et garder un slot libre");
                    }
                }), BaseOperations.transfer(visit, source, "prendre-" + role, () -> {
                    List<ItemStack> contents = visit.logistics.outputs.inventory().containerContents();
                    if (source.get().equals(visit.logistics.facts.chest())
                        && !LogisticsInventory.equal(visit.enderContents(), contents)) {
                        throw new IllegalStateException("ender chest modifié sans transfert confirmé");
                    }
                    int index = select.applyAsInt(contents);
                    if (index < 0) {
                        throw new IllegalStateException("base sans " + role + " au coffre " + source.get().toShortString());
                    }
                    return visit.logistics.outputs.inventory().takeStackToHotbar(index, contents.get(index), 8);
                }), ContainerTree.close(visit.logistics.outputs)));
    }

    public static Node send(BaseVisitContext visit, Supplier<BlockPos> target, String purpose) {
        return LogisticsOperations.unless("aucun objet à transférer " + purpose, () -> visit.facts.cargo.isEmpty(),
            new Sequence("déposer-" + purpose, BaseOperations.open(visit, target),
                new RepeatUntil("réception-totale-" + purpose, () -> visit.facts.cargo.isEmpty(), 72,
                    BaseOperations.transfer(visit, target, purpose, () -> {
                        ItemStack expected = BaseOperations.cargo(visit);
                        return visit.logistics.outputs.inventory().firstDeposit(
                            stack -> ItemStack.areEqual(stack, expected));
                    })), ContainerTree.close(visit.logistics.outputs)));
    }
}
