package com.hunterbuddy.modules.scaffold;

final class PlaceDelay {
    private int ticks;

    void reset() {
        ticks = 0;
    }

    void advance() {
        ticks++;
    }

    boolean reached(int required) {
        return ticks >= required;
    }
}
