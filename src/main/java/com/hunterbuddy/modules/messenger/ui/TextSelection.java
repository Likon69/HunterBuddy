package com.hunterbuddy.modules.messenger.ui;

import com.hunterbuddy.modules.messenger.PrivateMessage;
import java.util.List;

final class TextSelection {
    private static final String SEPARATORS = " ,.;:!?()[]\"";

    private TextPosition anchor;
    private TextPosition head;

    void begin(TextPosition position) {
        anchor = position;
        head = position;
    }

    void extend(TextPosition position) {
        if (anchor == null) anchor = position;
        head = position;
    }

    void clear() {
        anchor = null;
        head = null;
    }

    boolean isEmpty() {
        return anchor == null || head == null || anchor.equals(head);
    }

    TextPosition from() {
        return anchor.compareTo(head) <= 0 ? anchor : head;
    }

    TextPosition to() {
        return anchor.compareTo(head) <= 0 ? head : anchor;
    }

    boolean covers(TextPosition position) {
        return !isEmpty() && from().compareTo(position) <= 0 && position.compareTo(to()) <= 0;
    }

    void selectMessage(int message, int length) {
        anchor = new TextPosition(message, 0);
        head = new TextPosition(message, length);
    }

    void selectWord(String text, int message, int offset) {
        int at = Math.min(offset, text.length() - 1);
        if (at < 0 || SEPARATORS.indexOf(text.charAt(at)) >= 0) {
            begin(new TextPosition(message, offset));
            return;
        }

        int start = at;
        while (start > 0 && SEPARATORS.indexOf(text.charAt(start - 1)) < 0) start--;
        int end = at;
        while (end < text.length() && SEPARATORS.indexOf(text.charAt(end)) < 0) end++;

        anchor = new TextPosition(message, start);
        head = new TextPosition(message, end);
    }

    void selectAll(List<PrivateMessage> messages) {
        if (messages.isEmpty()) {
            clear();
            return;
        }
        int last = messages.size() - 1;
        anchor = new TextPosition(0, 0);
        head = new TextPosition(last, messages.get(last).text().length());
    }

    int[] range(int message, int lineStart, int lineEnd) {
        if (isEmpty()) return null;

        TextPosition from = from();
        TextPosition to = to();
        if (message < from.message() || message > to.message()) return null;

        int a = message == from.message() ? Math.max(from.offset(), lineStart) : lineStart;
        int b = message == to.message() ? Math.min(to.offset(), lineEnd) : lineEnd;
        if (b <= a) return null;
        return new int[] {a - lineStart, b - lineStart};
    }

    String text(List<PrivateMessage> messages, boolean withNames) {
        if (isEmpty() || messages.isEmpty()) return "";

        TextPosition from = from();
        TextPosition to = to();
        int first = Math.max(0, from.message());
        int last = Math.min(messages.size() - 1, to.message());
        boolean prefix = withNames && last > first;

        StringBuilder out = new StringBuilder();
        for (int i = first; i <= last; i++) {
            String full = messages.get(i).text();
            int start = i == from.message() ? Math.min(from.offset(), full.length()) : 0;
            int end = i == to.message() ? Math.min(to.offset(), full.length()) : full.length();
            if (end < start) continue;

            if (!out.isEmpty()) out.append('\n');
            if (prefix) out.append(messages.get(i).incoming() ? messages.get(i).peer() : "You").append(": ");
            out.append(full, start, end);
        }
        return out.toString();
    }
}
