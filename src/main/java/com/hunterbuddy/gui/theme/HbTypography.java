package com.hunterbuddy.gui.theme;

import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class HbTypography implements TextRenderer {
    private final HbGuiTheme theme;
    private final HbTextBatches batches;
    private final HbFontLoader fonts = new HbFontLoader();
    private final Object2DoubleLinkedOpenHashMap<String> regularWidths = widths();
    private final Object2DoubleLinkedOpenHashMap<String> boldWidths = widths();
    private TextRenderer regular;
    private TextRenderer bold;
    private double regularHeight;
    private double boldHeight;
    private String requested;
    private double alpha = 1;
    private double nativeScale;
    private boolean building;
    private boolean fontStarted;

    public HbTypography(HbGuiTheme theme) {
        this.theme = theme;
        batches = new HbTextBatches(this, theme);
    }

    private static Object2DoubleLinkedOpenHashMap<String> widths() {
        Object2DoubleLinkedOpenHashMap<String> widths = new Object2DoubleLinkedOpenHashMap<>();
        widths.defaultReturnValue(-1);
        return widths;
    }

    TextRenderer font(boolean strong) {
        String familyName = theme.fontFamily.get();
        if (!familyName.equals(requested)) {
            fonts.reset();
            regular = null;
            bold = null;
            regularWidths.clear();
            boldWidths.clear();
            requested = familyName;
        }
        if (strong ? bold == null : regular == null) {
            TextRenderer renderer = fonts.load(familyName, strong);
            if (strong) {
                bold = renderer;
                boldHeight = renderer.getHeight();
            } else {
                regular = renderer;
                regularHeight = renderer.getHeight();
            }
        }
        return strong ? bold : regular;
    }

    double baseHeight(boolean strong) {
        font(strong);
        return strong ? boldHeight : regularHeight;
    }

    private double relativeWidth(String text, boolean strong) {
        TextRenderer font = font(strong);
        Object2DoubleLinkedOpenHashMap<String> cache = strong ? boldWidths : regularWidths;
        double measured = cache.getDouble(text);
        if (measured >= 0) return measured;
        measured = font.getWidth(text) / baseHeight(strong);
        if (cache.size() == 4096) cache.removeFirstDouble();
        cache.put(text, measured);
        return measured;
    }

    public double width(String text, double size, boolean strong) {
        return theme.scale(relativeWidth(text, strong) * theme.textSize(size));
    }

    public String fit(String text, double size, boolean strong, double available) {
        if (width(text, size, strong) <= available) return text;
        String ellipsis = "…";
        if (width(ellipsis, size, strong) > available) return "";
        int end = text.length();
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1);
            String shortened = text.substring(0, end) + ellipsis;
            if (width(shortened, size, strong) <= available) return shortened;
        }
        return ellipsis;
    }

    public void draw(GuiRenderer gui, String text, double x, double y, double size, Color color, boolean strong) {
        batches.add(gui, text, x, y, theme.textSize(size), color, strong, alpha);
    }

    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        if (building) throw new IllegalStateException("Theme text rendering is already active.");
        nativeScale = scale;
        building = true;
        fontStarted = false;
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (fontStarted) return font(false).getWidth(text, length, shadow);
        double width = length == text.length() && !shadow ? relativeWidth(text, false) * theme.textSize(11)
            : font(false).getWidth(text, length, shadow) * theme.textSize(11) / baseHeight(false);
        return building ? width * nativeScale : width;
    }

    @Override
    public double getHeight(boolean shadow) {
        return theme.textSize(11) * (building ? nativeScale : 1);
    }

    @Override
    public double render(String text, double x, double y, Color color, boolean shadow) {
        if (text.isEmpty()) return x;
        boolean standalone = !building;
        if (standalone) begin(1, false, false);
        TextRenderer font = font(false);
        if (!fontStarted) {
            font.begin(nativeScale * theme.textSize(11) / regularHeight);
            fontStarted = true;
        }
        font.setAlpha(alpha);
        double result = font.render(text, x, y, color, shadow);
        if (standalone) end();
        return result;
    }

    @Override
    public boolean isBuilding() {
        return building;
    }

    @Override
    public void end() {
        if (!building) throw new IllegalStateException("Theme text rendering is not active.");
        if (fontStarted) font(false).end();
        fontStarted = false;
        building = false;
    }
}
