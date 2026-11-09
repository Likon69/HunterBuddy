package com.hunterbuddy.journal;

import java.util.Locale;

public enum JournalSource {
    METEOR("Meteor"), BARITONE("Baritone"), HUNTERBUDDY("HunterBuddy"), OTHER("Other mods");

    public final String label;

    JournalSource(String label) {
        this.label = label;
    }

    public static JournalSource identify(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("hunterbuddy") || lower.startsWith("com.hunterbuddy.")) return HUNTERBUDDY;
        if (lower.equals("baritone") || lower.startsWith("baritone.")) return BARITONE;
        if (lower.equals("meteor") || lower.equals("meteor client") || lower.equals("meteor-client")
            || lower.startsWith("meteordevelopment.meteorclient.")) return METEOR;
        return OTHER;
    }

    public static boolean modLogger(String name, String thread) {
        if (name == null || name.isBlank()) return false;
        if (thread != null && (thread.startsWith("Server thread") || thread.startsWith("Netty"))) return false;
        return !name.startsWith("net.minecraft.") && !name.startsWith("com.mojang.")
            && !name.startsWith("org.apache.") && !name.startsWith("org.slf4j.")
            && !name.startsWith("org.lwjgl.") && !name.startsWith("io.netty.")
            && !name.startsWith("net.fabricmc.") && !name.startsWith("FabricLoader")
            && !name.equals("Minecraft") && !name.equals("STDOUT") && !name.equals("STDERR");
    }
}
