package com.hunterbuddy.behavior.hooks;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.nodes.Guard;
import java.util.Objects;

public record HookEntry(Hook hook, String owner, Contributor contributor,
                        int priority, Guard guard, boolean flightAware, Node node, int recoveryLevel) {
    public HookEntry(Hook hook, String owner, Contributor contributor,
                     int priority, Guard guard, boolean flightAware, Node node) {
        this(hook, owner, contributor, priority, guard, flightAware, node, 0);
    }

    public HookEntry {
        Objects.requireNonNull(hook);
        Objects.requireNonNull(contributor);
        Objects.requireNonNull(guard);
        Objects.requireNonNull(node);
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("empty hook owner");
        if (guard.child() != node) throw new IllegalArgumentException("HookEntry Guard must wrap its node");
        if (recoveryLevel != 0 && recoveryLevel != 2 && recoveryLevel != 3) {
            throw new IllegalArgumentException("invalid recovery level: " + recoveryLevel);
        }
        if (hook != Hook.RECOVERY && recoveryLevel != 0) {
            throw new IllegalArgumentException("recovery level outside RECOVERY");
        }
    }
}
