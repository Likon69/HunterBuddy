package com.hunterbuddy.behavior.mc;

public record BaritoneSnapshot(boolean available, boolean pathing, boolean hasPath, boolean goalActive,
                               boolean calcInProgress, boolean inControl, String lastPathEvent,
                               boolean elytraActive, boolean elytraDestinationSet) {
}
