package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.ProgressGuard;
import com.hunterbuddy.behavior.nodes.ReactiveSelector;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class TreeValidator {
    private TreeValidator() {
    }

    public static void validate(Node root) {
        visit(root, false, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    public static void validate(List<HookEntry> entries) {
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        long bots = entries.stream().filter(entry -> entry.hook() == Hook.BOT).count();
        if (bots > 1) throw new IllegalArgumentException("BOT accepts one entry");
        for (HookEntry entry : entries) {
            if (!admitted(entry.hook(), entry.contributor())) {
                throw new IllegalArgumentException("contributor " + entry.contributor() + " forbidden in " + entry.hook());
            }
            if (entry.hook() == Hook.RECOVERY) throw new IllegalArgumentException("RECOVERY is outside the root");
            if (entry.contributor() == Contributor.PLUGIN && containsLease(entry.node())) {
                throw new IllegalArgumentException("PLUGIN cannot request a resource: " + entry.owner());
            }
            visit(entry.guard(), false, visited);
            if (entry.contributor() == Contributor.ROUTINE && entries.stream()
                .filter(other -> other.hook() == entry.hook() && other.owner().equals(entry.owner())).count() > 1
                && entry.hook() != Hook.BOT_PRE && entry.hook() != Hook.BOT_POST) {
                throw new IllegalArgumentException("routine accepts one principal entry: " + entry.owner());
            }
        }
    }

    private static void visit(Node node, boolean bounded, Set<Node> visited) {
        if (!visited.add(node)) throw new IllegalArgumentException("double parent or cycle: " + node.name());
        if (node instanceof Action && !bounded) throw new IllegalArgumentException("Action without Timeout/ProgressGuard: " + node.name());
        if (node instanceof WithLease lease && lease.resources().isEmpty()) throw new IllegalArgumentException("empty WithLease");
        if (node instanceof ReactiveSelector && node.children().stream().anyMatch(child -> !(child instanceof Guard))) {
            throw new IllegalArgumentException("ReactiveSelector child without Guard");
        }
        boolean nextBounded = bounded || node instanceof Timeout || node instanceof ProgressGuard;
        for (Node child : node.children()) visit(child, nextBounded, visited);
    }

    private static boolean containsLease(Node node) {
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return containsLease(node, visited);
    }

    private static boolean containsLease(Node node, Set<Node> visited) {
        if (!visited.add(node)) throw new IllegalArgumentException("double parent or cycle: " + node.name());
        if (node instanceof WithLease) return true;
        return node.children().stream().anyMatch(child -> containsLease(child, visited));
    }

    private static boolean admitted(Hook hook, Contributor contributor) {
        return switch (hook) {
            case SESSION, DEATH, IDLE -> contributor == Contributor.CORE;
            case SURVIVAL -> contributor == Contributor.ROUTINE || contributor == Contributor.PLUGIN;
            case FLIGHT -> contributor == Contributor.CORE || contributor == Contributor.ROUTINE;
            case COMBAT, REGEAR -> contributor == Contributor.ROUTINE;
            case BOT_PRE -> contributor != Contributor.CORE;
            case BOT -> contributor == Contributor.BOT;
            case BOT_POST -> contributor == Contributor.BOT || contributor == Contributor.PLUGIN;
            case RECOVERY -> contributor != Contributor.PLUGIN;
        };
    }

    public static void bind(Node root, String path, String owner, Hook hook) {
        root.bind(path, owner, hook);
        for (int i = 0; i < root.children().size(); i++) {
            Node child = root.children().get(i);
            bind(child, path + "/" + i + ":" + child.name(), owner, hook);
        }
    }

    public static void validate(Node root, Node recovery) {
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visit(root, false, visited);
        visit(recovery, false, visited);
    }
}
