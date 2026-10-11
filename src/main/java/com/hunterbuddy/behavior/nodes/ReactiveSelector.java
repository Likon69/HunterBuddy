package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Set;

public final class ReactiveSelector extends Composite {
    private final long[] cooldownUntil;
    private int selected = -1;

    public ReactiveSelector(String name, Node... children) {
        super(name, children);
        cooldownUntil = new long[children.length];
        for (int i = 0; i < children.length; i++) {
            if (children[i].active()) {
                if (selected >= 0) throw new IllegalArgumentException("multiple active ReactiveSelector branches");
                selected = i;
            }
        }
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        int candidate = firstEligible(ctx, 0, selected < 0 ? children().size() : selected + 1);
        if (selected >= 0 && candidate != selected) {
            children().get(selected).halt(ctx, candidate >= 0 ? ExitReason.PREEMPTED : ExitReason.GUARD_LOST);
            int after = selected + 1;
            selected = -1;
            if (candidate < 0) candidate = firstEligible(ctx, after, children().size());
        }
        while (candidate >= 0) {
            Guard guard = (Guard) children().get(candidate);
            selected = candidate;
            NodeResult result = guard.execute(ctx);
            if (result instanceof Running) return result;
            selected = -1;
            if (!(result instanceof Failure)) return result;
            cooldownUntil[candidate] = ctx.tick() + guard.failureCooldown();
            candidate = firstEligible(ctx, candidate + 1, children().size());
        }
        return fail(FailureCode.NO_OPTION, name());
    }

    private int firstEligible(TickContext ctx, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!(children().get(i) instanceof Guard guard)) throw new IllegalStateException("ReactiveSelector child must be Guard");
            if (ctx.tick() >= cooldownUntil[i] && guard.eligible(ctx)) return i;
        }
        return -1;
    }

    public void detachForRebuild(Set<Node> retained) {
        detach(retained);
        selected = -1;
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        selected = -1;
    }
}
