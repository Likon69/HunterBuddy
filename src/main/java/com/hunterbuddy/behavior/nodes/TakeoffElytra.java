package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.PostTickable;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.core.RocketAction;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.SlotIntent;
import com.hunterbuddy.util.Rockets;

public final class TakeoffElytra extends RocketAction implements PostTickable {
    private final Key<Integer> slotKey;
    private final WorldReader world;
    private final Outputs outputs;
    private Stage stage;

    public TakeoffElytra(Key<Integer> slotKey, WorldReader world, Outputs outputs) {
        super("takeoff-elytra"); this.slotKey = slotKey; this.world = world; this.outputs = outputs;
    }

    @Override
    protected void onActionStart(TickContext ctx) { stage = Stage.READY; }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (stage == Stage.ACTIVE && !outputs.flight().running() && !outputs.flight().succeeded() && outputs.flight().failure() == null) stage = Stage.READY;
        int slot = ctx.bb().get(ctx.owner(), slotKey).orElse(-1);
        if (stage == Stage.READY && !outputs.flight().canTakeoff(slot)) return fail(FailureCode.MISSING_ITEM, "usable elytra and plain hotbar rockets above the reserve required");
        outputs.slot(ctx.owner(), new SlotIntent(slot, true));
        if (ctx.sensors().selectedSlot() != slot || ctx.sensors().serverSlot() != slot || !outputs.slotSettled(slot)) return new Running("settling takeoff rocket slot");
        if (stage == Stage.READY) {
            outputs.flight().start(ctx, slot);
            stage = Stage.ACTIVE;
        }
        if (outputs.flight().succeeded()) {
            return ctx.progress(new ProgressEvidence(EvidenceKind.MILESTONE, 1, "native takeoff confirmed")) ? Success.INSTANCE : new Running("takeoff confirmation rejected=setback");
        }
        if (outputs.flight().failure() != null) return fail(FailureCode.NOT_CONFIRMED, outputs.flight().failure());
        outputs.look(ctx.owner(), new LookIntent(ctx.sensors().yaw(), ctx.sensors().gliding() ? -20 : -60));
        outputs.flight().pre(ctx);
        outputs.movement(ctx.owner(), new MovementIntent(false, false, false, false, outputs.flight().jumpPressed(), false, false));
        return new Running("native ElytraTakeoff Pre/Post");
    }

    @Override
    public void tickPost(TickContext ctx) { if (stage == Stage.ACTIVE) outputs.flight().post(ctx); }
    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) { ctx.bb().set(ctx.owner(), slotKey, world.findSlot(Rockets::isPlain)); }
    @Override
    protected void onSuspend(TickContext ctx) { outputs.flight().cancel(); stage = Stage.READY; }
    @Override
    protected void onActionStop(TickContext ctx, ExitReason reason) { outputs.flight().cancel(); }
    private enum Stage { READY, ACTIVE }
}
