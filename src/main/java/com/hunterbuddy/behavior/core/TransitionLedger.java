package com.hunterbuddy.behavior.core;

import java.util.HashMap;
import java.util.Map;

public final class TransitionLedger {
    private final Map<String, Map<String, Integer>> starts = new HashMap<>();

    public boolean start(String path, String target) {
        return starts.computeIfAbsent(path, ignored -> new HashMap<>()).merge(target, 1, Integer::sum) <= 5;
    }

    public void progress() {
        starts.clear();
    }
}
