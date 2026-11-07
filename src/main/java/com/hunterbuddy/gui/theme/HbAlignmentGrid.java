package com.hunterbuddy.gui.theme;

import com.hunterbuddy.gui.theme.widgets.HbWindowSnap;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.List;

public final class HbAlignmentGrid {
    public static final double MINOR_SPACING = 8;
    private static final double FADE_SECONDS = 0.12;
    private static final int MAJOR_EVERY = 4;
    private static final double MINOR_ALPHA = 0.35;
    private static final double MAJOR_ALPHA = 0.7;
    private static final double GUIDE_ALPHA = 0.6;
    private final Color minor = new Color();
    private final Color major = new Color();
    private final Color guide = new Color();
    private double fade;

    public void advance(boolean active, double delta) {
        fade = Math.clamp(fade + (active ? delta : -delta) / FADE_SECONDS, 0, 1);
    }

    public void drawGrid(HbQuadSink sink, HbGuiTheme theme, double width, double height) {
        if (fade <= 0) return;
        HbStyle style = theme.style();
        double eased = HbAnim.easeOut(fade);
        HbDraw.withAlpha(minor, style.gridColor(theme), MINOR_ALPHA * eased);
        HbDraw.withAlpha(major, style.gridColor(theme), MAJOR_ALPHA * eased);
        double spacing = theme.hbScale(MINOR_SPACING);
        double thickness = Math.max(1, Math.round(theme.hbScale(1)));
        int index = 0;
        for (double x = 0; x <= width; x += spacing, index++) {
            boolean isMajor = index % MAJOR_EVERY == 0;
            style.drawGridLine(sink, x, 0, height, true, thickness, isMajor, isMajor ? major : minor);
        }
        index = 0;
        for (double y = 0; y <= height; y += spacing, index++) {
            boolean isMajor = index % MAJOR_EVERY == 0;
            style.drawGridLine(sink, 0, y, width, false, thickness, isMajor, isMajor ? major : minor);
        }
    }

    public void drawGuides(HbQuadSink sink, HbGuiTheme theme, List<HbWindowSnap.Guide> guides, double width, double height) {
        if (fade <= 0 || guides.isEmpty()) return;
        HbDraw.withAlpha(guide, theme.style().guideColor(theme), GUIDE_ALPHA * HbAnim.easeOut(fade));
        double thickness = Math.max(1, Math.round(theme.hbScale(1)));
        for (HbWindowSnap.Guide line : guides) {
            double length = line.to() - line.from();
            if (line.vertical()) sink.quad(Math.clamp(line.at(), 0, Math.max(0, width - thickness)), line.from(), thickness, length, guide);
            else sink.quad(line.from(), Math.clamp(line.at(), 0, Math.max(0, height - thickness)), length, thickness, guide);
        }
    }
}
