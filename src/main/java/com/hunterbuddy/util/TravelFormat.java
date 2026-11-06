package com.hunterbuddy.util;

import java.util.Locale;

public final class TravelFormat {
    private TravelFormat() {
    }

    public static int quantiseEta(int seconds) {
        if (seconds < 0) return seconds;
        if (seconds >= 3600) return seconds / 60 * 60;
        if (seconds >= 60) return seconds / 10 * 10;
        return seconds / 5 * 5;
    }

    public static String formatDistance(double blocks) {
        return blocks >= 1000.0
            ? String.format(Locale.ROOT, "%.1f km", blocks / 1000.0)
            : String.format(Locale.ROOT, "%.0f m", blocks);
    }

    public static String formatTime(int seconds) {
        if (seconds >= 3600) return String.format(Locale.ROOT, "%dh %02dm", seconds / 3600, (seconds % 3600) / 60);
        if (seconds >= 60) return String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60);
        return seconds + "s";
    }
}
