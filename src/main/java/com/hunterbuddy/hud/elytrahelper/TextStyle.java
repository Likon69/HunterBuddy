package com.hunterbuddy.hud.elytrahelper;

import meteordevelopment.meteorclient.utils.render.color.Color;

public record TextStyle(boolean shadow, double scale, Color labelColor, Color valueColor) {
    public static TextStyle from(HelperSettings settings) {
        return new TextStyle(
            settings.textShadow.get(),
            settings.textScale.get(),
            settings.colors.label.get(),
            settings.colors.value.get());
    }
}
