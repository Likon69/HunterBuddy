package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.OperationFailure;
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
            if (result instanceof Failure failure && (failure.code() == FailureCode.MISSION_STOP
                || failure.code() == FailureCode.NOT_CONFIRMED || failure.code() == FailureCode.FAULT)) {
                stop(ctx, "[" + failure.code() + "] " + failure.reason());
            }
            return result;
        } catch (OperationFailure ex) {
            if (ex.code() == FailureCode.MISSION_STOP || ex.code() == FailureCode.NOT_CONFIRMED
                || ex.code() == FailureCode.FAULT) stop(ctx, ex.getMessage());
            return fail(ex.code(), ex.getMessage());
        } catch (RuntimeException ex) {
            stop(ctx, ex.getMessage());
            return fail(FailureCode.FAULT, ex.getMessage());
        }
    }

    private void stop(TickContext ctx, String reason) {
        logistics.mission.milestone(ctx, "logistics ambiguous: " + reason);
        ctx.stopBot(ExitReason.FAILED, "Logistics operation cannot be verified: " + reason);
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
