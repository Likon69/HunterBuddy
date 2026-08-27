package com.hunterbuddy.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.MeteorClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The trails the radar has followed, kept on disk between sessions.
 *
 * <p>A trail is remembered as a line rather than as a heap of chunks: an origin,
 * a direction, and how far along it the hits ran. The chunks are kept too, but
 * only so the map can draw what was actually found rather than an infinite line
 * through it.
 *
 * <p>Nothing here decides anything. A remembered trail never enters a fit and
 * never raises an alert — it is a drawing and a name, and the moment a live line
 * turns out to be the same trail it takes the entry over instead of adding a
 * second one.
 */
public final class TrailStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One remembered trail. Public fields because Gson writes them straight out. */
    public static final class Trail {
        public String id;
        public String dimension;
        public double originX;
        public double originZ;
        public double dirX;
        public double dirZ;
        public double spanMin;
        public double spanMax;
        public double heading;
        public int hits;
        public long firstSeen;
        public long lastSeen;

        /** When Discord was last told about this trail, so a resume can stay quiet. */
        public long lastNotified;
        public List<int[]> chunks = new ArrayList<>();
        public String startWaypoint;
        public String endWaypoint;

        /** Id of the leg before this one, when a trail turned a corner. */
        public String previous;

        /** Block positions of the two markers, so the map can draw them without Xaero. */
        public int startX;
        public int startZ;
        public int endX;
        public int endZ;

        public double length() {
            return spanMax - spanMin;
        }

        /** Signed distance from the line, in chunks, for a chunk coordinate. */
        public double lateral(double chunkX, double chunkZ) {
            return (chunkX - originX) * -dirZ + (chunkZ - originZ) * dirX;
        }

        /** Distance along the line, in chunks, measured from its own origin. */
        public double along(double chunkX, double chunkZ) {
            return (chunkX - originX) * dirX + (chunkZ - originZ) * dirZ;
        }
    }

    private static final class Model {
        List<Trail> trails = new ArrayList<>();
    }

    private final List<Trail> trails = new ArrayList<>();
    private boolean loaded;

    public List<Trail> all() {
        return trails;
    }

    public List<Trail> forDimension(String dimension) {
        List<Trail> out = new ArrayList<>();
        for (Trail trail : trails) {
            if (dimension.equals(trail.dimension)) out.add(trail);
        }

        return out;
    }

    public Trail byId(String id) {
        for (Trail trail : trails) {
            if (trail.id.equals(id)) return trail;
        }

        return null;
    }

    public Trail create(String dimension) {
        Trail trail = new Trail();
        trail.id = UUID.randomUUID().toString().substring(0, 8);
        trail.dimension = dimension;
        trail.firstSeen = System.currentTimeMillis();
        trails.add(trail);
        return trail;
    }

    public boolean remove(Trail trail) {
        return trails.remove(trail);
    }

    public void clear() {
        trails.clear();
    }

    public static File file() {
        return new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "trails.json");
    }

    /** Where the last copy taken before a wholesale delete sits. */
    public static File backupFile() {
        return new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "trails.json.bak");
    }

    public static boolean hasBackup() {
        return backupFile().exists();
    }

    /**
     * Copies the file aside before something that cannot be undone.
     *
     * <p>One level deep and overwritten each time: this is a filet against a
     * mistyped command, not a history. False means the copy was attempted and
     * failed, which is the only case a caller must stop for; nothing to copy is
     * a success, since an empty memory has nothing to lose.
     */
    public boolean backup() {
        File file = file();
        if (!file.exists()) return true;

        try {
            file.getParentFile().mkdirs();
            java.nio.file.Files.copy(file.toPath(), backupFile().toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) {
            HunterBuddyAddon.LOG.error("TrailStore: could not back trails.json up", e);
            return false;
        }
    }

    /**
     * Puts the copy back and rereads it.
     *
     * <p>The copy is left where it is: restoring twice by mistake is harmless,
     * and losing the only copy to a restore that went to the wrong world is not.
     */
    public boolean restore() {
        File backup = backupFile();
        if (!backup.exists()) return false;

        try {
            java.nio.file.Files.copy(backup.toPath(), file().toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            forceReload();
            return true;
        } catch (IOException | RuntimeException e) {
            HunterBuddyAddon.LOG.error("TrailStore: could not restore trails.json", e);
            return false;
        }
    }

    /** Reads the file once per session; later calls are cheap unless {@link #forceReload} is used. */
    public void load() {
        if (loaded) return;
        forceReload();
    }

    public void forceReload() {
        loaded = true;
        trails.clear();

        File file = file();
        if (!file.exists()) return;

        try (Reader reader = new FileReader(file)) {
            Model model = GSON.fromJson(reader, Model.class);
            if (model != null && model.trails != null) {
                for (Trail trail : model.trails) {
                    if (trail != null && trail.id != null && trail.dimension != null) trails.add(trail);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Includes a malformed file: a broken memory is worth a line in the log
            // and an empty list, never a crash on world join.
            HunterBuddyAddon.LOG.error("TrailStore: could not read trails.json", e);
        }
    }

    public void save() {
        File file = file();

        try {
            file.getParentFile().mkdirs();
            Model model = new Model();
            model.trails = trails;

            try (Writer writer = new FileWriter(file)) {
                GSON.toJson(model, writer);
            }
        } catch (IOException | RuntimeException e) {
            HunterBuddyAddon.LOG.error("TrailStore: could not write trails.json", e);
        }
    }
}
