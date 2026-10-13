package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.InventoryBridge;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Objects;

public final class CloseScreen extends Action {
    private final InventoryBridge inventory;

    public CloseScreen(InventoryBridge inventory) {
        super("close-screen");
        this.inventory = Objects.requireNonNull(inventory);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (ctx.leases().holder(Resource.SCREEN).filter(lease -> lease.owner().equals(ctx.owner())).isEmpty()) {
            return fail(FailureCode.LEASE_TIMEOUT, "SCREEN lease required");
        }
        if (!inventory.screenOpen()) return Success.INSTANCE;
        if (!inventory.isOurs()) return fail(FailureCode.REFUSED, "never close a player-owned screen");
        return inventory.close() ? Success.INSTANCE : new Running("waiting for empty cursor before closing");
    }
}
