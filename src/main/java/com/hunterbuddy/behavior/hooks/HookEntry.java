package com.hunterbuddy.behavior.hooks;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.nodes.Guard;
import java.util.Objects;

public record HookEntry(Hook hook, String owner, Contributor contributor,
                        int priority, Guard guard, boolean flightAware, Node node) {
    public HookEntry {
        Objects.requireNonNull(hook);
        Objects.requireNonNull(contributor);
        Objects.requireNonNull(guard);
        Objects.requireNonNull(node);
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("empty hook owner");
        if (guard.child() != node) throw new IllegalArgumentException("HookEntry Guard must wrap its node");
    }
}
