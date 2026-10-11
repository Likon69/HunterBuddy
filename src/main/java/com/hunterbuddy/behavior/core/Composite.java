package com.hunterbuddy.behavior.core;

import java.util.List;
import java.util.Set;

public abstract class Composite extends Node {
    private List<Node> children;

    protected Composite(String name, Node... children) {
        super(name);
        this.children = List.of(children);
    }

    @Override
    public final List<Node> children() {
        return children;
    }

    protected final void detach(Set<Node> transferred) {
        children = children.stream().filter(child -> !transferred.contains(child)).toList();
    }
}
