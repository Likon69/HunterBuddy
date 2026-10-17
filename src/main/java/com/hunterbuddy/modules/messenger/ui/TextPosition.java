package com.hunterbuddy.modules.messenger.ui;

record TextPosition(int message, int offset) implements Comparable<TextPosition> {
    @Override
    public int compareTo(TextPosition other) {
        int byMessage = Integer.compare(message, other.message);
        return byMessage != 0 ? byMessage : Integer.compare(offset, other.offset);
    }
}
