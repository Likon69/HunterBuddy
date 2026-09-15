package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.WingTipTracker;
import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A light, purely cosmetic wake behind the elytra while gliding: two trails from roughly where the wingtips
 * are, each fading out over its own tail, plus a small engine-flame flicker at each wingtip that grows with
 * speed. dbrighthd/elytratrails does the same idea with particles, a config screen, a server-sync network
 * protocol and compatibility layers for other model mods - all so every player on a server can see and
 * customise each other's trail. None of that applies here: this is only ever drawn for the local player, on
 * a server that does not have the mod, so it is just free-form quads redrawn from a short position history,
 * batched with everything else HunterBuddy renders.
 */
public class FlightTrail extends Module {
    public enum Style {
        LINES, RIBBON
    }

    /** Different silhouettes for the flame's taper, root to tip - see 'flame-style'. */
    public enum FlameStyle {
        CONE, NEEDLE, TEARDROP
    }

    /**
     * A hard ceiling on how long a point can survive regardless of {@link #length}: 'length' measures the
     * trail in blocks, which is what makes it read as a fixed physical streamer instead of shrinking and
     * growing with speed, but a fixed block length alone never empties out once the bot has landed and
     * stopped laying new points down behind it. This is the backstop that makes a leftover trail still fade
     * away after landing, the way a plain age-based trail would.
     */
    private static final long HARD_MAX_AGE_MS = 3000L;

