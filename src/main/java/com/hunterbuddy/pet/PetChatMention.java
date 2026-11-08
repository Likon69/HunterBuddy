package com.hunterbuddy.pet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PetChatMention {
    private static final Pattern PUBLIC = Pattern.compile("^(?:\\[[0-9:]+\\]\\s*)?<([A-Za-z0-9_]{1,16})>\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern NAME = Pattern.compile("(?<![A-Za-z0-9_])bender(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMATTING = Pattern.compile("\\u00a7[0-9a-fk-or]");

    private PetChatMention() {
    }

    public static boolean mentions(String line, String self) {
        if (line == null) return false;
        Matcher chat = PUBLIC.matcher(FORMATTING.matcher(line).replaceAll("").strip());
        if (!chat.matches() || chat.group(1).equalsIgnoreCase(self)) return false;
        return NAME.matcher(chat.group(2)).find();
    }
}
