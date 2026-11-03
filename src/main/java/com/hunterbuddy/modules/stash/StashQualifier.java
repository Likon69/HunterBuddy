package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder.StashChunk;

public final class StashQualifier {
    private final StashGeneralSettings general;
    private final StashThresholdSettings thresholds;

    public StashQualifier(StashGeneralSettings general, StashThresholdSettings thresholds) {
        this.general = general;
        this.thresholds = thresholds;
    }

    public boolean qualifies(StashChunk chunk) {
        return chunk.getTotal() >= general.minimumStorageCount.get() || meetsThresholds(chunk);
    }

    public boolean meetsThresholds(StashChunk chunk) {
        if (isUnconstrained()) return true;
        if (reaches(chunk, StashType.STACKED_MINECART)) return true;
        for (StashType type : StashType.values()) {
            if (type != StashType.STACKED_MINECART && reaches(chunk, type)) return true;
        }
        return false;
    }

    private boolean isUnconstrained() {
        for (StashType type : StashType.values()) {
            if (type != StashType.STACKED_MINECART && thresholds.minimum(type) != 0) return false;
        }
        return true;
    }

    private boolean reaches(StashChunk chunk, StashType type) {
        int minimum = thresholds.minimum(type);
        return minimum > 0 && chunk.countOf(type) >= minimum;
    }
}
