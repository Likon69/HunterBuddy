package com.hunterbuddy.hud;

import com.hunterbuddy.hud.text.HudText;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;

final class CoordsGlyphs {
    private static final String DIGITS = "0123456789";
    private double digit;
    private double minus;
    private double dot;
    private double separator;

    void measure(HudRenderer renderer, double textScale) {
        digit = 0;
        for (int i = 0; i < DIGITS.length(); i++) {
            digit = Math.max(digit, HudText.textWidth(renderer, DIGITS.substring(i, i + 1), false, textScale));
        }
        minus = HudText.textWidth(renderer, "-", false, textScale);
        dot = HudText.textWidth(renderer, ".", false, textScale);
        separator = HudText.textWidth(renderer, ", ", false, textScale);
    }

    double width(CoordsHud.Row row) {
        double total = 0;
        int axes = 0;
        for (String part : row.part) {
            if (part == null) continue;
            if (axes++ > 0) total += separator;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                total += c == '-' ? minus : c == '.' ? dot : digit;
            }
        }
        return total;
    }
}
