package com.hunterbuddy.behavior.core;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public record CandidateSelection<T>(Optional<T> candidate, long retryAtMs) {
    public CandidateSelection {
        Objects.requireNonNull(candidate);
        if (retryAtMs < 0) throw new IllegalArgumentException("negative retry deadline");
    }

    public boolean exhausted() {
        return candidate.isEmpty() && retryAtMs == 0;
    }

    public <R> CandidateSelection<R> map(Function<? super T, ? extends R> mapping) {
        Objects.requireNonNull(mapping);
        return new CandidateSelection<>(candidate.map(value -> Objects.requireNonNull(mapping.apply(value))), retryAtMs);
    }
}
