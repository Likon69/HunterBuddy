package com.hunterbuddy.behavior.farmer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ZoneKnowledge {
    public Map<String, ChunkObservation> coverage = new LinkedHashMap<>();
    public Map<String, MaterialSite> sites = new LinkedHashMap<>();
    public transient boolean layer;

    public CoverageState state(String chunk) {
        ChunkObservation observation = coverage.get(chunk);
        return observation == null ? CoverageState.UNKNOWN : observation.state();
    }
}
