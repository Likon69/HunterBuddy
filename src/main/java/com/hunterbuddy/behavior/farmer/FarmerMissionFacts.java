package com.hunterbuddy.behavior.farmer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FarmerMissionFacts {
    public String world = "";
    public String profile = "";
    public int step;
    public Long site;
    public boolean baseRequested;
    public boolean changeRequested;
    public String changeReason = "";
    public long waitUntil;
    public boolean waitAtBase;
    public Map<Long, Long> occupied = new LinkedHashMap<>();
}
