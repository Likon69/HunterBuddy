package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.ActivityTracker;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HuntFeed;
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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * The session as a strip of coloured time: flying, mining, regearing, away.
 *
 * <p>Above the strip, the finds — a star where a stash entered the log, a skull where a player
 * did. It is the end-of-night screenshot drawn continuously: what the hours went on, and what
 * they brought back.
 */
public class SessionTimelineHud extends HudElement {
    public static final HudElementInfo<SessionTimelineHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "SessionTimeline",
        "The session as coloured activity segments with find markers.",
        SessionTimelineHud::new);

    public enum Scope {
        Session,
        LastHour
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder()
        .name("width").description("Width of the strip.")
        .defaultValue(234.0).min(140.0).max(400.0).sliderRange(160.0, 320.0).build());

    private final Setting<Scope> scope = sgGeneral.add(new EnumSetting.Builder<Scope>()
        .name("scope").description("The whole session, or a rolling last hour.")
        .defaultValue(Scope.Session).build());

    private final Setting<Boolean> showMarkers = sgGeneral.add(new BoolSetting.Builder()
        .name("markers").description("Stars for stashes, skulls for player sightings.")
        .defaultValue(true).build());

    private final Setting<Boolean> showLegend = sgGeneral.add(new BoolSetting.Builder()
        .name("legend").description("The colour key under the strip.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("Title and legend.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("The duration.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> flyColor = sgColors.add(new ColorSetting.Builder()
        .name("fly-color").description("Time spent gliding.")
        .defaultValue(new SettingColor(63, 111, 158, 255)).build());

    private final Setting<SettingColor> mineColor = sgColors.add(new ColorSetting.Builder()
        .name("mine-color").description("Time spent mining.")
        .defaultValue(new SettingColor(139, 148, 161, 255)).build());

    private final Setting<SettingColor> regearColor = sgColors.add(new ColorSetting.Builder()
        .name("regear-color").description("Time spent regearing.")
        .defaultValue(new SettingColor(200, 134, 60, 255)).build());

    private final Setting<SettingColor> afkColor = sgColors.add(new ColorSetting.Builder()
        .name("afk-color").description("Time spent away.")
        .defaultValue(new SettingColor(43, 52, 66, 255)).build());

    private final Setting<SettingColor> idleColor = sgColors.add(new ColorSetting.Builder()
        .name("idle-color").description("Time on foot, present but neither flying nor working.")
        .defaultValue(new SettingColor(85, 96, 111, 255)).build());

    private final Setting<SettingColor> stashMarkColor = sgColors.add(new ColorSetting.Builder()
        .name("stash-marker-color").description("The stash stars.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final Setting<SettingColor> dangerMarkColor = sgColors.add(new ColorSetting.Builder()
        .name("danger-marker-color").description("The player skulls.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    public SessionTimelineHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        List<ActivityTracker.Segment> segments;
        List<HuntFeed.Entry> events;
        long now = System.currentTimeMillis();

        if (MeteorClient.mc.world != null) {
            segments = ActivityTracker.get().segments();
            events = HuntFeed.get().entries();
        } else if (isInEditor()) {
            segments = demoSegments(now);
            events = demoEvents(now);
        } else {
            setSize(0.0, 0.0);
            return;
        }

        if (segments.isEmpty()) {
            setSize(0.0, 0.0);
            return;
        }

        long start = scope.get() == Scope.LastHour
            ? Math.max(segments.get(0).startedAt(), now - 3_600_000L)
            : segments.get(0).startedAt();
        long span = Math.max(60_000L, now - start);

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();
        double w = width.get();
        double lineH = renderer.textHeight(shadow, scale);
        double markerH = showMarkers.get() ? lineH : 0.0;
        double barH = 7.0 * scale;
        double legendH = showLegend.get() ? lineH + 2.0 : 0.0;
        double h = lineH + 2.0 + markerH + barH + 2.0 + legendH;

        panel.draw(renderer, x + pad, y + pad, w, h, HudGlowPanel.Severity.OK);

        double left = x + pad;
        double ty = y + pad;

        renderer.text(scope.get() == Scope.LastHour ? "last hour · " : "session · ",
            left, ty, labelColor.get(), shadow, scale);
        renderer.text(formatDuration(span),
            left + renderer.textWidth(scope.get() == Scope.LastHour ? "last hour · " : "session · ", shadow, scale),
            ty, valueColor.get(), shadow, scale);

        double markerY = ty + lineH + 2.0;
        double barY = markerY + markerH;

        for (int i = 0; i < segments.size(); i++) {
            ActivityTracker.Segment segment = segments.get(i);
            long segStart = Math.max(segment.startedAt(), start);
            long segEnd = i + 1 < segments.size() ? segments.get(i + 1).startedAt() : now;
            if (segEnd <= start) continue;

            double x1 = left + (segStart - start) / (double) span * w;
            double x2 = left + (segEnd - start) / (double) span * w;
            renderer.quad(x1, barY, Math.max(0.5, x2 - x1), barH, colorFor(segment.activity()));
        }

        if (showMarkers.get()) {
            for (HuntFeed.Entry event : events) {
                if (event.at() < start) continue;

                String glyph;
                SettingColor color;
                switch (event.type()) {
                    case STASH -> {
                        glyph = "*";
                        color = stashMarkColor.get();
                    }
                    case PLAYER_ENTER, DANGER -> {
                        glyph = "X";
                        color = dangerMarkColor.get();
                    }
                    default -> {
                        continue;
                    }
                }

                double mx = left + (event.at() - start) / (double) span * w
                    - renderer.textWidth(glyph, shadow, scale) / 2.0;
                renderer.text(glyph, Math.max(left, Math.min(mx, left + w - 6.0)), markerY, color, shadow, scale);
            }
        }

        if (showLegend.get()) {
            double ly = barY + barH + 2.0;
            double lx = left;
            lx = legendChip(renderer, "fly", flyColor.get(), lx, ly, shadow, scale);
            lx = legendChip(renderer, "mine", mineColor.get(), lx, ly, shadow, scale);
            lx = legendChip(renderer, "regear", regearColor.get(), lx, ly, shadow, scale);
            legendChip(renderer, "afk", afkColor.get(), lx, ly, shadow, scale);
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    private SettingColor colorFor(ActivityTracker.Activity activity) {
        return switch (activity) {
            case FLY -> flyColor.get();
            case MINE -> mineColor.get();
            case REGEAR -> regearColor.get();
            case AFK -> afkColor.get();
            case IDLE -> idleColor.get();
        };
    }

    private double legendChip(HudRenderer renderer, String label, SettingColor color,
                              double lx, double ly, boolean shadow, double scale) {
        double sq = 5.0 * scale;
        double textH = renderer.textHeight(shadow, scale);
        renderer.quad(lx, ly + (textH - sq) / 2.0, sq, sq, color);
        renderer.text(label, lx + sq + 2.0 * scale, ly, labelColor.get(), shadow, scale);
        return lx + sq + 2.0 * scale + renderer.textWidth(label, shadow, scale) + 6.0 * scale;
    }

    private static String formatDuration(long ms) {
        long minutes = ms / 60_000L;
        return minutes >= 60L
            ? String.format("%d h %02d", minutes / 60L, minutes % 60L)
            : minutes + " min";
    }

    private static List<ActivityTracker.Segment> demoSegments(long now) {
        List<ActivityTracker.Segment> out = new ArrayList<>();
        long start = now - 3 * 3_600_000L;
        out.add(new ActivityTracker.Segment(ActivityTracker.Activity.FLY, start));
        out.add(new ActivityTracker.Segment(ActivityTracker.Activity.MINE, start + 70 * 60_000L));
        out.add(new ActivityTracker.Segment(ActivityTracker.Activity.FLY, start + 96 * 60_000L));
        out.add(new ActivityTracker.Segment(ActivityTracker.Activity.REGEAR, start + 148 * 60_000L));
        out.add(new ActivityTracker.Segment(ActivityTracker.Activity.FLY, start + 166 * 60_000L));
        out.add(new ActivityTracker.Segment(ActivityTracker.Activity.AFK, start + 168 * 60_000L));
        return out;
    }

    private static List<HuntFeed.Entry> demoEvents(long now) {
        List<HuntFeed.Entry> out = new ArrayList<>();
        long start = now - 3 * 3_600_000L;
        out.add(new HuntFeed.Entry(HuntFeed.Type.STASH, start + 28 * 60_000L, "", null));
        out.add(new HuntFeed.Entry(HuntFeed.Type.STASH, start + 86 * 60_000L, "", null));
        out.add(new HuntFeed.Entry(HuntFeed.Type.STASH, start + 95 * 60_000L, "", null));
        out.add(new HuntFeed.Entry(HuntFeed.Type.PLAYER_ENTER, start + 150 * 60_000L, "", null));
        return out;
    }
}
