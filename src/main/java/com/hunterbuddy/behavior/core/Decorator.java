package com.hunterbuddy.behavior.core;

import java.util.List;
import java.util.Objects;

public abstract class Decorator extends Node {
    private final Node child;

    protected Decorator(String name, Node child) {
        super(name);
        this.child = Objects.requireNonNull(child);
    }

    public final Node child() {
        return child;
    }

    @Override
    public final List<Node> children() {
        return List.of(child);
    }
}
