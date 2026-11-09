package com.hunterbuddy.journal;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class JournalWriterTest {
    @Test
    void uncheckedWorkerFailureIsDisplayedAndLoggedWithTheOriginalCause() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var status = new AtomicReference<String>();
        var cause = new AtomicReference<Throwable>();
        var done = new CountDownLatch(1);
        JournalWriter writer = new JournalWriter(executor, status::set, (message, error) -> {
            cause.set(error); done.countDown();
        }, message -> fail("Unexpected success"));
        var denied = new SecurityException("Denied test directory");
        try {
            writer.save("journal", () -> { throw denied; });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertSame(denied, cause.get());
            assertTrue(status.get().contains("SecurityException: Denied test directory"));
        } finally { writer.shutdown(); }
    }

    @Test
    void stoppedExecutorReportsSubmissionFailureInsteadOfLeavingSavingStatus() {
        var executor = Executors.newSingleThreadExecutor();
        executor.shutdown();
        var status = new AtomicReference<String>();
        var cause = new AtomicReference<Throwable>();
        JournalWriter writer = new JournalWriter(executor, status::set, (message, error) -> cause.set(error), message -> fail());
        writer.save("performance report", () -> Path.of("unused"));
        assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, cause.get());
        assertTrue(status.get().startsWith("Could not save performance report"));
    }

    @Test
    void successfulExportCreatesTheDirectoryAndReportsItsAbsolutePath(@TempDir Path game) throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var done = new CountDownLatch(1);
        var status = new AtomicReference<String>();
        JournalWriter writer = new JournalWriter(executor, status::set, (message, error) -> fail(message, error), message -> done.countDown());
        try {
            writer.save("journal", () -> JournalExport.save(game, new JournalEntry[0], true));
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(Files.isDirectory(game.resolve("hunterbuddy/logs")));
            assertTrue(status.get().contains(game.toAbsolutePath().toString()));
        } finally { writer.shutdown(); }
    }
}
