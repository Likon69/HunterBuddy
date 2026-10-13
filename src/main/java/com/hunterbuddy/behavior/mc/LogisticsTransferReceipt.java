package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.farmer.TransferFacts;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class LogisticsTransferReceipt {
    private final LogisticsContext logistics;
    private final String role;
    private final boolean inShulker;
    private final String purpose;

    public LogisticsTransferReceipt(LogisticsContext logistics, String role, boolean inShulker, String purpose) {
        this.logistics = logistics;
        this.role = role;
        this.inShulker = inShulker;
        this.purpose = purpose;
    }

    public void before(TickContext ctx, InventoryTransaction transaction) {
        TransferFacts transfer = logistics.facts.transfer;
        if (transfer.pending) throw new IllegalStateException("another inventory transfer needs reconciliation");
        transfer.stack = LogisticsInventory.encode(List.of(logistics.outputs.inventory().sourceStack(transaction)), logistics.world);
        transfer.deposit = logistics.outputs.inventory().playerSource(transaction);
        transfer.player = LogisticsInventory.encode(LogisticsInventory.player(logistics.world), logistics.world);
        transfer.contents = LogisticsInventory.encode(logistics.outputs.inventory().containerContents(), logistics.world);
        transfer.container = inShulker ? "shulker" : "chest";
        transfer.role = role;
        transfer.purpose = purpose;
        transfer.pending = true;
        logistics.save(ctx, "transfer intent " + role + " " + purpose);
    }

    public void confirmed(TickContext ctx) {
        TransferFacts transfer = logistics.facts.transfer;
        if (!transfer.pending || !transfer.role.equals(role) || !transfer.purpose.equals(purpose)) {
            throw new IllegalStateException("inventory receipt has no matching transfer intent");
        }
        accept(ctx, logistics);
    }

    public static void accept(TickContext ctx, LogisticsContext logistics) {
        TransferFacts transfer = logistics.facts.transfer;
        ShulkerCycleFacts cycle = logistics.cycle(transfer.role);
        List<ItemStack> contents = logistics.outputs.inventory().containerContents();
        if (transfer.container.equals("shulker")) {
            TemporaryEquipment item = logistics.equipment(transfer.role);
            item.signature(item.signature().withContents(contents, logistics.world));
            cycle.signature = item.signature().encoded();
            item.contents(ctx, logistics.outputs.inventory());
        } else {
            observeEnderChest(ctx, logistics);
            if (transfer.purpose.equals("take")) {
                ShulkerSignature signature = ShulkerSignature.decode(cycle.signature, logistics.world);
                if (logistics.world.countMatching(signature::matches) != cycle.takeBaseline + 1
                    || !contents.get(cycle.sourceSlot).isEmpty()) {
                    throw new IllegalStateException("shulker taking receipt disagrees with its source slot and player count");
                }
                cycle.takenContents = logistics.facts.enderContents;
            }
            if (transfer.purpose.equals("store")) recordStorage(ctx, logistics, cycle, contents);
        }
        transfer.pending = false;
        logistics.save(ctx, "transfer confirmed " + transfer.role + " " + transfer.purpose);
    }

    private static void recordStorage(TickContext ctx, LogisticsContext logistics,
                                      ShulkerCycleFacts cycle, List<ItemStack> contents) {
        List<ItemStack> before = LogisticsInventory.decode(logistics.facts.transfer.contents, 27, logistics.world);
        ShulkerSignature signature = ShulkerSignature.decode(cycle.signature, logistics.world);
        int receiver = -1;
        for (int i = 0; i < contents.size(); i++) {
            if (!before.get(i).isEmpty() || !signature.matches(contents.get(i))) continue;
            if (receiver >= 0) throw new IllegalStateException("several slots received the stored shulker");
            receiver = i;
        }
        if (receiver < 0) throw new IllegalStateException("stored shulker has no confirmed receiving slot");
        int baseline = logistics.equipment(logistics.facts.transfer.role).beforePickup();
        if (logistics.world.countMatching(signature::matches) != baseline) {
            throw new IllegalStateException("stored shulker has an unexpected player count");
        }
        cycle.storageSlot = receiver;
        cycle.storedContents = logistics.facts.enderContents;
        String role = logistics.facts.transfer.role;
        if (!role.equals("material")) {
            logistics.mission.equipment(ctx, "logistics-" + role, cycle.signature);
            logistics.mission.equipment(ctx, "logistics-" + role + "-slot", Integer.toString(receiver));
        }
        logistics.equipment(role).milestone(ctx, "stored-in-ender-chest");
    }

    public static void observeEnderChest(TickContext ctx, LogisticsContext logistics) {
        List<ItemStack> contents = logistics.outputs.inventory().containerContents();
        logistics.facts.enderContents = LogisticsInventory.encode(contents, logistics.world);
        List<ShulkerSignature> signatures = contents.stream().filter(ShulkerSignature::isShulker)
            .map(stack -> ShulkerSignature.of(stack, logistics.world)).toList();
        logistics.mission.carried(ctx, logistics.facts.step,
            signatures.stream().filter(sig -> LogisticsInventory.full(sig, logistics.profile)).count());
        logistics.mission.inProgress(ctx, logistics.facts.step,
            signatures.stream().filter(sig -> !sig.empty() && LogisticsInventory.pure(sig, logistics.profile)
                && !LogisticsInventory.full(sig, logistics.profile)).count());
        logistics.save(ctx, "ender chest contents confirmed");
    }
}
