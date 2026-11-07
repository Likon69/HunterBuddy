package com.hunterbuddy.pet;

public final class PetStability {
    public static final long WAIT_MILLIS = 15000;
    private Object world;
    private long since;

    public void restart() {
        world = null;
    }

    public void adopt(Object current, long since) {
        world = current;
        this.since = since;
    }

    public void update(Object current, long now) {
        if (current == world) return;
        world = current;
        since = now;
    }

    public boolean ready(long now) {
        return world != null && now - since >= WAIT_MILLIS;
    }
}
