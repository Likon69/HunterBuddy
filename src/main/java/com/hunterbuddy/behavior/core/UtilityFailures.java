package com.hunterbuddy.behavior.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UtilityFailures {
    private final Blackboard blackboard;
    private final Map<String, Set<Key<UtilityFailure>>> recorded = new HashMap<>();

    public UtilityFailures(Blackboard blackboard) {
        this.blackboard = Objects.requireNonNull(blackboard);
    }

    public UtilityFailure get(String owner, String path, String target) {
        return blackboard.get(owner, key(path, target)).orElse(UtilityFailure.NONE);
    }

    public void failed(String owner, String path, String target, long nowMs) {
        Key<UtilityFailure> key = key(path, target);
        int consecutive = Math.min(3, get(owner, path, target).consecutive() + 1);
        long retryAt = consecutive == 3 ? 0 : Math.addExact(nowMs, consecutive == 1 ? 30_000 : 60_000);
        blackboard.set(owner, key, new UtilityFailure(consecutive, retryAt));
        recorded.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
    }

    public void succeeded(String owner, String path, String target) {
        Key<UtilityFailure> key = key(path, target);
        blackboard.remove(owner, key);
        Set<Key<UtilityFailure>> keys = recorded.get(owner);
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) recorded.remove(owner);
        }
    }

    public void clear() {
        recorded.forEach((owner, keys) -> keys.forEach(key -> blackboard.remove(owner, key)));
        recorded.clear();
    }

    private Key<UtilityFailure> key(String path, String target) {
        return new Key<>("utility." + path + ".fail@" + target, UtilityFailure.class, Scope.OWNER, false);
    }
}
