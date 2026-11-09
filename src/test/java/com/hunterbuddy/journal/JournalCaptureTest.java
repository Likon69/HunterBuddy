package com.hunterbuddy.journal;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class JournalCaptureTest {
    private JournalStore store;

    @BeforeEach
    void start() {
        store = new JournalStore(16);
        JournalCapture.start(store, Map.of("Flight", JournalSource.HUNTERBUDDY));
        JournalContext.current().origin = JournalSource.OTHER;
    }

    @AfterEach
    void stop() {
        JournalCapture.stop();
        JournalContext.current().serverDepth = 0;
        JournalContext.current().origin = null;
    }

    @Test
    void localNotificationsAreCapturedAndVanillaChatMirrorsAreNotDuplicated() {
        JournalCapture.chat(Text.literal("[Meteor] [Flight] Started"), false);
        JournalCapture.chat(Text.literal("[Baritone] Path found"), false);
        new JournalAppender().append(Log4jLogEvent.newBuilder().setLoggerName("net.minecraft.client.gui.hud.ChatHud")
            .setThreadName("Render thread").setLevel(Level.INFO).setMessage(new SimpleMessage("[CHAT] [Baritone] Path found")).build());
        JournalEntry[] entries = store.snapshot().entries();
        assertEquals(2, entries.length);
        assertEquals(JournalSource.HUNTERBUDDY, entries[0].source());
        assertEquals(JournalSource.BARITONE, entries[1].source());
    }

    @Test
    void allServerMessagesAreRejectedEvenWhenTheySpoofModPrefixes() {
        JournalContext context = JournalContext.current();
        context.serverDepth++;
        try {
            for (String message : new String[]{"<Steve> hello", "Steve joined the game", "Steve left the game", "Steve died",
                "[Meteor] Fake notice", "[HunterBuddy] x=123", "Server announcement"}) JournalCapture.chat(Text.literal(message), false);
        } finally {
            context.serverDepth--;
        }
        JournalCapture.chat(Text.literal("<Alex> another message"), false);
        JournalCapture.chat(Text.translatable("multiplayer.player.joined", "Alex"), false);
        JournalCapture.chat(Text.translatable("death.attack.fall", "Alex"), false);
        JournalCapture.chat(Text.literal("[Baritone] Signed player message"), true);
        assertEquals(0, store.snapshot().entries().length);
    }

    @Test
    void aRecognizablePrefixAloneDoesNotAuthorizeACoreGameMessage() {
        JournalContext.current().origin = null;
        JournalCapture.chat(Text.literal("[Meteor] Not emitted by a mod"), false);
        assertEquals(0, store.snapshot().entries().length);
        assertEquals(JournalSource.OTHER, JournalSource.identify("some.mod.MeteorIntegration"));
    }

    @Test
    void remoteScopeIsCarriedAcrossDelayedExecution() {
        JournalContext context = JournalContext.current();
        context.serverDepth++;
        Runnable queued;
        try {
            queued = context.carry(JournalSource.METEOR, () -> JournalCapture.chat(Text.literal("[Meteor] Server relay"), false));
        } finally {
            context.serverDepth--;
        }
        queued.run();
        assertEquals(0, context.serverDepth);
        assertEquals(JournalSource.OTHER, context.origin);
        assertEquals(0, store.snapshot().entries().length);
    }

    @Test
    void modOriginSurvivesAnAsyncMeteorCallbackAndRestoresOnFailure() throws Exception {
        Runnable queued = JournalContext.current().carry(JournalSource.HUNTERBUDDY,
            () -> JournalCapture.chat(Text.literal("Background work finished"), false));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> { try { queued.run(); } catch (Throwable error) { failure.set(error); } });
        thread.start();
        thread.join();
        assertNull(failure.get());
        assertEquals(JournalSource.HUNTERBUDDY, store.snapshot().entries()[0].source());
        JournalContext.current().origin = null;
        assertThrows(IllegalStateException.class, () -> JournalContext.current().carry(JournalSource.BARITONE,
            () -> { throw new IllegalStateException(); }).run());
        assertNull(JournalContext.current().origin);
    }

    @Test
    void consoleSeverityAndSourcesArePreservedAndStoppedCaptureDoesNothing() {
        JournalAppender sink = new JournalAppender();
        sink.append(Log4jLogEvent.newBuilder().setLoggerName("com.hunterbuddy.Worker").setThreadName("Worker")
            .setLevel(Level.ERROR).setMessage(new SimpleMessage("Failure")).build());
        sink.append(Log4jLogEvent.newBuilder().setLoggerName("some.mod.Client").setThreadName("Render thread")
            .setLevel(Level.WARN).setMessage(new SimpleMessage("Warning")).build());
        sink.append(Log4jLogEvent.newBuilder().setLoggerName("some.mod.Server").setThreadName("Server thread")
            .setLevel(Level.INFO).setMessage(new SimpleMessage("Server output")).build());
        assertEquals(2, store.snapshot().entries().length);
        assertEquals(JournalEntry.Level.ERROR, store.snapshot().entries()[0].level());
        assertEquals(JournalEntry.Level.WARN, store.snapshot().entries()[1].level());
        JournalCapture.stop();
        JournalCapture.chat(Text.literal("Not captured"), false);
        JournalCapture.record(0, JournalSource.METEOR, JournalEntry.Level.INFO, "Not captured");
        assertEquals(2, store.snapshot().entries().length);
    }

    @Test
    void anotherModsConsoleChatMirrorDoesNotReintroducePlayerChat() {
        JournalAppender sink = new JournalAppender();
        for (String message : new String[]{"[CHAT] Steve joined the game", "[ChatLog] <Steve> hello", "[CHAT] Server announcement"}) {
            sink.append(Log4jLogEvent.newBuilder().setLoggerName("some.mod.ChatLogger").setThreadName("Render thread")
                .setLevel(Level.INFO).setMessage(new SimpleMessage(message)).build());
        }
        assertEquals(0, store.snapshot().entries().length);
    }

    @Test
    void nestedMeteorFormattingPreservesWarningAndErrorLevels() {
        JournalCapture.chat(Text.empty().append(Text.literal("[Meteor] "))
            .append(Text.empty().append(Text.literal("Warning").formatted(Formatting.YELLOW))), false);
        JournalCapture.chat(Text.empty().append(Text.literal("[Meteor] "))
            .append(Text.empty().append(Text.literal("Failure").formatted(Formatting.RED))), false);
        assertEquals(JournalEntry.Level.WARN, store.snapshot().entries()[0].level());
        assertEquals(JournalEntry.Level.ERROR, store.snapshot().entries()[1].level());
    }
}
