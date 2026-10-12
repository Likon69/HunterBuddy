package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Supplier;

public record RoutineContext(TickContext ticks, Supplier<DeathPolicy> deathPolicySource) {
    public RoutineContext(TickContext ticks) {
        this(ticks, () -> DeathPolicy.STOP);
    }

    public RoutineContext {
        Objects.requireNonNull(ticks);
        Objects.requireNonNull(deathPolicySource);
    }

    public DeathPolicy deathPolicy() {
        return Objects.requireNonNull(deathPolicySource.get());
    }
}
