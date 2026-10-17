package com.hunterbuddy.modules.messenger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public final class OutgoingQueue {
    public record Pending(String peer, String text) {
    }

    private static final int CAPACITY = 16;
    private static final long ECHO_WINDOW_MS = 6000L;

    private record Awaiting(String peer, String text, long sentAt) {
    }

    private final Deque<Pending> queue = new ArrayDeque<>();
    private final List<Awaiting> awaiting = new ArrayList<>();
    private long lastSent;

    public boolean offer(String peer, String text) {
        if (queue.size() >= CAPACITY) return false;
        queue.add(new Pending(peer, text));
        return true;
    }

    public Optional<Pending> poll(long now, long intervalMs) {
        if (queue.isEmpty() || now - lastSent < intervalMs) return Optional.empty();

        lastSent = now;
        Pending next = queue.poll();
        awaiting.add(new Awaiting(next.peer(), next.text(), now));
        return Optional.of(next);
    }

    public boolean consumeEcho(String peer, String text, long now) {
        awaiting.removeIf(entry -> now - entry.sentAt() > ECHO_WINDOW_MS);

        Iterator<Awaiting> it = awaiting.iterator();
        while (it.hasNext()) {
            Awaiting entry = it.next();
            if (entry.peer().equalsIgnoreCase(peer) && entry.text().equals(text)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public void clear() {
        queue.clear();
        awaiting.clear();
    }
}
