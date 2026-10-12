package com.hunterbuddy.behavior.bot;

import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.hooks.Hook;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class BotStats {
    public static final Snapshot EMPTY = new BotStats().snapshot();
    private Counters session = new Counters();
    private final Counters cumulative = new Counters();

    public void startSession() {
        session = new Counters();
    }

    public void sampleTime(long activeMs, Hook hook) {
        if (activeMs < 0) throw new IllegalArgumentException("negative active time");
        session.sampleTime(activeMs, hook);
        cumulative.sampleTime(activeMs, hook);
    }

    public void evidence(ProgressEvidence evidence) {
        Objects.requireNonNull(evidence);
        session.evidence(evidence);
        cumulative.evidence(evidence);
    }

    public void stopped(ExitReason reason) {
        Objects.requireNonNull(reason);
        session.stopped(reason);
        cumulative.stopped(reason);
    }

    public Snapshot snapshot() {
        return new Snapshot(session.snapshot(), cumulative.snapshot());
    }

    public record Snapshot(Values session, Values cumulative) {
        public Snapshot {
            Objects.requireNonNull(session);
            Objects.requireNonNull(cumulative);
        }
    }

    public record Values(long activeMs, Map<Hook, Long> hookMs, Map<EvidenceKind, Long> proofs,
                         Map<EvidenceKind, Double> gains, long deaths, long faults) {
        public Values {
            hookMs = Map.copyOf(hookMs);
            proofs = Map.copyOf(proofs);
            gains = Map.copyOf(gains);
        }

        public long proofCount() {
            return proofs.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    private static final class Counters {
        private long activeMs;
        private final Map<Hook, Long> hookMs = new EnumMap<>(Hook.class);
        private final Map<EvidenceKind, Long> proofs = new EnumMap<>(EvidenceKind.class);
        private final Map<EvidenceKind, Double> gains = new EnumMap<>(EvidenceKind.class);
        private long deaths;
        private long faults;

        private void sampleTime(long elapsedMs, Hook hook) {
            activeMs += elapsedMs;
            if (elapsedMs > 0 && hook != null) hookMs.merge(hook, elapsedMs, Long::sum);
        }

        private void evidence(ProgressEvidence evidence) {
            proofs.merge(evidence.kind(), 1L, Long::sum);
            gains.merge(evidence.kind(), evidence.gain(), Double::sum);
        }

        private void stopped(ExitReason reason) {
            if (reason == ExitReason.DEAD) deaths++;
            if (reason == ExitReason.FAULT) faults++;
        }

        private Values snapshot() {
            return new Values(activeMs, hookMs, proofs, gains, deaths, faults);
        }
    }
}
