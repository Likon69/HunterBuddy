package com.hunterbuddy.journal;

public final class JournalSelection {
    private long anchor;
    private long focus;

    public void start(long id, boolean extend) {
        if (!extend || anchor == 0) anchor = id;
        focus = id;
    }

    public void extend(long id) {
        if (anchor != 0) focus = id;
    }

    public boolean contains(long id) {
        return anchor != 0 && id >= Math.min(anchor, focus) && id <= Math.max(anchor, focus);
    }

    public void clear() {
        anchor = focus = 0;
    }

    public String copy(JournalEntry[] entries, boolean hidden) {
        StringBuilder result = new StringBuilder();
        for (JournalEntry entry : entries) {
            if (!contains(entry.id())) continue;
            if (!result.isEmpty()) result.append('\n');
            result.append(JournalText.format(entry, hidden));
        }
        return result.toString();
    }
}
