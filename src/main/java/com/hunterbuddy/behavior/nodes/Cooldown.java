package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Function;

public final class Cooldown extends Decorator {
    private final int ticks;
    private Function<TickContext, String> target = ctx -> "default";
    private Key<Long> activeKey;

    public Cooldown(int ticks, Node child) {
        super("cooldown(" + ticks + ")", child);
        if (ticks < 0) throw new IllegalArgumentException("negative cooldown");
        this.ticks = ticks;
    }

    public Cooldown keyedBy(Function<TickContext, String> target) {
        if (active()) throw new IllegalStateException("cannot configure an active Cooldown");
        this.target = Objects.requireNonNull(target);
        return this;
    }

    @Override
    protected void onStart(TickContext ctx) {
        String key = Objects.requireNonNull(target.apply(ctx), "null cooldown target");
        activeKey = new Key<>("cooldown." + path() + "@" + key, Long.class, Scope.OWNER, false);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        long until = ctx.bb().get(ctx.owner(), activeKey).orElse(0L);
        if (ctx.tick() < until) return fail(FailureCode.COOLDOWN, "remaining=" + (until - ctx.tick()));
        NodeResult result = child().execute(ctx);
        if (!(result instanceof Running)) ctx.bb().set(ctx.owner(), activeKey, Math.addExact(ctx.tick(), ticks));
        return result;
    }
}
