package com.hunterbuddy.behavior.bot;

import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.routine.DeathPolicy;
import java.util.Objects;

public record BotConfig(DeathPolicy deathPolicy, boolean resumeAfterReconnect,
                        ProgressPolicy progress, int defaultActionLimitTicks) {
    public BotConfig {
        Objects.requireNonNull(deathPolicy);
        Objects.requireNonNull(progress);
        if (defaultActionLimitTicks < 1) throw new IllegalArgumentException("action limit must be positive");
    }
}
