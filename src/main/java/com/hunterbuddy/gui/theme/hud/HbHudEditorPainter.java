package com.hunterbuddy.gui.theme.hud;

import com.hunterbuddy.gui.theme.HbAlignmentGrid;
import com.hunterbuddy.gui.theme.HbAnim;
import com.hunterbuddy.gui.theme.HbDraw;
import com.hunterbuddy.gui.theme.HbGuiTheme;
import com.hunterbuddy.gui.theme.HbQuadSink;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.List;

public final class HbHudEditorPainter {
    public record Marquee(int x1, int y1, int x2, int y2) {
    }

    private static final double THIRDS_ALPHA = 0.6;
    private static final double DASH_DIVISOR = 25.0;
    private static final double SELECTED_FILL = 0.07;
    private static final double MARQUEE_FILL = 0.10;
    private static final double HOVER_FILL = 0.12;
    private static final double INACTIVE_FILL = 0.25;
    private static final double INACTIVE_OUTLINE = 0.8;
    private final HbAlignmentGrid alignment = new HbAlignmentGrid();
    private final HbQuadSink sink = (x, y, width, height, color) -> Renderer2D.COLOR.quad(x, y, width, height, color);
    private final Color fill = new Color();
    private final Color outline = new Color();

    public void paintBackdrop(HbGuiTheme theme, boolean active, double delta, double animation) {
        alignment.advance(active, delta);
        double width = Utils.getWindowWidth();
        double height = Utils.getWindowHeight();
        alignment.drawGrid(sink, theme, width, height);
        HbDraw.withAlpha(outline, theme.colors().dim(), THIRDS_ALPHA * HbAnim.easeOut(animation));
        double thickness = thickness(theme);
        double dash = width / DASH_DIVISOR;
        for (int third = 1; third <= 2; third++) {
            double x = width / 3.0 * third;
            for (double y = 0; y < height; y += dash * 2) sink.quad(x, y, thickness, Math.min(dash, height - y), outline);
            double y = height / 3.0 * third;
            for (double dashX = 0; dashX < width; dashX += dash * 2) sink.quad(dashX, y, Math.min(dash, width - dashX), thickness, outline);
        }
    }

    public void paintElements(HbGuiTheme theme, Hud hud, List<HudElement> selection, HudElement hovered, Marquee marquee, boolean aligning) {
        double thickness = thickness(theme);
        for (HudElement element : hud) {
            frame(element, thickness, null, theme.colors().border());
        }
        for (HudElement element : hud) {
            if (element.isActive()) continue;
            HbDraw.withAlpha(fill, theme.colors().risk(), INACTIVE_FILL);
            HbDraw.withAlpha(outline, theme.colors().risk(), INACTIVE_OUTLINE);
            frame(element, thickness, fill, outline);
        }
        HbDraw.withAlpha(fill, theme.accent(), SELECTED_FILL);
        for (HudElement element : selection) frame(element, thickness, fill, theme.accent());
        if (marquee != null) {
            HbDraw.withAlpha(fill, theme.accent(), MARQUEE_FILL);
            frame(marquee.x1(), marquee.y1(), marquee.x2() - marquee.x1(), marquee.y2() - marquee.y1(), thickness, fill, theme.accent());
        }
        if (hovered != null) {
            HbDraw.withAlpha(fill, theme.colors().dim(), HOVER_FILL);
            frame(hovered, thickness, fill, theme.colors().dim());
        }
        if (aligning) {
            int width = Utils.getWindowWidth();
            int height = Utils.getWindowHeight();
            alignment.drawGuides(sink, theme, HbHudGuides.of(selection, hud, width, height, hud.border.get()), width, height);
        }
    }

    private void frame(HudElement element, double thickness, Color body, Color edge) {
        frame(element.x, element.y, element.getWidth(), element.getHeight(), thickness, body, edge);
    }

    private void frame(double x, double y, double width, double height, double thickness, Color body, Color edge) {
        if (body != null) sink.quad(x + thickness, y + thickness, width - thickness * 2, height - thickness * 2, body);
        sink.quad(x, y, width, thickness, edge);
        sink.quad(x, y + height - thickness, width, thickness, edge);
        sink.quad(x, y + thickness, thickness, height - thickness * 2, edge);
        sink.quad(x + width - thickness, y + thickness, thickness, height - thickness * 2, edge);
    }

    private static double thickness(HbGuiTheme theme) {
        return Math.max(1, Math.round(theme.hbScale(1)));
    }
}
