package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.modules.WaypointFollower;
import java.util.List;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * The heading error, read as a distance instead of a number.
 *
 * <p>A graduated band centred on where you look; the waypoint is a diamond
 * sliding along it. On the cap the diamond sits on the centre line, off it the
 * offset is the correction — the eye steers on that without reading anything.
 * The waypoints after the current one appear as pale ghosts, so the turn that
 * follows the reach is visible before it happens.
 */
public class FollowerHeadingHud extends HudElement {
    public static final HudElementInfo<FollowerHeadingHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "FollowerHeading",
        "A heading tape for the waypoint follower: the target as a sliding diamond, the next turns as ghosts.",
        FollowerHeadingHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Integer> tapeWidth = sgGeneral.add(new IntSetting.Builder()
        .name("width").description("Width of the tape, in pixels.")
        .defaultValue(230).min(120).max(500).sliderRange(160, 360).build());

    private final Setting<Integer> rangeDegrees = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Degrees of heading the tape spans. Narrower magnifies the error; wider keeps a hard turn on the band.")
        .defaultValue(120).min(40).max(240).sliderRange(60, 180).build());

    private final Setting<Integer> ghosts = sgGeneral.add(new IntSetting.Builder()
        .name("ghosts")
        .description("How many upcoming waypoints are drawn as pale marks. Zero turns them off.")
        .defaultValue(2).min(0).max(4).sliderRange(0, 4).build());

    private final Setting<Boolean> showFoot = sgGeneral.add(new BoolSetting.Builder()
        .name("show-foot").description("The wp i/total, distance and arrival line under the tape.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of text and marks.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<Boolean> animations = sgGeneral.add(new BoolSetting.Builder()
        .name("animations")
        .description("Motion on the tape: the lock echo while you hold the cap, the diamond's beat on a new target, the edge arrow's lean. All of it holds still while paused.")
        .defaultValue(true).build());

    private final Setting<SettingColor> tapeColor = sgColors.add(new ColorSetting.Builder()
        .name("tape-color").description("Ticks and cardinal letters.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> centreColor = sgColors.add(new ColorSetting.Builder()
        .name("centre-color").description("The fixed centre line — where you are looking.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> markColor = sgColors.add(new ColorSetting.Builder()
        .name("mark-color").description("The current waypoint's diamond.")
        .defaultValue(new SettingColor(120, 255, 200, 255)).build());

    private final Setting<SettingColor> ghostColor = sgColors.add(new ColorSetting.Builder()
        .name("ghost-color").description("The upcoming waypoints.")
        .defaultValue(new SettingColor(255, 255, 255, 80)).build());

    private final Setting<SettingColor> offColor = sgColors.add(new ColorSetting.Builder()
        .name("off-color").description("The edge arrow when the target has left the band.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> footColor = sgColors.add(new ColorSetting.Builder()
        .name("foot-color").description("The line under the tape.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();
    private long lastFrameNanos;
    private double animTime;

    private static final double TAPE_HEIGHT = 16.0;

    public FollowerHeadingHud() {
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

        float yaw = MeteorClient.mc.player == null ? 0.0f : MathHelper.wrapDegrees(MeteorClient.mc.player.getYaw());
        float targetYaw = live ? follower.hudTargetYaw() : Float.NaN;

        if (Float.isNaN(targetYaw)) {
            if (isInEditor()) {
                // The demo runs the real path: a target twelve degrees right of
                // wherever you look, a ghost further out, plausible numbers.
                renderTape(renderer, yaw, MathHelper.wrapDegrees(yaw + 12.0f),
                    new float[]{MathHelper.wrapDegrees(yaw + 38.0f)},
                    "wp 3/10", "3.2 km", "~8m 40s", 0.5, false);
                return;
            }

            // Nothing to steer by, nothing drawn: the cockpit says why.
            setSize(0.0, 0.0);
            return;
        }

        List<BlockPos> route = follower.hudRoute();
        int index = follower.hudRouteIndex();
        int ghostCount = Math.min(ghosts.get(), Math.max(0, route.size() - index - 1));
        float[] ghostYaws = new float[Math.max(0, ghostCount)];

        for (int i = 0; i < ghostYaws.length; i++) {
            ghostYaws[i] = MathHelper.wrapDegrees(follower.hudYawTo(route.get(index + 1 + i)));
        }

        int done = follower.hudCompleted();
        int total = done + follower.hudRemaining();
        double distance = follower.hudDistance();
        int eta = quantiseEta(follower.hudEtaSeconds());

        // A held follower is not steering, and a tape in its flying colours
        // would say it is. Greyed, it still shows where the line sits — worth
        // having while deciding whether to resume — without claiming anything.
        WaypointFollower.HudState state = follower.hudState();
        boolean held = state == WaypointFollower.HudState.PAUSED
            || state == WaypointFollower.HudState.AREA_LOADER;

        renderTape(renderer, yaw, MathHelper.wrapDegrees(targetYaw), ghostYaws,
            String.format(Locale.ROOT, "wp %d/%d", Math.min(done + 1, total), total),
            distance < 0.0 ? "--" : formatDistance(distance),
            eta < 0 ? "--" : "~" + formatTime(eta),
            follower.hudYawDeadzone(), held);
    }

    private void renderTape(HudRenderer renderer, float yaw, float targetYaw, float[] ghostYaws,
                            String wpText, String distText, String etaText, double deadzone,
                            boolean held) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double inset = panel.padding();
        double width = tapeWidth.get() * scale;
        double tapeH = TAPE_HEIGHT * scale;
        double lineHeight = renderer.textHeight(shadow, scale);
        double footH = showFoot.get() ? lineHeight + 3.0 * scale : 0.0;
        double height = tapeH + footH;

        double error = MathHelper.wrapDegrees(targetYaw - yaw);
        boolean aligned = Math.abs(error) <= Math.max(deadzone, 2.0);

        SettingColor diamondC = held ? tapeColor.get() : markColor.get();
        SettingColor centre = held ? tapeColor.get() : centreColor.get();
        SettingColor edge = held ? tapeColor.get() : offColor.get();

        panel.draw(renderer, this.x + inset, this.y + inset, width, height,
            held || !aligned ? HudGlowPanel.Severity.WARN : HudGlowPanel.Severity.OK);

        double x0 = this.x + inset;
        double y0 = this.y + inset;
        double cx = x0 + width / 2.0;
        double pxPerDeg = width / rangeDegrees.get();
        double half = rangeDegrees.get() / 2.0;

        // Ticks every fifteen degrees, taller with a cardinal letter at the
        // forty-fives. Drawn from the marks that fall in the window, so the
        // band scrolls under the fixed centre as the view turns.
        int first = (int) Math.ceil((yaw - half) / 15.0) * 15;

        for (int mark = first; mark <= yaw + half; mark += 15) {
            double dx = MathHelper.wrapDegrees(mark - yaw) * pxPerDeg;
            double tx = cx + dx;
            if (tx < x0 + 1.0 || tx > x0 + width - 1.0) continue;

            boolean major = Math.floorMod(mark, 45) == 0;
            double tick = (major ? 5.0 : 3.0) * scale;
            renderer.quad(tx, y0, Math.max(1.0, scale), tick, tapeColor.get());

            // A cardinal on the forty-fives, the number on the ticks between --
            // the mock-up's band reads 285, 300, NW -- but the numbers only when
            // the band is wide enough that they do not run into each other.
            String label = major ? cardinal(mark) : null;
            double labelScale = scale * 0.85;

            if (label == null && pxPerDeg * 15.0 >= 26.0 * scale) {
                label = Integer.toString(Math.floorMod(mark, 360));
                labelScale = scale * 0.75;
            }

            if (label != null) {
                double lw = renderer.textWidth(label, shadow, labelScale);
                if (tx - lw / 2.0 > x0 && tx + lw / 2.0 < x0 + width) {
                    renderer.text(label, tx - lw / 2.0, y0 + tick + 1.0 * scale, tapeColor.get(), shadow, labelScale);
                }
            }
        }

        // Ghosts before the diamond, so the current mark always wins the
        // overlap. They breathe a little, out of phase with one another: a
        // turn still in the future reads as pending, not as painted on.
        boolean anim = animations.get();
        int ghostIndex = 0;

        if (!held) for (float ghost : ghostYaws) {
            double dx = MathHelper.wrapDegrees(ghost - yaw);
            ghostIndex++;
            if (Math.abs(dx) > half - 2.0) continue;
            double gx = cx + dx * pxPerDeg;
            double gs = 3.0 * scale;
            int ga = ghostColor.get().a;

            if (anim) {
                double gb = 0.5 + 0.5 * Math.sin(animTime * Math.PI * 2.0 / 2.6 + ghostIndex * 1.9);
                ga = (int) MathHelper.clamp(ga * (0.65 + 0.45 * gb), 20.0, 255.0);
            }

            tri(renderer, gx - gs, y0 + 2.0 * scale, gx + gs, y0 + 2.0 * scale, gx, y0 + 2.0 * scale + gs * 1.6,
                new Color(ghostColor.get().r, ghostColor.get().g, ghostColor.get().b, ga));
        }

        // The centre line: where you look, and where the diamond belongs. It
        // flashes to the mark's colour for a beat when the cap is captured --
        // the tape's way of saying "held" without a word.
        float lockFresh = anim && !held ? pulse.freshness("aligned", aligned ? "y" : "n", 500L) : 0.0f;
        Color centreDraw = aligned
            ? HudPulse.tint(HudPulse.Style.Lively, centre, markColor.get(), lockFresh)
            : centre;
        renderer.quad(cx - Math.max(0.5, scale / 2.0), y0, Math.max(1.0, scale), tapeH, centreDraw);

        double margin = 6.0 * scale;

        if (Math.abs(error) * pxPerDeg <= width / 2.0 - margin) {
            double mx = cx + error * pxPerDeg;
            double my = y0 + tapeH / 2.0 + 1.0 * scale;

            // A fresh target beats the diamond once; holding the cap rings a
            // slow echo out of it. Both say what the steering already knows.
            float targetFresh = anim && !held ? pulse.freshness("target", wpText, 600L) : 0.0f;
            double s = 4.5 * scale * (anim ? HudPulse.pop(targetFresh) : 1.0);

            if (anim && !held && aligned) {
                double echoP = animTime % 1.4 / 1.4;
                double es = s * (1.0 + 1.1 * echoP);
                int ea = (int) (70.0 * (1.0 - echoP));
                if (ea > 5) {
                    Color echo = new Color(diamondC.r, diamondC.g, diamondC.b, ea);
                    tri(renderer, mx, my - es, mx + es, my, mx, my + es, echo);
                    tri(renderer, mx, my - es, mx, my + es, mx - es, my, echo);
                }
            }

            tri(renderer, mx, my - s, mx + s, my, mx, my + s, diamondC);
            tri(renderer, mx, my - s, mx, my + s, mx - s, my, diamondC);
        } else {
            // Off the band: an arrow at the edge it left by, with the degrees
            // to bring it back — the one moment a number beats a position. It
            // leans toward its own edge on a quick beat: a pointing hand, not
            // an alarm.
            String text = String.format(Locale.ROOT, error < 0 ? "< %.0f°" : "%.0f° >", Math.abs(error));
            double tw = renderer.textWidth(text, shadow, scale);
            double lean = anim && !held ? (0.5 + 0.5 * Math.sin(animTime * Math.PI * 2.0 / 0.9)) * 1.5 * scale : 0.0;
            double tx = error < 0
                ? x0 + 2.0 * scale - lean
                : x0 + width - tw - 2.0 * scale + lean;
            renderer.text(text, tx, y0 + tapeH / 2.0 - lineHeight / 2.0, edge, shadow, scale);
        }

        if (showFoot.get()) {
            double fy = y0 + tapeH + 3.0 * scale;

            renderer.text(wpText, x0, fy, tapeColor.get(), shadow, scale);

            double dw = Math.max(renderer.textWidth(distText, shadow, scale),
                renderer.textWidth("00000.0 km", shadow, scale));
            renderer.text(distText, cx - dw / 2.0, fy, footColor.get(), shadow, scale);

            double ew = Math.max(renderer.textWidth(etaText, shadow, scale),
                renderer.textWidth("~000h 00m", shadow, scale));
            renderer.text(etaText, x0 + width - ew, fy, footColor.get(), shadow, scale);
        }

        setSize(width + inset * 2.0, height + inset * 2.0);
    }

    /** Minecraft compass: yaw 0 faces south, and west comes up at ninety. */
    private static String cardinal(int mark) {
        return switch (Math.floorMod(mark, 360) / 45) {
            case 0 -> "S";
            case 1 -> "SW";
            case 2 -> "W";
            case 3 -> "NW";
            case 4 -> "N";
            case 5 -> "NE";
            case 6 -> "E";
            default -> "SE";
        };
    }

    /**
     * A triangle that survives the UI pipeline's culling.
     *
     * <p>Meteor's UI pipelines cull back faces, and in y-down screen space a
     * triangle wound with positive signed area is a back face. Same guard as
     * the radar HUD, for the same reason.
     */
    private void tri(HudRenderer renderer, double x1, double y1, double x2, double y2, double x3, double y3,
                     Color colour) {
        double cross = (x2 - x1) * (y3 - y1) - (x3 - x1) * (y2 - y1);

        if (cross > 0.0) renderer.triangle(x1, y1, x3, y3, x2, y2, colour);
        else renderer.triangle(x1, y1, x2, y2, x3, y3, colour);
    }

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

    private static String formatTime(int seconds) {
        if (seconds >= 3600) return String.format(Locale.ROOT, "%dh %02dm", seconds / 3600, (seconds % 3600) / 60);
        if (seconds >= 60) return String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60);
        return seconds + "s";
    }
}
