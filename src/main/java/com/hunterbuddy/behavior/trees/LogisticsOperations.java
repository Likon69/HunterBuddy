package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.InventoryTransaction;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsTransferReceipt;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.ReconcileLogisticsTransfer;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.TransferItems;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.item.ItemStack;

public final class LogisticsOperations {
    private LogisticsOperations() {}

    public static Node unless(String fact, BooleanSupplier present, Node operation) {
        return new Selector(fact, guard(fact, present), operation);
    }

    public static Node guard(String fact, BooleanSupplier present) {
        return new Guard(new Condition(fact, sensors -> present.getAsBoolean()), new Succeed());
    }

    public static Node prepare(LogisticsContext logistics, Predicate<ItemStack> filter, int slot) {
        return new Deferred("préparer-slot-" + slot, () ->
            EquipmentTree.prepareHeld(logistics.layout, filter, slot, logistics.outputs, logistics.mission));
    }

    public static Node open(LogisticsContext logistics, String role, boolean inShulker) {
        return new Deferred("ouvrir-" + (inShulker ? role : "ender-chest"), () -> new Sequence("conteneur-vérifié",
            ContainerTree.open(inShulker ? logistics.facts.shulker() : logistics.facts.chest(),
                logistics.world, logistics.outputs, logistics.mission),
            new ReconcileLogisticsTransfer(logistics, role, inShulker),
            new InventoryMilestone("preuve-conteneur-suivi", ctx -> {
                String equipment = inShulker ? role : "chest";
                if (!logistics.placed(equipment)) throw new IllegalStateException("opened equipment is not tracked and present");
                if (!inShulker) LogisticsTransferReceipt.observeEnderChest(ctx, logistics);
                logistics.equipment(equipment).milestone(ctx, "opened");
            })));
    }

    public static Node transfer(LogisticsContext logistics, String role, boolean inShulker, String purpose,
                                Supplier<InventoryTransaction> transaction) {
        LogisticsTransferReceipt receipt = new LogisticsTransferReceipt(logistics, role, inShulker, purpose);
        return new Timeout(120, new WithLease(Set.of(Resource.INVENTORY), LeasePolicy.WAIT,
            new TransferItems(logistics.outputs.inventory(), transaction, null, logistics.mission,
                role + " " + purpose, receipt::before, receipt::confirmed)));
    }

    public static Node pendingTransfer(LogisticsContext logistics) {
        return unless("aucun transfert à réconcilier", () -> !logistics.facts.transfer.pending,
            new Deferred("ouvrir-conteneur-du-transfert-interrompu", () -> new Sequence("reprendre-preuves-du-transfert",
                ContainerTree.close(logistics.outputs),
                open(logistics, logistics.facts.transfer.role, logistics.facts.transfer.container.equals("shulker")),
                ContainerTree.close(logistics.outputs))));
    }
}
