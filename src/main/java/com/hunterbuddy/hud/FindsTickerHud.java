package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.util.HuntFeed;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * The last few finds as a live feed: coloured dot, what it was, how long ago.
 *
 * <p>The Discord notifier already tells the phone; this tells the pilot. Lines fade as they age,
 * so a glance separates "just now" from "ten minutes back" without reading a single number.
 */
public class FindsTickerHud extends HudElement {
    public static final HudElementInfo<FindsTickerHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "FindsTicker",
        "Recent finds and sightings as a fading feed.",
        FindsTickerHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTypes = settings.createGroup("Types");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Integer> maxLines = sgGeneral.add(new IntSetting.Builder()
        .name("max-lines").description("How many entries to show.")
        .defaultValue(4).min(1).max(8).sliderRange(1, 8).build());

    private final Setting<Integer> maxAgeMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("max-age").description("Entries older than this many minutes drop off.")
        .defaultValue(10).min(1).max(60).sliderRange(1, 30).build());

    private final Setting<Double> panelWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("width")
        .description("Fixed width of the feed. Fixed rather than fitted: entries come and go, and a panel that resized with each would never sit still.")
        .defaultValue(190.0).min(120.0).max(360.0).sliderRange(140.0, 300.0).build());

    private final Setting<Boolean> alertOnPlayer = sgGeneral.add(new BoolSetting.Builder()
        .name("alert-on-player").description("Critical glow for a minute after a player sighting.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<Boolean> showStashes = sgTypes.add(new BoolSetting.Builder()
        .name("stashes").description("Stash detections.").defaultValue(true).build());
    private final Setting<Boolean> showSpawners = sgTypes.add(new BoolSetting.Builder()
        .name("spawners").description("Classified spawners.").defaultValue(true).build());
    private final Setting<Boolean> showPortals = sgTypes.add(new BoolSetting.Builder()
        .name("portals").description("Portals you lit.").defaultValue(true).build());
    private final Setting<Boolean> showPlayers = sgTypes.add(new BoolSetting.Builder()
        .name("players").description("Players entering and leaving range.").defaultValue(true).build());
    private final Setting<Boolean> showOldChunks = sgTypes.add(new BoolSetting.Builder()
        .name("old-chunks").description("Confirmed old chunk clusters.").defaultValue(true).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("age-color").description("The age column.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color").description("The entry text.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> stashColor = sgColors.add(new ColorSetting.Builder()
        .name("stash-color").description("Stash dot.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final Setting<SettingColor> spawnerColor = sgColors.add(new ColorSetting.Builder()
        .name("spawner-color").description("Spawner dot.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> portalColor = sgColors.add(new ColorSetting.Builder()
        .name("portal-color").description("Portal dot.")
        .defaultValue(new SettingColor(150, 80, 220, 255)).build());

    private final Setting<SettingColor> playerColor = sgColors.add(new ColorSetting.Builder()
        .name("player-color").description("Player dot.")
        .defaultValue(new SettingColor(255, 80, 80, 255)).build());

    private final Setting<SettingColor> oldChunkColor = sgColors.add(new ColorSetting.Builder()
        .name("old-chunk-color").description("Old chunk dot.")
        .defaultValue(new SettingColor(126, 200, 255, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);
    private final HudPulse.Tracker pulse = new HudPulse.Tracker();

    public FindsTickerHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        List<HuntFeed.Entry> entries = collect();

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();
        double lineH = renderer.textHeight(shadow, scale) + 2.0 * scale;
        double w = panelWidth.get();

        if (entries.isEmpty()) {
            if (isInEditor()) {
                entries = demoEntries();
            } else {
                setSize(0.0, 0.0);
                return;
            }
        }

        double h = entries.size() * lineH - 2.0 * scale;

        HudGlowPanel.Severity severity = HudGlowPanel.Severity.OK;
        if (alertOnPlayer.get()) {
            long age = HuntFeed.get().ageOfNewest(HuntFeed.Type.PLAYER_ENTER);
            if (age >= 0 && age < 60_000L) severity = HudGlowPanel.Severity.CRITICAL;
        }

        panel.draw(renderer, x + pad, y + pad, w, h, severity);

        float freshness = pulse.freshness("newest",
            entries.isEmpty() ? "" : String.valueOf(entries.get(0).at()), 700);

        double ty = y + pad;
        for (int i = 0; i < entries.size(); i++) {
            drawEntry(renderer, entries.get(i), x + pad, ty, w, shadow, scale, i == 0 ? freshness : 0.0f);
            ty += lineH;
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    private void drawEntry(HudRenderer renderer, HuntFeed.Entry entry, double left, double top,
                           double w, boolean shadow, double scale, float freshness) {
        long age = entry.ageMs();

        // Full presence for half a minute, then a slide toward one third: old lines stay legible
        // but visibly spent.
        double fade = age <= 30_000L ? 1.0
            : Math.max(0.35, 1.0 - (age - 30_000L) / (double) (maxAgeMinutes.get() * 60_000L));

        SettingColor dot = colorFor(entry.type());
        double dotSize = 3.0 * scale;
        double textH = renderer.textHeight(shadow, scale);

        renderer.quad(left, top + (textH - dotSize) / 2.0, dotSize, dotSize,
            new Color(dot.r, dot.g, dot.b, (int) (dot.a * fade)));

        String ageText = formatAge(age);
        double ageW = renderer.textWidth("88 min", shadow, scale);
        double textLeft = left + dotSize + 4.0 * scale;
        double textRight = left + w - ageW - 2.0 * scale;

        String label = fit(renderer, entry.label(), textRight - textLeft, shadow, scale);

        SettingColor base = valueColor.get();
        Color faded = new Color(base.r, base.g, base.b, (int) (base.a * fade));
        renderer.text(label, textLeft, top,
            freshness > 0.0f ? HudPulse.tint(HudPulse.Style.Subtle, toSetting(faded), dot, freshness) : faded,
            shadow, scale);

        SettingColor ageBase = labelColor.get();
        renderer.text(ageText, left + w - renderer.textWidth(ageText, shadow, scale), top,
            new Color(ageBase.r, ageBase.g, ageBase.b, (int) (ageBase.a * fade)), shadow, scale);
    }

    private List<HuntFeed.Entry> collect() {
        long cutoff = maxAgeMinutes.get() * 60_000L;
        List<HuntFeed.Entry> out = new ArrayList<>();

        for (HuntFeed.Entry entry : HuntFeed.get().entries()) {
            if (entry.ageMs() > cutoff) break;
            if (!enabled(entry.type())) continue;

            out.add(entry);
            if (out.size() >= maxLines.get()) break;
        }

        return out;
    }

    private boolean enabled(HuntFeed.Type type) {
        return switch (type) {
            case STASH -> showStashes.get();
            case SPAWNER -> showSpawners.get();
            case PORTAL -> showPortals.get();
            case PLAYER_ENTER, PLAYER_LEAVE, DANGER -> showPlayers.get();
            case OLD_CHUNK -> showOldChunks.get();
        };
    }

    private SettingColor colorFor(HuntFeed.Type type) {
        return switch (type) {
            case STASH -> stashColor.get();
            case SPAWNER -> spawnerColor.get();
            case PORTAL -> portalColor.get();
            case PLAYER_ENTER, PLAYER_LEAVE, DANGER -> playerColor.get();
            case OLD_CHUNK -> oldChunkColor.get();
        };
    }

    private static String formatAge(long ms) {
        long seconds = ms / 1000L;
        if (seconds < 60L) return seconds + " s";
        if (seconds < 3600L) return (seconds / 60L) + " min";
        return (seconds / 3600L) + " h";
    }

    private String fit(HudRenderer renderer, String label, double available, boolean shadow, double scale) {
        if (renderer.textWidth(label, shadow, scale) <= available) return label;

        String out = label;
        while (out.length() > 1 && renderer.textWidth(out + "..", shadow, scale) > available) {
            out = out.substring(0, out.length() - 1);
        }

        return out + "..";
    }

    private static SettingColor toSetting(Color color) {
        return new SettingColor(color.r, color.g, color.b, color.a);
    }

    private List<HuntFeed.Entry> demoEntries() {
        long now = System.currentTimeMillis();
        List<HuntFeed.Entry> out = new ArrayList<>();
        out.add(new HuntFeed.Entry(HuntFeed.Type.STASH, now - 2_000L, "Stash · 26 containers", null));
        out.add(new HuntFeed.Entry(HuntFeed.Type.SPAWNER, now - 70_000L, "Dungeon spawner · skeleton", null));
        out.add(new HuntFeed.Entry(HuntFeed.Type.PORTAL, now - 240_000L, "Portal lit", null));
        out.add(new HuntFeed.Entry(HuntFeed.Type.PLAYER_ENTER, now - 660_000L, "Fit2Win", null));
        return out.subList(0, Math.min(out.size(), maxLines.get()));
    }
}
