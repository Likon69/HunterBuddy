package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.InventoryBridge;
import com.hunterbuddy.behavior.mc.InventoryTransaction;
import com.hunterbuddy.behavior.resource.Lease;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;
import java.util.function.Supplier;

public final class ClickSlot extends Action {
    private final Supplier<InventoryTransaction> transaction;
    private final InventoryBridge inventory;
    private InventoryBridge.Ticket ticket;
    private Lease lease;

    public ClickSlot(Supplier<InventoryTransaction> transaction, InventoryBridge inventory) {
        super("click-slot");
        limit(100);
        this.transaction = Objects.requireNonNull(transaction);
        this.inventory = Objects.requireNonNull(inventory);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        ticket = null;
        lease = null;
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (ticket == null) {
            lease = ctx.leases().holder(Resource.INVENTORY).filter(holder -> holder.owner().equals(ctx.owner())).orElseThrow();
            if (!inventory.readyForClick(lease)) return new Running("settling inventory clicks");
            try {
                ticket = inventory.click(transaction.get(), lease, ctx.nowMs() - ctx.clock().frozenMs(),
                    detail -> ctx.event(this, Kind.PROGRESS, null, "TRANSACTION_CONFIRMED", detail));
            } catch (IllegalStateException ex) {
                return fail(FailureCode.REFUSED, ex.getMessage());
            }
        }
        if (ticket.confirmed()) {
            ctx.progress(new ProgressEvidence(EvidenceKind.MILESTONE, 1, "inventory transaction confirmed"));
            return Success.INSTANCE;
        }
        if (ticket.failed()) return fail(FailureCode.PREEMPTED, inventory.status());
        if (ticket.expired(ctx.nowMs() - ctx.clock().frozenMs())) return fail(FailureCode.TIMEOUT, "inventory confirmation exceeded 3000ms");
        return new Running("waiting for server inventory confirmation");
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) { if (ticket != null) ticket.delay(ms); }

    @Override
    protected void onActionStop(TickContext ctx, ExitReason reason) {
        if (lease != null && reason != ExitReason.COMPLETED) inventory.release(lease);
    }
}
