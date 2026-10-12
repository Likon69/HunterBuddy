package com.hunterbuddy.behavior.plugin;

import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public record PluginContext(TickContext ticks, String owner) {
    public PluginContext {
        Objects.requireNonNull(ticks);
        if (owner == null || !owner.startsWith("plugin:") || owner.substring(7).isBlank()) {
            throw new IllegalArgumentException("invalid plugin owner");
        }
    }
}
