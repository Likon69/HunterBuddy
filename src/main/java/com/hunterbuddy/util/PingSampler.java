package com.hunterbuddy.util;

import java.util.ArrayDeque;
import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;

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

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.getNetworkHandler() == null) return;

        long now = System.currentTimeMillis();
        if (now - lastSampleAt < SAMPLE_EVERY_MS) return;

        lastSampleAt = now;

        PlayerListEntry entry = findSelfEntry();

        if (entry == null) {
            // Said once, not every second: if the tab list never yields an entry the reason is
            // structural, and a line a second would bury the log without adding anything.
            if (!reportedMissing) {
                reportedMissing = true;
                var handler = MeteorClient.mc.getNetworkHandler();
                HunterBuddyAddon.LOG.info(
                    "[HB] ping: no tab-list entry for self (uuid={} name={} listSize={})",
                    MeteorClient.mc.player.getUuid(),
                    MeteorClient.mc.player.getGameProfile().name(),
                    handler == null ? -1 : handler.getPlayerList().size());
            }

            return;
        }

        int latency = entry.getLatency();

        synchronized (this) {
            samples.addLast(latency);
            while (samples.size() > CAPACITY) samples.removeFirst();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof KeepAliveS2CPacket) {
            lastKeepAliveAt = System.currentTimeMillis();
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        synchronized (this) {
            samples.clear();
            lastKeepAliveAt = 0L;
        }

        reportedMissing = false;
    }
}
