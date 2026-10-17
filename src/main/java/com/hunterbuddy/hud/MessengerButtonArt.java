package com.hunterbuddy.hud;

import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.Identifier;

final class MessengerButtonArt {
    enum Shape {
        Circle("disc", "drop_body", "drop_rim", "drop_specular"),
        Square("square", "square_glass", "square_rim", "square_sheen"),
        RoundedSquare("rounded", "rounded_glass", "rounded_rim", "rounded_sheen");

        final Identifier mask;
        final Identifier body;
        final Identifier rim;
        final Identifier sheen;

        Shape(String mask, String body, String rim, String sheen) {
            this.mask = texture(mask);
            this.body = texture(body);
            this.rim = texture(rim);
            this.sheen = texture(sheen);
        }

        boolean contains(double px, double py, double size) {
            if (px < 0.0 || py < 0.0 || px > size || py > size) return false;
            double radius = this == Circle ? size / 2.0 : this == RoundedSquare ? size * 0.24 : 0.0;
            double dx = Math.max(0.0, Math.abs(px - size / 2.0) - (size / 2.0 - radius));
            double dy = Math.max(0.0, Math.abs(py - size / 2.0) - (size / 2.0 - radius));
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    enum Icon {
        ChatBubble("icon_chat"),
        Conversation("icon_conversation"),
        Envelope("icon_envelope");

        final Identifier texture;

        Icon(String name) {
            texture = texture(name);
        }
    }

    private static final Identifier GLOW = Identifier.of("hunterbuddy", "textures/radar/glow.png");
    private final HudRenderer renderer;
    private final GlassDrop drop;

    MessengerButtonArt(HudRenderer renderer) {
        this.renderer = renderer;
        drop = new GlassDrop(renderer);
    }

    void flat(Shape shape, double x, double y, double size, Color base, boolean hover) {
        renderer.texture(shape.mask, x, y, size, size, lifted(base, hover));
        renderer.texture(shape.rim, x, y, size, size, new Color(255, 255, 255, hover ? 95 : 35));
    }

    void glass(Shape shape, double x, double y, double size, Color base, boolean halo, boolean hover, double shiver) {
        Color tint = lifted(base, hover);
        if (shape == Shape.Circle) {
            drop.draw(x + size / 2.0, y + size / 2.0, size, tint, halo, 1.0 + shiver, 1.0 - shiver, hover);
            return;
        }

        renderer.texture(shape.mask, x - size * 0.04, y + size * 0.04, size * 1.08, size * 1.08, new Color(0, 0, 0, 75));
        if (halo) renderer.texture(GLOW, x - size * 0.2, y - size * 0.2, size * 1.4, size * 1.4,
            new Color(tint.r, tint.g, tint.b, 65));
        double w = size * (1.0 + shiver);
        double h = size * (1.0 - shiver);
        double left = x + (size - w) / 2.0;
        double top = y + (size - h) / 2.0;
        renderer.texture(shape.body, left, top, w, h, tint);
        renderer.texture(shape.rim, left, top, w, h, new Color(255, 255, 255, 170));
        renderer.texture(shape.sheen, left, top, w, h, new Color(255, 255, 255, hover ? 195 : 160));
    }

    void outline(Shape shape, double x, double y, double size, Color color, double grow) {
        double expanded = size * grow;
        renderer.texture(shape.rim, x + (size - expanded) / 2.0, y + (size - expanded) / 2.0, expanded, expanded, color);
    }

    void ripple(Shape shape, double x, double y, double size, Color accent, float fresh) {
        outline(shape, x, y, size, new Color(accent.r, accent.g, accent.b, (int) (180 * fresh)),
            1.0 + 0.7 * (1.0 - fresh));
    }

    void icon(Shape shape, Icon icon, double x, double y, double size, Color color, Color backdrop) {
        if (backdrop != null) {
            double core = size * 0.72;
            renderer.texture(shape.mask, x + (size - core) / 2.0, y + (size - core) / 2.0, core, core, backdrop);
        }
        double glyph = size * 0.62;
        renderer.texture(icon.texture, x + (size - glyph) / 2.0, y + (size - glyph) / 2.0, glyph, glyph, color);
    }

    private static Color lifted(Color color, boolean hover) {
        int lift = hover ? 24 : 0;
        return new Color(Math.min(255, color.r + lift), Math.min(255, color.g + lift), Math.min(255, color.b + lift), color.a);
    }

    private static Identifier texture(String name) {
        return Identifier.of("hunterbuddy", "textures/messenger/" + name + ".png");
    }
}
