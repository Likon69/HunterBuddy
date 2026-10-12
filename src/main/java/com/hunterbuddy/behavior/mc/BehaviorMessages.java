package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.StopReport;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class BehaviorMessages {
    private final Consumer<String> chat;
    private final Consumer<String> log;
    private final BooleanSupplier chatAvailable;
    private final Deque<String> pending = new ArrayDeque<>();

    public BehaviorMessages(Consumer<String> chat, Consumer<String> log, BooleanSupplier chatAvailable) {
        this.chat = Objects.requireNonNull(chat);
        this.log = Objects.requireNonNull(log);
        this.chatAvailable = Objects.requireNonNull(chatAvailable);
    }

    public void stopped(StopReport report) {
        alert("Stopped [" + report.reason() + "]: " + report.detail());
    }

    public void alert(String message) {
        Objects.requireNonNull(message);
        pending.addLast(message);
        try {
            log.accept("TreeJava: " + message);
        } finally {
            flush();
        }
    }

    public void flush() {
        if (!chatAvailable.getAsBoolean()) return;
        while (!pending.isEmpty()) {
            chat.accept(pending.getFirst());
            pending.removeFirst();
        }
    }
}
