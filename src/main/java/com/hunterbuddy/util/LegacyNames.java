package com.hunterbuddy.util;

import java.util.Locale;
import java.util.Map;

public final class LegacyNames {
    private static final Map<String, String> MODULES = Map.of(
        "disconnect-sound", "kick-alarm",
        "yaw-lock", "heading-lock",
        "shulker-overview", "shulker-peek",
        "replenish", "hotbar-refill",
        "elytraswap", "elytra-keeper",
        "sign-render", "sign-reader",
        "pearlloader", "pearl-butler"
    );

    private static final Map<String, String> HUD = Map.of(
        "timerspeed", "TimerGauge"
    );

    private LegacyNames() {
    }

    public static String module(String oldName) {
        return oldName == null ? null : MODULES.get(oldName.toLowerCase(Locale.ROOT));
    }

    public static String hud(String oldName) {
        return oldName == null ? null : HUD.get(oldName.toLowerCase(Locale.ROOT));
    }
}
