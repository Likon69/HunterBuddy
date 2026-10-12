package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.LookAt;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Objects;
import java.util.Set;

public record LookTask(LookIntent look, double turnRate) implements LabTask {
    private static final Key<LookIntent> TARGET = new Key<>("lab-look-target", LookIntent.class, Scope.OWNER, false);
    private static final int LIMIT_TICKS = 5;

    public LookTask {
        Objects.requireNonNull(look);
        if (!Double.isFinite(turnRate) || turnRate <= 0 || turnRate > 360) throw new IllegalArgumentException("invalid lab turn rate");
    }

    @Override
    public String name() {
        return "look";
    }

    @Override
    public String target() {
        return look.toString();
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.LOOK);
    }

    @Override
    public void initialize(BotContext context) {
        context.ticks().bb().set(context.owner(), TARGET, look);
    }

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        return new Timeout(Math.min(LIMIT_TICKS, context.config().defaultActionLimitTicks()),
            new WithLease(resources(), LeasePolicy.WAIT, LookAt.angles(TARGET, turnRate, outputs)));
    }
}
