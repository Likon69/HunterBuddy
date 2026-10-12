package com.hunterbuddy.behavior.resource;

import baritone.api.pathing.goals.Goal;
import java.util.Objects;

public record BaritoneIntent(Goal goal, boolean allowBreak, boolean allowPlace) {
    public BaritoneIntent {
        Objects.requireNonNull(goal);
    }
}
