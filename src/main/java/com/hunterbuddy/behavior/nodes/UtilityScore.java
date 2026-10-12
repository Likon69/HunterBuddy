package com.hunterbuddy.behavior.nodes;

public record UtilityScore(String path, String target, double raw, int failures, double effective,
                           long retryAtMs, boolean waiting, boolean abandoned, boolean exhausted) {
    public boolean wanted() {
        return Double.isFinite(raw) && raw > 0;
    }

    public boolean eligible() {
        return wanted() && !waiting && !abandoned && !exhausted && Double.isFinite(effective) && effective > 0;
    }
}
