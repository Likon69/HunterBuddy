package com.hunterbuddy.hud;

import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.Identifier;

final class GlassDrop {
    static final double SHADOW_SCALE = 1.35;
    static final double SPECULAR_MIN_DIAMETER = 36.0;

    private static final double SHADOW_DROP = 0.08;
    private static final double[] HALO_RADII = {4.0, 9.0, 15.0};
    private static final int[] HALO_ALPHA = {36, 20, 10};
    private static final Color SHADOW_TINT = new Color(0, 0, 0, 110);
    private static final Color RIM_TINT = new Color(255, 255, 255, 230);
    private static final Color SPECULAR_TINT = new Color(255, 255, 255, 235);

    private static final Identifier SHADOW = Identifier.of("hunterbuddy", "textures/messenger/drop_shadow.png");
    private static final Identifier BODY = Identifier.of("hunterbuddy", "textures/messenger/drop_body.png");
    private static final Identifier RIM = Identifier.of("hunterbuddy", "textures/messenger/drop_rim.png");
    private static final Identifier SPECULAR = Identifier.of("hunterbuddy", "textures/messenger/drop_specular.png");
    private static final Identifier DISC = Identifier.of("hunterbuddy", "textures/messenger/disc.png");
    private static final Identifier GLOW = Identifier.of("hunterbuddy", "textures/radar/glow.png");
    private static final Identifier RING = Identifier.of("hunterbuddy", "textures/radar/ring.png");

    private final Color[] haloColors = {new Color(), new Color(), new Color()};
    private final Color rippleColor = new Color();
    private final HudRenderer renderer;

    GlassDrop(HudRenderer renderer) {
        this.renderer = renderer;
    }

    void draw(double cx, double cy, double d, Color tint, boolean halo, double stretchX, double stretchY, boolean hover) {
        double shadow = d * SHADOW_SCALE;
        renderer.texture(SHADOW, cx - shadow / 2.0, cy + d * SHADOW_DROP - shadow / 2.0, shadow, shadow, SHADOW_TINT);

        if (halo) {
            for (int i = HALO_RADII.length - 1; i >= 0; i--) {
                double size = d + HALO_RADII[i] * 2.0;
                renderer.texture(GLOW, cx - size / 2.0, cy - size / 2.0, size, size,
                    haloColors[i].set(tint.r, tint.g, tint.b, HALO_ALPHA[i]));
            }
        }

        double w = d * stretchX;
        double h = d * stretchY;
        renderer.texture(BODY, cx - w / 2.0, cy - h / 2.0, w, h, tint);
        renderer.texture(RIM, cx - w / 2.0, cy - h / 2.0, w, h, RIM_TINT);

        if (d >= SPECULAR_MIN_DIAMETER) {
            double lift = hover ? 1.0 : 0.0;
            renderer.texture(SPECULAR, cx - w / 2.0 - lift, cy - h / 2.0 - lift, w, h, SPECULAR_TINT);
        }
    }

    void ripple(double cx, double cy, double d, Color accent, float fresh) {
        double size = d + 0.7 * d * (1.0 - fresh);
        renderer.texture(RING, cx - size / 2.0, cy - size / 2.0, size, size,
            rippleColor.set(accent.r, accent.g, accent.b, (int) (180 * fresh)));
    }

    void core(double cx, double cy, double d, Color color) {
        double size = d * 0.58;
        renderer.texture(DISC, cx - size / 2.0, cy - size / 2.0, size, size, color);
    }
}
