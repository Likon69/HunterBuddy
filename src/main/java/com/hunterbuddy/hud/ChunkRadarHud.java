package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.ChunkRadar;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.TrailStore;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.Renderer2D;
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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * The trail seen from above.
 *
 * <p>The map says everything and the text says nothing: a heading printed as a
 * number has to be read and converted, while the same heading drawn as a line you
 * are or are not parallel to is read at a glance — and so is your drift, which is
 * simply how far your marker sits from that line.
 *
 * <p>Nothing here is drawn with single-pixel GL lines except the dashes, which
 * are meant to be faint. Everything that matters is a filled shape or a sprite:
 * at the size of a HUD, a one pixel line crawls when you turn and disappears
 * against bright ground.
 *
 * <p>Never a coordinate, on any setting.
 */
public class ChunkRadarHud extends HudElement {
    public static final HudElementInfo<ChunkRadarHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "ChunkRadar",
        "Top-down view of the 1.12 trail the chunk-radar module is following.",
        ChunkRadarHud::new);

    private static final Identifier RING = Identifier.of("hunterbuddy", "textures/radar/ring.png");
    private static final Identifier GLOW = Identifier.of("hunterbuddy", "textures/radar/glow.png");
    private static final Identifier MARKER = Identifier.of("hunterbuddy", "textures/radar/marker.png");
    private static final Identifier CHEVRON = Identifier.of("hunterbuddy", "textures/radar/chevron.png");

    public ChunkRadarHud() {
        super(INFO);
        glowPanel = new HudGlowPanel(settings.createGroup("Panel"));
    }

    public enum Orientation {
        NORTH_UP,
        HEADING_UP
    }

    public enum Shape {
        CIRCLE,
        SQUARE
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final HudGlowPanel glowPanel;

    private final Setting<Shape> shape = sgGeneral.add(new EnumSetting.Builder<Shape>()
        .name("shape")
        .description("A disc reads as an instrument and puts everything at a distance you can judge. A square keeps the corners.")
        .defaultValue(Shape.CIRCLE)
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("How many chunks out from you the view reaches.")
        .defaultValue(48)
        .min(8)
        .sliderRange(8, 256)
        .build()
    );

    private final Setting<Double> size = sgGeneral.add(new DoubleSetting.Builder()
        .name("size")
        .description("Width of the square, in pixels.")
        .defaultValue(160.0)
        .min(40.0)
        .sliderRange(40.0, 400.0)
        .build()
    );

    private final Setting<Orientation> orientation = sgGeneral.add(new EnumSetting.Builder<Orientation>()
        .name("orientation")
        .description("Heading up keeps the trail vertical, which turns your drift into the sideways gap between your marker and the line and makes it readable without a number. North up keeps the map still while you turn.")
        .defaultValue(Orientation.HEADING_UP)
        .build()
    );

    private final Setting<Boolean> onlyWhenActive = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-active")
        .description("Stay out of the way until there is a trail to show. An empty square on screen for a whole flight is a square you stop looking at.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("show-background")
        .description("Darken the disc behind everything. Off by default: the frame alone says where the edges are without a slab of colour over the world.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showLine = sgGeneral.add(new BoolSetting.Builder()
        .name("show-line")
        .description("Draw the fitted line, the one being let go, the corridor and the Nether guide. Off leaves the cells, the markers and the chevron: the map without the claim. The line drawn in the world is not affected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> lineWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("line-width")
        .description("Thickness of the fitted line in pixels. The halo around it grows with it, so the line keeps its shape at any width. Under one pixel the line is held at one and dimmed instead, which is the only way a hairline stays continuous on a screen that samples each pixel once. The dashes of a projection are left thin on purpose: they are meant to stay quiet.")
        .defaultValue(2.0)
        .min(0.2)
        .sliderRange(0.2, 8.0)
        .visible(showLine::get)
        .build()
    );

    private final Setting<Boolean> lineGlow = sgGeneral.add(new BoolSetting.Builder()
        .name("line-glow")
        .description("Two faint bands either side of the line. They make it findable against bright ground, but they also spread it over six times its width, and what lies under that width is the cells the line was fitted to. Off: a plain band, nothing around it.")
        .defaultValue(false)
        .visible(showLine::get)
        .build()
    );

    private final Setting<Double> lineOpacity = sgGeneral.add(new DoubleSetting.Builder()
        .name("line-opacity")
        .description("Opacity of the line and its halo, as a percentage of the colour's own alpha. Above a hundred it can only strengthen the halo, the core is already solid.")
        .defaultValue(100.0)
        .min(0.0)
        .sliderRange(0.0, 200.0)
        .visible(showLine::get)
        .build()
    );

    private final Setting<Boolean> showFrame = sgGeneral.add(new BoolSetting.Builder()
        .name("show-frame")
        .description("Draw the outer ring, or the border in square mode. The north mark goes with it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> frameWidth = sgGeneral.add(new IntSetting.Builder()
        .name("frame-width")
        .description("Thickness of that ring in pixels. Past the thickness the sprite has, it is drawn as a shape instead so the width is real rather than a stretched picture.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 6)
        .visible(showFrame::get)
        .build()
    );

    private final Setting<Boolean> frameShowsState = sgGeneral.add(new BoolSetting.Builder()
        .name("frame-shows-state")
        .description("Colour the outer ring by what the module is doing: acquired, merely aligned, quiet on a highway, guiding in the Nether. Off by default, because while it is on it overrides the frame colour and that colour then looks broken.")
        .defaultValue(false)
        .visible(showFrame::get)
        .build()
    );

    private final Setting<Boolean> showCorridor = sgGeneral.add(new BoolSetting.Builder()
        .name("show-corridor")
        .description("A faint band either side of the line, as wide as fork-distance. One look says whether you are inside it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showReach = sgGeneral.add(new BoolSetting.Builder()
        .name("show-reach")
        .description("A very faint disc showing how far the server is sending you chunks. Inside it a hit is what the radar sees now; outside it is memory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showRings = sgGeneral.add(new BoolSetting.Builder()
        .name("show-rings")
        .description("Faint circles at a fixed spacing. They are the scale, and they replace having to print one.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> ringSpacing = sgGeneral.add(new IntSetting.Builder()
        .name("ring-spacing")
        .description("Chunks between two rings.")
        .defaultValue(16)
        .min(4)
        .sliderRange(4, 64)
        .visible(showRings::get)
        .build()
    );

    private final Setting<Boolean> showMarkers = sgGeneral.add(new BoolSetting.Builder()
        .name("show-markers")
        .description("Draw the waypoints the module places: the ends of a trail, the sightings, the zones. Without them this is a picture of chunks rather than a map.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showMemory = sgGeneral.add(new BoolSetting.Builder()
        .name("show-memory")
        .description("Draw the trails remembered from earlier sessions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showNearMiss = sgGeneral.add(new BoolSetting.Builder()
        .name("show-near-miss")
        .description("Draw the near misses too.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showStored = sgGeneral.add(new BoolSetting.Builder()
        .name("show-stored")
        .description("Draw the chunks the server already had.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> chunkSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("chunk-size")
        .description("Size of a drawn chunk, as a multiple of the size the radius gives it. Below one they stop touching each other, which is what makes a dotted trail read as a trail rather than a smear; above one a sparse trail is findable from further away.")
        .defaultValue(1.0)
        .min(0.25)
        .sliderRange(0.25, 4.0)
        .build()
    );

    private final Setting<Double> chunkOpacity = sgGeneral.add(new DoubleSetting.Builder()
        .name("chunk-opacity")
        .description("Opacity of every drawn chunk, as a percentage of its colour's own alpha. The kinds keep their relative weights: a near miss stays fainter than a hit. The fade with distance from the centre is applied on top.")
        .defaultValue(100.0)
        .min(0.0)
        .sliderRange(0.0, 200.0)
        .build()
    );

    private final Setting<Boolean> showText = sgGeneral.add(new BoolSetting.Builder()
        .name("show-text")
        .description("One line under the disc with the heading. Off by default: everything it could say is already in the drawing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.1).sliderRange(0.1, 3.0)
        .visible(showText::get).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render a shadow behind the text.")
        .defaultValue(true)
        .visible(showText::get).build());

    private final Setting<Boolean> moduleColors = sgColors.add(new BoolSetting.Builder()
        .name("use-module-colors")
        .description("Take the colours from the chunk-radar module, so there is one set to tune and the map matches the world. Uncheck it to reveal the per-kind colours below and set them here instead -- while it is on they are hidden, because a colour that is shown and overridden looks broken.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> frameColor = sgColors.add(new ColorSetting.Builder()
        .name("frame").description("The outer ring, when it is not showing a state.")
        .defaultValue(new SettingColor(120, 255, 200, 90)).build());

    private final Setting<SettingColor> zoneColor = sgColors.add(new ColorSetting.Builder()
        .name("zone").description("Zone markers.")
        .defaultValue(new SettingColor(255, 200, 60, 220)).build());

    private final Setting<SettingColor> trailColor = sgColors.add(new ColorSetting.Builder()
        .name("trail").description("A hit that belongs to the line.")
        .defaultValue(new SettingColor(255, 190, 60, 230))
        .visible(() -> !moduleColors.get()).build());

    private final Setting<SettingColor> looseColor = sgColors.add(new ColorSetting.Builder()
        .name("loose").description("A hit that does not fit the line.")
        .defaultValue(new SettingColor(255, 190, 60, 80))
        .visible(() -> !moduleColors.get()).build());

    private final Setting<SettingColor> nearColor = sgColors.add(new ColorSetting.Builder()
        .name("near-miss").description("A near miss.")
        .defaultValue(new SettingColor(200, 90, 90, 110))
        .visible(() -> !moduleColors.get()).build());

    private final Setting<SettingColor> storedColor = sgColors.add(new ColorSetting.Builder()
        .name("stored").description("A chunk the server already had.")
        .defaultValue(new SettingColor(120, 140, 180, 80))
        .visible(() -> !moduleColors.get()).build());

    private final Setting<SettingColor> memoryColor = sgColors.add(new ColorSetting.Builder()
        .name("memory").description("A trail remembered from an earlier session.")
        .defaultValue(new SettingColor(150, 120, 220, 130))
        .visible(() -> !moduleColors.get()).build());

    private final Setting<SettingColor> lineColor = sgColors.add(new ColorSetting.Builder()
        .name("line").description("The fitted line.")
        .defaultValue(new SettingColor(120, 255, 200, 255))
        .visible(() -> !moduleColors.get()).build());

    private final Setting<SettingColor> playerColor = sgColors.add(new ColorSetting.Builder()
        .name("player").description("Your own marker.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text").description("Colour of the readout.")
        .defaultValue(new SettingColor(200, 220, 255, 255))
        .visible(showText::get).build());

    // Per-frame working state, so the drawing helpers need no argument lists.
    private double box;
    private double centreX;
    private double centreZ;
    private double scale;
    private double sin;
    private double cos;
    private double cell;
    private double fade = 1.0;

    /** Smoothed map rotation, so a four degree correction does not snap the world round. */
    private double turnedTo = Double.NaN;

    @Override
    public void render(HudRenderer renderer) {
        ChunkRadar radar = ChunkRadar.get();
        boolean wanted = isInEditor() || !onlyWhenActive.get() || hasSomethingToShow(radar);

        // Fades in and out rather than appearing. A panel that pops into being is
        // a panel you look at instead of through.
        fade = MathHelper.clamp(fade + (wanted ? 1.0 : -1.0) * renderer.delta / 0.25, 0.0, 1.0);

        if (fade <= 0.0) {
            setSize(0.0, 0.0);
            return;
        }

        box = size.get();
        double lineHeight = showText.get() ? renderer.textHeight(textShadow.get(), textScale.get()) : 0.0;
        setSize(box, box + lineHeight);

        boolean here = MeteorClient.mc.world != null
            && MeteorClient.mc.world.getRegistryKey().equals(World.OVERWORLD);

        // Round panel for a round radar: a rectangular halo around a disc is four
        // corners of colour hanging off nothing.
        if (shape.get() == Shape.CIRCLE && available(GLOW)) {
            glowPanel.drawRound(renderer, GLOW, x, y, box, severity(radar, here));
        } else {
            glowPanel.draw(renderer, x, y, box, box + lineHeight, severity(radar, here));
        }

        if (showBackground.get()) spriteUnder(renderer, GLOW, 0.0, 0.0, box, box, alpha(new Color(0, 0, 0, 200), 0.55));

        if (radar == null || !radar.isActive() || MeteorClient.mc.player == null) {
            frame(renderer, alpha(frameColor.get(), 1.0));
            caption(renderer, "radar off", frameColor.get());
            return;
        }

        Vec3d at = MeteorClient.mc.player.getEntityPos();
        centreX = at.x / 16.0;
        centreZ = at.z / 16.0;
        scale = box / (radius.get() * 2.0);
        // The floor comes first: a chunk under two pixels is a chunk you cannot
        // see, and the multiplier is a taste applied to a size that already works.
        cell = Math.max(1.0, Math.max(2.0, scale) * chunkSize.get());

        double up = here ? radar.headingOrNaN() : radar.guideHeading();
        turn(radar, up);

        double rotation = orientation.get() == Orientation.HEADING_UP ? 180.0 - turnedTo : 0.0;
        sin = Math.sin(Math.toRadians(rotation));
        cos = Math.cos(Math.toRadians(rotation));

        if (showReach.get()) reach(renderer, radar);
        if (showRings.get()) rings(renderer);

        if (!here) {
            if (showLine.get()) {
                netherGuide(renderer, radar);
                guideFoot(renderer, radar);
            }

            frame(renderer, stateColour(radar, false));
            north(renderer, rotation);
            chevron(renderer, rotation);
            caption(renderer, guideCaption(radar, up), pick(lineColor, radar.lineColour()));
            return;
        }

        if (showCorridor.get() && showLine.get()) corridor(renderer, radar);

        // Every line first, every cell after. The line is a claim about where the
        // trail runs and the cells are the evidence it was fitted to, so a line
        // drawn over them hides the one thing that can contradict it -- which is
        // exactly what a band six times its own width was doing.
        if (showMemory.get()) memoryLines(renderer, radar);

        if (showLine.get()) {
            released(renderer, radar);
            liveLine(renderer, radar);
        }

        // Oldest and faintest first, brightest last, and the hits on the line
        // always over the loose ones whatever the distance says.
        if (showMemory.get()) memoryChunks(renderer, radar);
        if (showStored.get()) plates(renderer, radar.storedChunks(), pick(storedColor, radar.storedColour()));
        if (showNearMiss.get()) plates(renderer, radar.nearMisses(), pick(nearColor, radar.nearMissColour()));
        plates(renderer, radar.pool(), pick(looseColor, radar.looseColour()));
        plates(renderer, radar.line(), pick(trailColor, radar.trailColour()));

        if (showMarkers.get()) markers(renderer, radar);

        edgeArrows(renderer, radar);
        frame(renderer, stateColour(radar, true));
        north(renderer, rotation);
        chevron(renderer, rotation);

        caption(renderer, Double.isNaN(up) ? "no line" : String.format(Locale.ROOT, "%.0f", up),
            pick(textColor, null));
    }

    // State

    private void turn(ChunkRadar radar, double up) {
        // Without a line the map follows your ten second course, never the raw
        // yaw: a map that spins with the mouse is unreadable while you look around.
        double target = Double.isNaN(up) ? radar.smoothedCourse() : up;
        if (Double.isNaN(target)) target = MeteorClient.mc.player.getYaw();

        if (Double.isNaN(turnedTo)) {
            turnedTo = target;
            return;
        }

        turnedTo = MathHelper.wrapDegrees(
            (float) (turnedTo + MathHelper.wrapDegrees((float) (target - turnedTo)) * 0.1));
    }

    /**
     * How loud the halo behind the panel should be.
     *
     * <p>The shared panel has three levels and no silent one, so quiet is OK: the
     * halo it gives that is already the softest of the three, and turning the glow
     * off entirely is a setting of the panel's own.
     */
    private HudGlowPanel.Severity severity(ChunkRadar radar, boolean here) {
        if (radar == null || !radar.isActive() || !here) return HudGlowPanel.Severity.OK;

        boolean adrift = radar.hasLine() && Math.abs(radar.drift()) > radar.corridorChunks() * 3.0;
        return adrift || (!radar.isAcquired() && radar.hasLine())
            ? HudGlowPanel.Severity.WARN
            : HudGlowPanel.Severity.OK;
    }

    private Color stateColour(ChunkRadar radar, boolean here) {
        if (!frameShowsState.get()) return alpha(frameColor.get(), 1.0);

        SettingColor line = pick(lineColor, radar.lineColour());

        if (!here) return alpha(pick(memoryColor, radar.memoryColour()), 1.0);
        if (radar.silenced()) return alpha(new SettingColor(150, 150, 150, 140), 1.0);
        if (radar.isAcquired()) return alpha(line, 1.0);
        if (radar.hasLine()) return alpha(line, 0.4);

        return alpha(frameColor.get(), 1.0);
    }

    // Pieces

    private void frame(HudRenderer renderer, Color colour) {
        if (!showFrame.get()) return;

        double width = frameWidth.get();

        if (shape.get() == Shape.CIRCLE) {
            // The sprite's own band is about two pixels at this size; asking it for
            // more just stretches a picture. Past that it is drawn as a shape.
            if (width <= 2.0 && spriteOver(renderer, RING, 0.0, 0.0, box, box, colour)) return;
            ring(renderer, box / 2.0 - width / 2.0, width, colour, 96);
            return;
        }

        renderer.quad(x, y, box, width, colour);
        renderer.quad(x, y + box - width, box, width, colour);
        renderer.quad(x, y, width, box, colour);
        renderer.quad(x + box - width, y, width, box, colour);
    }

    private void rings(HudRenderer renderer) {
        Color c = alpha(frameColor.get(), 0.42);
        double half = box / 2.0;

        for (double r = ringSpacing.get() * scale; r < half; r += ringSpacing.get() * scale) {
            if (!spriteUnder(renderer, RING, half - r, half - r, r * 2.0, r * 2.0, c)) ring(renderer, r, 1.0, c);
        }
    }

    private void reach(HudRenderer renderer, ChunkRadar radar) {
        double r = radar.reachChunks() * scale;
        if (r <= 1.0 || r > box) return;

        double half = box / 2.0;
        spriteUnder(renderer, GLOW, half - r, half - r, r * 2.0, r * 2.0, alpha(frameColor.get(), 0.18));
    }

    /**
     * The band the line is followed inside.
     *
     * <p>Drawn as a filled strip rather than two edges: the question it answers is
     * whether the chevron is in it, and an area answers that without being read.
     */
    private void corridor(HudRenderer renderer, ChunkRadar radar) {
        if (!radar.hasLine()) return;

        SettingColor base = pick(lineColor, radar.lineColour());
        Color c = alpha(new SettingColor(base.r, base.g, base.b, 255), 0.08);
        double half = radar.corridorChunks() * scale;
        double here = radar.alongOf(centreX, centreZ);
        double[] a = radar.linePoint(here - radius.get() * 1.5);
        double[] b = radar.linePoint(here + radius.get() * 1.5);

        strip(renderer, centreOf(a), centreOf(b), half, c);
    }

    private void plates(HudRenderer renderer, List<ChunkPos> chunks, SettingColor base) {
        for (ChunkPos pos : chunks) {
            double[] p = project(pos.x + 0.5, pos.z + 0.5);
            if (outside(p)) continue;
            plate(renderer, p, faded(base, p));
        }
    }

    /** The remembered lines and their projections, drawn under the cells. */
    private void memoryLines(HudRenderer renderer, ChunkRadar radar) {
        SettingColor base = pick(memoryColor, radar.memoryColour());
        int reach = radar.memoryProjectionChunks();

        for (TrailStore.Trail trail : radar.memories()) {
            band(renderer, trail.originX, trail.originZ, trail.dirX, trail.dirZ,
                trail.spanMin, trail.spanMax, base, 1.5);

            for (int step = 0; step < reach; step += 16) {
                dash(renderer, trail, trail.spanMax + step, trail.spanMax + step + 8, base);
                dash(renderer, trail, trail.spanMin - step - 8, trail.spanMin - step, base);
            }
        }
    }

    /** The cells of those trails, drawn over every line. */
    private void memoryChunks(HudRenderer renderer, ChunkRadar radar) {
        SettingColor base = pick(memoryColor, radar.memoryColour());

        for (TrailStore.Trail trail : radar.memories()) {
            for (int[] chunk : trail.chunks) {
                double[] p = project(chunk[0] + 0.5, chunk[1] + 0.5);
                if (outside(p)) continue;
                plate(renderer, p, faded(base, p));
            }
        }
    }

    private void markers(HudRenderer renderer, ChunkRadar radar) {
        // Blocks, not chunk indices: a marker is a place, and dividing by sixteen
        // already gives the continuous chunk position it sits at.
        for (ChunkRadar.Marker marker : radar.markers()) {
            double[] p = project(marker.blockX() / 16.0, marker.blockZ() / 16.0);
            if (outside(p)) continue;

            SettingColor base = switch (marker.kind()) {
                case TRAIL -> pick(trailColor, radar.trailColour());
                case SIGHTING -> pick(nearColor, radar.nearMissColour());
                case ZONE -> zoneColor.get();
                case HIT -> pick(trailColor, radar.trailColour());
            };

            double half = marker.kind() == ChunkRadar.Marker.Kind.HIT ? 2.0 : 2.5;
            Color c = alpha(new SettingColor(base.r, base.g, base.b, 255), 1.0);

            if (!spriteOver(renderer, MARKER, p[0] - half, p[1] - half, half * 2.0, half * 2.0, c)) {
                renderer.quad(x + p[0] - half, y + p[1] - half, half * 2.0, half * 2.0, c);
            }
        }
    }

    private void liveLine(HudRenderer renderer, ChunkRadar radar) {
        if (!radar.hasLine()) return;

        SettingColor base = pick(lineColor, radar.lineColour());
        double here = radar.alongOf(centreX, centreZ);
        double[] a = radar.linePoint(here - radius.get() * 1.5);
        double[] b = radar.linePoint(here + radius.get() * 1.5);

        glowLine(renderer, centreOf(a), centreOf(b), base, 1.0);
    }

    private void released(HudRenderer renderer, ChunkRadar radar) {
        double[] gone = radar.releasedLine();
        if (gone == null) return;

        SettingColor base = pick(lineColor, radar.lineColour());
        double here = (centreX - gone[0]) * gone[2] + (centreZ - gone[1]) * gone[3];

        glowLine(renderer,
            centreAt(gone[0] + gone[2] * (here - radius.get() * 1.5), gone[1] + gone[3] * (here - radius.get() * 1.5)),
            centreAt(gone[0] + gone[2] * (here + radius.get() * 1.5), gone[1] + gone[3] * (here + radius.get() * 1.5)),
            base, gone[4] * 0.5);
    }

    /**
     * The guide, drawn in the chunk coordinates you are actually standing in.
     *
     * <p>The geometry it comes from is in overworld chunks; the module converts a
     * point along it to a Nether block, and dividing that by sixteen gives the
     * Nether chunk the map is drawn in. The conversion itself lives in one place
     * over there, so the map and the world cannot disagree about where the fil is.
     */
    private void netherGuide(HudRenderer renderer, ChunkRadar radar) {
        double[] guide = radar.guideLine();
        if (guide == null) return;

        double here = radar.guideAlong();

        // Times eight, because the two ends of this line are measured in different
        // worlds. The map down here is drawn in Nether chunks; the parameter along
        // the guide is in overworld chunks, and one of those is an eighth of a
        // chunk on this screen. Without it the line covered a fifth of the disc
        // while the same line fills it upstairs.
        double reach = radius.get() * 1.5 * 8.0;

        glowLine(renderer, guidePoint(radar, guide, here - reach), guidePoint(radar, guide, here + reach),
            pick(lineColor, radar.lineColour()), 1.0);
    }

    private double[] guidePoint(ChunkRadar radar, double[] guide, double along) {
        double[] block = ChunkRadar.netherBlockAt(guide, along);
        return project(block[0] / 16.0, block[1] / 16.0);
    }

    /**
     * The nearest point of the guide, which is the only place worth steering at.
     *
     * <p>Inside the disc it gets a marker; outside, the same arrow the overworld
     * uses. Either way the answer to "which way is the fil" is on the screen
     * rather than in your head.
     */
    private void guideFoot(HudRenderer renderer, ChunkRadar radar) {
        double[] guide = radar.guideLine();
        if (guide == null) return;

        double[] foot = guidePoint(radar, guide, radar.guideAlong());
        SettingColor base = pick(lineColor, radar.lineColour());

        if (outside(foot)) {
            arrow(renderer, foot, base);
            return;
        }

        Color c = alpha(new SettingColor(base.r, base.g, base.b, 255), 1.0);
        if (!spriteOver(renderer, MARKER, foot[0] - 2.5, foot[1] - 2.5, 5.0, 5.0, c)) {
            renderer.quad(x + foot[0] - 2.0, y + foot[1] - 2.0, 4.0, 4.0, c);
        }
    }

    /** Down there the useful number is not the heading, it is how far off the fil you are. */
    private String guideCaption(ChunkRadar radar, double heading) {
        if (Double.isNaN(heading)) return "no guide";
        if (!showLine.get()) return String.format(Locale.ROOT, "%.0f", heading);

        // The letter says where the guide is, not where you are. A positive offset
        // means you are to the right of it, so it is on your left — which is also
        // the side the marker is drawn on, and the two must not disagree.
        double off = radar.guideOffsetBlocks();
        return String.format(Locale.ROOT, "guide %.0f %s", Math.abs(off), off > 0.0 ? "L" : "R");
    }

    /**
     * Points at what is off the edge.
     *
     * <p>Coming out of a portal with the trail a thousand chunks ahead, the map is
     * empty, correct and useless. An arrow on the rim says which way to turn, which
     * is the only question being asked at that moment. It answers for the live line
     * too, not only for memories: a wide detour puts your own trail off the edge.
     */
    private void edgeArrows(HudRenderer renderer, ChunkRadar radar) {
        if (radar.hasLine() && Math.abs(radar.drift()) > radius.get()) {
            double here = radar.alongOf(centreX, centreZ);
            double[] closest = radar.linePoint(here);
            arrow(renderer, centreOf(closest), pick(lineColor, radar.lineColour()));
            return;
        }

        if (radar.hasLine()) return;

        TrailStore.Trail nearest = null;
        double best = Double.MAX_VALUE;

        for (TrailStore.Trail trail : radar.memories()) {
            double off = Math.abs(trail.lateral(centreX, centreZ));
            if (off >= best || off > radar.memoryProjectionChunks()) continue;
            best = off;
            nearest = trail;
        }

        if (nearest == null || best <= radius.get()) return;

        double along = nearest.along(centreX, centreZ);
        arrow(renderer, centreAt(nearest.originX + nearest.dirX * along, nearest.originZ + nearest.dirZ * along),
            pick(memoryColor, radar.memoryColour()));
    }

    private void arrow(HudRenderer renderer, double[] towards, SettingColor base) {
        double half = box / 2.0;
        double dx = towards[0] - half;
        double dy = towards[1] - half;
        double length = Math.hypot(dx, dy);
        if (length < 1.0E-3) return;

        dx /= length;
        dy /= length;

        double edge = half - 7.0;
        double tipX = x + half + dx * edge;
        double tipY = y + half + dy * edge;
        Color c = alpha(new SettingColor(base.r, base.g, base.b, 255), 1.0);

        tri(renderer, tipX, tipY,
            tipX - dx * 8.0 - dy * 5.0, tipY - dy * 8.0 + dx * 5.0,
            tipX - dx * 8.0 + dy * 5.0, tipY - dy * 8.0 - dx * 5.0, c);
    }

    /** A small mark on the rim for north, useless and hidden when north is already up. */
    private void north(HudRenderer renderer, double rotation) {
        if (orientation.get() != Orientation.HEADING_UP) return;

        // Where north lands once the map is turned: project() sends the world
        // north (0, -1) onto (sin R, -cos R). The x used to be negated here, which
        // mirrored the mark across the vertical axis -- right heading, wrong side,
        // and the only place it looked right was north-up, where it is not drawn.
        double angle = Math.toRadians(rotation);
        double dx = Math.sin(angle);
        double dy = -Math.cos(angle);
        double half = box / 2.0;
        double edge = half - 3.0;
        double tipX = x + half + dx * edge;
        double tipY = y + half + dy * edge;
        Color c = alpha(frameColor.get(), 1.0);

        tri(renderer, tipX, tipY,
            tipX - dx * 5.0 - dy * 3.0, tipY - dy * 5.0 + dx * 3.0,
            tipX - dx * 5.0 + dy * 3.0, tipY - dy * 5.0 - dx * 3.0, c);
    }

    /**
     * You, as a chevron.
     *
     * <p>A dot cannot lean. With the map turned so the trail runs upwards, a
     * chevron pointing anywhere but up says your heading is off and which way to
     * correct — the number the readout used to print, drawn. Filled, over a darker
     * copy, so it survives a bright background.
     */
    private void chevron(HudRenderer renderer, double rotation) {
        if (MeteorClient.mc.player == null) return;

        double angle = Math.toRadians(MeteorClient.mc.player.getYaw() + rotation);
        double fx = -Math.sin(angle);
        double fz = Math.cos(angle);
        double half = box / 2.0;
        double cx = x + half;
        double cy = y + half;

        // The sprite points up at zero, and the rotation that sends (0,-1) onto the
        // screen direction (fx, fz) is the yaw plus half a turn.
        double degrees = MeteorClient.mc.player.getYaw() + rotation + 180.0;
        if (spriteTurned(renderer, CHEVRON, half - 8.0, half - 8.0, 16.0, 16.0, degrees,
            alpha(playerColor.get(), 1.0))) {
            return;
        }

        chevronAt(renderer, cx, cy, fx, fz, 8.0, 5.5, alpha(new SettingColor(0, 0, 0, 200), 1.0));
        chevronAt(renderer, cx, cy, fx, fz, 6.5, 4.0, alpha(playerColor.get(), 1.0));
    }

    private void chevronAt(HudRenderer renderer, double cx, double cy, double fx, double fz,
                           double reach, double width, Color colour) {
        tri(renderer, cx + fx * reach, cy + fz * reach,
            cx - fx * reach * 0.5 - fz * width, cy - fz * reach * 0.5 + fx * width,
            cx - fx * reach * 0.5 + fz * width, cy - fz * reach * 0.5 - fx * width, colour);
    }

    private void caption(HudRenderer renderer, String text, Color colour) {
        if (!showText.get()) return;
        renderer.text(text, x, y + box, alpha(colour, 1.0), textShadow.get(), textScale.get());
    }

    // Shapes

    /**
     * A line with a halo: a two pixel core over six and twelve pixel bands.
     *
     * <p>One pixel of colour on a lit background is invisible from any normal
     * distance to a screen. The halo is what makes the line findable without
     * hunting for it.
     */
    private void glowLine(HudRenderer renderer, double[] a, double[] b, SettingColor base, double strength) {
        double weight = strength * lineOpacity.get() / 100.0;
        double width = lineWidth.get();

        // Below a pixel there is nothing left to shrink. A band from y=10.6 to
        // y=11.4 contains no pixel centre at all and is drawn as nothing, so the
        // line would blink in and out as it slid across the rows. Held at one
        // pixel and dimmed by the same fraction instead: that is what a hairline
        // looks like anywhere the screen is sampled once per pixel.
        double thin = Math.min(1.0, width);

        Color core = alpha(base, weight * thin);

        // The halo is a multiple of the core, not a fixed number of pixels: at
        // eight pixels a three pixel halo would be a rim, and at one it would
        // swallow the line. It keeps the real width, so a hairline gets a hairline
        // halo rather than the one a full pixel would have earned.
        double halo = width / 2.0;
        double half = Math.max(0.5, halo);

        if (lineGlow.get()) {
            strip(renderer, a, b, halo * 6.0,
                alpha(new SettingColor(base.r, base.g, base.b, 255), 0.10 * weight));
            strip(renderer, a, b, halo * 3.0,
                alpha(new SettingColor(base.r, base.g, base.b, 255), 0.25 * weight));
        }

        strip(renderer, a, b, half, core);
    }

    /**
     * A triangle wound the way the card will keep.
     *
     * <p>Meteor's 2D pipelines are built with culling on, and its own quads wind
     * so that this cross product comes out negative. A triangle built by hand from
     * a segment and its normal winds the other way whichever direction the segment
     * runs — so every band, halo, corridor, thick ring and arrow was computed,
     * clipped, coloured and then thrown away by the card. Nothing was wrong with
     * the geometry, which is why it took a look at the pipeline to find.
     */
    private void tri(HudRenderer renderer, double x1, double y1, double x2, double y2, double x3, double y3,
                     Color colour) {
        double cross = (x2 - x1) * (y3 - y1) - (x3 - x1) * (y2 - y1);

        if (cross > 0.0) renderer.triangle(x1, y1, x3, y3, x2, y2, colour);
        else renderer.triangle(x1, y1, x2, y2, x3, y3, colour);
    }

    /** A band of half-width {@code half} pixels along a segment, clipped to the shape. */
    private void strip(HudRenderer renderer, double[] a, double[] b, double half, Color colour) {
        double[] inside = clip(a[0], a[1], b[0], b[1]);
        if (inside == null) return;

        double dx = inside[2] - inside[0];
        double dy = inside[3] - inside[1];
        double length = Math.hypot(dx, dy);
        if (length < 1.0E-3) return;

        double nx = -dy / length * half;
        double ny = dx / length * half;

        tri(renderer, x + inside[0] + nx, y + inside[1] + ny,
            x + inside[0] - nx, y + inside[1] - ny,
            x + inside[2] - nx, y + inside[3] - ny, colour);
        tri(renderer, x + inside[2] - nx, y + inside[3] - ny,
            x + inside[2] + nx, y + inside[3] + ny,
            x + inside[0] + nx, y + inside[1] + ny, colour);
    }

    private void band(HudRenderer renderer, double originX, double originZ, double dirX, double dirZ,
                      double from, double to, SettingColor colour, double half) {
        strip(renderer, centreAt(originX + dirX * from, originZ + dirZ * from),
            centreAt(originX + dirX * to, originZ + dirZ * to), half, alpha(colour, 1.0));
    }

    /** A dash of a projected memory, dropped whole when it leaves the shape. */
    private void dash(HudRenderer renderer, TrailStore.Trail trail, double from, double to, SettingColor colour) {
        double[] a = centreAt(trail.originX + trail.dirX * from, trail.originZ + trail.dirZ * from);
        double[] b = centreAt(trail.originX + trail.dirX * to, trail.originZ + trail.dirZ * to);
        if (outside(a) || outside(b)) return;

        renderer.line(x + a[0], y + a[1], x + b[0], y + b[1], alpha(colour, 1.0));
    }

    /** An unfilled circle built from quads, the fallback when the sprite is missing. */
    private void ring(HudRenderer renderer, double r, double thickness, Color colour) {
        ring(renderer, r, thickness, colour, 48);
    }

    private void ring(HudRenderer renderer, double r, double thickness, Color colour, int steps) {
        double half = box / 2.0;
        double previousX = x + half + r;
        double previousY = y + half;

        for (int step = 1; step <= steps; step++) {
            double angle = (double) step / steps * Math.PI * 2.0;
            double px = x + half + Math.cos(angle) * r;
            double py = y + half + Math.sin(angle) * r;
            strip(renderer, new double[]{previousX - x, previousY - y}, new double[]{px - x, py - y},
                thickness / 2.0, colour);
            previousX = px;
            previousY = py;
        }
    }

    /**
     * A cell, as a square with a darker edge, or a dot when it is too small for one.
     *
     * <p>Under three pixels a bordered square is mostly border. A dot keeps the
     * position honest and stops a run of adjacent chunks turning into a smear.
     */
    private void plate(HudRenderer renderer, double[] p, Color colour) {
        if (cell < 3.0) {
            renderer.quad(x + p[0] - cell / 2.0, y + p[1] - cell / 2.0, cell, cell, colour);
            return;
        }

        double left = Math.max(0.0, p[0] - cell / 2.0);
        double top = Math.max(0.0, p[1] - cell / 2.0);
        double right = Math.min(box, p[0] + cell / 2.0);
        double bottom = Math.min(box, p[1] + cell / 2.0);
        if (right <= left || bottom <= top) return;

        renderer.quad(x + left, y + top, right - left, bottom - top,
            new Color(colour.r / 2, colour.g / 2, colour.b / 2, colour.a));
        renderer.quad(x + left + 1.0, y + top + 1.0,
            Math.max(0.0, right - left - 2.0), Math.max(0.0, bottom - top - 2.0), colour);
    }

    /**
     * A sprite drawn under the shapes.
     *
     * <p>The shape renderer batches and flushes when the element is finished, so a
     * texture drawn inline goes to the screen before any of them. That is exactly
     * what a background wants, and exactly what a frame does not — hence the two
     * methods rather than one.
     */
    private boolean spriteUnder(HudRenderer renderer, Identifier id, double px, double py, double w, double h,
                                Color c) {
        if (c.a <= 0) return true;
        if (!available(id)) return false;

        renderer.texture(id, x + px, y + py, w, h, c);
        return true;
    }

    /**
     * Whether the sprite is really in the pack.
     *
     * <p>Asked of the resource manager rather than the texture manager, which
     * answers a magenta placeholder for anything missing — a placeholder is worse
     * than the shape it was meant to replace, and the fallbacks exist precisely so
     * that a file gone astray costs nothing.
     */
    private boolean available(Identifier id) {
        return MeteorClient.mc.getResourceManager().getResource(id).isPresent();
    }

    /** A sprite drawn over the shapes, by way of the post queue. */
    private boolean spriteOver(HudRenderer renderer, Identifier id, double px, double py, double w, double h,
                               Color c) {
        if (c.a <= 0) return true;
        if (!available(id)) return false;

        renderer.post(() -> renderer.texture(id, x + px, y + py, w, h, c));
        return true;
    }

    /**
     * A sprite turned to an angle, which {@code HudRenderer} has no call for.
     *
     * <p>{@code Renderer2D} does, so this does by hand what {@code texture} does
     * for us otherwise: begin the textured batch, place one rotated quad, and hand
     * it the texture's own handles. Degrees, clockwise on screen, about the middle
     * of the quad.
     */
    private boolean spriteTurned(HudRenderer renderer, Identifier id, double px, double py, double w, double h,
                                 double degrees, Color c) {
        if (c.a <= 0) return true;
        if (!available(id)) return false;

        renderer.post(() -> {
            AbstractTexture texture = MeteorClient.mc.getTextureManager().getTexture(id);
            if (texture == null) return;

            Renderer2D.TEXTURE.begin();
            Renderer2D.TEXTURE.texQuad(x + px, y + py, w, h, degrees, 0.0, 0.0, 1.0, 1.0, c);
            Renderer2D.TEXTURE.render(texture.getGlTextureView(), texture.getSampler());
        });

        return true;
    }

    // Geometry

    /**
     * A continuous chunk position to pixels, north at the top before rotation.
     *
     * <p>Continuous, not an index. Your own position is {@code x / 16}, a marker's
     * is {@code block / 16}, but a chunk index {@code c} means the square from
     * {@code c} to {@code c + 1}, so its centre is {@code c + 0.5}. Everything
     * that comes from the fit — the line, the corridor, the memories — is in chunk
     * indices, and passing them straight through put the whole carpet half a chunk
     * off the chevron and the markers.
     */
    private double[] project(double chunkX, double chunkZ) {
        double dx = (chunkX - centreX) * scale;
        double dz = (chunkZ - centreZ) * scale;

        return new double[]{
            box / 2.0 + dx * cos - dz * sin,
            box / 2.0 + dx * sin + dz * cos
        };
    }

    /** A chunk index pair, projected at the centre of the chunk it names. */
    private double[] centreAt(double indexX, double indexZ) {
        return project(indexX + 0.5, indexZ + 0.5);
    }

    private double[] centreOf(double[] index) {
        return centreAt(index[0], index[1]);
    }

    private boolean outside(double[] p) {
        if (shape.get() == Shape.CIRCLE) {
            double half = box / 2.0;
            return Math.hypot(p[0] - half, p[1] - half) > half - 1.0;
        }

        return p[0] < 0.0 || p[1] < 0.0 || p[0] > box || p[1] > box;
    }

    /**
     * Cuts a segment down to the part inside the shape.
     *
     * <p>Nothing clips for us: a line drawn towards a point a thousand chunks away
     * is drawn all the way there, straight across whatever else is on the screen.
     */
    private double[] clip(double x0, double y0, double x1, double y1) {
        if (shape.get() == Shape.CIRCLE) return clipCircle(x0, y0, x1, y1);

        double dx = x1 - x0;
        double dy = y1 - y0;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0, box - x0, y0, box - y0};
        double t0 = 0.0;
        double t1 = 1.0;

        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return null;
                continue;
            }

            double r = q[i] / p[i];

            if (p[i] < 0.0) {
                if (r > t1) return null;
                if (r > t0) t0 = r;
            } else {
                if (r < t0) return null;
                if (r < t1) t1 = r;
            }
        }

        return new double[]{x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy};
    }

    /** The same, against the inscribed circle: the quadratic in the segment parameter. */
    private double[] clipCircle(double x0, double y0, double x1, double y1) {
        double half = box / 2.0;
        double r = half - 1.0;
        double fx = x0 - half;
        double fy = y0 - half;
        double dx = x1 - x0;
        double dy = y1 - y0;

        double a = dx * dx + dy * dy;
        if (a < 1.0E-9) return Math.hypot(fx, fy) <= r ? new double[]{x0, y0, x1, y1} : null;

        double b = 2.0 * (fx * dx + fy * dy);
        double c = fx * fx + fy * fy - r * r;
        double disc = b * b - 4.0 * a * c;
        if (disc < 0.0) return null;

        double root = Math.sqrt(disc);
        double t0 = Math.max(0.0, (-b - root) / (2.0 * a));
        double t1 = Math.min(1.0, (-b + root) / (2.0 * a));
        if (t1 <= t0) return null;

        return new double[]{x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy};
    }

    private Color faded(SettingColor base, double[] p) {
        double half = box / 2.0;
        double distance = Math.hypot(p[0] - half, p[1] - half) / half;
        return alpha(base, MathHelper.clamp(1.0 - 0.6 * distance, 0.4, 1.0) * chunkOpacity.get() / 100.0);
    }

    /** Every colour on the panel goes through here, so the fade in and out is global. */
    private Color alpha(Color base, double factor) {
        return new Color(base.r, base.g, base.b, (int) MathHelper.clamp(base.a * factor * fade, 0.0, 255.0));
    }

    private SettingColor pick(Setting<SettingColor> own, SettingColor fromModule) {
        return moduleColors.get() && fromModule != null ? fromModule : own.get();
    }

    private boolean hasSomethingToShow(ChunkRadar radar) {
        if (radar == null || !radar.isActive()) return false;
        if (MeteorClient.mc.world == null) return false;

        if (!MeteorClient.mc.world.getRegistryKey().equals(World.OVERWORLD)) {
            return !Double.isNaN(radar.guideHeading());
        }

        return radar.hasLine() || !radar.memories().isEmpty();
    }
}
