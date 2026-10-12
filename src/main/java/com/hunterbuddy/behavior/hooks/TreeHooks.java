package com.hunterbuddy.behavior.hooks;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.ReactiveSelector;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class TreeHooks {
    private final List<Consumer<List<HookEntry>>> pending = new ArrayList<>();
    private final Consumer<String> rejected;
    private List<HookEntry> entries = List.of();
    private ReactiveSelector root = ReactiveSelector.hooks();
    private Recovery recovery;
    private final Map<Guard, Long> entryIds = new IdentityHashMap<>();
    private long nextId;

    public TreeHooks(Consumer<String> rejected) {
        this.rejected = Objects.requireNonNull(rejected);
    }

    public void insert(HookEntry entry) {
        Objects.requireNonNull(entry);
        pending.add(next -> next.add(entry));
    }

    public void recovery(Recovery recovery) {
        TreeValidator.validate(root, recovery);
        this.recovery = Objects.requireNonNull(recovery);
    }

    public void remove(String owner) {
        pending.add(next -> next.removeIf(entry -> entry.owner().equals(owner)));
    }

    public void remove(String owner, Hook hook) {
        pending.add(next -> next.removeIf(entry -> entry.owner().equals(owner) && entry.hook() == hook));
    }

    public void discardPendingChanges() {
        pending.clear();
    }

    public void haltAll(TickContext ctx, ExitReason reason) {
        RuntimeException failure = null;
        List<Node> installed = new ArrayList<>();
        installed.add(root);
        entries.stream().map(HookEntry::guard).forEach(installed::add);
        for (Node node : installed) {
            try {
                node.halt(ctx, reason);
            } catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else if (failure != ex) failure.addSuppressed(ex);
            }
        }
        root = build(entries);
        if (failure != null) throw failure;
    }

    public boolean applyPendingChanges(TickContext ctx) {
        if (pending.isEmpty()) return false;
        List<HookEntry> next = new ArrayList<>(entries);
        List<Consumer<List<HookEntry>>> changes = List.copyOf(pending);
        pending.clear();
        changes.forEach(change -> change.accept(next));
        next.sort(Comparator.comparing(HookEntry::hook).thenComparing(Comparator.comparingInt(HookEntry::priority).reversed()));
        try {
            validateCandidate(next);
        } catch (IllegalArgumentException ex) {
            rejected.accept(ex.getMessage());
            ctx.event(root, Kind.HOOK, null, "refused", ex.getMessage());
            return false;
        }
        Set<Node> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        next.stream().filter(entries::contains).map(HookEntry::guard).forEach(retained::add);
        root.detachForRebuild(retained);
        root.halt(ctx, ExitReason.STOPPED);
        entries.stream().filter(entry -> !next.contains(entry)).map(HookEntry::guard).forEach(Node::resetMemory);
        for (String owner : entries.stream().map(HookEntry::owner).distinct().toList()) {
            if (next.stream().noneMatch(entry -> entry.owner().equals(owner))) {
                ctx.bb().removeOwner(owner);
                ctx.budgets().removeOwner(owner);
            }
        }
        entries = List.copyOf(next);
        entryIds.keySet().removeIf(guard -> entries.stream().noneMatch(entry -> entry.guard() == guard));
        root = build(entries);
        ctx.event(root, Kind.HOOK, null, "rebuilt", "entries=" + entries.size());
        return true;
    }

    private void validateCandidate(List<HookEntry> candidate) {
        TreeValidator.validate(candidate);
        if (recovery != null) TreeValidator.validate(ReactiveSelector.hooks(candidate.stream()
            .map(HookEntry::guard).toArray(Node[]::new)), recovery);
        Set<Node> attached = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(root, attached);
        for (HookEntry entry : candidate) checkActive(entry.guard(), attached);
    }

    private void collect(Node node, Set<Node> nodes) {
        nodes.add(node);
        node.children().forEach(child -> collect(child, nodes));
    }

    private void checkActive(Node node, Set<Node> attached) {
        if (node.active() && !attached.contains(node)) throw new IllegalArgumentException("node active in another tree: " + node.name());
        node.children().forEach(child -> checkActive(child, attached));
    }

    private ReactiveSelector build(List<HookEntry> installed) {
        for (HookEntry entry : installed) {
            if (!entry.guard().active()) entry.guard().flightAware(entry.flightAware());
            long id = entryIds.computeIfAbsent(entry.guard(), ignored -> ++nextId);
            TreeValidator.bind(entry.guard(), "root/" + entry.hook() + "/" + entry.owner() + "/" + id + ":" + entry.guard().name(), entry.owner(), entry.hook());
        }
        return ReactiveSelector.hooks(installed.stream().map(HookEntry::guard).toArray(Node[]::new));
    }

    public ReactiveSelector build() {
        return root;
    }

    public List<HookEntry> entries() {
        return entries;
    }

    public String taskOwner() {
        return entries.stream().filter(entry -> entry.hook() == Hook.BOT).map(HookEntry::owner).findFirst().orElse("core");
    }
}
