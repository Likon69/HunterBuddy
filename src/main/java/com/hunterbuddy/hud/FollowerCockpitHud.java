package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.modules.WaypointFollower;
import java.util.Locale;
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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * Is the follower fine, and if not, why.
 *
 * <p>The bot flies unattended for hours, and the question that matters from the
 * other side of the room is not where it is — it is whether it is still going.
 * One big line answers it: the state while everything is fine, the problem the
 * moment it is not, in a colour the frame carries too. Three small lines say
 * the route, the consumables and the session underneath, for the closer look.
 *
 * <p>Everything here is read from the follower's own tick-time answers; the
 * element computes nothing the module has not already computed.
 */
public class FollowerCockpitHud extends HudElement {
    public static final HudElementInfo<FollowerCockpitHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "FollowerCockpit",
        "The waypoint follower's state, or its problem, plus route, consumables and session.",
        FollowerCockpitHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Boolean> showRoute = sgGeneral.add(new BoolSetting.Builder()
        .name("show-route").description("The wp i/total, distance and arrival line.")
        .defaultValue(true).build());

    private final Setting<Boolean> showConsumables = sgGeneral.add(new BoolSetting.Builder()
        .name("show-consumables").description("The rockets and elytra line.")
        .defaultValue(true).build());

    private final Setting<Boolean> showSession = sgGeneral.add(new BoolSetting.Builder()
        .name("show-session").description("The session duration, waypoints done and ground covered line.")
        .defaultValue(true).build());

    private final Setting<Integer> rocketsWarn = sgGeneral.add(new IntSetting.Builder()
        .name("rockets-warn")
        .description("Rocket count at or under which the line turns to the warning colour. Zero is always critical.")
        .defaultValue(8).min(1).max(64).sliderRange(1, 32).build());

