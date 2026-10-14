package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.Outputs;

public final class RedeployGlide extends Action {
    private final WorldReader world;
    private final Outputs outputs;
    private int ticks;

    public RedeployGlide(WorldReader world, Outputs outputs) {
        super("redeploy-glide-during-fall");
        this.world = world;
        this.outputs = outputs;
        limit(200);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        ticks = 0;
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (ctx.sensors().gliding() || ctx.sensors().onGround() || ctx.sensors().inWater()
            || ctx.sensors().submerged()) return Success.INSTANCE;
        boolean jump = world.elytraEquipped() && ctx.sensors().velocityY() < 0 && ticks++ % 2 == 0;
        outputs.movement(ctx.owner(), new MovementIntent(false, false, false, false, jump, false, false));
        return new Running("waiting for vanilla glide redeployment or supported landing");
    }
}
