package com.hunterbuddy.behavior.core;

import java.util.Objects;

public record Key<T>(String name, Class<T> type, Scope scope, boolean durable) {
    public Key {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("empty key name");
        Objects.requireNonNull(type);
        Objects.requireNonNull(scope);
        if (durable && scope == Scope.TASK) throw new IllegalArgumentException("TASK cannot be durable");
    }
}
