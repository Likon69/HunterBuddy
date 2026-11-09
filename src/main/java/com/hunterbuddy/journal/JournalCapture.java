package com.hunterbuddy.journal;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

public final class JournalCapture {
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Pattern PLAYER = Pattern.compile("^\\s*(?:\\[[^]]*]\\s*)*<[^>]+>\\s");
    private static volatile Map<String, JournalSource> moduleSources = Map.of();
    private static volatile JournalStore store;
    private static JournalAppender appender;

    private JournalCapture() {
    }

    public static synchronized void start(JournalStore target) {
        Map<String, JournalSource> known = new HashMap<>();
        for (Module module : Modules.get().getAll()) known.put(module.title, JournalSource.identify(module.getClass().getName()));
        start(target, known);
    }

    static synchronized void start(JournalStore target, Map<String, JournalSource> known) {
        stop();
        moduleSources = Map.copyOf(known);
        store = target;
        appender = new JournalAppender();
        appender.start();
        ((Logger) LogManager.getRootLogger()).addAppender(appender);
    }

    public static synchronized void stop() {
        store = null;
        if (appender == null) return;
        ((Logger) LogManager.getRootLogger()).removeAppender(appender);
        appender.stop();
        appender = null;
    }

    public static boolean enabled() {
        return store != null;
    }

    public static void record(long time, JournalSource source, JournalEntry.Level level, String message) {
        JournalStore target = store;
        if (target != null && JournalContext.current().serverDepth == 0) target.add(time, source, level, message);
    }

    public static void chat(Text text, boolean signed) {
        if (!enabled() || signed || JournalContext.current().serverDepth > 0 || serverText(text)) return;
        String message = text.getString();
        if (chatEcho(message)) return;
        JournalSource origin = JournalContext.current().origin;
        if (origin == null) origin = caller();
        if (origin == null) return;
        JournalSource source = identify(message, origin);
        Text body = text.getSiblings().isEmpty() ? text : text.getSiblings().getLast();
        JournalEntry.Level level = level(body);
        record(System.currentTimeMillis(), source, level, message);
    }

    private static JournalEntry.Level level(Text text) {
        JournalEntry.Level result = JournalEntry.Level.INFO;
        var color = text.getStyle().getColor();
        if (color != null) {
            if (color.getRgb() == 0xff5555 || color.getRgb() == 0xaa0000) return JournalEntry.Level.ERROR;
            if (color.getRgb() == 0xffff55 || color.getRgb() == 0xffaa00) result = JournalEntry.Level.WARN;
        }
        for (Text child : text.getSiblings()) {
            JournalEntry.Level nested = level(child);
            if (nested == JournalEntry.Level.ERROR) return nested;
            if (nested == JournalEntry.Level.WARN) result = nested;
        }
        return result;
    }

    static boolean chatEcho(String message) {
        return message.contains("[CHAT]") || PLAYER.matcher(JournalText.clean(message)).find();
    }

    public static JournalSource caller() {
        return WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getClassName)
            .filter(name -> !name.startsWith("com.hunterbuddy.journal.") && !name.startsWith("net.minecraft.")
                && !name.startsWith("java.") && !name.startsWith("jdk.") && !name.startsWith("com.llamalad7.")
                && !name.startsWith("com.mojang.") && !name.startsWith("net.fabricmc.")
                && !name.startsWith("org.spongepowered.") && !name.startsWith("org.lwjgl.")
                && !name.startsWith("org.junit.") && !name.startsWith("org.gradle.") && !name.startsWith("worker.org.gradle.")
                && !name.equals("meteordevelopment.meteorclient.utils.player.ChatUtils")
                && !name.equals("meteordevelopment.meteorclient.systems.modules.Module"))
            .findFirst().map(JournalSource::identify).orElse(null));
    }

    private static JournalSource identify(String message, JournalSource carried) {
        int from = 0;
        for (int i = 0; i < 3; i++) {
            int left = message.indexOf('[', from);
            if (left < 0 || left > 80) break;
            int right = message.indexOf(']', left);
            if (right < 0 || right - left > 48) break;
            String prefix = message.substring(left + 1, right);
            JournalSource module = moduleSources.get(prefix);
            if (module != null) return module;
            JournalSource known = JournalSource.identify(prefix);
            if (known != JournalSource.OTHER && known != JournalSource.METEOR) return known;
            from = right + 1;
        }
        if (carried != null && carried != JournalSource.OTHER) return carried;
        return JournalSource.identify(message.substring(0, Math.min(80, message.length())));
    }

    private static boolean serverText(Text text) {
        if (text.getContent() instanceof TranslatableTextContent content) {
            String key = content.getKey();
            if (key.startsWith("death.") || key.startsWith("multiplayer.player.") || key.startsWith("chat.type.")) return true;
        }
        for (Text child : text.getSiblings()) if (serverText(child)) return true;
        return false;
    }
}
