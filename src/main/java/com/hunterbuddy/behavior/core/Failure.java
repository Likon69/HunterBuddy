package com.hunterbuddy.behavior.core;

import java.util.Objects;

public record Failure(FailureCode code, String reason) implements NodeResult {
    public Failure {
        Objects.requireNonNull(code);
        Objects.requireNonNull(reason);
    }
}
