package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.Outputs;

public final class WaterJumpTap extends Action {
    private final float pitch;
    private final WorldReader world;
    private final Outputs outputs;
    private int ticks;

    public WaterJumpTap(float pitch, WorldReader world, Outputs outputs) {
        super("water-jump");
        if (!Float.isFinite(pitch) || pitch < -90 || pitch > 90) throw new IllegalArgumentException("invalid water jump pitch");
        this.pitch = pitch; this.world = world; this.outputs = outputs; limit(40);
    }

    @Override
    protected void onActionStart(TickContext ctx) { ticks = 0; }
    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (ctx.sensors().gliding()) return Success.INSTANCE;
        if (!world.elytraEquipped()) return fail(FailureCode.MISSING_ITEM, "usable elytra required");
        boolean pressed = ticks++ % 2 == 0;
        outputs.flight().jump(ctx, pressed);
        outputs.look(ctx.owner(), new LookIntent(ctx.sensors().yaw(), pitch));
        outputs.movement(ctx.owner(), new MovementIntent(true, false, false, false, pressed, false, ctx.sensors().food() > 6));
        return new Running("vanilla water jump tap");
    }

    @Override
    protected void onSuspend(TickContext ctx) { outputs.flight().endJump(); }
    @Override
    protected void onActionStop(TickContext ctx, ExitReason reason) { outputs.flight().endJump(); }
}
