package com.hunterbuddy.behavior.persist;

import java.util.Map;
import java.util.Objects;

public record StoreEntry(int schema, Map<String, Value> values) {
    public static final int SCHEMA = 1;

    public StoreEntry {
        values = Map.copyOf(values);
    }

    public record Value(String type, String scope, String json) {
        public Value {
            Objects.requireNonNull(type);
            Objects.requireNonNull(scope);
            Objects.requireNonNull(json);
        }
    }
}
