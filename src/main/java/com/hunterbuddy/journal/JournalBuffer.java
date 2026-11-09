package com.hunterbuddy.journal;

import java.util.Arrays;

public final class JournalBuffer {
    private JournalEntry[] entries = new JournalEntry[0];
    private long first;
    private long last;
    private long revision = -1;
    private int size;

    void capacity(int capacity) {
        if (entries.length == capacity) return;
        entries = new JournalEntry[capacity];
        first = last = size = 0;
        revision = -1;
    }

    void put(JournalEntry entry) {
        entries[(int) (entry.id() % entries.length)] = entry;
    }

    void range(long revision, long first, long last, int size) {
        if (size < this.size) {
            for (int i = 0; i < entries.length; i++) {
                JournalEntry entry = entries[i];
                if (entry != null && (entry.id() < first || entry.id() > last)) entries[i] = null;
            }
        }
        this.revision = revision;
        this.first = first;
        this.last = last;
        this.size = size;
    }

    public JournalEntry get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        return entries[(int) ((first + index) % entries.length)];
    }

    public int index(long id) {
        return (int) Math.max(0, Math.min(Math.max(0, size - 1), id - first));
    }

    public int capacity() {
        return entries.length;
    }

    public int size() {
        return size;
    }

    public long revision() {
        return revision;
    }

    public long last() {
        return last;
    }

    public void clear() {
        Arrays.fill(entries, null);
        first = last = size = 0;
        revision = -1;
    }
}
