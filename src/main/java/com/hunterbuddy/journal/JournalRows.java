package com.hunterbuddy.journal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public final class JournalRows {
    public record Row(JournalEntry entry, int part, String prefix, String message) {
    }

    private final Map<Long, List<Row>> cache = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private long revision = -1;
    private double width;
    private double textScale;
    private boolean hidden;
    private int font;

    public boolean update(JournalStore.Snapshot snapshot, boolean hidden, double width, double textScale, int font,
                          ToDoubleFunction<String> measure) {
        boolean layout = this.width != width || this.textScale != textScale || this.hidden != hidden || this.font != font;
        if (!layout && revision == snapshot.revision()) return false;
        if (layout) cache.clear();
        this.width = width;
        this.textScale = textScale;
        this.hidden = hidden;
        this.font = font;
        revision = snapshot.revision();
        rows.clear();
        long first = snapshot.entries().length == 0 ? Long.MAX_VALUE : snapshot.entries()[0].id();
        cache.keySet().removeIf(id -> id < first);
        for (JournalEntry entry : snapshot.entries()) {
            List<Row> prepared = cache.get(entry.id());
            if (prepared == null) {
                List<String> parts = wrap(JournalText.message(entry, hidden), width, measure);
                prepared = new ArrayList<>(parts.size());
                for (int i = 0; i < parts.size(); i++) prepared.add(new Row(entry, i, i == 0 ? JournalText.prefix(entry) : "", parts.get(i)));
                cache.put(entry.id(), prepared);
            }
            rows.addAll(prepared);
        }
        return true;
    }

    public void clear() {
        cache.clear();
        rows.clear();
        revision = -1;
    }

    public int size() {
        return rows.size();
    }

    public Row get(int index) {
        return rows.get(index);
    }

    public int index(long id, int part) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.entry().id() > id || row.entry().id() == id && row.part() >= part) return i;
        }
        return Math.max(0, rows.size() - 1);
    }

    public static List<String> wrap(String text, double width, ToDoubleFunction<String> measure) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int low = start + 1;
            int high = text.length();
            int fit = start;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (measure.applyAsDouble(text.substring(start, middle)) <= width) {
                    fit = middle;
                    low = middle + 1;
                } else high = middle - 1;
            }
            if (fit > start && fit < text.length() && Character.isHighSurrogate(text.charAt(fit - 1))) fit--;
            if (fit <= start) fit = text.offsetByCodePoints(start, 1);
            if (fit < text.length()) {
                int space = text.lastIndexOf(' ', fit - 1);
                if (space > start + (fit - start) / 3) fit = space;
            }
            result.add(text.substring(start, fit));
            start = fit;
            while (start < text.length() && text.charAt(start) == ' ') start++;
        }
        if (result.isEmpty()) result.add("");
        return result;
    }
}
