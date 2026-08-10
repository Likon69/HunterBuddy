package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.arealoader.AreaLoaderMode;
import com.hunterbuddy.util.HudGlowPanel;
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

/**
 * The AreaLoader path drawn whole, for whichever mode is running: legs flown, the leg in progress,
 * the turns to come.
 *
 * <p>Each mode hands back the same shape — an ordered world-XZ polyline plus how far along it the
 * flight is — so the picture cannot drift from the path the module actually steers by. Spiral,
 * Rectangle, ZigZag and the polar Circle all render here. Green is behind you, white is under you,
 * grey is the plan.
 */
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

    public SpiralCoverageHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
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
                setSize(0.0, 0.0);
                return;
            }
        }

        List<double[]> pts = preview.points;
        if (pts == null || pts.size() < 2) {
            setSize(0.0, 0.0);
            return;
        }

        double curX = preview.currentX;
        double curZ = preview.currentZ;

        double minX = curX;
        double maxX = curX;
        double minZ = curZ;
        double maxZ = curZ;
        for (double[] p : pts) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }

        boolean shadow = textShadow.get();
        double scale = textScale.get();
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

        for (int i = 0; i < pts.size() - 1; i++) {
            SettingColor color = i < preview.flownSegments ? flownColor.get()
                : i == preview.flownSegments ? currentColor.get()
                : planColor.get();

            double[] a = pts.get(i);
            double[] b = pts.get(i + 1);
            renderer.line(a[0] * fit + offX, a[1] * fit + offZ, b[0] * fit + offX, b[1] * fit + offZ, color);
        }

        SettingColor self = selfColor.get();
        int pulse = (int) (170 + 85 * Math.sin(System.currentTimeMillis() / 250.0));
        renderer.quad(curX * fit + offX - 2.0, curZ * fit + offZ - 2.0, 4.0, 4.0,
            new Color(self.r, self.g, self.b, Math.max(0, Math.min(255, pulse))));

        double ty = top + box + 2.0;
        double tx = x + pad;
        tx = label(renderer, preview.label + " · ", tx, ty, shadow, scale);
        tx = value(renderer, preview.detail, tx, ty, shadow, scale);
        if (preview.recovering) {
            label(renderer, " · recovering", tx, ty, shadow, scale);
        } else if (preview.nextTurnDistance >= 0.0) {
            tx = label(renderer, " · turn ", tx, ty, shadow, scale);
            value(renderer, String.format("%.0f b", preview.nextTurnDistance), tx, ty, shadow, scale);
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    private double label(HudRenderer renderer, String s, double tx, double ty, boolean shadow, double scale) {
        renderer.text(s, tx, ty, labelColor.get(), shadow, scale);
        return tx + renderer.textWidth(s, shadow, scale);
    }

    private double value(HudRenderer renderer, String s, double tx, double ty, boolean shadow, double scale) {
        renderer.text(s, tx, ty, valueColor.get(), shadow, scale);
        return tx + renderer.textWidth(s, shadow, scale);
    }

    /** A little square spiral so the element can be judged and placed without a flight behind it. */
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
