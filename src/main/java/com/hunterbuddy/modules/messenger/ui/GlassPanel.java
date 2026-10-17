package com.hunterbuddy.modules.messenger.ui;

import com.hunterbuddy.modules.messenger.MessengerSettings;
import meteordevelopment.meteorclient.utils.render.color.Color;

final class GlassPanel {
    private static final Color WHITE_TOP = new Color(255, 255, 255, 28);
    private static final Color WHITE_CLEAR = new Color(255, 255, 255, 0);
    private static final Color RIM_LIGHT = new Color(255, 255, 255, 46);
    private static final Color RIM_DARK = new Color(0, 0, 0, 60);
    private static final double SHEEN_HEIGHT = 0.42;

    private GlassPanel() {
    }

    static void draw(Painter p, MessengerSettings cfg, Rect box, double headerH, Color peer) {
        double x = box.x();
        double top = box.y();
        double w = box.w();
        double h = box.h();
        double radius = cfg.cornerRadius.get();
        boolean tint = cfg.glass.get() == MessengerSettings.Glass.Tint;

        if (tint) p.shadow(x, top, w, h, radius, 5, 18);
        else p.shadow(x, top, w, h, radius, 3, 22);

        p.round(x, top, w, h, radius, cfg.backgroundColor.get());
        p.round(x, top, w, headerH, radius, cfg.headerColor.get(), true, false);

        boolean colored = peer != null && cfg.peerColors.get();
        if (colored) p.round(x, top, w, headerH, radius, p.with(peer, 40), true, false);

        if (tint && cfg.sheen.get()) {
            double inset = Math.min(radius, w / 2.0);
            p.gradient(x + inset, top + 1.0, w - inset * 2.0, h * SHEEN_HEIGHT, WHITE_TOP, WHITE_CLEAR);
            p.rect(x + inset, top + 1.0, w - inset * 2.0, 1.0, RIM_LIGHT);
            p.rect(x + inset, top + h - 2.0, w - inset * 2.0, 1.0, RIM_DARK);
        }

        int band = cfg.bandWidth.get();
        if (colored && band > 0) p.rect(x, top + radius, band, h - radius * 2.0, peer);
    }
}
