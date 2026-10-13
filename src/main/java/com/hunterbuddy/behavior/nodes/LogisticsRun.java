package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.LogisticsContext;

public final class LogisticsRun extends Decorator {
    private final LogisticsContext logistics;

    public LogisticsRun(LogisticsContext logistics, Node child) {
        super("logistics-evidence-scope", child);
        this.logistics = logistics;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        try {
            NodeResult result = child().execute(ctx);
            if (result instanceof Failure failure) stop(ctx, failure.reason());
            return result;
        } catch (RuntimeException ex) {
            stop(ctx, ex.getMessage());
            return fail(FailureCode.REFUSED, ex.getMessage());
        }
    }

    private void stop(TickContext ctx, String reason) {
        logistics.mission.milestone(ctx, "logistics ambiguous: " + reason);
        ctx.stopBot(ExitReason.FAILED, "Opération logistique impossible à vérifier: " + reason);
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        if (logistics.worldChanged() || !logistics.outputs.inventory().container()) {
            child().halt(ctx, ExitReason.PREEMPTED);
            child().resetMemory();
        }
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        logistics.outputs.mining().releaseExit();
        logistics.outputs.mining().breakRule(null);
    }

    @Override
    protected void onSuspend(TickContext ctx) {
        logistics.outputs.mining().releaseExit();
    }
}
