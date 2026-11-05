package com.hunterbuddy.modules.signs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hunterbuddy.HunterBuddyAddon;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import meteordevelopment.meteorclient.MeteorClient;

public final class SignHistoryStore {
    private static final String FOLDER_NAME = "sign-history";
    private static final String FILE_NAME = "signs.json";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String CORRUPT_SUFFIX = ".corrupt-";

    private final Gson gson = new GsonBuilder().create();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SignHistory-io");
        thread.setDaemon(true);
        return thread;
    });

    public CompletableFuture<Map<SignKey, SignRecord>> read(String world) {
        Path file = file(world);
        return CompletableFuture.supplyAsync(() -> readNow(file), ioExecutor);
    }

    private Map<SignKey, SignRecord> readNow(Path file) {
        Map<SignKey, SignRecord> records = new LinkedHashMap<>();
        if (!Files.exists(file)) return records;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<SignRecord> loaded = gson.fromJson(reader, new TypeToken<List<SignRecord>>() { }.getType());
            if (loaded != null) for (SignRecord record : loaded) if (record != null && record.isValid()) records.put(record.key(), record);
        } catch (Exception failure) {
            HunterBuddyAddon.LOG.error("Error reading sign history", failure);
            setAside(file);
            records.clear();
        }
        return records;
    }

    private void setAside(Path file) {
        Path corrupt = file.resolveSibling(file.getFileName() + CORRUPT_SUFFIX + System.currentTimeMillis());
        try {
            Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            HunterBuddyAddon.LOG.error("Error setting aside corrupt sign history", failure);
        }
    }

    public void write(String world, List<SignRecord> snapshot) {
        Path file = file(world);
        ioExecutor.execute(() -> writeNow(file, snapshot));
    }

    private void writeNow(Path file, List<SignRecord> snapshot) {
        Path temporary = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(snapshot, writer);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            HunterBuddyAddon.LOG.error("Error saving sign history", failure);
        }
    }

    private static Path file(String world) {
        return MeteorClient.FOLDER.toPath().resolve(FOLDER_NAME).resolve(world).resolve(FILE_NAME);
    }
}
