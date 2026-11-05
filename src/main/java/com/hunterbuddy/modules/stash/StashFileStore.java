package com.hunterbuddy.modules.stash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.StashFinder.StashChunk;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.Utils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StashFileStore {
    private static final String FOLDER_NAME = "stashes";
    private static final String FILE_NAME = "stashes.json";
    private static final String LEGACY_FILE_NAME = "stashes-mlep.json";
    private static final String TEMP_SUFFIX = ".tmp";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "StashFinder-io");
        thread.setDaemon(true);
        return thread;
    });

    public List<StashChunk> read() {
        Path file = readableFile();
        if (!Files.exists(file)) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<StashChunk> loaded = gson.fromJson(reader, new TypeToken<List<StashChunk>>() { }.getType());
            return restoreCoordinates(loaded);
        } catch (Exception failure) {
            return new ArrayList<>();
        }
    }

    public void writeComplete(List<StashChunk> snapshot) {
        ioExecutor.execute(() -> write(snapshot));
    }

    public void writeMergedWithDisk(List<StashChunk> snapshot) {
        ioExecutor.execute(() -> write(mergeWithDisk(snapshot)));
    }

    public static List<StashChunk> mergeByPosition(List<StashChunk> base, List<StashChunk> winners) {
        Map<StashChunk, StashChunk> merged = new LinkedHashMap<>();
        for (StashChunk chunk : base) merged.put(chunk, chunk);
        for (StashChunk chunk : winners) merged.put(chunk, chunk);
        return new ArrayList<>(merged.values());
    }

    private List<StashChunk> mergeWithDisk(List<StashChunk> snapshot) {
        return mergeByPosition(read(), snapshot);
    }

    private void write(List<StashChunk> chunks) {
        Path file = file();
        Path temporary = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(chunks, writer);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            HunterBuddyAddon.LOG.error("Error saving stash list", failure);
        }
    }

    private static synchronized Path readableFile() {
        Path file = file();
        Path legacy = file.resolveSibling(LEGACY_FILE_NAME);
        if (Files.exists(file) || !Files.exists(legacy)) return file;
        try {
            Files.move(legacy, file);
            return file;
        } catch (IOException failure) {
            HunterBuddyAddon.LOG.error("Error migrating stash list", failure);
            return legacy;
        }
    }

    private static List<StashChunk> restoreCoordinates(List<StashChunk> loaded) {
        if (loaded == null) return new ArrayList<>();
        loaded.forEach(StashChunk::calculatePos);
        return new ArrayList<>(loaded);
    }

    private static Path file() {
        return MeteorClient.FOLDER.toPath().resolve(FOLDER_NAME).resolve(Utils.getFileWorldName()).resolve(FILE_NAME);
    }
}
