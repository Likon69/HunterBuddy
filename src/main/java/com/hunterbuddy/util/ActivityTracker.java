package com.hunterbuddy.util;

import com.hunterbuddy.modules.AutoFlyingRegear;
import com.hunterbuddy.modules.MlepMine;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

/**
 * What the session was spent doing, second by second, as a run of segments.
 *
 * <p>One classifier rather than one per HUD, for the SessionStats reason: the timeline and the
 * per-hour ratios have to agree on what counted as active time, or "stashes per hour" quietly
 * uses a different hour than the frise drawn next to it.
 *
 * <p>Priority runs regear &gt; mine &gt; fly &gt; afk &gt; idle: a regear mid-flight is a regear,
 * and AFK only wins when nothing else claims the second.
 */
public final class ActivityTracker {
    private static final ActivityTracker INSTANCE = new ActivityTracker();

    private static final long AFK_AFTER_MS = 60_000L;
    private static final int MAX_SEGMENTS = 4096;

    public enum Activity {
        FLY,
        MINE,
        REGEAR,
        AFK,
        IDLE
    }

    /** One stretch of one activity. The end of a segment is the start of the next. */
    public record Segment(Activity activity, long startedAt) {
    }

    private final List<Segment> segments = new ArrayList<>();

    private MlepMine mine;
    private AutoFlyingRegear regear;

    private double lastX = Double.NaN;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;
    private long lastInputAt;

    private ActivityTracker() {
    }

    public static ActivityTracker get() {
        return INSTANCE;
    }

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    /** The segments so far, oldest first. A copy. */
    public synchronized List<Segment> segments() {
        return new ArrayList<>(segments);
    }

    public synchronized long startedAt() {
        return segments.isEmpty() ? System.currentTimeMillis() : segments.get(0).startedAt;
    }

    /** Session seconds spent on anything except AFK — the divisor for every per-hour ratio. */
    public synchronized long activeSeconds() {
        if (segments.isEmpty()) return 0L;

        long total = 0L;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            long end = i + 1 < segments.size()
                ? segments.get(i + 1).startedAt
                : System.currentTimeMillis();

            if (segment.activity != Activity.AFK) total += end - segment.startedAt;
        }

        return total / 1000L;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        synchronized (this) {
            segments.clear();
            lastX = Double.NaN;
            lastInputAt = System.currentTimeMillis();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return;

        noteInput();

        Activity current = classify();

        synchronized (this) {
            if (segments.isEmpty() || segments.get(segments.size() - 1).activity != current) {
                if (segments.size() >= MAX_SEGMENTS) segments.remove(0);
                segments.add(new Segment(current, System.currentTimeMillis()));
            }
        }
    }

    /**
     * Remembers the last time the player did anything.
     *
     * <p>Position and view direction together: an AFK pool moves you without input, but it does
     * not turn your head, and a hand-flown line does both. Either changing counts as presence.
     */
    private void noteInput() {
        var player = MeteorClient.mc.player;
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYaw();
        float pitch = player.getPitch();

        if (Double.isNaN(lastX)
            || Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ) > 0.05
            || Math.abs(yaw - lastYaw) > 0.5f
            || Math.abs(pitch - lastPitch) > 0.5f) {
            lastInputAt = System.currentTimeMillis();
        }

        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastPitch = pitch;
    }

    private Activity classify() {
        if (regear == null) regear = Modules.get().get(AutoFlyingRegear.class);
        if (mine == null) mine = Modules.get().get(MlepMine.class);

        if (regear != null && regear.isActive() && regear.isBusy()) return Activity.REGEAR;
        if (mine != null && mine.isActive() && mine.isMining()) return Activity.MINE;
        if (MeteorClient.mc.player.isGliding()) return Activity.FLY;
        if (System.currentTimeMillis() - lastInputAt > AFK_AFTER_MS) return Activity.AFK;

        return Activity.IDLE;
    }
}
