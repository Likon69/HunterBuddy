package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Arrays;
import java.util.Objects;

public final class Parallel extends Composite {
    private final ParallelPolicy policy;
    private final RestartPolicy restartPolicy;
    private final boolean[] terminal;
    private final int[] restarts;

    public Parallel(String name, ParallelPolicy policy, RestartPolicy restartPolicy, Node... children) {
        super(name, children);
        this.policy = Objects.requireNonNull(policy);
        this.restartPolicy = Objects.requireNonNull(restartPolicy);
        if (policy == ParallelPolicy.MAIN && children().isEmpty()) throw new IllegalArgumentException("MAIN needs a principal child");
        if (policy == ParallelPolicy.ALL && restartPolicy.maxRestarts() != 0) {
            throw new IllegalArgumentException("ALL does not restart completed children");
        }
        terminal = new boolean[children().size()];
        restarts = new int[children().size()];
    }

    @Override
    protected void onStart(TickContext ctx) {
        clear();
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return policy == ParallelPolicy.MAIN ? tickMain(ctx) : tickAll(ctx);
    }

    private NodeResult tickMain(TickContext ctx) {
        NodeResult result = children().getFirst().execute(ctx);
        if (!(result instanceof Running)) return result;
        for (int i = 1; i < children().size(); i++) {
            if (terminal[i]) {
                if (restarts[i] >= restartPolicy.maxRestarts()) continue;
                children().get(i).resetMemory();
                restarts[i]++;
                terminal[i] = false;
            }
            terminal[i] = !(ctx.executeMonitor(children().get(i)) instanceof Running);
        }
        return result;
    }

    private NodeResult tickAll(TickContext ctx) {
        boolean complete = true;
        for (int i = 0; i < children().size(); i++) {
            if (terminal[i]) continue;
            NodeResult result = children().get(i).execute(ctx);
            if (result instanceof Failure) return result;
            if (result instanceof Success) terminal[i] = true;
            else complete = false;
        }
        return complete ? Success.INSTANCE : Running.WORKING;
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        clear();
    }

    @Override
    protected void onReset() {
        clear();
    }

    private void clear() {
        Arrays.fill(terminal, false);
        Arrays.fill(restarts, 0);
    }
}
