package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.BaseVisitContext;

public final class BaseVisitScope extends Decorator {
    private final BaseVisitContext visit;

    public BaseVisitScope(BaseVisitContext visit, Node child) {
        super("visite-base-preuves-durables", child);
        this.visit = visit;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        try {
            if (visit.facts != null && !visit.facts.world.equals(visit.logistics.world.storageKey())) {
                return fail(FailureCode.REFUSED, "base visit interrupted by a world change");
            }
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

    private void stop(TickContext ctx, String cause) {
        visit.logistics.mission.milestone(ctx, "base interrupted: " + cause);
        ctx.stopBot(ExitReason.FAILED, "Base visit stopped: " + cause);
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        child().halt(ctx, ExitReason.PREEMPTED);
        child().resetMemory();
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        visit.logistics.outputs.mining().breakRule(null);
    }
}
