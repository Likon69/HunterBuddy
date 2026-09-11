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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * The regear as a journey: an identity badge on the far left saying what it is, a big line
 * saying where it is on the line and whether it is stuck, a metro spine drawing the whole route
 * with the current station lit, and — at the full density — the stocks it is carrying with what
 * this run added, the counters, and the last event.
 *
 * <p>Two knobs shape it. {@code density} chooses how many rows exist; {@code size} scales the
 * whole thing — every node, rail, bar, gap and icon, and the base text scale — so the entire
 * element, metro line and all, shrinks or grows as one. {@code text-scale} is a fine trim on the
 * text on top of that.
 *
 * <p>The mode is never a trailing data word: it lives in the leading badge (a drawn elytra plus
 * an R/P tag, and an xp bottle under REPAIR), or — idle — as a labelled tail on the ledger line.
 * Everything drawn here is read from the module's own accounting; nothing is recomputed.
 */
public class RegearStatusHud extends HudElement {
    public static final HudElementInfo<RegearStatusHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "RegearStatus",
        "The regear's identity badge, its station on its line, stocks and gains, counters, next-regear estimate and last event.",
        RegearStatusHud::new);

    /** How many rows the panel carries while a regear is running. */
    public enum Density {
        MINIMAL,
        COMPACT,
        FULL
    }

    /** What the element shows while no regear is running. */
    public enum IdleView {
        HIDDEN,
        LEDGER,
        FULL
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Density> density = sgGeneral.add(new EnumSetting.Builder<Density>()
        .name("density")
        .description("MINIMAL is the badge and the big line only; COMPACT adds the metro spine and one merged status row; FULL adds the stocks, counters and event rows.")
        .defaultValue(Density.COMPACT).build());

    private final Setting<Double> size = sgGeneral.add(new DoubleSetting.Builder()
        .name("size")
        .description("Master scale for the whole element: every node, rail, bar, gap and icon, and the base text scale. The knob that makes the whole thing — metro line and bars included — smaller or larger as one.")
        .defaultValue(1.0).min(0.6).max(2.0).sliderRange(0.6, 2.0).build());

    private final Setting<Double> iconSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("icon-size")
        .description("How big the item icons are - the badge, the stocks, the next-regear icon and the metro stations - on top of size. 1 is the old look; the rows and the metro line grow with them.")
        .defaultValue(1.5).min(0.8).max(2.5).sliderRange(0.8, 2.5).build());

    private final Setting<IdleView> idleView = sgGeneral.add(new EnumSetting.Builder<IdleView>()
        .name("idle-view")
        .description("Between regears: HIDDEN draws nothing, LEDGER one muted line with the counters, next-regear estimate and the mode as a labelled tail, FULL the whole panel.")
        .defaultValue(IdleView.LEDGER).build());

    private final Setting<Boolean> showSpineIcons = sgGeneral.add(new BoolSetting.Builder()
        .name("show-spine-icons")
        .description("At FULL, draw the supply stations (elytra, gaps, totems, rockets, mend) as their item icon instead of a dot. Ignored below size 0.75, where every node is a dot.")
        .defaultValue(true).build());

    private final Setting<Boolean> showSupplies = sgGeneral.add(new BoolSetting.Builder()
        .name("show-supplies").description("FULL: the stocks row — rockets, valid elytras, gaps, totems, ender chests, obsidian (and bottles under REPAIR), with what this regear added.")
        .defaultValue(true).build());

    private final Setting<Boolean> showStockIcons = sgGeneral.add(new BoolSetting.Builder()
        .name("show-stock-icons").description("Draw an item icon before each stock. Off falls back to a short text label (rkt/ely/gap/tot/ech/obs/xp).")
        .defaultValue(true).visible(showSupplies::get).build());

    private final Setting<Boolean> showCounters = sgGeneral.add(new BoolSetting.Builder()
        .name("show-counters").description("FULL: regears this session and all time, current or last duration, average, and the estimated time to the next.")
        .defaultValue(true).build());

    private final Setting<Boolean> showEvents = sgGeneral.add(new BoolSetting.Builder()
        .name("show-events").description("FULL: the last thing the regear did or ran into, fading out after a while.")
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
        .name("text-scale").description("A fine trim on the text, multiplied on top of size (effective text scale = size × text-scale).")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<Boolean> animations = sgGeneral.add(new BoolSetting.Builder()
        .name("animations")
        .description("Motion on change: the big line hops when the station changes, a gain warms its reading, the lit station breathes, a stall pulses red.")
        .defaultValue(true).build());

    private final Setting<SettingColor> busyColor = sgColors.add(new ColorSetting.Builder()
        .name("busy-color").description("The big line and the lit station during a regear, and the REPLACE badge tag.")
        .defaultValue(new SettingColor(90, 200, 255, 255)).build());

    private final Setting<SettingColor> mendColor = sgColors.add(new ColorSetting.Builder()
        .name("mend-color").description("The big line while mending, the stations already passed, the progress fill and the warmth of a landing gain.")
        .defaultValue(new SettingColor(110, 240, 150, 255)).build());

    private final Setting<SettingColor> warnColor = sgColors.add(new ColorSetting.Builder()
        .name("warn-color").description("Waiting over lava, stocks running low, a regear due soon.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> badColor = sgColors.add(new ColorSetting.Builder()
        .name("bad-color").description("A stock at zero, a failed takeoff, a stalled phase.")
        .defaultValue(new SettingColor(255, 90, 90, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("Readings in normal range.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> mutedColor = sgColors.add(new ColorSetting.Builder()
        .name("muted-color").description("Labels, separators, the counters, the idle ledger and the upcoming stations.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();
    private long lastFrameNanos;
    private double animTime;

    /** How much bigger the big line is than the rest, at COMPACT and FULL. */
    private static final double STATE_SCALE = 1.3;
    /** A phase that has not changed station for this long is drawn as stalled. */
    private static final long STALL_MS = 45_000L;
    /** The worst-case big line, so its width never jitters as the phase text moves. */
    private static final String STATE_TEMPLATE = "REGEAR #000 · XP BOTTLES 000/000";
    /** Below this size the spine draws dots only, no sprites. */
    private static final double SPINE_ICON_SIZE = 0.75;

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
                renderEditorDemo(renderer);
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

        if (!busy && !waiting) {
            switch (idleView.get()) {
                case HIDDEN -> {
                    setSize(0.0, 0.0);
                    return;
                }
                case LEDGER -> {
                    renderLedger(renderer, regear, now);
                    return;
                }
                case FULL -> {
                    // Fall through to the full panel, drawn against the idle state.
                }
            }
        }

        renderPanel(renderer, regear, busy, waiting, step, now);
    }

    // ---------------------------------------------------------------- the panel

    private void renderPanel(HudRenderer renderer, AutoFlyingRegear regear,
                             boolean busy, boolean waiting, AutoFlyingRegear.Step step, long now) {
        boolean shadow = textShadow.get();
        double s = size.get();
        double ts = s * textScale.get();
        boolean anim = animations.get();
        double inset = panel.padding();
        Density d = density.get();

        int rockets = regear.getRocketCount();
        int elytras = regear.getValidElytraCount();
        int gaps = regear.hudGapCount();
        int totems = regear.hudTotemCount();
        int bottles = regear.getBottleCount();
        boolean repair = regear.getElytraMode() == AutoFlyingRegear.ElytraMode.REPAIR;

        boolean stalled = busy && now - stepSinceMs > STALL_MS && step != AutoFlyingRegear.Step.MEND;

        // ---- the big line
        String stateText;
        SettingColor stateColor;
        if (waiting) {
            stateText = "LAVA BELOW · WAITING " + regear.hudLavaWaitSeconds() + "s";
            stateColor = warnColor.get();
        } else if (busy) {
            stateText = "REGEAR #" + (regear.hudSessionRegears() + 1) + " · " + regear.hudPhaseDetail();
            stateColor = stalled ? badColor.get() : step == AutoFlyingRegear.Step.MEND ? mendColor.get() : busyColor.get();
        } else {
            AutoFlyingRegear.Run last = regear.hudLastRun();
            boolean failed = last != null && last.outcome != null && !last.outcome.startsWith("complete");
            stateText = failed ? "REGEAR · " + last.outcome.toUpperCase(Locale.ROOT) : "REGEAR · READY";
            stateColor = failed ? badColor.get() : mutedColor.get();
        }

        double stateMul = d == Density.MINIMAL ? 1.0 : STATE_SCALE;

        // ---- geometry
        double gap = 3.0 * s;
        double badgeIcon = 13.0 * s * iconSize.get();
        double ico = 11.0 * s * iconSize.get();
        double lineH = renderer.textHeight(shadow, ts);
        double stateH = renderer.textHeight(shadow, ts * stateMul);
        double rowH = Math.max(lineH, ico);
        double stateRowH = Math.max(stateH, badgeIcon);

        boolean showSpine = d != Density.MINIMAL;
        boolean spineIcons = d == Density.FULL && showSpineIcons.get() && s >= SPINE_ICON_SIZE;
        boolean mergedRow = d == Density.COMPACT;
        boolean stocksRow = d == Density.FULL && showSupplies.get();
        boolean countersRow = d == Density.FULL && showCounters.get();
        boolean eventRow = d == Density.FULL && showEvents.get();

        // ---- stations
        List<AutoFlyingRegear.Step> stations = new ArrayList<>();
        for (AutoFlyingRegear.Step st : AutoFlyingRegear.Step.values()) {
            if (regear.hudStepEnabled(st)) stations.add(st);
        }
        int current = step == null ? -1 : stations.indexOf(step);

        // ---- stocks and the run whose gains are still held
        AutoFlyingRegear.Run run = regear.hudCurrentRun();
        AutoFlyingRegear.Run gainsFrom = run != null ? run
            : regear.hudLastRun() != null && now - regear.hudLastRun().endedMs < gainsHold.get() * 1000L ? regear.hudLastRun() : null;
        List<Stock> stocks = stocksRow ? buildStocks(regear, rockets, elytras, gaps, totems, bottles, repair, gainsFrom) : List.of();

        // ---- width, majored throughout so nothing jitters as the numbers move
        double badgeW = badgeWidth(renderer, repair, shadow, ts, badgeIcon, s);
        double stateW = Math.max(renderer.textWidth(stateText, shadow, ts * stateMul),
            renderer.textWidth(STATE_TEMPLATE, shadow, ts * stateMul));
        double width = badgeW + gap + stateW;

        if (mergedRow) width = Math.max(width, mergedWidth(renderer, regear, rockets, elytras, gaps, totems, shadow, ts, ico, s));
        if (stocksRow) width = Math.max(width, stocksWidth(renderer, stocks, shadow, ts, ico, s));
        if (countersRow) width = Math.max(width, countersWidth(renderer, shadow, ts, ico, s));
        if (eventRow) {
            String event = regear.hudLastEvent();
            width = Math.max(width, renderer.textWidth(event == null ? "" : event, shadow, ts));
        }
        if (showSpine) width = Math.max(width, Math.max(72.0 * s, spineMinWidth(stations.size(), s, spineIcons)));

        // ---- height
        double labelH = renderer.textHeight(shadow, ts * 0.6);
        double spineH = showSpine ? 2.0 * spineHalf(s, spineIcons) + labelH + 1.0 * s : 0.0;
        double height = stateRowH
            + (showSpine ? gap + spineH : 0.0)
            + (mergedRow ? gap + rowH : 0.0)
            + (stocksRow ? gap + rowH : 0.0)
            + (countersRow ? gap + rowH : 0.0)
            + (eventRow ? gap + lineH : 0.0);

        // ---- severity
        int secsNext = regear.hudSecondsToNextRegear();
        boolean soon = secsNext >= 0 && secsNext < 300;
        HudGlowPanel.Severity severity = elytras == 0 || rockets == 0 || (repair && bottles == 0) || stalled
            ? HudGlowPanel.Severity.CRITICAL
            : waiting || soon || elytras < lowElytras.get() || rockets < lowRockets.get() || (repair && bottles < lowBottles.get())
                ? HudGlowPanel.Severity.WARN
                : HudGlowPanel.Severity.OK;

        panel.draw(renderer, this.x + inset, this.y + inset, width, height, severity);

        double x0 = this.x + inset;
        double y = this.y + inset;

        // ---- row: badge + big line
        drawBadge(renderer, x0, y, stateRowH, repair, shadow, ts, badgeIcon, s);

        float fresh = anim ? pulse.freshness("state", stateText, 900L) : 0.0f;
        Color stateDraw = HudPulse.tint(HudPulse.Style.Lively, stateColor, new SettingColor(255, 255, 255, 255), fresh);
        if (anim && stalled) {
            double breathe = 0.5 + 0.5 * Math.sin(animTime * Math.PI * 2.0 / 1.2);
            stateDraw = new Color(
                (int) (stateDraw.r + (255 - stateDraw.r) * 0.30 * breathe),
                (int) (stateDraw.g + (255 - stateDraw.g) * 0.30 * breathe),
                (int) (stateDraw.b + (255 - stateDraw.b) * 0.30 * breathe),
                stateDraw.a);
        }
        double stateTextY = y + (stateRowH - stateH) / 2.0 + HudPulse.bounce(HudPulse.Style.Lively, fresh, ts);
        renderer.text(stateText, x0 + badgeW + gap, stateTextY, stateDraw, shadow, ts * stateMul);
        y += stateRowH;

        // ---- row: metro spine
        if (showSpine) {
            y += gap;
            drawSpine(renderer, x0, y, width, stations, current, anim, s, ts, spineIcons, shadow);
            y += spineH;
        }

        // ---- row: merged status (COMPACT)
        if (mergedRow) {
            y += gap;
            drawMerged(renderer, x0, y, rowH, regear, rockets, elytras, gaps, totems, anim, shadow, ts, ico, s);
            y += rowH;
        }

        // ---- row: stocks (FULL)
        if (stocksRow) {
            y += gap;
            drawStocks(renderer, x0, y, rowH, stocks, anim, shadow, ts, ico, s);
            y += rowH;
        }

        // ---- row: counters (FULL)
        if (countersRow) {
            y += gap;
            drawCounters(renderer, x0, y, rowH, regear, now, shadow, ts, ico, s);
            y += rowH;
        }

        // ---- row: event (FULL)
        if (eventRow) {
            y += gap;
            String event = regear.hudLastEvent();
            float eventAge = (float) Math.min(1.0, (now - regear.hudLastEventMs()) / (eventFade.get() * 1000.0));
            float alpha = 1.0f - eventAge;
            if (event != null && !event.isEmpty() && alpha > 0.02f) {
                SettingColor m = mutedColor.get();
                renderer.text(event, x0, y, new Color(m.r, m.g, m.b, (int) (m.a * alpha)), shadow, ts);
            }
        }

        setSize(width + inset * 2.0, height + inset * 2.0);
    }

    // ---------------------------------------------------------------- the ledger (idle)

    /** One muted line: number, totals, last duration, next estimate, and the mode as a labelled tail. */
    private void renderLedger(HudRenderer renderer, AutoFlyingRegear regear, long now) {
        boolean shadow = textShadow.get();
        double s = size.get();
        double ts = s * textScale.get();
        double ico = 11.0 * s * iconSize.get();
        double inset = panel.padding();
        double lineH = renderer.textHeight(shadow, ts);
        double rowH = Math.max(lineH, ico);

        int rockets = regear.getRocketCount();
        int elytras = regear.getValidElytraCount();
        int bottles = regear.getBottleCount();
        boolean repair = regear.getElytraMode() == AutoFlyingRegear.ElytraMode.REPAIR;

        AutoFlyingRegear.Run last = regear.hudLastRun();
        // Minute resolution on purpose: with seconds the "ago" ticked every frame, and since the
        // panel now hugs its text (no fixed template) that would have jittered its right edge.
        String lastText;
        if (last == null) {
            lastText = "none yet";
        } else {
            long agoS = (now - last.endedMs) / 1000L;
            lastText = (agoS < 60 ? "<1m" : agoS < 3600 ? (agoS / 60) + "m"
                : (agoS / 3600) + "h" + String.format(Locale.ROOT, "%02d", (agoS % 3600) / 60)) + " ago";
        }

        // Segment layout so the bound icon can sit inline; the tail says "mode X", never a bare word.
        // The session counter is dropped until there has actually been one this session — "#0" read
        // like the id of a regear that never happened.
        int sessionCount = regear.hudSessionRegears();
        String sessionPart = sessionCount > 0 ? "#" + sessionCount + " · " : "";
        String head = String.format(Locale.ROOT, "regear · %s%d total · last %s · ",
            sessionPart, LifetimeStats.get().totalRegears(), lastText);
        // A colour with no number is useless, so a stock under its threshold is named and counted
        // here — normally nothing, only what is actually low. Kept quiet when everything is fine.
        StringBuilder low = new StringBuilder();
        if (rockets < lowRockets.get()) low.append(" · rkt ").append(rockets);
        if (elytras < lowElytras.get()) low.append(" · ely ").append(elytras);
        if (repair && bottles < lowBottles.get()) low.append(" · xp ").append(bottles);
        if (regear.hudGoalGaps() > 0 && regear.hudGapCount() < regear.hudGoalGaps()) low.append(" · gap ").append(regear.hudGapCount());
        if (regear.hudGoalTotems() > 0 && regear.hudTotemCount() < regear.hudGoalTotems()) low.append(" · tot ").append(regear.hudTotemCount());
        if (regear.hudEnderChestCount() <= regear.hudKeepEnderChests()) low.append(" · ech ").append(regear.hudEnderChestCount());
        if (regear.hudObsidianCount() <= regear.hudObsidianRefillAt()) low.append(" · obs ").append(regear.hudObsidianCount());
        String tail = low + " · mode " + (repair ? "REPAIR" : "REPLACE");

        // Hug the text: measure the "next" segment exactly as drawNext will draw it (actual time
        // string, icon only when there is a bound), so nothing over-reserves and the idle line has
        // no empty tail. The panel only nudges when the numbers actually change, never per frame.
        String nextTime = nextTimeText(regear.hudSecondsToNextRegear());
        boolean nextHasIcon = boundStack(regear.hudNextRegearBound()) != null;
        double nextW = renderer.textWidth("next ", shadow, ts)
            + (nextHasIcon ? ico + 2.0 * s : 0.0)
            + renderer.textWidth(nextTime, shadow, ts);
        double width = renderer.textWidth(head, shadow, ts) + nextW + renderer.textWidth(tail, shadow, ts);

        boolean failed = last != null && last.outcome != null && !last.outcome.startsWith("complete");
        int secsNext = regear.hudSecondsToNextRegear();
        boolean soon = secsNext >= 0 && secsNext < 300;
        HudGlowPanel.Severity severity = elytras == 0 || rockets == 0 || (repair && bottles == 0) || failed
            ? HudGlowPanel.Severity.CRITICAL
            : soon || elytras < lowElytras.get() || rockets < lowRockets.get() || (repair && bottles < lowBottles.get())
                ? HudGlowPanel.Severity.WARN
                : HudGlowPanel.Severity.OK;

        panel.draw(renderer, this.x + inset, this.y + inset, width, rowH, severity);

        SettingColor color = severity == HudGlowPanel.Severity.CRITICAL ? badColor.get()
            : severity == HudGlowPanel.Severity.WARN ? warnColor.get() : mutedColor.get();

        double x0 = this.x + inset;
        double y = this.y + inset;
        double textY = y + (rowH - lineH) / 2.0;
        double centerY = y + rowH / 2.0;

        renderer.text(head, x0, textY, color, shadow, ts);
        double x = x0 + renderer.textWidth(head, shadow, ts);
        x = drawNext(renderer, x, textY, centerY, regear, color, shadow, ts, ico, s);
        renderer.text(tail, x, textY, color, shadow, ts);

        setSize(width + inset * 2.0, rowH + inset * 2.0);
    }

    // ---------------------------------------------------------------- the badge

    private double badgeWidth(HudRenderer renderer, boolean repair, boolean shadow, double ts, double badgeIcon, double s) {
        double w = badgeIcon + 2.0 * s + renderer.textWidth("R", shadow, ts);
        if (repair) w += 2.0 * s + badgeIcon * 0.85;
        return w;
    }

    /** The identity mark: an elytra, a one-letter mode tag, and — under REPAIR — an xp bottle. */
    private void drawBadge(HudRenderer renderer, double x0, double y, double rowH, boolean repair,
                           boolean shadow, double ts, double badgeIcon, double s) {
        double centerY = y + rowH / 2.0;
        double x = x0;

        postItem(renderer, Items.ELYTRA.getDefaultStack(), x, centerY - badgeIcon / 2.0, badgeIcon);
        x += badgeIcon + 2.0 * s;

        String tag = repair ? "P" : "R";
        SettingColor tagColor = repair ? mendColor.get() : busyColor.get();
        double tagH = renderer.textHeight(shadow, ts);
        renderer.text(tag, x, centerY - tagH / 2.0, tagColor, shadow, ts);
        x += renderer.textWidth("R", shadow, ts);

        if (repair) {
            x += 2.0 * s;
            double xp = badgeIcon * 0.85;
            postItem(renderer, Items.EXPERIENCE_BOTTLE.getDefaultStack(), x, centerY - xp / 2.0, xp);
        }
    }

    // ---------------------------------------------------------------- the metro spine

    /**
     * The whole journey on one rail: passed stations in mend green, the current one lit and
     * breathing, the rest dim; supply stations drawn as their item when asked and big enough.
     */
    /** Half the height of the metro line's node row: a node, or a station icon at icon-size when that is bigger. */
    private double spineHalf(double s, boolean icons) {
        double nodeMax = 6.0 * s;
        return icons ? Math.max(nodeMax, nodeMax * 0.9 * iconSize.get()) : nodeMax;
    }

    /** The narrowest the metro line can be with its station icons side by side, not over each other. */
    private double spineMinWidth(int n, double s, boolean icons) {
        if (!icons || n <= 1) return 0.0;
        double half = spineHalf(s, true);
        return 2.0 * Math.max(7.0 * s, half + 1.0 * s) + (n - 1) * 2.0 * half;
    }

    private void drawSpine(HudRenderer renderer, double x0, double yTop, double width,
                           List<AutoFlyingRegear.Step> stations, int current, boolean anim,
                           double s, double ts, boolean icons, boolean shadow) {
        int n = stations.size();
        if (n <= 0) return;

        double nodeMax = 6.0 * s;
        // with icons the stations are as big as icon-size makes them, and the line makes room for them
        double half = spineHalf(s, icons);
        double yc = yTop + half;
        double pad = icons ? Math.max(7.0 * s, half + 1.0 * s) : nodeMax;
        double rail = 3.0 * s;

        double[] nx = new double[n];
        if (n == 1) {
            nx[0] = x0 + width / 2.0;
        } else {
            for (int i = 0; i < n; i++) nx[i] = x0 + pad + i * (width - 2.0 * pad) / (n - 1);
        }

        SettingColor dim = mutedColor.get();
        SettingColor busy = busyColor.get();
        SettingColor mend = mendColor.get();

        // The rail, dim end to end, then the ridden stretch behind the current node in mend.
        renderer.quad(nx[0], yc - rail / 2.0, nx[n - 1] - nx[0], rail, new Color(dim.r, dim.g, dim.b, (int) (dim.a * 0.35)));
        if (current > 0) {
            int c = Math.min(current, n - 1);
            renderer.quad(nx[0], yc - rail / 2.0, nx[c] - nx[0], rail, new Color(mend.r, mend.g, mend.b, (int) (mend.a * 0.85)));
        }

        double breathe = anim ? 0.65 + 0.35 * Math.sin(animTime * Math.PI * 2.0 / 1.6) : 1.0;

        for (int i = 0; i < n; i++) {
            AutoFlyingRegear.Step st = stations.get(i);
            boolean isCurrent = i == current;
            boolean passed = current >= 0 && i < current;
            ItemStack supply = icons ? supplyStack(st) : null;

            if (supply != null) {
                double isz = 2.0 * nodeMax * 0.9 * iconSize.get();
                postItem(renderer, supply, nx[i] - isz / 2.0, yc - isz / 2.0, isz);
                if (isCurrent) {
                    ring(renderer, nx[i], yc, half, 1.5 * s, new Color(busy.r, busy.g, busy.b, (int) (busy.a * breathe)));
                }
            } else if (passed) {
                disc(renderer, nx[i], yc, 3.0 * s, new Color(mend.r, mend.g, mend.b, (int) (mend.a * 0.85)));
            } else if (isCurrent) {
                disc(renderer, nx[i], yc, 4.5 * s, busy);
                ring(renderer, nx[i], yc, nodeMax, 1.5 * s, new Color(busy.r, busy.g, busy.b, (int) (busy.a * breathe)));
            } else {
                ring(renderer, nx[i], yc, 3.0 * s, Math.max(1.0, 1.0 * s), new Color(dim.r, dim.g, dim.b, (int) (dim.a * 0.5)));
            }

            if (st == AutoFlyingRegear.Step.LAND || st == AutoFlyingRegear.Step.FLY) {
                String lbl = st == AutoFlyingRegear.Step.LAND ? "LAND" : "FLY";
                double lts = ts * 0.6;
                double lw = renderer.textWidth(lbl, shadow, lts);
                renderer.text(lbl, nx[i] - lw / 2.0, yc + half + 1.0 * s,
                    new Color(dim.r, dim.g, dim.b, (int) (dim.a * 0.8)), shadow, lts);
            }
        }
    }

    private static ItemStack supplyStack(AutoFlyingRegear.Step st) {
        return switch (st) {
            case ELYTRA -> Items.ELYTRA.getDefaultStack();
            case GAPS -> Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack();
            case TOTEMS -> Items.TOTEM_OF_UNDYING.getDefaultStack();
            case ROCKETS -> Items.FIREWORK_ROCKET.getDefaultStack();
            case MEND -> Items.EXPERIENCE_BOTTLE.getDefaultStack();
            default -> null;
        };
    }

    // ---------------------------------------------------------------- the merged status row (COMPACT)

    private record Field(String label, String value, String template, SettingColor color) {}

    private List<Field> mergedFields(AutoFlyingRegear regear, int rockets, int elytras, int gaps, int totems) {
        List<Field> f = new ArrayList<>();
        f.add(new Field("rkt ", String.valueOf(rockets), "0000", stockColor(rockets, lowRockets.get())));
        f.add(new Field("ely ", String.valueOf(elytras), "00", stockColor(elytras, lowElytras.get())));
        if (regear.hudGoalGaps() > 0) f.add(new Field("gap ", String.valueOf(gaps), "000", gaps == 0 ? warnColor.get() : valueColor.get()));
        if (regear.hudGoalTotems() > 0) f.add(new Field("tot ", String.valueOf(totems), "00", totems == 0 ? warnColor.get() : valueColor.get()));
        // orange at or under the ones the refill keeps, and at or under the refill threshold; red at none
        int echests = regear.hudEnderChestCount();
        f.add(new Field("ech ", String.valueOf(echests), "00", stockColor(echests, regear.hudKeepEnderChests() + 1)));
        if (regear.hudObsidianRefillAt() >= 0) {
            int obsidian = regear.hudObsidianCount();
            f.add(new Field("obs ", String.valueOf(obsidian), "000", stockColor(obsidian, regear.hudObsidianRefillAt() + 1)));
        }
        return f;
    }

    private double mergedWidth(HudRenderer renderer, AutoFlyingRegear regear, int rockets, int elytras, int gaps, int totems,
                               boolean shadow, double ts, double ico, double s) {
        List<Field> fields = mergedFields(regear, rockets, elytras, gaps, totems);
        double sep = renderer.textWidth(" · ", shadow, ts);
        double w = 0.0;
        for (Field f : fields) {
            w += renderer.textWidth(f.label, shadow, ts)
                + Math.max(renderer.textWidth(f.value, shadow, ts), renderer.textWidth(f.template, shadow, ts));
        }
        w += fields.size() * sep;
        return w + nextWidth(renderer, shadow, ts, ico, s);
    }

    private void drawMerged(HudRenderer renderer, double x0, double y, double rowH, AutoFlyingRegear regear,
                            int rockets, int elytras, int gaps, int totems, boolean anim,
                            boolean shadow, double ts, double ico, double s) {
        double textY = y + (rowH - renderer.textHeight(shadow, ts)) / 2.0;
        double centerY = y + rowH / 2.0;
        List<Field> fields = mergedFields(regear, rockets, elytras, gaps, totems);
        double x = x0;

        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            if (i > 0) x = sep(renderer, x, textY, shadow, ts);
            renderer.text(f.label, x, textY, mutedColor.get(), shadow, ts);
            x += renderer.textWidth(f.label, shadow, ts);
            float fresh = anim ? pulse.freshness("m" + f.label, f.value, 800L) : 0.0f;
            renderer.text(f.value, x, textY, HudPulse.tint(HudPulse.Style.Subtle, f.color, mendColor.get(), fresh), shadow, ts);
            x += renderer.textWidth(f.value, shadow, ts);
        }

        x = sep(renderer, x, textY, shadow, ts);
        drawNext(renderer, x, textY, centerY, regear, mutedColor.get(), shadow, ts, ico, s);
    }

    // ---------------------------------------------------------------- the stocks row (FULL)

    private record Stock(String label, ItemStack stack, String value, String template, SettingColor color, String gain) {}

    private List<Stock> buildStocks(AutoFlyingRegear regear, int rockets, int elytras, int gaps, int totems, int bottles,
                                    boolean repair, AutoFlyingRegear.Run gainsFrom) {
        boolean g = gainsFrom != null;
        // A finished run freezes its gain at what it actually added (after - before); only a run
        // still in progress reads against the live count, because its *After fields are set at
        // endRun. Reading the held gain against the live count made it drift as the player flew.
        boolean ended = g && gainsFrom.endedMs != 0L;
        List<Stock> list = new ArrayList<>();
        list.add(new Stock("rkt", Items.FIREWORK_ROCKET.getDefaultStack(), String.valueOf(rockets), "0000",
            stockColor(rockets, lowRockets.get()), gain(g ? (ended ? gainsFrom.rocketsAfter : rockets) - gainsFrom.rocketsBefore : 0, g)));
        list.add(new Stock("ely", Items.ELYTRA.getDefaultStack(), String.valueOf(elytras), "00",
            stockColor(elytras, lowElytras.get()), gain(g ? (ended ? gainsFrom.elytrasAfter : elytras) - gainsFrom.elytrasBefore : 0, g)));
        if (regear.hudGoalGaps() > 0) {
            list.add(new Stock("gap", Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack(), String.valueOf(gaps), "000",
                gaps == 0 ? warnColor.get() : valueColor.get(), gain(g ? (ended ? gainsFrom.gapsAfter : gaps) - gainsFrom.gapsBefore : 0, g)));
        }
        if (regear.hudGoalTotems() > 0) {
            list.add(new Stock("tot", Items.TOTEM_OF_UNDYING.getDefaultStack(), String.valueOf(totems), "00",
                totems == 0 ? warnColor.get() : valueColor.get(), gain(g ? (ended ? gainsFrom.totemsAfter : totems) - gainsFrom.totemsBefore : 0, g)));
        }
        // orange at or under the ones the refill keeps, and at or under the refill threshold; red at none
        int echests = regear.hudEnderChestCount();
        list.add(new Stock("ech", Items.ENDER_CHEST.getDefaultStack(), String.valueOf(echests), "00",
            stockColor(echests, regear.hudKeepEnderChests() + 1),
            gain(g ? (ended ? gainsFrom.enderChestsAfter : echests) - gainsFrom.enderChestsBefore : 0, g)));
        if (regear.hudObsidianRefillAt() >= 0) {
            int obsidian = regear.hudObsidianCount();
            list.add(new Stock("obs", Items.OBSIDIAN.getDefaultStack(), String.valueOf(obsidian), "000",
                stockColor(obsidian, regear.hudObsidianRefillAt() + 1),
                gain(g ? (ended ? gainsFrom.obsidianAfter : obsidian) - gainsFrom.obsidianBefore : 0, g)));
        }
        if (repair) {
            list.add(new Stock("xp", Items.EXPERIENCE_BOTTLE.getDefaultStack(), String.valueOf(bottles), "0000",
                stockColor(bottles, lowBottles.get()), gain(g ? (ended ? gainsFrom.bottlesAfter : bottles) - gainsFrom.bottlesBefore : 0, g)));
        }
        return list;
    }

    private double stockLead(HudRenderer renderer, Stock st, boolean shadow, double ts, double ico, double s) {
        return showStockIcons.get() ? ico + 2.0 * s : renderer.textWidth(st.label + " ", shadow, ts);
    }

    private double stocksWidth(HudRenderer renderer, List<Stock> stocks, boolean shadow, double ts, double ico, double s) {
        double sep = renderer.textWidth(" · ", shadow, ts);
        double gainReserve = renderer.textWidth(" (+0000)", shadow, ts);
        double w = 0.0;
        for (int i = 0; i < stocks.size(); i++) {
            if (i > 0) w += sep;
            Stock st = stocks.get(i);
            w += stockLead(renderer, st, shadow, ts, ico, s)
                + Math.max(renderer.textWidth(st.value, shadow, ts), renderer.textWidth(st.template, shadow, ts))
                + gainReserve;
        }
        return w;
    }

    private void drawStocks(HudRenderer renderer, double x0, double y, double rowH, List<Stock> stocks, boolean anim,
                            boolean shadow, double ts, double ico, double s) {
        double textY = y + (rowH - renderer.textHeight(shadow, ts)) / 2.0;
        double centerY = y + rowH / 2.0;
        double x = x0;

        for (int i = 0; i < stocks.size(); i++) {
            Stock st = stocks.get(i);
            if (i > 0) x = sep(renderer, x, textY, shadow, ts);

            float fresh = anim ? pulse.freshness("stock" + st.label, st.value, 800L) : 0.0f;

            if (showStockIcons.get()) {
                double sz = anim ? ico * HudPulse.pop(fresh) : ico;
                postItem(renderer, st.stack, x + (ico - sz) / 2.0, centerY - sz / 2.0, sz);
                x += ico + 2.0 * s;
            } else {
                renderer.text(st.label + " ", x, textY, mutedColor.get(), shadow, ts);
                x += renderer.textWidth(st.label + " ", shadow, ts);
            }

            renderer.text(st.value, x, textY, HudPulse.tint(HudPulse.Style.Subtle, st.color, mendColor.get(), fresh), shadow, ts);
            x += renderer.textWidth(st.value, shadow, ts);

            if (!st.gain.isEmpty()) {
                String g = " " + st.gain;
                renderer.text(g, x, textY, st.gain.startsWith("(-") ? warnColor.get() : mendColor.get(), shadow, ts);
                x += renderer.textWidth(g, shadow, ts);
            }
        }
    }

    // ---------------------------------------------------------------- the counters row (FULL)

    private String countersHead(AutoFlyingRegear regear, long now) {
        AutoFlyingRegear.Run run = regear.hudCurrentRun();
        AutoFlyingRegear.Run last = regear.hudLastRun();
        String nowLast = run != null ? "now " + formatSpan(run.durationMs() / 1000L)
            : last == null ? "last --" : "last " + formatSpan(last.durationMs() / 1000L);
        int sessionCount = regear.hudSessionRegears();
        String avg = sessionCount == 0 ? "--" : formatSpan(regear.hudAverageRunMs() / 1000L);
        String sessionPart = sessionCount > 0 ? "#" + sessionCount + " session · " : "";
        return String.format(Locale.ROOT, "%s%d total · %s · avg %s · ",
            sessionPart, LifetimeStats.get().totalRegears(), nowLast, avg);
    }

    private double countersWidth(HudRenderer renderer, boolean shadow, double ts, double ico, double s) {
        double head = renderer.textWidth("#000 session · 0000 total · last 00m00 · avg 00m00 · ", shadow, ts);
        return head + nextWidth(renderer, shadow, ts, ico, s);
    }

    private void drawCounters(HudRenderer renderer, double x0, double y, double rowH, AutoFlyingRegear regear, long now,
                              boolean shadow, double ts, double ico, double s) {
        double textY = y + (rowH - renderer.textHeight(shadow, ts)) / 2.0;
        double centerY = y + rowH / 2.0;
        String head = countersHead(regear, now);
        renderer.text(head, x0, textY, mutedColor.get(), shadow, ts);
        double x = x0 + renderer.textWidth(head, shadow, ts);
        drawNext(renderer, x, textY, centerY, regear, mutedColor.get(), shadow, ts, ico, s);
    }

    // ---------------------------------------------------------------- the next-regear reading

    private double nextWidth(HudRenderer renderer, boolean shadow, double ts, double ico, double s) {
        return renderer.textWidth("next ", shadow, ts) + ico + 2.0 * s + renderer.textWidth("≈000h00", shadow, ts);
    }

    /**
     * "next", the bound's item icon, and the time until the next forced regear. Muted, unless it
     * is close, when the time turns to the warning colour.
     */
    private double drawNext(HudRenderer renderer, double x, double textY, double centerY, AutoFlyingRegear regear,
                            SettingColor labelColor, boolean shadow, double ts, double ico, double s) {
        int secs = regear.hudSecondsToNextRegear();
        AutoFlyingRegear.RegearBound bound = regear.hudNextRegearBound();
        String time = nextTimeText(secs);
        SettingColor timeColor = secs >= 0 && secs < 300 ? warnColor.get() : labelColor;

        renderer.text("next ", x, textY, labelColor, shadow, ts);
        x += renderer.textWidth("next ", shadow, ts);

        ItemStack bs = boundStack(bound);
        if (bs != null) {
            postItem(renderer, bs, x, centerY - ico / 2.0, ico);
            x += ico + 2.0 * s;
        }

        renderer.text(time, x, textY, timeColor, shadow, ts);
        x += renderer.textWidth(time, shadow, ts);
        return x;
    }

    private static ItemStack boundStack(AutoFlyingRegear.RegearBound bound) {
        return switch (bound) {
            case ROCKETS -> Items.FIREWORK_ROCKET.getDefaultStack();
            case GAPS -> Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack();
            case TOTEMS -> Items.TOTEM_OF_UNDYING.getDefaultStack();
            case ELYTRA -> Items.ELYTRA.getDefaultStack();
            case BOTTLES -> Items.EXPERIENCE_BOTTLE.getDefaultStack();
            case UNKNOWN -> null;
        };
    }

    /**
     * Time to the next regear, quantised so the last digit does not spin: "now" at zero, "≈%dm"
     * under two hours (to five minutes), "≈%dh%02d" beyond, "--" while it cannot be said.
     */
    private static String nextTimeText(int secs) {
        if (secs < 0) return "--";
        if (secs == 0) return "now";
        int minutes = (int) Math.round(secs / 60.0);
        if (minutes <= 0) minutes = 1;
        if (minutes >= 120) return String.format(Locale.ROOT, "≈%dh%02d", minutes / 60, minutes % 60 / 5 * 5);
        return "≈" + (minutes < 5 ? minutes : minutes / 5 * 5) + "m";
    }

    // ---------------------------------------------------------------- the editor placeholder

    /** A representative full panel, so the element can be grabbed and placed with the follower off. */
    private void renderEditorDemo(HudRenderer renderer) {
        boolean shadow = textShadow.get();
        double s = size.get();
        double ts = s * textScale.get();
        double inset = panel.padding();
        double gap = 3.0 * s;
        double badgeIcon = 13.0 * s * iconSize.get();
        double ico = 11.0 * s * iconSize.get();
        double lineH = renderer.textHeight(shadow, ts);
        double rowH = Math.max(lineH, ico);
        double stateH = renderer.textHeight(shadow, ts * STATE_SCALE);
        double stateRowH = Math.max(stateH, badgeIcon);

        String stateText = "REGEAR #4 · ROCKETS 178";
        double badgeW = badgeWidth(renderer, false, shadow, ts, badgeIcon, s);
        double stateW = Math.max(renderer.textWidth(stateText, shadow, ts * STATE_SCALE),
            renderer.textWidth(STATE_TEMPLATE, shadow, ts * STATE_SCALE));
        String merged = "rkt 178 · ely 5 · gap 32 · tot 4 · ech 6 · obs 45 · next ";

        double labelH = renderer.textHeight(shadow, ts * 0.6);
        double spineH = 2.0 * spineHalf(s, showSpineIcons.get() && s >= SPINE_ICON_SIZE) + labelH + 1.0 * s;
        double width = Math.max(badgeW + gap + stateW,
            renderer.textWidth(merged, shadow, ts) + ico + 2.0 * s + renderer.textWidth("≈38m", shadow, ts));
        width = Math.max(width, Math.max(72.0 * s, spineMinWidth(8, s, showSpineIcons.get() && s >= SPINE_ICON_SIZE)));
        double height = stateRowH + gap + spineH + gap + rowH;

        panel.draw(renderer, this.x + inset, this.y + inset, width, height, HudGlowPanel.Severity.OK);

        double x0 = this.x + inset;
        double y = this.y + inset;

        drawBadge(renderer, x0, y, stateRowH, false, shadow, ts, badgeIcon, s);
        renderer.text(stateText, x0 + badgeW + gap, y + (stateRowH - stateH) / 2.0, busyColor.get(), shadow, ts * STATE_SCALE);
        y += stateRowH + gap;

        List<AutoFlyingRegear.Step> demo = List.of(
            AutoFlyingRegear.Step.LAND, AutoFlyingRegear.Step.BOX, AutoFlyingRegear.Step.CHEST,
            AutoFlyingRegear.Step.ELYTRA, AutoFlyingRegear.Step.ROCKETS, AutoFlyingRegear.Step.MEND,
            AutoFlyingRegear.Step.CLEAR, AutoFlyingRegear.Step.FLY);
        drawSpine(renderer, x0, y, width, demo, 4, animations.get(), s, ts, showSpineIcons.get() && s >= SPINE_ICON_SIZE, shadow);
        y += spineH + gap;

        double textY = y + (rowH - lineH) / 2.0;
        double centerY = y + rowH / 2.0;
        renderer.text(merged, x0, textY, mutedColor.get(), shadow, ts);
        double x = x0 + renderer.textWidth(merged, shadow, ts);
        postItem(renderer, Items.FIREWORK_ROCKET.getDefaultStack(), x, centerY - ico / 2.0, ico);
        x += ico + 2.0 * s;
        renderer.text("≈38m", x, textY, mutedColor.get(), shadow, ts);

        setSize(width + inset * 2.0, height + inset * 2.0);
    }

    // ---------------------------------------------------------------- small shared pieces

    private double sep(HudRenderer renderer, double x, double y, boolean shadow, double ts) {
        renderer.text(" · ", x, y, mutedColor.get(), shadow, ts);
        return x + renderer.textWidth(" · ", shadow, ts);
    }

    private SettingColor stockColor(int value, int low) {
        if (value == 0) return badColor.get();
        if (value < low) return warnColor.get();
        return valueColor.get();
    }

    /** "(+n)" for a gain, "(-n)" for a loss (a mending run spends bottles), nothing without a run to compare with. */
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

    /** An item icon, posted so the panel's buffered quads cannot paint over it, at an integer position. */
    private void postItem(HudRenderer renderer, ItemStack stack, double x, double y, double px) {
        final int ix = (int) Math.round(x);
        final int iy = (int) Math.round(y);
        final float scale = (float) (px / 16.0);
        renderer.post(() -> renderer.item(stack, ix, iy, scale, false));
    }

    // ---- primitives, cull-safe, shared with the follower HUDs

    /** A filled disc: a fan of triangles from the centre. */
    private void disc(HudRenderer renderer, double cx, double cy, double r, Color colour) {
        int segments = 20;
        double previousX = cx + r;
        double previousY = cy;
        for (int i = 1; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            double px = cx + Math.cos(angle) * r;
            double py = cy + Math.sin(angle) * r;
            tri(renderer, cx, cy, previousX, previousY, px, py, colour);
            previousX = px;
            previousY = py;
        }
    }

    /** A circle outline of the given thickness, two triangles per segment. */
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

    /** A triangle wound so the signed area is negative in y-down screen space, past the UI cull. */
    private void tri(HudRenderer renderer, double x1, double y1, double x2, double y2, double x3, double y3, Color colour) {
        double cross = (x2 - x1) * (y3 - y1) - (x3 - x1) * (y2 - y1);
        if (cross > 0.0) renderer.triangle(x1, y1, x3, y3, x2, y2, colour);
        else renderer.triangle(x1, y1, x2, y2, x3, y3, colour);
    }
}
