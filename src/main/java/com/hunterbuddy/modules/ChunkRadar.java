package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HuntFeed;
import com.hunterbuddy.util.TrailStore;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.feature.waypoint.WaypointAPI;
import xaeroplus.module.ModuleManager;
import xaeroplus.settings.Settings;
import xaeroplus.module.impl.PaletteNewChunks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Follows the 1.12 trail from the blocks in each arriving chunk.
 *
 * <p>The rule is deliberately looser than "no 1.17 block at all". Measured over
 * two flights, chunks carrying one to ten blocks of copper and a little tuff sit
 * on the trail line 90% of the time, against 18% for ordinary modern chunks —
 * and 18% is only the geometry of flying along the line. They are old chunks
 * XaeroPlus never colours, because it starts scanning at y=5 and the regeneration
 * left a dusting of copper, tuff and deepslate between 0 and 8. Taking them makes
 * the trail three times denser.
 *
 * <p>Nothing is claimed from a single chunk. A trail is a line of points, so the
 * radar only acquires on an alignment: five hits spread over thirty chunks,
 * sitting within a chunk of the fitted line. An isolated old chunk — a traveller
 * from 1.12, an artefact — is drawn pale and never raises anything.
 */
public class ChunkRadar extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLine = settings.createGroup("Line");
    private final SettingGroup sgAlerts = settings.createGroup("Alerts");
    private final SettingGroup sgMemory = settings.createGroup("Memory");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> addWaypoints = sgMemory.add(new BoolSetting.Builder()
        .name("add-waypoints")
        .description("Mark trails on the minimap: one waypoint where a trail was picked up and one that follows its far end, plus one for each crossing and each zone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> temporaryWaypoints = sgMemory.add(new BoolSetting.Builder()
        .name("temporary-waypoints")
        .description("Trail waypoints are removed when you disconnect.")
        .defaultValue(true)
        .visible(addWaypoints::get)
        .build()
    );

    private final Setting<Boolean> zoneWaypointsPermanent = sgMemory.add(new BoolSetting.Builder()
        .name("zone-waypoints-permanent")
        .description("Zone waypoints outlive the session even when the rest do not. A place someone stopped and built in 1.12 is worth coming back to; a heading is not.")
        .defaultValue(true)
        .visible(addWaypoints::get)
        .build()
    );

    private final Setting<Boolean> renderMemory = sgMemory.add(new BoolSetting.Builder()
        .name("render-memory")
        .description("Draw the trails remembered from earlier sessions. They are a picture and nothing else: no projection, no readout, no alerts, and they never enter a fit.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> include119 = sgGeneral.add(new BoolSetting.Builder()
        .name("include-1-19-upgraded")
        .description("Count ground from 1.19 and later as trail as well. The block rule only recognises 1.12, so over anything newer it finds nothing and draws nothing; this adds XaeroPlus's palette answer on top of it -- a chunk read off the server's disk instead of generated as you arrived is a chunk somebody has already been to. Ticking it switches on XaeroPlus's own Palette NewChunks module and its Version Upgraded option, both of which it needs and one of which ships turned off, so there is nothing else to go and tick.")
        .defaultValue(false)
        .onChanged(v -> syncXaero())
        .build()
    );

    private final Setting<Integer> copperMax = sgGeneral.add(new IntSetting.Builder()
        .name("copper-max")
        .description("Most copper a chunk may hold above y=0 and still count as old. Modern chunks start at 14 at the first percentile and sit at 78 in the middle, so ten separates them cleanly.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("How many arriving chunks to read each tick.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> memory = sgGeneral.add(new IntSetting.Builder()
        .name("memory")
        .description("How many chunks of each class to keep. Older ones fall off the back.")
        .defaultValue(512)
        .min(16)
        .sliderRange(16, 4096)
        .build()
    );

    private final Setting<Integer> forgetDistance = sgGeneral.add(new IntSetting.Builder()
        .name("forget-distance")
        .description("Chunks away from you a hit may be before it is forgotten. It leaves the fit and the map, and the ground it stood on becomes readable again if you ever fly back.")
        .defaultValue(500)
        .min(100)
        .sliderRange(100, 4000)
        .build()
    );

    private final Setting<meteordevelopment.meteorclient.utils.misc.Keybind> alignKey =
        sgGeneral.add(new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
            .name("align-key")
            .description("Turns you onto the line, once per press. Not a lock: the mouse is yours again the instant you touch it, and holding the key changes nothing. It picks whichever of the line's two directions you are already closer to facing, so it never spins you round.")
            .defaultValue(meteordevelopment.meteorclient.utils.misc.Keybind.none())
            .build()
        );

    private final Setting<meteordevelopment.meteorclient.utils.misc.Keybind> releaseKey =
        sgGeneral.add(new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
            .name("release-key")
            .description("Lets go of the line you are following, once per press. Nothing is forgotten: the entry and its waypoints stay, and if the chunks still arriving rebuild the same line it will be picked up again -- which is the right answer, because a trail that reacquires itself in five hits was really there.")
            .defaultValue(meteordevelopment.meteorclient.utils.misc.Keybind.none())
            .build()
        );

    private final Setting<meteordevelopment.meteorclient.utils.misc.Keybind> clearMarksKey =
        sgGeneral.add(new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
            .name("clear-marks-key")
            .description("Wipes what is drawn -- hit plates, zone plates, near misses, stored chunks -- without touching the line, the memory or the waypoints. For when a dense region has tiled the screen and you cannot read it any more.")
            .defaultValue(meteordevelopment.meteorclient.utils.misc.Keybind.none())
            .build()
        );

    private final Setting<Boolean> log = sgGeneral.add(new BoolSetting.Builder()
        .name("log")
        .description("Write every classified chunk and every event to hunterbuddy/chunk-radar-<date>.csv. Coordinates live here and nowhere else.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minPoints = sgLine.add(new IntSetting.Builder()
        .name("min-points")
        .description("Hits needed before the radar will claim a trail.")
        .defaultValue(5)
        .min(2)
        .sliderRange(2, 20)
        .build()
    );

    private final Setting<Integer> minSpread = sgLine.add(new IntSetting.Builder()
        .name("min-spread")
        .description("Chunks the hits have to span, end to end, before they count as a line rather than a cluster.")
        .defaultValue(30)
        .min(5)
        .sliderRange(5, 200)
        .build()
    );

    private final Setting<Double> maxResidual = sgLine.add(new DoubleSetting.Builder()
        .name("max-residual")
        .description("How far the hits may sit from the fitted line on average, in chunks. The measured trail holds to half a chunk.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 5.0)
        .build()
    );

    private final Setting<Double> forkDistance = sgLine.add(new DoubleSetting.Builder()
        .name("fork-distance")
        .description("A hit further than this from the line is left out of the fit and called a fork.")
        .defaultValue(3.0)
        .min(1.0)
        .sliderRange(1.0, 16.0)
        .build()
    );

    private final Setting<Integer> lostAfter = sgAlerts.add(new IntSetting.Builder()
        .name("lost-after")
        .description("Chunks travelled along the line with nothing found before the trail counts as lost.")
        .defaultValue(300)
        .min(50)
        .sliderRange(50, 800)
        .build()
    );

    private final Setting<Integer> headingSpread = sgAlerts.add(new IntSetting.Builder()
        .name("heading-spread")
        .description("Chunks the hits must span before the heading is worth quoting. Replayed on a real flight, the fitted heading is still five degrees out at thirty chunks of spread and inside one degree past fifty-five, so anything less announces a turn that is only the fit settling down.")
        .defaultValue(80)
        .min(30)
        .sliderRange(30, 300)
        .build()
    );

    private final Setting<Double> headingChange = sgAlerts.add(new DoubleSetting.Builder()
        .name("heading-change")
        .description("Degrees the fitted heading has to move before it is announced again.")
        .defaultValue(5.0)
        .min(1.0)
        .sliderRange(1.0, 45.0)
        .build()
    );

    private final Setting<Double> switchDegrees = sgAlerts.add(new DoubleSetting.Builder()
        .name("switch-angle")
        .description("How far off the live line a second alignment has to run before the radar hands the line over to it. Anything parallel is the same trail seen slightly to one side, and switching there would throw away the history for nothing.")
        .defaultValue(20.0)
        .min(5.0)
        .sliderRange(5.0, 90.0)
        .build()
    );

    private final Setting<Integer> loadedRadius = sgAlerts.add(new IntSetting.Builder()
        .name("loaded-radius")
        .description("How many chunks out the server actually sends you, measured at 11 to 13 on 2b2t. Only the crossing test uses it, and it looks twice this far.")
        .defaultValue(12)
        .min(4)
        .sliderRange(4, 32)
        .build()
    );

    private final Setting<Double> crossingAngle = sgAlerts.add(new DoubleSetting.Builder()
        .name("crossing-angle")
        .description("How far from your own course a nearby alignment has to run to be called a crossing. Note what this can and cannot see: the loaded band is about twenty chunks wide, so a trail crossed square on offers three or four hits and no one could call it. Dense trails and shallow angles are what show up.")
        .defaultValue(40.0)
        .min(20.0)
        .sliderRange(20.0, 90.0)
        .build()
    );

    private final Setting<Integer> resumeQuiet = sgAlerts.add(new IntSetting.Builder()
        .name("resume-quiet")
        .description("Minutes of silence a trail needs before picking it up again is worth telling Discord about. A three minute detour off a trail you have followed for two hours should not read on your phone as a new find.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 240)
        .build()
    );

    private final Setting<Integer> highwayBand = sgAlerts.add(new IntSetting.Builder()
        .name("highway-band")
        .description("Chunks either side of an axis or a diagonal that count as highway. Old chunks there are a road everyone has flown, not a find.")
        .defaultValue(13)
        .min(0)
        .sliderRange(0, 64)
        .build()
    );

    private final Setting<Integer> spawnRadius = sgAlerts.add(new IntSetting.Builder()
        .name("spawn-radius")
        .description("Blocks around the origin where nothing is announced. Everything within it is old and everyone has seen it.")
        .defaultValue(5000)
        .min(0)
        .sliderRange(0, 20000)
        .build()
    );

    private final Setting<Integer> regionDensity = sgAlerts.add(new IntSetting.Builder()
        .name("region-density")
        .description("Percentage of hits over the last thirty chunks of travel above which this is an old region rather than a trail through a new one. The plates keep being drawn, the line stops being fitted, and nothing is announced.")
        .defaultValue(40)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> alertHits = sgAlerts.add(new BoolSetting.Builder()
        .name("alert-hits")
        .description("Say something the moment a 1.12 chunk turns up, before there is any line to speak of. Off by default: it is the loudest thing the module does, and it is worth turning on deliberately rather than discovering it in flight.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hitSpacing = sgAlerts.add(new IntSetting.Builder()
        .name("hit-spacing")
        .description("Chunks a hit must be from every hit already known, announced or not, before it counts as a new place. Measured against the announced ones alone it meant nothing: the hits of one trail sit ten to twenty-six chunks apart, so every other one cleared the bar and you got an alert every ten seconds.")
        .defaultValue(64)
        .min(4)
        .sliderRange(4, 64)
        .visible(alertHits::get)
        .build()
    );

    private final Setting<Integer> zoneHits = sgAlerts.add(new IntSetting.Builder()
        .name("zone-hits")
        .description("Hits inside a five by five square that mean someone stopped and built there in 1.12. The rarest of the four events and the one worth a phone buzzing.")
        .defaultValue(9)
        .min(4)
        .sliderRange(4, 25)
        .build()
    );

    private final Setting<Boolean> hideCoordinates = sgAlerts.add(new BoolSetting.Builder()
        .name("hide-coordinates")
        .description("Chat, HUD and webhook give a distance and a direction, never a position. Coordinates go to the log file on disk and nowhere else. Leave this on unless you are certain who reads your Discord.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> webhook = sgAlerts.add(new BoolSetting.Builder()
        .name("webhook")
        .description("Send acquired, lost, turning and zone to a Discord webhook.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookLink = sgAlerts.add(new StringSetting.Builder()
        .name("webhook-link")
        .description("Discord webhook URL.")
        .defaultValue("")
        .visible(webhook::get)
        .build()
    );

    private final Setting<String> discordId = sgAlerts.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord id, to be pinged. Leave empty for no ping.")
        .defaultValue("")
        .visible(webhook::get)
        .build()
    );

    private final Setting<Integer> alertCooldown = sgAlerts.add(new IntSetting.Builder()
        .name("alert-cooldown")
        .description("Seconds between two webhook sends, whatever happens in between.")
        .defaultValue(60)
        .min(5)
        .sliderRange(5, 600)
        .build()
    );

    public enum PlateHeight {
        RELATIVE,
        FIXED
    }

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw the plates and the fitted line in the world.")
        .defaultValue(true)
        .build()
    );

    private final Setting<PlateHeight> plateHeightMode = sgRender.add(new EnumSetting.Builder<PlateHeight>()
        .name("plate-height")
        .description("Where the chunk plates are drawn: at an offset from you, or pinned to a height so they lie on the ground. The line is never affected, it always follows you.")
        .defaultValue(PlateHeight.RELATIVE)
        .visible(render::get)
        .build()
    );

    private final Setting<Integer> lineOffset = sgRender.add(new IntSetting.Builder()
        .name("line-offset")
        .description("Height of the line relative to you. Zero puts it at your own level, under your nose, which is where it is useful for steering. It is always relative to you: pinned to the ground it would vanish behind the first hill and the far end is what you are looking at.")
        .defaultValue(0)
        .min(-64)
        .sliderRange(-64, 64)
        .visible(render::get)
        .build()
    );

    private final Setting<Integer> plateOffset = sgRender.add(new IntSetting.Builder()
        .name("plate-offset")
        .description("Height of the chunk plates relative to you. Below by default, so a glance down reads the map without the line cutting through it.")
        .defaultValue(-12)
        .min(-64)
        .sliderRange(-64, 64)
        .visible(() -> render.get() && plateHeightMode.get() == PlateHeight.RELATIVE)
        .build()
    );

    private final Setting<Integer> plateY = sgRender.add(new IntSetting.Builder()
        .name("plate-y")
        .description("Height the plates are pinned to.")
        .defaultValue(64)
        .min(-64)
        .sliderRange(-64, 320)
        .visible(() -> render.get() && plateHeightMode.get() == PlateHeight.FIXED)
        .build()
    );

    private final Setting<Integer> plateOpacity = sgRender.add(new IntSetting.Builder()
        .name("plate-opacity")
        .description("Dims every plate at once, on top of whatever alpha its own colour carries: hits, pale ones, near misses, stored, remembered. The line is not touched, because the line is what you steer by.")
        .defaultValue(60)
        .min(0)
        .sliderRange(0, 100)
        .visible(render::get)
        .build()
    );

    private final Setting<Integer> projection = sgRender.add(new IntSetting.Builder()
        .name("projection")
        .description("How far ahead of you to extend the fitted line, in chunks.")
        .defaultValue(120)
        .min(0)
        .sliderRange(0, 500)
        .visible(render::get)
        .build()
    );

    private final Setting<Integer> tail = sgRender.add(new IntSetting.Builder()
        .name("tail")
        .description("How far behind you the line is drawn, in chunks. Behind means behind you, not behind the trail: the drawing follows where you look, the fit does not.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 200)
        .visible(render::get)
        .build()
    );

    private final Setting<Integer> memoryProjection = sgRender.add(new IntSetting.Builder()
        .name("memory-projection")
        .description("How far past its own ends a remembered trail is extended, in chunks, drawn broken so it cannot be mistaken for ground already covered. A 1.12 trail holds to half a chunk over fifty kilometres, so the extension is a fair guess. Zero turns it off.")
        .defaultValue(4000)
        .min(0)
        .sliderRange(0, 8000)
        .visible(() -> render.get() && renderMemory.get())
        .build()
    );

    private final Setting<Boolean> netherGuide = sgRender.add(new BoolSetting.Builder()
        .name("nether-guide")
        .description("In the Nether, draw the trail you were following at one eighth of its coordinates, so you can fly it down there. A guide and nothing else: no chunk is read and nothing is announced below.")
        .defaultValue(true)
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> renderRed = sgRender.add(new BoolSetting.Builder()
        .name("render-near-miss")
        .description("Also draw the near misses: copper just over the line with nothing else, or a single other marker in single figures.")
        .defaultValue(false)
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> renderStored = sgRender.add(new BoolSetting.Builder()
        .name("render-stored")
        .description("Also draw chunks the server had on disk rather than generating for you.")
        .defaultValue(false)
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> readout = sgRender.add(new BoolSetting.Builder()
        .name("readout")
        .description("Float the heading and your drift on the line itself. Off by default: the radar HUD carries the same three numbers where they hold still and can be read.")
        .defaultValue(false)
        .visible(render::get)
        .build()
    );

    private final Setting<Integer> readoutDistance = sgRender.add(new IntSetting.Builder()
        .name("readout-distance")
        .description("How far ahead the readout floats, in chunks. It used to ride the end of the projection, two thousand blocks out, where the anchor moves with every frame and the text dances too much to read.")
        .defaultValue(30)
        .min(5)
        .sliderRange(5, 200)
        .visible(() -> render.get() && readout.get())
        .build()
    );

    private final Setting<SettingColor> trailColor = sgRender.add(new ColorSetting.Builder()
        .name("trail-color")
        .description("A hit that belongs to the fitted line.")
        .defaultValue(new SettingColor(255, 190, 60, 220))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> looseColor = sgRender.add(new ColorSetting.Builder()
        .name("loose-color")
        .description("A hit that does not fit the line. Drawn pale on purpose: it claims nothing.")
        .defaultValue(new SettingColor(255, 190, 60, 70))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> redColor = sgRender.add(new ColorSetting.Builder()
        .name("near-miss-color")
        .description("Colour of a near miss.")
        .defaultValue(new SettingColor(200, 90, 90, 110))
        .visible(() -> render.get() && renderRed.get())
        .build()
    );

    private final Setting<SettingColor> storedColor = sgRender.add(new ColorSetting.Builder()
        .name("stored-color")
        .description("Colour of a chunk the server already had.")
        .defaultValue(new SettingColor(120, 140, 180, 80))
        .visible(() -> render.get() && renderStored.get())
        .build()
    );

    private final Setting<SettingColor> memoryColor = sgRender.add(new ColorSetting.Builder()
        .name("memory-color")
        .description("Trails remembered from earlier sessions.")
        .defaultValue(new SettingColor(150, 120, 220, 110))
        .visible(() -> render.get() && renderMemory.get())
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Colour of the fitted line and its projection.")
        .defaultValue(new SettingColor(120, 255, 200, 255))
        .visible(render::get)
        .build()
    );

    /** Marker families counted above y=0. Deepslate is absent on purpose: see the class note. */
    private static final Map<Block, Integer> MARKERS = buildMarkers();
    private static final int COPPER = 0;
    private static final int TUFF = 4;

    /**
     * Deepslate between y=5 and y=15, which no old chunk can have.
     *
     * <p>The retrogeneration that gave pre-1.18 chunks their underside cannot
     * write at or above y=0 at all, so the only deepslate in an old chunk is its
     * own 1.12 bedrock converted in place, and that stops at y=4. Measured: none
     * of the old chunks carry any above five, against 88% of the new ones.
     *
     * <p>Copper and tuff stay tolerated, and the difference is not arbitrary:
     * those come from features in the neighbouring new chunks, which are allowed
     * to write a chunk past their own edge. Deepslate is terrain, and terrain
     * does not spill.
     */
    private static final int DEEPSLATE_HIGH = 11;
    private static final int MARKER_COUNT = 12;

    private static final java.util.Set<Block> DEEPSLATE =
        java.util.Set.of(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE);

    /** A single other marker up to this many blocks still counts as a near miss. */
    private static final int NEAR_MARKER_MAX = 5;

    /** Drift past this many fork-distances, held for the delay below, is leaving the corridor. */
    private static final double DRIFT_MULTIPLIER = 3.0;
    private static final long DRIFT_HOLD_MS = 5000L;

    /** How far your course has to differ from the line before sideways counts as leaving. */
    private static final double DRIFT_ANGLE_DEGREES = 30.0;

    /** Hits at the head of the line that are fitted on their own to catch a corner. */
    private static final int ELBOW_WINDOW = 30;

    /** Length of a dash, and of the gap after it, on a projected memory. */
    private static final double DASH_CHUNKS = 8.0;

    /** How far past a remembered segment you may stand and still be picked up on it. */
    private static final double RESUME_MARGIN = 100.0;

    /** How long a sighting waits, in case the same line is acquired outright. */
    private static final long SIGHTING_HOLD_MS = 20_000L;

    /** A released line is drawn pale for this long, then it is gone. */
    private static final long RELEASE_FADE_MS = 30_000L;

    /** Off the line for this long and the line is let go, whatever the distance says. */
    private static final long OFF_LINE_RELEASE_MS = 60_000L;

    /** Two fitted lines within this many degrees of each other count as the same trail. */
    private static final double SAME_LINE_DEGREES = 10.0;

    /** A crossing needs this much, and no more: the loaded band cannot offer more. */
    private static final int CROSS_MIN_HITS = 4;
    private static final int CROSS_MIN_SPREAD = 12;

    /** Your own course is read over this window, and only trusted if it held steady. */
    private static final long COURSE_WINDOW_MS = 10_000L;
    private static final double COURSE_STABLE_DEGREES = 15.0;
    private static final int COURSE_SAMPLE_TICKS = 10;

    /** Forgetting is a second-by-second job, not a per-tick one. */
    private static final int FORGET_INTERVAL_TICKS = 20;

    private static final int ARRIVAL_LIMIT = 8192;

    private static final String OVERWORLD = World.OVERWORLD.getValue().toString();

    public enum Kind {
        HIT,
        NEAR,
        STORED,
        NONE,

        /**
         * The chunk arrived without the section the rule is read from.
         *
         * <p>No deepslate and no copper because there was nothing to look at is
         * not the same answer as no deepslate and no copper because the chunk is
         * old — and the first would read as the second. It is the likeliest shape
         * of a stray verdict, so it gets a name and a line in the log rather than
         * a guess.
         */
        UNREAD
    }

    /** A classified chunk waiting for the tick's fit before it is written down. */
    private record Row(ChunkPos pos, Kind kind, int[] counts) {}

    private final ConcurrentLinkedDeque<ChunkPos> arrivals = new ConcurrentLinkedDeque<>();
    private final LinkedHashMap<Long, Kind> seen = new LinkedHashMap<>();
    /**
     * Hits the live line owns, hits waiting to mean something, and hits that
     * belonged to a line already let go.
     *
     * <p>They used to be one list, refitted whole on every arrival. Follow one
     * trail, turn onto another crossing it, and the second trail's hits went
     * straight into the first one's fit: the first pass drew a diagonal between
     * them, the second threw nearly everything out, and the line became noise
     * until the sixty-second release rescued it. A hit now joins the line it is
     * near or waits in the pool, and the pool gets its own fit — which is what
     * lets a turn be recognised in seconds instead of a minute and a quarter.
     */
    private final List<ChunkPos> line = new ArrayList<>();
    private final List<ChunkPos> pool = new ArrayList<>();
    private final List<ChunkPos> archive = new ArrayList<>();
    private long archivedAt;
    private boolean poolDirty;

    private static final long WAYPOINT_SWEEP_MS = 10_000L;

    private final TrailStore store = new TrailStore();
    private final java.util.Set<MinimapWorld> pendingWaypointSaves =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /** The stored entry the live line is writing into, or null before one is acquired. */
    private TrailStore.Trail current;
    private String lastDimension;
    private long lastSweepMs;
    private boolean cleanupPending;
    private boolean resumePending;
    private boolean denseNow;
    private boolean alignPressed;
    private boolean releasePressed;
    private boolean clearMarksPressed;

    /** How long the wholesale delete stays primed once asked for. */
    private static final long CLEAR_ARM_MS = 10_000L;
    private long clearArmedAt;
    private float alignTarget = Float.NaN;
    private static final float ALIGN_STEP_DEGREES = 15.0F;
    private final List<ChunkPos> nearMisses = new ArrayList<>();
    private final List<ChunkPos> stored = new ArrayList<>();
    private final Set<Long> inliers = new HashSet<>();
    private final Set<Long> announcedZones = new HashSet<>();
    private final Set<Long> announcedCrossings = new HashSet<>();
    /**
     * A line seen but not claimed.
     *
     * <p>Held rather than announced at once: four hits over twelve chunks always
     * arrive before five over thirty, so every trail the radar ends up acquiring
     * would otherwise be announced twice, under two names, in two Discord
     * messages. If the acquisition comes within the hold, the sighting is dropped
     * without ever having been said. A crossing is not held — that one is never
     * going to be acquired, it runs across you.
     */
    private record Sighting(double originX, double originZ, double dirX, double dirZ,
                            String name, String title, String message, int blockX, int blockZ, long dueAt) {
        double lateral(double cx, double cz) {
            return (cx - originX) * -dirZ + (cz - originZ) * dirX;
        }

        double heading() {
            return MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dirZ, dirX)) - 90.0));
        }
    }

    private final List<Sighting> pendingSightings = new ArrayList<>();
    private final List<Sighting> placedSightings = new ArrayList<>();
    private final List<ChunkPos> zoneMarks = new ArrayList<>();
    private final List<ChunkPos> hitMarks = new ArrayList<>();

    /** Where you have been over the last ten seconds, for reading your own course. */
    private record Sample(long at, double x, double z) {}

    private final java.util.ArrayDeque<Sample> course = new java.util.ArrayDeque<>();
    private int courseTimer;

    private boolean fitted;
    private boolean aligned;
    private double originX;
    private double originZ;
    private double dirX = 1.0;
    private double dirZ;
    private double spread;
    private double residual;
    /** Furthest point along the line that a hit on it reaches; "lost" is measured from here. */
    private double spanEnd;
    private boolean acquired;
    private double announcedHeading = Double.NaN;
    private long lastAlertMs;
    private long lastForkMs;
    private final Map<String, Long> lastSentByType = new LinkedHashMap<>();
    private final Map<String, String> heldByType = new LinkedHashMap<>();
    private ChunkPos lastPlayerChunk;

    /** Recent classifications with where you were, for the density gate. */
    private final java.util.ArrayDeque<double[]> recent = new java.util.ArrayDeque<>();
    private boolean offLine;
    private long offLineAt;
    private long driftSince;

    /**
     * A line that has been let go.
     *
     * <p>Its geometry is copied rather than kept live: once the hits behind it
     * have been forgotten there is nothing left to fit, so a released line drawn
     * from the live parameters would vanish on the same tick instead of fading
     * out over half a minute.
     */
    /**
     * Set when a line is released for drift, cleared when you are back inside the
     * corridor.
     *
     * <p>Without it a drift release undoes itself: the hits behind the line are
     * still there and still aligned, so the next one re-acquires the very line
     * that was just let go, and parking off the corridor turns into a minute-long
     * cycle of acquire, off the line, release. Distance releases do not need this
     * — the evidence itself is gone.
     */
    private boolean awaitingReturn;
    private boolean released;
    private long releasedAt;
    private double relOriginX;
    private double relOriginZ;
    private double relDirX = 1.0;
    private double relDirZ;
    private int forgetTimer;

    private static final long LOG_FLUSH_MS = 5000L;

    private BufferedWriter writer;
    private int pendingFlush;
    private long lastFlushMs;

    public ChunkRadar() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "chunk-radar",
            "Follows the 1.12 trail from the blocks in each arriving chunk, fits a line through the hits, and calls out when it is acquired, lost, turning, or thick enough to be a stash. Overworld only for now.");
    }

    public static ChunkRadar get() {
        return Modules.get().get(ChunkRadar.class);
    }

    @Override
    public void onActivate() {
        reset();
        store.forceReload();
        cleanupPending = true;
        XaeroPlus.EVENT_BUS.register(this);
        syncXaero();
    }

    /**
     * Puts XaeroPlus's own switches where these settings say they should be.
     *
     * <p>The palette answers are not computed here, they are read out of
     * XaeroPlus's caches, and those caches are filled by a module that ships
     * turned off. Leaving that to the reader means one box ticked here and
     * nothing happening, with no way to tell that from a dimension that simply
     * has no trail in it -- so the box ticks the other one too.
     *
     * <p>Version Upgraded is the same story under a name that does not say what
     * it is for: without it, ground generated long ago and rewritten by the
     * server on its way to 1.19 reads as brand new, and a trail through it is
     * invisible.
     */
    private void syncXaero() {
        if (!include119.get()) return;

        try {
            if (!Settings.REGISTRY.paletteNewChunksEnabledSetting.get()) {
                Settings.REGISTRY.paletteNewChunksEnabledSetting.setValue(true);
                info("Switched on XaeroPlus's Palette NewChunks: it ships off, and nothing can be read while it is.");
            }

    Settings.REGISTRY.paletteNewChunksVersionUpgradedChunks.setValue(true);
        }
        catch (Exception e) {
            HunterBuddyAddon.LOG.warn("ChunkRadar: could not reach XaeroPlus's palette settings", e);
            warning("Could not reach XaeroPlus's palette settings. Tick Palette NewChunks yourself under Chunk Highlights.");
        }
    }

    @Override
    public void onDeactivate() {
        XaeroPlus.EVENT_BUS.unregister(this);
        reset();
        closeWriter();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // The live line goes; the remembered trails stay. They are on disk, they
        // outlive the session by design, and clearing them here would have made
        // the whole file pointless.
        flushWaypointSaves();
        reset();
        closeWriter();
    }

    private void reset() {
        current = null;
        lastDimension = null;
        lastSweepMs = 0L;
        arrivals.clear();
        seen.clear();
        line.clear();
        pool.clear();
        archive.clear();
        archivedAt = 0L;
        course.clear();
        announcedCrossings.clear();
        pendingSightings.clear();
        placedSightings.clear();
        zoneMarks.clear();
        hitMarks.clear();
        nearMisses.clear();
        stored.clear();
        inliers.clear();
        announcedZones.clear();
        fitted = false;
        aligned = false;
        acquired = false;
        spanEnd = 0.0;
        announcedHeading = Double.NaN;
        lastAlertMs = 0L;
        lastForkMs = 0L;
        lastSentByType.clear();
        heldByType.clear();
        recent.clear();
        lastPlayerChunk = null;
        alignTarget = Float.NaN;
        offLine = false;
        offLineAt = 0L;
        driftSince = 0L;
        released = false;
        releasedAt = 0L;
        awaitingReturn = false;
        forgetTimer = 0;
        spread = 0.0;
        residual = 0.0;
    }

    // Reading

    /**
     * The radar reads the overworld and nothing else.
     *
     * <p>There is one line, it is fitted up here, and below it is shown as the
     * guide -- the same trail at an eighth of the scale. Fitting a second line on
     * whatever happens to lie under the Nether roof would leave two lines
     * disagreeing by whatever angle separates them, when the one you came to fly
     * is already on the screen.
     */
    private boolean live() {
        return mc.world != null && mc.world.getRegistryKey().equals(World.OVERWORLD);
    }

    /** The dimension the store and the waypoints are keyed by: the one you are in. */
    private String dim() {
        return mc.world == null ? OVERWORLD : mc.world.getRegistryKey().getValue().toString();
    }

    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!live()) return;
        if (!event.chunk().getWorld().getRegistryKey().equals(mc.world.getRegistryKey())) return;
        if (arrivals.size() >= ARRIVAL_LIMIT) return;

        arrivals.addLast(event.chunk().getPos());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Watched before the overworld gate, so a portal is seen going both ways.
        String dimension = mc.world.getRegistryKey().getValue().toString();
        if (!dimension.equals(lastDimension)) {
            boolean returning = lastDimension != null && OVERWORLD.equals(dimension);
            lastDimension = dimension;
            store.forceReload();
            cleanupPending = true;
            resumePending = true;

            if (returning) rebaseAfterPortal();
        }

        // Rotation runs everywhere: in the Nether it aims at the guide line.
        handleAlignKey();
        handleReleaseKey();
        handleClearMarksKey();
        stepAlign();

        if (!live()) return;

        sweepWaypoints();
        checkTeleport();
        flushHeldAlerts();

        if (resumePending) {
            resumePending = false;
            resumeFromMemory();
        }

        denseNow = inOldRegion();
        boolean dense = denseNow;
        int budget = chunksPerTick.get();
        boolean changed = false;
        List<ChunkPos> fresh = new ArrayList<>();
        List<Row> rows = new ArrayList<>();

        while (budget-- > 0) {
            ChunkPos pos = arrivals.pollFirst();
            if (pos == null) break;

            long key = ChunkPos.toLong(pos.x, pos.z);
            if (seen.containsKey(key)) continue;

            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pos.x, pos.z);
            if (chunk == null) continue;

            int[] counts = scan(chunk);
            Kind kind = classify(counts, pos, readable(chunk));

            // Everything but UNREAD. That one is not an answer about the ground, it
            // is an answer about the delivery -- a chunk that arrived without the
            // section the rule reads. Filed in the map it would never be looked at
            // again, and on a trail running at four per cent density every hit
            // counts. Left out, the next delivery of the same chunk is read afresh.
            if (kind != Kind.UNREAD) {
                seen.put(key, kind);
                trim(seen, Math.max(4096, memory.get() * 4));
            }

            // The lists are checked as well as the seen map: that map is trimmed
            // after a few thousand chunks, so a chunk met again after a turn-round
            // would otherwise be counted twice — twice the weight in the fit, and
            // a zone made of the same five chunks seen on two passes.
            switch (kind) {
                case HIT -> {
                    // Inside an old region the plates keep coming and the line
                    // stops: fitting a line through ground that is old everywhere
                    // would produce a heading that means nothing, and announce it.
                    if (dense) {
                        if (!pool.contains(pos)) {
                            pool.add(pos);
                            while (pool.size() > memory.get()) pool.remove(0);
                        }
                    } else if (!line.contains(pos) && !pool.contains(pos)) {
                        // Assigned before anything is refitted: near the live line
                        // it joins it, otherwise it waits in the pool where it can
                        // grow into a line of its own without disturbing this one.
                        boolean owned = acquired && fitted
                            && Math.abs(lateral(pos.x, pos.z)) <= forkDistance.get();

                        List<ChunkPos> target = owned ? line : pool;
                        target.add(pos);
                        while (target.size() > memory.get()) target.remove(0);
                        if (!owned) poolDirty = true;

                        changed = owned || !acquired;
                        fresh.add(pos);
                    }
                }
                case NEAR -> {
                    if (!nearMisses.contains(pos)) {
                        nearMisses.add(pos);
                        while (nearMisses.size() > memory.get()) nearMisses.remove(0);
                    }
                }
                case STORED -> {
                    if (!stored.contains(pos)) {
                        stored.add(pos);
                        while (stored.size() > memory.get()) stored.remove(0);
                    }
                }
                default -> { }
            }

            rows.add(new Row(pos, kind, counts));

            if (mc.player != null) {
                Vec3d at = mc.player.getEntityPos();
                recent.addLast(new double[]{at.x / 16.0, at.z / 16.0, kind == Kind.HIT ? 1.0 : 0.0});
                while (recent.size() > 4096) recent.removeFirst();
            }
        }

        // Fit first, then judge the new hits against the line they just joined:
        // a fork or a zone is measured from the fitted line, not the previous one.
        if (changed) fit();
        if (changed) checkElbow();
        if (changed && acquired) updateTrail(false);

        // The log rows go out after the fit for the same reason. Written inside
        // the loop, a hit's along, lateral and inlier columns described the line as
        // it stood before that very hit moved it — the one row where those three
        // numbers matter most was the one row measuring the wrong line.
        for (Row row : rows) writeChunk(row.pos(), row.kind(), row.counts());

        for (ChunkPos pos : fresh) onHit(pos);
        checkLost();
        checkDrift();

        // Everything that speaks stays quiet inside an old region. Five aligned
        // points are trivial when every chunk is a hit, so a bascule there would
        // announce a heading that means nothing and write it to the file; and a
        // sighting would fire again and again on the same ground.
        if (!dense) switchIfPoolWins();
        flushSightings();

        if (++courseTimer >= COURSE_SAMPLE_TICKS) {
            courseTimer = 0;
            sampleCourse();
            writePosition();
            if (!dense) checkCrossing();
        }

        if (++forgetTimer >= FORGET_INTERVAL_TICKS) {
            forgetTimer = 0;
            forgetDistant();
        }
    }

    /**
     * Whether the section the whole rule rests on actually arrived.
     *
     * <p>The rule is read from y 0 to 15: no deepslate there, little copper, no
     * other marker. A chunk delivered without that section answers all three the
     * way an old chunk does, for no reason at all, and one stray verdict in a
     * thousand is exactly the shape of a false trail.
     */
    private boolean readable(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int index = (0 - chunk.getBottomY()) >> 4;

        if (index < 0 || index >= sections.length) return false;

        ChunkSection section = sections[index];
        return section != null && !section.isEmpty();
    }

    /** Marker counts above y=0, one slot per family. */
    private int[] scan(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = chunk.getBottomY();
        int[] counts = new int[MARKER_COUNT];

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (bottomY + (i << 4) < 0) continue;

            section.getBlockStateContainer().count((state, count) -> {
                Integer marker = MARKERS.get(state.getBlock());
                if (marker != null) counts[marker] += count;
            });

            // The one band worth a positional pass, and only when the palette says
            // there is deepslate in the section at all.
            if (bottomY + (i << 4) == 0 && section.hasAny(state -> DEEPSLATE.contains(state.getBlock()))) {
                for (int y = 5; y < 16 && counts[DEEPSLATE_HIGH] == 0; y++) {
                    for (int x = 0; x < 16 && counts[DEEPSLATE_HIGH] == 0; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (!DEEPSLATE.contains(section.getBlockState(x, y, z).getBlock())) continue;
                            counts[DEEPSLATE_HIGH]++;
                            break;
                        }
                    }
                }
            }
        }

        return counts;
    }

    /**
     * Which of the four buckets this chunk falls into.
     *
     * <p>A hit allows a little copper and tuff and nothing else. A near miss is
     * either copper just over the allowance with nothing else at all, or one
     * single other marker in single figures — the two shapes a genuine old chunk
     * could plausibly take if the allowance is set slightly too tight. Everything
     * else with markers is a modern chunk and is not drawn at all.
     */
    private Kind classify(int[] counts, ChunkPos pos, boolean readable) {
        if (!readable) return Kind.UNREAD;

        // Asked before the block rule, and on top of it rather than instead of it.
        // The blocks only recognise 1.12; over ground generated since, they find
        // nothing, and a trail through it is invisible however clear it is on the
        // map. The palette answers the other half of the question.
        if (include119.get() && stored(pos)) return Kind.HIT;

        int others = 0;
        int otherMax = 0;

        for (int i = 0; i < MARKER_COUNT; i++) {
            if (i == COPPER || i == TUFF || i == DEEPSLATE_HIGH) continue;
            if (counts[i] == 0) continue;
            others++;
            otherMax = Math.max(otherMax, counts[i]);
        }

        // Not a near miss either: this one is a proof, not a hint. An old chunk
        // cannot have it at all, so a chunk that does is modern however clean the
        // rest of it looks.
        if (counts[DEEPSLATE_HIGH] > 0) {
            return ModuleManager.getModule(PaletteNewChunks.class).isNewChunk(pos.x, pos.z, World.OVERWORLD)
                ? Kind.NONE
                : Kind.STORED;
        }

        // Tuff is measured and not judged, exactly like deepslate. The five old
        // chunks the rule was missing carried 14 to 85 blocks of it with almost no
        // copper: the regeneration drops tuff in lumps, so any threshold that
        // keeps those chunks lets everything through. It stays in the log because
        // the next question about it should be answered from data, not from
        // whether anyone remembered to count it.
        boolean copperOk = counts[COPPER] <= copperMax.get();

        if (copperOk && others == 0) return Kind.HIT;

        boolean copperEdge = counts[COPPER] > copperMax.get()
            && counts[COPPER] <= copperMax.get() * 2
            && others == 0;
        boolean oneOther = copperOk && others == 1 && otherMax <= NEAR_MARKER_MAX;

        if (copperEdge || oneOther) return Kind.NEAR;

        return ModuleManager.getModule(PaletteNewChunks.class).isNewChunk(pos.x, pos.z, World.OVERWORLD)
            ? Kind.NONE
            : Kind.STORED;
    }

    /**
     * The palette answer: did this chunk come off the server's disk?
     *
     * <p>Xaero keeps two caches and it is the second one that carries the hit. A
     * chunk missing from the new-chunk cache is not an old chunk, it is a chunk
     * that was never scanned -- seen once already, or arrived while the scan was
     * off. Only the inverse cache is a positive answer, so only it can say yes;
     * the rest is unread, and unread is a thing this module has always refused to
     * read as ancient.
     */
    private boolean stored(ChunkPos pos) {
        PaletteNewChunks module = ModuleManager.getModule(PaletteNewChunks.class);
        if (module == null) return false;

        return module.isInverseNewChunk(pos.x, pos.z, World.OVERWORLD);
    }

    // Line

    /**
     * Total least squares through the hits, refitted on the ones that agree.
     *
     * <p>Ordinary least squares would blow up on a trail running north-south,
     * where the line is nearly vertical in x. The principal axis has no preferred
     * direction. The second pass drops the hits further than fork-distance so one
     * traveller's stray chunk cannot lever the whole line off the trail.
     */
    private void fit() {
        // The live line is fitted on what it owns and on nothing else. Before
        // there is a line, the pool is all there is, and fitting it is how one
        // gets acquired in the first place.
        List<ChunkPos> source = acquired ? line : pool;

        if (source.size() < 2) {
            fitted = false;
            aligned = false;
            inliers.clear();
            return;
        }

        fitTo(source);

        List<ChunkPos> kept = new ArrayList<>();
        for (ChunkPos pos : source) {
            if (Math.abs(lateral(pos.x, pos.z)) <= forkDistance.get()) kept.add(pos);
        }

        if (kept.size() >= 2) fitTo(kept);

        orient(source);

        inliers.clear();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double squares = 0.0;
        int count = 0;

        for (ChunkPos pos : source) {
            double off = lateral(pos.x, pos.z);
            if (Math.abs(off) > forkDistance.get()) continue;

            inliers.add(ChunkPos.toLong(pos.x, pos.z));
            double at = along(pos.x, pos.z);
            min = Math.min(min, at);
            max = Math.max(max, at);
            squares += off * off;
            count++;
        }

        fitted = count >= 2;
        spread = count >= 2 ? max - min : 0.0;
        spanEnd = count >= 1 ? max : 0.0;
        residual = count > 0 ? Math.sqrt(squares / count) : 0.0;

        boolean nowAligned = count >= minPoints.get()
            && spread >= minSpread.get()
            && residual <= maxResidual.get();

        // A heading only means something once the hits span enough ground. Under
        // that, the fit is still settling and every new chunk swings it by more
        // than the turn threshold — which is how a flight that never turned once
        // reported a five degree turn seventy seconds in.
        boolean headingSolid = spread >= headingSpread.get();

        // Acquisition is tested on every fit, not only when alignment flips on. A
        // flag that clears one tick after the line came together would otherwise
        // cost the whole trail: the transition has already been spent, and it does
        // not come back until the line falls apart again.
        aligned = nowAligned;

        if (nowAligned && !acquired && !(awaitingReturn && sameAsReleased())) {
            // Whatever the old line still held goes to the archive first. Losing a
            // trail and picking up a different one within the forget distance used
            // to leave a live line that was the union of the two.
            archiveLine();

            // The aligned part of the pool becomes the line. What did not fit
            // stays behind as a candidate for the next one.
            pool.removeIf(pos -> {
                if (!inliers.contains(ChunkPos.toLong(pos.x, pos.z))) return false;
                line.add(pos);
                return true;
            });

            acquired = true;
            released = false;
            if (headingSolid) announcedHeading = heading();
            announceAcquisition(count, spread, headingSolid);
        }

        if (aligned && headingSolid) {
            double heading = heading();

            // The first heading worth quoting becomes the reference in silence.
            // Announcing it would be announcing a turn that never happened.
            if (Double.isNaN(announcedHeading)) {
                announcedHeading = heading;
            } else if (Math.abs(MathHelper.wrapDegrees((float) (heading - announcedHeading))) >= headingChange.get()) {
                announcedHeading = heading;
                alert("Trail turning", String.format(Locale.ROOT, "Heading now %.1f degrees.", heading));
            }
        }
    }

    /** A fit computed off to one side, without touching the live line. */
    private static final class LineFit {
        double originX;
        double originZ;
        double dirX = 1.0;
        double dirZ;
        double spread;
        double residual;
        int count;
        final List<ChunkPos> inliers = new ArrayList<>();

        double lateral(double cx, double cz) {
            return (cx - originX) * -dirZ + (cz - originZ) * dirX;
        }

        double along(double cx, double cz) {
            return (cx - originX) * dirX + (cz - originZ) * dirZ;
        }

        double heading() {
            return MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dirZ, dirX)) - 90.0));
        }
    }

    /**
     * Fits a list on its own terms.
     *
     * <p>Used for the pool and for the crossing test, both of which have to ask
     * "do these points make a line" without disturbing the one being followed.
     */
    private LineFit fitList(List<ChunkPos> points) {
        LineFit fit = new LineFit();
        if (points.size() < 2) return fit;

        principalAxis(points, fit);

        List<ChunkPos> kept = new ArrayList<>();
        for (ChunkPos pos : points) {
            if (Math.abs(fit.lateral(pos.x, pos.z)) <= forkDistance.get()) kept.add(pos);
        }

        if (kept.size() >= 2) principalAxis(kept, fit);

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double squares = 0.0;

        for (ChunkPos pos : points) {
            double off = fit.lateral(pos.x, pos.z);
            if (Math.abs(off) > forkDistance.get()) continue;

            fit.inliers.add(pos);
            double at = fit.along(pos.x, pos.z);
            min = Math.min(min, at);
            max = Math.max(max, at);
            squares += off * off;
        }

        fit.count = fit.inliers.size();
        fit.spread = fit.count >= 2 ? max - min : 0.0;
        fit.residual = fit.count > 0 ? Math.sqrt(squares / fit.count) : 0.0;
        return fit;
    }

    private static void principalAxis(List<ChunkPos> points, LineFit fit) {
        double sumX = 0.0;
        double sumZ = 0.0;

        for (ChunkPos pos : points) {
            sumX += pos.x;
            sumZ += pos.z;
        }

        fit.originX = sumX / points.size();
        fit.originZ = sumZ / points.size();

        double sxx = 0.0;
        double szz = 0.0;
        double sxz = 0.0;

        for (ChunkPos pos : points) {
            double dx = pos.x - fit.originX;
            double dz = pos.z - fit.originZ;
            sxx += dx * dx;
            szz += dz * dz;
            sxz += dx * dz;
        }

        double theta = 0.5 * Math.atan2(2.0 * sxz, sxx - szz);
        fit.dirX = Math.cos(theta);
        fit.dirZ = Math.sin(theta);
    }

    /** Angle between two lines, which have no direction: never more than a right angle. */
    private static double between(double a, double b) {
        double delta = Math.abs(MathHelper.wrapDegrees((float) (a - b)));
        return delta > 90.0 ? 180.0 - delta : delta;
    }

    /**
     * Hands the line over when the pool has become a better one.
     *
     * <p>Only on an angle. A pool that aligns parallel to the line is the same
     * trail seen a little to one side — the fit correcting itself, which is what
     * the second flight did in its first seconds — and switching there would
     * throw away the history for nothing.
     */
    private void switchIfPoolWins() {
        if (!acquired || !fitted || pool.size() < minPoints.get()) return;

        // Only when the pool has actually gained something. Refitting it every
        // tick would put back the pass over five hundred points that splitting the
        // lists was meant to remove.
        if (!poolDirty) return;
        poolDirty = false;

        LineFit candidate = fitList(pool);
        if (candidate.count < minPoints.get()) return;
        if (candidate.spread < minSpread.get()) return;
        if (candidate.residual > maxResidual.get()) return;

        double turn = between(candidate.heading(), heading());
        if (turn < switchDegrees.get()) return;

        releaseLine(String.format(Locale.ROOT, "switched to a trail at %.0f degrees to it", turn));

        pool.removeIf(pos -> {
            if (!candidate.inliers.contains(pos)) return false;
            line.add(pos);
            return true;
        });

        originX = candidate.originX;
        originZ = candidate.originZ;
        dirX = candidate.dirX;
        dirZ = candidate.dirZ;
        orient(line);

        acquired = true;
        released = false;
        awaitingReturn = false;
        announcedHeading = candidate.spread >= headingSpread.get() ? heading() : Double.NaN;

        announceAcquisition(candidate.count, candidate.spread, candidate.spread >= headingSpread.get());
    }

    private void fitTo(List<ChunkPos> points) {
        double sumX = 0.0;
        double sumZ = 0.0;

        for (ChunkPos pos : points) {
            sumX += pos.x;
            sumZ += pos.z;
        }

        originX = sumX / points.size();
        originZ = sumZ / points.size();

        double sxx = 0.0;
        double szz = 0.0;
        double sxz = 0.0;

        for (ChunkPos pos : points) {
            double dx = pos.x - originX;
            double dz = pos.z - originZ;
            sxx += dx * dx;
            szz += dz * dz;
            sxz += dx * dz;
        }

        double theta = 0.5 * Math.atan2(2.0 * sxz, sxx - szz);
        dirX = Math.cos(theta);
        dirZ = Math.sin(theta);
    }

    /**
     * Gives the line a direction: the one the trail was discovered in.
     *
     * <p>The principal axis has no sign of its own. Taking the player's velocity
     * at the instant of the fit does the job on a straight run and flips the line
     * the moment they turn round to mark a chunk — and a flipped line reads as a
     * 180 degree heading change, which is an alert. The oldest and newest hits on
     * the line do not move; only when they sit too close to tell does the
     * player's own direction settle it.
     */
    private void orient(List<ChunkPos> source) {
        ChunkPos first = null;
        ChunkPos last = null;

        for (ChunkPos pos : source) {
            if (Math.abs(lateral(pos.x, pos.z)) > forkDistance.get()) continue;
            if (first == null) first = pos;
            last = pos;
        }

        double sign = 0.0;
        if (first != null && last != null) sign = along(last.x, last.z) - along(first.x, first.z);

        if (Math.abs(sign) < 1.0) {
            double[] forward = forward();
            sign = dirX * forward[0] + dirZ * forward[1];
        }

        if (sign < 0.0) {
            dirX = -dirX;
            dirZ = -dirZ;
        }
    }

    /** Where you are going: velocity when you are moving, otherwise where you look. */
    private double[] forward() {
        if (mc.player == null) return new double[]{1.0, 0.0};

        Vec3d velocity = mc.player.getVelocity();
        if (velocity.horizontalLengthSquared() > 1.0E-4) {
            return new double[]{velocity.x, velocity.z};
        }

        double yaw = Math.toRadians(mc.player.getYaw());
        return new double[]{-Math.sin(yaw), Math.cos(yaw)};
    }

    /**
     * Where you are, in the frame the fit lives in.
     *
     * <p>The fit is built from chunk indices, and a chunk of index {@code c}
     * covers the ground from {@code c} to {@code c + 1} — its middle is
     * {@code c + 0.5}. A continuous position divided by sixteen is therefore half
     * a chunk ahead of the index it belongs to, and comparing the two put every
     * distance to the line out by up to eleven blocks: standing exactly on the
     * line as drawn, the drift read eleven and not zero.
     *
     * <p>One place, so the thirteen sites that ask the question cannot answer it
     * thirteen ways.
     */
    private double[] playerIndex() {
        return playerIndex(1.0);
    }

    /**
     * @param toOverworld 8 when you are below and the geometry is an overworld
     *                    one, so the two are compared in the same world
     */
    private double[] playerIndex(double toOverworld) {
        if (mc.player == null) return new double[]{0.0, 0.0};

        Vec3d at = mc.player.getEntityPos();
        return new double[]{at.x * toOverworld / 16.0 - 0.5, at.z * toOverworld / 16.0 - 0.5};
    }

    /** Your distance along the live line, and across it, both in chunks. */
    private double playerAlong() {
        double[] me = playerIndex();
        return along(me[0], me[1]);
    }

    private double playerLateral() {
        double[] me = playerIndex();
        return lateral(me[0], me[1]);
    }

    private double playerRelLateral() {
        double[] me = playerIndex();
        return relLateral(me[0], me[1]);
    }

    /** The same, measured on the frozen geometry of a line that has been let go. */
    private double playerAlongReleased() {
        double[] me = playerIndex();
        return (me[0] - relOriginX) * relDirX + (me[1] - relOriginZ) * relDirZ;
    }

    private double along(double chunkX, double chunkZ) {
        return (chunkX - originX) * dirX + (chunkZ - originZ) * dirZ;
    }

    private double lateral(double chunkX, double chunkZ) {
        return (chunkX - originX) * -dirZ + (chunkZ - originZ) * dirX;
    }

    private double heading() {
        return MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dirZ, dirX)) - 90.0));
    }

    public boolean isAligned() {
        return aligned;
    }

    // Events

    /**
     * Announces the first chunk of a cluster, and only the first.
     *
     * <p>A cluster runs from one chunk to seventeen, so announcing each one turns
     * a single find into a stream nobody reads. The spacing is measured against
     * every hit already announced this session, which makes the rule "a new
     * place", not "a new chunk".
     */
    private void announceHit(ChunkPos pos) {
        if (!alertHits.get() || denseNow || onHighwayOrSpawn()) return;

        // A chunk on the trail you are already following is not a suspect chunk.
        // The clusters run twenty to two hundred and sixty chunks apart, so every
        // one of them clears the spacing rule and you would collect an alert and a
        // waypoint for each while simply flying the line you came for.
        if (acquired && fitted && Math.abs(lateral(pos.x, pos.z)) <= forkDistance.get()) return;

        // Against every hit already known, not only the ones already said out
        // loud. The hits of a single trail are ten to twenty-six chunks apart, so
        // measuring against the announced ones alone let every other one through
        // and turned one find into an alert every ten seconds.
        int spacingSq = hitSpacing.get() * hitSpacing.get();

        for (ChunkPos known : hits()) {
            if (known.equals(pos)) continue;
            int dx = known.x - pos.x;
            int dz = known.z - pos.z;
            if (dx * dx + dz * dz < spacingSq) return;
        }

        for (ChunkPos told : hitMarks) {
            int dx = told.x - pos.x;
            int dz = told.z - pos.z;
            if (dx * dx + dz * dz < spacingSq) return;
        }

        hitMarks.add(pos);
        while (hitMarks.size() > 256) hitMarks.remove(0);

        alert("Old chunk", describe(pos));
        putWaypoint(dim(), "Old " + tag(ChunkPos.toLong(pos.x, pos.z)), "O", 5,
            (pos.x << 4) + 8, (pos.z << 4) + 8, temporaryWaypoints.get());
    }

    /**
     * Takes down the single-chunk marks a trail has grown to cover.
     *
     * <p>They said "something here" before there was a line; the line says it
     * better and says where it goes. Three markers fifty blocks apart on one trail
     * is a map you stop trusting.
     */
    private void absorbHitMarks() {
        if (!fitted) return;

        hitMarks.removeIf(pos -> {
            if (Math.abs(lateral(pos.x, pos.z)) > forkDistance.get()) return false;

            double at = along(pos.x, pos.z);
            if (at < spanEnd - spread || at > spanEnd) return false;

            dropWaypoint(dim(), "Old " + tag(ChunkPos.toLong(pos.x, pos.z)));
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
            return true;
        });
    }

    private void onHit(ChunkPos pos) {
        announceHit(pos);
        checkZone(pos);

        if (!fitted) return;

        double off = Math.abs(lateral(pos.x, pos.z));
        long now = System.currentTimeMillis();

        // Chat only, and throttled: a real fork produces a run of off-line hits,
        // and one line each would bury everything else.
        if (aligned && off >= forkDistance.get() && now - lastForkMs >= alertCooldown.get() * 1000L) {
            lastForkMs = now;
            info("Trail forking: a hit sits %.1f chunks off the line, %s.", off, describe(pos));
        }
    }

    /**
     * Nine hits inside a five by five square is not a trail passing through, it is
     * somewhere a 1.12 player stopped and stayed.
     */
    private void checkZone(ChunkPos centre) {
        // Only on blocks. A square of ancient ground is somewhere a 1.12 player
        // stopped and stayed, which is the whole claim; a square of chunks the
        // server had already stored is somewhere anybody has ever stood -- every
        // portal, every rest on a highway, every base of the last ten years. The
        // rule counts nine hits in five chunks, and with 1.19 ground included a
        // plain stretch of trail clears that on its own, over and over, along the
        // same line. There is no threshold that saves it, so it does not run.
        if (include119.get()) return;

        int count = 0;
        int offCorridor = 0;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        // Both lists: a zone is a thing on the ground, and whether a hit happens to
        // belong to the line being followed says nothing about that.
        for (ChunkPos pos : hits()) {
            if (Math.abs(pos.x - centre.x) > 2 || Math.abs(pos.z - centre.z) > 2) continue;
            count++;
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
            if (fitted && Math.abs(lateral(pos.x, pos.z)) >= 1.5) offCorridor++;
        }

        if (count < zoneHits.get()) return;
        if (onHighwayOrSpawn()) return;

        // Dense is not enough: a thick stretch of trail, two chunks wide, puts nine
        // hits in a five by five square on its own. A zone is wide as well —
        // hits well off the corridor when there is a line to measure from, a
        // square footprint when there is not.
        boolean wide = fitted ? offCorridor >= 3 : (maxX - minX >= 2 && maxZ - minZ >= 2);
        if (!wide) return;

        long zone = ChunkPos.toLong(Math.floorDiv(centre.x, 5), Math.floorDiv(centre.z, 5));
        if (!announcedZones.add(zone)) return;

        zoneMarks.add(centre);
        while (zoneMarks.size() > 64) zoneMarks.remove(0);

        alert("Old-chunk zone", String.format(Locale.ROOT,
            "%d hits inside five chunks, %s.", count, describe(centre)));

        // A tag per zone, not a constant name: putWaypoint drops the homonym before
        // it places, so a fixed "Zone 1.12" meant every zone erased the one before
        // it. The tag is a hash of the cell rather than the cell itself, because a
        // waypoint name is one of the places coordinates must not appear.
        putWaypoint(dim(), "Zone 1.12 " + tag(zone), "Z", 1,
            (centre.x << 4) + 8, (centre.z << 4) + 8, !zoneWaypointsPermanent.get());
    }

    /**
     * Lost means past the far end of what has been found, not merely far from the
     * last hit in time. Turning round and flying back over your own hits is not
     * losing the trail, and those chunks are already seen, so they would never
     * refresh a "last hit".
     */
    private void checkLost() {
        if (!acquired || !fitted || mc.player == null) return;

        Vec3d at = mc.player.getEntityPos();

        // Inside an old region the clock does not run: the hits are still arriving,
        // they are simply not being fitted, and losing a trail because the ground
        // around it is old would be exactly backwards.
        if (denseNow) {
            spanEnd = playerAlong();
            return;
        }

        double travelled = playerAlong() - spanEnd;
        if (travelled < lostAfter.get()) return;

        acquired = false;
        aligned = false;
        offLine = false;
        driftSince = 0L;
        alert("Trail lost", String.format(Locale.ROOT,
            "%.0f chunks past the last hit on the line with nothing found.", travelled));
    }

    /**
     * Turns you onto the line on a keypress, and then lets go.
     *
     * <p>Yaw only. The line is horizontal, so pitch is yours — on an elytra it is
     * the whole of your speed, and a radar that flattened it every time you asked
     * for a heading would be unusable. It aims at whichever end of the line you
     * are already closer to facing, which is what stops it spinning you round when
     * you are flying the trail backwards.
     */
    /**
     * The buttons that go with the module in the list.
     *
     * <p>Laid out by what each one costs, because that is the only thing worth
     * knowing before clicking: the first row is undone by flying on, the second
     * costs one trail, the third costs markers, and the last is the only thing
     * here that flying again does not bring back -- so it is the only one that has
     * to be clicked twice, and it leaves a copy behind.
     *
     * <p>Each button answers in its own label rather than in the chat: the panel
     * is where you clicked, so it is where the result belongs.
     */
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        int remembered = store.all().size();
        list.add(theme.label(remembered == 0
            ? "No trails remembered."
            : remembered + " trail(s) remembered" + (current == null ? "." : ", following one of them.")));

        // Costs nothing: the memory is untouched and the ground can be flown again.
        WHorizontalList flight = list.add(theme.horizontalList()).widget();

        WButton release = flight.add(theme.button("Let go of line")).widget();
        release.action = () -> release.set(releaseByHand() ? "Let go" : "No line");

        WButton marks = flight.add(theme.button("Clear marks")).widget();
        marks.action = () -> {
            int wiped = clearMarks();
            marks.set(wiped == 0 ? "Nothing drawn" : "Cleared " + wiped);
        };

        WButton reread = flight.add(theme.button("Reread ground")).widget();
        reread.action = () -> reread.set("Rereading " + reread() + " chunk(s)");

        // Costs one trail, and says which so a slip shows up at once.
        WButton forgetOne = list.add(theme.button("Forget the trail being followed")).widget();
        forgetOne.action = () -> {
            TrailStore.Trail trail = forgetCurrent();
            forgetOne.set(trail == null
                ? "Not following one"
                : String.format(Locale.ROOT, "Forgot heading %.0f", trail.heading));
        };

        // Costs markers. Hits and sightings come back on the next pass; zones do
        // not exist anywhere else, which is why they are a separate button.
        WHorizontalList waypoints = list.add(theme.horizontalList()).widget();

        WButton hitMarkers = waypoints.add(theme.button("Clear hit markers")).widget();
        hitMarkers.action = () -> hitMarkers.set("Removed " + clearWaypoints(false));

        WButton zoneMarkers = waypoints.add(theme.button("Clear zone markers")).widget();
        zoneMarkers.action = () -> zoneMarkers.set("Removed " + clearWaypoints(true));

        // The only thing here flying again does not undo.
        WHorizontalList memory = list.add(theme.horizontalList()).widget();

        WButton forgetAll = memory.add(theme.button("Forget all trails")).widget();
        forgetAll.action = () -> {
            List<TrailStore.Trail> all = List.copyOf(store.all());

            if (all.isEmpty()) {
                forgetAll.set("Nothing to forget");
                return;
            }

            long now = System.currentTimeMillis();

            if (now - clearArmedAt > CLEAR_ARM_MS) {
                clearArmedAt = now;
                forgetAll.set("Click again to forget " + all.size());
                return;
            }

            clearArmedAt = 0L;

            // A copy that was attempted and failed is a reason to stop, not to
            // carry on without one.
            if (!store.backup()) {
                forgetAll.set("Could not copy; nothing forgotten");
                return;
            }

            boolean kept = TrailStore.hasBackup();

            for (TrailStore.Trail trail : all) forgetTrail(trail);

            forgetAll.set(kept ? "Forgot " + all.size() + ", copy kept" : "Forgot " + all.size());
        };

        WButton restore = memory.add(theme.button("Restore copy")).widget();
        restore.action = () -> {
            if (!TrailStore.hasBackup()) {
                restore.set("No copy");
                return;
            }

            restore.set(restoreTrails() ? "Restored " + store.all().size() : "Restore failed");
        };

        return list;
    }

    private void handleReleaseKey() {
        if (mc.currentScreen != null || !releaseKey.get().isPressed()) {
            releasePressed = false;
            return;
        }

        if (releasePressed) return;
        releasePressed = true;

        if (!releaseByHand()) warning("No line to let go of.");
    }

    private void handleClearMarksKey() {
        if (mc.currentScreen != null || !clearMarksKey.get().isPressed()) {
            clearMarksPressed = false;
            return;
        }

        if (clearMarksPressed) return;
        clearMarksPressed = true;

        int wiped = clearMarks();
        info(wiped == 0 ? "Nothing drawn to clear." : "Cleared " + wiped + " mark(s) from the screen.");
    }

    /**
     * Lets go of the line under your hand, keeping everything it taught us.
     *
     * <p>Deliberately not a ban on the trail: if the chunks still arriving rebuild
     * the same alignment, it comes back, and a trail that reacquires itself in
     * five hits over thirty chunks was really there. The drift guard exists for
     * the case where the line was wrong; this is the case where you simply want
     * to fly somewhere else.
     *
     * <p>One thing to know: a trail already in the file can also be picked back up
     * on the next portal, the same way it would after a disconnect. Letting go is
     * not forgetting -- for that there is a separate word.
     */
    public boolean releaseByHand() {
        if (!hasLine() && !acquired) return false;

        releaseLine("dropped by hand", false);
        line.clear();
        pool.clear();

        return true;
    }

    /**
     * Forgets the trail being followed, entry and waypoints together.
     *
     * <p>The listing exists for the trails you are not on; this is the one you are
     * looking at, and having to find its number in a list to drop it is how the
     * wrong number gets typed. Returns what was forgotten so the caller can say
     * which one it was -- a heading and a length are enough to notice a mistake
     * immediately, which is the whole guard here.
     */
    public TrailStore.Trail forgetCurrent() {
        TrailStore.Trail trail = current;
        if (trail == null) return null;

        forgetTrail(trail);
        releaseByHand();

        return trail;
    }

    /**
     * Reads the copy back and puts the markers with it.
     *
     * <p>The delete took the waypoints too, and nothing else ever puts those back
     * when they are permanent -- the temporary ones are replaced at the next
     * sweep, the permanent ones are not. Left missing, the sweep reads a start
     * marker that is gone as the gesture that means "forget this trail", and the
     * whole restored file is dropped again within ten seconds.
     */
    public boolean restoreTrails() {
        if (!store.restore()) return false;

        String dimension = dim();

        for (TrailStore.Trail trail : store.forDimension(dimension)) {
            if (trail.startWaypoint == null) continue;

            putWaypoint(dimension, trail.startWaypoint, "T", 4, trail.startX, trail.startZ, temporaryWaypoints.get());

            if (trail.endWaypoint != null) {
                putWaypoint(dimension, trail.endWaypoint, "E", 4, trail.endX, trail.endZ, temporaryWaypoints.get());
            }
        }

        flushWaypointSaves();
        SupportMods.xaeroMinimap.requestWaypointsRefresh();

        return true;
    }

    /** Wipes what is drawn and nothing else. Returns how many marks went. */
    public int clearMarks() {
        int count = hitMarks.size() + zoneMarks.size() + nearMisses.size() + stored.size();

        hitMarks.clear();
        zoneMarks.clear();
        nearMisses.clear();
        stored.clear();

        return count;
    }

    /**
     * Forgets which chunks have already been classified, so they are read again.
     *
     * <p>The map is what makes a second pass over the same ground cost nothing. It
     * is also what makes a setting changed mid-flight -- include-1-19-upgraded
     * above all -- apply only to ground you have not seen yet. Emptying it is the
     * cheap way to re-ask the question, and until now the only way was to toggle
     * the module, which throws away the line, the course and the open log with it.
     */
    public int reread() {
        seen.clear();

        // Emptying the map is not asking the question again: nothing re-delivers a
        // chunk the server has already sent, so standing still after changing a
        // setting would show exactly what it showed before. The chunks the client
        // still holds go back in the queue instead. The lists guard against
        // duplicates on their own, and the map they were checked against is empty.
        if (mc.world == null || mc.player == null) return 0;

        int radius = mc.options.getClampedViewDistance();
        ChunkPos me = mc.player.getChunkPos();
        int requeued = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = me.x + dx;
                int cz = me.z + dz;

                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;

                arrivals.addLast(new ChunkPos(cx, cz));
                requeued++;
            }
        }

        return requeued;
    }

    /**
     * Removes the markers the radar itself placed, by name, in this dimension.
     *
     * <p>Never by pattern: only the exact shapes this module writes, so a waypoint
     * you placed by hand cannot be caught in it however it is named. The trail
     * pairs are left out -- those are the memory's own handles, and deleting one
     * is the gesture that forgets a trail.
     *
     * <p>Zones are asked for separately because they are the one kind that is not
     * reproducible: a hit or a sighting comes back the next time you fly over it,
     * a zone lives only as its marker. Their positions go to the log on the way
     * out, which is where coordinates are allowed to live.
     */
    public int clearWaypoints(boolean zones) {
        MinimapWorld world = waypointWorld();
        if (world == null) return 0;

        List<String> doomed = new ArrayList<>();

        for (WaypointSet set : world.getIterableWaypointSets()) {
            for (Waypoint existing : set.getWaypoints()) {
                String name = existing.getName();
                if (name == null) continue;

                boolean isZone = isZoneName(name);

                if (zones ? isZone : isHitName(name) || isSightingName(name)) {
                    // The only copy of where a zone was, and the reason it is
                    // written whether or not the log is switched on: everything
                    // else here comes back on the next pass, a zone does not.
                    if (isZone) {
                        writeEvent("Zone waypoint removed",
                            String.format(Locale.ROOT, "%s at %d %d", name, existing.getX(), existing.getZ()), true);
                    }

                    doomed.add(name);
                }
            }
        }

        if (doomed.isEmpty()) return 0;

        // Queues the world for saving, which is what makes the removal survive a
        // restart when the markers are permanent.
        waypointSet(dim());

        for (String name : doomed) dropWaypoint(dim(), name);

        SupportMods.xaeroMinimap.requestWaypointsRefresh();
        flushWaypointSaves();

        return doomed.size();
    }

    /**
     * The exact shapes this module writes, and nothing that merely looks like one.
     *
     * <p>A prefix test would have taken a waypoint of your own called "Old base"
     * with it. The tag is a hex word from {@link #tag}, so requiring it is enough
     * to make a collision impossible in practice.
     */
    private static boolean isHitName(String name) {
        return name.startsWith("Old ") && isTag(name.substring(4));
    }

    private static boolean isZoneName(String name) {
        return name.startsWith("Zone 1.12 ") && isTag(name.substring(10));
    }

    private static boolean isSightingName(String name) {
        String[] parts = name.split(" ");
        if (parts.length != 3 || !parts[0].equals("Sighting")) return false;

        try {
            Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        return isTag(parts[2]);
    }

    private static boolean isTag(String text) {
        if (text.isEmpty() || text.length() > 8) return false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c < '0' || c > '9') && (c < 'A' || c > 'F')) return false;
        }

        return true;
    }

    private void handleAlignKey() {
        if (mc.currentScreen != null || !alignKey.get().isPressed()) {
            alignPressed = false;
            return;
        }

        if (alignPressed) return;
        alignPressed = true;

        if (mc.player == null) return;

        // Below, it aims at the guide: the same trail at an eighth of the scale,
        // which is exactly the heading you want to be flying down there.
        double target = live() && hasLine() ? heading() : guideHeading();

        if (Double.isNaN(target)) {
            warning("No line to turn onto yet.");
            return;
        }

        double yaw = mc.player.getYaw();
        boolean reversed = Math.abs(MathHelper.wrapDegrees((float) (target - yaw))) > 90.0;
        alignTarget = MathHelper.wrapDegrees((float) (target + (reversed ? 180.0 : 0.0)));
        info("Lining up on %.1f degrees.", alignTarget);
    }

    /**
     * Walks the yaw onto the target over a few ticks.
     *
     * <p>Setting it outright teleports the view, which looks wrong and reads worse
     * to anything watching rotations. Fifteen degrees a tick covers a right angle
     * in a third of a second and still passes through every angle in between.
     * {@code lastYaw} is moved with it so the renderer interpolates from where you
     * were rather than snapping across the gap.
     */
    private void stepAlign() {
        if (Float.isNaN(alignTarget) || mc.player == null) return;

        float current = mc.player.getYaw();
        float delta = MathHelper.wrapDegrees(alignTarget - current);

        if (Math.abs(delta) <= 0.5F) {
            mc.player.lastYaw = current;
            mc.player.setYaw(alignTarget);
            alignTarget = Float.NaN;
            return;
        }

        mc.player.lastYaw = current;
        mc.player.setYaw(current + MathHelper.clamp(delta, -ALIGN_STEP_DEGREES, ALIGN_STEP_DEGREES));
    }

    private void sampleCourse() {
        if (mc.player == null) return;

        Vec3d at = mc.player.getEntityPos();
        long now = System.currentTimeMillis();
        course.addLast(new Sample(now, at.x, at.z));

        while (!course.isEmpty() && now - course.peekFirst().at() > COURSE_WINDOW_MS) course.removeFirst();
    }

    /** Your course over the window, or NaN if you have not gone far enough to have one. */
    private double courseHeading() {
        if (course.size() < 3) return Double.NaN;

        Sample first = course.peekFirst();
        Sample last = course.peekLast();
        double dx = last.x() - first.x();
        double dz = last.z() - first.z();

        if (dx * dx + dz * dz < 400.0) return Double.NaN;

        return MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
    }

    /**
     * Whether that course was flown, not turned through.
     *
     * <p>Without this the test fires on take-off, when you are still coming round
     * and every trail in sight sits at an angle to a heading you do not have yet.
     */
    private boolean courseSteady(double heading) {
        Sample previous = null;

        for (Sample sample : course) {
            if (previous != null) {
                double dx = sample.x() - previous.x();
                double dz = sample.z() - previous.z();

                if (dx * dx + dz * dz >= 4.0) {
                    double leg = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
                    if (Math.abs(MathHelper.wrapDegrees((float) (leg - heading))) > COURSE_STABLE_DEGREES) return false;
                }
            }

            previous = sample;
        }

        return true;
    }

    /**
     * Calls a trail running across your own.
     *
     * <p>Bounded by what the server sends: the loaded band is around twenty chunks
     * wide, so a trail crossed square on puts three or four chunks in front of you
     * and no rule could honestly call it. What this catches is the dense ones and
     * the oblique ones, and the description says so rather than letting the
     * silence be read as an all-clear.
     */
    private void checkCrossing() {
        double heading = courseHeading();
        if (Double.isNaN(heading) || !courseSteady(heading) || mc.player == null) return;

        double[] me = playerIndex();
        double cx = me[0];
        double cz = me[1];
        double reach = loadedRadius.get() * 2.0;
        double reachSq = reach * reach;

        List<ChunkPos> near = new ArrayList<>();
        for (ChunkPos pos : pool) {
            double dx = pos.x + 0.5 - cx;
            double dz = pos.z + 0.5 - cz;
            if (dx * dx + dz * dz <= reachSq) near.add(pos);
        }

        if (near.size() < CROSS_MIN_HITS) return;

        LineFit crossing = fitList(near);
        if (crossing.count < CROSS_MIN_HITS) return;
        if (crossing.spread < CROSS_MIN_SPREAD) return;
        if (crossing.residual > maxResidual.get()) return;

        long key = geometryKey(crossing);
        if (!announcedCrossings.add(key)) return;
        if (onHighwayOrSpawn()) return;

        ChunkPos middle = crossing.inliers.get(crossing.inliers.size() / 2);
        double angle = between(crossing.heading(), heading);

        // One event with two names. Four hits in a line near you is worth saying
        // whatever angle it runs at — it is the third rung under acquired and
        // resumed, the one that fires before there is enough to claim a trail.
        // Crossing is simply the steep case of it.
        boolean across = angle >= crossingAngle.get();
        String message = String.format(Locale.ROOT,
            "%d hits in line %.0f degrees off your course, %s.", crossing.count, angle, describe(middle));
        String name = String.format(Locale.ROOT, "Sighting %.0f %s", crossing.heading(), tag(key));

        Sighting sighting = new Sighting(crossing.originX, crossing.originZ, crossing.dirX, crossing.dirZ,
            name, across ? "Trail crossing" : "Trail sighted", message,
            (middle.x << 4) + 8, (middle.z << 4) + 8,
            System.currentTimeMillis() + (across ? 0L : SIGHTING_HOLD_MS));

        if (across) fireSighting(sighting);
        else pendingSightings.add(sighting);
    }

    /**
     * Identifies a line rather than a moment, so the same crossing is called once.
     *
     * <p>A line is its angle and its distance from the origin, and it has no
     * direction: the fitted vector is normalised before either is taken, or the
     * same trail met from the other side would key differently and be announced
     * twice.
     */
    /**
     * Takes down the sighting marker of a line that has just become a trail.
     *
     * <p>The sighting said "there is something here"; the trail's own start says
     * the same thing with more behind it. Leaving both would put two markers a
     * few chunks apart on one road.
     */
    private void promoteSighting() {
        if (!fitted) return;

        // By proximity, not by key. The key is quantised to ten degrees and eight
        // chunks, so a line acquired two chunks over from where it was sighted
        // hashes differently and the sighting marker would sit there for good,
        // right beside the trail's own start.
        pendingSightings.removeIf(this::isSameAsLive);

        placedSightings.removeIf(sighting -> {
            if (!isSameAsLive(sighting)) return false;
            dropWaypoint(dim(), sighting.name());
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
            return true;
        });
    }

    private boolean isSameAsLive(Sighting sighting) {
        if (Math.abs(sighting.lateral(originX, originZ)) > forkDistance.get() * DRIFT_MULTIPLIER) return false;
        return between(sighting.heading(), heading()) <= switchDegrees.get();
    }

    /** Says a sighting out loud and puts its marker down. */
    private void fireSighting(Sighting sighting) {
        alert(sighting.title(), sighting.message());
        putWaypoint(dim(), sighting.name(), "S", 6,
            sighting.blockX(), sighting.blockZ(), true);
        placedSightings.add(sighting);

        while (placedSightings.size() > 64) placedSightings.remove(0);
    }

    private void flushSightings() {
        if (pendingSightings.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<Sighting> due = new ArrayList<>();

        pendingSightings.removeIf(sighting -> {
            if (now < sighting.dueAt()) return false;
            due.add(sighting);
            return true;
        });

        for (Sighting sighting : due) fireSighting(sighting);
    }

    /** A short stable label for a place, carrying none of its coordinates. */
    private static String tag(long key) {
        return Integer.toHexString((int) (key ^ (key >>> 32))).toUpperCase(Locale.ROOT);
    }

    private static long geometryKey(LineFit fit) {
        double dx = fit.dirX;
        double dz = fit.dirZ;
        if (dx < 0.0 || (dx == 0.0 && dz < 0.0)) {
            dx = -dx;
            dz = -dz;
        }

        double offset = fit.originX * dz - fit.originZ * dx;
        long angle = Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 10.0);
        return angle * 1_000_003L + Math.round(offset / 8.0);
    }

    /**
     * Drops everything that has fallen a long way behind.
     *
     * <p>Memory used to be bounded by count alone, so a trail followed for an hour
     * kept feeding chunks from the far side of the world into the fit — and a
     * second trail met later had to argue with the first one. Distance is the
     * honest bound: what is five hundred chunks behind you is not evidence about
     * where you are.
     *
     * <p>The forgotten chunks leave the seen map as well. Without that, flying
     * back over the same ground would find every chunk already answered and the
     * line could never re-form from it.
     */
    private void forgetDistant() {
        if (mc.player == null) return;

        double[] me = playerIndex();
        double cx = me[0];
        double cz = me[1];
        double limit = forgetDistance.get();
        double limitSq = limit * limit;

        boolean removed = forget(line, cx, cz, limitSq);
        removed |= forget(pool, cx, cz, limitSq);
        forget(archive, cx, cz, limitSq);
        removed |= forget(nearMisses, cx, cz, limitSq);
        removed |= forget(stored, cx, cz, limitSq);

        if (!removed) return;

        if (line.size() < minPoints.get()) {
            // The hits that could have re-acquired the released line went with the
            // rest, so there is no cycle left to guard against.
            awaitingReturn = false;

            if (acquired || aligned) {
                releaseLine(String.format(Locale.ROOT,
                    "everything found has fallen more than %.0f chunks behind", limit));
            }
        }

        fit();
    }

    private boolean forget(List<ChunkPos> list, double cx, double cz, double limitSq) {
        boolean removed = false;
        java.util.Iterator<ChunkPos> it = list.iterator();

        while (it.hasNext()) {
            ChunkPos pos = it.next();
            double dx = pos.x + 0.5 - cx;
            double dz = pos.z + 0.5 - cz;
            if (dx * dx + dz * dz <= limitSq) continue;

            it.remove();
            seen.remove(ChunkPos.toLong(pos.x, pos.z));
            inliers.remove(ChunkPos.toLong(pos.x, pos.z));
            removed = true;
        }

        return removed;
    }

    /**
     * Moves the live line's hits into the archive.
     *
     * <p>They are drawn while the fade runs, then gone, and they never take part
     * in another fit: leaving them in play was how a line that had been let go
     * kept pulling the next one back onto it. The seen map is untouched — this is
     * about the fit, not about memory.
     */
    private void archiveLine() {
        if (line.isEmpty()) return;

        archive.clear();
        archive.addAll(line);
        archivedAt = System.currentTimeMillis();
        line.clear();
    }

    /**
     * Lets a line go without pretending it was lost.
     *
     * <p>Losing a trail is a claim about the trail. This is a claim about the
     * radar: it no longer has the evidence to say anything, so it stops saying it.
     * The geometry is frozen for the fade, everything that made it authoritative
     * is cleared, and the next alignment starts from nothing — which is what makes
     * the next "Trail acquired" mean the new trail's heading rather than the old
     * one's.
     */
    private void releaseLine(String why) {
        releaseLine(why, true);
    }

    private void releaseLine(String why, boolean announce) {
        archiveLine();
        current = null;
        released = fitted;
        releasedAt = System.currentTimeMillis();
        relOriginX = originX;
        relOriginZ = originZ;
        relDirX = dirX;
        relDirZ = dirZ;

        acquired = false;
        aligned = false;
        announcedHeading = Double.NaN;
        offLine = false;
        offLineAt = 0L;
        driftSince = 0L;

        String message = "Line released: " + why + ". Watching for a new alignment.";
        info(message);
        writeEvent("Line released", message);

        // Only what happened on its own is worth a notification. A release you
        // asked for is already known to the one person the feed would tell.
        if (announce) {
            HuntFeed.get().publish(HuntFeed.Type.OLD_CHUNK, message,
                hideCoordinates.get() || mc.player == null ? null : mc.player.getBlockPos());
        }
    }

    /**
     * Picks a known trail back up on connecting, without flying it again.
     *
     * <p>A daily restart used to mean two fresh clusters before the line came
     * back, and a "Trail acquired" for a road already in the file. If you come
     * back standing on a trail that is remembered — on its segment or on the line
     * it projects — its own chunks rebuild the live line and it is simply picked
     * up, quietly: one line in chat, nothing on Discord.
     */
    private void resumeFromMemory() {
        if (acquired || mc.player == null) return;

        double[] me = playerIndex();
        double cx = me[0];
        double cz = me[1];
        double reach = forkDistance.get() * DRIFT_MULTIPLIER;

        for (TrailStore.Trail trail : store.forDimension(dim())) {
            if (trail.chunks.size() < minPoints.get()) continue;
            if (Math.abs(trail.lateral(cx, cz)) > reach) continue;

            // Being on the line is not being on the trail. Its projection runs for
            // thousands of chunks, and coming out of a portal that far along it
            // would rebuild a line whose nearest hit is three thousand chunks
            // behind — and lose it on the same second.
            double alongTrail = trail.along(cx, cz);
            if (alongTrail < trail.spanMin - RESUME_MARGIN || alongTrail > trail.spanMax + RESUME_MARGIN) continue;

            line.clear();
            for (int[] chunk : trail.chunks) line.add(new ChunkPos(chunk[0], chunk[1]));

            originX = trail.originX;
            originZ = trail.originZ;
            dirX = trail.dirX;
            dirZ = trail.dirZ;
            current = trail;

            fit();
            if (!aligned) {
                line.clear();
                current = null;
                fitted = false;
                continue;
            }

            acquired = true;
            released = false;
            announcedHeading = spread >= headingSpread.get() ? heading() : Double.NaN;
            info("Trail resumed from memory: heading %.1f degrees, %d hits.", heading(), line.size());
            return;
        }
    }

    /**
     * Catches the trail turning a corner.
     *
     * <p>The global heading is frozen once the hits span enough ground, which is
     * what stops a settling fit from crying turn. That freeze also means a real
     * corner, eighty chunks in, barely moves the average and goes unnoticed. So
     * the last thirty hits are fitted on their own: if they run at an angle to the
     * line and hold to it, the trail turned, and the line restarts from them. The
     * part before the corner goes to the archive rather than dragging the new
     * heading back towards the old one.
     */
    private void checkElbow() {
        if (!acquired || !aligned || line.size() < ELBOW_WINDOW + 5) return;

        List<ChunkPos> tail = new ArrayList<>(line.subList(line.size() - ELBOW_WINDOW, line.size()));
        LineFit corner = fitList(tail);

        if (corner.count < ELBOW_WINDOW / 2) return;
        if (corner.residual > maxResidual.get()) return;
        if (between(corner.heading(), heading()) < headingChange.get()) return;

        List<ChunkPos> older = new ArrayList<>(line.subList(0, line.size() - ELBOW_WINDOW));
        archive.clear();
        archive.addAll(older);
        archivedAt = System.currentTimeMillis();

        line.clear();
        line.addAll(tail);

        originX = corner.originX;
        originZ = corner.originZ;
        dirX = corner.dirX;
        dirZ = corner.dirZ;
        orient(line);

        double turn = between(corner.heading(), announcedHeading);
        announcedHeading = heading();

        alert("Trail turning", String.format(Locale.ROOT,
            "The last %d hits run %.0f degrees off it; following %.1f now.",
            ELBOW_WINDOW, turn, heading()));

        // A new leg, chained to the old one, rather than the old entry rewritten.
        // Rewriting it would have moved its start waypoint onto the corner and
        // thrown away every chunk before it — the half of the trail already
        // walked, gone because it turned.
        String previous = current == null ? null : current.id;
        current = store.create(dim());
        current.previous = previous;

        updateTrail(true);
    }

    /** Whether you are pointed away from the line, course first and yaw as a fallback. */
    private boolean headingAwayFromLine() {
        if (mc.player == null || !fitted) return false;

        double course = courseHeading();
        if (Double.isNaN(course)) course = mc.player.getYaw();

        return between(course, heading()) > DRIFT_ANGLE_DEGREES;
    }

    /**
     * Puts the radar to sleep after a jump nothing could have flown.
     *
     * <p>Death, the End, a portal to the other side of the map: a hundred chunks
     * in one tick is not travel. Everything it was following is dropped without a
     * word — no fading line, no "released", no memory entry — because none of it
     * is about where you now are. The next alignment starts from nothing.
     */
    private void checkTeleport() {
        ChunkPos now = mc.player.getChunkPos();

        if (lastPlayerChunk != null) {
            double dx = now.x - lastPlayerChunk.x;
            double dz = now.z - lastPlayerChunk.z;

            if (dx * dx + dz * dz > 100.0 * 100.0) {
                line.clear();
                pool.clear();
                archive.clear();
                inliers.clear();
                arrivals.clear();
                recent.clear();
                current = null;
                acquired = false;
                aligned = false;
                fitted = false;
                released = false;
                awaitingReturn = false;
                offLine = false;
                offLineAt = 0L;
                driftSince = 0L;
                announcedHeading = Double.NaN;
            }
        }

        lastPlayerChunk = now;
    }

    /**
     * Places where old chunks say nothing.
     *
     * <p>The axes, the diagonals and the ground around the origin are old because
     * everybody has been there. A trail is worth a message because it is somebody
     * going somewhere alone; a highway is the opposite of that.
     */
    private boolean onHighwayOrSpawn() {
        if (mc.player != null && spawnRadius.get() > 0) {
            Vec3d at = mc.player.getEntityPos();
            if (Math.hypot(at.x, at.z) <= spawnRadius.get()) return true;
        }

        if (!fitted || highwayBand.get() <= 0) return false;

        // Parallel to a highway, within a band of it. Both halves are needed: a
        // line crossing an axis at speed is not a highway, and one running beside
        // it far away is a trail of its own.
        double heading = heading();
        boolean parallel = between(heading, 0.0) <= 2.0 || between(heading, 45.0) <= 2.0
            || between(heading, 90.0) <= 2.0 || between(heading, 135.0) <= 2.0;
        if (!parallel) return false;

        double band = highwayBand.get();
        double diagonal = Math.sqrt(2.0);

        return Math.abs(originX) <= band
            || Math.abs(originZ) <= band
            || Math.abs(originZ - originX) / diagonal <= band
            || Math.abs(originZ + originX) / diagonal <= band;
    }

    /**
     * Whether you are simply inside old ground rather than following a line
     * through new ground.
     *
     * <p>Measured over the last thirty chunks of travel rather than the last
     * thirty chunks received, because the question is about the ground you are
     * crossing and not about how fast it arrives.
     */
    private boolean inOldRegion() {
        if (regionDensity.get() <= 0 || mc.player == null || recent.size() < 8) return false;

        Vec3d at = mc.player.getEntityPos();
        double cx = at.x / 16.0;
        double cz = at.z / 16.0;

        while (!recent.isEmpty()) {
            double[] first = recent.peekFirst();
            double dx = first[0] - cx;
            double dz = first[1] - cz;
            if (dx * dx + dz * dz <= 30.0 * 30.0) break;
            recent.removeFirst();
        }

        if (recent.size() < 8) return false;

        int hits = 0;
        for (double[] sample : recent) {
            if (sample[2] > 0.5) hits++;
        }

        return hits * 100 >= recent.size() * regionDensity.get();
    }

    /**
     * Forgives the ground covered while you were in another dimension.
     *
     * <p>A portal jump is not a trail lost. Coming back onto the same line, the
     * distance travelled along it since the last hit would count every block of
     * the Nether trip and announce a loss on arrival, so the baseline is moved to
     * where you came out — and only then, and silently, because nothing about the
     * trail has changed.
     */
    private void rebaseAfterPortal() {
        if (!acquired || !fitted || mc.player == null) return;

        Vec3d at = mc.player.getEntityPos();
        if (Math.abs(playerLateral()) > forkDistance.get() * DRIFT_MULTIPLIER) return;

        spanEnd = playerAlong();
    }

    /**
     * Says when you have left the corridor sideways.
     *
     * <p>"Lost" is measured along the line, so turning ninety degrees off it never
     * trips anything: the distance along simply stops growing while you fly away.
     * That is the one way to leave the trail in silence, and the alerts exist so
     * you can look somewhere else.
     *
     * <p>Both directions have to hold for the same five seconds. A single
     * threshold with an instant trigger would chatter every time you weave across
     * it, and marking a chunk off the trail means deliberately stepping outside
     * for a moment.
     */
    private void checkDrift() {
        double threshold = forkDistance.get() * DRIFT_MULTIPLIER;

        // The guard is about the released line, so it is measured against the
        // frozen geometry and keeps working after the live fit is gone — which is
        // the usual case, since the hits behind it are what got forgotten.
        if (awaitingReturn && mc.player != null) {
            Vec3d back = mc.player.getEntityPos();
            if (Math.abs(playerRelLateral()) < threshold) awaitingReturn = false;
        }

        if (!acquired || !fitted || mc.player == null) {
            driftSince = 0L;
            return;
        }

        Vec3d where = mc.player.getEntityPos();
        double off = Math.abs(playerLateral());
        // Sideways is not enough: a sweep ten chunks off the line while still
        // pointing along it is how you look for the next cluster, not how you give
        // up. Leaving means both away from it and aimed away from it.
        boolean out = off >= threshold && headingAwayFromLine();
        long now = System.currentTimeMillis();


        if (out == offLine) {
            driftSince = 0L;

            // Sixty seconds outside the corridor and the line is no longer about
            // where you are. Let go without waiting for the distance rule.
            if (offLine && offLineAt != 0L && now - offLineAt >= OFF_LINE_RELEASE_MS) {
                awaitingReturn = true;
                releaseLine("a minute off the line");
            }

            return;
        }

        if (driftSince == 0L) {
            driftSince = now;
            return;
        }

        if (now - driftSince < DRIFT_HOLD_MS) return;

        driftSince = 0L;
        offLine = out;
        offLineAt = out ? now : 0L;

        // Chat and the feed, never the webhook: this is flying information, not a
        // find. Nobody's phone should buzz because you drifted.
        // Coming back can mean two things, and saying "back on the line" while you
        // are still twenty chunks from it is not one of them.
        String message = out
            ? String.format(Locale.ROOT, "Off the line: %.1f chunks sideways.", off)
            : off >= threshold
                ? String.format(Locale.ROOT, "Course back along the line, still %.1f chunks off it.", off)
                : String.format(Locale.ROOT, "Back on the line: %.1f chunks sideways.", off);

        info(message);
        writeEvent(out ? "Off the line" : "Back on the line", message);
        HuntFeed.get().publish(HuntFeed.Type.OLD_CHUNK, message,
            hideCoordinates.get() ? null : mc.player.getBlockPos());
    }

    /**
     * One line in chat, one in the feed, and a webhook if the cooldown allows.
     *
     * <p>Four events reach this: acquired, lost, turning, zone. A fork stays in
     * chat. The cooldown covers the webhook alone.
     */
    private void alert(String title, String message) {
        alert(title, message, true);
    }

    /**
     * @param toDiscord false keeps it to chat and the feed, for the events that
     *                  are information rather than news
     */
    private void alert(String title, String message, boolean toDiscord) {
        info("%s: %s", title, message);
        writeEvent(title, message);

        // The feed is read by HUDs that can print a position. With coordinates
        // hidden the entry carries none, so there is nothing for them to print.
        BlockPos where = mc.player == null || hideCoordinates.get() ? null : mc.player.getBlockPos();
        HuntFeed.get().publish(HuntFeed.Type.OLD_CHUNK, title + " · " + message, where);

        if (!toDiscord || !webhook.get() || webhookLink.get().trim().isEmpty()) return;

        // Per type, and nothing is dropped. A single cooldown across all events
        // meant a loss twenty seconds behind an acquisition was swallowed whole;
        // now each kind waits its own turn and the newest of a kind held back is
        // sent when that turn comes.
        long now = System.currentTimeMillis();
        Long last = lastSentByType.get(title);

        if (last != null && now - last < alertCooldown.get() * 1000L) {
            heldByType.put(title, message);
            return;
        }

        lastSentByType.put(title, now);
        send(title, message);
    }

    private void flushHeldAlerts() {
        if (heldByType.isEmpty()) return;

        long now = System.currentTimeMillis();
        long cooldown = alertCooldown.get() * 1000L;

        for (Map.Entry<String, String> entry : new ArrayList<>(heldByType.entrySet())) {
            Long last = lastSentByType.get(entry.getKey());
            if (last != null && now - last < cooldown) continue;

            heldByType.remove(entry.getKey());
            lastSentByType.put(entry.getKey(), now);
            send(entry.getKey(), entry.getValue());
        }
    }

    private void send(String title, String message) {
        String url = webhookLink.get().trim();
        if (url.isEmpty()) return;

        String ping = discordId.get().trim().matches("\\d+") ? discordId.get().trim() : null;
        String player = mc.player == null ? "unknown" : mc.player.getGameProfile().name();

        new Thread(() -> com.hunterbuddy.modules.regear.util.Utils.sendWebhook(url, title, message, ping, player),
            "ChunkRadar-Webhook").start();
    }

    /**
     * How a place is named out loud.
     *
     * <p>With hide-coordinates on this is a distance and a compass point, which is
     * everything you need in flight and nothing anyone else can fly to. The real
     * numbers go to the log file.
     */
    private String describe(ChunkPos pos) {
        if (!hideCoordinates.get()) return (pos.x << 4) + ", " + (pos.z << 4);
        if (mc.player == null) return "somewhere";

        Vec3d at = mc.player.getEntityPos();
        double dx = (pos.x << 4) + 8 - at.x;
        double dz = (pos.z << 4) + 8 - at.z;

        return String.format(Locale.ROOT, "%.0f blocks %s", Math.hypot(dx, dz), compass(dx, dz));
    }

    /** Minecraft compass: -z is north, +x is east. */
    private static String compass(double dx, double dz) {
        String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double angle = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
        return points[((int) Math.round(angle / 45.0)) & 7];
    }

    // Waypoints and memory

    /**
     * The set the waypoints of a dimension live in.
     *
     * <p>Resolved through {@code WaypointAPI} rather than through whatever world
     * the minimap happens to be showing: taken from the displayed world, a Nether
     * waypoint lands in the overworld list at eight times the wrong coordinates.
     */
    private MinimapWorld waypointWorld() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return null;

        // The overworld, which is the only place this module marks anything, and
        // the stored dimension string is carried so the file stays readable the
        // day that changes.
        MinimapWorld world = WaypointAPI.getMinimapWorld(World.OVERWORLD);
        if (world == null) world = session.getWorldManager().getCurrentWorld();
        return world;
    }

    private WaypointSet waypointSet(String dimension) {
        MinimapWorld world = waypointWorld();
        if (world == null) return null;

        if (!temporaryWaypoints.get()) pendingWaypointSaves.add(world);
        return world.getCurrentWaypointSet();
    }

    private void putWaypoint(String dimension, String name, String symbol, int color, int blockX, int blockZ,
                             boolean temporary) {
        if (!addWaypoints.get()) return;

        WaypointSet set = waypointSet(dimension);
        if (set == null) return;

        dropWaypoint(dimension, name);
        set.add(new Waypoint(blockX, 70, blockZ, name, symbol, color, 0, temporary));
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
    }

    /**
     * Every set of the world, not just the one on display.
     *
     * <p>Xaero lets you switch waypoint sets from a dropdown, and looking only at
     * the current one meant that switching made every marker the radar had placed
     * read as deleted — the sweep would then have wiped the whole memory ten
     * seconds later, for a click that moved nothing.
     */
    private void dropWaypoint(String dimension, String name) {
        if (name == null) return;

        MinimapWorld world = waypointWorld();
        if (world == null) return;

        for (WaypointSet set : world.getIterableWaypointSets()) {
            List<Waypoint> doomed = new ArrayList<>();
            for (Waypoint existing : set.getWaypoints()) {
                if (name.equals(existing.getName())) doomed.add(existing);
            }

            for (Waypoint existing : doomed) set.remove(existing);
        }
    }

    private boolean waypointExists(String dimension, String name) {
        if (name == null) return false;

        MinimapWorld world = waypointWorld();

        // No world means Xaero has not finished loading. Answering "gone" there
        // would let a sweep run seconds after joining wipe everything.
        if (world == null) return true;

        for (WaypointSet set : world.getIterableWaypointSets()) {
            for (Waypoint existing : set.getWaypoints()) {
                if (name.equals(existing.getName())) return true;
            }
        }

        return false;
    }

    /**
     * Writes permanent waypoints to disk, on a timer.
     *
     * <p>A permanent waypoint only lives in memory until its world is saved, so
     * without this it disappears on disconnect exactly like a temporary one. On a
     * timer because saving on every waypoint froze the game.
     */
    private void flushWaypointSaves() {
        if (pendingWaypointSaves.isEmpty()) return;

        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            pendingWaypointSaves.clear();
            return;
        }

        for (MinimapWorld world : pendingWaypointSaves) {
            try {
                session.getWorldManagerIO().saveWorld(world);
            } catch (Exception e) {
                HunterBuddyAddon.LOG.error("ChunkRadar: could not save minimap waypoints", e);
            }
        }

        pendingWaypointSaves.clear();
    }

    /**
     * Takes over the remembered trail this line turns out to be, or starts a new one.
     *
     * <p>Same test as the one that refuses to re-acquire a released line: near the
     * same ground and running the same way is the same trail. Without it, every
     * session would add another entry for the road it has already followed twice.
     */
    private boolean adoptOrCreateTrail() {
        String dimension = dim();
        TrailStore.Trail found = null;

        for (TrailStore.Trail trail : store.forDimension(dimension)) {
            if (Math.abs(trail.lateral(originX, originZ)) > forkDistance.get() * DRIFT_MULTIPLIER) continue;
            if (between(trail.heading, heading()) > switchDegrees.get()) continue;
            found = trail;
            break;
        }

        current = found != null ? found : store.create(dimension);
        updateTrail(true);
        return found != null;
    }

    /**
     * Announces an acquisition, and knows the difference between finding a trail
     * and coming back to one.
     *
     * <p>A three minute detour used to end in "Trail acquired" on Discord for a
     * trail already followed for two hours. Picking a known one back up is worth a
     * line in chat; it is only worth a phone buzzing if that trail has been quiet
     * for a while, which is what resume-quiet measures.
     */
    private void announceAcquisition(int count, double spreadChunks, boolean headingSolid) {
        // Checked before anything is written down: a highway is not a find, so it
        // earns no alert, no waypoint and no entry in the file either.
        if (onHighwayOrSpawn()) {
            info("Line on a highway or near spawn, staying quiet.");
            return;
        }

        boolean resumed = adoptOrCreateTrail();
        promoteSighting();
        absorbHitMarks();

        String message = String.format(Locale.ROOT, "%d hits over %.0f chunks, heading %.1f degrees%s.",
            count, spreadChunks, heading(), headingSolid ? "" : " (approx.)");

        // With coordinates showing, the one message worth carrying them is this
        // one, and it has to carry them all the way to Discord: a notification
        // saying a trail was found and not where is a notification you have to
        // come back to the game to act on.
        if (!hideCoordinates.get() && current != null && mc.player != null) {
            BlockPos you = mc.player.getBlockPos();
            message += String.format(Locale.ROOT, " First %d, %d. Latest %d, %d. You %d, %d.",
                current.startX, current.startZ, current.endX, current.endZ, you.getX(), you.getZ());
        }

        long now = System.currentTimeMillis();
        boolean quiet = resumed && current != null
            && now - current.lastNotified < resumeQuiet.get() * 60_000L;

        alert(resumed ? "Trail resumed" : "Trail acquired", message, !quiet);
        if (current != null && !quiet) current.lastNotified = now;
    }

    /** Copies the live line into its stored entry, and moves the far waypoint with it. */
    private void updateTrail(boolean placeStart) {
        if (current == null || !fitted) return;

        // Nothing new to write. Called once per acquisition and once per extending
        // hit, and the two overlap on the tick a line is picked up.
        if (!placeStart && current.hits == line.size()) return;

        current.originX = originX;
        current.originZ = originZ;
        current.dirX = dirX;
        current.dirZ = dirZ;
        current.heading = heading();
        current.hits = line.size();
        current.lastSeen = System.currentTimeMillis();
        if (current.firstSeen == 0L) current.firstSeen = current.lastSeen;

        ChunkPos first = null;
        ChunkPos last = null;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        current.chunks.clear();
        for (ChunkPos pos : line) {
            current.chunks.add(new int[]{pos.x, pos.z});
            double at = along(pos.x, pos.z);
            if (at < min) {
                min = at;
                first = pos;
            }
            if (at > max) {
                max = at;
                last = pos;
            }
        }

        if (first == null || last == null) return;

        // The name carries the heading, and the heading moves as the fit settles.
        // Without dropping the old one first, every degree of correction left
        // another orphan on the map under a name nothing would ever look for again.
        int cap = (int) Math.round(current.heading);
        String startName = "Trail " + cap + " " + current.id;
        String endName = startName + " end";

        if (placeStart || current.startWaypoint == null || !startName.equals(current.startWaypoint)) {
            if (current.startWaypoint != null && !startName.equals(current.startWaypoint)) {
                dropWaypoint(current.dimension, current.startWaypoint);
            }

            current.startWaypoint = startName;
            current.startX = (first.x << 4) + 8;
            current.startZ = (first.z << 4) + 8;
            putWaypoint(current.dimension, startName, "T", 4, current.startX, current.startZ,
                temporaryWaypoints.get());
        }

        // The far end moves as the trail grows; the near end never does.
        if (current.endWaypoint != null && !endName.equals(current.endWaypoint)) {
            dropWaypoint(current.dimension, current.endWaypoint);
        }

        current.endWaypoint = endName;
        current.endX = (last.x << 4) + 8;
        current.endZ = (last.z << 4) + 8;
        putWaypoint(current.dimension, endName, "E", 4, current.endX, current.endZ, temporaryWaypoints.get());

        // Taken from the two ends themselves, never from the chunk list.
        //
        // That list is windowed to memory, so on a trail longer than the window
        // its earliest entry is not the trail's beginning — it is simply the
        // oldest chunk still held. Measured on a 875 chunk trail with a 500 chunk
        // window, the stored extent began six kilometres past its own start
        // waypoint, which would have made the memory undrawable over the first
        // third of it and unresumable there. The two ends do not move: the start
        // is set once at acquisition, the end follows the far edge.
        double startAlong = along((current.startX - 8) / 16.0, (current.startZ - 8) / 16.0);
        double endAlong = along((current.endX - 8) / 16.0, (current.endZ - 8) / 16.0);
        current.spanMin = Math.min(startAlong, endAlong);
        current.spanMax = Math.max(startAlong, endAlong);

        store.save();
    }

    /**
     * Drops the memories whose start waypoint the player has deleted.
     *
     * <p>Deleting the waypoint is the gesture for "I am done with this one", and
     * it is the only one that needs no command. Keyed on the stored name, which
     * carries the trail id, so removing one trail's marker cannot take another
     * trail with it.
     */
    private void sweepWaypoints() {
        long now = System.currentTimeMillis();
        if (now - lastSweepMs < WAYPOINT_SWEEP_MS) return;
        lastSweepMs = now;

        flushWaypointSaves();

        if (!addWaypoints.get()) return;

        // "Delete the start waypoint and the trail is forgotten" is a gesture, and
        // a gesture needs a marker that survives long enough to be deleted on
        // purpose. A temporary one is gone at every disconnect, so every
        // reconnection read as the player having forgotten every trail — which is
        // how a line followed for three hours came back as a file with one entry
        // and twenty hits, and was announced as newly acquired instead of resumed.
        if (temporaryWaypoints.get()) {
            replaceTemporaryWaypoints();
            return;
        }

        // Run here rather than straight after the file is read: Xaero has usually
        // not finished loading its own waypoints at that point, and a cleanup that
        // sees nothing would be a cleanup that removes nothing or, worse, believes
        // everything is stale.
        if (cleanupPending && waypointWorld() != null) {
            cleanupPending = false;
            dropRenamedWaypoints();
        }

        String dimension = dim();
        List<TrailStore.Trail> marked = new ArrayList<>();
        List<TrailStore.Trail> doomed = new ArrayList<>();

        for (TrailStore.Trail trail : store.forDimension(dimension)) {
            if (trail.startWaypoint == null) continue;
            marked.add(trail);
            if (!waypointExists(dimension, trail.startWaypoint)) doomed.add(trail);
        }

        // Deleting one marker is a gesture; every marker vanishing at once is an
        // accident somewhere else. Searching all the sets should already have made
        // that impossible, and this is what stands behind it if it is not.
        // One or a hundred: the memory is never emptied wholesale. With a single
        // trail in the file the old form of this guard protected nothing, which is
        // precisely the case it had to cover.
        if (!marked.isEmpty() && doomed.size() == marked.size()) {
            HunterBuddyAddon.LOG.warn("ChunkRadar: every trail waypoint went missing at once, keeping the memory");
            return;
        }

        boolean changed = false;
        for (TrailStore.Trail trail : doomed) {
            dropWaypoint(dimension, trail.endWaypoint);
            store.remove(trail);
            if (trail == current) current = null;
            changed = true;
        }

        if (changed) {
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
            store.save();
        }
    }

    /**
     * Removes the markers left behind by earlier names.
     *
     * <p>A trail's waypoints are named after its heading, and a session that ended
     * mid-correction can leave {@code Trail 291 <id>} on the map while the file
     * now says 293. The id is what identifies the trail, so any marker carrying a
     * known id under a name the entry no longer claims is an orphan. Ids that
     * belong to no entry are left alone: they are somebody else's waypoints, or
     * ours from a memory that has since been forgotten on purpose.
     */
    private void dropRenamedWaypoints() {
        MinimapWorld world = waypointWorld();
        if (world == null) return;

        Map<String, Set<String>> claimed = new LinkedHashMap<>();
        for (TrailStore.Trail trail : store.all()) {
            Set<String> names = new HashSet<>();
            if (trail.startWaypoint != null) names.add(trail.startWaypoint);
            if (trail.endWaypoint != null) names.add(trail.endWaypoint);
            claimed.put(trail.id, names);
        }

        List<String> doomed = new ArrayList<>();

        for (WaypointSet set : world.getIterableWaypointSets()) {
            for (Waypoint existing : set.getWaypoints()) {
                String name = existing.getName();
                if (name == null || !name.startsWith("Trail ")) continue;

                String[] parts = name.split(" ");
                if (parts.length < 3) continue;

                Set<String> names = claimed.get(parts[2]);
                if (names != null && !names.contains(name)) doomed.add(name);
            }
        }

        for (String name : doomed) dropWaypoint(dim(), name);

        if (!doomed.isEmpty()) {
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
            HunterBuddyAddon.LOG.info("ChunkRadar: removed {} renamed trail waypoint(s)", doomed.size());
        }
    }

    /**
     * Puts back the temporary markers of trails still in the file.
     *
     * <p>Xaero drops temporary waypoints at disconnect, so a remembered trail
     * comes back with an entry and no marker on the map: the memory works and is
     * invisible, which reads as the memory not working. Nothing is forgotten
     * here — the marks are simply drawn again where the file says they were.
     */
    private void replaceTemporaryWaypoints() {
        String dimension = dim();

        for (TrailStore.Trail trail : store.forDimension(dimension)) {
            if (trail.startWaypoint == null) continue;
            if (waypointExists(dimension, trail.startWaypoint)) continue;

            putWaypoint(dimension, trail.startWaypoint, "T", 4, trail.startX, trail.startZ, true);
            if (trail.endWaypoint != null) {
                putWaypoint(dimension, trail.endWaypoint, "E", 4, trail.endX, trail.endZ, true);
            }
        }
    }

    /** Read by the {@code .trails} command. */
    public TrailStore trails() {
        return store;
    }

    /**
     * Remembered trails other than the one being followed, for the map to draw.
     *
     * <p>The live one is left out because the live rendering already covers it,
     * in the colour that says it is being claimed rather than recalled.
     */
    /** What the map draws as a marker: where, and which of the three it is. */
    public record Marker(int blockX, int blockZ, Marker.Kind kind) {
        public enum Kind {
            TRAIL,
            SIGHTING,
            ZONE,
            HIT
        }
    }

    /**
     * Everything the module has put on the minimap, for the radar square to draw
     * the same. Read-only: the HUD never touches a list of the module's.
     */
    public List<Marker> markers() {
        List<Marker> out = new ArrayList<>();

        for (TrailStore.Trail trail : store.forDimension(dim())) {
            if (trail.startWaypoint != null) out.add(new Marker(trail.startX, trail.startZ, Marker.Kind.TRAIL));
            if (trail.endWaypoint != null) out.add(new Marker(trail.endX, trail.endZ, Marker.Kind.TRAIL));
        }

        for (Sighting sighting : placedSightings) {
            out.add(new Marker(sighting.blockX(), sighting.blockZ(), Marker.Kind.SIGHTING));
        }

        for (ChunkPos zone : zoneMarks) {
            out.add(new Marker((zone.x << 4) + 8, (zone.z << 4) + 8, Marker.Kind.ZONE));
        }

        for (ChunkPos hit : hitMarks) {
            out.add(new Marker((hit.x << 4) + 8, (hit.z << 4) + 8, Marker.Kind.HIT));
        }

        return out;
    }

    /** Half-width of the corridor the line is followed in, in chunks. */
    public double corridorChunks() {
        return forkDistance.get();
    }

    /** How far out the server is sending you chunks, in chunks. */
    public int reachChunks() {
        return loadedRadius.get();
    }

    /** True while a highway, spawn or an old region is keeping the module quiet. */
    public boolean silenced() {
        return denseNow || onHighwayOrSpawn();
    }

    public boolean isAcquired() {
        return acquired;
    }

    /** Your own course over the last ten seconds, or NaN before you have one. */
    public double smoothedCourse() {
        return courseHeading();
    }

    // One set of colours to tune. The map reads these unless it has been told to
    // diverge, so a trail is the same colour wherever you look at it.

    public SettingColor trailColour() {
        return trailColor.get();
    }

    public SettingColor looseColour() {
        return looseColor.get();
    }

    public SettingColor nearMissColour() {
        return redColor.get();
    }

    public SettingColor storedColour() {
        return storedColor.get();
    }

    public SettingColor memoryColour() {
        return memoryColor.get();
    }

    public SettingColor lineColour() {
        return lineColor.get();
    }

    /**
     * The line that has been let go, while it fades.
     *
     * <p>{@code {originX, originZ, dirX, dirZ, fade}} in chunk coordinates, or
     * null. The map fades it out exactly as the world does, so the two never
     * disagree about what is still being followed.
     */
    public double[] releasedLine() {
        if (!released) return null;

        double age = System.currentTimeMillis() - releasedAt;
        if (age >= RELEASE_FADE_MS) return null;

        return new double[]{relOriginX, relOriginZ, relDirX, relDirZ, 1.0 - age / RELEASE_FADE_MS};
    }

    /** Guide geometry in Nether chunk coordinates, or null when there is nothing to follow. */
    public double[] guideLine() {
        return guideGeometry();
    }

    /** How far a memory is projected past its ends, in chunks, for the map to match the world. */
    public int memoryProjectionChunks() {
        return renderMemory.get() ? memoryProjection.get() : 0;
    }

    public List<TrailStore.Trail> memories() {
        List<TrailStore.Trail> out = new ArrayList<>();

        for (TrailStore.Trail trail : store.forDimension(dim())) {
            if (trail == current) continue;
            if (coversLive(trail) || coversReleased(trail)) continue;
            out.add(trail);
        }

        return out;
    }

    /**
     * Whether a remembered trail is the same geometry as the line being followed.
     *
     * <p>One line per road, and the live one wins: two lines a chunk apart in
     * different colours read as a fork that is not there. The plates go with the
     * line, so a covered memory is not drawn at all rather than drawn underneath.
     */
    private boolean coversLive(TrailStore.Trail trail) {
        if (!hasLine()) return false;
        if (Math.abs(trail.lateral(originX, originZ)) > forkDistance.get() * DRIFT_MULTIPLIER) return false;
        return between(trail.heading, heading()) <= switchDegrees.get();
    }

    private boolean coversReleased(TrailStore.Trail trail) {
        if (!released) return false;
        if (Math.abs(trail.lateral(relOriginX, relOriginZ)) > forkDistance.get() * DRIFT_MULTIPLIER) return false;
        return between(trail.heading, relHeading()) <= switchDegrees.get();
    }

    /** True when a memory already draws the ground the released line is fading over. */
    private boolean releasedIsRemembered() {
        if (!released) return false;

        for (TrailStore.Trail trail : store.forDimension(dim())) {
            if (coversReleased(trail)) return true;
        }

        return false;
    }

    public void forgetTrail(TrailStore.Trail trail) {
        String dimension = trail.dimension;
        dropWaypoint(dimension, trail.startWaypoint);
        dropWaypoint(dimension, trail.endWaypoint);
        store.remove(trail);
        if (trail == current) current = null;
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
        store.save();
    }

    // Render

    /** Where the chunk plates lie: under you, or pinned to a height. */
    private double plateHeight() {
        if (plateHeightMode.get() == PlateHeight.FIXED) return plateY.get();
        return (mc.player == null ? 0.0 : mc.player.getEntityPos().y) + plateOffset.get();
    }

    /**
     * Where the line runs, always measured from you.
     *
     * <p>Never pinned to the ground: its far end is a couple of thousand blocks
     * away, and at that range anything at ground level spends its time behind the
     * terrain.
     */
    private double lineHeight() {
        return (mc.player == null ? 0.0 : mc.player.getEntityPos().y) + lineOffset.get();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || mc.player == null || mc.world == null) return;

        if (!live()) {
            drawNetherGuide(event);
            return;
        }

        drawMemories(event);

        // The plates and the line live at their own heights: the map is read by
        // glancing down, the line by looking ahead, and a single plane made one of
        // the two get in the way of the other.
        double y = plateHeight();

        if (renderStored.get()) {
            for (ChunkPos pos : stored) plate(event, pos, y, storedColor.get());
        }

        if (renderRed.get()) {
            for (ChunkPos pos : nearMisses) plate(event, pos, y, redColor.get());
        }

        // Owned, waiting, and let go: three weights for three meanings.
        long archiveAge = System.currentTimeMillis() - archivedAt;
        if (!archive.isEmpty() && archiveAge < RELEASE_FADE_MS) {
            SettingColor base = trailColor.get();
            int fade = (int) (base.a * 0.5 * (1.0 - (double) archiveAge / RELEASE_FADE_MS));
            SettingColor pale = new SettingColor(base.r, base.g, base.b, Math.max(0, fade));
            for (ChunkPos pos : archive) plate(event, pos, y, pale);
        } else if (!archive.isEmpty()) {
            archive.clear();
        }

        for (ChunkPos pos : pool) plate(event, pos, y, looseColor.get());
        for (ChunkPos pos : line) plate(event, pos, y, trailColor.get());

        // A released line keeps being drawn for half a minute, fading, and frozen
        // where it stood. It says "this is what I was following" while it goes,
        // which is the difference between a radar that let go and one that broke.
        if (released) {
            long age = System.currentTimeMillis() - releasedAt;
            if (age >= RELEASE_FADE_MS) {
                released = false;
                return;
            }

            // A memory already covers this ground, and it is the permanent one of
            // the two. Drawing both would put two lines a chunk apart on the same
            // road, which reads as a fork.
            if (releasedIsRemembered()) return;

            SettingColor pale = lineColor.get();
            int fade = (int) (pale.a * 0.4 * (1.0 - (double) age / RELEASE_FADE_MS));
            Vec3d at = mc.player.getEntityPos();
            double here = playerAlongReleased();
            Vec3d from = relPoint(here - tail.get(), lineHeight());
            Vec3d to = relPoint(here + projection.get(), lineHeight());

            event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z,
                new Color(pale.r, pale.g, pale.b, Math.max(0, fade / 3)),
                new Color(pale.r, pale.g, pale.b, Math.max(0, fade)));
            return;
        }

        if (!fitted) return;

        Vec3d at = mc.player.getEntityPos();
        double here = playerAlong();
        SettingColor color = lineColor.get();
        int alpha = aligned ? color.a : color.a / 3;

        Vec3d from = point(here + tailEnd(), lineHeight());
        Vec3d to = point(here + headEnd(), lineHeight());
        event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z,
            new Color(color.r, color.g, color.b, Math.max(0, alpha / 3)),
            new Color(color.r, color.g, color.b, alpha));
    }

    /**
     * Which way along the line you are looking.
     *
     * <p>The line keeps the trail's own direction, oldest hit to newest, so that a
     * turn-round is not mistaken for the trail turning. That is right for the
     * logic and wrong for the picture: flown the other way, the whole projection
     * ended up behind you. Only the drawing flips.
     */
    private double facing() {
        if (mc.player == null) return 1.0;

        double yaw = Math.toRadians(mc.player.getYaw());
        double dot = dirX * -Math.sin(yaw) + dirZ * Math.cos(yaw);
        return dot >= 0.0 ? 1.0 : -1.0;
    }

    private double headEnd() {
        return facing() * projection.get();
    }

    private double tailEnd() {
        return -facing() * tail.get();
    }

    /**
     * Trails from earlier sessions: their chunks, and the segment they actually
     * covered.
     *
     * <p>No projection and no readout. A memory says where a trail was, not where
     * it goes — the line drawn ahead of you is a claim, and a claim needs evidence
     * arriving now.
     */
    private void drawMemories(Render3DEvent event) {
        if (!renderMemory.get()) return;

        SettingColor colour = memoryColor.get();
        double y = plateHeight();

        for (TrailStore.Trail trail : memories()) {
            for (int[] chunk : trail.chunks) plate(event, new ChunkPos(chunk[0], chunk[1]), y, colour);

            double lineY = lineHeight();
            Vec3d from = new Vec3d((trail.originX + trail.dirX * trail.spanMin) * 16.0 + 8.0, lineY,
                (trail.originZ + trail.dirZ * trail.spanMin) * 16.0 + 8.0);
            Vec3d to = new Vec3d((trail.originX + trail.dirX * trail.spanMax) * 16.0 + 8.0, lineY,
                (trail.originZ + trail.dirZ * trail.spanMax) * 16.0 + 8.0);

            event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z,
                new Color(colour.r, colour.g, colour.b, Math.max(0, colour.a / 2)),
                new Color(colour.r, colour.g, colour.b, colour.a));

            // Broken past both ends, never solid: what was walked and what is only
            // expected must not look the same, and a trail this straight over fifty
            // kilometres makes the expectation worth drawing.
            if (memoryProjection.get() > 0) {
                dashed(event, trail, trail.spanMax, trail.spanMax + memoryProjection.get(), lineY, colour);
                dashed(event, trail, trail.spanMin - memoryProjection.get(), trail.spanMin, lineY, colour);
            }
        }
    }

    private void dashed(Render3DEvent event, TrailStore.Trail trail, double from, double to, double y,
                        SettingColor colour) {
        Color faint = new Color(colour.r, colour.g, colour.b, Math.max(0, colour.a / 2));

        for (double at = from; at < to; at += DASH_CHUNKS * 2.0) {
            double end = Math.min(at + DASH_CHUNKS, to);

            event.renderer.line(
                (trail.originX + trail.dirX * at) * 16.0 + 8.0, y, (trail.originZ + trail.dirZ * at) * 16.0 + 8.0,
                (trail.originX + trail.dirX * end) * 16.0 + 8.0, y, (trail.originZ + trail.dirZ * end) * 16.0 + 8.0,
                faint, faint);
        }
    }

    /**
     * One chunk plate, dimmed by plate-opacity.
     *
     * <p>The single place every plate goes through, which is why the opacity
     * belongs here: hits, pale ones, near misses, stored chunks and memories all
     * dim together, and no future kind of plate can be forgotten.
     */
    /**
     * The guide line, drawn in the Nether at an eighth of its overworld scale.
     *
     * <p>Dividing every coordinate by eight is the whole trick: a straight line
     * stays straight and keeps its heading, so flying it below lands you on it
     * above. Nothing is read and nothing is announced down here — this is a
     * drawing to steer by, and the rule it came from is an overworld rule.
     */
    private void drawNetherGuide(Render3DEvent event) {
        if (!netherGuide.get() || mc.player == null) return;

        double[] guide = guideGeometry();
        if (guide == null) return;

        SettingColor colour = lineColor.get();
        double y = lineHeight();
        double here = guideAlong();

        Vec3d from = guidePoint(guide, here - tail.get(), y);
        Vec3d to = guidePoint(guide, here + projection.get(), y);

        event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z,
            new Color(colour.r, colour.g, colour.b, Math.max(0, colour.a / 3)), colour);
    }

    /**
     * Origin and direction of the guide, in Nether chunk coordinates.
     *
     * <p>The live line if there is still one from before the portal, otherwise the
     * remembered trail whose ground is closest to where you came out.
     */
    private double[] guideGeometry() {
        // Kept in overworld chunk indices, exactly as the fit produced them. The
        // eighth is applied once, in netherBlockAt, so there is a single place
        // where the two worlds meet and a single place to get it wrong.
        //
        if (fitted) return new double[]{originX, originZ, dirX, dirZ};

        TrailStore.Trail best = null;
        double bestOff = Double.MAX_VALUE;
        // Your Nether position as the overworld chunk index it answers to.
        double[] me = playerIndex(8.0);
        double cx = me[0];
        double cz = me[1];

        for (TrailStore.Trail trail : store.forDimension(OVERWORLD)) {
            double off = Math.abs(trail.lateral(cx, cz));
            if (off >= bestOff) continue;
            bestOff = off;
            best = trail;
        }

        if (best == null) return null;
        return new double[]{best.originX, best.originZ, best.dirX, best.dirZ};
    }

    /** Where you are in the guide's own frame: distance along it, in overworld chunks. */
    public double guideAlong() {
        double[] guide = guideGeometry();
        if (guide == null || mc.player == null) return 0.0;

        // The fit lives on chunk indices and a chunk's centre is its index plus a
        // half, so a continuous position has to lose that half before the two can
        // be compared. Without it the foot of the perpendicular and the distance
        // printed beside it were both up to a chunk and a half out.
        double[] me = playerIndex(8.0);
        double cx = me[0];
        double cz = me[1];

        return (cx - guide[0]) * guide[2] + (cz - guide[1]) * guide[3];
    }

    /** Signed distance from the guide, in Nether blocks: the number worth showing down there. */
    public double guideOffsetBlocks() {
        double[] guide = guideGeometry();
        if (guide == null || mc.player == null) return 0.0;

        double[] me = playerIndex(8.0);
        double cx = me[0];
        double cz = me[1];
        double lateralChunks = (cx - guide[0]) * -guide[3] + (cz - guide[1]) * guide[2];

        // One overworld chunk sideways is sixteen overworld blocks, so two below.
        return lateralChunks * 2.0;
    }

    /**
     * The one place the overworld-to-Nether conversion is written.
     *
     * <p>The fit runs on chunk indices, so a point at t along it is the chunk
     * {@code origin + dir*t}, whose centre block is that times sixteen plus eight.
     * The Nether block is that block divided by eight — which is
     * {@code (origin + dir*t) * 2 + 1}, not {@code ... * 2 + 8}. The old form was
     * seven blocks out in x and in z, fifty-six blocks once you climbed back up.
     */
    public static double[] netherBlockAt(double[] guide, double alongChunks) {
        return new double[]{
            (guide[0] + guide[2] * alongChunks) * 2.0 + 1.0,
            (guide[1] + guide[3] * alongChunks) * 2.0 + 1.0
        };
    }

    private static Vec3d guidePoint(double[] guide, double alongChunks, double y) {
        double[] block = netherBlockAt(guide, alongChunks);
        return new Vec3d(block[0], y, block[1]);
    }

    /** Heading of the guide, or NaN when there is nothing to follow down here. */
    public double guideHeading() {
        double[] guide = guideGeometry();
        if (guide == null) return Double.NaN;
        return MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(guide[3], guide[2])) - 90.0));
    }

    private void plate(Render3DEvent event, ChunkPos pos, double y, SettingColor color) {
        int x = pos.x << 4;
        int z = pos.z << 4;
        double opacity = plateOpacity.get() / 100.0;

        Color side = new Color(color.r, color.g, color.b, (int) Math.max(0, color.a / 4 * opacity));
        Color outline = new Color(color.r, color.g, color.b, (int) Math.max(0, color.a * opacity));

        event.renderer.box(new Box(x, y, z, x + 16.0, y, z + 16.0), side, outline, ShapeMode.Both, 0);
    }

    private double relLateral(double chunkX, double chunkZ) {
        return (chunkX - relOriginX) * -relDirZ + (chunkZ - relOriginZ) * relDirX;
    }

    private double relHeading() {
        return MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(relDirZ, relDirX)) - 90.0));
    }

    /**
     * Whether the line just fitted is the one that was released.
     *
     * <p>This is what the return guard is allowed to block, and nothing else. A
     * trail met two thousand chunks later, or one running a different way, is a
     * different trail and has to be announced — the guard exists to stop a line
     * re-acquiring itself while you sit beside it, not to make the second find of
     * a flight invisible.
     *
     * <p>Headings are compared modulo half a turn: a fitted line has no sign of
     * its own, so crossing the same trail the other way reads as 180 degrees
     * apart and is still the same trail.
     */
    private boolean sameAsReleased() {
        if (Math.abs(relLateral(originX, originZ)) > forkDistance.get() * DRIFT_MULTIPLIER) return false;

        double delta = Math.abs(MathHelper.wrapDegrees((float) (heading() - relHeading())));
        if (delta > 90.0) delta = 180.0 - delta;

        return delta <= SAME_LINE_DEGREES;
    }

    /** Same as {@link #point} but on the frozen geometry of a released line. */
    private Vec3d relPoint(double alongChunks, double y) {
        double cx = relOriginX + relDirX * alongChunks;
        double cz = relOriginZ + relDirZ * alongChunks;
        return new Vec3d(cx * 16.0 + 8.0, y, cz * 16.0 + 8.0);
    }

    private Vec3d point(double alongChunks, double y) {
        double cx = originX + dirX * alongChunks;
        double cz = originZ + dirZ * alongChunks;
        return new Vec3d(cx * 16.0 + 8.0, y, cz * 16.0 + 8.0);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!render.get() || !readout.get() || !hasLine() || mc.player == null || mc.world == null) return;
        if (!live()) return;

        Vec3d at = mc.player.getEntityPos();
        double here = playerAlong();
        // A fixed distance ahead rather than the end of the projection: near
        // enough that the anchor holds still, and on the side you are facing.
        Vec3d target = point(here + facing() * readoutDistance.get(), lineHeight());

        org.joml.Vector3d screen = new org.joml.Vector3d(target.x, target.y, target.z);
        if (!NametagUtils.to2D(screen, 1.0)) return;

        String text = String.format(Locale.ROOT, "%.1f deg  drift %+.1f  %s",
            heading(), drift(), aligned ? inliers.size() + " on line" : "unaligned");
        int width = mc.textRenderer.getWidth(text);
        SettingColor color = lineColor.get();

        NametagUtils.begin(screen, event.drawContext);
        event.drawContext.fill(-width / 2 - 2, -1, width / 2 + 2, 9, new Color(0, 0, 0, 150).getPacked());
        event.drawContext.drawText(mc.textRenderer, text, -width / 2, 0,
            new Color(color.r, color.g, color.b, 255).getPacked(), true);
        NametagUtils.end(event.drawContext);
    }

    // Read by the HUD

    /** Answers for the live line only: a released one is no longer being claimed. */
    public double drift() {
        if (!hasLine() || mc.player == null) return 0.0;
        Vec3d at = mc.player.getEntityPos();
        return playerLateral();
    }

    public double headingOrNaN() {
        return hasLine() ? heading() : Double.NaN;
    }

    /** Ownership, the same answer {@link #isInlier} gives, so the map and the count agree. */
    public int onLineCount() {
        return line.size();
    }

    /** Whether this chunk is one of the remembered hits — the profiler's trail key asks. */
    public boolean isHit(ChunkPos pos) {
        return line.contains(pos) || pool.contains(pos);
    }

    /**
     * Everything still in play. Allocates, so the map draws from {@link #line()}
     * and {@link #pool()} instead: colouring a combined list by asking each chunk
     * whether the line owns it is a search inside a loop, and at five hundred
     * chunks that is a quarter of a million comparisons per frame.
     */
    public List<ChunkPos> hits() {
        List<ChunkPos> all = new ArrayList<>(line.size() + pool.size());
        all.addAll(line);
        all.addAll(pool);
        return all;
    }

    public List<ChunkPos> line() {
        return line;
    }

    public List<ChunkPos> pool() {
        return pool;
    }

    public int hitCount() {
        return line.size() + pool.size();
    }

    public List<ChunkPos> nearMisses() {
        return nearMisses;
    }

    public List<ChunkPos> storedChunks() {
        return stored;
    }

    /** Owned by the live line, which is what the map draws bright. */
    public boolean isInlier(ChunkPos pos) {
        return line.contains(pos);
    }

    /** Whether the radar is reading this dimension's own chunks rather than a projection. */
    public boolean liveHere() {
        return live();
    }

    public boolean hasLine() {
        return fitted && !released;
    }

    /** Point on the fitted line at a distance along it, in chunk coordinates. */
    public double[] linePoint(double alongChunks) {
        return new double[]{originX + dirX * alongChunks, originZ + dirZ * alongChunks};
    }

    public double alongOf(double chunkX, double chunkZ) {
        return along(chunkX, chunkZ);
    }

    // Log

    /**
     * One row per classified chunk, modern ones included.
     *
     * <p>They used to be dropped as noise. They are not: the density gate divides
     * hits by everything that arrived, so without them the one number that decides
     * whether the radar speaks at all cannot be recomputed from the file. A flight
     * costs a few thousand extra rows and buys a log that can be replayed.
     */
    private void writeChunk(ChunkPos pos, Kind kind, int[] counts) {
        int others = 0;
        int otherMax = 0;

        // Counted exactly as classify counts them -- deepslate excluded and given
        // its own column. The two used to disagree, which made every offline
        // calibration read one thing while the rule read another.
        for (int i = 0; i < MARKER_COUNT; i++) {
            if (i == COPPER || i == TUFF || i == DEEPSLATE_HIGH || counts[i] == 0) continue;
            others++;
            otherMax = Math.max(otherMax, counts[i]);
        }

        write(String.join(",",
            "chunk",
            Long.toString(System.currentTimeMillis()),
            Integer.toString(pos.x),
            Integer.toString(pos.z),
            kind.name().toLowerCase(Locale.ROOT),
            Integer.toString(counts[COPPER]),
            Integer.toString(counts[TUFF]),
            Integer.toString(counts[DEEPSLATE_HIGH]),
            Integer.toString(others),
            Integer.toString(otherMax),
            fitted ? String.format(Locale.ROOT, "%.2f", along(pos.x, pos.z)) : "",
            fitted ? String.format(Locale.ROOT, "%.2f", lateral(pos.x, pos.z)) : "",
            isInlier(pos) ? "1" : "0",
            aligned ? "1" : "0",
            "",
            "",
            ""));
    }

    /**
     * Your own position, once a second.
     *
     * <p>The crossing rule and the drift both read your course, and the course was
     * the one thing the file never held: a replay could re-classify every chunk
     * and still not know where you were standing when it happened.
     */
    private void writePosition() {
        if (mc.player == null) return;

        ChunkPos pos = mc.player.getChunkPos();

        write(String.join(",",
            "pos",
            Long.toString(System.currentTimeMillis()),
            Integer.toString(pos.x),
            Integer.toString(pos.z),
            "", "", "", "", "", "", "", "",
            aligned ? "1" : "0",
            String.format(Locale.ROOT, "%.2f", MathHelper.wrapDegrees(mc.player.getYaw())),
            fitted ? String.format(Locale.ROOT, "%.2f", drift()) : "",
            String.format(Locale.ROOT, "%.1f", mc.player.getEntityPos().y)));
    }

    private void writeEvent(String title, String message) {
        writeEvent(title, message, false);
    }

    /**
     * @param force write even with the log switched off -- for the handful of
     *              lines that are the only copy of something about to be deleted.
     */
    private void writeEvent(String title, String message, boolean force) {
        ChunkPos pos = mc.player == null ? new ChunkPos(0, 0) : mc.player.getChunkPos();

        write(String.join(",",
            "event",
            Long.toString(System.currentTimeMillis()),
            Integer.toString(pos.x),
            Integer.toString(pos.z),
            title.toLowerCase(Locale.ROOT).replace(' ', '_'),
            "", "", "", "", "", "", "", "",
            aligned ? "1" : "0",
            fitted ? String.format(Locale.ROOT, "%.2f", heading()) : "",
            fitted ? String.format(Locale.ROOT, "%.2f", drift()) : "",
            message.replace(',', ';')), force);

        // Events are rare and the lines worth most; they do not wait for the
        // fiftieth chunk row to reach the disk.
        flushNow();
    }

    private void flushNow() {
        if (writer == null) return;

        try {
            writer.flush();
            pendingFlush = 0;
        } catch (IOException e) {
            HunterBuddyAddon.LOG.error("ChunkRadar: could not flush the radar log", e);
        }
    }

    private void write(String line) {
        write(line, false);
    }

    private void write(String line, boolean force) {
        if (!force && !log.get()) return;

        try {
            if (writer == null) {
                File file = new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "chunk-radar-" + LocalDate.now() + ".csv");
                file.getParentFile().mkdirs();
                boolean fresh = !file.exists() || file.length() == 0L;
                writer = new BufferedWriter(new FileWriter(file, true));
                if (fresh) {
                    // Chunk rows and event rows share the file and leave each
                    // other's columns empty rather than borrowing them: a heading
                    // stored under "lateral" is the kind of thing that reads fine
                    // for a week and then quietly ruins an analysis.
                    writer.write("kind,epoch_ms,cx,cz,class,copper,tuff,deepslate,others,other_max,along,lateral,inlier,aligned,heading,drift,detail"
                        + System.lineSeparator());
                }
            }

            writer.write(line);
            writer.write(System.lineSeparator());
            pendingFlush++;

            // By the clock as well as by the count. A flight that ends between two
            // fiftieths leaves its last rows in the buffer, and the file copied off
            // afterwards stops on a round number minutes before the flight did —
            // which is exactly what happened to the log of the twenty-second.
            long now = System.currentTimeMillis();
            if (pendingFlush >= 50 || now - lastFlushMs >= LOG_FLUSH_MS) {
                writer.flush();
                pendingFlush = 0;
                lastFlushMs = now;
            }
        } catch (IOException e) {
            HunterBuddyAddon.LOG.error("ChunkRadar: could not write the radar log", e);
            closeWriter();
        }
    }

    private void closeWriter() {
        if (writer == null) return;

        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            HunterBuddyAddon.LOG.error("ChunkRadar: could not close the radar log", e);
        }

        writer = null;
        pendingFlush = 0;
    }

    private static <K, V> void trim(LinkedHashMap<K, V> map, int limit) {
        while (map.size() > limit) {
            map.remove(map.keySet().iterator().next());
        }
    }

    private static Map<Block, Integer> buildMarkers() {
        Map<Block, Integer> markers = new IdentityHashMap<>();
        put(markers, COPPER, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.RAW_COPPER_BLOCK);
        put(markers, 1, Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.AMETHYST_CLUSTER);
        put(markers, 2, Blocks.SMOOTH_BASALT);
        put(markers, 3, Blocks.CALCITE);
        put(markers, TUFF, Blocks.TUFF);
        put(markers, 5, Blocks.AZALEA, Blocks.FLOWERING_AZALEA, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES);
        put(markers, 6, Blocks.BIG_DRIPLEAF, Blocks.SMALL_DRIPLEAF);
        put(markers, 7, Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET);
        put(markers, 8, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT);
        put(markers, 9, Blocks.POINTED_DRIPSTONE, Blocks.DRIPSTONE_BLOCK);
        put(markers, 10, Blocks.KELP, Blocks.KELP_PLANT);
        return markers;
    }

    private static void put(Map<Block, Integer> markers, int id, Block... blocks) {
        for (Block block : blocks) markers.put(block, id);
    }
}
