package com.hunterbuddy.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;

/**
 * A minute of TPS history, sampled on a fixed clock.
 *
 * <p>Meteor's {@link TickRate} already computes the rate; what it does not keep is a past. The
 * cardiogram needs one, and sampling on wall time rather than on ticks matters here of all
 * places — during the lag the trace exists to show, the ticks are exactly what stops coming.
 */
public final class TpsSampler {
    private static final TpsSampler INSTANCE = new TpsSampler();

    private static final long SAMPLE_EVERY_MS = 500L;
    private static final int CAPACITY = 120;

    private final float[] samples = new float[CAPACITY];
    private int head;
    private int filled;
    private long lastSampleAt;

    private TpsSampler() {
    }

    public static TpsSampler get() {
        return INSTANCE;
    }

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    public float current() {
        return TickRate.INSTANCE.getTickRate();
    }

    /** Seconds since the server last advanced the world. Above ~1 the server has flatlined. */
    public float timeSinceLastTick() {
        return TickRate.INSTANCE.getTimeSinceLastTick();
    }

    /** Oldest to newest. The array is full of zeros until the first minute has passed. */
    public synchronized float[] history() {
        float[] out = new float[filled];
        for (int i = 0; i < filled; i++) {
            out[i] = samples[(head - filled + i + CAPACITY * 2) % CAPACITY];
        }

        return out;
    }

    public synchronized float worst() {
        if (filled == 0) return current();

        float worst = Float.MAX_VALUE;
        for (int i = 0; i < filled; i++) {
            float sample = samples[(head - filled + i + CAPACITY * 2) % CAPACITY];
            if (sample < worst) worst = sample;
        }

        return worst;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now - lastSampleAt < SAMPLE_EVERY_MS) return;

        lastSampleAt = now;

        synchronized (this) {
            samples[head] = TickRate.INSTANCE.getTickRate();
            head = (head + 1) % CAPACITY;
            if (filled < CAPACITY) filled++;
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        synchronized (this) {
            head = 0;
            filled = 0;
        }
    }
}
