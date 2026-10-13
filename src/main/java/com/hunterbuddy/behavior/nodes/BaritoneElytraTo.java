package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.RocketAction;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.resource.Outputs;
import net.minecraft.util.math.BlockPos;

public final class BaritoneElytraTo extends RocketAction {
    private final Key<BlockPos> destination;
    private final BaritoneBridge bridge;
    private final Outputs outputs;
    private int waiting;

    public BaritoneElytraTo(Key<BlockPos> destination, BaritoneBridge bridge, Outputs outputs) {
        super("baritone-elytra"); this.destination = destination; this.bridge = bridge; this.outputs = outputs;
    }

    @Override
    protected void onActionStart(TickContext ctx) { waiting = 0; }
    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        BlockPos pos = ctx.bb().get(ctx.owner(), destination).orElse(null);
        if (pos == null) return fail(FailureCode.NO_OPTION, "elytra destination missing");
        if (!bridge.elytraAvailable()) return fail(FailureCode.UNSUPPORTED, "Baritone elytra is supported only in the Nether");
        if (!ctx.sensors().gliding() && Math.hypot(ctx.sensors().x() - pos.getX(), ctx.sensors().z() - pos.getZ()) <= 3) return Success.INSTANCE;
        outputs.elytra(ctx.owner(), pos, true);
        if (++waiting > 20 && !bridge.ownsFlight(pos)) return fail(FailureCode.UNREACHABLE, "elytra process ended before arrival");
        return new Running("Baritone elytra destination=" + pos);
    }
}
