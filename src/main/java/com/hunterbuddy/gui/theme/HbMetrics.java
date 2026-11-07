package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.renderer.text.TextRenderer;

final class HbMetrics {
    private double meteorTextHeight = Double.NaN;
    private double layoutScale = 1;
    private double textScale = 1;

    HbMetrics() {
        refresh();
    }

    boolean refresh() {
        TextRenderer meteor = TextRenderer.get();
        if (meteor.isBuilding()) return false;
        double height = meteor.getHeight();
        if (height == meteorTextHeight) return false;
        meteorTextHeight = height;
        layoutScale = (height + 8) / 20;
        textScale = height / (12 * layoutScale);
        return true;
    }

    double meteorTextHeight() {
        return meteorTextHeight;
    }

    double layoutScale() {
        return layoutScale;
    }

    double textScale() {
        return textScale;
    }
}
