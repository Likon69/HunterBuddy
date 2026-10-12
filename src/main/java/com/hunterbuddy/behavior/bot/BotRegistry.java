package com.hunterbuddy.behavior.bot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class BotRegistry {
    private final Map<String, Supplier<? extends Bot>> factories = new LinkedHashMap<>();

    public void register(String name, Supplier<? extends Bot> factory) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("empty bot name");
        Objects.requireNonNull(factory);
        if (factories.containsKey(name)) throw new IllegalArgumentException("duplicate bot: " + name);
        factories.put(name, factory);
    }

    public List<String> names() {
        return List.copyOf(factories.keySet());
    }

    public Bot create(String name) {
        Supplier<? extends Bot> factory = factories.get(Objects.requireNonNull(name));
        if (factory == null) throw new IllegalArgumentException("unknown bot: " + name);
        Bot bot = Objects.requireNonNull(factory.get(), "bot factory returned null: " + name);
        if (!name.equals(bot.name())) throw new IllegalArgumentException("bot factory name mismatch: " + name);
        return bot;
    }
}
