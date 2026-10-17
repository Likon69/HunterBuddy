package com.hunterbuddy.modules.messenger.ui;

import java.util.HashMap;
import java.util.Map;
import meteordevelopment.meteorclient.utils.render.color.Color;

final class PeerColor {
    private static final Map<String, Color> CACHE = new HashMap<>();

    private PeerColor() {
    }

    static Color of(String name) {
        return CACHE.computeIfAbsent(name, key -> {
            double hue = (key.toLowerCase().hashCode() & 0xffff) / 65536.0 * 360.0;
            return Color.fromHsv(hue, 0.55, 0.95);
        });
    }
}
