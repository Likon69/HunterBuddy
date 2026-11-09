package com.hunterbuddy.hud.text;

import java.util.Locale;

public final class HudFontProfile {
    private static long loads;
    private static long loadNanos;
    private static long frames;
    private static long previousLoads;
    private static long lastLoads;
    private static long maxLoads;

    private HudFontProfile() { }

    public static void loaded(long nanos) { loads++; loadNanos += nanos; }

    public static void frame() {
        lastLoads = loads - previousLoads;
        maxLoads = Math.max(maxLoads, lastLoads);
        previousLoads = loads;
        frames++;
    }

    public static String report() {
        return String.format(Locale.ROOT,
            "HUD font atlas profile (since startup, includes warmup)\nHUD passes: %d; atlas loads: %d; last pass loads: %d; max pass loads: %d; atlas CPU: %.3f ms\n",
            frames, loads, lastLoads, maxLoads, loadNanos / 1_000_000.0);
    }
}
