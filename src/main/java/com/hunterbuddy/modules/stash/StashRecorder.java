package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder.StashChunk;

public final class StashRecorder {
    private final StashLedger ledger;
    private final StashQualifier qualifier;
    private final TracerMarker tracerMarker;
    private final StashAnnouncer announcer;

    public StashRecorder(
        StashLedger ledger, StashQualifier qualifier, TracerMarker tracerMarker, StashAnnouncer announcer
    ) {
        this.ledger = ledger;
        this.qualifier = qualifier;
        this.tracerMarker = tracerMarker;
        this.announcer = announcer;
    }

    public void commit(StashChunk updated, StashChunk before) {
        if (!qualifier.qualifies(updated)) return;
        ledger.put(updated);
        tracerMarker.mark(updated);
        ledger.save();
        boolean changed = before == null || !updated.countsEqual(before);
        if (changed && qualifier.meetsThresholds(updated)) announcer.announce(updated);
    }
}
