package com.hunterbuddy.behavior.core;

import java.util.Objects;

public record ProgressEvidence(EvidenceKind kind, double gain, String reason) {
    public ProgressEvidence {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(reason);
        if (!Double.isFinite(gain) || gain <= 0) throw new IllegalArgumentException("progress gain must be positive");
    }
}
