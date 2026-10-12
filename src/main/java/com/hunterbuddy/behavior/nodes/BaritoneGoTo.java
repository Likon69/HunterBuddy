package com.hunterbuddy.behavior.nodes;

import baritone.api.pathing.goals.Goal;
import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.resource.BaritoneIntent;
import com.hunterbuddy.behavior.resource.BaritoneOutput;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;

public final class BaritoneGoTo extends Action {
    private final Key<Goal> goalKey;
    private final boolean allowBreak;
    private final boolean allowPlace;
    private final BaritoneBridge bridge;
    private final Outputs outputs;
    private BaritoneIntent intent;
    private long failures;
    private long startedAt;
    private Stage stage;

    public BaritoneGoTo(Key<Goal> goalKey, boolean allowBreak, boolean allowPlace, BaritoneBridge bridge, Outputs outputs) {
        super("baritone");
        this.goalKey = Objects.requireNonNull(goalKey);
        this.allowBreak = allowBreak;
        this.allowPlace = allowPlace;
        this.bridge = Objects.requireNonNull(bridge);
        this.outputs = Objects.requireNonNull(outputs);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        Goal goal = ctx.bb().get(ctx.owner(), goalKey).orElse(null);
        intent = goal == null ? null : new BaritoneIntent(goal, allowBreak, allowPlace);
        startedAt = outputs.nativeTick();
        failures = bridge.failures();
        Key<Integer> attempts = attempts(ctx);
        stage = ctx.bb().get(ctx.owner(), attempts).orElse(0) >= 3 ? Stage.EXHAUSTED : Stage.READY;
    }

    private Key<Integer> attempts(TickContext ctx) {
        return new Key<>("baritone-starts/" + target(ctx), Integer.class, Scope.OWNER, false);
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        startedAt += ticks;
        failures = bridge.failures();
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (intent == null) return fail(FailureCode.NO_OPTION, "Baritone goal is missing");
        if (intent.goal().isInGoal((int) Math.floor(ctx.sensors().x()), (int) Math.floor(ctx.sensors().y()), (int) Math.floor(ctx.sensors().z()))) {
            return ctx.monitor().samplesValid(ctx.tick()) ? Success.INSTANCE : new Running("arrival rejected=setback");
        }
        if (stage == Stage.EXHAUSTED) return fail(FailureCode.BUDGET, "Baritone starts=3 for target");
        if (stage == Stage.READY && outputs.baritoneStarted(ctx.owner(), intent)) {
            stage = Stage.RUNNING;
            startedAt = outputs.nativeTick();
        }
        if (stage == Stage.RUNNING && bridge.failures() > failures) return fail(FailureCode.UNREACHABLE, "Baritone calculation failed");
        var snapshot = bridge.capture();
        if (stage == Stage.RUNNING && outputs.nativeTick() - startedAt >= 20 && !snapshot.pathing() && !snapshot.goalActive() && !snapshot.calcInProgress()) {
            return fail(FailureCode.UNREACHABLE, "Baritone stopped before arrival");
        }
        String owner = ctx.owner();
        Key<Integer> attempts = attempts(ctx);
        outputs.baritone(owner, intent, signal -> {
            if (signal == BaritoneOutput.Signal.STARTED) ctx.bb().set(owner, attempts, ctx.bb().get(owner, attempts).orElse(0) + 1);
            ctx.event(this, Kind.LEASE, null, signal.detail(), "");
        });
        return new Running("Baritone goal=" + intent.goal());
    }

    private enum Stage {
        READY, RUNNING, EXHAUSTED
    }
}
