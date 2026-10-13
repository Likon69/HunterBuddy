package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
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
                throw new IllegalStateException("visite de base interrompue par un changement de monde");
            }
            NodeResult result = child().execute(ctx);
            if (result instanceof Failure failure) stop(ctx, failure.reason());
            return result;
        } catch (RuntimeException ex) {
            stop(ctx, ex.getMessage());
            return fail(FailureCode.REFUSED, ex.getMessage());
        }
    }

    private void stop(TickContext ctx, String cause) {
        visit.logistics.mission.milestone(ctx, "base interrompue : " + cause);
        ctx.stopBot(ExitReason.FAILED, "Visite de base arrêtée : " + cause);
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
