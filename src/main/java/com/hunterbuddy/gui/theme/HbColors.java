package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.utils.render.color.Color;

public final class HbColors {
    private final HbGuiTheme theme;

    HbColors(HbGuiTheme theme) {
        this.theme = theme;
    }

    public Color window() { return theme.backgroundColor.get(); }
    public Color row() { return theme.moduleBackground.get(); }
    public Color hover() { return theme.backgroundColor.get(false, true, true); }
    public Color text() { return theme.textColor.get(); }
    public Color dim() { return theme.textSecondaryColor.get(); }
    public Color border() { return theme.outlineColor.get(); }
    public Color input() { return theme.inputColor.get(); }
    public Color risk() { return theme.riskColor.get(); }
    public Color onAccent() { return theme.onAccentColor.get(); }

    public Color control(boolean pressed, boolean hover) {
        return pressed || hover ? theme.backgroundColor.get(pressed, hover, true) : input();
    }

    public Color edge(boolean pressed, boolean hover) {
        return theme.outlineColor.get(pressed, hover, true);
    }
}
