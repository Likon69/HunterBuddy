package com.hunterbuddy.pet;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongArray;

public final class PetEvents {
    private static final AtomicLongArray PENDING = new AtomicLongArray(PetEvent.ALL.length);
    private static final long[] DRAINED = new long[PetEvent.ALL.length];
    private static volatile boolean listening;

    private PetEvents() {
    }

    public static void publish(PetEvent event) {
        if (!listening) return;
        PENDING.incrementAndGet(event.ordinal());
    }

    public static void listen(boolean enabled) {
        listening = enabled;
        for (int i = 0; i < PetEvent.ALL.length; i++) PENDING.set(i, 0);
        Arrays.fill(DRAINED, 0);
    }

    public static long drain() {
        long mask = 0;
        for (int i = 0; i < PetEvent.ALL.length; i++) {
            DRAINED[i] = PENDING.getAndSet(i, 0);
            if (DRAINED[i] != 0) mask |= 1L << i;
        }
        return mask;
    }

    public static long count(PetEvent event) {
        return DRAINED[event.ordinal()];
    }
}
