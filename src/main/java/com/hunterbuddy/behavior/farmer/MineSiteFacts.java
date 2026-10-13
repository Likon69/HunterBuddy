package com.hunterbuddy.behavior.farmer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MineSiteFacts {
    public String world;
    public String profile;
    public long site;
    public long entrance;
    public long stand;
    public int stairDirection = -1;
    public int stairSpan;
    public List<Long> stairs = new ArrayList<>();
    public List<Long> gallery = new ArrayList<>();
    public List<Long> blocks = new ArrayList<>();
    public Set<Long> supports = new HashSet<>();
    public int mined;
    public int excavated;
    public Long pending;
    public boolean material;
    public boolean broken;
    public Map<String, Integer> before = new LinkedHashMap<>();
    public Map<String, Integer> entities = new LinkedHashMap<>();
    public Set<String> drops = new HashSet<>();
    public Map<String, Integer> dropCounts = new LinkedHashMap<>();
    public String siteState = "observed";
    public boolean unexplored;
    public Set<String> changedChunks = new HashSet<>();
    public long receiptVersion;
    public boolean exited;
    public String outcome = "";
}
