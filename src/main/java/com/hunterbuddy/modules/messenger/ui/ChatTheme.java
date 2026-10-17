package com.hunterbuddy.modules.messenger.ui;

import com.hunterbuddy.modules.messenger.MessengerSettings;
import meteordevelopment.meteorclient.utils.render.color.Color;

public enum ChatTheme {
    Classic,
    Bubbles,
    Slate,
    Compact;

    double padding(double lineHeight) {
        return Math.max(3.0, Math.round(lineHeight * (this == Compact ? 0.3 : 0.5)));
    }

    double rowHeight(double lineHeight, double spacing) {
        return lineHeight + spacing * (this == Compact ? 0.4 : 1.0) + (this == Bubbles ? 3.0 : 0.0);
    }

    double gap() {
        return switch (this) {
            case Classic -> 0.35;
            case Bubbles -> 0.65;
            case Slate -> 0.3;
            case Compact -> 0.15;
        };
    }

    double messageFraction() {
        return this == Classic ? 0.8 : this == Bubbles ? 0.84 : 1.0;
    }

    boolean alignedBubbles() {
        return this == Classic || this == Bubbles;
    }

    double radius(MessengerSettings cfg) {
        double radius = cfg.cornerRadius.get();
        return switch (this) {
            case Classic -> radius;
            case Bubbles -> radius * 1.6;
            case Slate -> Math.min(3.0, radius);
            case Compact -> Math.min(4.0, radius);
        };
    }

    double buttonRadius(double height) {
        return this == Slate || this == Compact ? 3.0 : height / 2.0;
    }

    void panel(Painter p, MessengerSettings cfg, Rect box, double headerH, Color peer) {
        if (this == Classic) {
            GlassPanel.draw(p, cfg, box, headerH, peer);
            return;
        }

        double radius = radius(cfg);
        double x = box.x();
        double y = box.y();
        double w = box.w();
        double h = box.h();
        Color accent = peer != null && cfg.peerColors.get() ? peer : cfg.accentColor.get();
        if (this != Compact) p.shadow(x, y, w, h, radius, 4, 18);
        p.round(x, y, w, h, radius, opaque(cfg.backgroundColor.get(), this == Bubbles ? 225 : 245));
        p.round(x, y, w, headerH, radius, opaque(cfg.headerColor.get(), 245), true, false);
        p.rect(x + radius, y + headerH - 1.0, w - radius * 2.0, 1.0, p.with(accent, this == Bubbles ? 150 : 75));
        if (this == Bubbles && cfg.glass.get() == MessengerSettings.Glass.Tint && cfg.sheen.get()) {
            p.gradient(x + radius, y + 1.0, w - radius * 2.0, headerH * 0.6,
                new Color(255, 255, 255, 18), new Color(255, 255, 255, 0));
        }
        if (peer != null && cfg.peerColors.get() && cfg.bandWidth.get() > 0) {
            p.round(x, y + radius, cfg.bandWidth.get(), h - radius * 2.0, 1.5, accent);
        }
    }

    void message(Painter p, MessengerSettings cfg, MessageLayout.Row row, Rect box) {
        Color fill = row.incoming() ? cfg.incomingColor.get() : cfg.outgoingColor.get();
        double radius = radius(cfg) * 0.8;
        if (alignedBubbles()) {
            p.round(box.x(), box.y(), box.w(), box.h(), radius,
                this == Bubbles ? opaque(fill, 235) : fill, row.first(), row.last());
            if (this == Classic && row.first() && cfg.glass.get() == MessengerSettings.Glass.Tint && cfg.sheen.get()) {
                double inset = Math.min(radius, box.w() / 2.0);
                p.gradient(box.x() + inset, box.y() + 1.0, box.w() - inset * 2.0, box.h() * 0.5,
                    new Color(255, 255, 255, 14), new Color(255, 255, 255, 0));
            }
            if (this == Bubbles && row.last()) {
                double tailX = row.incoming() ? box.x() - 2.0 : box.x() + box.w() - 5.0;
                p.round(tailX, box.y() + box.h() - 5.0, 7.0, 4.0, 2.0, opaque(fill, 235));
            }
        } else {
            if (this == Slate) p.rect(box.x(), box.y(), box.w(), box.h(), p.with(fill, 75));
            Color marker = row.incoming() ? cfg.dimColor.get() : cfg.outgoingColor.get();
            p.rect(box.x(), box.y() + (row.first() ? 2.0 : 0.0), this == Compact ? 2.0 : 3.0,
                box.h() - (row.first() ? 2.0 : 0.0) - (row.last() ? 2.0 : 0.0), marker);
        }
    }

    void input(Painter p, MessengerSettings cfg, Rect box, boolean focused) {
        Color border = p.with(focused ? cfg.accentColor.get() : cfg.dimColor.get(), focused ? 255 : 80);
        Color fill = cfg.glass.get() == MessengerSettings.Glass.Tint ? p.with(cfg.headerColor.get(), 120) : cfg.headerColor.get();
        if (this == Compact) {
            p.rect(box.x(), box.y(), box.w(), box.h(), opaque(cfg.headerColor.get(), 245));
            p.rect(box.x(), box.y() + box.h() - 1.0, box.w(), 1.0, border);
        } else {
            p.bordered(box.x(), box.y(), box.w(), box.h(), radius(cfg) * (this == Bubbles ? 1.0 : 0.6),
                this == Classic ? fill : opaque(cfg.headerColor.get(), 245), border);
        }
    }

    void contact(Painter p, MessengerSettings cfg, Rect row, boolean hover, boolean unread) {
        if (this == Bubbles || hover) {
            Color color = hover ? cfg.accentColor.get() : unread ? cfg.outgoingColor.get() : cfg.incomingColor.get();
            p.round(row.x(), row.y() + 1.0, row.w(), row.h() - 2.0, radius(cfg) * 0.6,
                p.with(color, hover ? 55 : 38));
        }
        if (this == Slate || this == Compact) {
            p.rect(row.x(), row.y() + row.h() - 1.0, row.w(), 1.0, p.with(cfg.dimColor.get(), 25));
        }
    }

    private static Color opaque(Color color, int alpha) {
        return new Color(color.r, color.g, color.b, Math.max(color.a, alpha));
    }
}
