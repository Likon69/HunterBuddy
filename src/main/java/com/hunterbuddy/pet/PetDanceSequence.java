package com.hunterbuddy.pet;

import java.util.Random;

public final class PetDanceSequence {
    private final Random random;
    private int[] order;
    private int[] previousOrder;
    private PetResources.FullPose[] frames;
    private int cursor;
    private int last = -1;
    private float clock;
    private float hold;
    public PetResources.FullPose current;
    public PetResources.FullPose next;
    public float mix;
    public float previousMix;

    public PetDanceSequence() {
        random = new Random();
    }

    public PetDanceSequence(long seed) {
        random = new Random(seed);
    }

    public void start(PetResources.FullPose[] frames, int[] order, int[] previousOrder, PetResources.FullPose first) {
        this.frames = frames;
        this.order = order;
        this.previousOrder = previousOrder;
        current = first;
        next = null;
        mix = previousMix = clock = 0;
        hold = 0.2f + random.nextFloat() * 0.08f;
        last = -1;
        for (int i = 0; i < frames.length; i++) if (frames[i] == first) last = i;
        for (int i = 0; i < frames.length; i++) order[i] = previousOrder[i] = i;
        cursor = frames.length;
    }

    public void update(float dt) {
        previousMix = mix;
        if (frames == null || frames.length < 2) return;
        if (next != null) {
            if (mix == 1 && previousMix == 1) {
                current = next;
                next = null;
                mix = previousMix = 0;
                clock = 0;
                hold = 0.2f + random.nextFloat() * 0.08f;
            } else mix = Math.min(1, mix + dt / 0.25f);
        } else {
            clock += dt;
            if (clock >= hold) {
                next = frames[pick()];
                mix = previousMix = 0;
            }
        }
    }

    private int pick() {
        if (cursor == frames.length) {
            System.arraycopy(order, 0, previousOrder, 0, frames.length);
            for (int i = frames.length - 1; i > 0; i--) swap(i, random.nextInt(i + 1));
            if (order[0] == last) swap(0, 1);
            boolean same = true;
            for (int i = 0; i < frames.length; i++) if (order[i] != previousOrder[i]) same = false;
            if (same && frames.length > 2) swap(1, frames.length - 1);
            cursor = 0;
        }
        last = order[cursor++];
        return last;
    }

    private void swap(int a, int b) {
        int value = order[a];
        order[a] = order[b];
        order[b] = value;
    }
}
