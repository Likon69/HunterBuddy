package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public record RoutineContext(TickContext ticks) {
    public RoutineContext {
        Objects.requireNonNull(ticks);
    }
}
