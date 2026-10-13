package com.hunterbuddy.behavior.trace;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class JsonlSink implements TraceSink {
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final long MAX_AGE_DAYS = 7;
    private final Path directory;
    private final ArrayBlockingQueue<Entry> queue = new ArrayBlockingQueue<>(4_096);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private volatile String error;
    private String bot = "lab";

    public JsonlSink(Path directory) {
        this.directory = Objects.requireNonNull(directory);
        Thread worker = new Thread(this::write, "TreeJava JSONL");
        worker.setDaemon(true);
        worker.start();
    }

    public void session(String bot) {
        if (bot == null || !bot.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("invalid trace bot name");
        this.bot = bot;
    }

    @Override
    public void accept(TraceEvent event) {
        if (!queue.offer(new Entry(bot, Objects.requireNonNull(event)))) dropped.incrementAndGet();
    }

    public String status() {
        return "JSONL written=" + written.get() + " queued=" + queue.size() + " TRACE_DROPPED=" + dropped.get()
            + (error == null ? "" : " error=" + error);
    }

    private void write() {
        cleanupOldFiles();
        Gson gson = new Gson();
        BufferedWriter writer = null;
        Path file = null;
        long fileBytes = 0;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Entry entry = queue.take();
                Path next = directory.resolve(entry.bot + "-" + LocalDate.now() + ".jsonl");
                try {
                    if (writer == null || !next.equals(file)) {
                        if (writer != null) writer.close();
                        writer = null;
                        Files.createDirectories(directory);
                        writer = Files.newBufferedWriter(next, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        file = next;
                        fileBytes = Files.exists(next) ? Files.size(next) : 0;
                    }
                    byte[] line = (gson.toJson(entry.event) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                    if (fileBytes + line.length > MAX_FILE_BYTES) {
                        dropped.incrementAndGet();
                    } else {
                        writer.write(new String(line, StandardCharsets.UTF_8));
                        fileBytes += line.length;
                        if (queue.isEmpty()) writer.flush();
                        written.incrementAndGet();
                    }
                    error = null;
                } catch (IOException ex) {
                    dropped.incrementAndGet();
                    error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                    if (writer != null) {
                        try { writer.close(); } catch (IOException ignored) { error = ex.getMessage(); }
                        writer = null;
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ex) { error = ex.getMessage(); }
            }
        }
    }

    private void cleanupOldFiles() {
        try {
            if (!Files.isDirectory(directory)) return;
            Instant cutoff = Instant.now().minus(MAX_AGE_DAYS, ChronoUnit.DAYS);
            try (var listing = Files.list(directory)) {
                listing.filter(path -> path.getFileName().toString().endsWith(".jsonl")).forEach(path -> {
                    try {
                        if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }

    private record Entry(String bot, TraceEvent event) {
    }
}
