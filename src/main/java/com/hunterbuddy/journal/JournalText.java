package com.hunterbuddy.journal;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public final class JournalText {
    private static final String NUMBER = "(?:[~^](?:[-+]?(?:\\d[\\d_]*(?:\\.\\d*)?|\\.\\d+))?|[-+]?(?:\\d[\\d_]*(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?)";
    private static final String SEPARATOR = "(?:\\s*[,;/]\\s*|\\s+)";
    private static final Pattern TRIPLE = Pattern.compile("(?<![\\w.])" + NUMBER + SEPARATOR + NUMBER + "(?:" + SEPARATOR + NUMBER + ")++(?![\\w.])");
    private static final Pattern AXIS = Pattern.compile("(?i)(?<![\\w])([\"']?(?:pos[xyz]|[xyz])[\"']?(?:\\s*[:=]\\s*|\\s+))" + NUMBER + "(?:" + SEPARATOR + NUMBER + ")*+");
    private static final Pattern PAIR = Pattern.compile("(?i)(\\b(?:xz|x\\s*/\\s*z|chunk)\\s*[:=(]?\\s*)" + NUMBER + "(?:" + SEPARATOR + NUMBER + ")++");
    private static final Pattern FORMATTING = Pattern.compile("\\x1B\\[[0-9;?]*[ -/]*[@-~]|\u00a7[0-9A-FK-ORa-fk-or]");
    private static final Pattern LINES = Pattern.compile("[\\p{Cntrl}\\p{Z}]+");
    private static final Pattern PREFIX = Pattern.compile("^\\s*\\[(?:Meteor|Meteor Client|HunterBuddy|Baritone)]\\s*", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private JournalText() {
    }

    public static String clean(String value) {
        return PREFIX.matcher(LINES.matcher(FORMATTING.matcher(value).replaceAll("")).replaceAll(" ").strip()).replaceFirst("");
    }

    public static String redact(String value) {
        value = TRIPLE.matcher(value).replaceAll("[coords]");
        value = AXIS.matcher(value).replaceAll("$1[hidden]");
        return PAIR.matcher(value).replaceAll("$1[coords]");
    }

    public static String message(JournalEntry entry, boolean hidden) {
        String text = clean(entry.message());
        return hidden ? redact(text) : text;
    }

    public static String prefix(JournalEntry entry) {
        return TIME.format(Instant.ofEpochMilli(entry.time())) + " [" + entry.source().label + "]";
    }

    public static String format(JournalEntry entry, boolean hidden) {
        return prefix(entry) + " " + message(entry, hidden);
    }
}
