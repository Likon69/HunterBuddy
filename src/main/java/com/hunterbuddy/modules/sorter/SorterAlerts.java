package com.hunterbuddy.modules.sorter;

import com.hunterbuddy.modules.sorter.model.SorterProfile;
import java.util.Locale;

final class SorterAlerts {
    private String message;
    private long recordedAt;
    private boolean blocking;
    private SorterProfile profile;

    void record(String message, boolean blocking, SorterProfile profile, Object... args) {
        this.message = args.length == 0 ? message : String.format(Locale.ROOT, message, args);
        recordedAt = System.nanoTime();
        this.blocking = blocking;
        this.profile = profile;
    }

    void clear() {
        message = null;
        blocking = false;
    }

    void discardOtherProfile(SorterProfile current) {
        if (profile != current) {
            clear();
        }
    }

    boolean blocking() {
        return blocking;
    }

    SorterRunSnapshot.Alert snapshot(long now) {
        long age = message == null ? 0 : Math.max(0, (now - recordedAt) / 1_000_000);
        return message != null && (blocking || age < 10_000) ? new SorterRunSnapshot.Alert(message, age, blocking) : null;
    }
}
