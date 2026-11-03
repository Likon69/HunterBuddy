package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import com.hunterbuddy.modules.regear.util.XaeroWaypointManager;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StashWaypoints {
    private static final String SOURCE = "StashFinder";
    private static final int WAYPOINT_HEIGHT = 64;
    private static final String PLAIN_INITIALS = "S";

    private final StashGeneralSettings general;
    private final StashThresholdSettings thresholds;
    private final StashWaypointSettings settings;
    private final StashLedger ledger;
    private final Map<String, StashChunk> clusters = new HashMap<>();

    public StashWaypoints(
        StashGeneralSettings general, StashThresholdSettings thresholds, StashWaypointSettings settings, StashLedger ledger
    ) {
        this.general = general;
        this.thresholds = thresholds;
        this.settings = settings;
        this.ledger = ledger;
    }

    public void clearClusters() {
        clusters.clear();
    }

    public void update(StashChunk found) {
        if (!XaeroWaypointManager.isAvailable() || !settings.addWaypoints.get()) return;
        int radius = general.clusterRadius.get();
        String id = clusterId(found, radius);
        StashChunk merged = merge(clusterMembers(found.dimension, id, radius));
        StashChunk previous = clusters.put(id, merged);
        if (previous != null) XaeroWaypointManager.removeWaypointXZ(SOURCE, previous.x, previous.z);
        place(merged);
    }

    private static String clusterId(StashChunk chunk, int radius) {
        return chunk.dimension + ":" + Math.floorDiv(chunk.chunkPos.x, radius) + ":" + Math.floorDiv(chunk.chunkPos.z, radius);
    }

    private List<StashChunk> clusterMembers(String dimension, String id, int radius) {
        return ledger.chunks().stream()
            .filter(chunk -> chunk.dimension.equals(dimension) && clusterId(chunk, radius).equals(id))
            .toList();
    }

    private static StashChunk merge(List<StashChunk> members) {
        StashChunk richest = members.get(0);
        for (StashChunk member : members) {
            if (member.getTotal() > richest.getTotal()) richest = member;
        }
        StashChunk merged = new StashChunk(richest.chunkPos);
        merged.dimension = richest.dimension;
        for (StashChunk member : members) accumulate(merged, member);
        return merged;
    }

    private static void accumulate(StashChunk merged, StashChunk member) {
        for (StashType type : StashType.values()) {
            int combined = type == StashType.STACKED_MINECART
                ? Math.max(merged.countOf(type), member.countOf(type))
                : merged.countOf(type) + member.countOf(type);
            merged.setCount(type, combined);
        }
    }

    private void place(StashChunk merged) {
        String symbols = symbolsFor(merged);
        boolean symbolic = settings.useSymbols.get();
        place(merged, symbolic ? symbolicName(merged, symbols) : plainName(merged), symbolic ? symbols : PLAIN_INITIALS);
    }

    private void place(StashChunk merged, String name, String initials) {
        XaeroWaypointManager.addWaypoint(
            SOURCE,
            new BlockPos(merged.x, WAYPOINT_HEIGHT, merged.z),
            name,
            initials,
            settings.waypointColor.get(),
            settings.temporaryWaypoints.get(),
            merged.dimension);
    }

    private String symbolicName(StashChunk merged, String symbols) {
        return settings.showQuantity.get() ? symbols + " " + merged.getTotal() : symbols;
    }

    private String plainName(StashChunk merged) {
        return settings.showQuantity.get() ? "Stash (" + merged.getTotal() + ")" : "Stash";
    }

    private String symbolsFor(StashChunk merged) {
        StringBuilder symbols = new StringBuilder();
        for (StashType type : StashType.values()) {
            int minimum = thresholds.minimum(type);
            if (type.hasSymbol() && minimum > 0 && merged.countOf(type) >= minimum) symbols.append(type.symbol());
        }
        return symbols.length() == 0 ? StashType.CHEST.symbol() : symbols.toString();
    }
}
