package com.hunterbuddy.modules.regear.arealoader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public final class AreaSaveFiles {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String ROOT_FOLDER = "arealoader";
    private static final String DEFAULT_SAVE_NAME = "default";
    private static final String[] ALL_SUFFIXES = {"_overworld", "_nether", "_end", ""};
    private static final long REPORT_PERIOD_MS = 30000;
    private static final long REPORT_WINDOW_MS = 1000;

    private final AreaLoader area;
    private final AreaDebug debug;
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private String rememberedSuffix;

    AreaSaveFiles(AreaLoader area, AreaDebug debug) {
        this.area = area;
        this.debug = debug;
    }

    public static String suffixOf(RegistryKey<World> dimension) {
        if (dimension == World.NETHER) return "_nether";
        if (dimension == World.END) return "_end";
        return "_overworld";
    }

    public static File fileFor(AreaLoader area, String fileName, String suffix) {
        String saveName = area.saveLocation.get().isBlank() ? DEFAULT_SAVE_NAME : area.saveLocation.get();
        File folder = new File(new File(MeteorClient.FOLDER, ROOT_FOLDER), saveName);
        return new File(folder, fileName + suffix + ".json");
    }

    String currentSuffix() {
        if (mc.world != null) {
            rememberedSuffix = suffixOf(mc.world.getRegistryKey());
        }
        return rememberedSuffix;
    }

    File file(String fileName) {
        String suffix = currentSuffix();
        if (suffix == null) {
            debug.info("Cannot build the save path, the dimension is unknown");
            return null;
        }
        return fileForDimension(fileName, suffix);
    }

    File fileForDimension(String fileName, String suffix) {
        return fileFor(area, fileName, suffix);
    }

    void save(String fileName, boolean goingToStart, AreaLoaderMode.PathingData data) {
        if (data == null) return;
        File file = file(fileName);
        if (file == null) return;
        if (!goingToStart && mc.world != null && mc.player != null) {
            data.currPos = mc.player.getBlockPos();
        }
        try {
            writeJson(file, data);
        } catch (IOException e) {
            debug.info("Could not save progress: %s", e.getMessage());
            return;
        }
        reportSave(file);
    }

    public static void writeJson(File file, Object data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        }
    }

    private void reportSave(File file) {
        if (!file.exists() || file.length() == 0) {
            ChatUtils.warning("Save file is missing or empty: %s", file.getAbsolutePath());
            return;
        }
        if (System.currentTimeMillis() % REPORT_PERIOD_MS < REPORT_WINDOW_MS) {
            ChatUtils.info("Progress saved to %s (%d bytes)", file.getAbsolutePath(), file.length());
        }
    }

    boolean delete(File file) {
        return file != null && file.exists() && file.delete();
    }

    int deleteEveryFile() {
        int deleted = 0;
        for (AreaLoaderModes mode : AreaLoaderModes.values()) {
            for (String suffix : ALL_SUFFIXES) {
                if (delete(fileForDimension(mode.name(), suffix))) {
                    deleted++;
                }
            }
        }
        rememberedSuffix = null;
        return deleted;
    }
}
