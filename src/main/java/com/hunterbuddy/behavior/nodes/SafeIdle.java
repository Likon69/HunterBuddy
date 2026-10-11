package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;
import java.util.function.Consumer;

public final class SafeIdle extends Node {
    private final Consumer<String> alert;

    public SafeIdle(Consumer<String> alert) {
        super("safe-idle");
        this.alert = Objects.requireNonNull(alert);
    }

    @Override
    protected void onStart(TickContext ctx) {
        ctx.leases().releaseAll();
        ctx.event(this, Kind.RECOVERY, null, "safe idle", "leases=0");
        alert.accept("SafeIdle: resources released");
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return new Running("safe idle");
    }
}
