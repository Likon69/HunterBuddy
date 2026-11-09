package com.hunterbuddy.hud.glass;

public final class BlurCadence {
    private final long interval;
    private long captured;
    private int revision;
    private int width;
    private int height;
    private int strength;
    private boolean valid;

    public BlurCadence(long interval) {
        this.interval = Math.max(0, interval);
    }

    public boolean refresh(long now, int revision, int width, int height, int strength) {
        if (valid && now - captured < interval && this.revision == revision && this.width == width
            && this.height == height && this.strength == strength) return false;
        captured = now;
        this.revision = revision;
        this.width = width;
        this.height = height;
        this.strength = strength;
        valid = true;
        return true;
    }

    public void invalidate() {
        valid = false;
    }
}
