package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.TransferFacts;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.LogisticsTransferReceipt;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class ReconcileLogisticsTransfer extends Node {
    private final LogisticsContext logistics;
    private final String role;
    private final boolean inShulker;

    public ReconcileLogisticsTransfer(LogisticsContext logistics, String role, boolean inShulker) {
        super("réconcilier-transfert-" + role);
        this.logistics = logistics;
        this.role = role;
        this.inShulker = inShulker;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        TransferFacts transfer = logistics.facts.transfer;
        List<ItemStack> actual = logistics.outputs.inventory().containerContents();
        if (!transfer.pending) {
            if (inShulker && !ShulkerSignature.decode(logistics.cycle(role).signature, logistics.world).matchesContents(actual)) {
                throw new OperationFailure(FailureCode.MISSION_STOP, "shulker content changed without a transfer intent");
            }
            String expected = logistics.facts.enderContents;
            if (!inShulker && !expected.isEmpty()
                && !LogisticsInventory.equal(LogisticsInventory.decode(expected, 27, logistics.world), actual)) {
                throw new OperationFailure(FailureCode.MISSION_STOP, "ender chest changed without a transfer intent");
            }
            return Success.INSTANCE;
        }
        String container = inShulker ? "shulker" : "chest";
        if (!transfer.container.equals(container) || !transfer.role.equals(role)) {
            throw new IllegalStateException("pending transfer belongs to another container or shulker role");
        }
        List<ItemStack> beforePlayer = LogisticsInventory.decode(transfer.player, 36, logistics.world);
        List<ItemStack> beforeContainer = LogisticsInventory.decode(transfer.contents, 27, logistics.world);
        List<ItemStack> player = LogisticsInventory.player(logistics.world);
        if (LogisticsInventory.equal(beforePlayer, player) && LogisticsInventory.equal(beforeContainer, actual)) {
            transfer.pending = false;
            logistics.save(ctx, "transfer not applied: both inventories unchanged");
            return Success.INSTANCE;
        }
        ItemStack stack = LogisticsInventory.decode(transfer.stack, 1, logistics.world).getFirst();
        if (!LogisticsInventory.authorizedTransfer(beforePlayer, beforeContainer, player, actual, stack, transfer.deposit)) {
            throw new OperationFailure(FailureCode.MISSION_STOP, "pending transfer fails exact item, component, direction and conservation checks");
        }
        LogisticsTransferReceipt.accept(ctx, logistics);
        return Success.INSTANCE;
    }
}