    private static final int FLAME_SEGMENTS = 5;
    /** A resting flame never shrinks all the way to nothing - it idles small instead of vanishing. */
    private static final double FLAME_MIN_FRACTION = 0.15;
    /** Length-to-width ratio of the flame cone: wider at the root, tapering to a point. */
    private static final double FLAME_WIDTH_RATIO = 0.18;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("style")
        .description("Two thin lines, or two flat ribbons with a real width.")
        .defaultValue(Style.RIBBON)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Colour of the trail at the wingtip; it fades to nothing over 'length' blocks.")
        .defaultValue(new SettingColor(215, 235, 255, 200))
        .build()
    );

    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder()
        .name("width")
        .description("Width of each ribbon, in blocks.")
        .defaultValue(0.12)
        .min(0.02).max(0.6).sliderRange(0.05, 0.3)
        .visible(() -> style.get() == Style.RIBBON)
        .build()
    );

    private final Setting<Double> length = sgGeneral.add(new DoubleSetting.Builder()
        .name("length")
        .description("How many blocks of trail to keep behind the wingtip, measured along the path flown - not a straight-line distance.")
        .defaultValue(30.0)
        .min(4.0).max(120.0).sliderRange(8.0, 60.0)
        .build()
    );

    /**
     * The fallback estimate (see {@link #wingRoot}) only ever runs when the exact capture has nothing fresh -
     * in practice, first person, where the game does not render the local player's own body at all. These
     * three used to be sliders; taken out once it was clear they never moved anything in third person, where
     * this is actually watched, and left at the values that were last dialled in.
     */
    private static final double WINGTIP_OFFSET = 0.6;
    private static final double WINGTIP_HEIGHT = 1.1;
    private static final double WINGTIP_PULLBACK = 0.0;

    private final Setting<Boolean> flames = sgGeneral.add(new BoolSetting.Builder()
        .name("flames")
        .description("A small engine-flame flicker at each wingtip that grows and brightens with speed, like a fighter jet under thrust.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> flameColor = sgGeneral.add(new ColorSetting.Builder()
        .name("flame-color")
        .description("Colour of the flames at their brightest (root) while idling - a cool, low-thrust orange. They fade to nothing at the tip regardless of speed.")
        .defaultValue(new SettingColor(255, 120, 30, 255))
        .visible(flames::get)
        .build()
    );

    private final Setting<SettingColor> flameColorPeak = sgGeneral.add(new ColorSetting.Builder()
        .name("flame-color-peak")
        .description("Colour of the flames at 'flame-speed-reference' or above: a real afterburner runs hotter under full thrust, and a hotter flame is bluer, not redder. Blended with 'flame-color' by how close to that speed you are.")
        .defaultValue(new SettingColor(170, 205, 255, 255))
        .visible(flames::get)
        .build()
    );

    private final Setting<FlameStyle> flameStyle = sgGeneral.add(new EnumSetting.Builder<FlameStyle>()
        .name("flame-style")
        .description("Cone: a straight taper from wide root to a point. Needle: narrows fast, a thin sharp tail. Teardrop: bulges out past the root before tapering, closer to a campfire's silhouette.")
        .defaultValue(FlameStyle.CONE)
        .visible(flames::get)
        .build()
    );

    private final Setting<Double> flameLength = sgGeneral.add(new DoubleSetting.Builder()
        .name("flame-length")
        .description("How long each flame gets at or above 'flame-speed-reference', in blocks.")
        .defaultValue(1.0)
        .min(0.2).max(3.0).sliderRange(0.4, 2.0)
        .visible(flames::get)
        .build()
    );

    private final Setting<Double> flameSpeedReference = sgGeneral.add(new DoubleSetting.Builder()
        .name("flame-speed-reference")
        .description("Speed, in blocks/second, at which the flames reach their full length and brightness. Below it they idle small; a rocket boost above it does not grow them further.")
        .defaultValue(40.0)
        .min(10.0).max(100.0).sliderRange(20.0, 70.0)
        .visible(flames::get)
        .build()
    );

    private final Setting<Double> trailGap = sgGeneral.add(new DoubleSetting.Builder()
        .name("trail-gap")
        .description("How far behind the wingtip, away from where you are looking, the trail itself begins - so it does not fuse straight into the flame, the way a real contrail only forms a little past the engine.")
        .defaultValue(0.4)
        .min(0.0).max(2.0).sliderRange(0.0, 1.2)
        .build()
    );

    private final Setting<Boolean> smokeReactsToSpeed = sgGeneral.add(new BoolSetting.Builder()
        .name("smoke-reacts-to-speed")
        .description("The trail itself thins and dims with speed, the other way round from the flames: a jet under full thrust burns clean and trails little smoke; idling or throttled back, it trails more. Each point remembers the speed it was laid down at, so slowing down and speeding back up shows as a real thick-then-thin band travelling down the trail, not just a change at the tip.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> smokeMinFraction = sgGeneral.add(new DoubleSetting.Builder()
        .name("smoke-min-fraction")
        .description("How thin and faint the trail gets at 'flame-speed-reference' or above, as a fraction of 'width' and 'color' at rest. 1.0 turns this off without unchecking 'smoke-reacts-to-speed'.")
        .defaultValue(0.35)
        .min(0.05).max(1.0).sliderRange(0.1, 0.8)
        .visible(smokeReactsToSpeed::get)
        .build()
    );

    private final Deque<Sample> left = new ArrayDeque<>();
    private final Deque<Sample> right = new ArrayDeque<>();

    /** Null until the first frame spent gliding; then true/false, logged on every change - see onRender3D. */
    private Boolean lastPrecise = null;
    private boolean lastShape;
    private Vec3d leftShape;
    private Vec3d rightShape;
    private ClientWorld shapeWorld;

    public FlightTrail() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "FlightTrail",
            "A light two-trail wake behind the elytra while gliding, with speed-reactive flames at the wingtips, instead of dbrighthd's heavier particle trail.");
    }

    @Override
    public void onDeactivate() {
        left.clear();
        right.clear();
        leftShape = null;
        rightShape = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();

        if (!mc.player.isGliding() || mc.world != shapeWorld) {
            leftShape = null;
            rightShape = null;
            shapeWorld = mc.world;
        }

        // Only grows while actually gliding; pruning below still runs every tick, so a trail already
        // laid down keeps fading out on landing instead of vanishing the instant the glide ends.
        if (mc.player.isGliding()) {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            Vec3d look = lookVector(yaw, pitch);
            Vec3d[] wingtips = wingtipPositions(yaw, pitch, mc.player.getEntityPos(), now, 1.0F);
            Vec3d gap = look.multiply(-trailGap.get());
            double smokeFrac = smokeFraction(speedFraction());

            // Same source as the live render tip (WingTipTracker when fresh, the same fallback otherwise) -
            // using the plain yaw/pitch estimate here while the tip used the exact capture is exactly what
            // made the trail kink and "glitch" a metre or two back: the two ends of the same trail were
            // built from two different positions.
            addSample(left, wingtips[0].add(gap), now, smokeFrac);
            addSample(right, wingtips[1].add(gap), now, smokeFrac);
        }

        double maxLen = length.get();
        prune(left, now, maxLen);
        prune(right, now, maxLen);
    }

    private void addSample(Deque<Sample> samples, Vec3d pos, long now, double smokeFrac) {
        Sample last = samples.peekLast();
        double dist = last == null ? 0.0 : last.dist + last.pos.distanceTo(pos);
        samples.addLast(new Sample(pos, now, dist, smokeFrac));
    }

    /**
     * 0 at 'flame-speed-reference' or above, 1 at rest - the flame's own growth curve, inverted, so the two
     * always read as complements of the same thrust rather than two independently tuned animations.
     */
    private double smokeFraction(double speedFrac) {
        if (!smokeReactsToSpeed.get()) return 1.0;
        return 1.0 - (1.0 - smokeMinFraction.get()) * speedFrac;
    }

    private double speedFraction() {
        double speedBps = mc.player.getVelocity().length() * 20.0;
        return MathHelper.clamp(speedBps / flameSpeedReference.get(), 0.0, 1.0);
    }

    private void prune(Deque<Sample> samples, long now, double maxLen) {
        Sample last = samples.peekLast();
        double headDist = last == null ? 0.0 : last.dist;

        while (!samples.isEmpty()) {
            Sample first = samples.peekFirst();
            boolean tooOld = now - first.t > HARD_MAX_AGE_MS;
            boolean tooFar = headDist - first.dist > maxLen;
            if (tooOld || tooFar) samples.pollFirst(); else break;
        }
    }

    private static Vec3d direction(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3d(-Math.sin(rad), 0.0, Math.cos(rad));
    }

    /**
     * Full look direction, pitch included: {@link #direction} alone is flat and was what made the wingtip
     * points sit up to a metre off the model whenever the player pitched down or up - normal for most of a
     * glide - because it pulled the origin straight back on the horizontal instead of back along the body.
     * The wingspan itself (see {@code rightDir} in the callers) stays yaw-only on purpose: the vanilla elytra
     * model does not roll with pitch, only the spine does.
     */
    private static Vec3d lookVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double xz = Math.cos(pitchRad);
        return new Vec3d(-Math.sin(yawRad) * xz, -Math.sin(pitchRad), Math.cos(yawRad) * xz);
    }

    /**
     * This render hook never touches the actual elytra model or its bones, so this is an approximation by
     * construction - see {@link #WINGTIP_HEIGHT} and {@link #WINGTIP_PULLBACK}.
     */
    private static Vec3d wingRoot(Vec3d look, Vec3d feet) {
        return feet.add(0.0, WINGTIP_HEIGHT, 0.0).subtract(look.multiply(WINGTIP_PULLBACK));
    }

    /**
     * The one place both {@link #onTick} (the trail's committed history) and {@link #onRender3D} (its live
     * tip) get a wingtip position from, so they never disagree: {@link WingTipTracker} when it has something
     * fresh, the yaw/pitch estimate otherwise. {@code bodyPos} is the feet position to build the estimate
     * from - interpolated in {@link #onRender3D}, the plain tick position in {@link #onTick}.
     */
    private Vec3d[] wingtipPositions(float yaw, float pitch, Vec3d bodyPos, long now, float tickDelta) {
        if (WingTipTracker.leftFresh(now) && WingTipTracker.rightFresh(now)) {
            if (WingTipTracker.leftBody != null && WingTipTracker.rightBody != null) {
                leftShape = WingTipTracker.leftBody;
                rightShape = WingTipTracker.rightBody;
            }
            return new Vec3d[] {WingTipTracker.leftWorld, WingTipTracker.rightWorld};
        }

        if (leftShape != null && rightShape != null) {
            float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, mc.player.lastBodyYaw, mc.player.bodyYaw);
            float glideAngle = WingTipTracker.glideAngle(WingTipTracker.glidingProgress(mc.player.getGlidingTicks() + tickDelta), pitch);
            return new Vec3d[] {
                bodyPos.add(WingTipTracker.toWorld(leftShape, bodyYaw, glideAngle)),
                bodyPos.add(WingTipTracker.toWorld(rightShape, bodyYaw, glideAngle))
            };
        }

        Vec3d look = lookVector(yaw, pitch);
        Vec3d body = wingRoot(look, bodyPos);
        Vec3d rightDir = direction(yaw + 90.0F);
        return new Vec3d[] {body.subtract(rightDir.multiply(WINGTIP_OFFSET)), body.add(rightDir.multiply(WINGTIP_OFFSET))};
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        double maxLen = length.get();

        // A live sample built from the render-interpolated position, not the last tick's: without it the
        // trail's own head only moves 20 times a second and the newest stretch pops in whole between ticks -
        // "ajouté par morceaux visibles". This one extra segment, recomputed every frame, is what makes the
        // tip glide forward continuously instead of stepping.
        Sample liveLeft = null;
        Sample liveRight = null;
        Vec3d look = null;
        // The wingtip itself, un-gapped: what the flame roots from, below. The trail starts a little further
        // back than this - see 'trail-gap' - so it does not fuse straight into the flame.
        Vec3d leftWingtip = null;
        Vec3d rightWingtip = null;
        if (mc.player.isGliding()) {
            float yaw = mc.player.getLerpedYaw(event.tickDelta);
            float pitch = mc.player.getLerpedPitch(event.tickDelta);
            look = lookVector(yaw, pitch);

            // Same helper onTick uses for its committed history, with the same freshness check: the two ends
            // of the trail must always agree on where the wingtip is, or the join between them kinks.
            boolean precise = WingTipTracker.leftFresh(now) && WingTipTracker.rightFresh(now);
            Vec3d[] wingtips = wingtipPositions(yaw, pitch, mc.player.getLerpedPos(event.tickDelta), now, event.tickDelta);
            leftWingtip = wingtips[0];
            rightWingtip = wingtips[1];

            boolean shape = !precise && leftShape != null && rightShape != null;
            if (this.lastPrecise == null || this.lastPrecise != precise || this.lastShape != shape) {
                this.lastPrecise = precise;
                this.lastShape = shape;
                this.info(precise
                    ? "Wingtip position: exact (reading the elytra model directly)"
                    : shape
                    ? "Wingtip position: last exact shape (model not drawn)"
                    : "Wingtip position: estimated (exact position unavailable - first person? adjust wingtip-height/pullback/offset)");
            }

            double speedFrac = speedFraction();
            Vec3d gap = look.multiply(-trailGap.get());
            liveLeft = liveSample(left, leftWingtip.add(gap), now, smokeFraction(speedFrac));
            liveRight = liveSample(right, rightWingtip.add(gap), now, smokeFraction(speedFrac));

            if (flames.get()) {
                double grow = FLAME_MIN_FRACTION + (1.0 - FLAME_MIN_FRACTION) * speedFrac;
                double flameLen = flameLength.get() * grow;
                double halfBaseWidth = flameLen * FLAME_WIDTH_RATIO;
                Vec3d back = look.multiply(-1.0);
                Color flameNow = lerpColor(flameColor.get(), flameColorPeak.get(), speedFrac);

                drawFlame(event, leftWingtip, back, halfBaseWidth, flameLen, grow, now, 0.0, flameNow);
                drawFlame(event, rightWingtip, back, halfBaseWidth, flameLen, grow, now, Math.PI, flameNow);
            }
        }

        if (style.get() == Style.RIBBON) {
            double halfWidth = width.get() / 2.0;
            drawRibbon(event, left, liveLeft, now, maxLen, halfWidth);
            drawRibbon(event, right, liveRight, now, maxLen, halfWidth);
        } else {
            drawLines(event, left, liveLeft, now, maxLen);
            drawLines(event, right, liveRight, now, maxLen);
        }
    }

    private Sample liveSample(Deque<Sample> samples, Vec3d pos, long now, double smokeFrac) {
        Sample last = samples.peekLast();
        double dist = last == null ? 0.0 : last.dist + last.pos.distanceTo(pos);
        return new Sample(pos, now, dist, smokeFrac);
    }

    private static Color lerpColor(SettingColor a, SettingColor b, double t) {
        t = MathHelper.clamp(t, 0.0, 1.0);
        return new Color(
            (int) Math.round(a.r + (b.r - a.r) * t),
            (int) Math.round(a.g + (b.g - a.g) * t),
            (int) Math.round(a.b + (b.b - a.b) * t),
            (int) Math.round(a.a + (b.a - a.a) * t)
        );
    }

    private double headDist(Deque<Sample> samples, Sample liveTip) {
        if (liveTip != null) return liveTip.dist;
        Sample last = samples.peekLast();
        return last == null ? 0.0 : last.dist;
    }

    private void drawLines(Render3DEvent event, Deque<Sample> samples, Sample liveTip, long now, double maxLen) {
        double headDist = headDist(samples, liveTip);
        Sample prev = null;
        for (Sample s : samples) {
            if (prev != null) {
                event.renderer.line(prev.pos.x, prev.pos.y, prev.pos.z, s.pos.x, s.pos.y, s.pos.z,
                    fade(prev, headDist, now, maxLen), fade(s, headDist, now, maxLen));
            }

            prev = s;
        }

        if (liveTip != null && prev != null) {
            event.renderer.line(prev.pos.x, prev.pos.y, prev.pos.z, liveTip.pos.x, liveTip.pos.y, liveTip.pos.z,
                fade(prev, headDist, now, maxLen), fade(liveTip, headDist, now, maxLen));
        }
    }

    private void drawRibbon(Render3DEvent event, Deque<Sample> samples, Sample liveTip, long now, double maxLen, double halfWidth) {
        double headDist = headDist(samples, liveTip);
        Sample prev = null;
        for (Sample s : samples) {
            if (prev != null) drawRibbonSegment(event, prev, s, headDist, now, maxLen, halfWidth);
            prev = s;
        }

        if (liveTip != null && prev != null) drawRibbonSegment(event, prev, liveTip, headDist, now, maxLen, halfWidth);
    }

    private void drawRibbonSegment(Render3DEvent event, Sample prev, Sample s, double headDist, long now, double maxLen, double halfWidth) {
        Vec3d segDir = s.pos.subtract(prev.pos);
        if (segDir.lengthSquared() <= 1.0E-9) return;

        // Extruded perpendicular to this one segment, not carried over from the last - simple, and fine for
        // how tight an elytra actually turns; a joint seam is not worth a spline here.
        Vec3d perp = segDir.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        if (perp.lengthSquared() < 1.0E-9) perp = segDir.crossProduct(new Vec3d(1.0, 0.0, 0.0));
        perp = perp.normalize();

        // Each end scaled by its own point's remembered smoke, not a single width for the whole segment -
        // this is what lets a slowdown show up as an actual thick patch travelling down the trail.
        double prevHalf = halfWidth * (smokeReactsToSpeed.get() ? prev.smokeFrac : 1.0);
        double sHalf = halfWidth * (smokeReactsToSpeed.get() ? s.smokeFrac : 1.0);

        Vec3d a1 = prev.pos.subtract(perp.multiply(prevHalf)), b1 = prev.pos.add(perp.multiply(prevHalf));
        Vec3d a2 = s.pos.subtract(perp.multiply(sHalf)), b2 = s.pos.add(perp.multiply(sHalf));
        Color c1 = fade(prev, headDist, now, maxLen), c2 = fade(s, headDist, now, maxLen);

        event.renderer.quad(
            a1.x, a1.y, a1.z, b1.x, b1.y, b1.z, b2.x, b2.y, b2.z, a2.x, a2.y, a2.z,
            c1, c1, c2, c2
        );
    }

    private Color fade(Sample s, double headDist, long now, double maxLen) {
        double distFrac = maxLen <= 0.0 ? 0.0 : 1.0 - (headDist - s.dist) / maxLen;
        double ageFrac = 1.0 - (double) (now - s.t) / (double) HARD_MAX_AGE_MS;
        double frac = MathHelper.clamp(Math.min(distFrac, ageFrac), 0.0, 1.0);
        double smoke = smokeReactsToSpeed.get() ? s.smokeFrac : 1.0;

        SettingColor base = color.get();
        return new Color(base.r, base.g, base.b, (int) Math.round(base.a * frac * smoke));
    }

    /**
     * Two crossed tapered strips from the wingtip backward along {@code axis}, root wide and bright, tip
     * narrow and empty - a low-poly cone, cheap enough to redraw every frame and read from most angles
     * without any camera-facing billboard math. {@code phase} just keeps the two wingtips' flicker out of
     * sync with each other, which reads far more like a live flame than a shared blink.
     */
    private void drawFlame(Render3DEvent event, Vec3d root, Vec3d axis, double halfBaseWidth, double flameLen, double alphaScale, long now, double phase, Color baseColor) {
        if (flameLen <= 0.0 || halfBaseWidth <= 0.0) return;

        Vec3d p1 = axis.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        if (p1.lengthSquared() < 1.0E-9) p1 = axis.crossProduct(new Vec3d(1.0, 0.0, 0.0));
        p1 = p1.normalize();
        Vec3d p2 = axis.crossProduct(p1).normalize();

        double flicker = 1.0 + 0.15 * Math.sin(now / 55.0 + phase);
        double half = halfBaseWidth * flicker;
        drawFlameStrip(event, root, axis, p1, half, flameLen, alphaScale, baseColor);
        drawFlameStrip(event, root, axis, p2, half, flameLen, alphaScale, baseColor);
    }

    /** Root-to-tip width, as a fraction of the base width, for the chosen 'flame-style'. */
    private double flameWidthFrac(double t) {
        return switch (flameStyle.get()) {
            case CONE -> 1.0 - t;
            case NEEDLE -> (1.0 - t) * (1.0 - t);
            // Thinner right at the root than a cone, bulges out past it, then closes back down - a
            // campfire's outline more than a jet's.
            case TEARDROP -> 0.15 + 0.85 * Math.sin(Math.min(t, 1.0) * Math.PI);
        };
    }

    private void drawFlameStrip(Render3DEvent event, Vec3d root, Vec3d axis, Vec3d perp, double halfBaseWidth, double flameLen, double alphaScale, Color base) {
        Vec3d prevPos = root;
        double prevHalf = halfBaseWidth * flameWidthFrac(0.0);
        double prevFrac = 1.0;

        for (int i = 1; i <= FLAME_SEGMENTS; i++) {
            double t = (double) i / FLAME_SEGMENTS;
            Vec3d pos = root.add(axis.multiply(flameLen * t));
            double half = halfBaseWidth * flameWidthFrac(t);
            double frac = 1.0 - t;

            Color c1 = new Color(base.r, base.g, base.b, (int) Math.round(base.a * prevFrac * alphaScale));
            Color c2 = new Color(base.r, base.g, base.b, (int) Math.round(base.a * frac * alphaScale));

            Vec3d a1 = prevPos.subtract(perp.multiply(prevHalf)), b1 = prevPos.add(perp.multiply(prevHalf));
            Vec3d a2 = pos.subtract(perp.multiply(half)), b2 = pos.add(perp.multiply(half));

            event.renderer.quad(a1.x, a1.y, a1.z, b1.x, b1.y, b1.z, b2.x, b2.y, b2.z, a2.x, a2.y, a2.z, c1, c1, c2, c2);

            prevPos = pos;
            prevHalf = half;
            prevFrac = frac;
        }
    }

    private static final class Sample {
        final Vec3d pos;
        final long t;
        final double dist;
        /** How much smoke this point was laid down with, 0-1 - the speed fraction at that moment, inverted. */
        final double smokeFrac;

        Sample(Vec3d pos, long t, double dist, double smokeFrac) {
            this.pos = pos;
            this.t = t;
            this.dist = dist;
            this.smokeFrac = smokeFrac;
        }
    }
}
