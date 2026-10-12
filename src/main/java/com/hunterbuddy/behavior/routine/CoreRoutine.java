package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Guard;
import java.util.List;
import java.util.Objects;

public abstract class CoreRoutine implements Routine {
    private final String name;
    private final Hook slot;
    private final Contributor contributor;
    private RoutineContext context;
    private List<HookEntry> entries = List.of();
    private StopReport stopped;

    protected CoreRoutine(String name, Hook slot) {
        this(name, slot, Contributor.CORE);
    }

    protected CoreRoutine(String name, Hook slot, Contributor contributor) {
        this.name = Objects.requireNonNull(name);
        this.slot = Objects.requireNonNull(slot);
        this.contributor = Objects.requireNonNull(contributor);
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final Hook slot() {
        return slot;
    }

    @Override
    public final List<HookEntry> entries(RoutineContext context) {
        Objects.requireNonNull(context);
        if (this.context != null && this.context.ticks() != context.ticks()) {
            throw new IllegalStateException("routine belongs to another context: " + name);
        }
        if (this.context == null) {
            List<HookEntry> built = List.copyOf(buildEntries(context));
            TreeValidator.validate(built);
            entries = built;
            this.context = context;
        }
        return entries;
    }

    protected abstract List<HookEntry> buildEntries(RoutineContext context);

    protected abstract String readyDetail();

    protected final HookEntry entry(Condition condition, Node node) {
        return new HookEntry(slot, "core:" + name, contributor, 0, new Guard(condition, node), true, node);
    }

    @Override
    public final void onInstall(RoutineContext context) {
        entries(context);
        if (entries.stream().anyMatch(entry -> entry.guard().active())) {
            throw new IllegalStateException("cannot install active routine: " + name);
        }
        stopped = null;
    }

    @Override
    public final void onUninstall(StopReport report) {
        Objects.requireNonNull(report);
        if (entries.stream().anyMatch(entry -> entry.guard().active())) {
            throw new IllegalStateException("cannot uninstall active routine: " + name);
        }
        stopped = report;
    }

    @Override
    public final RoutineStatus status() {
        if (stopped != null) return new RoutineStatus(name, slot, "stopped", stopped.detail());
        return entries.stream().filter(entry -> entry.guard().active()).findFirst()
            .map(entry -> new RoutineStatus(name, slot, "active", entry.node().name()))
            .orElseGet(() -> new RoutineStatus(name, slot, "ready", readyDetail()));
    }
}
