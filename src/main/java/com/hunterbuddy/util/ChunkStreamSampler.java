package com.hunterbuddy.util;

import java.util.ArrayDeque;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.orbit.EventHandler;

/**
 * How fast the server is actually sending terrain, in chunks per second.
 *
 * <p>Counted from the same event the detectors already use for chunk arrival, over a sliding
 * ten-second window. Ten seconds because the stream is bursty — the server sends fans of chunks,
 * then nothing — and a shorter window reads as a blinking number rather than a rate.
 */
public final class ChunkStreamSampler {
    private static final ChunkStreamSampler INSTANCE = new ChunkStreamSampler();

    private static final long WINDOW_MS = 10_000L;

    private final ArrayDeque<Long> arrivals = new ArrayDeque<>();

    private ChunkStreamSampler() {
    }

    public static ChunkStreamSampler get() {
        return INSTANCE;
    }

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    public synchronized double chunksPerSecond() {
        prune();
        return arrivals.size() / (WINDOW_MS / 1000.0);
    }

    private void prune() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!arrivals.isEmpty() && arrivals.peekFirst() < cutoff) arrivals.removeFirst();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        synchronized (this) {
            arrivals.addLast(System.currentTimeMillis());
            prune();
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        synchronized (this) {
            arrivals.clear();
        }
    }
}
