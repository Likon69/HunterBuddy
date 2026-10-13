package com.hunterbuddy.behavior.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class Blackboard {
    private final Map<String, Map<Key<?>, Object>> owners = new HashMap<>();
    private final Map<String, Map<String, Map<Key<?>, Object>>> worlds = new HashMap<>();
    private final Map<String, Deque<Map<Key<?>, Object>>> tasks = new HashMap<>();
    private String world = "unbound";

    public void world(String identity) {
        world = Objects.requireNonNull(identity);
    }

    public <T> Optional<T> get(String owner, Key<T> key) {
        return Optional.ofNullable(key.type().cast(values(owner, key.scope()).get(key)));
    }

    public <T> void set(String owner, Key<T> key, T value) {
        values(owner, key.scope()).put(key, key.type().cast(Objects.requireNonNull(value)));
    }

    public void remove(String owner, Key<?> key) {
        values(owner, key.scope()).remove(key);
        if (key.durable()) values(owner, Scope.WORLD).remove(key);
    }

    public void openTask(String owner) {
        tasks.computeIfAbsent(owner, ignored -> new ArrayDeque<>()).push(new HashMap<>());
    }

    public void closeTask(String owner) {
        Deque<Map<Key<?>, Object>> stack = tasks.get(owner);
        if (stack == null || stack.isEmpty()) throw new IllegalStateException("no TASK for " + owner);
        stack.pop();
        if (stack.isEmpty()) tasks.remove(owner);
    }

    public void clear(String owner, Scope scope) {
        if (scope == Scope.TASK && !tasks.containsKey(owner)) return;
        values(owner, scope).clear();
    }

    public void removeOwner(String owner) {
        Map<Key<?>, Object> memory = owners.remove(owner);
        if (memory != null) {
            Map<Key<?>, Object> durable = values(owner, Scope.WORLD);
            memory.forEach((key, value) -> {
                if (key.durable()) durable.put(key, value);
            });
        }
        tasks.remove(owner);
    }

    public void restoreOwner(String owner) {
        values(owner, Scope.WORLD).forEach((key, value) -> {
            if (key.durable() && key.scope() == Scope.OWNER) values(owner, Scope.OWNER).put(key, value);
        });
    }

    public Map<Key<?>, Object> durable(String owner) {
        Map<Key<?>, Object> durable = new HashMap<>(values(owner, Scope.WORLD));
        values(owner, Scope.OWNER).forEach((key, value) -> { if (key.durable()) durable.put(key, value); });
        durable.entrySet().removeIf(entry -> !entry.getKey().durable());
        return Map.copyOf(durable);
    }

    public void loadDurable(String owner, Map<Key<?>, Object> memory) {
        memory.forEach((key, value) -> {
            if (key.durable() && key.scope() != Scope.TASK) values(owner, Scope.WORLD).put(key, key.type().cast(value));
        });
        restoreOwner(owner);
    }

    private Map<Key<?>, Object> values(String owner, Scope scope) {
        Objects.requireNonNull(owner);
        return switch (scope) {
            case OWNER -> owners.computeIfAbsent(owner, ignored -> new HashMap<>());
            case WORLD -> worlds.computeIfAbsent(world, ignored -> new HashMap<>())
                .computeIfAbsent(owner, ignored -> new HashMap<>());
            case TASK -> {
                Deque<Map<Key<?>, Object>> stack = tasks.get(owner);
                if (stack == null || stack.isEmpty()) throw new IllegalStateException("no TASK for " + owner);
                yield stack.peek();
            }
        };
    }
}
