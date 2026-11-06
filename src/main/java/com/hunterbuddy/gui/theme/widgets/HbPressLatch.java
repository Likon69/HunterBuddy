package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbAnim;

final class HbPressLatch {
    private static final long HOLD_NANOS = 140_000_000L;
    private long releasedAt;

    void release() {
        releasedAt = HbAnim.now();
    }

    boolean held(boolean pressed) {
        return pressed || releasedAt != 0 && HbAnim.now() - releasedAt < HOLD_NANOS;
    }
}
