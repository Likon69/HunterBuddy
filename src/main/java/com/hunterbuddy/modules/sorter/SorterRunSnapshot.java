package com.hunterbuddy.modules.sorter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SorterRunSnapshot(
    Mode mode, Stage stage, String state, String pauseReason, Target target,
    Progress progress, Results results, Capacity capacity, Alert alert,
    Storage storage, long durationMillis
) {
    public enum Mode { OFF, SORTING, STATION, PAUSED, DONE }
    public enum Stage { INPUTS, SORT, STATION, PACK, STORE }

    public record Target(String role, String category, String position, double distance) { }
    public record Progress(int registered, int remaining, int emptied, int excluded) { }
    public record Results(int shulkers, int purifiedDelivered, int purified, int looseStacks, int packed, int packedFull,
                          Map<String, Integer> byCategory) {
        public Results {
            byCategory = Collections.unmodifiableMap(new LinkedHashMap<>(byCategory));
        }
    }
    public record Capacity(int fullChests, int reserves, List<String> missingCategories) {
        public Capacity {
            missingCategories = List.copyOf(missingCategories);
        }
    }
    public record Alert(String text, long ageMillis, boolean blocking) { }
    public record Storage(int inputs, int destinations, int reserves, String station) { }

    public static SorterRunSnapshot empty() {
        return new SorterRunSnapshot(Mode.OFF, null, "Off", "", null,
            new Progress(0, 0, 0, 0), new Results(0, 0, 0, 0, 0, 0, Map.of()),
            new Capacity(0, 0, List.of()), null, new Storage(0, 0, 0, ""), 0);
    }
}
