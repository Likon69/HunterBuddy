package com.hunterbuddy.behavior.core;

import java.util.Objects;

public record Running(String reason) implements NodeResult {
    public static final Running WORKING = new Running("working");

    public Running {
        Objects.requireNonNull(reason);
    }
}
