package com.hunterbuddy.behavior.persist;

import com.google.gson.Gson;
import com.hunterbuddy.behavior.bot.BotStats;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.core.UtilityFailure;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class PersistentStore {
    private static final Map<String, Class<?>> TYPES = Map.ofEntries(Map.entry("boolean", Boolean.class), Map.entry("string", String.class),
        Map.entry("integer", Integer.class), Map.entry("long", Long.class), Map.entry("double", Double.class),
        Map.entry("point", TargetPoint.class), Map.entry("failure", UtilityFailure.class), Map.entry("stats", BotStats.Snapshot.class));
    private static final Key<BotStats.Snapshot> STATS = new Key<>("stats", BotStats.Snapshot.class, Scope.WORLD, true);
    private final Path directory;
    private final Gson gson = new Gson();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(128), task -> { Thread thread = new Thread(task, "TreeJava store"); thread.setDaemon(true); return thread; });
    private volatile String error;
    private final Set<Path> unreadable = new HashSet<>();

    public PersistentStore(Path directory) { this.directory = Objects.requireNonNull(directory); }

    public CompletableFuture<Map<Key<?>, Object>> load(String world, String owner) {
        return submit(() -> read(path(world, owner)));
    }

    public CompletableFuture<Void> save(String world, String owner, Map<Key<?>, Object> values) {
        Map<Key<?>, Object> snapshot = Map.copyOf(values);
        return submit(() -> { write(path(world, owner), snapshot, false); return null; });
    }

    public CompletableFuture<Void> overwrite(String world, String owner, Map<Key<?>, Object> values) {
        Map<Key<?>, Object> snapshot = Map.copyOf(values);
        return submit(() -> { write(path(world, owner), snapshot, true); return null; });
    }

    public CompletableFuture<BotStats.Snapshot> loadStats(String bot) {
        return submit(() -> (BotStats.Snapshot) read(directory.resolve("stats-" + safe(bot) + ".json")).getOrDefault(STATS, BotStats.EMPTY));
    }

    public CompletableFuture<Void> saveStats(String bot, BotStats.Snapshot snapshot) {
        return submit(() -> { write(directory.resolve("stats-" + safe(bot) + ".json"), Map.of(STATS, snapshot), false); return null; });
    }

    public String status() { return "Store queued=" + worker.getQueue().size() + (error == null ? "" : " error=" + error); }

    private <T> CompletableFuture<T> submit(Operation<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try { result.complete(action.run()); }
                catch (IOException | RuntimeException ex) { error = ex.getClass().getSimpleName() + ": " + ex.getMessage(); result.completeExceptionally(ex); }
            });
        } catch (RejectedExecutionException ex) {
            error = "store queue is full";
            result.completeExceptionally(ex);
        }
        return result;
    }

    private Map<Key<?>, Object> read(Path path) throws IOException {
        try {
            if (Files.notExists(path)) {
                unreadable.remove(path);
                return Map.of();
            }
            if (Files.size(path) > sizeLimit(path)) throw new IOException("store exceeds size limit");
            StoreEntry entry = gson.fromJson(Files.readString(path, StandardCharsets.UTF_8), StoreEntry.class);
            if (entry == null || entry.values() == null) throw new IOException("invalid store structure");
            if (entry.schema() != StoreEntry.SCHEMA) throw new IOException("unsupported store schema " + entry.schema());
            if (entry.values().size() > 4_096) throw new IOException("too many durable entries");
            Map<Key<?>, Object> values = new LinkedHashMap<>();
            for (var value : entry.values().entrySet()) {
                Class<?> type = TYPES.get(value.getValue().type());
                Scope scope = Scope.valueOf(value.getValue().scope());
                if (type == null || scope == Scope.TASK) continue;
                Object decoded = gson.fromJson(value.getValue().json(), type);
                String prefix = scope.name() + "/";
                if (decoded != null && value.getKey().startsWith(prefix)) values.put(new Key<>(value.getKey().substring(prefix.length()), type, scope, true), decoded);
            }
            unreadable.remove(path);
            return Map.copyOf(values);
        } catch (IOException | RuntimeException ex) {
            unreadable.add(path);
            throw new IOException("Cannot read " + path.getFileName() + ": " + Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()), ex);
        }
    }

    private void write(Path path, Map<Key<?>, Object> snapshot, boolean overwrite) throws IOException {
        if (!overwrite && unreadable.contains(path)) throw new IOException("Unreadable store is protected: " + path.getFileName());
        Map<String, StoreEntry.Value> values = new LinkedHashMap<>();
        for (var entry : snapshot.entrySet()) {
            Key<?> key = entry.getKey();
            if (!key.durable() || key.scope() == Scope.TASK) continue;
            String type = TYPES.entrySet().stream().filter(candidate -> candidate.getValue() == key.type()).map(Map.Entry::getKey)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("unsupported durable type: " + key.type().getName()));
            values.put(key.scope().name() + "/" + key.name(), new StoreEntry.Value(type, key.scope().name(), gson.toJson(entry.getValue())));
        }
        if (values.size() > 4_096) throw new IOException("too many durable entries");
        String data = gson.toJson(new StoreEntry(StoreEntry.SCHEMA, values));
        if (data.getBytes(StandardCharsets.UTF_8).length > sizeLimit(path)) throw new IOException("store exceeds size limit");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, data, StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (overwrite) unreadable.remove(path);
        } finally { Files.deleteIfExists(temporary); }
    }

    private Path path(String world, String owner) { return directory.resolve(hash(world)).resolve(safe(owner) + ".json"); }

    private long sizeLimit(Path path) {
        return path.getFileName().toString().equals("bot-world-knowledge.json") ? 67_108_864 : 1_048_576;
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_:-]+")) throw new IllegalArgumentException("invalid store name");
        return value.replace(':', '-');
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    @FunctionalInterface
    private interface Operation<T> { T run() throws IOException; }
}
