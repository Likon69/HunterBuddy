package com.hunterbuddy.modules.messenger;

import java.util.ArrayList;
import java.util.List;

public final class Conversation {
    private final String peer;
    private final List<PrivateMessage> messages = new ArrayList<>();
    private int unread;
    private long lastActivity;
    private int revision;

    public Conversation(String peer) {
        this.peer = peer;
    }

    public String peer() {
        return peer;
    }

    public List<PrivateMessage> messages() {
        return messages;
    }

    public int unread() {
        return unread;
    }

    public long lastActivity() {
        return lastActivity;
    }

    public int revision() {
        return revision;
    }

    public void add(PrivateMessage message, int limit) {
        messages.add(message);
        while (messages.size() > limit) messages.remove(0);
        lastActivity = message.epochMillis();
        revision++;
    }

    public boolean trim(int limit) {
        if (messages.size() <= limit) return false;

        messages.subList(0, messages.size() - limit).clear();
        revision++;
        return true;
    }

    public void addUnread() {
        unread++;
    }

    public void restore(int unread, long lastActivity) {
        this.unread = unread;
        this.lastActivity = lastActivity;
    }

    public void markRead() {
        unread = 0;
    }

    public void clear() {
        messages.clear();
        unread = 0;
        revision++;
    }
}
