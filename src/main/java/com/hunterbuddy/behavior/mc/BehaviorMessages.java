package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.StopReport;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.MeteorClient;

public final class BehaviorMessages {
    private final Consumer<String> chat;
    private final Consumer<String> log;
    private final Deque<String> pending = new ArrayDeque<>();

    public BehaviorMessages(Consumer<String> chat, Consumer<String> log) {
        this.chat = Objects.requireNonNull(chat);
        this.log = Objects.requireNonNull(log);
    }

    public void stopped(StopReport report) {
        String message = "Stopped [" + report.reason() + "]: " + report.detail();
        log.accept("TreeJava: " + message);
        pending.addLast(message);
        flush();
    }

    public void flush() {
        if (MeteorClient.mc.world == null) return;
        while (!pending.isEmpty()) chat.accept(pending.removeFirst());
    }
}
