package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.*;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.*;
import com.hunterbuddy.behavior.resource.*;
import com.hunterbuddy.util.QuietClicks;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;

public final class PrepareInventory extends Action {
    private final InventoryLayout layout;
    private final InventoryBridge inventory;
    private final MissionJournal journal;
    private final Predicate<ItemStack> held;
    private final int target;
    private InventoryBridge.Ticket ticket;
    private int attempts;

    public PrepareInventory(InventoryLayout layout, InventoryBridge inventory, MissionJournal journal) {
        this(layout, inventory, journal, null, -1);
    }

    public PrepareInventory(InventoryLayout layout, InventoryBridge inventory, MissionJournal journal, Predicate<ItemStack> held, int target) {
        super("prepare-inventory");
        this.layout = layout;
        this.inventory = inventory;
        this.journal = journal;
        this.held = held;
        this.target = target;
        limit(400);
    }

    @Override
    protected void onActionStart(TickContext ctx) { ticket = null; attempts = 0; }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        Lease lease = ctx.leases().holder(Resource.INVENTORY).filter(l -> l.owner().equals(ctx.owner())).orElseThrow();
        if (ticket != null) {
            if (ticket.failed()) return fail(FailureCode.PREEMPTED, inventory.status());
            if (ticket.expired(ctx.nowMs() - ctx.clock().frozenMs())) return fail(FailureCode.TIMEOUT, "player click unconfirmed");
            if (!ticket.confirmed()) return new Running("waiting for player inventory confirmation");
            inventory.release(lease);
            ticket = null;
        }
        if (!inventory.claimPlayer(lease)) return fail(FailureCode.REFUSED, "player inventory unavailable or foreign screen");
        int from = -1;
        int to = -1;
        if (held != null) {
            if (held.test(layout.stack(target))) return Success.INSTANCE;
            from = layout.find(held, 0);
            to = target;
        } else {
            for (int i = 0; i < layout.size(); i++) {
                if (layout.filter(i).test(layout.stack(i))) continue;
                from = layout.find(layout.filter(i), i + 1);
                if (from < 0) return fail(FailureCode.MISSING_ITEM, "hotbar requirement missing slot=" + i);
                to = i;
                break;
            }
            if (to < 0) {
                if (layout.stack(8).isEmpty()) return Success.INSTANCE;
                int empty = layout.find(ItemStack::isEmpty, layout.size());
                if (empty < 0) return fail(FailureCode.MISSING_ITEM, "inventory needs a free storage slot");
                from = empty;
                to = 8;
            }
        }
        if (from < 0) return fail(FailureCode.MISSING_ITEM, "required item missing");
        if (!QuietClicks.readyOrSettle()) return new Running("settling player inventory");
        if (++attempts > 18) return fail(FailureCode.BUDGET, "layout click budget exhausted");
        String owner = ctx.owner();
        ticket = inventory.click(inventory.playerSwap(from, to), lease, ctx.nowMs() - ctx.clock().frozenMs(),
            detail -> journal.milestone(ctx, owner, "transferred player layout " + detail));
        return new Running("organizing hotbar");
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) { if (ticket != null) ticket.delay(ms); }
}
