package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.*;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.*;
import com.hunterbuddy.behavior.resource.*;
import com.hunterbuddy.util.QuietClicks;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.item.ItemStack;

public final class TransferItems extends Action {
    private final InventoryBridge inventory;
    private final Supplier<InventoryTransaction> transaction;
    private final Predicate<ItemStack> deposit;
    private final MissionJournal journal;
    private final String label;
    private InventoryBridge.Ticket ticket;
    private int transfers;

    public TransferItems(InventoryBridge inventory, Supplier<InventoryTransaction> transaction, Predicate<ItemStack> deposit, MissionJournal journal, String label) {
        super("transfer-items");
        this.inventory = inventory;
        this.transaction = transaction;
        this.deposit = deposit;
        this.journal = journal;
        this.label = label;
        limit(1_200);
    }

    @Override
    protected void onActionStart(TickContext ctx) { ticket = null; transfers = 0; }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        Lease lease = ctx.leases().holder(Resource.INVENTORY).filter(l -> l.owner().equals(ctx.owner())).orElseThrow();
        if (!inventory.container()) return fail(FailureCode.REFUSED, "owned open container required");
        if (ticket != null) {
            if (ticket.failed()) return fail(FailureCode.PREEMPTED, inventory.status());
            if (ticket.expired(ctx.nowMs() - ctx.clock().frozenMs())) return fail(FailureCode.TIMEOUT, "container transfer unconfirmed");
            if (!ticket.confirmed()) return new Running("waiting for source and destination receipts");
            transfers++;
            ctx.progress(new ProgressEvidence(EvidenceKind.MILESTONE, transfers, label));
            inventory.release(lease);
            ticket = null;
            if (deposit == null) return Success.INSTANCE;
        }
        if (deposit != null && !inventory.hasMatchingItem(deposit)) return Success.INSTANCE;
        if (transfers >= 72) return fail(FailureCode.BUDGET, "bounded deposit unfinished after 72 confirmed clicks");
        if (!QuietClicks.readyOrSettle()) return new Running("settling transfer click");
        try {
            String owner = ctx.owner();
            int count = transfers + 1;
            ticket = inventory.click(transaction.get(), lease, ctx.nowMs() - ctx.clock().frozenMs(),
                detail -> journal.milestone(ctx, owner, label + " transferred=" + count + " " + detail));
        } catch (IllegalStateException ex) { return fail(FailureCode.REFUSED, ex.getMessage()); }
        return new Running("confirming transfer");
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) { if (ticket != null) ticket.delay(ms); }
}
