package com.hunterbuddy.behavior.trace;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class JsonlSink implements TraceSink {
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
        Gson gson = new Gson();
        BufferedWriter writer = null;
        Path file = null;
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
                    }
                    writer.write(gson.toJson(entry.event));
                    writer.newLine();
                    if (queue.isEmpty()) writer.flush();
                    written.incrementAndGet();
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

    private record Entry(String bot, TraceEvent event) {
    }
}
