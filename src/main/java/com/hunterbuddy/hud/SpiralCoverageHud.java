package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.arealoader.AreaLoaderMode;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.MathHelper;

public class SpiralCoverageHud extends HudElement {
    public static final HudElementInfo<SpiralCoverageHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "SpiralCoverage",
        "AreaLoader coverage for any mode: flown legs, current leg, planned turns.",
        SpiralCoverageHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Double> boxSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("size").description("Side of the drawing, in pixels.")
        .defaultValue(140.0).min(70.0).max(320.0).sliderRange(90.0, 240.0).build());

    private final Setting<Boolean> hideWhenInactive = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-when-inactive").description("Disappear while AreaLoader is not running.")
        .defaultValue(true).build());

    private final Setting<Boolean> animations = sgGeneral.add(new BoolSetting.Builder()
        .name("animations")
        .description("The marker's glide, its sonar ring and the eased reframing as the spiral grows.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("Labels.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("Values.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> flownColor = sgColors.add(new ColorSetting.Builder()
        .name("flown-color").description("Legs already flown.")
        .defaultValue(new SettingColor(110, 240, 130, 220)).build());

    private final Setting<SettingColor> currentColor = sgColors.add(new ColorSetting.Builder()
        .name("current-color").description("The leg in progress.")
        .defaultValue(new SettingColor(255, 255, 255, 230)).build());

    private final Setting<SettingColor> planColor = sgColors.add(new ColorSetting.Builder()
        .name("plan-color").description("The turns still to come.")
        .defaultValue(new SettingColor(255, 255, 255, 60)).build());

    private final Setting<SettingColor> selfColor = sgColors.add(new ColorSetting.Builder()
        .name("self-color").description("You.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();
    private long lastFrameNanos;
    private double animTime;
    private boolean hidden = true;
    private long appearNanos;

    public SpiralCoverageHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.016 : Math.min(0.25, (now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;

        AreaLoaderMode.CoveragePreview preview = null;

        AreaLoader loader = Modules.get().get(AreaLoader.class);
        if (loader != null && MeteorClient.mc.player != null
            && (loader.isActive() || !hideWhenInactive.get())) {
            preview = loader.currentMode().coveragePreview();
        }

        if (preview == null) {
            if (isInEditor()) {
                preview = demoPreview();
            } else {
                hidden = true;
                setSize(0.0, 0.0);
                return;
            }
        }

        List<double[]> pts = preview.points;
        if (pts == null || pts.size() < 2) {
            hidden = true;
            setSize(0.0, 0.0);
            return;
        }

        if (hidden) {
            pulse.clear();
            appearNanos = now;
            hidden = false;
        }

        renderPreview(renderer, now, dt, preview, pts);
    }

    private void renderPreview(HudRenderer renderer, long now, double dt, AreaLoaderMode.CoveragePreview preview,
                               List<double[]> pts) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        boolean anim = animations.get();
        animTime += dt;

        double appear;
        if (anim) {
            double elapsed = (now - appearNanos) / 1_000_000_000.0;
            double t = Math.min(1.0, elapsed / 0.35);
            appear = 0.2 + 0.8 * smoothstep(t);
        } else {
            appear = 1.0;
        }

        double curX = preview.currentX;
        double curZ = preview.currentZ;

        double rawMinX = curX;
        double rawMaxX = curX;
        double rawMinZ = curZ;
        double rawMaxZ = curZ;
        for (double[] p : pts) {
            rawMinX = Math.min(rawMinX, p[0]);
            rawMaxX = Math.max(rawMaxX, p[0]);
            rawMinZ = Math.min(rawMinZ, p[1]);
            rawMaxZ = Math.max(rawMaxZ, p[1]);
        }

        double minX = anim ? pulse.ease("boundsMinX", rawMinX, dt, 0.6) : rawMinX;
        double maxX = anim ? pulse.ease("boundsMaxX", rawMaxX, dt, 0.6) : rawMaxX;
        double minZ = anim ? pulse.ease("boundsMinZ", rawMinZ, dt, 0.6) : rawMinZ;
        double maxZ = anim ? pulse.ease("boundsMaxZ", rawMaxZ, dt, 0.6) : rawMaxZ;

        double pad = panel.padding();
        double box = boxSize.get();
        double lineH = renderer.textHeight(shadow, scale);
        double w = Math.max(box, renderer.textWidth("zigzag · 88888/88888 · turn 88888 b", shadow, scale));
        double h = box + 2.0 + lineH;

        HudGlowPanel.Severity severity = preview.recovering ? HudGlowPanel.Severity.WARN : HudGlowPanel.Severity.OK;
        panel.draw(renderer, x + pad, y + pad, w, h, severity);

        double left = x + pad + (w - box) / 2.0;
        double top = y + pad;

        double span = Math.max(1.0, Math.max(maxX - minX, maxZ - minZ));
        double margin = 8.0;
        double fit = (box - margin * 2.0) / span;
        double offX = left + margin - minX * fit + (box - margin * 2.0 - (maxX - minX) * fit) / 2.0;
        double offZ = top + margin - minZ * fit + (box - margin * 2.0 - (maxZ - minZ) * fit) / 2.0;

        int segCount = pts.size() - 1;
        int curSeg = Math.max(0, Math.min(preview.flownSegments, segCount - 1));

        double thin = Math.max(1.0, scale);
        double thick = Math.max(1.4, 1.8 * scale);

        for (int i = 0; i < segCount; i++) {
            double[] a = pts.get(i);
            double[] b = pts.get(i + 1);
            double ax = a[0] * fit + offX;
            double ay = a[1] * fit + offZ;
            double bx = b[0] * fit + offX;
            double by = b[1] * fit + offZ;

            if (i < curSeg) {
                renderer.line(ax, ay, bx, by, fade(flownColor.get(), appear));
            } else if (i == curSeg) {
                double dxw = b[0] - a[0];
                double dzw = b[1] - a[1];
                double lenSq = dxw * dxw + dzw * dzw;
                double t = lenSq > 1.0e-6
                    ? MathHelper.clamp(((curX - a[0]) * dxw + (curZ - a[1]) * dzw) / lenSq, 0.0, 1.0)
                    : 0.0;
                double splitX = ax + (bx - ax) * t;
                double splitY = ay + (by - ay) * t;

                SettingColor cc = currentColor.get();
                stroke(renderer, ax, ay, splitX, splitY, thick, fade(cc, appear));
                stroke(renderer, splitX, splitY, bx, by, thick,
                    new Color(cc.r, cc.g, cc.b, (int) (cc.a * 0.35 * appear)));
            } else {
                SettingColor pc = planColor.get();
                dashedLine(renderer, ax, ay, bx, by, thin, fade(pc, appear), scale);
            }
        }

        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            double px = p[0] * fit + offX;
            double py = p[1] * fit + offZ;
            SettingColor markColor = i <= curSeg ? flownColor.get() : planColor.get();
            disc(renderer, px, py, Math.max(1.2, 1.6 * scale),
                new Color(markColor.r, markColor.g, markColor.b, (int) (Math.min(180, markColor.a) * appear)));
        }

        double rawMarkerX = curX * fit + offX;
        double rawMarkerY = curZ * fit + offZ;
        double markerX = anim ? pulse.ease("markerX", rawMarkerX, dt, 0.18) : rawMarkerX;
        double markerY = anim ? pulse.ease("markerY", rawMarkerY, dt, 0.18) : rawMarkerY;

        SettingColor self = selfColor.get();
        if (anim) {
            double period = 3.2;
            double f = (animTime % period) / period;
            double pingR = 3.0 * scale + 13.0 * scale * Math.pow(f, 0.6);
            int pingA = (int) (70.0 * (1.0 - smoothstep(f)));
            if (pingA > 4) {
                ring(renderer, markerX, markerY, pingR, Math.max(1.0, scale),
                    new Color(self.r, self.g, self.b, (int) (pingA * appear)));
            }
        }

        ring(renderer, markerX, markerY, 4.0 * scale, Math.max(1.0, 1.1 * scale),
            new Color(self.r, self.g, self.b, (int) (self.a * appear)));
        disc(renderer, markerX, markerY, 2.2 * scale, fade(self, appear));

        double ty = top + box + 2.0;
        double tx = x + pad;
        tx = label(renderer, preview.label + " · ", tx, ty, shadow, scale, appear);
        tx = value(renderer, preview.detail, tx, ty, shadow, scale, appear);
        if (preview.recovering) {
            label(renderer, " · recovering", tx, ty, shadow, scale, appear);
        } else if (preview.nextTurnDistance >= 0.0) {
            tx = label(renderer, " · turn ", tx, ty, shadow, scale, appear);
            value(renderer, String.format("%.0f b", preview.nextTurnDistance), tx, ty, shadow, scale, appear);
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    private double label(HudRenderer renderer, String s, double tx, double ty, boolean shadow, double scale,
                         double appear) {
        renderer.text(s, tx, ty, fade(labelColor.get(), appear), shadow, scale);
        return tx + renderer.textWidth(s, shadow, scale);
    }

    private double value(HudRenderer renderer, String s, double tx, double ty, boolean shadow, double scale,
                         double appear) {
        renderer.text(s, tx, ty, fade(valueColor.get(), appear), shadow, scale);
        return tx + renderer.textWidth(s, shadow, scale);
    }

    private void dashedLine(HudRenderer renderer, double x1, double y1, double x2, double y2,
                            double thickness, Color colour, double scale) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1.0e-6) {
            return;
        }

        double ux = dx / len;
        double uy = dy / len;
        double dash = 4.0 * scale;
        double gap = 3.0 * scale;
        double pos = 0.0;
        while (pos < len) {
            double segEnd = Math.min(len, pos + dash);
            stroke(renderer, x1 + ux * pos, y1 + uy * pos, x1 + ux * segEnd, y1 + uy * segEnd, thickness, colour);
            pos = segEnd + gap;
        }
    }

    private void disc(HudRenderer renderer, double cx, double cy, double r, Color colour) {
        int segments = 20;
        double previousX = cx + r;
        double previousY = cy;

        for (int i = 1; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            double x = cx + Math.cos(angle) * r;
            double y = cy + Math.sin(angle) * r;
            tri(renderer, cx, cy, previousX, previousY, x, y, colour);
            previousX = x;
            previousY = y;
        }
    }

    private void ring(HudRenderer renderer, double cx, double cy, double r, double thickness, Color colour) {
        int segments = 24;
        double inner = Math.max(0.5, r - thickness);
        double outerX = cx + r;
        double outerY = cy;
        double innerX = cx + inner;
        double innerY = cy;

        for (int i = 1; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            double ox = cx + Math.cos(angle) * r;
            double oy = cy + Math.sin(angle) * r;
            double ix = cx + Math.cos(angle) * inner;
            double iy = cy + Math.sin(angle) * inner;

            tri(renderer, outerX, outerY, ox, oy, ix, iy, colour);
            tri(renderer, outerX, outerY, ix, iy, innerX, innerY, colour);

            outerX = ox;
            outerY = oy;
            innerX = ix;
            innerY = iy;
        }
    }

    private void tri(HudRenderer renderer, double x1, double y1, double x2, double y2, double x3, double y3,
                     Color colour) {
        double cross = (x2 - x1) * (y3 - y1) - (x3 - x1) * (y2 - y1);

        if (cross > 0.0) renderer.triangle(x1, y1, x3, y3, x2, y2, colour);
        else renderer.triangle(x1, y1, x2, y2, x3, y3, colour);
    }

    private void stroke(HudRenderer renderer, double x1, double y1, double x2, double y2, double thickness,
                        Color colour) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1.0e-6) return;

        double nx = -dy / len * (thickness / 2.0);
        double ny = dx / len * (thickness / 2.0);

        tri(renderer, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, colour);
        tri(renderer, x1 + nx, y1 + ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, colour);
    }

    private static double smoothstep(double x) {
        return x * x * (3.0 - 2.0 * x);
    }

    private static Color fade(Color colour, double appear) {
        return new Color(colour.r, colour.g, colour.b, (int) (colour.a * appear));
    }

    private AreaLoaderMode.CoveragePreview demoPreview() {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{0, 0});
        pts.add(new double[]{56, 0});
        pts.add(new double[]{56, 56});
        pts.add(new double[]{-56, 56});
        pts.add(new double[]{-56, -56});
        pts.add(new double[]{112, -56});
        pts.add(new double[]{112, 112});

        double t = (System.currentTimeMillis() % 6000L) / 6000.0;
        double curX = -56;
        double curZ = 56 - t * 112;
        return new AreaLoaderMode.CoveragePreview(pts, 3, curX, curZ, "spiral", "224x168", 96.0, false);
    }
}
