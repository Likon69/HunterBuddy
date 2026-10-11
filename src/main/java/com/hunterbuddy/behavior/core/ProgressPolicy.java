package com.hunterbuddy.behavior.core;

import java.util.EnumSet;
import java.util.Set;

public record ProgressPolicy(Set<EvidenceKind> accepted, long noProgressMs) {
    public static final ProgressPolicy DEFAULT = new ProgressPolicy(EnumSet.allOf(EvidenceKind.class), 180_000);

    public ProgressPolicy {
        accepted = Set.copyOf(accepted);
        if (noProgressMs < 1) throw new IllegalArgumentException("progress window must be positive");
    }
}
