package com.hunterbuddy.journal;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class JournalWriter {
    @FunctionalInterface
    public interface Export { Path save() throws Exception; }

    private final ExecutorService executor;
    private final Consumer<String> status;
    private final BiConsumer<String, Throwable> failure;
    private final Consumer<String> success;
    private volatile boolean busy;

    public JournalWriter(Consumer<String> status, BiConsumer<String, Throwable> failure, Consumer<String> success) {
        this(Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "HunterBuddy Journal Export");
            thread.setDaemon(true);
            return thread;
        }), status, failure, success);
    }

    JournalWriter(ExecutorService executor, Consumer<String> status, BiConsumer<String, Throwable> failure, Consumer<String> success) {
        this.executor = executor;
        this.status = status;
        this.failure = failure;
        this.success = success;
    }

    public synchronized void save(String name, Export export) {
        if (busy) { status.accept("An export is already running"); return; }
        busy = true;
        status.accept("Saving " + name + "...");
        try {
            executor.execute(() -> {
                try {
                    Path target = export.save().toAbsolutePath().normalize();
                    String message = "Saved " + name + ": " + target;
                    status.accept(message);
                    success.accept(message);
                } catch (Throwable error) {
                    failed(name, error);
                    if (error instanceof Error fatal) throw fatal;
                } finally {
                    busy = false;
                }
            });
        } catch (RuntimeException error) {
            busy = false;
            failed(name, error);
        }
    }

    public void failed(String name, Throwable error) {
        String message = "Could not save " + name + ": " + error.getClass().getSimpleName() + ": " + error.getMessage();
        status.accept(message);
        failure.accept(message, error);
    }

    public void shutdown() { executor.shutdown(); }
}
