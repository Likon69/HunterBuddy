package com.hunterbuddy.behavior.bot;

import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.trace.TraceEvent;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BotStats {
    public static final Snapshot EMPTY = new BotStats().snapshot();
    private Counters session = new Counters();
    private Counters cumulative = new Counters();

    public void startSession() {
        session = new Counters();
    }

    public void mergeCumulative(Values previous) { cumulative.merge(new Counters(previous)); }

    public static Snapshot combine(Snapshot current, Values previous) {
        Counters total = new Counters(current.cumulative());
        total.merge(new Counters(previous));
        return new Snapshot(current.session(), total.snapshot());
    }

    public void reset() {
        session = new Counters();
        cumulative = new Counters();
    }

    public void add(String metric, double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("invalid metric increment");
        session.metrics.merge(metric, value, Double::sum);
        cumulative.metrics.merge(metric, value, Double::sum);
    }

    public void observe(TraceEvent event) {
        String metric = switch (event.kind()) {
            case SUCCESS -> event.hook() == Hook.BOT && event.nodePath().matches("root/BOT/[^/]+/[^/]+") ? "targetsSucceeded" : null;
            case FAILURE -> event.hook() == Hook.BOT && event.nodePath().matches("root/BOT/[^/]+/[^/]+") ? "targetsFailed" : null;
            case PROGRESS -> event.reason().equals("TRANSACTION_CONFIRMED") ? "transactions" : null;
            case RECOVERY -> event.reason().equals("safe idle") ? "safeIdle" : event.reason().equals("enter") ? "recovery." + event.detail() : null;
            case BOT -> event.reason().equals("reconnected") ? "reconnects" : event.reason().equals("restart") ? "restarts" : null;
            default -> null;
        };
        if (metric != null) add(metric, 1);
    }

    public void sampleTime(long activeMs, Hook hook) {
        if (activeMs < 0) throw new IllegalArgumentException("negative active time");
        session.sampleTime(activeMs, hook);
        cumulative.sampleTime(activeMs, hook);
    }

    public void samplePause(long elapsedMs, Hook hook) {
        if (elapsedMs < 0) throw new IllegalArgumentException("negative pause time");
        if (hook == null || elapsedMs == 0) return;
        session.hookMs.merge(hook, elapsedMs, Long::sum);
        cumulative.hookMs.merge(hook, elapsedMs, Long::sum);
        add("pausedMs", elapsedMs);
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
                         Map<EvidenceKind, Double> gains, long deaths, long faults, Map<String, Double> metrics) {
        public Values {
            hookMs = Map.copyOf(hookMs);
            proofs = Map.copyOf(proofs);
            gains = Map.copyOf(gains);
            metrics = Map.copyOf(metrics);
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
        private final Map<String, Double> metrics = new LinkedHashMap<>();

        private Counters() {
        }

        private Counters(Values values) {
            activeMs = Math.max(0, values.activeMs());
            values.hookMs().forEach((key, value) -> { if (value >= 0) hookMs.put(key, value); });
            values.proofs().forEach((key, value) -> { if (value >= 0) proofs.put(key, value); });
            values.gains().forEach((key, value) -> { if (Double.isFinite(value) && value >= 0) gains.put(key, value); });
            deaths = Math.max(0, values.deaths());
            faults = Math.max(0, values.faults());
            values.metrics().forEach((key, value) -> { if (Double.isFinite(value) && value >= 0) metrics.put(key, value); });
        }

        private void sampleTime(long elapsedMs, Hook hook) {
            activeMs += elapsedMs;
            if (elapsedMs > 0 && hook != null) hookMs.merge(hook, elapsedMs, Long::sum);
        }

        private void merge(Counters other) {
            activeMs += other.activeMs;
            deaths += other.deaths;
            faults += other.faults;
            other.hookMs.forEach((key, value) -> hookMs.merge(key, value, Long::sum));
            other.proofs.forEach((key, value) -> proofs.merge(key, value, Long::sum));
            other.gains.forEach((key, value) -> gains.merge(key, value, Double::sum));
            other.metrics.forEach((key, value) -> metrics.merge(key, value, Double::sum));
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
            return new Values(activeMs, hookMs, proofs, gains, deaths, faults, metrics);
        }
    }
}
