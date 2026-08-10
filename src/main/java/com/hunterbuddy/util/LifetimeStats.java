package com.hunterbuddy.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

/**
 * The numbers that outlive a session: total distance, total time, total stashes.
 *
 * <p>Fed by folding SessionStats deltas in, once a minute, rather than by listening to events a
 * second time. The fold is delta-based on purpose: SessionStats resets when you join a server,
 * and the order two subscribers see that event in is not promised — so instead of trusting a
 * "fold before reset" that might run after it, a sample that shrinks is read as the reset itself
 * and the baseline starts over.
 */
public final class LifetimeStats {
    private static final LifetimeStats INSTANCE = new LifetimeStats();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final long FOLD_EVERY_MS = 60_000L;
    private static final long SAVE_EVERY_MS = 300_000L;

    /** What goes to disk. Public fields for Gson. */
    public static final class Data {
        public double distance;
        public long seconds;
        public int stashes;
    }

    private Data data = new Data();
    private boolean loaded;

    private double foldedDistance;
    private long foldedSeconds;
    private int foldedStashes;

    private long lastFoldAt;
    private long lastSaveAt;

    private LifetimeStats() {
    }

    public static LifetimeStats get() {
        return INSTANCE;
    }

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
        INSTANCE.load();
    }

    /**
     * Lifetime distance including the not-yet-folded tail of the running session.
     *
     * <p>The tail handles a reset the same way {@link #fold()} does: between a reconnect (which
     * zeroes SessionStats) and the next fold, {@code foldedDistance} still holds the old session's
     * large value. A plain {@code session - folded} would go negative and clamp to zero, freezing
     * the lifetime total for up to a minute; when the sample sits below the baseline, the whole of
     * it is the tail.
     */
    public synchronized double totalDistance() {
        double s = SessionStats.get().distance();
        return data.distance + (s >= foldedDistance ? s - foldedDistance : s);
    }

    public synchronized long totalSeconds() {
        long s = SessionStats.get().elapsedSeconds();
        return data.seconds + (s >= foldedSeconds ? s - foldedSeconds : s);
    }

    public synchronized int totalStashes() {
        int s = SessionStats.get().stashesFound();
        return data.stashes + (s >= foldedStashes ? s - foldedStashes : s);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now - lastFoldAt < FOLD_EVERY_MS) return;

        lastFoldAt = now;
        fold();

        if (now - lastSaveAt >= SAVE_EVERY_MS) {
            lastSaveAt = now;
            save();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        fold();
        save();
    }

    private synchronized void fold() {
        SessionStats session = SessionStats.get();

        double distance = session.distance();
        long seconds = session.elapsedSeconds();
        int stashes = session.stashesFound();

        // A counter below its baseline means SessionStats has reset under us; the new session
        // starts folding from zero and nothing is counted twice.
        if (distance < foldedDistance || seconds < foldedSeconds || stashes < foldedStashes) {
            foldedDistance = 0.0;
            foldedSeconds = 0L;
            foldedStashes = 0;
        }

        data.distance += Math.max(0.0, distance - foldedDistance);
        data.seconds += Math.max(0L, seconds - foldedSeconds);
        data.stashes += Math.max(0, stashes - foldedStashes);

        foldedDistance = distance;
        foldedSeconds = seconds;
        foldedStashes = stashes;
    }

    private File file() {
        return new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "lifetime.json");
    }

    private synchronized void load() {
        if (loaded) return;
        loaded = true;

        File file = file();
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Data read = GSON.fromJson(reader, Data.class);
            if (read != null) data = read;
        } catch (Exception e) {
            // A totals file that cannot be read is left alone on disk and simply not added to;
            // overwriting it with zeros would be the one way to actually lose the record.
            MeteorClient.LOG.warn("LifetimeStats: could not read {}: {}", file, e.toString());
        }
    }

    /** Written to a sibling and moved into place, so a crash mid-write cannot leave half a file. */
    private synchronized void save() {
        File file = file();
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) return;

        Path tmp = new File(parent, "lifetime.json.tmp").toPath();

        try {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }

            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            MeteorClient.LOG.warn("LifetimeStats: could not save {}: {}", file, e.toString());
        }
    }
}
