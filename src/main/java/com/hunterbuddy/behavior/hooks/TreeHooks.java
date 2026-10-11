package com.hunterbuddy.behavior.hooks;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.nodes.ReactiveSelector;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class TreeHooks {
    private final List<Consumer<List<HookEntry>>> pending = new ArrayList<>();
    private final Consumer<String> rejected;
    private List<HookEntry> entries = List.of();
    private ReactiveSelector root = new ReactiveSelector("root");
    private Recovery recovery;

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
        root = build(entries);
        ctx.event(root, Kind.HOOK, null, "rebuilt", "entries=" + entries.size());
        return true;
    }

    private void validateCandidate(List<HookEntry> candidate) {
        TreeValidator.validate(candidate);
        if (recovery != null) TreeValidator.validate(new ReactiveSelector("root", candidate.stream()
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
            TreeValidator.bind(entry.guard(), "root/" + entry.hook() + "/" + entry.owner() + "/" + entry.guard().name(), entry.owner(), entry.hook());
        }
        return new ReactiveSelector("root", installed.stream().map(HookEntry::guard).toArray(Node[]::new));
    }

    public ReactiveSelector build() {
        return root;
    }

    public List<HookEntry> entries() {
        return entries;
    }
}
