package com.hunterbuddy.hud.cards;

import meteordevelopment.meteorclient.utils.render.color.Color;

public record CardStyle(double scale, double textScale, boolean compact, boolean blur, boolean reflection,
                        Color background, Color text) {
}
