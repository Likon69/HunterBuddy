package com.hunterbuddy.behavior.core;

import java.util.Objects;

public record StopReport(ExitReason reason, String detail, String owner, long tick, long nowMs) {
    public StopReport {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(owner);
    }
}
