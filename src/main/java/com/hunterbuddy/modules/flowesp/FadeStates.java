package com.hunterbuddy.modules.flowesp;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class FadeStates {
    private static final double VISIBLE_THRESHOLD = 0.001;

    private final Map<Long, Double> fades = new HashMap<>();

    void reset() {
        fades.clear();
    }

    double fadeIn(long chunkKey, double step, double initial) {
        double next = Math.min(1.0, fades.getOrDefault(chunkKey, initial) + step);
        fades.put(chunkKey, next);
        return next;
    }

    double fadeOut(long chunkKey, double step) {
        double next = Math.max(0.0, fades.getOrDefault(chunkKey, 0.0) - step);
        if (next <= VISIBLE_THRESHOLD) {
            fades.remove(chunkKey);
            return 0.0;
        }
        fades.put(chunkKey, next);
        return next;
    }

    void retainOnly(Set<Long> chunkKeys) {
        fades.keySet().retainAll(chunkKeys);
    }

    static boolean isVisible(double fade) {
        return fade > VISIBLE_THRESHOLD;
    }
}