    private final Setting<Integer> elytraWarn = sgGeneral.add(new IntSetting.Builder()
        .name("elytra-warn")
        .description("Worn elytra percentage at or under which its reading turns to the warning colour.")
        .defaultValue(15).min(1).max(90).sliderRange(1, 50).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<Boolean> animations = sgGeneral.add(new BoolSetting.Builder()
        .name("animations")
        .description("Motion on change: the state line hops when it switches, readings warm as they move, a critical state breathes.")
        .defaultValue(true).build());

    private final Setting<SettingColor> okColor = sgColors.add(new ColorSetting.Builder()
        .name("ok-color").description("The state line while everything is fine.")
        .defaultValue(new SettingColor(110, 240, 150, 255)).build());

    private final Setting<SettingColor> warnColor = sgColors.add(new ColorSetting.Builder()
        .name("warn-color").description("Starting, loading, and readings running low.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> heldColor = sgColors.add(new ColorSetting.Builder()
        .name("held-color").description("Paused, or steering handed to AreaLoader.")
        .defaultValue(new SettingColor(255, 160, 80, 255)).build());

    private final Setting<SettingColor> badColor = sgColors.add(new ColorSetting.Builder()
        .name("bad-color").description("The problems that stop the flight outright.")
        .defaultValue(new SettingColor(255, 90, 90, 255)).build());

    private final Setting<SettingColor> baritoneColor = sgColors.add(new ColorSetting.Builder()
        .name("baritone-color").description("The state line while Baritone flies the Nether.")
        .defaultValue(new SettingColor(110, 170, 240, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("The route and consumable readings.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> mutedColor = sgColors.add(new ColorSetting.Builder()
        .name("muted-color").description("The session line, and the element with the follower off.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();
    private long lastFrameNanos;
    private double animTime;

    /** How much bigger the state line is than the rest. */
    private static final double STATE_SCALE = 1.3;

    public FollowerCockpitHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.016 : Math.min(0.25, (now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        animTime += dt;

        WaypointFollower follower = Modules.get().get(WaypointFollower.class);
        boolean live = MeteorClient.mc.player != null && MeteorClient.mc.world != null
            && follower != null && follower.isActive();

        if (!live) {
            if (isInEditor()) {
                renderLines(renderer, "FLYING · PITCH40 · OVERWORLD", okColor.get(),
                    new String[]{"wp 3/10", "1 243 m", "~2:10"},
                    new String[]{"rockets 41", "elytra 87%"}, valueColor.get(),
                    "session 2h04 · 6 done · 118 km");
                return;
            }

            // Off means gone: nothing drawn, no size, no panel. An element that
            // announces its own absence is one more thing on the screen.
            setSize(0.0, 0.0);
            return;
        }

        WaypointFollower.HudState state = follower.hudState();

        String stateText = switch (state) {
            case RECORDING -> "NETHER".equals(follower.hudDimensionName())
                ? "RECORDING ROUTE · NETHER"
                : "RECORDING · SURFACED · UNTICK TO FLY";
            // Ten attempts is where the loader gives up trying: past it this is
            // no longer "loading", it is stuck, and an AFK glance must tell the
            // two apart.
            case LOADING -> follower.hudLoadRetries() >= 10
                ? "WAYPOINTS NOT LOADED (10/10)"
                : "WAYPOINTS LOADING (" + Math.min(10, follower.hudLoadRetries()) + "/10)";
            case PAUSED -> "PAUSED";
            case AREA_LOADER -> "AREALOADER HAS CONTROL";
            case NO_WAYPOINTS -> "NO WAYPOINTS";
            case NO_ELYTRA -> "NO ELYTRA";
            case NO_ROCKETS -> "NO ROCKETS";
            case STARTING -> "STARTING";
            case WAIT_GLIDE -> "WAITING FOR GLIDE";
            case FLYING -> "FLYING · " + follower.hudModeName() + " · " + follower.hudDimensionName();
        };

        SettingColor stateColor = switch (state) {
            case RECORDING -> okColor.get();
            case FLYING -> "BARITONE".equals(follower.hudModeName()) ? baritoneColor.get() : okColor.get();
            case LOADING -> follower.hudLoadRetries() >= 10 ? badColor.get() : warnColor.get();
            case STARTING, WAIT_GLIDE -> warnColor.get();
            case PAUSED, AREA_LOADER -> heldColor.get();
            case NO_WAYPOINTS, NO_ELYTRA, NO_ROCKETS -> badColor.get();
        };

        int remaining = follower.hudRemaining();
        int done = follower.hudCompleted();
        double distance = follower.hudDistance();
        int eta = quantiseEta(follower.hudEtaSeconds());

        String[] route = {
            String.format(Locale.ROOT, "wp %d/%d", Math.min(done + 1, done + remaining), done + remaining),
            distance < 0.0 ? "--" : formatDistance(distance),
            formatClock(eta)
        };

        int rockets = follower.hudRockets();
        double elytra = follower.hudElytraPercent();
        String[] consumables = {
            "rockets " + rockets,
            "elytra " + (elytra < 0.0 ? "--" : String.format(Locale.ROOT, "%.0f%%", elytra))
        };

        SettingColor consumableColor = rockets == 0 || elytra >= 0.0 && elytra <= elytraWarn.get()
            ? badColor.get()
            : rockets <= rocketsWarn.get() ? warnColor.get() : valueColor.get();

        String session = String.format(Locale.ROOT, "session %s · %d done · %s",
            formatSession(follower.hudSessionSeconds()), done, formatDistance(follower.hudBlocksTraveled()));

        renderLines(renderer, stateText, stateColor, route, consumables, consumableColor, session);
    }

    /** Lays the four lines out, panel underneath, templates keeping the width still. */
    private void renderLines(HudRenderer renderer, String stateText, SettingColor stateColor,
                             String[] route, String[] consumables, SettingColor consumableColor,
                             String session) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double stateHeight = renderer.textHeight(shadow, scale * STATE_SCALE);
        double lineHeight = renderer.textHeight(shadow, scale);
        double gap = 2.0 * scale;
        double inset = panel.padding();

        double width = renderer.textWidth(stateText, shadow, scale * STATE_SCALE);
        width = Math.max(width, renderer.textWidth("WAYPOINTS LOADING (10/10)", shadow, scale * STATE_SCALE));
        if (showRoute.get()) width = Math.max(width, Math.max(rowWidth(renderer, route, shadow, scale),
            renderer.textWidth("wp 000/000 · 00000.0 km · ~000:00", shadow, scale)));
        if (showConsumables.get()) width = Math.max(width, Math.max(rowWidth(renderer, consumables, shadow, scale),
            renderer.textWidth("rockets 0000 · elytra 100%", shadow, scale)));
        if (showSession.get()) width = Math.max(width, slot(renderer, session, "session 00h00 · 000 done · 00000.0 km", shadow, scale));

        double height = stateHeight
            + (showRoute.get() ? lineHeight + gap : 0.0)
            + (showConsumables.get() ? lineHeight + gap : 0.0)
            + (showSession.get() ? lineHeight + gap : 0.0);

        HudGlowPanel.Severity severity =
            stateColor == badColor.get() || consumableColor == badColor.get() ? HudGlowPanel.Severity.CRITICAL
                : stateColor == okColor.get() && consumableColor == valueColor.get()
                    || stateColor == baritoneColor.get() && consumableColor == valueColor.get()
                        ? HudGlowPanel.Severity.OK
                        : HudGlowPanel.Severity.WARN;

        panel.draw(renderer, this.x + inset, this.y + inset, width, height, severity);

        boolean anim = animations.get();

        // The state line is the event channel: it hops once when it switches
        // and flashes toward white, and a critical state breathes on a slow
        // beat -- an alarm readable from across the room, not a strobe.
        // Colour and a two-pixel hop only; the layout never moves.
        float stateFresh = anim ? pulse.freshness("state", stateText, 900L) : 0.0f;
        Color stateDraw = HudPulse.tint(HudPulse.Style.Lively, stateColor,
            new SettingColor(255, 255, 255, 255), stateFresh);

        if (anim && severity == HudGlowPanel.Severity.CRITICAL) {
            double breathe = 0.5 + 0.5 * Math.sin(animTime * Math.PI * 2.0 / 1.2);
            stateDraw = new Color(
                (int) (stateDraw.r + (255 - stateDraw.r) * 0.30 * breathe),
                (int) (stateDraw.g + (255 - stateDraw.g) * 0.30 * breathe),
                (int) (stateDraw.b + (255 - stateDraw.b) * 0.30 * breathe),
                stateDraw.a);
        }

        double y = this.y + inset;
        renderer.text(stateText, this.x + inset,
            y + HudPulse.bounce(HudPulse.Style.Lively, stateFresh, scale), stateDraw, shadow, scale * STATE_SCALE);
        y += stateHeight + gap;

        if (showRoute.get()) {
            // Only the wp counter flashes: it moves once per waypoint. The
            // distance beside it moves every frame, and a flash that never
            // rests is one the eye learns to ignore.
            Color[] routeColors = {
                HudPulse.tint(HudPulse.Style.Subtle, valueColor.get(), okColor.get(),
                    anim ? pulse.freshness("wp", route[0], 900L) : 0.0f),
                valueColor.get(), valueColor.get()
            };
            drawSeparated(renderer, this.x + inset, y, route, routeColors, shadow, scale);
            y += lineHeight + gap;
        }

        if (showConsumables.get()) {
            // A rocket spent or an elytra point lost warms its own reading for
            // a moment, so consumption is visible as it happens.
            Color[] consumableColors = {
                HudPulse.tint(HudPulse.Style.Subtle, consumableColor, warnColor.get(),
                    anim ? pulse.freshness("rockets", consumables[0], 800L) : 0.0f),
                HudPulse.tint(HudPulse.Style.Subtle, consumableColor, warnColor.get(),
                    anim ? pulse.freshness("elytra", consumables[1], 800L) : 0.0f)
            };
            drawSeparated(renderer, this.x + inset, y, consumables, consumableColors, shadow, scale);
            y += lineHeight + gap;
        }

        if (showSession.get()) {
            renderer.text(session, this.x + inset, y, mutedColor.get(), shadow, scale);
        }

        setSize(width + inset * 2.0, height + inset * 2.0);
    }

    private double slot(HudRenderer renderer, String text, String template, boolean shadow, double scale) {
        return Math.max(renderer.textWidth(text, shadow, scale), renderer.textWidth(template, shadow, scale));
    }

    /** The fields of a row bright, the dots between them dimmed, as the mock-up draws them. */
    private void drawSeparated(HudRenderer renderer, double x, double y, String[] parts, Color[] colors,
                               boolean shadow, double scale) {
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                renderer.text(" · ", x, y, mutedColor.get(), shadow, scale);
                x += renderer.textWidth(" · ", shadow, scale);
            }

            renderer.text(parts[i], x, y, colors[Math.min(i, colors.length - 1)], shadow, scale);
            x += renderer.textWidth(parts[i], shadow, scale);
        }
    }

    private double rowWidth(HudRenderer renderer, String[] parts, boolean shadow, double scale) {
        double width = 0.0;

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) width += renderer.textWidth(" · ", shadow, scale);
            width += renderer.textWidth(parts[i], shadow, scale);
        }

        return width;
    }

    /** The mock-up's clock: ~2:10 under an hour, ~1:04h above it, --:-- without one. */
    private static String formatClock(int seconds) {
        if (seconds < 0) return "--:--";
        if (seconds < 3600) return String.format(Locale.ROOT, "~%d:%02d", seconds / 60, seconds % 60);
        return String.format(Locale.ROOT, "~%d:%02dh", seconds / 3600, (seconds % 3600) / 60);
    }

    /**
     * Steps the arrival time so its last digit does not spin.
     *
     * <p>Same steps as TravelHud, for the same reason: nobody plans an hour-long
     * flight to the second, and a number that changes every frame is not read.
     */
    private static int quantiseEta(int seconds) {
        if (seconds < 0) return seconds;
        if (seconds >= 3600) return seconds / 60 * 60;
        if (seconds >= 60) return seconds / 10 * 10;
        return seconds / 5 * 5;
    }

    private static String formatDistance(double blocks) {
        return blocks >= 1000.0
            ? String.format(Locale.ROOT, "%.1f km", blocks / 1000.0)
            : String.format(Locale.ROOT, "%.0f m", blocks);
    }

    private static String formatSession(long seconds) {
        if (seconds >= 3600) return String.format(Locale.ROOT, "%dh%02d", seconds / 3600, (seconds % 3600) / 60);
        return String.format(Locale.ROOT, "%dm", seconds / 60);
    }
}
