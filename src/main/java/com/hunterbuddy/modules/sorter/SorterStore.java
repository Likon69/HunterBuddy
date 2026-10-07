package com.hunterbuddy.modules.sorter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hunterbuddy.modules.sorter.logic.CategoryRules;
import com.hunterbuddy.modules.sorter.model.SorterProfile;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class SorterStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("HunterBuddy/Sorter");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static SorterProfile cached;
    private static String cachedKey;

    private SorterStore() {
    }


    public static SorterProfile get() {
        String key = key();
        if (cached == null || !key.equals(cachedKey)) {
            cached = load(key);
            cachedKey = key;
        }
        return cached;
    }

    public static void reload() {
        cached = null;
        get();
    }

    public static void save() {
        if (cached == null) {
            return;
        }
        File file = file(cachedKey);
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            file.getParentFile().mkdirs();
            try (Writer w = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(cached, w);
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("Could not save sorter profile {}", file, e);
        }
    }

    private static SorterProfile load(String key) {
        File file = file(key);
        if (file.isFile()) {
            try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                SorterProfile profile = GSON.fromJson(r, SorterProfile.class);
                if (profile != null) {
                    if (profile.reserves == null) {
                        profile.reserves = new ArrayList<>();
                    }
                    if (profile.rules == null || profile.rules.categories == null || profile.rules.categories.isEmpty()) {
                        profile.rules = CategoryRules.defaults();
                    }
                    return profile;
                }
            } catch (Exception e) {
                LOGGER.error("Bad sorter profile {}, starting a fresh one (old file kept as .broken)", file, e);
                file.renameTo(new File(file.getParentFile(), file.getName() + ".broken"));
            }
        }
        SorterProfile fresh = new SorterProfile();
        cached = fresh;
        cachedKey = key;
        save();
        return fresh;
    }

    private static File file(String key) {
        return new File(new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "sorter"), key + ".json");
    }

    private static String key() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String server = "unknown";
        if (mc.getCurrentServerEntry() != null) {
            server = mc.getCurrentServerEntry().address;
        } else if (mc.isInSingleplayer() && mc.getServer() != null) {
            server = "sp_" + mc.getServer().getSaveProperties().getLevelName();
        }
        String dim = mc.world == null ? "none" : mc.world.getRegistryKey().getValue().toString();
        return sanitize(server) + "__" + sanitize(dim);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }

    public static SorterProfile.Pos pos(BlockPos p) {
        return new SorterProfile.Pos(p.getX(), p.getY(), p.getZ());
    }

    public static BlockPos blockPos(SorterProfile.Pos p) {
        return new BlockPos(p.x, p.y, p.z);
    }
}
