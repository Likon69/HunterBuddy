package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.AutoFlyingRegear;
import com.hunterbuddy.util.HudGlowPanel;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * What the regear is doing, and whether it is about to run out of what it needs.
 *
 * <p>A regear is a long unattended sequence, and the module's own line reported the raw state
 * name. This says the phase in words and, next to it, the three stocks that decide whether the
 * next one will even be possible.
 */
public class RegearStatusHud extends HudElement {
    public static final HudElementInfo<RegearStatusHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "RegearStatus",
        "AutoFlyingRegear phase, supplies and mending progress.",
        RegearStatusHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Boolean> hideWhenIdle = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-when-idle")
        .description("Show nothing while the regear is not running, so the screen stays clear in normal flight.")
        .defaultValue(true).build());

    private final Setting<Boolean> showSupplies = sgGeneral.add(new BoolSetting.Builder()
        .name("show-supplies")
        .description("Show bottles, rockets and valid elytras. The glance that says whether the bot is about to run dry.")
        .defaultValue(true).build());

    private final Setting<Boolean> showRepair = sgGeneral.add(new BoolSetting.Builder()
        .name("show-repair")
        .description("Show the mending progress of the elytra in hand while REPAIR is running.")
        .defaultValue(true).build());

    private final Setting<Integer> lowBottles = sgGeneral.add(new IntSetting.Builder()
        .name("low-bottles").description("Bottle count below this turns the number orange.")
        .defaultValue(16).min(0).max(512).sliderRange(0, 128).build());

    private final Setting<Integer> lowRockets = sgGeneral.add(new IntSetting.Builder()
        .name("low-rockets").description("Rocket count below this turns the number orange.")
        .defaultValue(64).min(0).max(512).sliderRange(0, 256).build());

    private final Setting<Integer> lowElytras = sgGeneral.add(new IntSetting.Builder()
        .name("low-elytras").description("Valid elytra count below this turns the number orange.")
        .defaultValue(2).min(0).max(20).sliderRange(0, 10).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("Units and separators.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> idleColor = sgColors.add(new ColorSetting.Builder()
        .name("idle-color").description("Phase colour while nothing is running.")
        .defaultValue(new SettingColor(140, 140, 140, 255)).build());

    private final Setting<SettingColor> busyColor = sgColors.add(new ColorSetting.Builder()
        .name("busy-color").description("Phase colour during the regear.")
        .defaultValue(new SettingColor(90, 200, 255, 255)).build());

    private final Setting<SettingColor> repairColor = sgColors.add(new ColorSetting.Builder()
        .name("repair-color").description("Phase colour while mending.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("Supply counts in normal range.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> warnColor = sgColors.add(new ColorSetting.Builder()
        .name("warn-color").description("Supply counts running low.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final SettingGroup sgPanel = settings.createGroup("Panel");
    private final HudGlowPanel panel = new HudGlowPanel(this.sgPanel);

    public RegearStatusHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double height = renderer.textHeight(shadow, scale);

        AutoFlyingRegear regear = Modules.get().get(AutoFlyingRegear.class);
        boolean busy = regear != null && MeteorClient.mc.player != null
            && regear.isActive() && regear.isBusy();
        double inset = panel.padding();

        // The editor always gets something to grab, panel included: placed bare it would be the
        // only one of the three not looking like the others while you position it.
        if (!busy && isInEditor()) {
            String placeholder = "RegearStatus (idle)";
            double w = renderer.textWidth(placeholder, shadow, scale);
            panel.draw(renderer, this.x + inset, this.y + inset, w, height, HudGlowPanel.Severity.OK);
            renderer.text(placeholder, this.x + inset, this.y + inset, idleColor.get(), shadow, scale);
            setSize(w + inset * 2.0, height + inset * 2.0);
            return;
        }

        if (!busy && hideWhenIdle.get()) {
            setSize(0.0, 0.0);
            return;
        }

        if (regear == null || MeteorClient.mc.player == null) {
            double w = renderer.textWidth("Idle", shadow, scale);
            panel.draw(renderer, this.x + inset, this.y + inset, w, height, HudGlowPanel.Severity.OK);
            renderer.text("Idle", this.x + inset, this.y + inset, idleColor.get(), shadow, scale);
            setSize(w + inset * 2.0, height + inset * 2.0);
            return;
        }

        int repairPct = showRepair.get() ? regear.getRepairPercent() : -1;
        int bottles = regear.getBottleCount();
        int rockets = regear.getRocketCount();
        int elytras = regear.getValidElytraCount();
        double pad = 4.0 * scale;

        // Measured before drawing, since the panel goes underneath and the width is only known
        // once every field has been laid out.
        double contentWidth = measure(renderer, regear, repairPct, bottles, rockets, elytras,
            shadow, scale, pad);

        panel.draw(renderer, this.x + inset, this.y + inset, contentWidth, height,
            severityOf(regear, bottles, rockets, elytras));

        double x = this.x + inset;
        double y = this.y + inset;

        SettingColor phaseColor = repairPct >= 0 ? repairColor.get() : busyColor.get();
        x = draw(renderer, x, y, regear.getPhaseLabel(), phaseColor, shadow, scale);

        if (showSupplies.get()) {
            x = separator(renderer, x, y, pad, shadow, scale);
            x = drawCount(renderer, x, y, bottles, "xp", lowBottles.get(), shadow, scale);
            x = separator(renderer, x, y, pad, shadow, scale);
            x = drawCount(renderer, x, y, rockets, "r", lowRockets.get(), shadow, scale);
            x = separator(renderer, x, y, pad, shadow, scale);
            x = drawCount(renderer, x, y, elytras, "e", lowElytras.get(), shadow, scale);
        }

        if (repairPct >= 0) {
            x = separator(renderer, x, y, pad, shadow, scale);
            x = draw(renderer, x, y, String.valueOf(repairPct), repairColor.get(), shadow, scale);
            x = draw(renderer, x, y, "%", labelColor.get(), shadow, scale);
        }

        setSize(contentWidth + inset * 2.0, height + inset * 2.0);
    }

    /** Lays the line out without drawing it, to size the panel that goes under it. */
    private double measure(HudRenderer renderer, AutoFlyingRegear regear, int repairPct,
                           int bottles, int rockets, int elytras,
                           boolean shadow, double scale, double pad) {
        double sep = pad * 2 + renderer.textWidth("·", shadow, scale);
        double w = renderer.textWidth(regear.getPhaseLabel(), shadow, scale);

        if (showSupplies.get()) {
            w += sep + renderer.textWidth(bottles + "xp", shadow, scale);
            w += sep + renderer.textWidth(rockets + "r", shadow, scale);
            w += sep + renderer.textWidth(elytras + "e", shadow, scale);
        }

        if (repairPct >= 0) w += sep + renderer.textWidth(repairPct + "%", shadow, scale);

        return Math.max(w, 40.0);
    }

    /**
     * State of the line as a whole, for the halo.
     *
     * <p>A stock at zero is critical rather than merely low: it is the one that ends the run.
     * Experience bottles only count under REPAIR, where the mending spends them; under REPLACE
     * the regear never asks for one and a bag without them is perfectly normal.
     */
    private HudGlowPanel.Severity severityOf(AutoFlyingRegear regear,
                                             int bottles, int rockets, int elytras) {
        boolean repairing = regear.getElytraMode() == AutoFlyingRegear.ElytraMode.REPAIR;

        if (elytras == 0 || rockets == 0 || (repairing && bottles == 0)) {
            return HudGlowPanel.Severity.CRITICAL;
        }

        if (elytras < lowElytras.get() || rockets < lowRockets.get()
            || (repairing && bottles < lowBottles.get())) {
            return HudGlowPanel.Severity.WARN;
        }

        return HudGlowPanel.Severity.OK;
    }

    private double draw(HudRenderer renderer, double x, double y, String text, SettingColor color,
                        boolean shadow, double scale) {
        renderer.text(text, x, y, color, shadow, scale);
        return x + renderer.textWidth(text, shadow, scale);
    }

    /** Count in its own colour, unit in grey, orange once the stock is running low. */
    private double drawCount(HudRenderer renderer, double x, double y, int value, String unit,
                             int threshold, boolean shadow, double scale) {
        SettingColor color = value < threshold ? warnColor.get() : valueColor.get();
        x = draw(renderer, x, y, String.valueOf(value), color, shadow, scale);
        return draw(renderer, x, y, unit, labelColor.get(), shadow, scale);
    }

    private double separator(HudRenderer renderer, double x, double y, double pad,
                             boolean shadow, double scale) {
        x += pad;
        renderer.text("·", x, y, labelColor.get(), shadow, scale);
        return x + renderer.textWidth("·", shadow, scale) + pad;
    }
}
