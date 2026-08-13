package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.util.ElytraFlightMath;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import java.util.HashMap;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

/**
 * Where you are going, how fast, and whether you will get there.
 *
 * <p>Aims at an Xaero waypoint by name, so it works whether a follower is flying or you are
 * steering yourself. The three numbers that matter on a long haul — distance, arrival, speed —
 * plus the one nobody computes: whether the elytra you carry actually covers the trip.
 */
public class TravelHud extends HudElement {
    public static final HudElementInfo<TravelHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "Travel",
        "Distance, ETA and speed toward a named waypoint, checked against elytra range.",
        TravelHud::new);

    public enum Format {
        Compact,
        Detailed
    }

    /** What the arrival time is divided by. */
    public enum EtaBasis {
        Progress,
        Cruise
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDisplay = settings.createGroup("Display");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<String> destination = sgGeneral.add(new StringSetting.Builder()
        .name("destination")
        .description("Name, or start of a name, of the Xaero waypoint to aim at. Left empty, the nearest waypoint carrying the fallback prefix is used.")
        .defaultValue("").build());

    private final Setting<String> fallbackPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("fallback-prefix")
        .description("Prefix used when destination is empty. Matches the follower's own prefix so an unattended run is picked up without typing anything.")
        .defaultValue("Hunt_").build());

    private final Setting<Double> smoothing = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-smoothing")
        .description("Time constant of the speed average, in seconds. Elytra speed swings from 60 to 20 between boosts, and a raw reading makes the arrival time jump around unreadably. Higher is steadier and slower to react.")
        .defaultValue(1.0).min(0.2).max(5.0).sliderRange(0.2, 3.0).build());

    private final Setting<Double> cruiseWindow = sgGeneral.add(new DoubleSetting.Builder()
        .name("eta-averaging")
        .description("Seconds of flight the arrival time is averaged over. Long enough to swallow a climb-and-glide cycle, so the estimate settles instead of chasing every boost.")
        .defaultValue(45.0).min(5.0).max(300.0).sliderRange(10.0, 120.0).build());

    private final Setting<EtaBasis> etaBasis = sgGeneral.add(new EnumSetting.Builder<EtaBasis>()
        .name("eta-basis")
        .description("Progress divides by how fast the target is actually getting closer, so detours and a winding trail count against the estimate. Cruise divides by ground speed, which assumes you are flying straight at it.")
        .defaultValue(EtaBasis.Progress).build());

    private final Setting<Double> reservePct = sgGeneral.add(new DoubleSetting.Builder()
        .name("elytra-reserve-pct")
        .description("Durability kept back on every elytra when working out how far you can still fly.")
        .defaultValue(2.0).min(0.0).sliderRange(0.0, 20.0).build());

    private final Setting<Format> format = sgDisplay.add(new EnumSetting.Builder<Format>()
        .name("format").description("One dense line, or one field per line.")
        .defaultValue(Format.Compact).build());

    private final Setting<Boolean> showHeading = sgDisplay.add(new BoolSetting.Builder()
        .name("show-heading-arrow")
        .description("An arrow for the bearing to the target, green when you are pointed at it. The only field that shows drift as it happens.")
        .defaultValue(true).build());

    private final Setting<Boolean> showProgress = sgDisplay.add(new BoolSetting.Builder()
        .name("show-progress-bar")
        .description("A bar that fills as the distance closes, measured from where the target was first picked up.")
        .defaultValue(true).build());

    private final Setting<HudPulse.Style> animationStyle = sgDisplay.add(
        new EnumSetting.Builder<HudPulse.Style>()
            .name("bar-style")
            .description("Subtle glides the fill and shades it from red to green as you close in. Lively adds a highlight sweeping the filled part.")
            .defaultValue(HudPulse.Style.Subtle)
            .visible(showProgress::get).build());

    private final Setting<Integer> barHeightPx = sgDisplay.add(new IntSetting.Builder()
        .name("bar-height").description("Thickness of the progress bar, in pixels.")
        .defaultValue(3).min(1).max(10).sliderRange(1, 6)
        .visible(showProgress::get).build());

    private final Setting<Boolean> showSessionDistance = sgDisplay.add(new BoolSetting.Builder()
        .name("show-session-distance").description("Total ground covered since this element started counting.")
        .defaultValue(true).build());

    private final Setting<Boolean> showRangeCheck = sgDisplay.add(new BoolSetting.Builder()
        .name("check-elytra-range")
        .description("Turn the arrival time red when the elytra you carry does not cover the trip, and say how much is missing. An arrival time you cannot reach is worse than none.")
        .defaultValue(true).build());

    private final Setting<Boolean> showArrivalClock = sgDisplay.add(new BoolSetting.Builder()
        .name("show-arrival-clock").description("Add the wall-clock time you are due to land.")
        .defaultValue(false).build());

    private final Setting<Double> textScale = sgDisplay.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgDisplay.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("Labels, units and separators.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("Values in normal range.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> goodColor = sgColors.add(new ColorSetting.Builder()
        .name("good-color").description("On course, and close to arrival.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final Setting<SettingColor> warnColor = sgColors.add(new ColorSetting.Builder()
        .name("warn-color").description("Off course.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> dangerColor = sgColors.add(new ColorSetting.Builder()
        .name("danger-color").description("Not enough elytra to get there.")
        .defaultValue(new SettingColor(255, 80, 80, 255)).build());

    /** Smoothed ground speed, blocks per second. Shown as the b/s reading.  */
    private double speed;

    /**
     * The same speed averaged far more slowly, and what the arrival time is built on.
     *
     * <p>A Pitch40 flight is not one speed: you climb at forty and glide back down at thirty for
     * much longer, so an estimate built on the reading of the moment swings by a third and never
     * settles. Averaged over a minute those cycles cancel and what is left is the speed you are
     * actually covering ground at — which is the only one that predicts an arrival.
     */
    private double cruiseSpeed;

    /** Last speed worth estimating from, kept so a stop does not blank the arrival time. */
    private double lastGoodSpeed;

    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;
    private long lastNanos;
    private double sessionDistance;

    /** Dimension the baselines belong to, so they are dropped when it changes. */
    private String baselineWorld = "";

    /**
     * Distance at which each destination was first picked up, keyed by name.
     *
     * <p>Per destination rather than one shared baseline: switching target mid-flight would
     * otherwise leave the bar measuring progress toward somewhere you are no longer going.
     */
    private final Map<String, Double> baselines = new HashMap<>();

    /** When each baseline was set, so progress since then can be turned into a speed. */
    private final Map<String, Long> baselineAt = new HashMap<>();

    /**
     * How fast the target is actually getting closer, smoothed.
     *
     * <p>Ground speed answers "how fast am I moving"; this answers "how fast am I arriving", and
     * on anything but a straight run they are different numbers. Following a trail that winds, or
     * detouring around a stash, covers ground at full speed while closing on the destination at a
     * fraction of it — and an estimate built on the first is optimistic by exactly that fraction.
     */
    private double closingSpeed;

    /** Target the closing speed belongs to, so switching destination does not inherit it. */
    private String closingTarget = "";

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();

    /** Length of the last frame, so the bar eases at the same rate on any machine. */
    private double lastFrameSeconds = 0.016;

    private final SettingGroup sgPanel = settings.createGroup("Panel");
    private final HudGlowPanel panel = new HudGlowPanel(this.sgPanel);

    public TravelHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double lineHeight = renderer.textHeight(shadow, scale);

        boolean live = MeteorClient.mc.player != null && MeteorClient.mc.world != null;

        if (live) tickMotion();

        Target target = live ? findTarget() : null;

        if (target == null) {
            // Both ways out of here used to happen before the format switch, so the editor —
            // where there is rarely a waypoint to aim at — only ever saw "no destination", and
            // toggling Compact against Detailed changed nothing on screen.
            if (isInEditor()) {
                renderDemo(renderer, shadow, scale, lineHeight);
                return;
            }

            if (!live) {
                setSize(90.0, lineHeight);
                return;
            }

            renderer.text("no destination", this.x, this.y, labelColor.get(), shadow, scale);
            setSize(renderer.textWidth("no destination", shadow, scale), lineHeight);
            return;
        }

        double dx = target.x - MeteorClient.mc.player.getX();
        double dz = target.z - MeteorClient.mc.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Distances are expressed in the current dimension, so a baseline measured in one is
        // meaningless in the other — through a portal the bar would read as nearly complete or
        // barely begun depending on the direction.
        String worldKey = MeteorClient.mc.world.getRegistryKey().getValue().toString();
        if (!worldKey.equals(baselineWorld)) {
            baselines.clear();
            baselineAt.clear();
            baselineWorld = worldKey;
        }

        Double previous = baselines.get(target.name);
        double baseline = previous == null ? distance : Math.max(previous, distance);
        baselines.put(target.name, baseline);

        // The clock is tied to the baseline, not to the first sighting. The baseline rises again
        // whenever you end up further out than you have ever been from this target, and measuring
        // progress from an old mark against a new distance would report ground you never gained.
        if (previous == null || baseline > previous) baselineAt.put(target.name, System.currentTimeMillis());

        // Estimated on the last speed you actually held when you are stopped, rather than
        // giving up. Standing still to check the bag is exactly when the shortfall is worth
        // reading, and blanking it there made the red warning vanish at the one moment it
        // could still change your mind.
        if (cruiseSpeed >= 2.0) lastGoodSpeed = cruiseSpeed;
        double etaSpeed = cruiseSpeed >= 2.0 ? cruiseSpeed : lastGoodSpeed;

        if (etaBasis.get() == EtaBasis.Progress) {
            double closing = trackClosing(target.name, baseline, distance);
            if (closing > 0.0) etaSpeed = closing;
        }

        int etaSeconds = etaSpeed >= 2.0 ? quantiseEta((int) (distance / etaSpeed)) : -1;
        int rangeSeconds = ElytraFlightMath.totalUsableSeconds(MeteorClient.mc.player, reservePct.get());
        boolean short_ = showRangeCheck.get() && etaSeconds > 0 && rangeSeconds < etaSeconds;

        if (format.get() == Format.Compact) {
            renderCompact(renderer, target, distance, etaSeconds, rangeSeconds, short_, baseline, dx, dz,
                shadow, scale, lineHeight);
        } else {
            renderDetailed(renderer, target, distance, etaSeconds, rangeSeconds, short_, baseline, dx, dz,
                shadow, scale, lineHeight);
        }
    }

    /**
     * The editor preview: the chosen format, drawn from made-up but plausible numbers.
     *
     * <p>It runs the real render path rather than a mock-up of it, so what you line the panel up
     * against in the editor is what will be on screen in flight — every setting, down to the bar
     * style and the arrival clock, behaves here exactly as it will there.
     *
     * <p>The bearing is derived from where you are actually looking so the arrow reads as on
     * course, which is the state worth laying the element out against.
     */
    private void renderDemo(HudRenderer renderer, boolean shadow, double scale, double lineHeight) {
        Target target = new Target("Hunt_Base", 0.0, 0.0);

        double distance = 12_400.0;
        int eta = 740;
        int rangeSeconds = 900;

        // Distance is 30% of the baseline, so the bar sits at 70%.
        double baseline = distance / 0.3;

        double bearing = Math.toRadians(playerYaw() + 90.0);
        double dx = Math.cos(bearing) * distance;
        double dz = Math.sin(bearing) * distance;

        // Both readings come from fields the live path owns. Borrowed rather than assigned: the
        // editor is opened in-game, and leaving 8.3 km behind in the session odometer would be a
        // real number quietly replaced by a decorative one.
        double savedSpeed = speed;
        double savedSession = sessionDistance;

        speed = 48.0;
        sessionDistance = 8_300.0;

        try {
            if (format.get() == Format.Compact) {
                renderCompact(renderer, target, distance, eta, rangeSeconds, false, baseline, dx, dz,
                    shadow, scale, lineHeight);
            } else {
                renderDetailed(renderer, target, distance, eta, rangeSeconds, false, baseline, dx, dz,
                    shadow, scale, lineHeight);
            }
        } finally {
            speed = savedSpeed;
            sessionDistance = savedSession;
        }
    }

    /** Zero with no player, so the editor preview can ask before there is one. */
    private static float playerYaw() {
        return MeteorClient.mc.player == null ? 0.0f : MeteorClient.mc.player.getYaw();
    }

    /**
     * Advances the speed average and the session odometer.
     *
     * <p>Both use the same guard the follower uses on its own odometer: a step over a hundred
     * blocks in one frame is a teleport or a dimension change, not travel, and folding it in
     * would poison the average and add kilometres that were never flown.
     */
    private void tickMotion() {
        double x = MeteorClient.mc.player.getX();
        double z = MeteorClient.mc.player.getZ();

        long now = System.nanoTime();
        double dt = lastNanos == 0L ? 0.0 : (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;

        if (!Double.isNaN(lastX) && dt > 0.0 && dt < 1.0) {
            double step = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ));

            if (step < 100.0) {
                sessionDistance += step;

                // Read from the velocity vector rather than from the frame-to-frame step.
                // Position only advances on a tick, twenty times a second, while this runs once
                // per frame — so the step samples a per-tick movement over a per-frame interval,
                // alternating between zero and several times the truth. The average of that is
                // right, but only on average, and every irregularity in frame pacing leans on
                // it. The velocity vector is already a per-tick displacement and needs nothing
                // but the factor twenty; it is what SpeedKMH has always used in this addon.
                var velocity = MeteorClient.mc.player.getVelocity();
                double instant = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;

                // Exponentially weighted, with the weight derived from the elapsed time rather
                // than fixed per frame. A constant factor would fold in two hundred samples a
                // second at 200 fps and thirty at 30, so the same setting would give a time
                // constant six times apart — the estimate turning twitchy on a good machine and
                // sluggish when the game struggles, and changing character mid-flight as the
                // load moves. Tying it to dt makes the response identical at any framerate.
                double alpha = 1.0 - Math.exp(-dt / Math.max(0.05, smoothing.get()));
                speed += alpha * (instant - speed);

                // Same maths, a far longer constant. Seeded from the fast reading on the first
                // sample so the estimate is usable straight away rather than crawling up from
                // zero for the first minute.
                if (cruiseSpeed <= 0.0) cruiseSpeed = instant;
                double cruiseAlpha = 1.0 - Math.exp(-dt / Math.max(1.0, cruiseWindow.get()));
                cruiseSpeed += cruiseAlpha * (instant - cruiseSpeed);
            }
        }

        lastX = x;
        lastZ = z;
        if (dt > 0.0 && dt < 1.0) lastFrameSeconds = dt;
    }

    /**
     * The rate the target is closing at, or 0 while there is not yet enough of it to trust.
     *
     * <p>Averaged over the whole approach rather than sampled: the quantity wanted is exactly
     * "ground gained divided by time taken", which already carries every detour and every pause
     * in it. The smoothing on top is only there so the reading does not step when the baseline is
     * re-marked.
     *
     * <p>It stays quiet until fifty blocks and five seconds have gone by. Before that the divisor
     * is small enough that a boost or a bend swings the answer wildly, and a wrong arrival time
     * is worse than the honest fallback to ground speed.
     */
    private double trackClosing(String name, double baseline, double distance) {
        if (!name.equals(closingTarget)) {
            closingTarget = name;
            closingSpeed = 0.0;
        }

        Long since = baselineAt.get(name);
        if (since == null) return 0.0;

        double gained = baseline - distance;
        double elapsed = (System.currentTimeMillis() - since) / 1000.0;

        if (gained < 50.0 || elapsed < 5.0) return 0.0;

        double sample = gained / elapsed;

        // Seeded outright the first time so the estimate is usable the moment it qualifies,
        // rather than climbing out of zero and reading as hours remaining while it does.
        if (closingSpeed <= 0.0) closingSpeed = sample;
        else closingSpeed += (1.0 - Math.exp(-lastFrameSeconds / Math.max(1.0, cruiseWindow.get())))
            * (sample - closingSpeed);

        return closingSpeed;
    }

    /**
     * The waypoint being aimed at, already converted into the player's frame.
     *
     * <p>The conversion is the whole difficulty. A waypoint may be stored in Overworld
     * coordinates while you fly the Nether, and the ratio between the two is not a constant to
     * hardcode: XaeroPlus has a "prefer overworld waypoints" mode that changes what is stored,
     * so an eight written into the code would be wrong by a factor of eight in exactly the case
     * this element exists for. The session is asked instead.
     */
    private Target findTarget() {
        String wanted = destination.get().trim();

        // With nothing typed, whatever is actually flying you takes precedence over a waypoint
        // that merely happens to be nearby: if Baritone is heading somewhere, that is where you
        // are going.
        //
        // Its coordinates are used raw. Baritone's goals already live in the dimension you are
        // standing in, so the conversion the Xaero waypoints need would be wrong here — and
        // wrong by a factor of eight in the Nether, which is where this matters most.
        if (wanted.isEmpty()) {
            int[] goal = BaritoneHelper.getGoalXZ();
            if (goal != null) return new Target("Baritone", goal[0], goal[1]);
        }

        try {
            MinimapSession session = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) return null;

            MinimapWorld world = session.getWorldManager().getCurrentWorld();
            if (world == null) return null;

            WaypointSet set = world.getCurrentWaypointSet();
            if (set == null) return null;

            double division = session.getDimensionHelper().getDimensionDivision(world);
            String prefix = fallbackPrefix.get();

            Waypoint best = null;
            double bestDistance = Double.MAX_VALUE;

            for (Waypoint wp : set.getWaypoints()) {
                String name = wp.getName();
                if (name == null) continue;

                if (!wanted.isEmpty()) {
                    if (!name.equalsIgnoreCase(wanted) && !name.toLowerCase().startsWith(wanted.toLowerCase())) continue;
                } else if (prefix.isEmpty() || !name.startsWith(prefix)) {
                    continue;
                }

                // An exact name wins outright, whatever the distance. Ranking it by proximity
                // like the others let a nearer waypoint that merely starts with the same text
                // take precedence over the one actually named — so typing a full name could
                // aim somewhere else entirely.
                if (!wanted.isEmpty() && name.equalsIgnoreCase(wanted)) {
                    best = wp;
                    break;
                }

                double x = wp.getX() * division;
                double z = wp.getZ() * division;
                double dx = x - MeteorClient.mc.player.getX();
                double dz = z - MeteorClient.mc.player.getZ();
                double d = dx * dx + dz * dz;

                // Otherwise the nearest match, which for the fallback is the one a follower is
                // heading for next.
                if (d < bestDistance) {
                    bestDistance = d;
                    best = wp;
                }
            }

            if (best == null) return null;
            return new Target(best.getName(), best.getX() * division, best.getZ() * division);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderCompact(HudRenderer renderer, Target target, double distance, int eta,
                               int rangeSeconds, boolean short_, double baseline, double dx, double dz,
                               boolean shadow, double scale, double lineHeight) {
        double yLine = this.y + panel.padding();
        double pad = 3.0 * scale;
        double inset = panel.padding();

        // Measured before anything is drawn, because the panel goes underneath and the width is
        // only known once every field has been laid out. The dry pass costs a few text
        // measurements; drawing first and backfilling is not an option with no depth to sort by.
        double contentWidth = measureCompact(renderer, target, distance, eta, rangeSeconds, short_,
            dx, dz, shadow, scale, pad);
        double barHeight = showProgress.get() && baseline > 1.0 ? (barHeightPx.get() + 1) * scale : 0.0;

        panel.draw(renderer, this.x + inset, this.y + inset, contentWidth, lineHeight + barHeight,
            severityOf(short_, dx, dz));

        double x = this.x + inset;

        if (showHeading.get()) {
            x = drawSegment(renderer, x, yLine, headingArrow(dx, dz), headingColor(dx, dz), shadow, scale);
            x += pad;
        }

        x = drawSegment(renderer, x, yLine, target.name, valueColor.get(), shadow, scale);
        x = separator(renderer, x, yLine, pad, shadow, scale);

        // Every volatile field gets a slot sized for its own worst case, so a number growing a
        // digit never pushes its neighbours along the line.
        x = drawFixed(renderer, x, yLine, formatDistance(distance), "00000.0 km",
            valueColor.get(), shadow, scale);
        x = separator(renderer, x, yLine, pad, shadow, scale);
        x = drawFixed(renderer, x, yLine, eta < 0 ? "--" : formatTime(eta), "000h 00m",
            short_ ? dangerColor.get() : (eta >= 0 && eta < 60 ? goodColor.get() : valueColor.get()),
            shadow, scale);

        if (short_) {
            x = drawFixed(renderer, x, yLine, " elytra -" + formatTime(eta - rangeSeconds),
                " elytra -000h 00m", dangerColor.get(), shadow, scale);
        }

        x = separator(renderer, x, yLine, pad, shadow, scale);
        x = drawFixed(renderer, x, yLine, String.format("%.0f", speed), "000",
            valueColor.get(), shadow, scale);
        x = drawSegment(renderer, x, yLine, " b/s", labelColor.get(), shadow, scale);

        if (showArrivalClock.get() && eta >= 0) {
            x = separator(renderer, x, yLine, pad, shadow, scale);
            x = drawSegment(renderer, x, yLine, arrivalClock(eta), labelColor.get(), shadow, scale);
        }

        if (showSessionDistance.get()) {
            x = separator(renderer, x, yLine, pad, shadow, scale);
            x = drawFixed(renderer, x, yLine, formatDistance(sessionDistance), "00000.0 km",
                labelColor.get(), shadow, scale);
        }

        double height = lineHeight + barHeight;

        if (showProgress.get() && baseline > 1.0) {
            drawProgressBar(renderer, this.x + inset, yLine + lineHeight + 1.0 * scale, contentWidth, barHeightPx.get() * scale,
                progressOf(distance, baseline));
        }

        setSize(contentWidth + inset * 2.0, height + inset * 2.0);
    }

    /** Lays the compact line out without drawing it, to size the panel that goes under it. */
    private double measureCompact(HudRenderer renderer, Target target, double distance, int eta,
                                  int rangeSeconds, boolean short_, double dx, double dz,
                                  boolean shadow, double scale, double pad) {
        double sep = pad * 2 + renderer.textWidth("·", shadow, scale);
        double w = 0.0;

        // The glyph actually drawn, not a stand-in: "^" and "<" need not be the same width, and
        // measuring one while drawing the other slides the panel by a pixel or two on this field.
        if (showHeading.get()) w += renderer.textWidth(headingArrow(dx, dz), shadow, scale) + pad;

        w += renderer.textWidth(target.name, shadow, scale);
        w += sep + slot(renderer, formatDistance(distance), "00000.0 km", shadow, scale);
        w += sep + slot(renderer, eta < 0 ? "--" : formatTime(eta), "000h 00m", shadow, scale);

        if (short_) {
            w += slot(renderer, " elytra -" + formatTime(eta - rangeSeconds), " elytra -000h 00m",
                shadow, scale);
        }

        w += sep + slot(renderer, String.format("%.0f", speed), "000", shadow, scale);
        w += renderer.textWidth(" b/s", shadow, scale);

        if (showArrivalClock.get() && eta >= 0) {
            w += sep + renderer.textWidth(arrivalClock(eta), shadow, scale);
        }

        if (showSessionDistance.get()) {
            w += sep + slot(renderer, formatDistance(sessionDistance), "00000.0 km", shadow, scale);
        }

        return Math.max(w, 90.0);
    }

    /** Widest row of the detailed layout, from the templates rather than the live values. */
    private double measureDetailed(HudRenderer renderer, Target target, boolean shadow, double scale) {
        double labelWidth = renderer.textWidth("Travelled ", shadow, scale);
        double widest = renderer.textWidth(target.name, shadow, scale);

        for (String template : new String[]{"00000.0 km", "000h 00m  (elytra -000h 00m)", "000 b/s"}) {
            widest = Math.max(widest, renderer.textWidth(template, shadow, scale));
        }

        return Math.max(labelWidth + widest, 90.0);
    }

    private double slot(HudRenderer renderer, String text, String template, boolean shadow, double scale) {
        return Math.max(renderer.textWidth(text, shadow, scale), renderer.textWidth(template, shadow, scale));
    }

    /**
     * State of the line as a whole, for the halo.
     *
     * <p>Not enough elytra outranks everything: it is the one condition that ends the flight
     * rather than merely making it untidy. Drift is a warning, because it costs distance but
     * corrects itself as soon as the follower turns.
     */
    private HudGlowPanel.Severity severityOf(boolean short_, double dx, double dz) {
        if (short_) return HudGlowPanel.Severity.CRITICAL;

        if (showHeading.get()) {
            double off = Math.abs(MathHelper.wrapDegrees(
                Math.toDegrees(Math.atan2(dz, dx)) - 90.0 - playerYaw()));
            if (off > 5.0) return HudGlowPanel.Severity.WARN;
        }

        return HudGlowPanel.Severity.OK;
    }

    private void renderDetailed(HudRenderer renderer, Target target, double distance, int eta,
                                int rangeSeconds, boolean short_, double baseline, double dx, double dz,
                                boolean shadow, double scale, double lineHeight) {
        // Two passes here as well: every row is measured, then the panel is laid down, then the
        // rows are drawn. The widest row sets the panel, so none of them can be drawn first.
        double inset = panel.padding();
        double rows = 4
            + (showHeading.get() ? 1 : 0)
            + (showSessionDistance.get() ? 1 : 0);
        double barHeight = showProgress.get() && baseline > 1.0 ? (barHeightPx.get() + 1) * scale : 0.0;
        double contentWidth = measureDetailed(renderer, target, shadow, scale);

        panel.draw(renderer, this.x + inset, this.y + inset, contentWidth,
            rows * lineHeight + barHeight, severityOf(short_, dx, dz));

        double y = this.y + inset;

        drawRow(renderer, y, "Dest", target.name, target.name, valueColor.get(), shadow, scale);
        y += lineHeight;
        drawRow(renderer, y, "Dist", formatDistance(distance), "00000.0 km", valueColor.get(), shadow, scale);
        y += lineHeight;

        String etaText = eta < 0 ? "--" : formatTime(eta);
        if (short_) etaText += "  (elytra -" + formatTime(eta - rangeSeconds) + ")";
        drawRow(renderer, y, "ETA", etaText, "000h 00m  (elytra -000h 00m)",
            short_ ? dangerColor.get() : valueColor.get(), shadow, scale);
        y += lineHeight;

        drawRow(renderer, y, "Speed", String.format("%.0f b/s", speed),
            "000 b/s", valueColor.get(), shadow, scale);
        y += lineHeight;

        if (showHeading.get()) {
            drawRow(renderer, y, "Heading", headingArrow(dx, dz), "^",
                headingColor(dx, dz), shadow, scale);
            y += lineHeight;
        }

        if (showSessionDistance.get()) {
            drawRow(renderer, y, "Travelled", formatDistance(sessionDistance),
                "00000.0 km", valueColor.get(), shadow, scale);
            y += lineHeight;
        }

        if (showProgress.get() && baseline > 1.0) {
            drawProgressBar(renderer, this.x + inset, y + 1.0 * scale, contentWidth, barHeightPx.get() * scale,
                progressOf(distance, baseline));
        }

        setSize(contentWidth + inset * 2.0, rows * lineHeight + barHeight + inset * 2.0);
    }

    /**
     * How much of the trip is behind you, clamped to 0-1.
     *
     * <p>The clamp is not cosmetic: pick a target while flying away from it and the current
     * distance exceeds the baseline, which without this would drive the bar past its own ends.
     */
    private static double progressOf(double distance, double baseline) {
        return MathHelper.clamp(1.0 - distance / baseline, 0.0, 1.0);
    }

    /**
     * The trip so far, as a bar that moves the way the trip does.
     *
     * <p>The fill is eased rather than set: distance jumps around on a boost, and a bar snapping
     * with it reads as broken. It also shades from the danger colour through the warning one to
     * green, so the far end of a long haul is legible at a glance without reading the number.
     */
    private void drawProgressBar(HudRenderer renderer, double x, double y, double width, double height,
                                 double progress) {
        renderer.quad(x, y, width, height, labelColor.get());

        double eased = pulse.ease("bar", progress, Math.max(0.001, lastFrameSeconds), 0.35);
        if (eased <= 0.0) return;

        double filled = width * eased;
        SettingColor from = eased < 0.5 ? dangerColor.get() : warnColor.get();
        SettingColor to = eased < 0.5 ? warnColor.get() : goodColor.get();
        float t = (float) (eased < 0.5 ? eased * 2.0 : (eased - 0.5) * 2.0);

        Color fill = new Color(
            (int) (from.r + (to.r - from.r) * t),
            (int) (from.g + (to.g - from.g) * t),
            (int) (from.b + (to.b - from.b) * t),
            from.a);

        renderer.quad(x, y, filled, height, fill);

        double sweep = HudPulse.shimmer(animationStyle.get(), 2200L);
        if (sweep < 0.0 || filled <= 0.0) return;

        // A short bright band running the filled part. Clipped to it rather than to the whole
        // bar, so it never suggests progress that has not happened.
        double bandWidth = Math.max(4.0, filled * 0.15);
        double bandX = x + sweep * (filled + bandWidth) - bandWidth;
        double left = Math.max(x, bandX);
        double right = Math.min(x + filled, bandX + bandWidth);

        if (right > left) {
            renderer.quad(left, y, right - left, height, new Color(255, 255, 255, 70));
        }
    }

    /**
     * One labelled row, its value column sized from a template rather than from the value.
     *
     * <p>Without the template the element itself changes width as the numbers do, which on a
     * right-anchored or centred HUD makes the whole block slide across the screen.
     */
    private double drawRow(HudRenderer renderer, double y, String label, String value, String template,
                           SettingColor color, boolean shadow, double scale) {
        double x0 = this.x + panel.padding();
        renderer.text(label, x0, y, labelColor.get(), shadow, scale);
        double labelWidth = renderer.textWidth("Travelled ", shadow, scale);
        renderer.text(value, x0 + labelWidth, y, color, shadow, scale);
        return labelWidth + Math.max(renderer.textWidth(value, shadow, scale),
            renderer.textWidth(template, shadow, scale));
    }

    private double drawSegment(HudRenderer renderer, double x, double y, String text, SettingColor color,
                               boolean shadow, double scale) {
        renderer.text(text, x, y, color, shadow, scale);
        return x + renderer.textWidth(text, shadow, scale);
    }

    private double separator(HudRenderer renderer, double x, double y, double pad, boolean shadow, double scale) {
        x += pad;
        renderer.text("·", x, y, labelColor.get(), shadow, scale);
        return x + renderer.textWidth("·", shadow, scale) + pad;
    }

    /** Bearing to the target relative to where you are looking. */
    private String headingArrow(double dx, double dz) {
        double relative = MathHelper.wrapDegrees(
            Math.toDegrees(Math.atan2(dz, dx)) - 90.0 - playerYaw());
        int sector = (int) Math.round(relative / 45.0) & 7;

        return switch (sector) {
            case 0 -> "^";
            case 1 -> ">";
            case 2 -> ">";
            case 3 -> "v";
            case 4 -> "v";
            case 5 -> "<";
            // Sector 7 is the target between 22.5 and 67.5 degrees to your left. It used to
            // land on the catch-all and read as "straight ahead", so drifting left showed an
            // aligned arrow next to an orange colour that said otherwise — the one field meant
            // to reveal drift, hiding it on one side. Sector 0 is explicit above, so the
            // catch-all now only ever sees 7.
            case 6 -> "<";
            default -> "<";
        };
    }

    private SettingColor headingColor(double dx, double dz) {
        double relative = Math.abs(MathHelper.wrapDegrees(
            Math.toDegrees(Math.atan2(dz, dx)) - 90.0 - playerYaw()));
        return relative <= 5.0 ? goodColor.get() : warnColor.get();
    }

    /**
     * Rounds the arrival time to a step you can actually read.
     *
     * <p>To the second, the last digit changed on every frame and the text under it changed
     * width with it, so every field to its right slid back and forth. Rounding is half the
     * cure — the other half is reserving the width — and it costs nothing: nobody plans a
     * three-hour flight to the second.
     */
    private static int quantiseEta(int seconds) {
        if (seconds >= 3600) return seconds / 60 * 60;
        if (seconds >= 60) return seconds / 10 * 10;
        return seconds / 5 * 5;
    }

    /**
     * Draws text inside a slot wide enough for the longest value it will ever hold.
     *
     * <p>The line is proportional text, so "9m 05s" and "11m 40s" are not the same width and
     * everything after them moves as the numbers change. Advancing by the template's width
     * instead of the text's pins each field in place: the digits change, the layout does not.
     *
     * <p>Which only holds while the template really is the worst case — the {@code max} means
     * an undersized one silently hands the width back to the value and the field starts
     * pushing again. The distance slots allow five digits of kilometres rather than three for
     * that reason: three covers a thousand kilometres, and a target three million blocks out
     * is three thousand, so the guarantee would have failed on exactly the flights this
     * element is for. Nothing here depends on the font's digits being equal width; it depends
     * on the template being an upper bound.
     */
    private double drawFixed(HudRenderer renderer, double x, double y, String text, String template,
                             SettingColor color, boolean shadow, double scale) {
        renderer.text(text, x, y, color, shadow, scale);
        return x + Math.max(renderer.textWidth(text, shadow, scale),
            renderer.textWidth(template, shadow, scale));
    }

    private static String formatDistance(double blocks) {
        return blocks >= 1000.0 ? String.format("%.1f km", blocks / 1000.0) : String.format("%.0f m", blocks);
    }

    private static String formatTime(int seconds) {
        if (seconds >= 3600) return String.format("%dh %02dm", seconds / 3600, (seconds % 3600) / 60);
        if (seconds >= 60) return String.format("%dm %02ds", seconds / 60, seconds % 60);
        return seconds + "s";
    }

    private static String arrivalClock(int etaSeconds) {
        java.time.LocalTime at = java.time.LocalTime.now().plusSeconds(etaSeconds);
        return String.format("~%02d:%02d", at.getHour(), at.getMinute());
    }

    private record Target(String name, double x, double z) {
    }
}
