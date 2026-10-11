package com.hunterbuddy.behavior.resource;

import java.util.Objects;
import java.util.Set;

public final class Lease {
    private final String owner;
    private final Set<Resource> resources;
    private LeaseState state = LeaseState.GRANTED;

    Lease(String owner, Set<Resource> resources) {
        this.owner = Objects.requireNonNull(owner);
        this.resources = Set.copyOf(resources);
    }

    public String owner() {
        return owner;
    }

    public Set<Resource> resources() {
        return resources;
    }

    public LeaseState state() {
        return state;
    }

    void state(LeaseState state) {
        this.state = state;
    }
}
