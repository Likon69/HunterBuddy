package com.hunterbuddy.modules.visualrange;

import net.minecraft.text.Text;

public final class DisconnectMemory {
    private static final long FRESH_MILLIS = 2000L;

    private static volatile Text reason;
    private static volatile long storedAt;

    private DisconnectMemory() {
    }

    public static void remember(Text newReason) {
        reason = newReason;
        storedAt = System.currentTimeMillis();
    }

    public static Text takeFresh() {
        Text stored = reason;
        if (stored == null || System.currentTimeMillis() - storedAt >= FRESH_MILLIS) return null;
        reason = null;
        return stored;
    }
}
