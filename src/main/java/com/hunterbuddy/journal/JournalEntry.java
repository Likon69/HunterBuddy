package com.hunterbuddy.journal;

public record JournalEntry(long id, long time, JournalSource source, Level level, String message) {
    public enum Level { INFO, WARN, ERROR }
}
