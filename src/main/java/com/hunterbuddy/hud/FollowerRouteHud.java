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
import net.minecraft.util.math.Vec3d;

/**
 * The whole tour on one line, like a metro map.
 *
 * <p>You are a fixed point; the route slides toward you. The current waypoint
 * carries a halo the size of the reach distance, so the arrival is visible
 * before it happens; the ones after it are milestones spaced by the square
 * root of their distance, and past the visible few a +N pill stands in for the
 * rest. A milestone just reached slides off to the left and fades.
 *
 * <p>Nothing here moves per frame that should not: the scale of the lane is
 * frozen while a waypoint is being flown, and only re-derived when the target
 * changes — which is what keeps forty waypoints as steady as one.
 *
 * <p>The motion system came out of a three-way design debate, and every mover
 * earns its place by encoding something real:
 * <ul>
 * <li>the flow arrows run on blocks actually consumed off the route — frozen
 * when the bot orbits or stalls, never a carpet that rolls on its own;</li>
 * <li>the sonar ping is an odometer: one pulse per stretch of route eaten,
 * the stretch shrinking with the distance left, so the cadence rises as the
 * arrival comes — a countdown felt before it is read;</li>
 * <li>the arrival is a five-beat handover: the old halo contracts onto the
 * marker, a beat of silence, the strike ring with a whip running down the
 * lane rocking the stations, and the new halo opens from nothing;</li>
 * <li>the rail behind you fills with the fraction of the tour already done;
 * a tick ahead marks where you will be in ten seconds; the player marker
 * turns amber when the bot has stalled without being paused; the arrival
 * estimate only flashes when it gets worse, never on its normal countdown.</li>
 * </ul>
 */
public class FollowerRouteHud extends HudElement {
    public static final HudElementInfo<FollowerRouteHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "FollowerRoute",
        "The follower's route as a line of milestones: reach halo on the current one, +N for the rest.",
        FollowerRouteHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Integer> laneWidth = sgGeneral.add(new IntSetting.Builder()
        .name("width").description("Width of the lane, in pixels.")
        .defaultValue(240).min(140).max(500).sliderRange(180, 360).build());

    private final Setting<Integer> visibleStops = sgGeneral.add(new IntSetting.Builder()
        .name("visible-stops")
        .description("Milestones drawn beyond the current one before the +N pill takes over.")
        .defaultValue(4).min(1).max(8).sliderRange(1, 6).build());

    private final Setting<Boolean> showUnder = sgGeneral.add(new BoolSetting.Builder()
        .name("show-distance").description("Distance and arrival under the current milestone.")
        .defaultValue(true).build());

