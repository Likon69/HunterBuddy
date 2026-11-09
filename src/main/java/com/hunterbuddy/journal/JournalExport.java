package com.hunterbuddy.journal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class JournalExport {
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS");

    private JournalExport() {
    }

    public static Path save(Path gameDirectory, JournalEntry[] entries, boolean hidden) throws IOException {
        Path directory = gameDirectory.resolve("hunterbuddy").resolve("logs");
        Files.createDirectories(directory);
        StringBuilder text = new StringBuilder();
        for (JournalEntry entry : entries) text.append(JournalText.format(entry, hidden)).append('\n');
        String name = "client-log_" + NAME.format(LocalDateTime.now());
        for (int i = 0; ; i++) {
            Path target = directory.resolve(name + (i == 0 ? "" : "_" + i) + ".txt");
            try {
                Files.writeString(target, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                return target;
            } catch (FileAlreadyExistsException ignored) {
            }
        }
    }
}
