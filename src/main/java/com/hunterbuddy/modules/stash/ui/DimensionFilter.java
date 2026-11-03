package com.hunterbuddy.modules.stash.ui;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import com.hunterbuddy.modules.stash.StashDimensions;

public enum DimensionFilter {
    ALL("All", null),
    OVERWORLD("Overworld", StashDimensions.OVERWORLD),
    NETHER("Nether", StashDimensions.NETHER),
    END("End", StashDimensions.END);

    private final String label;
    private final String dimension;

    DimensionFilter(String label, String dimension) {
        this.label = label;
        this.dimension = dimension;
    }

    public String label() {
        return label;
    }

    public boolean accepts(StashChunk chunk) {
        return dimension == null || dimension.equals(chunk.dimension);
    }
}
