package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.AutoFlyingRegear;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.util.LifetimeStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * The regear as a journey: where it is on the line, what it is carrying, how many times it has
 * done this and when it will have to again.
 *
 * <p>A regear is minutes of unattended work with a dozen ways to stall, and the question from
 * the other side of the room is not the raw state name but "how far along, and is it stuck".
 * So the big line says the station and its progress; a metro line under it draws the whole
 * journey with the current station lit; then the stocks with what this run has added, the
 * counters (this session, all time, last and average duration, and the estimated time to the
 * next one from the rate rockets are actually being spent), and the last thing that happened.
 *
 * <p>Idle, it folds to one muted line with the counters, or to nothing, as you prefer.
 * Everything drawn here is read from the module's own accounting; nothing is recomputed.
 */
public class RegearStatusHud extends HudElement {
    public static final HudElementInfo<RegearStatusHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "RegearStatus",
        "The regear's station on its line, stocks and gains, counters, next-regear estimate and last event.",
        RegearStatusHud::new);

    /** What the element shows while no regear is running. */
    public enum IdleView {
        HIDDEN,
        COMPACT,
        FULL
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<IdleView> idleView = sgGeneral.add(new EnumSetting.Builder<IdleView>()
        .name("idle-view")
        .description("Between regears: HIDDEN draws nothing, COMPACT one muted line with the counters and the next-regear estimate, FULL the whole panel.")
        .defaultValue(IdleView.COMPACT).build());

    private final Setting<Boolean> showLine = sgGeneral.add(new BoolSetting.Builder()
        .name("show-line").description("The metro line: one segment per station, the current one lit.")
        .defaultValue(true).build());

    private final Setting<Boolean> showSupplies = sgGeneral.add(new BoolSetting.Builder()
        .name("show-supplies").description("Rockets, valid elytras, gaps, totems (and bottles under REPAIR), with what this regear added.")
        .defaultValue(true).build());

    private final Setting<Boolean> showCounters = sgGeneral.add(new BoolSetting.Builder()
        .name("show-counters").description("Regears this session and all time, last and average duration, estimated time to the next.")
        .defaultValue(true).build());

    private final Setting<Boolean> showEvents = sgGeneral.add(new BoolSetting.Builder()
        .name("show-events").description("The last thing the regear did or ran into, fading out after a while.")
        .defaultValue(true).build());

    private final Setting<Integer> eventFade = sgGeneral.add(new IntSetting.Builder()
        .name("event-fade-seconds").description("How long the event line stays before fading out.")
        .defaultValue(45).min(5).max(600).sliderRange(5, 180).visible(showEvents::get).build());

    private final Setting<Integer> gainsHold = sgGeneral.add(new IntSetting.Builder()
        .name("gains-hold-seconds").description("How long after a regear the (+n) gains stay next to the stocks.")
        .defaultValue(120).min(0).max(3600).sliderRange(0, 600).visible(showSupplies::get).build());

    private final Setting<Integer> lowRockets = sgGeneral.add(new IntSetting.Builder()
        .name("low-rockets").description("Rocket count below this turns orange. Zero is always red.")
        .defaultValue(64).min(0).max(512).sliderRange(0, 256).build());

    private final Setting<Integer> lowElytras = sgGeneral.add(new IntSetting.Builder()
        .name("low-elytras").description("Valid elytra count below this turns orange. Zero is always red.")
        .defaultValue(2).min(0).max(20).sliderRange(0, 10).build());

    private final Setting<Integer> lowBottles = sgGeneral.add(new IntSetting.Builder()
        .name("low-bottles").description("Under REPAIR, bottle count below this turns orange.")
        .defaultValue(16).min(0).max(512).sliderRange(0, 128).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<Boolean> animations = sgGeneral.add(new BoolSetting.Builder()
        .name("animations")
        .description("Motion on change: the big line hops when the station changes, a gain warms its reading, the lit station breathes, a stall pulses red.")
        .defaultValue(true).build());

    private final Setting<SettingColor> busyColor = sgColors.add(new ColorSetting.Builder()
        .name("busy-color").description("The big line and the lit station during a regear.")
        .defaultValue(new SettingColor(90, 200, 255, 255)).build());

    private final Setting<SettingColor> mendColor = sgColors.add(new ColorSetting.Builder()
        .name("mend-color").description("The big line while mending, and the stations already passed.")
        .defaultValue(new SettingColor(110, 240, 150, 255)).build());

    private final Setting<SettingColor> warnColor = sgColors.add(new ColorSetting.Builder()
        .name("warn-color").description("Waiting over lava, stocks running low.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> badColor = sgColors.add(new ColorSetting.Builder()
        .name("bad-color").description("A stock at zero, a failed takeoff, a stalled phase.")
        .defaultValue(new SettingColor(255, 90, 90, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("Readings in normal range.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> mutedColor = sgColors.add(new ColorSetting.Builder()
        .name("muted-color").description("Labels, separators, the counters and the idle line.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();
    private long lastFrameNanos;
    private double animTime;

    /** How much bigger the big line is than the rest. */
    private static final double STATE_SCALE = 1.3;
    /** A phase that has not changed station for this long is drawn as stalled. */
    private static final long STALL_MS = 45_000L;

    private AutoFlyingRegear.Step lastStep;
    private long stepSinceMs;

    public RegearStatusHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        long nanos = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.016 : Math.min(0.25, (nanos - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = nanos;
        animTime += dt;

        AutoFlyingRegear regear = Modules.get().get(AutoFlyingRegear.class);
        boolean live = MeteorClient.mc.player != null && MeteorClient.mc.world != null && regear != null && regear.isActive();

        if (!live) {
            if (isInEditor()) {
                renderPanel(renderer, "REGEAR #4 · ROCKETS 178", busyColor.get(), 6, 3,
                    new String[]{"rockets 178", "elytra 5", "gaps 32", "totems 4"},
                    new Color[]{valueColor.get(), valueColor.get(), valueColor.get(), valueColor.get()},
                    new String[]{"(+192)", "(+3)", "(+32)", "(+2)"},
                    "#4 session · 27 total · last 2m14 · avg 2m05 · next ≈ 38 min",
                    "took rocket shulker #2", 1.0f, HudGlowPanel.Severity.OK, 0.0f);
                return;
            }

            // Off means gone: an element announcing its own absence is one more thing on screen.
            setSize(0.0, 0.0);
            return;
        }

        boolean busy = regear.isBusy();
        boolean waiting = !busy && regear.hudWaitingForGround();
        long now = System.currentTimeMillis();

        AutoFlyingRegear.Step step = regear.hudStep();
        if (step != lastStep) {
            lastStep = step;
            stepSinceMs = now;
        }

        int rockets = regear.getRocketCount();
        int elytras = regear.getValidElytraCount();
        int gaps = regear.hudGapCount();
        int totems = regear.hudTotemCount();
        int bottles = regear.getBottleCount();
        boolean repair = regear.getElytraMode() == AutoFlyingRegear.ElytraMode.REPAIR;

        if (!busy && !waiting) {
            switch (idleView.get()) {
                case HIDDEN -> {
                    setSize(0.0, 0.0);
                    return;
                }
                case COMPACT -> {
                    renderCompact(renderer, regear, rockets, elytras, bottles, repair, now);
                    return;
                }
                case FULL -> {
                }
            }
        }

        // ---- the big line
        String stateText;
        SettingColor stateColor;
        boolean stalled = busy && now - stepSinceMs > STALL_MS && step != AutoFlyingRegear.Step.MEND;

        if (waiting) {
            stateText = "LOW · LAVA BELOW · WAITING " + regear.hudLavaWaitSeconds() + "s";
            stateColor = warnColor.get();
        } else if (busy) {
            int number = regear.hudSessionRegears() + 1;
            stateText = "REGEAR #" + number + " · " + regear.hudPhaseDetail();
            stateColor = stalled ? badColor.get() : step == AutoFlyingRegear.Step.MEND ? mendColor.get() : busyColor.get();
        } else {
            AutoFlyingRegear.Run last = regear.hudLastRun();
            boolean failed = last != null && last.outcome != null && !last.outcome.startsWith("complete");
            stateText = "REGEAR · READY · " + (repair ? "REPAIR" : "REPLACE") + (failed ? " · LAST " + last.outcome.toUpperCase(Locale.ROOT) : "");
            stateColor = failed ? badColor.get() : mutedColor.get();
        }

        // ---- the line of stations
        List<AutoFlyingRegear.Step> stations = new ArrayList<>();
        for (AutoFlyingRegear.Step s : AutoFlyingRegear.Step.values()) {
            if (regear.hudStepEnabled(s)) stations.add(s);
        }
        int current = step == null ? -1 : stations.indexOf(step);

        // ---- the stocks and what this run has added
        AutoFlyingRegear.Run run = regear.hudCurrentRun();
        AutoFlyingRegear.Run gainsFrom = run != null ? run
            : regear.hudLastRun() != null && now - regear.hudLastRun().endedMs < gainsHold.get() * 1000L ? regear.hudLastRun() : null;

        List<String> stockText = new ArrayList<>();
        List<Color> stockColor = new ArrayList<>();
        List<String> gainText = new ArrayList<>();

        stockText.add("rockets " + rockets);
        stockColor.add(stockColor(rockets, lowRockets.get()));
        gainText.add(gain(gainsFrom == null ? 0 : rockets - gainsFrom.rocketsBefore, gainsFrom != null));

        stockText.add("elytra " + elytras);
        stockColor.add(stockColor(elytras, lowElytras.get()));
        gainText.add(gain(gainsFrom == null ? 0 : elytras - gainsFrom.elytrasBefore, gainsFrom != null));

        if (regear.hudGoalGaps() > 0) {
            stockText.add("gaps " + gaps);
            stockColor.add(gaps == 0 ? warnColor.get() : valueColor.get());
            gainText.add(gain(gainsFrom == null ? 0 : gaps - gainsFrom.gapsBefore, gainsFrom != null));
        }

        if (regear.hudGoalTotems() > 0) {
            stockText.add("totems " + totems);
            stockColor.add(totems == 0 ? warnColor.get() : valueColor.get());
            gainText.add(gain(gainsFrom == null ? 0 : totems - gainsFrom.totemsBefore, gainsFrom != null));
        }

        if (repair) {
            stockText.add("xp " + bottles);
            stockColor.add(stockColor(bottles, lowBottles.get()));
            gainText.add(gain(gainsFrom == null ? 0 : bottles - gainsFrom.bottlesBefore, gainsFrom != null));
        }

        // ---- the counters
        String counters = counters(regear, rockets, now);

        // ---- the last event
        float eventAge = (float) Math.min(1.0, (now - regear.hudLastEventMs()) / (eventFade.get() * 1000.0));
        String event = regear.hudLastEvent();

        HudGlowPanel.Severity severity = elytras == 0 || rockets == 0 || (repair && bottles == 0) || stalled
            ? HudGlowPanel.Severity.CRITICAL
            : waiting || elytras < lowElytras.get() || rockets < lowRockets.get() || (repair && bottles < lowBottles.get())
                ? HudGlowPanel.Severity.WARN
                : HudGlowPanel.Severity.OK;

        renderPanel(renderer, stateText, stateColor, stations.size(), current,
            stockText.toArray(new String[0]), stockColor.toArray(new Color[0]), gainText.toArray(new String[0]),
            counters, event, 1.0f - eventAge, severity, stalled ? 1.0f : 0.0f);
    }

    /** The idle line: number, time since, estimate, mode. One line, muted, so it can stay on screen for hours. */
    private void renderCompact(HudRenderer renderer, AutoFlyingRegear regear, int rockets, int elytras, int bottles,
                               boolean repair, long now) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double inset = panel.padding();
        double height = renderer.textHeight(shadow, scale);

        AutoFlyingRegear.Run last = regear.hudLastRun();
        String ago = last == null ? "none yet" : formatSpan((now - last.endedMs) / 1000L) + " ago";
        String text = String.format(Locale.ROOT, "regear · #%d session · %d total · %s · next %s · %s",
            regear.hudSessionRegears(), LifetimeStats.get().totalRegears(), ago,
            nextEstimate(regear, rockets), repair ? "REPAIR" : "REPLACE");

        double width = Math.max(renderer.textWidth(text, shadow, scale),
            renderer.textWidth("regear · #000 session · 0000 total · 00h00 ago · next ≈ 00h00 · REPLACE", shadow, scale));

        HudGlowPanel.Severity severity = elytras == 0 || rockets == 0 || (repair && bottles == 0)
            ? HudGlowPanel.Severity.CRITICAL
            : elytras < lowElytras.get() || rockets < lowRockets.get() || (repair && bottles < lowBottles.get())
                ? HudGlowPanel.Severity.WARN
                : HudGlowPanel.Severity.OK;

        panel.draw(renderer, this.x + inset, this.y + inset, width, height, severity);
        SettingColor color = severity == HudGlowPanel.Severity.CRITICAL ? badColor.get()
            : severity == HudGlowPanel.Severity.WARN ? warnColor.get() : mutedColor.get();
        renderer.text(text, this.x + inset, this.y + inset, color, shadow, scale);
        setSize(width + inset * 2.0, height + inset * 2.0);
    }

    /**
     * Lays the panel out: big line, metro line, stocks, counters, event. Templates keep the width
     * still while the numbers move; the panel goes under first, since nothing here sorts by depth.
     */
    private void renderPanel(HudRenderer renderer, String stateText, SettingColor stateColor,
                             int stationCount, int currentStation,
                             String[] stocks, Color[] stockColors, String[] gains,
                             String counters, String event, float eventAlpha,
                             HudGlowPanel.Severity severity, float stallPulse) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        boolean anim = animations.get();
        double inset = panel.padding();
        double stateHeight = renderer.textHeight(shadow, scale * STATE_SCALE);
        double lineHeight = renderer.textHeight(shadow, scale);
        double gap = 2.0 * scale;
        double barHeight = 3.0 * scale;

        double width = Math.max(renderer.textWidth(stateText, shadow, scale * STATE_SCALE),
            renderer.textWidth("REGEAR #000 · XP BOTTLES 0000/0000", shadow, scale * STATE_SCALE));
        if (showSupplies.get()) {
            width = Math.max(width, stocksWidth(renderer, stocks, gains, shadow, scale));
        }
        if (showCounters.get()) {
            width = Math.max(width, Math.max(renderer.textWidth(counters, shadow, scale),
                renderer.textWidth("#000 session · 0000 total · last 00m00 · avg 00m00 · next ≈ 00h00", shadow, scale)));
        }
        boolean eventShown = showEvents.get() && event != null && !event.isEmpty() && eventAlpha > 0.02f;
        if (showEvents.get()) {
            width = Math.max(width, renderer.textWidth(eventShown ? event : "", shadow, scale));
        }

        double height = stateHeight
            + (showLine.get() ? barHeight + gap * 2.0 : 0.0)
            + (showSupplies.get() ? lineHeight + gap : 0.0)
            + (showCounters.get() ? lineHeight + gap : 0.0)
            + (showEvents.get() ? lineHeight + gap : 0.0);

        panel.draw(renderer, this.x + inset, this.y + inset, width, height, severity);

        double x0 = this.x + inset;
        double y = this.y + inset;

        // The big line hops once when the station changes and flashes toward white; a stall
        // breathes red on a slow beat, readable from across the room without being a strobe.
        float fresh = anim ? pulse.freshness("state", stateText, 900L) : 0.0f;
        Color stateDraw = HudPulse.tint(HudPulse.Style.Lively, stateColor, new SettingColor(255, 255, 255, 255), fresh);
        if (anim && stallPulse > 0.0f) {
            double breathe = 0.5 + 0.5 * Math.sin(animTime * Math.PI * 2.0 / 1.2);
            stateDraw = new Color(
                (int) (stateDraw.r + (255 - stateDraw.r) * 0.30 * breathe),
                (int) (stateDraw.g + (255 - stateDraw.g) * 0.30 * breathe),
                (int) (stateDraw.b + (255 - stateDraw.b) * 0.30 * breathe),
                stateDraw.a);
        }
        renderer.text(stateText, x0, y + HudPulse.bounce(HudPulse.Style.Lively, fresh, scale), stateDraw, shadow, scale * STATE_SCALE);
        y += stateHeight;

        if (showLine.get()) {
            y += gap;
            drawLine(renderer, x0, y, width, barHeight, stationCount, currentStation, anim, scale);
            y += barHeight + gap;
        }

        if (showSupplies.get()) {
            double x = x0;
            for (int i = 0; i < stocks.length; i++) {
                if (i > 0) {
                    renderer.text(" · ", x, y, mutedColor.get(), shadow, scale);
                    x += renderer.textWidth(" · ", shadow, scale);
                }

                // A gain warms its reading for a moment, so the restock is visible as it lands.
                Color c = HudPulse.tint(HudPulse.Style.Subtle, new SettingColor(stockColors[i].r, stockColors[i].g, stockColors[i].b, stockColors[i].a),
                    mendColor.get(), anim ? pulse.freshness("stock" + i, stocks[i], 800L) : 0.0f);
                renderer.text(stocks[i], x, y, c, shadow, scale);
                x += renderer.textWidth(stocks[i], shadow, scale);

                if (!gains[i].isEmpty()) {
                    String g = " " + gains[i];
                    renderer.text(g, x, y, gains[i].startsWith("(-") ? warnColor.get() : mendColor.get(), shadow, scale);
                    x += renderer.textWidth(g, shadow, scale);
                }
            }
            y += lineHeight + gap;
        }

        if (showCounters.get()) {
            renderer.text(counters, x0, y, mutedColor.get(), shadow, scale);
            y += lineHeight + gap;
        }

        if (eventShown) {
            SettingColor m = mutedColor.get();
            Color faded = new Color(m.r, m.g, m.b, (int) (m.a * Math.max(0.0f, Math.min(1.0f, eventAlpha))));
            renderer.text(event, x0, y, faded, shadow, scale);
        }

        setSize(width + inset * 2.0, height + inset * 2.0);
    }

    /**
     * The metro line: one segment per station, passed ones in the mend green, the current one in
     * the busy blue and breathing, the rest dim. Idle, the whole line is dim.
     */
    private void drawLine(HudRenderer renderer, double x, double y, double width, double height,
                          int stations, int current, boolean anim, double scale) {
        if (stations <= 0) return;

        double spacing = 2.0 * scale;
        double segment = (width - spacing * (stations - 1)) / stations;
        SettingColor done = mendColor.get();
        SettingColor lit = busyColor.get();
        SettingColor dim = mutedColor.get();
        double breathe = anim ? 0.65 + 0.35 * Math.sin(animTime * Math.PI * 2.0 / 1.6) : 1.0;

        for (int i = 0; i < stations; i++) {
            Color color;
            if (current >= 0 && i < current) {
                color = new Color(done.r, done.g, done.b, (int) (done.a * 0.85));
            } else if (i == current) {
                color = new Color(lit.r, lit.g, lit.b, (int) (lit.a * breathe));
            } else {
                color = new Color(dim.r, dim.g, dim.b, (int) (dim.a * 0.35));
            }

            renderer.quad(x + i * (segment + spacing), y, segment, height, color);
        }
    }

    private double stocksWidth(HudRenderer renderer, String[] stocks, String[] gains, boolean shadow, double scale) {
        double w = 0.0;
        for (int i = 0; i < stocks.length; i++) {
            if (i > 0) w += renderer.textWidth(" · ", shadow, scale);
            w += renderer.textWidth(stocks[i], shadow, scale);
            if (!gains[i].isEmpty()) w += renderer.textWidth(" " + gains[i], shadow, scale);
        }

        // Worst case of the row as configured: four digits of rockets with a three-digit gain,
        // two of elytra, three of gaps, two of totems, four of bottles.
        double template = renderer.textWidth("rockets 0000 (+000) · elytra 00 (+0) · gaps 000 (+00) · totems 00 (+0) · xp 0000 (+000)", shadow, scale);
        return Math.max(w, template);
    }

    private String counters(AutoFlyingRegear regear, int rockets, long now) {
        AutoFlyingRegear.Run last = regear.hudLastRun();
        AutoFlyingRegear.Run run = regear.hudCurrentRun();
        String lastText = run != null ? "now " + formatSpan(run.durationMs() / 1000L)
            : last == null ? "last --" : "last " + formatSpan(last.durationMs() / 1000L);
        String avg = regear.hudSessionRegears() == 0 ? "--" : formatSpan(regear.hudAverageRunMs() / 1000L);

        return String.format(Locale.ROOT, "#%d session · %d total · %s · avg %s · next %s",
            regear.hudSessionRegears(), LifetimeStats.get().totalRegears(), lastText, avg, nextEstimate(regear, rockets));
    }

    /**
     * Time until rockets fall to the regear threshold at the rate they are actually being spent
     * since the last regear. Quantised so the last digit does not spin; "--" until there is enough
     * to go on.
     */
    private static String nextEstimate(AutoFlyingRegear regear, int rockets) {
        double perMinute = regear.hudRocketsPerMinute();
        if (perMinute <= 0.0) return "--";

        int left = rockets - regear.hudMinRockets();
        if (left <= 0) return "now";

        int minutes = (int) Math.round(left / perMinute);
        if (minutes >= 120) return String.format(Locale.ROOT, "≈ %dh%02d", minutes / 60, minutes % 60 / 5 * 5);
        if (minutes >= 20) return "≈ " + (minutes / 5 * 5) + " min";
        return "≈ " + Math.max(1, minutes) + " min";
    }

    private Color stockColor(int value, int low) {
        if (value == 0) return badColor.get();
        if (value < low) return warnColor.get();
        return valueColor.get();
    }

    /** "(+n)" for a gain, "(-n)" for a loss (a mending run spends bottles), nothing when there is no run to compare with. */
    private static String gain(int delta, boolean applicable) {
        if (!applicable || delta == 0) return "";
        return delta > 0 ? "(+" + delta + ")" : "(" + delta + ")";
    }

    /** 2m14 under an hour, 1h04 above, 38s under a minute. */
    private static String formatSpan(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return String.format(Locale.ROOT, "%dm%02d", seconds / 60, seconds % 60);
        return String.format(Locale.ROOT, "%dh%02d", seconds / 3600, seconds % 3600 / 60);
    }
}
