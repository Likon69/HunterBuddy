package com.hunterbuddy.pet;

final class PetThirst {
    static final float LIMIT = 600;
    static final float WAIT = 4;
    float dry;
    private boolean asked;
    private float sinceAsk;

    void reset() {
        dry = 0;
        asked = false;
        sinceAsk = 0;
    }

    void drank() {
        reset();
    }

    void spoke() {
        if (asked) sinceAsk = 0;
    }

    void tick(float dt, boolean active) {
        if (active) dry += dt;
        if (asked) sinceAsk += dt;
    }

    boolean ask(boolean fit) {
        if (asked || !fit || dry < LIMIT) return false;
        asked = true;
        sinceAsk = 0;
        return true;
    }

    boolean drink(boolean fit) {
        return asked && fit && sinceAsk >= WAIT;
    }
}
