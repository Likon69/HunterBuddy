package com.hunterbuddy.behavior.resource;

import java.util.EnumSet;
import java.util.Set;

public final class Implications {
    private Implications() {
    }

    public static Set<Resource> expand(Set<Resource> requested) {
        if (requested.isEmpty()) throw new IllegalArgumentException("empty WithLease");
        EnumSet<Resource> result = EnumSet.copyOf(requested);
        if (result.contains(Resource.BARITONE)) result.addAll(Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.SLOT));
        if (result.contains(Resource.MINING)) result.addAll(Set.of(Resource.LOOK, Resource.SLOT));
        if (result.contains(Resource.INVENTORY)) result.add(Resource.SCREEN);
        if (result.contains(Resource.ROCKETS)) result.addAll(Set.of(Resource.SLOT, Resource.INTERACT));
        return Set.copyOf(result);
    }
}
