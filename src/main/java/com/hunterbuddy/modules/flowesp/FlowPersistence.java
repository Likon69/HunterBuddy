package com.hunterbuddy.modules.flowesp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.client.MinecraftClient;

final class FlowPersistence {
    private static final int FORMAT_MAGIC = 0x464C5731;
    private static final String OVERWORLD_FILE = "overworld.dat";
    private static final String NETHER_FILE = "nether.dat";
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final FlowCache cache;
    private final StorageOptions options;
    private final Consumer<String> debug;
    private ExecutorService ioExecutor;
    private ScheduledExecutorService autoSave;

    FlowPersistence(FlowCache cache, StorageOptions options, Consumer<String> debug) {
        this.cache = cache;
        this.options = options;
        this.debug = debug;
    }

    private Path directory() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("meteor-client").resolve("flow-esp-cache");
    }

    void start() {
        ioExecutor = Executors.newSingleThreadExecutor(task -> daemon(task, "FlowESP-IO"));
        if (!options.persist.get()) return;
        ioExecutor.execute(this::loadAll);
        int interval = options.saveInterval.get();
        if (interval > 0) {
            autoSave = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "FlowESP-AutoSave"));
            autoSave.scheduleWithFixedDelay(this::saveIfDirty, interval, interval, TimeUnit.SECONDS);
        }
    }

    void saveIfDirty() {
        if (ioExecutor == null || !cache.consumeDirty()) return;
        ioExecutor.execute(this::writeAll);
    }

    void stop() {
        if (autoSave != null) {
            autoSave.shutdownNow();
            autoSave = null;
        }
        if (ioExecutor == null) return;
        if (options.persist.get()) {
            saveIfDirty();
        }
        ioExecutor.shutdown();
        awaitIdle();
        ioExecutor = null;
    }

    private void awaitIdle() {
        try {
            ioExecutor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private void loadAll() {
        int overworld = load(OVERWORLD_FILE, cache.overworld());
        int nether = load(NETHER_FILE, cache.nether());
        debug.accept("Loaded " + overworld + " Overworld and " + nether + " Nether chunks from disk");
    }

    private int load(String fileName, DimensionCache target) {
        Path file = directory().resolve(fileName);
        if (!Files.isRegularFile(file)) return 0;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != FORMAT_MAGIC) return 0;
            int count = in.readInt();
            for (int index = 0; index < count; index++) {
                target.add(FlowChunkCodec.read(in));
            }
            return count;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private void writeAll() {
        try {
            Files.createDirectories(directory());
            write(OVERWORLD_FILE, cache.overworld().snapshot());
            write(NETHER_FILE, cache.nether().snapshot());
        } catch (IOException e) {
            debug.accept("Could not save the cache: " + e.getMessage());
        }
    }

    private void write(String fileName, List<FlowChunk> chunks) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(directory().resolve(fileName))))) {
            out.writeInt(FORMAT_MAGIC);
            out.writeInt(chunks.size());
            for (FlowChunk chunk : chunks) {
                FlowChunkCodec.write(out, chunk);
            }
        }
    }
}
