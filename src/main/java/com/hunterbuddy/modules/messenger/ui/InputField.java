package com.hunterbuddy.modules.messenger.ui;

import meteordevelopment.meteorclient.utils.render.color.Color;
import org.lwjgl.glfw.GLFW;

public final class InputField {
    private final StringBuilder text = new StringBuilder();
    private int cursor;
    private int view;
    private int max = 256;
    private int anchor = -1;
    private int shown;
    private long lastEdit;

    public String text() {
        return text.toString();
    }

    public void clear() {
        text.setLength(0);
        cursor = 0;
        view = 0;
        anchor = -1;
    }

    public void limit(int max) {
        this.max = Math.max(1, max);
        if (text.length() > this.max) {
            text.setLength(safeLength(text, this.max));
            cursor = Math.min(cursor, text.length());
            view = Math.min(view, text.length());
            anchor = -1;
        }
    }

    public boolean hasSelection() {
        return anchor >= 0 && anchor != cursor;
    }

    public void insert(String value) {
        StringBuilder clean = new StringBuilder();
        value.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(clean::appendCodePoint);
        if (clean.isEmpty()) return;

        if (anchor == cursor) anchor = -1;
        deleteSelection();
        int room = max - text.length();
        if (room <= 0) return;

        String part = clean.length() > room ? clean.substring(0, safeLength(clean, room)) : clean.toString();
        text.insert(cursor, part);
        cursor += part.length();
        lastEdit = System.currentTimeMillis();
    }

    public void key(int key, int modifiers) {
        lastEdit = System.currentTimeMillis();
        if (anchor == cursor) anchor = -1;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) deleteSelection();
                else if (cursor > 0) {
                    int from = text.offsetByCodePoints(cursor, -1);
                    text.delete(from, cursor);
                    cursor = from;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) deleteSelection();
                else if (cursor < text.length()) text.delete(cursor, text.offsetByCodePoints(cursor, 1));
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (!shift && hasSelection()) move(false, Math.min(anchor, cursor));
                else move(shift, ctrl ? wordLeft() : cursor > 0 ? text.offsetByCodePoints(cursor, -1) : 0);
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (!shift && hasSelection()) move(false, Math.max(anchor, cursor));
                else move(shift, ctrl ? wordRight() : cursor < text.length() ? text.offsetByCodePoints(cursor, 1) : text.length());
            }
            case GLFW.GLFW_KEY_HOME -> move(shift, 0);
            case GLFW.GLFW_KEY_END -> move(shift, text.length());
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) selectAll();
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl && hasSelection()) Clipboard.copy(selected());
            }
            case GLFW.GLFW_KEY_X -> {
                if (ctrl && hasSelection()) {
                    Clipboard.copy(selected());
                    deleteSelection();
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) insert(Clipboard.paste().replaceAll("[\\r\\n\\t]+", " "));
            }
            default -> {
            }
        }
    }

    public void click(Painter painter, double relativeX) {
        cursor = indexAt(painter, relativeX);
        anchor = -1;
        lastEdit = System.currentTimeMillis();
    }

    public void drag(Painter painter, double relativeX) {
        if (anchor < 0) anchor = cursor;
        if (relativeX < 0.0 && view > 0) view = text.offsetByCodePoints(view, -1);

        int target = indexAt(painter, relativeX);
        cursor = target > shown ? Math.min(text.length(), text.offsetByCodePoints(shown, 1)) : target;
        lastEdit = System.currentTimeMillis();
    }

    public void word(Painter painter, double relativeX) {
        int at = indexAt(painter, relativeX);
        int start = at;
        while (start > 0 && text.charAt(start - 1) != ' ') start--;
        int end = at;
        while (end < text.length() && text.charAt(end) != ' ') end++;

        anchor = start;
        cursor = end;
        lastEdit = System.currentTimeMillis();
    }

    public void selectAll() {
        anchor = 0;
        cursor = text.length();
    }

    private String selected() {
        return text.substring(Math.min(anchor, cursor), Math.max(anchor, cursor));
    }

    private void deleteSelection() {
        if (!hasSelection()) return;

        int from = Math.min(anchor, cursor);
        text.delete(from, Math.max(anchor, cursor));
        cursor = from;
        anchor = -1;
    }

    private void move(boolean extend, int target) {
        if (extend) {
            if (anchor < 0) anchor = cursor;
        } else {
            anchor = -1;
        }
        cursor = target;
    }

    private int wordLeft() {
        int i = cursor;
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        while (i > 0 && text.charAt(i - 1) != ' ') i--;
        return i;
    }

    private int wordRight() {
        int i = cursor;
        while (i < text.length() && text.charAt(i) != ' ') i++;
        while (i < text.length() && text.charAt(i) == ' ') i++;
        return i;
    }

    private int indexAt(Painter painter, double relativeX) {
        if (relativeX <= 0.0) return view;

        int best = view;
        double bestGap = relativeX;
        int i = view;
        while (i < text.length()) {
            i = text.offsetByCodePoints(i, 1);
            double gap = Math.abs(painter.advance(text.substring(view, i)) - relativeX);
            if (gap >= bestGap) break;
            best = i;
            bestGap = gap;
        }
        return best;
    }

    public void render(Painter painter, double x, double y, double width, boolean focused,
                       Color textColor, Color hintColor, Color caretColor, Color selectionColor, String hint) {
        double lineHeight = painter.lineHeight();

        if (text.isEmpty() && !focused) {
            painter.text(hint, x, y, hintColor);
            return;
        }

        if (view > text.length()) view = text.length();
        if (cursor < view) view = cursor;
        while (view < cursor && painter.width(text.substring(view, cursor)) > width - 2.0) {
            view = text.offsetByCodePoints(view, 1);
        }

        int end = text.length();
        while (end > view && painter.width(text.substring(view, end)) > width) {
            end = text.offsetByCodePoints(end, -1);
        }

        shown = end;

        if (hasSelection()) {
            int from = Math.max(Math.min(anchor, cursor), view);
            int to = Math.min(Math.max(anchor, cursor), end);
            if (to > from) {
                double start = painter.advance(text.substring(view, from));
                painter.rect(x + start, y, painter.advance(text.substring(view, to)) - start, lineHeight, selectionColor);
            }
        }

        painter.text(text.substring(view, end), x, y, textColor);

        boolean blink = (System.currentTimeMillis() - lastEdit) % 1000L < 550L;
        if (focused && blink) {
            double cx = x + painter.advance(text.substring(view, Math.min(cursor, end)));
            painter.rect(cx, y, 1.0, lineHeight, caretColor);
        }
    }

    private static int safeLength(CharSequence value, int length) {
        if (length > 0 && length < value.length() && Character.isHighSurrogate(value.charAt(length - 1))) return length - 1;
        return length;
    }
}
