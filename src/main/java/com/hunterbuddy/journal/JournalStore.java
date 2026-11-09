package com.hunterbuddy.journal;

import java.util.Arrays;

public final class JournalStore {
    public record Snapshot(long revision, JournalEntry[] entries) {
    }

    private JournalEntry[] entries;
    private int head;
    private int size;
    private long sequence;
    private long revision;

    public JournalStore(int capacity) {
        entries = new JournalEntry[Math.max(1, Math.min(8192, capacity))];
    }

    public synchronized void add(long time, JournalSource source, JournalEntry.Level level, String message) {
        if (message == null) return;
        boolean truncated = message.length() > 8192;
        if (truncated) message = message.substring(0, 8192);
        if (message.isBlank()) return;
        if (truncated) message += "...";
        if (size == entries.length) {
            entries[head] = null;
            head = (head + 1) % entries.length;
            size--;
        }
        entries[(head + size++) % entries.length] = new JournalEntry(++sequence, time, source, level, message);
        revision++;
    }

    public synchronized boolean read(JournalBuffer target) {
        target.capacity(entries.length);
        if (target.revision() == revision) return false;
        long oldest = size == 0 ? sequence + 1 : entries[head].id();
        long newest = size == 0 ? sequence : entries[(head + size - 1) % entries.length].id();
        long from = Math.max(oldest, target.last() + 1);
        for (long id = from; id <= newest; id++) target.put(entries[(int) ((head + id - oldest) % entries.length)]);
        target.range(revision, oldest, newest, size);
        return true;
    }

    public synchronized Snapshot snapshot() {
        JournalEntry[] copy = new JournalEntry[size];
        for (int i = 0; i < size; i++) copy[i] = entries[(head + i) % entries.length];
        return new Snapshot(revision, copy);
    }

    public synchronized void clear() {
        Arrays.fill(entries, null);
        head = size = 0;
        revision++;
    }

    public synchronized void resize(int capacity) {
        capacity = Math.max(1, Math.min(8192, capacity));
        if (capacity == entries.length) return;
        JournalEntry[] copy = new JournalEntry[capacity];
        int count = Math.min(size, capacity);
        for (int i = 0; i < count; i++) copy[i] = entries[(head + size - count + i) % entries.length];
        entries = copy;
        head = 0;
        size = count;
        revision++;
    }
}
