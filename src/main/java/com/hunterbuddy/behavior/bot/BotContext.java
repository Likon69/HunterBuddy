package com.hunterbuddy.behavior.bot;

import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public record BotContext(TickContext ticks, String owner, BotConfig config) {
    public BotContext {
        Objects.requireNonNull(ticks);
        Objects.requireNonNull(config);
        if (owner == null || !owner.startsWith("bot:") || owner.substring(4).isBlank()) {
            throw new IllegalArgumentException("invalid bot owner");
        }
    }
}
