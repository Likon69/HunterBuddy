package com.hunterbuddy.pet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hunterbuddy.HunterBuddyAddon;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

public final class PetMemory {
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("hunterbuddy/pet-memory.json");
    private long beers;
    private long fled;
    private long stashes;
    private long totems;
    private boolean dirty;
    private boolean writable = true;

    public void load() {
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            beers = value(data, "beers");
            fled = value(data, "players_fled");
            stashes = value(data, "stashes_found");
            totems = value(data, "totems_lost");
        } catch (Exception error) {
            writable = false;
            HunterBuddyAddon.LOG.warn("Pet memory could not be read; preserving the existing file.");
        }
    }

    public void note(PetEvent event, boolean hides, long count) {
        switch (event) {
            case DRINK -> beers += count;
            case PLAYER_ARRIVE -> { if (hides) fled += count; }
            case STASH_FOUND -> stashes += count;
            case TOTEM_POP -> totems += count;
            default -> { return; }
        }
        dirty = true;
    }

    public void save() {
        if (!dirty || !writable) return;
        try {
            Files.createDirectories(path.getParent());
            JsonObject data = new JsonObject();
            data.addProperty("beers", beers);
            data.addProperty("players_fled", fled);
            data.addProperty("stashes_found", stashes);
            data.addProperty("totems_lost", totems);
            Path temporary = path.resolveSibling("pet-memory.json.tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                new Gson().toJson(data, writer);
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (Exception error) {
            HunterBuddyAddon.LOG.warn("Pet memory could not be saved.");
        }
    }

    private static long value(JsonObject data, String key) {
        return data.has(key) ? Math.max(0, data.get(key).getAsLong()) : 0;
    }
}
