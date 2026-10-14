package com.hunterbuddy.behavior.farmer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FarmerMissionFacts {
    public String world = "";
    public String profile = "";
    public int step;
    public Long site;
    public List<int[]> zone = new ArrayList<>();
    public MaterialSite siteSnapshot;
    public boolean siteActive;
    public boolean baseRequested;
    public boolean changeRequested;
    public String changeReason = "";
    public long waitUntil;
    public boolean waitAtBase;
    public Map<Long, Long> occupied = new LinkedHashMap<>();
    public Map<Long, Integer> siteFailures = new LinkedHashMap<>();
}
