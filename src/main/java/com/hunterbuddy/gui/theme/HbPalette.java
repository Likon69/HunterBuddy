package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public record HbPalette(Color window, Color row, Color hover, Color accent, Color text, Color dim, Color border,
                        Color input, Color risk, Color onAccent) {
    public static Color rgb(int hex, double alpha) {
        return new Color((hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF, (int) Math.round(alpha * 255));
    }

    public static Color rgb(int hex) {
        return rgb(hex, 1);
    }

    public void apply(HbGuiTheme theme) {
        paint(theme.accentColor, accent);
        paint(theme.checkboxColor, accent);
        paint(theme.favoriteColor, accent);
        paint(theme.plusColor, accent);
        paint(theme.minusColor, accent);

        paint(theme.textColor, text);
        paint(theme.titleTextColor, text);
        paint(theme.textSecondaryColor, dim);
        paint(theme.placeholderColor, HbDraw.withAlpha(dim, 0.6));
        paint(theme.textHighlightColor, HbDraw.withAlpha(accent, 0.4));

        paintThreeState(theme, "background", window, hover, border);
        paint(theme.moduleBackground, row);

        paintThreeState(theme, "outline", border, dim, accent);

        paint(theme.separatorText, text);
        paint(theme.separatorCenter, dim);
        paint(theme.separatorEdges, border);

        paintThreeState(theme, "Scrollbar", row, hover, border);

        paint(theme.sliderLeft, accent);
        paint(theme.sliderRight, border);
        paintThreeState(theme, "slider-handle", text, text, accent);

        paint(theme.inputColor, input);
        paint(theme.riskColor, risk);
        paint(theme.onAccentColor, onAccent);
    }

    private static void paint(Setting<SettingColor> setting, Color color) {
        setting.getDefaultValue().set(color);
        setting.reset();
    }

    private static void paintThreeState(HbGuiTheme theme, String name, Color normal, Color hovered, Color pressed) {
        paint(theme.settings.get(name + "-color", SettingColor.class), normal);
        paint(theme.settings.get("hovered-" + name + "-color", SettingColor.class), hovered);
        paint(theme.settings.get("pressed-" + name + "-color", SettingColor.class), pressed);
    }
}