    private final Setting<Boolean> animations = sgGeneral.add(new BoolSetting.Builder()
        .name("animations")
        .description("The lane's motion: flow arrows on the route actually eaten, the sonar countdown, the arrival handover, the ten-second mark, the stall amber. Everything holds still while paused or stalled.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of text and marks.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> accentColor = sgColors.add(new ColorSetting.Builder()
        .name("accent-color").description("You, the current milestone and its halo.")
        .defaultValue(new SettingColor(120, 255, 200, 255)).build());

    private final Setting<SettingColor> stopColor = sgColors.add(new ColorSetting.Builder()
        .name("stop-color").description("The milestones still to come.")
        .defaultValue(new SettingColor(255, 255, 255, 220)).build());

    private final Setting<SettingColor> laneColor = sgColors.add(new ColorSetting.Builder()
        .name("lane-color").description("The base line and the +N pill.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("Distance, arrival and the mode initials.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    private static final double LANE_HEIGHT = 20.0;

    /** Where you sit on the lane: fixed, so nothing about you ever moves. */
    private static final double PLAYER_AT = 0.30;

    /** How long a reached milestone takes to fade off the lane, in ms. */
    private static final long FADE_MS = 1500L;

    /** The amber of a bot that has stopped flying without being paused. */
    private static final Color STALL_AMBER = new Color(255, 190, 70, 255);

    /**
     * The distance the lane's far edge stands for, frozen per target.
     *
     * <p>Re-derived every frame it would breathe with each new chunk of route;
     * frozen at target acquisition, the only thing that moves is what should:
     * the milestones sliding toward you.
     */
    private double scaleRef;
    private BlockPos scaleTarget;

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();
    private long lastFrameNanos;

    /** The clock the paused chip runs on; only ever accumulates. */
    private double animTime;

    /** How far the flow arrows have drifted, in pixels. Fed by consumption. */
    private double flowOffset;

    /**
     * The sonar's own phase, advanced by blocks eaten rather than by the
     * clock, so it freezes the moment the route stops being consumed. The
     * debate's one unanimous ruling: integrate the phase, never modulo the
     * clock, or every change of cadence makes it jump.
     */
    private double pingPhase;

    /** Yesterday's distance, for the consumption motor. -1 = no reading yet. */
    private double prevD0 = -1.0;

    /** The halo radius of the frame before, so the handover can contract it. */
    private double lastHaloR;
    private double contractFromR;
    private long prevCompletedAt;

    /** Stall detection: when the speed first dropped, and the eased amber. */
    private long stallSince;
    private double stallBlend;

    /** When the pause began, for the chip's slowing breath. */
    private long pausedSince;

    /** Arrival-estimate regression: only a worsening ETA is news. */
    private int prevEta = -1;
    private long etaRiseAt;
    private double etaWarnFresh;

    /** Fraction of the tour already done, for the rail behind the marker. */
    private double railFrac;

    public FollowerRouteHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.016 : Math.min(0.25, (now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;

        WaypointFollower follower = Modules.get().get(WaypointFollower.class);
        boolean live = MeteorClient.mc.player != null && MeteorClient.mc.world != null
            && follower != null && follower.isActive();

        List<BlockPos> route = live ? follower.hudRoute() : List.of();
        int index = live ? follower.hudRouteIndex() : -1;

        if (!live || index < 0) {
            if (isInEditor()) {
                // The demo needs the frozen scale the live path derives at
                // acquisition, or every milestone lands on the far edge. It
                // also runs the motors at a plausible cruise so the editor
                // shows the lane alive, not embalmed.
                scaleRef = 6000.0;
                scaleTarget = null;
                double demoRate = 38.0;
                flowOffset += demoRate * dt * (16.0 * textScale.get() / 12.0);
                pingPhase = (pingPhase + demoRate * dt / 64.0) % 1.0;
                railFrac = 0.30;
                stallBlend = 0.0;
                etaWarnFresh = 0.0;
                renderLane(renderer, dt, new double[]{980.0, 2200.0, 3900.0, 5200.0}, 14, 40.0,
                    "1 240 m", "~2m 10s", "P40", false, -1.0, 38.0, 0.0f);
                return;
            }

            // No route, no lane: the cockpit says why.
            setSize(0.0, 0.0);
            return;
        }

        // Cumulative distances from you to each milestone, in the order they
        // will actually be visited. In numeric mode that is the list as it
        // stands; in closest mode the follower goes to the nearest and then to
        // the nearest from there, so the lane is built by the same greedy walk
        // — drawn in list order it would zigzag, with milestones behind you.
        boolean closest = follower.hudClosestMode();
        int remaining = closest ? route.size() : route.size() - index;
        int shown = Math.min(visibleStops.get(), remaining);

        List<BlockPos> ahead;
        if (closest) {
            List<BlockPos> pool = new java.util.ArrayList<>(route);
            List<BlockPos> picked = new java.util.ArrayList<>(shown);
            Vec3d from = MeteorClient.mc.player.getEntityPos();

            for (int i = 0; i < shown; i++) {
                int best = 0;
                double bestDistance = Double.MAX_VALUE;

                for (int j = 0; j < pool.size(); j++) {
                    Vec3d at = follower.hudPosOf(pool.get(j));
                    double d = Math.hypot(at.x - from.x, at.z - from.z);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = j;
                    }
                }

                BlockPos pick = pool.remove(best);
                picked.add(pick);
                from = follower.hudPosOf(pick);
            }

            ahead = picked;
        } else {
            ahead = route.subList(index, index + shown);
        }

        double d0 = Math.max(0.0, follower.hudDistance());
        double[] cumulative = new double[shown];
        cumulative[0] = d0;

        Vec3d previous = follower.hudPosOf(ahead.get(0));
        for (int i = 1; i < shown; i++) {
            Vec3d at = follower.hudPosOf(ahead.get(i));
            cumulative[i] = cumulative[i - 1] + Math.hypot(at.x - previous.x, at.z - previous.z);
            previous = at;
        }

        BlockPos target = ahead.get(0);
        if (!target.equals(scaleTarget)) {
            scaleTarget = target;
            // The far edge covers the furthest visible milestone with a margin,
            // and never less than the reach — a lane shorter than its own halo
            // reads as already arrived.
            scaleRef = Math.max(cumulative[shown - 1] * 1.15, Math.max(d0 * 1.3, follower.hudReach() * 4.0));

            // A new target restarts the countdown and swallows the distance
            // jump: the consumption motor must never read a hand-over as a
            // thousand blocks eaten in one frame.
            pingPhase = 0.0;
            prevD0 = -1.0;
        }

        int overflow = remaining - shown;
        int eta = quantiseEta(follower.hudEtaSeconds());

        WaypointFollower.HudState state = follower.hudState();
        boolean paused = state == WaypointFollower.HudState.PAUSED
            || state == WaypointFollower.HudState.AREA_LOADER;

        long sinceDone = System.currentTimeMillis() - follower.hudLastCompletedAt();
        double doneFade = follower.hudLastCompletedAt() > 0L && sinceDone < FADE_MS
            ? sinceDone / (double) FADE_MS
            : -1.0;

        boolean anim = animations.get();
        long nowMs = System.currentTimeMillis();

        // ---- The consumption motor. The debate's verdict on the old flow:
        // a smoothed player speed rolls the carpet while the bot orbits, so
        // the motor is the route actually eaten — smoothed as a RATE, because
        // the distance arrives at tick rate and the frames between ticks must
        // coast rather than stutter.
        double instRate = 0.0;
        if (prevD0 >= 0.0) instRate = Math.max(0.0, prevD0 - d0) / Math.max(1.0e-3, dt);
        prevD0 = d0;
        double rate = pulse.ease("flowrate", Math.min(80.0, instRate), dt, 0.4);

        if (anim && !paused) {
            flowOffset += rate * dt * (16.0 * textScale.get() / 12.0);

            // The sonar's odometer: one pulse per stretch of route, the
            // stretch shrinking from 64 blocks down to 24 as the distance
            // falls — cadence rising toward the arrival, frozen at a stall.
            double spacingBlocks = MathHelper.clamp(d0 / 6.0, 24.0, 64.0);
            pingPhase = (pingPhase + rate * dt / spacingBlocks) % 1.0;
        }

        // ---- Stall: flying nowhere without being paused is the one state
        // the lane used to miss entirely.
        if (!paused && follower.hudSpeed() < 1.5) {
            if (stallSince == 0L) stallSince = nowMs;
        } else {
            stallSince = 0L;
        }
        boolean stalled = stallSince != 0L && nowMs - stallSince > 2500L;
        stallBlend = pulse.ease("stall", stalled ? 1.0 : 0.0, dt, 0.6);

        if (paused) {
            if (pausedSince == 0L) pausedSince = nowMs;
        } else {
            pausedSince = 0L;
        }

        // ---- The arrival estimate: a countdown that counts down is doing its
        // job; only a countdown that goes back up is worth a flash.
        int rawEta = follower.hudEtaSeconds();
        if (rawEta >= 0 && prevEta >= 0 && rawEta > prevEta + Math.max(6, prevEta / 10)) etaRiseAt = nowMs;
        prevEta = rawEta;
        etaWarnFresh = etaRiseAt == 0L ? 0.0 : Math.max(0.0, 1.0 - (nowMs - etaRiseAt) / 2500.0);

        int doneCount = follower.hudCompleted();
        int remainingCount = follower.hudRemaining();
        railFrac = doneCount + remainingCount > 0 ? doneCount / (double) (doneCount + remainingCount) : 0.0;

        // A completion edge freezes the halo radius of the frame before, so
        // the handover has something real to contract.
        long completedAt = follower.hudLastCompletedAt();
        if (completedAt != prevCompletedAt) {
            prevCompletedAt = completedAt;
            contractFromR = lastHaloR;
        }

        // How fresh the hand-over to this target is, for the pop on its dot:
        // keyed on the position, so a re-fit of the same waypoint stays quiet.
        float targetFresh = pulse.freshness("target", target.toShortString(), 600L);

        renderLane(renderer, dt, cumulative, overflow, follower.hudReach(),
            formatDistance(d0),
            paused ? "--" : eta < 0 ? "--" : "~" + formatTime(eta),
            modeInitials(follower.hudModeName()), paused, doneFade,
            follower.hudSpeed(), targetFresh);
    }

    private void renderLane(HudRenderer renderer, double dt, double[] cumulative, int overflow,
                            double reach, String distText, String etaText, String mode,
                            boolean paused, double doneFade, double speedBps, float targetFresh) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double inset = panel.padding();
        double width = laneWidth.get() * scale;
        double laneH = LANE_HEIGHT * scale;
        double lineHeight = renderer.textHeight(shadow, scale);
        double underH = showUnder.get() ? lineHeight + 2.0 * scale : 0.0;
        double height = laneH + underH;

        boolean anim = animations.get();
        animTime += dt;

        HudGlowPanel.Severity severity = paused || stallBlend > 0.5
            ? HudGlowPanel.Severity.WARN
            : HudGlowPanel.Severity.OK;
        panel.draw(renderer, this.x + inset, this.y + inset, width, height, severity);

        double x0 = this.x + inset;
        double y0 = this.y + inset;
        double midY = y0 + laneH / 2.0;
        double playerX = x0 + width * PLAYER_AT;

        // The right end of the lane belongs to the +N pill and the mode
        // letters; the milestones stop short of it, or the pill's grey text
        // ends up underneath them.
        double reserve = 56.0 * scale;
        double span = width - width * PLAYER_AT - reserve;

        SettingColor accent = paused ? laneColor.get() : accentColor.get();
        SettingColor stops = paused ? laneColor.get() : stopColor.get();

        // The base line the whole route rides on.
        renderer.quad(x0, midY - scale, width, 2.0 * scale, laneColor.get());

        // The rail already ridden: the stretch behind the marker fills with
        // the fraction of the tour done. Those pixels used to carry nothing.
        double railW = (playerX - x0) * pulse.ease("rail", railFrac, dt, 0.4);
        if (railW > 0.5) {
            renderer.quad(x0, midY - scale, railW, 2.0 * scale,
                new Color(accent.r, accent.g, accent.b, 90));
        }

        // The whip of the handover: after the strike, a hump of thickness runs
        // from the marker down the lane, rocking each station as it passes —
        // the reached milestone pushing the rest of the route toward you.
        double whipX = -1.0;
        if (anim && !paused && doneFade >= 0.0) {
            double tSec = doneFade * (FADE_MS / 1000.0);
            if (tSec >= 0.18) {
                double candidate = playerX + 420.0 * scale * (tSec - 0.18);
                double laneEnd = x0 + width - reserve;
                if (candidate < laneEnd) {
                    whipX = candidate;
                    double prog = (whipX - playerX) / Math.max(1.0, laneEnd - playerX);
                    double humpH = (2.0 + 4.5 * (1.0 - prog)) * scale;
                    int humpA = (int) (200.0 * (1.0 - prog));
                    if (humpA > 5) {
                        renderer.quad(whipX - 11.0 * scale, midY - humpH / 2.0, 22.0 * scale, humpH,
                            new Color(accent.r, accent.g, accent.b, humpA));
                    }
                }
            }
        }

        // ---- The handover, first half: the milestone just reached.
        // Contract, hold one breath of silence, strike. Times in milliseconds
        // of doneFade — never in frames, which are a different length on
        // every machine.
        if (doneFade >= 0.0) {
            // The deposit: the reached disc sliding off behind you, all 1.5 s.
            double slide = (8.0 + 16.0 * doneFade) * scale;
            Color fade = new Color(accent.r, accent.g, accent.b,
                (int) (accent.a * (1.0 - doneFade) * 0.8));
            disc(renderer, playerX - slide, midY, 3.0 * scale, fade);

            if (anim) {
                if (doneFade < 0.0733 && contractFromR > 2.0) {
                    // 0-110 ms: the old halo is eaten — its ring contracts
                    // onto the marker, brightening as it shrinks.
                    double cf = doneFade / 0.0733;
                    double r = contractFromR + (2.5 * scale - contractFromR) * cf;
                    ring(renderer, playerX, midY, r, (1.5 + 0.9 * cf) * scale,
                        new Color(accent.r, accent.g, accent.b, (int) (150.0 + 105.0 * cf)));
                } else if (doneFade >= 0.12) {
                    // 110-180 ms is the silence. Then the strike: one ring
                    // blown out of the only immobile point of the lane.
                    double u = MathHelper.clamp((doneFade - 0.12) / 0.28, 0.0, 1.0);
                    if (u < 1.0) {
                        double r = (3.0 + 19.0 * (1.0 - Math.pow(1.0 - u, 3.0))) * scale;
                        int a = (int) (190.0 * (1.0 - u) * (1.0 - u));
                        if (a > 5) {
                            ring(renderer, playerX, midY, r, Math.max(1.0, (1.6 - 0.8 * u) * scale),
                                new Color(accent.r, accent.g, accent.b, a));
                        }
                    }
                }
            }
        }

        // You: a triangle that never moves — and the one part of the lane
        // allowed to raise its voice: it turns amber and breathes when the bot
        // has stalled without being paused. The colour is information, so it
        // applies with animations off too; only the breathing is motion.
        Color triC = stallBlend <= 0.01 ? accent
            : new Color(
                (int) (accent.r + (STALL_AMBER.r - accent.r) * stallBlend),
                (int) (accent.g + (STALL_AMBER.g - accent.g) * stallBlend),
                (int) (accent.b + (STALL_AMBER.b - accent.b) * stallBlend),
                accent.a);
        double ps = 5.0 * scale;
        if (anim && stallBlend > 0.01) {
            ps *= 1.0 + 0.12 * stallBlend * (0.5 + 0.5 * Math.sin(animTime * Math.PI * 2.0 / 2.6));
        }
        tri(renderer, playerX - ps, midY - ps, playerX - ps, midY + ps, playerX + ps, midY, triC);

        // Milestones, eased toward their places so a re-fit slides rather than snaps.
        double firstX = 0.0;
        double firstHaloR = 0.0;

        // The handover, second half: the new halo is born at nothing inside
        // the marker's reach and opens as it settles — with animations off it
        // is simply there, full size, at once.
        double open = !anim || doneFade < 0.0 ? 1.0
            : smoothstep(MathHelper.clamp((doneFade - 0.12) / 0.28, 0.0, 1.0));

        for (int i = 0; i < cumulative.length; i++) {
            double t = Math.sqrt(MathHelper.clamp(cumulative[i] / Math.max(1.0, scaleRef), 0.0, 1.0));
            double sx = playerX + span * pulse.ease("stop" + i, t, dt, 0.25) / 1.0;

            if (i == 0) {
                firstX = sx;

                // The reach, as a ring whose size is the reach at this lane's
                // own scale: it meets the player marker exactly when the
                // waypoint will be called reached. A ring and a soft fill, like
                // the mock-up -- round, because the reach is.
                // Never smaller than the mock-up's circle: on a real route the
                // reach at lane scale is a pixel or two, and a ring clamped
                // down there disappears under the dot it is meant to circle.
                double edge = Math.sqrt(MathHelper.clamp((cumulative[0] + reach) / Math.max(1.0, scaleRef), 0.0, 1.0));
                double haloR = MathHelper.clamp((edge - t) * span, 9.0 * scale, 16.0 * scale) * open;
                int ringA = Math.min(255, 150 + (int) (targetFresh * 80.0f));

                if (haloR > 0.5) {
                    disc(renderer, sx, midY, haloR, new Color(accent.r, accent.g, accent.b, 34));
                    ring(renderer, sx, midY, haloR, Math.max(1.0, 1.5 * scale),
                        new Color(accent.r, accent.g, accent.b, ringA));
                }

                // The sonar: one ring per stretch of route eaten, growing from
                // the dot out past the reach and dying there. Its phase rides
                // the odometer, so it freezes with the bot and quickens on its
                // own as the remaining distance shortens.
                if (anim && !paused && open > 0.6) {
                    double f = pingPhase;
                    double pingR = 3.0 * scale + (haloR + 4.0 * scale - 3.0 * scale) * Math.pow(f, 0.6);
                    int pingA = (int) (110.0 * (1.0 - f) * (1.0 - f));
                    if (pingA > 6 && pingR > 3.5 * scale) {
                        ring(renderer, sx, midY, pingR, Math.max(1.0, scale),
                            new Color(accent.r, accent.g, accent.b, pingA));
                    }
                }

                // A fresh target beats its dot once, hardest at the instant of
                // the hand-over, settled within the quarter second.
                double dotPop = anim ? 1.0 + 0.55 * targetFresh * targetFresh : 1.0;
                disc(renderer, sx, midY, 3.2 * scale * dotPop, stops);
                firstHaloR = haloR;
                lastHaloR = haloR;
            } else {
                // The milestones after the current one are station ticks on the
                // line, as the mock-up draws them -- short grey bars, not dots.
                // The whip of a fresh arrival rocks each one as it passes.
                Color tick = new Color(Math.min(255, laneColor.get().r + 40),
                    Math.min(255, laneColor.get().g + 40),
                    Math.min(255, laneColor.get().b + 45), 230);
                double tickH = 9.0 * scale;
                if (whipX >= 0.0) {
                    double bump = Math.max(0.0, 1.0 - Math.abs(sx - whipX) / (14.0 * scale));
                    tickH *= 1.0 + 0.5 * bump;
                }
                renderer.quad(sx - 1.0 * scale, midY - tickH / 2.0, 2.0 * scale, tickH, tick);
            }
        }

        // The ten-second mark: where you will be at the present pace. It
        // touches the halo exactly ten seconds before the arrival, and if the
        // bot slows it slides back toward you — a vane, read without numbers.
        if (anim && !paused && speedBps > 1.0 && cumulative.length > 0) {
            double leadDist = cumulative[0] - speedBps * 10.0;
            double lx;
            int la = 90;

            if (leadDist <= 0.0) {
                lx = playerX;
            } else {
                lx = playerX + span * Math.sqrt(MathHelper.clamp(leadDist / Math.max(1.0, scaleRef), 0.0, 1.0));
            }

            double lim = firstX - firstHaloR - 2.0 * scale;
            if (lx > lim) {
                lx = lim;
                la = 150;
            }

            if (lx >= playerX) {
                renderer.quad(lx - 0.5 * scale, midY - 3.5 * scale, Math.max(1.0, scale), 7.0 * scale,
                    new Color(accent.r, accent.g, accent.b, la));
            }
        }

        // The travel flow: little arrows streaming from you toward the current
        // milestone. Their motor is the route being consumed, so they stand
        // still the moment the bot stops making progress — a stopped conveyor,
        // not an empty one — and in pause they hold, half-dimmed.
        if (anim) {
            double flowFrom = playerX + 9.0 * scale;
            double flowTo = firstX - firstHaloR - 3.0 * scale;

            if (flowTo - flowFrom > 26.0 * scale) {
                double spacing = 16.0 * scale;
                double off = flowOffset % spacing;

                for (double ax = flowFrom + off; ax < flowTo; ax += spacing) {
                    double fadeIn = MathHelper.clamp((ax - flowFrom) / (10.0 * scale), 0.0, 1.0);
                    double fadeOut = MathHelper.clamp((flowTo - ax) / (14.0 * scale), 0.0, 1.0);
                    int a = (int) (80.0 * fadeIn * fadeOut * (paused ? 0.5 : 1.0));
                    if (a <= 4) continue;

                    double as = 2.4 * scale;
                    tri(renderer, ax - as, midY - as * 0.9, ax - as, midY + as * 0.9, ax + as, midY,
                        new Color(accent.r, accent.g, accent.b, a));
                }
            }
        }

        // The lane's reserved right end: the +N pill, then the mode
        // letters, both sitting on the lane row itself. PAUSED is not a mode: it is the
        // chip at the head of the lane, where the mock-up puts it, and the mode
        // letters go away with the flying they describe.
        double textY = midY - lineHeight / 2.0;
        double tailX = x0 + width;

        if (paused) {
            // The chip's plate stands still — a breathing plate makes its whole
            // edge shimmer — and the word itself carries the pulse, slowing
            // with the age of the pause: a fresh pause breathes, a long one is
            // nearly asleep. The one motion of a frozen lane.
            String chip = "PAUSED";
            double cw = renderer.textWidth(chip, shadow, scale * 0.8);
            renderer.quad(x0, y0, cw + 8.0 * scale, lineHeight + 2.0 * scale, new Color(74, 82, 97, 165));

            int chipTextA = 255;
            if (anim) {
                double pauseAge = pausedSince == 0L ? 0.0 : (System.currentTimeMillis() - pausedSince) / 1000.0;
                double chipT = MathHelper.clamp(2.0 + pauseAge / 20.0, 2.0, 4.5);
                chipTextA = (int) (150.0 + 85.0 * (0.5 + 0.5 * Math.sin(animTime * Math.PI * 2.0 / chipT)));
            }
            renderer.text(chip, x0 + 4.0 * scale, y0 + 1.0 * scale,
                new Color(205, 211, 221, chipTextA), shadow, scale * 0.8);
        } else {
            double tailW = renderer.textWidth(mode, shadow, scale * 0.85);
            tailX = x0 + width - tailW;
            renderer.text(mode, tailX, textY, laneColor.get(), shadow, scale * 0.85);
        }

        if (overflow > 0) {
            // The pill takes a brief accent tint whenever its count moves, so
            // a shrinking route shows its progress even out in the overflow.
            String more = "+" + overflow;
            float overflowFresh = anim ? pulse.freshness("overflow", more, 800L) : 0.0f;
            double mw = renderer.textWidth(more, shadow, scale * 0.85);
            double px = tailX - mw - 10.0 * scale;
            renderer.quad(px - 3.0 * scale, textY - 1.0 * scale, mw + 6.0 * scale, lineHeight + 2.0 * scale,
                new Color(laneColor.get().r, laneColor.get().g, laneColor.get().b, 60));
            renderer.text(more, px, textY,
                HudPulse.tint(HudPulse.Style.Subtle, laneColor.get(), accentColor.get(), overflowFresh),
                shadow, scale * 0.85);
        }

        if (showUnder.get()) {
            // Two tones, as drawn in the mock-up: the distance bright, the
            // arrival dimmed behind its dot.
            String bright = distText;
            String dim = " · " + etaText;
            double bw = renderer.textWidth(bright, shadow, scale);
            double uw = bw + renderer.textWidth(dim, shadow, scale);

            // Centred under the current milestone, but never sliding into the
            // lane's reserved right end -- the +N pill and the mode live there,
            // and the three used to pile up into one unreadable corner.
            double maxX = Math.max(x0 + 2.0 * scale, x0 + width - reserve - uw);
            double ux = MathHelper.clamp(firstX - uw / 2.0, x0 + 2.0 * scale, maxX);
            double uy = y0 + laneH + 1.0 * scale;

            // The odometer's tick: each hundred metres eaten warms the
            // distance for a third of a second — terrain covered, said in
            // colour, in place of the metronome the estimate used to be.
            float distFresh = anim && !paused && cumulative.length > 0
                ? pulse.freshness("dist100", String.valueOf((int) (cumulative[0] / 100.0)), 350L)
                : 0.0f;
            renderer.text(bright, ux, uy,
                paused ? laneColor.get()
                    : HudPulse.tint(HudPulse.Style.Subtle, textColor.get(), accentColor.get(), distFresh),
                shadow, scale);

            // The arrival estimate flashes amber only when it has grown — a
            // countdown counting down is doing its job in silence.
            Color dimC = etaWarnFresh > 0.01 && !paused
                ? new Color(
                    (int) (laneColor.get().r + (STALL_AMBER.r - laneColor.get().r) * etaWarnFresh),
                    (int) (laneColor.get().g + (STALL_AMBER.g - laneColor.get().g) * etaWarnFresh),
                    (int) (laneColor.get().b + (STALL_AMBER.b - laneColor.get().b) * etaWarnFresh),
                    255)
                : laneColor.get();
            renderer.text(dim, ux + bw, uy, dimC, shadow, scale);
        }

        setSize(width + inset * 2.0, height + inset * 2.0);
    }

    private static double smoothstep(double x) {
        return x * x * (3.0 - 2.0 * x);
    }

    /** A filled disc: a fan of triangles from the centre, cull-safe. */
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

    /**
     * A triangle that survives the UI pipeline's culling — same guard as the
     * radar HUD: wound so the signed area is negative in y-down screen space.
     */
    private void tri(HudRenderer renderer, double x1, double y1, double x2, double y2, double x3, double y3,
                     Color colour) {
        double cross = (x2 - x1) * (y3 - y1) - (x3 - x1) * (y2 - y1);

        if (cross > 0.0) renderer.triangle(x1, y1, x3, y3, x2, y2, colour);
        else renderer.triangle(x1, y1, x2, y2, x3, y3, colour);
    }

    private static String modeInitials(String mode) {
        return switch (mode) {
            case "PITCH40" -> "P40";
            case "ROCKETFLY" -> "RF";
            case "BARITONE" -> "BAR";
            default -> "MAN";
        };
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
