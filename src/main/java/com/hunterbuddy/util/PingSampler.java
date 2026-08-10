package com.hunterbuddy.util;

import java.util.ArrayDeque;
import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.StatisticsS2CPacket;

/**
 * The connection's recent past: half a minute of ping, and how fresh the keepalives are.
 *
 * <p>The ping is the tab-list latency the server reports about you, sampled once a second. It
 * says nothing about packet loss — nothing client-side reliably does — so the health signal
 * next to it is the age of the last keepalive: the server sends them steadily, and when they
 * stop arriving the connection is dying whatever the last reported ping said.
 */
public final class PingSampler {
    private static final PingSampler INSTANCE = new PingSampler();

    private static final long SAMPLE_EVERY_MS = 1000L;
    private static final int CAPACITY = 30;

    private final ArrayDeque<Integer> samples = new ArrayDeque<>();
    private long lastSampleAt;

    /** When the outstanding stats request went out, or 0 when none is pending. */
    private volatile long probeSentAt;
    // Written on the network thread, read on the render thread; volatile to avoid a torn long.
    private volatile long lastKeepAliveAt;

    /** One diagnostic line per session, not one per second. */
    private boolean reportedMissing;

    private PingSampler() {
    }

    /**
     * Your own tab-list entry, looked for every way the client offers.
     *
     * <p>The reported latency is the only figure in milliseconds a vanilla client ever sees —
     * nothing else measures a round trip from this side — so it is worth three attempts rather
     * than one. The uuid is the normal route; through ViaVersion the profile behind the tab-list
     * entry need not carry the same uuid as the entity, which is why the name is tried next, and
     * why the last resort walks the list and compares names itself.
     */
    private static PlayerListEntry findSelfEntry() {
        var handler = MeteorClient.mc.getNetworkHandler();
        if (handler == null || MeteorClient.mc.player == null) return null;

        PlayerListEntry entry = handler.getPlayerListEntry(MeteorClient.mc.player.getUuid());
        if (entry != null) return entry;

        String name = MeteorClient.mc.player.getGameProfile().name();

        entry = handler.getPlayerListEntry(name);
        if (entry != null) return entry;

        for (PlayerListEntry candidate : handler.getPlayerList()) {
            if (candidate.getProfile() != null && name.equalsIgnoreCase(candidate.getProfile().name())) {
                return candidate;
            }
        }

        return null;
    }

    public static PingSampler get() {
        return INSTANCE;
    }

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    public synchronized int current() {
        return samples.isEmpty() ? -1 : samples.peekLast();
    }

    /**
     * Whether the server has ever told us a latency.
     *
     * <p>The tab list is the only place a vanilla client sees a figure in milliseconds, and a
     * server is free to leave it at zero — 2b2t through ViaVersion does. A steady 0 ms is not a
     * fast connection, it is no measurement at all, and showing it as one is worse than saying
     * nothing: the keepalive age underneath is measured here and means something.
     */
    public synchronized boolean hasLatency() {
        for (int sample : samples) {
            if (sample > 0) return true;
        }

        return false;
    }

    /** Oldest to newest. A copy. */
    public synchronized int[] history() {
        int[] out = new int[samples.size()];
        int i = 0;
        for (int sample : samples) out[i++] = sample;
        return out;
    }

    /** Half the spread over the window — a plain figure for how unsteady the line is. */
    public synchronized int jitter() {
        if (samples.size() < 2) return 0;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int sample : samples) {
            if (sample < min) min = sample;
            if (sample > max) max = sample;
        }

        return (max - min) / 2;
    }

    /** Seconds since the last keepalive, or -1 before the first one. */
    public double keepAliveAgeSeconds() {
        long at = lastKeepAliveAt;
        return at == 0L ? -1.0 : (System.currentTimeMillis() - at) / 1000.0;
    }

    /**
     * Times a round trip of our own instead of waiting to be told.
     *
     * <p>The tab-list latency is what every client reads first, and on 2b2t through ViaVersion it
     * stays at zero — a value that looks like an answer and is not one. But the play protocol
     * does contain one exchange the client itself starts: asking for statistics gets exactly one
     * reply. Sending it and timing the reply measures the line directly, which is why other
     * clients show a figure here where reading the tab list shows nothing.
     *
     * <p>Once a second, and never two at a time: an outstanding request is left to finish or to
     * time out rather than being piled on.
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.getNetworkHandler() == null) return;

        long now = System.currentTimeMillis();

        // A reply that never came. Dropped rather than recorded: a lost packet is not a slow one,
        // and folding a timeout in as a sample would poison the average with a made-up figure.
        if (probeSentAt != 0L && now - probeSentAt > 5000L) probeSentAt = 0L;

        if (now - lastSampleAt < SAMPLE_EVERY_MS) return;

        lastSampleAt = now;

        // The reported latency stays the preferred source when the server does fill it in: it is
        // the server's own measurement and costs nothing.
        PlayerListEntry entry = findSelfEntry();
        int reported = entry == null ? 0 : entry.getLatency();

        if (reported > 0) {
            record(reported);
            return;
        }

        if (probeSentAt != 0L) return;

        probeSentAt = now;
        MeteorClient.mc.getNetworkHandler()
            .sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.REQUEST_STATS));
    }

    private synchronized void record(int latency) {
        samples.addLast(latency);
        while (samples.size() > CAPACITY) samples.removeFirst();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof KeepAliveS2CPacket) {
            lastKeepAliveAt = System.currentTimeMillis();
            return;
        }

        if (event.packet instanceof StatisticsS2CPacket) {
            long sent = probeSentAt;
            if (sent == 0L) return;

            probeSentAt = 0L;
            record((int) (System.currentTimeMillis() - sent));
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        synchronized (this) {
            samples.clear();
            lastKeepAliveAt = 0L;
            probeSentAt = 0L;
        }

        reportedMissing = false;
    }
}
