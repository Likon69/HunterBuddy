package com.hunterbuddy.journal;

import com.hunterbuddy.modules.ClientLog;
import com.hunterbuddy.modules.messenger.ui.Rect;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;

public final class JournalLayout {
    public enum Action {
        LOG("Log"), CLEAR("Clear"), SAVE("Save"), SIZE("Restore"), COORDS("Coords: Visible"),
        TEXT_DOWN("Aa -"), TEXT_UP("Aa +"), SCALE_DOWN("UI -"), SCALE_UP("UI +");

        public static final Action[] ALL = values();
        public final String label;

        Action(String label) {
            this.label = label;
        }
    }

    public final Rect[] buttons = new Rect[Action.ALL.length];
    public Rect window;
    public Rect content;
    public Rect closedButton;
    public double rowHeight;
    public double headerHeight;
    public double prefixWidth;
    public int visible;
    private double screenWidth;
    private double screenHeight;
    private double bottom;
    private double chatRight;
    private double chatTop;
    private double scale;
    private double textScale;
    private boolean expanded;
    private int font = -1;
    private double x = Double.NaN;
    private double y;

    public void update(HudRenderer renderer, ClientLog config, double sw, double sh, double bottom, double chatRight, double chatTop, boolean expanded, int font) {
        double s = config.scale.get();
        double t = config.textScale.get();
        if (window != null && sw == screenWidth && sh == screenHeight && s == scale && t == textScale
            && this.bottom == bottom && this.chatRight == chatRight && this.chatTop == chatTop && this.expanded == expanded && this.font == font) return;
        screenWidth = sw;
        screenHeight = sh;
        this.bottom = bottom;
        this.chatRight = chatRight;
        this.chatTop = chatTop;
        scale = s;
        textScale = t;
        this.expanded = expanded;
        this.font = font;
        double w = Math.max(120, Math.min(sw - 24, expanded ? sw - 36 : 900 * s));
        double h = Math.max(100, Math.min(sh - bottom - 20, expanded ? sh - bottom - 36 : 520 * s));
        if (Double.isNaN(x)) { x = (sw - w) / 2; y = Math.max(10, (sh - bottom - h) / 2); }
        double wx = expanded ? (sw - w) / 2 : Math.max(8, Math.min(sw - w - 8, x));
        double wy = expanded ? 18 : Math.max(8, Math.min(sh - bottom - h - 8, y));
        window = new Rect(wx, wy, w, h);
        double labelWidth = renderer.textWidth("Log", false, 0.82 * t) + 45 * s;
        double labelHeight = renderer.textHeight(false, 0.82 * t) + 10 * s;
        closedButton = icon(sw, sh, s, chatRight, chatTop, labelWidth, labelHeight, Math.max(24, bottom / 2));
        double logHeight = Math.max(29 * s, labelHeight);
        headerHeight = Math.max(44 * s, logHeight + 18 * s);
        buttons[Action.LOG.ordinal()] = new Rect(wx + 12 * s, wy + 9 * s, Math.max(72 * s, labelWidth), logHeight);
        double left = wx + 12 * s;
        double top = wy + headerHeight + 4 * s;
        double bh = Math.max(24 * s, renderer.textHeight(false, 0.72 * t) + 10 * s);
        for (Action action : Action.ALL) {
            if (action == Action.LOG) continue;
            double bw = Math.min(w - 24 * s, renderer.textWidth(action.label, false, 0.72 * t) + 20 * s);
            if (left + bw > wx + w - 12 * s) { left = wx + 12 * s; top += bh + 6 * s; }
            buttons[action.ordinal()] = new Rect(left, top, bw, bh);
            left += bw + 6 * s;
        }
        double bodyTop = top + bh + 10 * s;
        rowHeight = Math.ceil(renderer.textHeight(false, t) + 5 * s);
        content = new Rect(wx + 14 * s, bodyTop, Math.max(1, w - 34 * s), Math.max(0, wy + h - 30 * s - bodyTop));
        prefixWidth = Math.min(content.w() * 0.42, renderer.textWidth("88:88:88 [HunterBuddy]", false, 0.65 * t) + 12 * s);
        visible = Math.max(0, (int) (content.h() / rowHeight));
    }

    public static Rect icon(double sw, double sh, double scale, double chatRight, double chatTop) {
        return icon(sw, sh, scale, chatRight, chatTop, 0, 0, 32);
    }

    public static Rect icon(double sw, double sh, double scale, double chatRight, double chatTop,
                            double minimumWidth, double minimumHeight, double crosshairGap) {
        double w = Math.max(86 * scale, minimumWidth);
        double h = Math.max(34 * scale, minimumHeight);
        double x = sw / 2 - w / 2;
        double y = Math.max(8, sh / 2 - Math.max(76 * scale, h + crosshairGap));
        if (x < chatRight && y + h > chatTop) {
            if (chatTop - h - 12 * scale >= 8) y = chatTop - h - 12 * scale;
            else if (chatRight + w + 12 * scale <= sw) x = chatRight + 8 * scale;
            else y = 8;
        }
        return new Rect(x, y, w, h);
    }

    public void move(double px, double py) {
        x = px;
        y = py;
        font = -1;
    }

    public Action hit(double px, double py, boolean open) {
        if (!open) return closedButton != null && closedButton.contains(px, py) ? Action.LOG : null;
        for (Action action : Action.ALL) if (buttons[action.ordinal()].contains(px, py)) return action;
        return null;
    }
}
