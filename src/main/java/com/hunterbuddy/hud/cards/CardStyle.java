package com.hunterbuddy.hud.cards;

import com.hunterbuddy.hud.glass.GlassStyle;
import meteordevelopment.meteorclient.utils.render.color.Color;

public record CardStyle(double scale, double textScale, boolean compact, boolean blur, boolean reflection,
                        GlassStyle glassStyle, Color background, Color text) {
}
