package com.hunterbuddy.behavior.nodes;

public record UtilityScore(String path, String target, double raw, int failures, double effective,
                           long retryAtMs, boolean waiting, boolean abandoned) {
    public boolean eligible() {
        return !waiting && !abandoned && Double.isFinite(effective) && effective > 0;
    }
}
