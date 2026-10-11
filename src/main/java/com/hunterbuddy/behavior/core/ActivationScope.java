package com.hunterbuddy.behavior.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class ActivationScope {
    private final Deque<Runnable> cleanup = new ArrayDeque<>();
    private boolean closed;

    public void defer(Runnable action) {
        if (closed) throw new IllegalStateException("activation already closed");
        cleanup.push(Objects.requireNonNull(action));
    }

    public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        while (!cleanup.isEmpty()) {
            try {
                cleanup.pop().run();
            } catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else if (failure != ex) failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }
}
