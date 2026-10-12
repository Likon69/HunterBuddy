package com.hunterbuddy.behavior.resource;

import com.hunterbuddy.behavior.hooks.Hook;
import java.util.Objects;
import java.util.Set;

public record GateOwner(String name, Hook hook, Set<Resource> resources, GatePolicy policy) {
    public GateOwner {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("empty gate owner");
        Objects.requireNonNull(hook);
        resources = Implications.expand(Set.copyOf(resources));
        Objects.requireNonNull(policy);
    }

    public String owner() {
        return "gate:" + name;
    }
}
