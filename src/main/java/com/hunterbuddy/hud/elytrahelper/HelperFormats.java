package com.hunterbuddy.hud.elytrahelper;

public final class HelperFormats {
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final double BLOCKS_PER_KILOBLOCK = 1000.0;
    private static final double KMH_PER_BPS = 3.6;

    private HelperFormats() {
    }

    public static String time(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / SECONDS_PER_HOUR;
        long minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        long remainder = seconds % SECONDS_PER_MINUTE;
        if (hours > 0) return String.format("%dh %02dm %02ds", hours, minutes, remainder);
        if (minutes > 0) return String.format("%dm %02ds", minutes, remainder);
        return String.format("%ds", remainder);
    }

    public static String distance(double blocks) {
        if (blocks >= BLOCKS_PER_KILOBLOCK) return String.format("%.1fk", blocks / BLOCKS_PER_KILOBLOCK);
        return String.format("%.0f", blocks);
    }

    public static String speed(double blocksPerSecond, boolean withKmh) {
        String text = String.format("%.1f b/s", blocksPerSecond);
        if (!withKmh) return text;
        return text + String.format(" (%.0f km/h)", blocksPerSecond * KMH_PER_BPS);
    }
}
