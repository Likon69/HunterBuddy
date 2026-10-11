package com.hunterbuddy.behavior.core;

import java.util.ArrayDeque;
import java.util.Deque;

public final class RestartBudget {
    private final Deque<Long> restarts = new ArrayDeque<>();

    public boolean tryRestart(long nowMs) {
        while (!restarts.isEmpty() && nowMs - restarts.peekFirst() >= 3_600_000) restarts.removeFirst();
        if (restarts.size() >= 3) return false;
        restarts.addLast(nowMs);
        return true;
    }

    public int count() {
        return restarts.size();
    }
}
