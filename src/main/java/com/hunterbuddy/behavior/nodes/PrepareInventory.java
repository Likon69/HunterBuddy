package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.InventoryBridge;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.resource.Lease;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Objects;
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

    public PrepareInventory(InventoryLayout layout, InventoryBridge inventory, MissionJournal journal, Predicate<ItemStack> held, int target) {
        super("prepare-inventory");
        this.layout = layout;
        this.inventory = inventory;
        this.journal = journal;
        this.held = Objects.requireNonNull(held);
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
        if (!inventory.claimPlayer(lease)) return fail(FailureCode.PREEMPTED, "player inventory unavailable or foreign screen");
        if (held.test(layout.stack(target))) return Success.INSTANCE;
        int from = layout.find(held, 0);
        int to = target;
        if (from < 0) return fail(FailureCode.MISSING_ITEM, "required item missing");
        if (!inventory.readyForClick(lease)) return new Running("settling player inventory");
        if (++attempts > 18) return fail(FailureCode.BUDGET, "layout click budget exhausted");
        String owner = ctx.owner();
        ticket = inventory.click(inventory.playerSwap(from, to), lease, ctx.nowMs() - ctx.clock().frozenMs(),
            detail -> journal.milestone(ctx, owner, "transferred player layout " + detail));
        return new Running("organizing hotbar");
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) { if (ticket != null) ticket.delay(ms); }
}
