package com.hunterbuddy.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;

/**
 * The running log of what the hunt has turned up, one entry per event.
 *
 * <p>SessionStats answers "how many"; this answers "what, where, when". The modules that detect
 * things publish here at the same spot they notify, so the feed cannot disagree with the chat
 * line or the webhook that announced the same find.
 *
 * <p>Bounded like everything else that grows during a session: the HUDs reading this show a
 * handful of recent lines, and nothing looks back further than the cap.
 */
public final class HuntFeed {
    private static final HuntFeed INSTANCE = new HuntFeed();

    private static final int MAX_ENTRIES = 256;

    /** What kind of thing happened. The colours live with the HUDs that draw them. */
    public enum Type {
        STASH,
        SPAWNER,
        PORTAL,
        PLAYER_ENTER,
        PLAYER_LEAVE,
        OLD_CHUNK,
        DANGER
    }

    /**
     * One event. {@code pos} may be null when the event has no single place.
     *
     * <p>{@code dimension} is where that position means something. A find is recorded with the
     * world it was found in because coordinates alone lie across a portal: 400 blocks in the
     * Nether is 3200 in the overworld, and a reader offering to point you at it has to know
     * whether the arrow would mean anything at all.
     */
    public record Entry(Type type, long at, String label, BlockPos pos, String dimension) {
        /** For entries with no dimension to speak of, such as the HUDs' editor previews. */
        public Entry(Type type, long at, String label, BlockPos pos) {
            this(type, at, label, pos, null);
        }

        public long ageMs() {
            return System.currentTimeMillis() - at;
        }
    }

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    private HuntFeed() {
    }

    public static HuntFeed get() {
        return INSTANCE;
    }

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    /**
     * Records an event.
     *
     * <p>Synchronised because a publisher is not guaranteed to be on the tick thread — the
     * notifier modules do webhook work off-thread, and a reader copying the deque mid-add would
     * see it torn.
     */
    public synchronized void publish(Type type, String label, BlockPos pos) {
        // Taken here rather than asked of the publishers: every one of them already knows where it
        // is, and none of them should have to remember to say so.
        String dimension = MeteorClient.mc.world == null
            ? null
            : MeteorClient.mc.world.getRegistryKey().getValue().toString();

        entries.addFirst(new Entry(type, System.currentTimeMillis(), label,
            pos == null ? null : pos.toImmutable(), dimension));

        while (entries.size() > MAX_ENTRIES) entries.removeLast();
    }

    /** Newest first. A copy, so the caller can walk it while events keep arriving. */
    public synchronized List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    /** Age of the newest entry of one type, in milliseconds, or -1 if there is none. */
    public synchronized long ageOfNewest(Type type) {
        for (Entry entry : entries) {
            if (entry.type == type) return entry.ageMs();
        }

        return -1L;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        synchronized (this) {
            entries.clear();
        }
    }
}
