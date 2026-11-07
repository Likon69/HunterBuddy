package com.hunterbuddy.pet;

import com.mojang.logging.LogUtils;
import java.util.function.Consumer;

public final class PetSfxLog {
    private static final long INTERVAL = 60000;
    private final Consumer<String> sink;
    private final long[] next = new long[PetSfxAction.ALL.length];

    public PetSfxLog(Consumer<String> sink) {
        this.sink = sink;
    }

    public static PetSfxLog logging() {
        return new PetSfxLog(LogUtils.getLogger()::info);
    }

    public void skipped(PetSfxAction action, long now, String reason) {
        if (sink == null || now < next[action.ordinal()]) return;
        next[action.ordinal()] = now + INTERVAL;
        sink.accept("[pet] sfx " + action.key + " skipped: " + reason);
    }
}
