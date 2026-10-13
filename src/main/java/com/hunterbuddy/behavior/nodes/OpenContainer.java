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
import net.minecraft.util.math.BlockPos;

public final class OpenContainer extends Action {
    private final BlockPos pos;
    private final InventoryBridge inventory;

    public OpenContainer(BlockPos pos, InventoryBridge inventory) {
        super("open-container");
        this.pos = Objects.requireNonNull(pos).toImmutable();
        this.inventory = Objects.requireNonNull(inventory);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (inventory.at(pos)) return Success.INSTANCE;
        if (inventory.container()) return fail(FailureCode.REFUSED, "another owned container must be closed before opening this position");
        if (ctx.sensors().screenOpen() && !ctx.sensors().screenOurs()) return fail(FailureCode.REFUSED, "player owns the screen");
        boolean sent = inventory.open(pos, ctx.leases().holder(Resource.SCREEN).filter(lease -> lease.owner().equals(ctx.owner())).orElseThrow());
        return sent ? Success.INSTANCE : new Running("waiting to open container");
    }
}
