package com.hunterbuddy.modules.visualrange;

import com.hunterbuddy.HunterBuddyAddon;

import java.util.HashSet;
import java.util.Set;

public final class WatchGuard {
    private final Set<String> reportedFailures = new HashSet<>();

    public void run(String watchName, Runnable watch) {
        try {
            watch.run();
        } catch (Exception failure) {
            if (reportedFailures.add(watchName)) {
                HunterBuddyAddon.LOG.error("[HB] VisualRange '{}' watch failed", watchName, failure);
            }
        }
    }
}
