package com.hunterbuddy.journal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;

public final class JournalRows {
    public record Row(JournalEntry entry, int part, String prefix, String message) {
    }

    private record Prepared(JournalEntry entry, int layout, Row[] rows) {
    }

    private Prepared[] cache = new Prepared[0];
    private Row[] visible = new Row[0];
    private JournalBuffer buffer;
    private ToDoubleFunction<String> measure;
    private UnaryOperator<String> prefix;
    private long seen = -1;
    private long anchor;
    private int part;
    private int count;
    private int limit;
    private int layout;
    private int font;
    private double width;
    private double textScale;
    private boolean hidden;
    private boolean following = true;
    private boolean dirty = true;

    public boolean update(JournalBuffer buffer, boolean hidden, double width, double textScale, int font, int limit,
                          ToDoubleFunction<String> measure, UnaryOperator<String> prefix) {
        limit = Math.max(0, Math.min(1024, limit));
        if (this.width != width || this.textScale != textScale || this.hidden != hidden || this.font != font) {
            layout++;
            dirty = true;
        }
        if (this.limit != limit) {
            this.limit = limit;
            visible = new Row[limit];
            dirty = true;
        }
        if (cache.length != buffer.capacity()) {
            cache = new Prepared[buffer.capacity()];
            dirty = true;
        }
        this.buffer = buffer;
        this.measure = measure;
        this.prefix = prefix;
        this.width = width;
        this.textScale = textScale;
        this.hidden = hidden;
        this.font = font;
        if (!dirty && seen == buffer.revision()) return false;
        seen = buffer.revision();
        dirty = false;
        Arrays.fill(visible, null);
        count = 0;
        if (buffer.size() == 0) { Arrays.fill(cache, null); return true; }
        if (limit == 0) return true;
        if (following) tail();
        else {
            int index = buffer.index(anchor);
            if (buffer.get(index).id() != anchor) part = 0;
            while (index < buffer.size() && count < limit) {
                Row[] rows = prepare(index).rows();
                part = Math.max(0, Math.min(part, rows.length - 1));
                for (int i = part; i < rows.length && count < limit; i++) visible[count++] = rows[i];
                index++;
                part = 0;
            }
            if (count < limit) { following = true; tail(); }
        }
        if (count > 0) {
            anchor = visible[0].entry().id();
            part = visible[0].part();
        }
        return true;
    }

    private void tail() {
        count = 0;
        int write = limit;
        for (int i = buffer.size() - 1; i >= 0 && write > 0; i--) {
            Row[] rows = prepare(i).rows();
            for (int j = rows.length - 1; j >= 0 && write > 0; j--) visible[--write] = rows[j];
        }
        count = limit - write;
        System.arraycopy(visible, write, visible, 0, count);
        Arrays.fill(visible, count, visible.length, null);
    }

    private Prepared prepare(int index) {
        JournalEntry entry = buffer.get(index);
        int slot = (int) (entry.id() % cache.length);
        Prepared prepared = cache[slot];
        if (prepared != null && prepared.entry() == entry && prepared.layout() == layout) return prepared;
        List<String> parts = wrap(JournalText.message(entry, hidden), width, measure);
        Row[] rows = new Row[parts.size()];
        String label = prefix.apply(JournalText.prefix(entry));
        for (int i = 0; i < rows.length; i++) rows[i] = new Row(entry, i, i == 0 ? label : "", parts.get(i));
        prepared = new Prepared(entry, layout, rows);
        cache[slot] = prepared;
        return prepared;
    }

    public void scroll(int amount) {
        if (buffer == null || buffer.size() == 0 || count == 0) return;
        int index = buffer.index(anchor);
        int position = Math.min(part, prepare(index).rows().length - 1);
        int direction = Integer.signum(amount);
        for (int i = 0; i < Math.min(4096, Math.abs(amount)); i++) {
            if (direction < 0) {
                if (position > 0) position--;
                else if (index > 0) position = prepare(--index).rows().length - 1;
                else break;
            } else {
                if (position + 1 < prepare(index).rows().length) position++;
                else if (index + 1 < buffer.size()) { index++; position = 0; }
                else break;
            }
        }
        anchor = buffer.get(index).id();
        part = position;
        following = false;
        dirty = true;
    }

    public void home() {
        if (buffer == null || buffer.size() == 0) return;
        anchor = buffer.get(0).id();
        part = 0;
        following = false;
        dirty = true;
    }

    public void end() {
        following = true;
        dirty = true;
    }

    public void pause() {
        following = false;
    }

    public double position() {
        if (following || buffer == null || count == 0) return 1;
        double start = rowPosition(visible[0], false);
        return start / Math.max(0.000001, buffer.size() - extent());
    }

    public double fraction() {
        return buffer == null || buffer.size() == 0 || count == 0 ? 1 : Math.min(1, extent() / buffer.size());
    }

    private double extent() {
        return rowPosition(visible[count - 1], true) - rowPosition(visible[0], false);
    }

    private double rowPosition(Row row, boolean after) {
        int index = buffer.index(row.entry().id());
        return index + (double) (row.part() + (after ? 1 : 0)) / prepare(index).rows().length;
    }

    public int size() {
        return count;
    }

    public Row get(int index) {
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
        return visible[index];
    }

    public void clear() {
        cache = new Prepared[0];
        buffer = null;
        measure = null;
        prefix = null;
        Arrays.fill(visible, null);
        count = 0;
        seen = -1;
        anchor = 0;
        following = true;
        dirty = true;
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
