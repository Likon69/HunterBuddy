package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.SpawnerDetector;
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
import meteordevelopment.meteorclient.settings.EnumSetting;
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

    public enum MapStyle {
        Clean,
        Screen,
        Grid,
        Radar
    }

    public enum TrailStyle {
        Line,
        Bold,
        Glow
    }

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

    private final Setting<MapStyle> mapStyle = sgGeneral.add(new EnumSetting.Builder<MapStyle>()
        .name("map-style")
        .description("Clean draws on the panel, Screen adds a dark backdrop, Grid adds a faint grid, Radar adds a dense backdrop, range rings and corner marks.")
        .defaultValue(MapStyle.Screen).build());

    private final Setting<TrailStyle> trailStyle = sgGeneral.add(new EnumSetting.Builder<TrailStyle>()
        .name("trail-style")
        .description("The legs already flown: a thin line, a bold one, or a bold one with a soft halo.")
        .defaultValue(TrailStyle.Glow).build());

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
        .defaultValue(new SettingColor(80, 190, 255, 240)).build());

    private final Setting<SettingColor> currentColor = sgColors.add(new ColorSetting.Builder()
        .name("current-color").description("The leg in progress.")
        .defaultValue(new SettingColor(255, 255, 255, 230)).build());

    private final Setting<SettingColor> planColor = sgColors.add(new ColorSetting.Builder()
        .name("plan-color").description("The turns still to come.")
        .defaultValue(new SettingColor(255, 255, 255, 60)).build());

    private final Setting<SettingColor> selfColor = sgColors.add(new ColorSetting.Builder()
        .name("self-color").description("You.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final Setting<SettingColor> mapColor = sgColors.add(new ColorSetting.Builder()
        .name("map-color").description("Backdrop behind the drawing, so the glow and the sky never wash out the lines.")
        .defaultValue(new SettingColor(6, 10, 16, 170))
        .visible(() -> mapStyle.get() != MapStyle.Clean).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();
    private long lastFrameNanos;
    private double animTime;
    private boolean hidden = true;
    private long appearNanos;
    private AreaLoaderMode.CoveragePreview lastPreview;
    private long lastPreviewMillis;

    public SpiralCoverageHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        long now = System.nanoTime();
        long nowMs = System.currentTimeMillis();
        double dt = lastFrameNanos == 0L ? 0.016 : Math.min(0.25, (now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;

        AreaLoaderMode.CoveragePreview preview = null;

        AreaLoader loader = Modules.get().get(AreaLoader.class);
        SpawnerDetector detector = Modules.get().get(SpawnerDetector.class);
        boolean loaderActive = loader != null && loader.isActive();
        boolean areaLoaderHandoff = areaLoaderHandoff(detector);
        if (loader != null && MeteorClient.mc.player != null
            && (loaderActive || !hideWhenInactive.get())) {
            preview = loader.currentMode().coveragePreview();
            if (loaderActive && validPreview(preview)) {
                lastPreview = preview;
                lastPreviewMillis = nowMs;
            }
        }

        boolean stale = false;
        if (!loaderActive && areaLoaderHandoff && validPreview(lastPreview)) {
            preview = lastPreview;
            stale = true;
        }

        if (!validPreview(preview)) {
            if (isInEditor()) {
                preview = demoPreview();
            } else {
                hidden = true;
                setSize(0.0, 0.0);
                return;
            }
        }

        List<double[]> pts = preview.points;

        if (hidden) {
            pulse.clear();
            appearNanos = now;
            hidden = false;
        }

        renderPreview(renderer, now, dt, preview, pts, hudState(loaderActive, detector, preview, stale, nowMs));
    }

    private void renderPreview(HudRenderer renderer, long now, double dt, AreaLoaderMode.CoveragePreview preview,
                               List<double[]> pts, HudState state) {
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
            if (validPoint(p)) {
                rawMinX = Math.min(rawMinX, p[0]);
                rawMaxX = Math.max(rawMaxX, p[0]);
                rawMinZ = Math.min(rawMinZ, p[1]);
                rawMaxZ = Math.max(rawMaxZ, p[1]);
            }
        }

        double minX = anim ? pulse.ease("boundsMinX", rawMinX, dt, 0.6) : rawMinX;
        double maxX = anim ? pulse.ease("boundsMaxX", rawMaxX, dt, 0.6) : rawMaxX;
        double minZ = anim ? pulse.ease("boundsMinZ", rawMinZ, dt, 0.6) : rawMinZ;
        double maxZ = anim ? pulse.ease("boundsMaxZ", rawMaxZ, dt, 0.6) : rawMaxZ;

        double pad = panel.padding();
        double box = boxSize.get();
        double lineH = renderer.textHeight(shadow, scale);
        double gap = 3.0 * scale;
        double barH = Math.max(3.0, 4.0 * scale);
        double w = box;
        w = Math.max(w, renderer.textWidth("ZIGZAG PAUSED COMBAT 999s next 88888 b", shadow, scale));
        w = Math.max(w, renderer.textWidth("turns 88888 lines 88888 88888/88888", shadow, scale));
        w = Math.max(w, renderer.textWidth("center 8888.8 km · flown 8888.8 km", shadow, scale));
        w = Math.max(w, renderer.textWidth("progress 100%", shadow, scale));
        double h = lineH + gap + box + gap + lineH * 3.0 + gap + barH;

        panel.draw(renderer, x + pad, y + pad, w, h, state.severity);

        double left = x + pad + (w - box) / 2.0;
        double headerY = y + pad;
        double top = headerY + lineH + gap;

        if (mapStyle.get() != MapStyle.Clean) {
            if (mapStyle.get() == MapStyle.Radar) {
                SettingColor backdrop = mapColor.get();
                int alpha = (int) (Math.max(225, backdrop.a) * appear);
                Color upper = new Color(backdrop.r, backdrop.g, backdrop.b, alpha);
                Color lower = new Color(backdrop.r / 2, backdrop.g / 2, backdrop.b / 2, alpha);
                renderer.quad(left, top, box, box, upper, upper, lower, lower);
            } else {
                renderer.quad(left, top, box, box, fade(mapColor.get(), appear));
            }
            SettingColor edge = planColor.get();
            Color frame = new Color(edge.r, edge.g, edge.b, (int) (edge.a * 0.5 * appear));
            renderer.quad(left, top, box, 1.0, frame);
            renderer.quad(left, top + box - 1.0, box, 1.0, frame);
            renderer.quad(left, top + 1.0, 1.0, box - 2.0, frame);
            renderer.quad(left + box - 1.0, top + 1.0, 1.0, box - 2.0, frame);

            if (mapStyle.get() == MapStyle.Grid) {
                Color grid = new Color(edge.r, edge.g, edge.b, (int) (edge.a * 0.3 * appear));
                for (int k = 1; k < 4; k++) {
                    double o = box * k / 4.0;
                    renderer.quad(left + o, top + 1.0, 1.0, box - 2.0, grid);
                    renderer.quad(left + 1.0, top + o, box - 2.0, 1.0, grid);
                }
            } else if (mapStyle.get() == MapStyle.Radar) {
                double cx = left + box / 2.0;
                double cy = top + box / 2.0;
                Color guides = new Color(120, 160, 190, (int) (26 * appear));
                for (int k = 1; k <= 3; k++) {
                    ring(renderer, cx, cy, (box / 2.0 - 8.0) * k / 3.0, 0.75, guides);
                }
                Color axes = new Color(120, 160, 190, (int) (16 * appear));
                dashedLine(renderer, left + 8.0, cy, left + box - 8.0, cy, 0.75, axes, 1.0);
                dashedLine(renderer, cx, top + 8.0, cx, top + box - 8.0, 0.75, axes, 1.0);
                double tick = Math.min(12.0, box * 0.08);
                Color marks = new Color(130, 205, 245, (int) (105 * appear));
                for (int ix = 0; ix < 2; ix++) {
                    for (int iy = 0; iy < 2; iy++) {
                        double cornerX = ix == 0 ? left + 3.0 : left + box - 4.0;
                        double cornerY = iy == 0 ? top + 3.0 : top + box - 4.0;
                        renderer.quad(ix == 0 ? cornerX : cornerX - tick + 1.0, cornerY, tick, 1.0, marks);
                        renderer.quad(cornerX, iy == 0 ? cornerY + 1.0 : cornerY - tick + 1.0, 1.0, tick - 1.0, marks);
                    }
                }
            }
        }

        double span = Math.max(1.0, Math.max(maxX - minX, maxZ - minZ));
        double margin = 8.0;
        double fit = (box - margin * 2.0) / span;
        double offX = left + margin - minX * fit + (box - margin * 2.0 - (maxX - minX) * fit) / 2.0;
        double offZ = top + margin - minZ * fit + (box - margin * 2.0 - (maxZ - minZ) * fit) / 2.0;

        int segCount = pts.size() - 1;
        int curSeg = clampSegment(preview.flownSegments, segCount);
        double progress = segmentProgress(pts, curSeg, curX, curZ);
        int turns = Math.max(0, preview.flownSegments);
        String mode = modeName(preview.label);
        double shown = shownProgress(mode, pts, curSeg, progress);
        double easedProgress = anim ? pulse.ease("progress", shown, dt, 0.25) : shown;
        String detail = safeText(preview.detail);
        String distanceLine = distanceLine(mode, preview, pts, curSeg, progress);
        String progressLine = progressLine(mode, shown);
        float turnFresh = anim ? pulse.freshness("turns", String.valueOf(turns), 900L) : 0.0f;
        float badgeFresh = anim ? pulse.freshness("badge", state.badge, 900L) : 0.0f;
        Color turnColor = HudPulse.tint(HudPulse.Style.Subtle, valueColor.get(), currentColor.get(), turnFresh);
        Color badgeColor = HudPulse.tint(HudPulse.Style.Subtle, valueColor.get(), currentColor.get(), badgeFresh);

        double thin = Math.max(1.0, scale);
        double thick = Math.max(1.4, 1.8 * scale);
        double trail = Math.max(1.2, 1.4 * scale);

        double tx = x + pad;
        tx = value(renderer, mode, tx, headerY, shadow, scale, appear);
        tx = label(renderer, " ", tx, headerY, shadow, scale, appear);
        renderer.text(state.badge, tx, headerY, fade(badgeColor, appear), shadow, scale);
        tx += renderer.textWidth(state.badge, shadow, scale);
        if (state.stale) {
            tx = label(renderer, " ", tx, headerY, shadow, scale, appear);
            tx = value(renderer, state.ageSeconds + "s", tx, headerY, shadow, scale, appear);
        }
        if (preview.nextTurnDistance >= 0.0) {
            tx = label(renderer, " next ", tx, headerY, shadow, scale, appear);
            value(renderer, formatDistance(preview.nextTurnDistance), tx, headerY, shadow, scale, appear);
        }

        for (int i = 0; i < segCount; i++) {
            double[] a = pts.get(i);
            double[] b = pts.get(i + 1);
            if (!validPoint(a) || !validPoint(b)) {
                continue;
            }
            double ax = a[0] * fit + offX;
            double ay = a[1] * fit + offZ;
            double bx = b[0] * fit + offX;
            double by = b[1] * fit + offZ;

            if (i < curSeg) {
                SettingColor fc = flownColor.get();
                if (trailStyle.get() == TrailStyle.Line) {
                    stroke(renderer, ax, ay, bx, by, thin, fade(fc, appear));
                } else {
                    if (trailStyle.get() == TrailStyle.Glow) {
                        stroke(renderer, ax, ay, bx, by, trail + 3.0 * scale,
                            new Color(fc.r, fc.g, fc.b, (int) (fc.a * 0.22 * appear)));
                    }
                    stroke(renderer, ax, ay, bx, by, trail, fade(fc, appear));
                }
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
            if (!validPoint(p)) {
                continue;
            }
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

        double ty = top + box + gap;
        tx = x + pad;
        tx = label(renderer, "turns ", tx, ty, shadow, scale, appear);
        renderer.text(String.valueOf(turns), tx, ty, fade(turnColor, appear), shadow, scale);
        tx += renderer.textWidth(String.valueOf(turns), shadow, scale);
        if (!modeMetric(mode, preview, turns).isEmpty()) {
            tx = value(renderer, modeMetric(mode, preview, turns), tx, ty, shadow, scale, appear);
        }
        if (!detail.isEmpty()) {
            tx = label(renderer, " ", tx, ty, shadow, scale, appear);
            value(renderer, detail, tx, ty, shadow, scale, appear);
        }

        ty += lineH;
        value(renderer, distanceLine, x + pad, ty, shadow, scale, appear);

        ty += lineH;
        value(renderer, progressLine, x + pad, ty, shadow, scale, appear);

        double barY = ty + lineH + gap;
        SettingColor pc = planColor.get();
        SettingColor cc = currentColor.get();
        renderer.quad(x + pad, barY, w, barH, new Color(pc.r, pc.g, pc.b, (int) (Math.min(90, pc.a) * appear)));
        renderer.quad(x + pad, barY, w * easedProgress, barH, new Color(cc.r, cc.g, cc.b, (int) (cc.a * appear)));

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    private boolean validPreview(AreaLoaderMode.CoveragePreview preview) {
        if (preview == null || preview.points == null || preview.points.size() < 2) return false;
        if (!Double.isFinite(preview.currentX) || !Double.isFinite(preview.currentZ)) return false;

        int valid = 0;
        for (double[] p : preview.points) {
            if (validPoint(p)) valid++;
            if (valid >= 2) return true;
        }

        return false;
    }

    private static boolean validPoint(double[] p) {
        return p != null && p.length >= 2 && Double.isFinite(p[0]) && Double.isFinite(p[1]);
    }

    private static int clampSegment(int flownSegments, int segCount) {
        if (segCount <= 1) return 0;
        return Math.max(0, Math.min(flownSegments, segCount - 1));
    }

    private static double segmentProgress(List<double[]> pts, int curSeg, double curX, double curZ) {
        if (pts == null || curSeg < 0 || curSeg + 1 >= pts.size()) return 0.0;
        double[] a = pts.get(curSeg);
        double[] b = pts.get(curSeg + 1);
        if (!validPoint(a) || !validPoint(b)) return 0.0;

        double dx = b[0] - a[0];
        double dz = b[1] - a[1];
        double lenSq = dx * dx + dz * dz;
        if (lenSq <= 1.0e-6) return 0.0;

        return MathHelper.clamp(((curX - a[0]) * dx + (curZ - a[1]) * dz) / lenSq, 0.0, 1.0);
    }

    private static double pathLength(List<double[]> pts, int curSeg, double progress) {
        if (pts == null || pts.size() < 2) return 0.0;

        double length = 0.0;
        int lastFull = Math.max(0, Math.min(curSeg, pts.size() - 1));
        for (int i = 0; i < lastFull; i++) {
            length += segmentLength(pts.get(i), pts.get(i + 1));
        }

        if (curSeg >= 0 && curSeg + 1 < pts.size()) {
            length += segmentLength(pts.get(curSeg), pts.get(curSeg + 1)) * MathHelper.clamp(progress, 0.0, 1.0);
        }

        return length;
    }

    private static double segmentLength(double[] a, double[] b) {
        if (!validPoint(a) || !validPoint(b)) return 0.0;
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }

    private static String modeName(String label) {
        String s = safeText(label).toLowerCase();
        return switch (s) {
            case "spiral" -> "SPIRAL";
            case "zigzag" -> "ZIGZAG";
            case "rect" -> "RECT";
            case "circle" -> "CIRCLE";
            default -> s.isEmpty() ? "MODE" : s.toUpperCase();
        };
    }

    private static String modeMetric(String mode, AreaLoaderMode.CoveragePreview preview, int turns) {
        if ("SPIRAL".equals(mode)) return " laps " + turns / 4;
        if ("ZIGZAG".equals(mode)) return " lines " + Math.max(0, preview.modeCounter);
        return "";
    }

    private static String distanceLine(String mode, AreaLoaderMode.CoveragePreview preview, List<double[]> pts, int curSeg, double progress) {
        double origin = validPoint(pts.get(0)) ? Math.hypot(preview.currentX - pts.get(0)[0], preview.currentZ - pts.get(0)[1]) : 0.0;
        return ("SPIRAL".equals(mode) ? "center " : "start ") + formatDistance(origin)
            + " · flown " + formatDistance(pathLength(pts, curSeg, progress));
    }

    private static double shownProgress(String mode, List<double[]> pts, int curSeg, double progress) {
        if ("RECT".equals(mode)) {
            double total = pathLength(pts, pts.size() - 1, 0.0);
            return total > 0.0 ? MathHelper.clamp(pathLength(pts, curSeg, progress) / total, 0.0, 1.0) : 0.0;
        }
        if ("CIRCLE".equals(mode)) {
            double perTurn = (pts.size() - 1 - curSeg) / 2.0;
            if (perTurn <= 0.0) return 0.0;
            double turnsDone = (curSeg + progress) / perTurn;
            return turnsDone - Math.floor(turnsDone);
        }
        if ("SPIRAL".equals(mode)) {
            int lapStart = curSeg - curSeg % 4;
            double lap = 0.0;
            double done = 0.0;
            for (int i = lapStart; i < lapStart + 4 && i + 1 < pts.size(); i++) {
                double len = segmentLength(pts.get(i), pts.get(i + 1));
                lap += len;
                if (i < curSeg) done += len;
                else if (i == curSeg) done += len * progress;
            }
            return lap > 0.0 ? MathHelper.clamp(done / lap, 0.0, 1.0) : 0.0;
        }
        return progress;
    }

    private static String progressLine(String mode, double shown) {
        String percent = Math.round(shown * 100.0) + "%";
        return switch (mode) {
            case "SPIRAL", "CIRCLE" -> "lap " + percent;
            case "RECT" -> "progress " + percent;
            case "ZIGZAG" -> "leg " + percent;
            default -> "side " + percent;
        };
    }

    private static String formatDistance(double blocks) {
        if (!Double.isFinite(blocks)) return "--";
        double b = Math.max(0.0, blocks);
        if (b < 1000.0) return String.format("%.0f b", b);
        return String.format("%.1f km", b / 1000.0);
    }

    private HudState hudState(boolean loaderActive, SpawnerDetector detector, AreaLoaderMode.CoveragePreview preview,
                              boolean stale, long nowMs) {
        if (loaderActive) {
            return new HudState(preview.recovering ? "RECOVER" : "OK",
                preview.recovering ? HudGlowPanel.Severity.WARN : HudGlowPanel.Severity.OK, false, 0L);
        }

        if (areaLoaderHandoff(detector)) {
            String badge;
            if (detector.isAutoPaused()) {
                String reason = safeText(detector.getAutoPauseReason());
                badge = reason.isEmpty() ? "PAUSED" : "PAUSED " + reason.toUpperCase();
            } else {
                badge = safeText(detector.getAutoStateName());
                if (badge.isEmpty()) badge = "SPAWNER";
            }

            long age = lastPreviewMillis > 0L ? Math.max(0L, (nowMs - lastPreviewMillis) / 1000L) : 0L;
            return new HudState(badge, HudGlowPanel.Severity.WARN, stale, age);
        }

        return new HudState("OFF", HudGlowPanel.Severity.OK, false, 0L);
    }

    private static boolean spawnerBusy(SpawnerDetector detector) {
        if (detector == null || !detector.isActive()) return false;
        String state = detector.getAutoStateName();
        return state != null && !"IDLE".equals(state);
    }

    private static boolean areaLoaderHandoff(SpawnerDetector detector) {
        return spawnerBusy(detector) && detector.isAutoAreaLoaderHandoff();
    }

    private static String safeText(String s) {
        return s == null ? "" : s;
    }

    private static final class HudState {
        final String badge;
        final HudGlowPanel.Severity severity;
        final boolean stale;
        final long ageSeconds;

        HudState(String badge, HudGlowPanel.Severity severity, boolean stale, long ageSeconds) {
            this.badge = badge;
            this.severity = severity;
            this.stale = stale;
            this.ageSeconds = ageSeconds;
        }
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
