package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayDeque;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;

import static com.hunterbuddy.modules.regear.util.Utils.angleDifference;
import static com.hunterbuddy.modules.regear.util.Utils.positionInDirection;
import static com.hunterbuddy.modules.regear.util.Utils.sendWebhook;
import static com.hunterbuddy.modules.regear.util.Utils.smoothRotation;

/**
 * BepHax's TrailFollower, ported as-is, plus a second steering mode written
 * from the description of their v0.5.2 rebuild (no source for that rebuild
 * exists to port from — BepHax is closed-source — so {@link SteeringMode#ROUTE_FINDER}
 * below is a clean-room reading of the changelog text, not a copy of anything).
 *
 * <p>The original port's logic is untouched and stays selectable as a
 * fallback ({@link SteeringMode#AVERAGE}, no longer the default): forward-
 * weighted average over the recent trail chunks, a committed heading with a
 * turn-rate cap and a reverse lock, and a sweep search when chunks stop
 * arriving. The module name and the setting names are theirs, which also
 * happens to keep any saved values applying.
 *
 * <p>What changed for the rebuild, per the changelog, is only how the target
 * heading gets picked. Chunks were already being classified trail-or-not by
 * XaeroPlus's own old/new-chunk detectors ({@link #isValidChunk}) before any
 * of this — "chunks loaded before you are the trail, chunks generated fresh
 * for you are the wall" is that same signal, just named (with one nuance
 * that name doesn't carry: XaeroPlus's own verdict is sticky once cached, so
 * a chunk someone else is generating live in front of you right now reads as
 * "wall" too, same as one truly never visited — this follows saved trails,
 * not live ones, exactly as the average always did). What is new is scoring
 * a fan of candidate headings by how far each one's trail actually continues
 * out to {@link #routeLookaheadChunks} steps of 16 blocks, tolerating gaps up
 * to {@link #routeMaxGap} such steps before disqualifying a lead, and
 * penalising both the worst gap crossed and the degrees turned from the
 * committed course — see {@link #updateDesiredYawFromRouteFinder}. It plugs
 * into the exact same {@link #desiredYaw} the average did, so the turn-rate
 * cap, the sweep search and the trail timeout downstream in {@link #onTick}
 * still apply unchanged regardless of which mode picked the heading. The
 * reverse lock is applied differently in each: under AVERAGE it filters what
 * may enter the trail deque at all; under ROUTE_FINDER the map takes every
 * classified chunk and the lock instead rules out any candidate heading too
 * far off the direction actually being flown ({@link #travelYaw}), while the
 * chunks flown through already ({@link #ownTrackChunks}) never score as
 * trail ahead.
 */
public class TrailFollower extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<Integer> maxTrailLength = this.sgGeneral.add(new IntSetting.Builder()
        .name("max-trail-length")
        .description("The number of trail points to keep for the average. Adjust to change how quickly the average will change. More does not necessarily equal better because if the list is too long it will contain chunks behind you.")
        .defaultValue(20)
        .sliderRange(1, 100)
        .build()
    );

    public final Setting<Integer> chunksBeforeStarting = this.sgGeneral.add(new IntSetting.Builder()
        .name("chunks-before-starting")
        .description("Useful for afking looking for a trail. The amount of chunks before it gets detected as a trail.")
        .defaultValue(10)
        .sliderRange(1, 50)
        .build()
    );

    public final Setting<Integer> chunkConsiderationWindow = this.sgGeneral.add(new IntSetting.Builder()
        .name("chunk-timeframe")
        .description("The amount of time in seconds that the chunks must be found in before starting.")
        .defaultValue(5)
        .sliderRange(1, 20)
        .build()
    );

    public final Setting<TrailEndBehavior> trailEndBehavior = this.sgGeneral.add(new EnumSetting.Builder<TrailEndBehavior>()
        .name("trail-end-behavior")
        .description("What to do when the trail ends.")
        .defaultValue(TrailEndBehavior.DISABLE)
        .build()
    );

    public final Setting<Double> trailEndYaw = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("trail-end-yaw")
        .description("The direction to go after the trail is abandoned.")
        .defaultValue(0.0)
        .sliderRange(0.0, 359.9)
        .visible(() -> this.trailEndBehavior.get() == TrailEndBehavior.FLY_TOWARDS_YAW)
        .build()
    );

    public final Setting<OverworldFlightMode> overworldFlightMode = this.sgGeneral.add(new EnumSetting.Builder<OverworldFlightMode>()
        .name("overworld-flight-mode")
        .description("Choose how TrailFollower flies in Overworld. If other is selected then nothing will be automatically enabled, instead just your yaw will be changed to point towards the trail.")
        .defaultValue(OverworldFlightMode.PITCH40)
        .build()
    );

    public final Setting<NetherPathMode> netherPathMode = this.sgGeneral.add(new EnumSetting.Builder<NetherPathMode>()
        .name("nether-path-mode")
        .description("Choose how TrailFollower sets the baritone goal in Nether. AVERAGE aims along the steered heading, same as overworld yaw-lock. OTHER follows the trail itself: the goal is the farthest known trail chunk ahead of you, connected to where you are, pushed on along that line so Baritone never lands at it. Never a chunk behind you.")
        .defaultValue(NetherPathMode.OTHER)
        .build()
    );

    public final Setting<Boolean> netherCorridor = this.sgGeneral.add(new BoolSetting.Builder()
        .name("nether-corridor")
        .description("Tell Baritone (dekrom fork) which chunks are the trail so its path search cannot leave them; no effect on upstream Baritone.")
        .defaultValue(true)
        .build()
    );

    public final Setting<SteeringMode> steeringMode = this.sgGeneral.add(new EnumSetting.Builder<SteeringMode>()
        .name("steering-mode")
        .description("How the target heading is picked from classified chunks. ROUTE_FINDER scores a fan of candidate headings by how far the trail actually continues on each, tolerating small gaps and penalising sharp turns. AVERAGE is the original forward-weighted average of every trail chunk seen.")
        .defaultValue(SteeringMode.ROUTE_FINDER)
        .build()
    );

    public final Setting<Boolean> pitch40Firework = this.sgGeneral.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Uses a firework automatically if your velocity is too low.")
        .defaultValue(true)
        .visible(() -> this.overworldFlightMode.get() == OverworldFlightMode.PITCH40)
        .build()
    );

    public final Setting<Double> rotateScaling = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("rotate-scaling")
        .description("Scaling of how fast the yaw changes. 1 = instant, 0 = doesn't change")
        .defaultValue(0.15)
        .sliderRange(0.0, 1.0)
        .build()
    );

    public final Setting<Boolean> oppositeDimension = this.sgGeneral.add(new BoolSetting.Builder()
        .name("opposite-dimension")
        .description("Follows trails from the opposite dimension (Requires that you've already loaded the other dimension with XP).")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> autoElytra = this.sgGeneral.add(new BoolSetting.Builder()
        .name("auto-start-baritone-elytra")
        .description("Starts baritone elytra for you.")
        .defaultValue(true)
        .build()
    );

    private final SettingGroup sgRouteFinder = this.settings.createGroup("Route Finder", false);

    public final Setting<Integer> routeLookaheadChunks = this.sgRouteFinder.add(new IntSetting.Builder()
        .name("lookahead-chunks")
        .description("How many steps of 16 blocks out each candidate heading is scored, in units of a chunk. No point scoring further than chunks actually get classified at, so this tracks your server's view distance automatically at 0 — the client already knows it (the server tells it on join and on every change), no guessing needed.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 32)
        .visible(() -> this.steeringMode.get() == SteeringMode.ROUTE_FINDER)
        .build()
    );

    public final Setting<Integer> routeMaxGap = this.sgRouteFinder.add(new IntSetting.Builder()
        .name("max-gap-chunks")
        .description("Consecutive 16-block steps of non-trail a heading is allowed to cross, so long as trail picks up again afterward, before it's cut short there instead. A real trail can skip a pond or a stretch the palette didn't flag; one that never finds trail again within this many steps isn't the trail.")
        .defaultValue(5)
        .sliderRange(1, 10)
        .visible(() -> this.steeringMode.get() == SteeringMode.ROUTE_FINDER)
        .build()
    );

    public final Setting<Double> frontierConeAngle = this.sgRouteFinder.add(new DoubleSetting.Builder()
        .name("frontier-cone-angle")
        .description("Nether only. A trail chunk counts as ahead only within this many degrees of the trail's own heading. Nothing outside the cone, and nothing behind the farthest point already reached, can ever become the goal.")
        .defaultValue(100.0)
        .sliderRange(30.0, 160.0)
        .build()
    );

    public final Setting<Double> frontierMaxTurn = this.sgRouteFinder.add(new DoubleSetting.Builder()
        .name("frontier-max-turn")
        .description("Nether only. How many degrees the trail heading may turn each time the frontier advances. Low keeps a straight trail straight through a stray chunk; high follows sharp bends sooner.")
        .defaultValue(30.0)
        .sliderRange(5.0, 90.0)
        .build()
    );

    public final Setting<Double> routeFanDegrees = this.sgRouteFinder.add(new DoubleSetting.Builder()
        .name("fan-width")
        .description("Candidate headings are scored this many degrees either side of the committed direction.")
        .defaultValue(70.0)
        .min(10.0)
        .sliderMax(170.0)
        .visible(() -> this.steeringMode.get() == SteeringMode.ROUTE_FINDER)
        .build()
    );

    public final Setting<Double> routeFanStepDegrees = this.sgRouteFinder.add(new DoubleSetting.Builder()
        .name("fan-step")
        .description("Degrees between candidate headings in the fan. Finer steps find the trail's true angle more exactly, at the cost of more chunk lookups per tick — still cheap even at the low end.")
        .defaultValue(5.0)
        .min(1.0)
        .sliderMax(15.0)
        .visible(() -> this.steeringMode.get() == SteeringMode.ROUTE_FINDER)
        .build()
    );

    public final Setting<Double> routeGapPenalty = this.sgRouteFinder.add(new DoubleSetting.Builder()
        .name("gap-penalty")
        .description("Score subtracted per chunk of the worst gap a heading crosses. Higher favours an unbroken trail over one that reaches further but through holes.")
        .defaultValue(1.5)
        .min(0.0)
        .sliderMax(5.0)
        .visible(() -> this.steeringMode.get() == SteeringMode.ROUTE_FINDER)
        .build()
    );

    public final Setting<Double> routeLateralPenalty = this.sgRouteFinder.add(new DoubleSetting.Builder()
        .name("lateral-penalty")
        .description("Score subtracted per degree a candidate heading turns from the committed direction. Higher holds a straighter course; lower follows a bending trail more eagerly.")
        .defaultValue(0.08)
        .min(0.0)
        .sliderMax(0.5)
        .visible(() -> this.steeringMode.get() == SteeringMode.ROUTE_FINDER)
        .build()
    );

    private final SettingGroup sgAdvanced = this.settings.createGroup("Advanced", false);

    public final Setting<Double> pathDistance = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("path-distance")
        .description("The distance to add trail positions in the direction the player is facing. (Ignored when following overworld from nether)")
        .defaultValue(500.0)
        .sliderRange(100.0, 2000.0)
        .onChanged(value -> this.pathDistanceActual = value)
        .build()
    );

    public final Setting<FollowMode> flightMethod = this.sgAdvanced.add(new EnumSetting.Builder<FollowMode>()
        .name("flight-method")
        .description("Decided how the goals will be used. Leave this on AUTO unless you want to use yaw lock in the nether for example.")
        .defaultValue(FollowMode.AUTO)
        .build()
    );

    public final Setting<Double> startDirectionWeighting = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("start-direction-weight")
        .description("Initial bias toward the direction you're facing when enabling. Decays as trail becomes established. 0 = no bias, 1 = strong bias. Only blended in by AVERAGE — the route finder starts wherever you're looking on its own, with nothing here to bias.")
        .defaultValue(0.7)
        .min(0.0)
        .sliderMax(1.0)
        .visible(() -> this.steeringMode.get() == SteeringMode.AVERAGE)
        .build()
    );

    public final Setting<Double> forwardConeAngle = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("forward-cone-angle")
        .description("During initial detection, only consider chunks within this angle of your facing direction. 90 = hemisphere ahead, 180 = all around.")
        .defaultValue(120.0)
        .min(45.0)
        .sliderMax(180.0)
        .build()
    );

    public final Setting<Double> forwardWeightStrength = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("forward-weight-strength")
        .description("How much to favor chunks aligned with current direction in the average. 0 = equal weight, 1 = strong forward preference. AVERAGE only — the route finder scores by lookahead and gaps, not a weighted mean, so this has nothing to act on there.")
        .defaultValue(0.6)
        .min(0.0)
        .sliderMax(1.0)
        .visible(() -> this.steeringMode.get() == SteeringMode.AVERAGE)
        .build()
    );

    public final Setting<DirectionWeighting> directionWeighting = this.sgAdvanced.add(new EnumSetting.Builder<DirectionWeighting>()
        .name("direction-weighting")
        .description("How the chunks found should be weighted. Useful for path splits. Left will weight chunks to the left of the player higher, right will weigh chunks to the right higher, and none will be in the middle/random. AVERAGE only — it weights the trail deque, which the route finder never reads.")
        .defaultValue(DirectionWeighting.NONE)
        .visible(() -> this.steeringMode.get() == SteeringMode.AVERAGE)
        .build()
    );

    public final Setting<Integer> directionWeightingMultiplier = this.sgAdvanced.add(new IntSetting.Builder()
        .name("direction-weighting-multiplier")
        .description("The multiplier for how much weight should be given to chunks in the direction specified. Values are capped to be in the range [2, maxTrailLength].")
        .defaultValue(2)
        .min(2)
        .sliderMax(10)
        .visible(() -> this.steeringMode.get() == SteeringMode.AVERAGE && this.directionWeighting.get() != DirectionWeighting.NONE)
        .build()
    );

    public final Setting<Boolean> only112 = this.sgAdvanced.add(new BoolSetting.Builder()
        .name("follow-only-1.12")
        .description("Will only follow 1.12 chunks and will ignore other ones.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> chunkFoundTimeout = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("chunk-found-timeout")
        .description("The amount of MS without a trail chunk ahead of you before weaving side to side to let the world load. Raise on laggy servers so normal chunk droughts don't trigger searching.")
        .defaultValue(15000.0)
        .min(1000.0)
        .sliderMax(20000.0)
        .build()
    );

    public final Setting<SearchBehavior> searchBehavior = this.sgAdvanced.add(new EnumSetting.Builder<SearchBehavior>()
        .name("search-behavior")
        .description("What to do when chunks stop arriving. SWEEP weaves around the trail direction keeping forward progress, CIRCLE spins in place (legacy), STRAIGHT holds the trail direction. Baritone mode treats CIRCLE as STRAIGHT.")
        .defaultValue(SearchBehavior.STRAIGHT)
        .build()
    );

    public final Setting<Double> sweepAngle = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("sweep-angle")
        .description("Starting sweep amplitude off the trail direction. Widens up to 90 degrees the longer no forward chunks arrive, slowing forward progress so slow-loading chunks can catch up.")
        .defaultValue(35.0)
        .min(10.0)
        .sliderMax(90.0)
        .visible(() -> this.searchBehavior.get() == SearchBehavior.SWEEP)
        .build()
    );

    public final Setting<Double> circlingDegPerTick = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("Circling-degrees-per-tick")
        .description("The amount of degrees to change per tick while searching (circle spin rate / sweep speed).")
        .defaultValue(2.0)
        .min(1.0)
        .sliderMax(20.0)
        .build()
    );

    public final Setting<Double> trailTimeout = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("trail-timeout")
        .description("The amount of MS without a chunk found to stop following the trail.")
        .defaultValue(120000.0)
        .min(10000.0)
        .sliderMax(180000.0)
        .build()
    );

    public final Setting<Double> maxTrailDeviation = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("max-trail-deviation")
        .description("Maximum allowed angle (in degrees) from the committed trail direction. Helps avoid switching to intersecting trails.")
        .defaultValue(180.0)
        .min(1.0)
        .sliderMax(270.0)
        .build()
    );

    public final Setting<Boolean> reverseLock = this.sgAdvanced.add(new BoolSetting.Builder()
        .name("reverse-lock")
        .description("Ignores chunks too far off the committed trail direction, so chunks loading late behind you (server lag) can't turn you back the way you came. Also stops the route finder from picking a heading that far off the direction actually being flown.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> reverseLockAngle = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("reverse-lock-angle")
        .description("Chunks more than this many degrees off the committed trail direction are ignored.")
        .defaultValue(110.0)
        .min(60.0)
        .sliderMax(180.0)
        .visible(this.reverseLock::get)
        .build()
    );

    public final Setting<Double> maxTurnRate = this.sgAdvanced.add(new DoubleSetting.Builder()
        .name("max-turn-rate")
        .description("Maximum degrees per second the committed trail direction can change. Stops lag bursts of sideways chunks from whipping the heading around.")
        .defaultValue(45.0)
        .min(5.0)
        .sliderMax(180.0)
        .build()
    );

    public final Setting<Integer> chunkCacheLength = this.sgAdvanced.add(new IntSetting.Builder()
        .name("chunk-cache-length")
        .description("The amount of chunks to keep in the cache. (Won't be applied until deactivating)")
        .defaultValue(100000)
        .min(1000)
        .sliderRange(1000, 10000000)
        .build()
    );

    public final Setting<String> webhookLink = this.sgGeneral.add(new StringSetting.Builder()
        .name("webhook-link")
        .description("Will send all updates to the webhook link. Leave blank to disable.")
        .defaultValue("")
        .build()
    );

    public final Setting<Integer> baritoneUpdateTicks = this.sgAdvanced.add(new IntSetting.Builder()
        .name("baritone-path-update-ticks")
        .description("The amount of ticks between updates to the baritone goal. Low values may cause high instability.")
        .defaultValue(100)
        .sliderRange(20, 600)
        .build()
    );

    public final Setting<Boolean> debug = this.sgAdvanced.add(new BoolSetting.Builder()
        .name("debug")
        .description("Debug mode.")
        .defaultValue(false)
        .build()
    );

    private boolean oldAutoFireworkValue;
    private boolean oldAutoBoundAdjustValue;
    private FollowMode followMode;
    private boolean followingTrail = false;
    private ArrayDeque<Vec3d> trail = new ArrayDeque<>();
    private ArrayDeque<Vec3d> possibleTrail = new ArrayDeque<>();
    private long lastFoundTrailTime;
    private long lastSteerChunkTime;
    private long lastFoundPossibleTrailTime;
    private double pathDistanceActual = this.pathDistance.get();
    private boolean started = false;
    private double initialYaw = 0.0;
    private boolean hasInitialDirection = false;
    private int chunksFoundSinceStart = 0;
    private Cache<Long, Byte> seenChunksCache = Caffeine.newBuilder()
        .maximumSize(this.chunkCacheLength.get().intValue())
        .expireAfterWrite(Duration.ofMinutes(5L))
        .build();

    /**
     * The chunks confirmed trail this session, keyed the same way
     * {@link #seenChunksCache} keys a chunk. {@link #scoreHeading} only ever
     * tests for {@code TRUE} — a wall and an unclassified chunk score
     * identically there, so despite the {@code Boolean} this is really a set
     * of trail chunks with extra room that goes unused; the {@code false}
     * entries {@link #onChunkData} writes cost nothing and aren't read as a
     * wall by anything today; a {@code Set<Long>} would say the same thing
     * more honestly. Left as-is rather than promoted to an actual wall/trail
     * distinction the walk would stop hard on: a chunk someone else is
     * generating live right in front of you reads exactly like true frontier
     * to XaeroPlus's own classifier (see the class doc), and a route finder
     * that refused to cross it outright would refuse ground the average
     * always followed.
     */
    private Cache<Long, Boolean> routeChunkMap = Caffeine.newBuilder()
        .maximumSize(this.chunkCacheLength.get().intValue())
        .expireAfterWrite(Duration.ofMinutes(5L))
        .build();

    /**
     * The chunks the player has actually been in this session — only the
     * exact chunk each tick, never its neighbours, so a parallel trail one
     * chunk over stays scorable. {@link #scoreHeading} treats these as
     * non-trail whatever {@link #routeChunkMap} says about them.
     */
    private Cache<Long, Boolean> ownTrackChunks = Caffeine.newBuilder()
        .maximumSize(this.chunkCacheLength.get().intValue())
        .expireAfterWrite(Duration.ofMinutes(5L))
        .build();

    /**
     * Ticks of player positions kept for the travel heading, and the least
     * horizontal displacement across them that counts as a direction at all.
     */
    private static final int TRAVEL_HISTORY_TICKS = 40;
    private static final double TRAVEL_MIN_DISTANCE = 8.0;
    private final ArrayDeque<Vec3d> recentPositions = new ArrayDeque<>();
    /** The direction actually being flown, smoothed over {@link #TRAVEL_HISTORY_TICKS}; see {@link #recordTravel}. */
    private double travelYaw;
    private boolean travelYawValid;
    /** The last goal handed to Baritone in the Nether, so an unchanged frontier is not re-issued every tick. */
    private Vec3d lastBaritoneGoal;
    /** The farthest trail point accepted so far; the next frontier may never sit behind it. */
    private Vec3d frontierAnchor;
    /** The trail's own heading, turned only by following successive frontiers; see {@link #frontierGoal}. */
    private double trailHeadingYaw;
    private boolean trailHeadingValid;
    private double targetYaw;
    private double desiredYaw;
    private double committedYaw;
    private double searchPhase;
    private boolean wasSearching;
    private int baritoneSetGoalTicks = 0;
    Vec3d posDebug;

    /**
     * The chunks Baritone's Nether path search is allowed to use: the trail as
     * {@link #frontierGoal} last walked it, the gaps between its chunks filled
     * in, the line on to the goal, and our own track. Rebuilt on every walk,
     * handed to the dekrom Baritone fork by {@link #pushCorridor}.
     */
    private final LongOpenHashSet corridorKeys = new LongOpenHashSet();
    /**
     * The fork's corridor API, looked up once by reflection: HunterBuddy
     * compiles against upstream Baritone, which has no such methods — there
     * the lookup fails once and the corridor is simply never pushed.
     */
    private Method corridorSetMethod;
    private Method corridorClearMethod;
    private boolean corridorLookedUp;
    private boolean corridorAvailable;
    /** Whether Baritone currently holds a corridor from us, so it is cleared once and not re-cleared. */
    private boolean corridorPushed;
    private long lastCorridorPushMs;
    private int lastCorridorHash;

    public TrailFollower() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "TrailFollower", "Automatically follows trails in all dimensions.");
    }

    void resetTrail() {
        this.baritoneSetGoalTicks = 0;
        this.followingTrail = false;
        this.trail = new ArrayDeque<>();
        this.possibleTrail = new ArrayDeque<>();
        this.hasInitialDirection = false;
        this.chunksFoundSinceStart = 0;
        this.wasSearching = false;
        this.searchPhase = 0.0;
        this.ownTrackChunks.invalidateAll();
        this.recentPositions.clear();
        this.travelYawValid = false;
        this.lastBaritoneGoal = null;
        this.frontierAnchor = null;
        this.trailHeadingValid = false;
        this.clearCorridor();
    }

    @Override
    public void onActivate() {
        this.resetTrail();
        XaeroPlus.EVENT_BUS.register(this);
        if (this.started) {
            if (this.mc.player != null && this.mc.world != null) {
                RegistryKey<World> currentDimension = this.mc.world.getRegistryKey();
                if (this.oppositeDimension.get()) {
                    if (currentDimension.equals(World.END)) {
                        this.info("There is no opposite dimension to the end. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }

                    if (currentDimension.equals(World.NETHER)) {
                        this.info("Following overworld trails from the nether is not supported yet, sorry. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }
                }

                if (this.flightMethod.get() != FollowMode.AUTO) {
                    this.followMode = this.flightMethod.get();
                } else if (!currentDimension.equals(World.NETHER)) {
                    this.followMode = FollowMode.YAWLOCK;
                    this.info("You are in the overworld or end, basic yaw mode will be used.");
                } else {
                    try {
                        Class.forName("baritone.api.BaritoneAPI");
                        this.followMode = FollowMode.BARITONE;
                        this.info("You are in the nether, baritone mode will be used.");
                    } catch (ClassNotFoundException e) {
                        this.info("Baritone is required to trail follow in the nether. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }
                }

                if (this.followMode == FollowMode.YAWLOCK && !this.mc.world.getRegistryKey().equals(World.NETHER)) {
                    if (this.overworldFlightMode.get() == OverworldFlightMode.PITCH40) {
                        Module pitch40UtilModule = Modules.get().get(Pitch40.class);
                        if (!pitch40UtilModule.isActive()) {
                            pitch40UtilModule.toggle();
                            if (this.pitch40Firework.get()) {
                                Setting<Boolean> setting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-firework");
                                if (setting != null) {
                                    this.info("Auto Firework enabled, if you want to change the velocity threshold or the firework cooldown check the settings under Pitch40.");
                                    this.oldAutoFireworkValue = setting.get();
                                    setting.set(true);
                                }
                            }

                            Setting<Boolean> autoBoundAdjustSetting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-bound-adjust");
                            if (autoBoundAdjustSetting != null) {
                                this.oldAutoBoundAdjustValue = autoBoundAdjustSetting.get();
                                autoBoundAdjustSetting.set(false);
                            }
                        }
                    } else if (this.overworldFlightMode.get() == OverworldFlightMode.ROCKETS) {
                        RocketFly rocketFly = Modules.get().get(RocketFly.class);
                        if (!rocketFly.isActive()) {
                            rocketFly.toggle();
                        }
                    }
                }

                this.initialYaw = this.getActualYaw(this.mc.player.getYaw());
                this.hasInitialDirection = true;
                Vec3d offset = new Vec3d(
                        Math.sin(-this.mc.player.getYaw() * Math.PI / 180.0), 0.0, Math.cos(-this.mc.player.getYaw() * Math.PI / 180.0)
                    )
                    .normalize()
                    .multiply(this.pathDistance.get());
                Vec3d targetPos = this.mc.player.getEntityPos().add(offset);

                for (int i = 0; i < this.maxTrailLength.get().intValue() * this.startDirectionWeighting.get(); i++) {
                    this.trail.add(targetPos);
                }

                this.targetYaw = this.initialYaw;
            } else {
                this.toggle();
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.started = false;
        this.seenChunksCache = Caffeine.newBuilder().maximumSize(this.chunkCacheLength.get().intValue()).expireAfterWrite(Duration.ofMinutes(5L)).build();
        this.routeChunkMap = Caffeine.newBuilder().maximumSize(this.chunkCacheLength.get().intValue()).expireAfterWrite(Duration.ofMinutes(5L)).build();
        XaeroPlus.EVENT_BUS.unregister(this);
        this.trail.clear();
        if (this.followMode != null) {
            switch (this.followMode) {
                case BARITONE:
                    this.clearCorridor();
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
                    break;
                case YAWLOCK:
                    if (this.mc.world == null || this.mc.world.getRegistryKey().equals(World.NETHER)) {
                        return;
                    }

                    if (this.overworldFlightMode.get() == OverworldFlightMode.ROCKETS) {
                        RocketFly rocketFly = Modules.get().get(RocketFly.class);
                        if (rocketFly != null) {
                            rocketFly.resetYLock();
                            if (rocketFly.isActive()) {
                                rocketFly.toggle();
                            }
                        }
                    } else if (this.overworldFlightMode.get() == OverworldFlightMode.PITCH40) {
                        Module pitch40UtilModule = Modules.get().get(Pitch40.class);
                        if (pitch40UtilModule != null) {
                            if (pitch40UtilModule.isActive()) {
                                pitch40UtilModule.toggle();
                            }

                            Setting<Boolean> autoFireworkSetting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-firework");
                            if (autoFireworkSetting != null) {
                                autoFireworkSetting.set(this.oldAutoFireworkValue);
                            }

                            Setting<Boolean> autoBoundAdjustSetting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-bound-adjust");
                            if (autoBoundAdjustSetting != null) {
                                autoBoundAdjustSetting.set(this.oldAutoBoundAdjustValue);
                            }
                        }
                    }
            }
        }
    }

    /** How far the trail walk looks around the player, in chunks (Chebyshev): the loaded area and a stretch of remembered trail beyond it. */
    private static final int FRONTIER_WALK_RADIUS_CHUNKS = 32;
    /** How far back along the walked trail its local direction is measured, in blocks. */
    private static final double TRAIL_DIRECTION_SPAN = 96.0;
    /** Below this span the chain is too short to give a direction (chunk rounding alone is 8 blocks). */
    private static final double TRAIL_DIRECTION_MIN_SPAN = 48.0;

    /** What one walk of the known trail found: the frontier chunk and the chunk each walked chunk was reached from. */
    private record TrailWalk(Vec3d best, long bestKey, it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap parent, long rootKey) {}

    /**
     * Walks the known trail chunks outward from the chunk we are in, a step jumping up to
     * {@link #routeMaxGap} chunks of non-trail (the route finder's tolerance), through any trail
     * chunk within {@link #FRONTIER_WALK_RADIUS_CHUNKS} — the ones we have flown included, because
     * this walk is about connectivity, and refusing them left the walk with nowhere to start from on
     * a one-chunk-wide trail we were flying exactly. Every step taken is rasterised into the
     * corridor handed to Baritone.
     *
     * <p>The frontier is the walked chunk farthest along {@code forward} past {@code anchor}; a chunk
     * we have flown, one behind the anchor (one chunk of slack) or one outside {@code coneCos}
     * around the heading as seen from the anchor can never be it.
     */
    private TrailWalk walkTrail(ChunkPos playerChunk, Vec3d playerPos, Vec3d anchor, Vec3d forward, double coneCos) {
        double minAnchorProgress = -16.0;
        int reach = Math.max(1, this.routeMaxGap.get() + 1);
        it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap parent = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
        parent.defaultReturnValue(Long.MIN_VALUE);
        ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
        long rootKey = playerChunk.toLong();
        parent.put(rootKey, rootKey);
        queue.add(playerChunk);
        Vec3d best = null;
        long bestKey = Long.MIN_VALUE;
        double bestProgress = Double.NEGATIVE_INFINITY;
        int expanded = 0;
        while (!queue.isEmpty() && expanded < 4096) {
            ChunkPos current = queue.poll();
            expanded++;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    ChunkPos next = new ChunkPos(current.x + dx, current.z + dz);
                    if (Math.max(Math.abs(next.x - playerChunk.x), Math.abs(next.z - playerChunk.z)) > FRONTIER_WALK_RADIUS_CHUNKS) continue;
                    long key = next.toLong();
                    if (parent.containsKey(key)) continue;
                    if (!Boolean.TRUE.equals(this.routeChunkMap.getIfPresent(key))) continue;
                    parent.put(key, current.toLong());
                    queue.add(next);
                    // The trail is dotted: the corridor takes the whole line between the two
                    // chunks, or the fork's one-chunk dilation would leave disconnected islands.
                    this.rasteriseCorridor(current.x, current.z, next.x, next.z);
                    // Connectivity done; now whether this chunk may be the frontier.
                    if (this.ownTrackChunks.getIfPresent(key) != null) continue;
                    Vec3d center = new Vec3d(next.getStartX() + 8.0, playerPos.y, next.getStartZ() + 8.0);
                    Vec3d fromAnchor = center.subtract(anchor);
                    double anchorProgress = fromAnchor.x * forward.x + fromAnchor.z * forward.z;
                    if (anchorProgress < minAnchorProgress) continue;
                    double fromAnchorDist = Math.hypot(fromAnchor.x, fromAnchor.z);
                    if (fromAnchorDist >= 16.0 && anchorProgress / fromAnchorDist < coneCos) continue;
                    if (anchorProgress > bestProgress) {
                        bestProgress = anchorProgress;
                        best = center;
                        bestKey = key;
                    }
                }
            }
        }
        return new TrailWalk(best, bestKey, parent, rootKey);
    }

    /**
     * Where Baritone is sent in the Nether when {@code nether-path-mode} is OTHER.
     *
     * <p>The trail is walked ({@link #walkTrail}) for its frontier: the farthest trail chunk along
     * the trail heading past the last accepted frontier ({@link #frontierAnchor}). Nothing behind
     * that mark and nothing outside {@code frontier-cone-angle} can become the frontier, which is
     * what keeps the goal from ever pointing back down the trail. The trail heading is then
     * re-measured on the chain of chunks that led to the frontier, over its last
     * {@link #TRAIL_DIRECTION_SPAN} blocks, and turned at most {@code frontier-max-turn} per update.
     *
     * <p>The goal sits {@link #pathDistanceActual} past the frontier along that heading — on the
     * trail's own line, never beside it — or, when we have already flown past the frontier (the
     * server shows us chunks late), past our own projection on that line, so Baritone can never
     * reach the goal and land. When no new frontier is known the same line is held straight ahead;
     * only before any heading exists does this return {@code null}, and the caller then keeps the
     * previous goal.
     */
    private Vec3d frontierGoal() {
        if (this.mc.player == null) return null;
        // The corridor is rebuilt from scratch on every walk: only the edges accepted below belong to it.
        this.corridorKeys.clear();
        Vec3d playerPos = this.mc.player.getEntityPos();
        ChunkPos playerChunk = this.mc.player.getChunkPos();
        // The reference direction is the TRAIL's heading, not the player's: during a detour around
        // a massif the player flies sideways or back, and measuring "ahead" against that is exactly
        // what put the goal behind us. The trail heading starts as the launch heading and only
        // turns by following the trail itself, capped per update by frontier-max-turn.
        double forwardYaw = this.trailHeadingValid ? this.trailHeadingYaw : this.initialYaw;
        Vec3d forward = positionInDirection(Vec3d.ZERO, forwardYaw, 1.0);
        double coneCos = Math.cos(Math.toRadians(this.frontierConeAngle.get()));
        Vec3d anchor = this.frontierAnchor != null ? this.frontierAnchor : playerPos;

        TrailWalk walk = this.walkTrail(playerChunk, playerPos, anchor, forward, coneCos);
        Vec3d best = walk.best();
        if (best != null) {
            // The trail's direction where it was last seen: measured on the chain of trail chunks
            // that led to the frontier, far enough back to be more than chunk rounding. A chain too
            // short for that falls back to the step from the previous frontier.
            Double localYaw = null;
            long cursor = walk.bestKey();
            double span = 0.0;
            ChunkPos tail = new ChunkPos(walk.bestKey());
            while (span < TRAIL_DIRECTION_SPAN) {
                long up = walk.parent().get(cursor);
                if (up == Long.MIN_VALUE || up == cursor || up == walk.rootKey()) break;
                cursor = up;
                tail = new ChunkPos(cursor);
                span = Math.hypot(tail.getStartX() + 8.0 - best.x, tail.getStartZ() + 8.0 - best.z);
            }
            if (span >= TRAIL_DIRECTION_MIN_SPAN) {
                localYaw = this.wrapYaw(Math.toDegrees(Math.atan2(-(best.x - (tail.getStartX() + 8.0)), best.z - (tail.getStartZ() + 8.0))));
            } else if (this.frontierAnchor != null) {
                Vec3d step = best.subtract(this.frontierAnchor);
                if (Math.hypot(step.x, step.z) >= 16.0) {
                    localYaw = this.wrapYaw(Math.toDegrees(Math.atan2(-step.x, step.z)));
                }
            }
            this.trailHeadingYaw = localYaw != null ? this.approachYaw(forwardYaw, localYaw, this.frontierMaxTurn.get()) : forwardYaw;
            this.trailHeadingValid = true;
            this.frontierAnchor = best;
        } else if (!this.trailHeadingValid) {
            return null;
        }

        // The goal lies on the trail's line, path-distance past whichever is farther along it: the
        // frontier, or ourselves when we have overtaken the chunks the server has shown us.
        Vec3d heading = positionInDirection(Vec3d.ZERO, this.trailHeadingYaw, 1.0);
        Vec3d base = this.frontierAnchor != null ? this.frontierAnchor : playerPos;
        Vec3d fromBase = playerPos.subtract(base);
        double playerAhead = fromBase.x * heading.x + fromBase.z * heading.z;
        if (playerAhead > 0.0) {
            base = base.add(heading.multiply(playerAhead));
        }
        Vec3d goal = base.add(heading.multiply(this.pathDistanceActual));
        this.finishCorridor(playerChunk, base, goal);
        return goal;
    }

    /**
     * Completes {@link #corridorKeys} after a walk: the line from {@code base} out to the goal, so the
     * search does not dead-end where the loaded trail ends; a bridge from us to the nearest corridor
     * chunk when a detour has taken us off it, so the search can rejoin it (and only then — a bridge
     * from us to the frontier would let the search cut the trail's bends); and every chunk we have
     * flown, so the search can always start where we are.
     */
    private void finishCorridor(ChunkPos playerChunk, Vec3d base, Vec3d goal) {
        this.rasteriseCorridor(
            (int) Math.floor(base.x / 16.0), (int) Math.floor(base.z / 16.0),
            (int) Math.floor(goal.x / 16.0), (int) Math.floor(goal.z / 16.0)
        );
        if (!this.corridorKeys.contains(playerChunk.toLong())) {
            long nearest = Long.MIN_VALUE;
            int nearestDistance = Integer.MAX_VALUE;
            for (long key : this.corridorKeys) {
                int distance = Math.max(Math.abs(ChunkPos.getPackedX(key) - playerChunk.x), Math.abs(ChunkPos.getPackedZ(key) - playerChunk.z));
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = key;
                }
            }
            if (nearest != Long.MIN_VALUE) {
                this.rasteriseCorridor(playerChunk.x, playerChunk.z, ChunkPos.getPackedX(nearest), ChunkPos.getPackedZ(nearest));
            }
        }
        for (Long key : this.ownTrackChunks.asMap().keySet()) {
            this.corridorKeys.add(key.longValue());
        }
    }

    /**
     * The corridor for the AVERAGE path mode, whose goal is set by the yaw steering rather than by a
     * frontier: the same walk of the trail (so Baritone is still held to the trail chunks) plus the
     * line from us to that goal.
     */
    private void corridorForAverageGoal(Vec3d goal) {
        if (this.mc.player == null) return;
        this.corridorKeys.clear();
        Vec3d playerPos = this.mc.player.getEntityPos();
        ChunkPos playerChunk = this.mc.player.getChunkPos();
        Vec3d forward = positionInDirection(Vec3d.ZERO, this.targetYaw, 1.0);
        this.walkTrail(playerChunk, playerPos, playerPos, forward, Math.cos(Math.toRadians(this.frontierConeAngle.get())));
        this.finishCorridor(playerChunk, playerPos, goal);
    }

    /**
     * Adds every chunk on the straight line between two chunks to {@link #corridorKeys}: the
     * major axis advances one chunk per step, the minor one is rounded to the nearest chunk,
     * so consecutive chunks always touch at least by a corner.
     */
    private void rasteriseCorridor(int fromX, int fromZ, int toX, int toZ) {
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toZ - fromZ));
        if (steps == 0) {
            this.corridorKeys.add(ChunkPos.toLong(fromX, fromZ));
            return;
        }
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = fromX + (int) Math.round((toX - fromX) * t);
            int z = fromZ + (int) Math.round((toZ - fromZ) * t);
            this.corridorKeys.add(ChunkPos.toLong(x, z));
        }
    }

    /**
     * Hands {@link #corridorKeys} to the Baritone fork so its Nether path search cannot leave the
     * trail: at most once a second, and only when the set changed. Called before Baritone is
     * handed a goal, so a freshly created elytra behaviour sees the corridor from its first
     * search; the fork refreshes a live one itself. With {@link #netherCorridor} off, whatever
     * was pushed is cleared once and nothing more is sent.
     */
    private void pushCorridor() {
        if (!this.netherCorridor.get()) {
            this.clearCorridor();
            return;
        }
        if (this.corridorKeys.isEmpty() && !this.corridorPushed) return;
        long now = System.currentTimeMillis();
        if (now - this.lastCorridorPushMs < 1000L) return;
        int hash = this.corridorKeys.hashCode();
        if (this.corridorPushed && hash == this.lastCorridorHash) return;
        if (!this.corridorApi()) return;
        try {
            this.corridorSetMethod.invoke(BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess(), this.corridorKeys.toLongArray(), 1);
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.corridorAvailable = false;
            this.info("corridor: Baritone fork call failed (" + e.getClass().getSimpleName() + "), flying without it");
            return;
        }
        this.lastCorridorPushMs = now;
        this.lastCorridorHash = hash;
        this.corridorPushed = true;
        if (this.debug.get()) {
            this.info("corridor: pushed " + this.corridorKeys.size() + " chunks");
        }
    }

    /** Takes back the corridor Baritone holds from us, if it holds one. */
    private void clearCorridor() {
        if (!this.corridorPushed || !this.corridorAvailable) return;
        this.corridorPushed = false;
        this.lastCorridorHash = 0;
        try {
            this.corridorClearMethod.invoke(BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess());
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.corridorAvailable = false;
            this.info("corridor: Baritone fork call failed (" + e.getClass().getSimpleName() + "), flying without it");
        }
    }

    /**
     * Resolves the fork's {@code setPathCorridor(long[], int)} and {@code clearPathCorridor()}
     * once; upstream Baritone has neither, and then the corridor stays off for the session.
     */
    private boolean corridorApi() {
        if (this.corridorLookedUp) return this.corridorAvailable;
        this.corridorLookedUp = true;
        try {
            Class<?> processClass = BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().getClass();
            this.corridorSetMethod = processClass.getMethod("setPathCorridor", long[].class, int.class);
            this.corridorClearMethod = processClass.getMethod("clearPathCorridor");
            this.corridorAvailable = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.corridorAvailable = false;
            this.info("corridor: Baritone fork API not found, flying without it");
        }
        return this.corridorAvailable;
    }

    private void searchForTrail() {
        if (!this.wasSearching) {
            this.wasSearching = true;
            this.searchPhase = 0.0;
            this.targetYaw = this.committedYaw;
        }

        SearchBehavior behavior = this.searchBehavior.get();
        if (this.followMode == FollowMode.BARITONE && behavior == SearchBehavior.CIRCLE) {
            behavior = SearchBehavior.STRAIGHT;
        }

        switch (behavior) {
            case SWEEP:
                this.searchPhase = this.searchPhase + this.circlingDegPerTick.get();
                double starvedMs = Math.max(0.0, System.currentTimeMillis() - this.lastSteerChunkTime - this.chunkFoundTimeout.get());
                double growWindowMs = Math.max(1000.0, this.trailTimeout.get() - this.chunkFoundTimeout.get());
                double amplitude = this.sweepAngle.get() + (90.0 - this.sweepAngle.get()) * Math.min(1.0, starvedMs / growWindowMs);
                this.targetYaw = this.wrapYaw(this.committedYaw + amplitude * Math.sin(Math.toRadians(this.searchPhase)));
                break;
            case CIRCLE:
                this.targetYaw = this.wrapYaw(this.targetYaw + this.circlingDegPerTick.get());
                break;
            case STRAIGHT:
                this.targetYaw = this.committedYaw;
        }

        if (this.mc.player.age % 100 == 0) {
            this.log(
                "Searching for trail chunks, abandoning trail in "
                    + (this.trailTimeout.get() - (System.currentTimeMillis() - this.lastFoundTrailTime)) / 1000.0
                    + " seconds."
            );
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (!this.started) {
                this.started = true;
                this.onActivate();
                if (!this.isActive()) {
                    return;
                }
            }

            this.recordTravel();

            long sinceLastChunk = System.currentTimeMillis() - this.lastFoundTrailTime;
            if (this.followingTrail && sinceLastChunk > this.trailTimeout.get()) {
                this.resetTrail();
                this.log("Trail timed out, stopping.");
                switch (this.trailEndBehavior.get()) {
                    case DISABLE:
                        this.toggle();
                        break;
                    case FLY_TOWARDS_YAW:
                        this.targetYaw = this.trailEndYaw.get();
                        break;
                    case DISCONNECT:
                        this.mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[TrailFollower] Trail timed out.")));
                }

                if (!this.isActive()) {
                    return;
                }
            }

            if (this.followingTrail && this.steeringMode.get() == SteeringMode.ROUTE_FINDER) {
                this.updateDesiredYawFromRouteFinder();
            }

            if (this.followingTrail && System.currentTimeMillis() - this.lastSteerChunkTime > this.chunkFoundTimeout.get()) {
                this.searchForTrail();
            } else {
                this.wasSearching = false;
                if (this.followingTrail) {
                    this.committedYaw = this.approachYaw(this.committedYaw, this.desiredYaw, this.maxTurnRate.get() / 20.0);
                    this.targetYaw = this.committedYaw;
                }
            }

            switch (this.followMode) {
                case BARITONE:
                    if (this.baritoneSetGoalTicks > 0) {
                        this.baritoneSetGoalTicks--;
                    } else if (this.baritoneSetGoalTicks == 0) {
                        this.baritoneSetGoalTicks = this.baritoneUpdateTicks.get();
                        if (this.mc.world.getRegistryKey().equals(World.NETHER)) {
                            Vec3d baritoneTarget = null;
                            if (this.netherPathMode.get() == NetherPathMode.AVERAGE) {
                                this.corridorKeys.clear();
                                if (!this.trail.isEmpty()) {
                                    baritoneTarget = positionInDirection(this.mc.player.getEntityPos(), this.targetYaw, this.pathDistanceActual);
                                    // The yaw steering sets the goal; the corridor still holds Baritone to the trail chunks.
                                    this.corridorForAverageGoal(baritoneTarget);
                                }
                            } else {
                                // The trail itself decides where Baritone goes: the farthest trail
                                // chunk ahead that is connected to us, extended along that line so
                                // Baritone never "arrives" and lands. Refreshed often because the
                                // frontier moves with every chunk that loads. Before any trail is
                                // found there is nothing to chase yet, so hold the launch heading.
                                this.baritoneSetGoalTicks = Math.min(this.baritoneUpdateTicks.get(), 20);
                                baritoneTarget = this.frontierGoal();
                                if (baritoneTarget == null && !this.followingTrail && !this.trail.isEmpty()) {
                                    baritoneTarget = positionInDirection(this.mc.player.getEntityPos(), this.targetYaw, this.pathDistanceActual);
                                }
                            }

                            // The corridor goes first, so a new elytra behaviour is created with it present.
                            this.pushCorridor();

                            // Every new goal makes Baritone tear its elytra process down and re-plan
                            // from scratch, so it is only handed one when the line to it has turned
                            // (more than 8 degrees) or moved on by a good stretch (200 blocks), or
                            // when Baritone has dropped the goal it had.
                            boolean goalChanged = baritoneTarget != null
                                && (this.lastBaritoneGoal == null
                                    || this.lastBaritoneGoal.distanceTo(baritoneTarget) > 200.0
                                    || Math.abs(angleDifference(Rotations.getYaw(this.lastBaritoneGoal), Rotations.getYaw(baritoneTarget))) > 8.0);
                            if (baritoneTarget != null
                                && (goalChanged
                                    || !BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().isActive())) {
                                this.lastBaritoneGoal = baritoneTarget;
                                BaritoneAPI.getProvider()
                                    .getPrimaryBaritone()
                                    .getCustomGoalProcess()
                                    .setGoalAndPath(new GoalXZ((int) baritoneTarget.x, (int) baritoneTarget.z));
                            }
                        } else {
                            Vec3d targetPos = positionInDirection(this.mc.player.getEntityPos(), this.targetYaw, this.pathDistanceActual);
                            BaritoneAPI.getProvider()
                                .getPrimaryBaritone()
                                .getCustomGoalProcess()
                                .setGoalAndPath(new GoalXZ((int) targetPos.x, (int) targetPos.z));
                        }

                        if (this.autoElytra.get()
                            && BaritoneHelper.hasElytraProcess()
                            && BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null) {
                            BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("elytra");
                        }
                    }
                    break;
                case YAWLOCK:
                    this.mc
                        .player
                        .setYaw(smoothRotation(this.getActualYaw(this.mc.player.getYaw()), this.targetYaw, this.rotateScaling.get()));
            }
        }
    }

    /**
     * Remembers the chunk the player is in and refreshes the smoothed travel
     * heading from the oldest kept position to the current one. The heading
     * only counts once that displacement is long enough to mean something —
     * hovering in place has no direction.
     */
    private void recordTravel() {
        Vec3d playerPos = this.mc.player.getEntityPos();
        this.ownTrackChunks.put(this.mc.player.getChunkPos().toLong(), Boolean.TRUE);

        this.recentPositions.addLast(playerPos);
        while (this.recentPositions.size() > TRAVEL_HISTORY_TICKS) {
            this.recentPositions.pollFirst();
        }

        Vec3d oldest = this.recentPositions.peekFirst();
        double dx = playerPos.x - oldest.x;
        double dz = playerPos.z - oldest.z;
        this.travelYawValid = Math.hypot(dx, dz) >= TRAVEL_MIN_DISTANCE;
        if (this.travelYawValid) {
            // The inverse of positionInDirection, which maps a yaw to (-sin, cos).
            this.travelYaw = this.wrapYaw(Math.toDegrees(Math.atan2(-dx, dz)));
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.debug.get()) {
            Vec3d targetPos = positionInDirection(this.mc.player.getEntityPos(), this.targetYaw, 10.0);
            event.renderer
                .line(
                    this.mc.player.getX(),
                    this.mc.player.getY(),
                    this.mc.player.getZ(),
                    targetPos.x,
                    targetPos.y,
                    targetPos.z,
                    new Color(255, 0, 0)
                );
            if (this.posDebug != null) {
                event.renderer
                    .line(
                        this.mc.player.getX(),
                        this.mc.player.getY(),
                        this.mc.player.getZ(),
                        this.posDebug.x,
                        targetPos.y,
                        this.posDebug.z,
                        new Color(0, 0, 255)
                    );
            }
        }
    }

    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event) {
        if (!event.seenChunk()) {
            if (this.mc.player != null && this.mc.world != null) {
                RegistryKey<World> currentDimension = this.mc.world.getRegistryKey();
                WorldChunk chunk = event.chunk();
                ChunkPos chunkPos = chunk.getPos();
                long chunkLong = chunkPos.toLong();
                if (this.seenChunksCache.getIfPresent(chunkLong) == null) {
                    ChunkPos chunkDelta = new ChunkPos(
                        chunkPos.x - this.mc.player.getChunkPos().x, chunkPos.z - this.mc.player.getChunkPos().z
                    );
                    if (this.oppositeDimension.get()) {
                        if (currentDimension.equals(World.OVERWORLD)) {
                            chunkPos = new ChunkPos(
                                this.mc.player.getChunkPos().x / 8 + chunkDelta.x,
                                this.mc.player.getChunkPos().z / 8 + chunkDelta.z
                            );
                            currentDimension = World.NETHER;
                        } else if (currentDimension.equals(World.NETHER)) {
                            chunkPos = new ChunkPos(
                                this.mc.player.getChunkPos().x * 8 + chunkDelta.x,
                                this.mc.player.getChunkPos().z * 8 + chunkDelta.z
                            );
                            currentDimension = World.OVERWORLD;
                        }
                    }

                    // Every classification feeds the route finder's map, trail or
                    // wall — a wall has to be known as one to be scored as one,
                    // not just silently absent from the map. Keyed by chunkLong,
                    // not chunkPos.toLong(): chunkPos may have just been remapped
                    // into the other dimension's coordinate space above, but
                    // scoreHeading probes from the player's real position, in this
                    // dimension's own space — keying by the remapped value would
                    // write entries the route finder's own lookups can never hit.
                    boolean valid = this.isValidChunk(chunk, chunkPos, currentDimension);
                    this.routeChunkMap.put(chunkLong, valid);

                    if (valid) {
                        this.seenChunksCache.put(chunkLong, (byte) 127);
                        Vec3d pos = chunk.getPos().getCenterAtY(0).toCenterPos();
                        this.posDebug = pos;
                        if (!this.followingTrail) {
                            if (System.currentTimeMillis() - this.lastFoundPossibleTrailTime > this.chunkConsiderationWindow.get() * 1000) {
                                this.possibleTrail.clear();
                            }

                            if (this.hasInitialDirection) {
                                double chunkAngleToPlayer = Rotations.getYaw(pos);
                                double angleDiffFromInitial = angleDifference(this.initialYaw, chunkAngleToPlayer);
                                if (Math.abs(angleDiffFromInitial) > this.forwardConeAngle.get()) {
                                    return;
                                }
                            }

                            this.possibleTrail.add(pos);
                            this.lastFoundPossibleTrailTime = System.currentTimeMillis();
                            if (this.possibleTrail.size() > this.chunksBeforeStarting.get()) {
                                this.log("Trail found, starting to follow.");
                                this.followingTrail = true;
                                this.lastFoundTrailTime = System.currentTimeMillis();
                                this.lastSteerChunkTime = this.lastFoundTrailTime;
                                this.chunksFoundSinceStart = this.possibleTrail.size();
                                if (!this.hasInitialDirection) {
                                    this.initialYaw = this.targetYaw;
                                    this.hasInitialDirection = true;
                                }

                                this.committedYaw = this.wrapYaw(this.targetYaw);
                                this.desiredYaw = this.committedYaw;
                                this.wasSearching = false;
                                this.trail.addAll(this.possibleTrail);
                                this.possibleTrail.clear();
                            }
                        } else {
                            double chunkAngle = Rotations.getYaw(pos);
                            double angleDiff = angleDifference(this.committedYaw, chunkAngle);
                            if (!(Math.abs(angleDiff) > this.maxTrailDeviation.get())) {
                                this.lastFoundTrailTime = System.currentTimeMillis();
                                if (!this.reverseLock.get() || !(Math.abs(angleDiff) > this.reverseLockAngle.get())) {
                                    if (Math.abs(angleDiff) <= 90.0) {
                                        this.lastSteerChunkTime = System.currentTimeMillis();
                                    }

                                    while (this.trail.size() >= this.maxTrailLength.get()) {
                                        this.trail.pollFirst();
                                    }

                                    if (angleDiff > 0.0 && angleDiff < 90.0 && this.directionWeighting.get() == DirectionWeighting.LEFT) {
                                        for (int i = 0; i < this.directionWeightingMultiplier.get() - 1; i++) {
                                            this.trail.pollFirst();
                                            this.trail.add(pos);
                                        }

                                        this.trail.add(pos);
                                    } else if (angleDiff < 0.0 && angleDiff > -90.0 && this.directionWeighting.get() == DirectionWeighting.RIGHT) {
                                        for (int i = 0; i < this.directionWeightingMultiplier.get() - 1; i++) {
                                            this.trail.pollFirst();
                                            this.trail.add(pos);
                                        }

                                        this.trail.add(pos);
                                    } else {
                                        this.trail.add(pos);
                                    }

                                    this.chunksFoundSinceStart++;
                                    // The route finder computes its own heading on tick, straight
                                    // off routeChunkMap — it has no use for this average, and
                                    // running it anyway would fight the route finder's own pick.
                                    if (this.steeringMode.get() == SteeringMode.AVERAGE && !this.trail.isEmpty()) {
                                        Vec3d averagePos = this.calculateForwardWeightedAverage(this.trail);
                                        if (averagePos != null) {
                                            Vec3d positionVec = averagePos.subtract(this.mc.player.getEntityPos()).normalize();
                                            Vec3d targetPos = this.mc.player.getEntityPos().add(positionVec.multiply(10.0));
                                            double calculatedYaw = Rotations.getYaw(targetPos);
                                            double decayedWeight = this.getDecayedInitialWeight();
                                            if (decayedWeight > 0.01) {
                                                this.desiredYaw = this.blendYaw(calculatedYaw, this.initialYaw, decayedWeight);
                                            } else {
                                                this.desiredYaw = this.wrapYaw(calculatedYaw);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isValidChunk(WorldChunk chunk, ChunkPos chunkPos, RegistryKey<World> currentDimension) {
        PaletteNewChunks paletteNewChunks = ModuleManager.getModule(PaletteNewChunks.class);
        boolean is119NewChunk = paletteNewChunks.isNewChunk(chunkPos.x, chunkPos.z, currentDimension);
        boolean is112OldChunk = ModuleManager.getModule(OldChunks.class).isOldChunk(chunkPos.x, chunkPos.z, currentDimension);
        // In the Nether, XaeroPlus calls a chunk new on a single 1.16+ block, and an ore vein
        // spilling over from a fresh neighbour puts one in most old trail chunks; its palette
        // heuristic also misfires on chunks the server ticked before sending. Either one drops a
        // chunk out of a trail that is plainly there on the map. Read the blocks ourselves with the
        // border margin (OldChunkNotifier's rule): a pre-1.16 chunk is trail whatever XaeroPlus says.
        boolean realNether = this.mc.world != null && this.mc.world.getRegistryKey().equals(World.NETHER) && !this.oppositeDimension.get();
        if (realNether && OldChunkNotifier.isPreSixteenNetherChunk(chunk, 4, 3)) return true;
        boolean isHighlighted = is119NewChunk || paletteNewChunks.isInverseNewChunk(chunkPos.x, chunkPos.z, currentDimension);
        return isHighlighted && (!is119NewChunk && !this.only112.get() || is112OldChunk);
    }

    private Vec3d calculateForwardWeightedAverage(ArrayDeque<Vec3d> positions) {
        if (positions.isEmpty()) {
            return null;
        }

        double sumX = 0.0;
        double sumZ = 0.0;
        double totalWeight = 0.0;
        Vec3d playerPos = this.mc.player.getEntityPos();

        for (Vec3d pos : positions) {
            double chunkYaw = Rotations.getYaw(pos);
            double angleDiff = Math.abs(angleDifference(this.committedYaw, chunkYaw));
            if (!(angleDiff > 90.0)) {
                double finalWeight;
                if (this.forwardWeightStrength.get() <= 0.01) {
                    finalWeight = 1.0;
                } else {
                    double alignmentFactor = 1.0 - angleDiff / 180.0;
                    double weight = 1.0 - this.forwardWeightStrength.get() + this.forwardWeightStrength.get() * alignmentFactor;
                    double dist = playerPos.distanceTo(pos);
                    double distWeight = 1.0 / (1.0 + dist / 256.0);
                    finalWeight = weight * (0.5 + 0.5 * distWeight);
                }

                sumX += pos.x * finalWeight;
                sumZ += pos.z * finalWeight;
                totalWeight += finalWeight;
            }
        }

        return totalWeight <= 0.0 ? null : new Vec3d(sumX / totalWeight, 0.0, sumZ / totalWeight);
    }

    /**
     * The rebuilt steering: score a fan of candidate headings and steer at
     * whichever one wins, instead of averaging every trail chunk seen.
     *
     * <p>Runs every tick rather than off chunk arrival — unlike the average,
     * a score is cheap to recompute whole from {@link #routeChunkMap}, so
     * there's no running total to maintain and nothing to get stale between
     * chunks. It only ever writes {@link #desiredYaw}, the same field the
     * average wrote: the turn-rate cap, the reverse lock and the sweep
     * search downstream in {@link #onTick} don't know or care which mode
     * picked the number.
     */
    private void updateDesiredYawFromRouteFinder() {
        Vec3d playerPos = this.mc.player.getEntityPos();
        double half = this.routeFanDegrees.get();
        double step = Math.max(1.0, this.routeFanStepDegrees.get());
        int maxChunks = this.effectiveLookaheadChunks();
        int steps = (int) Math.floor(half / step);

        double bestScore = Double.NEGATIVE_INFINITY;
        double bestYaw = this.committedYaw;
        boolean found = false;

        // Scanned outward from 0 rather than left-to-right across the fan, so a
        // tie keeps the smallest turn rather than always the leftmost candidate
        // — a perfectly symmetric trail (a straight one, dead ahead) now steers
        // straight instead of drifting left every time two headings draw level.
        for (int k = 0; k <= steps; k++) {
            double[] offsets = k == 0 ? new double[]{0.0} : new double[]{k * step, -k * step};

            for (double offset : offsets) {
                double heading = this.wrapYaw(this.committedYaw + offset);
                // The reverse lock holds here too: no heading that far off the
                // direction actually being flown, whatever the map says of it.
                if (this.reverseLock.get()
                    && this.travelYawValid
                    && Math.abs(angleDifference(this.travelYaw, heading)) > this.reverseLockAngle.get()) continue;
                RouteScore score = this.scoreHeading(playerPos, heading, maxChunks);
                if (score.trailChunks() == 0) continue;

                double total = score.trailChunks()
                    - this.routeGapPenalty.get() * score.worstGap()
                    - this.routeLateralPenalty.get() * Math.abs(offset);

                if (total > bestScore) {
                    bestScore = total;
                    bestYaw = heading;
                    found = true;
                }
            }
        }

        // No candidate found any trail at all this tick — leave desiredYaw where
        // it was rather than snapping toward the committed heading itself, which
        // would read as "go straight" and fight the sweep search once it kicks in.
        if (found) this.desiredYaw = this.wrapYaw(bestYaw);
    }

    /**
     * {@link #routeLookaheadChunks} at 0 means "figure it out" — and the
     * client already knows the answer without guessing: the server tells it
     * the view distance on join and on every change ({@code
     * ChunkLoadDistanceS2CPacket}), and vanilla keeps that clamped against the
     * client's own render-distance option in exactly the shape wanted here —
     * {@code GameOptions.getClampedViewDistance()} already is
     * {@code min(received, option)}, with the option alone as a sane answer
     * before anything has been received. Scoring further than this is scoring
     * chunks that can never be classified — there's nothing out there to see.
     */
    private int effectiveLookaheadChunks() {
        int configured = this.routeLookaheadChunks.get();
        return configured > 0 ? configured : Math.max(1, this.mc.options.getClampedViewDistance());
    }

    /**
     * Walks one candidate heading outward in steps of 16 blocks, counting how
     * much of it lands on confirmed trail and the worst run of non-trail
     * *between two trail chunks* it has to cross to get there. Stops early
     * once a gap runs past {@link #routeMaxGap} — the heading is disqualified
     * past that point, so there is nothing left worth counting further out.
     *
     * <p>Only counted once it's closed by a trail chunk further out: every
     * heading eventually runs out of loaded chunks and ends in unknown, and
     * that final, never-reopened run isn't a gap in a trail — it's just where
     * the world stops being loaded yet. Counting it would give every heading
     * the same worst-gap the moment {@link #routeLookaheadChunks} reaches past
     * whatever's actually loaded, which is the ordinary case in flight; the
     * setting would then be scoring nothing.
     *
     * <p>An unclassified chunk — one that simply hasn't loaded yet — still
     * counts exactly like a confirmed wall for {@code trailChunks}: a heading
     * can only be credited with trail it has actually seen.
     */
    private RouteScore scoreHeading(Vec3d playerPos, double headingYaw, int maxChunks) {
        int trailChunks = 0;
        int currentGap = 0;
        int worstGap = 0;

        for (int i = 1; i <= maxChunks; i++) {
            Vec3d probe = positionInDirection(playerPos, headingYaw, i * 16.0);
            long key = new ChunkPos((int) Math.floor(probe.x / 16.0), (int) Math.floor(probe.z / 16.0)).toLong();
            Boolean classified = this.routeChunkMap.getIfPresent(key);
            // The chunks behind us are the trail we already followed, and
            // behind us everything is loaded and classified while ahead it
            // isn't yet — scoring them is what pulled the heading around.
            boolean ownTrack = this.ownTrackChunks.getIfPresent(key) != null;

            if (Boolean.TRUE.equals(classified) && !ownTrack) {
                // The gap this chunk just closed only counts here — reopened by
                // trail rather than left hanging at the edge of loaded terrain.
                if (currentGap > 0) worstGap = Math.max(worstGap, currentGap);
                trailChunks++;
                currentGap = 0;
            } else {
                currentGap++;
                if (currentGap > this.routeMaxGap.get()) break;
            }
        }

        return new RouteScore(trailChunks, worstGap);
    }

    private record RouteScore(int trailChunks, int worstGap) {
    }

    private double getDecayedInitialWeight() {
        if (this.hasInitialDirection && !(this.startDirectionWeighting.get() <= 0.0)) {
            int decayChunks = this.maxTrailLength.get() * 2;
            double decayFactor = Math.max(0.0, 1.0 - (double) this.chunksFoundSinceStart / decayChunks);
            return this.startDirectionWeighting.get() * decayFactor;
        } else {
            return 0.0;
        }
    }

    private float getActualYaw(float yaw) {
        return (yaw % 360.0F + 360.0F) % 360.0F;
    }

    private double wrapYaw(double yaw) {
        return (yaw % 360.0 + 360.0) % 360.0;
    }

    private double approachYaw(double current, double target, double maxStep) {
        double diff = angleDifference(target, current);
        return Math.abs(diff) <= maxStep ? this.wrapYaw(target) : this.wrapYaw(current + Math.copySign(maxStep, diff));
    }

    private double blendYaw(double yaw1, double yaw2, double weight) {
        yaw1 = (yaw1 % 360.0 + 360.0) % 360.0;
        yaw2 = (yaw2 % 360.0 + 360.0) % 360.0;
        double diff = yaw2 - yaw1;
        if (diff > 180.0) {
            diff -= 360.0;
        }

        if (diff < -180.0) {
            diff += 360.0;
        }

        double result = yaw1 + diff * weight;
        return (result % 360.0 + 360.0) % 360.0;
    }

    private void log(String message) {
        this.info(message);
        if (!this.webhookLink.get().isEmpty()) {
            sendWebhook(this.webhookLink.get(), "TrailFollower", message, null, this.mc.player.getGameProfile().name());
        }
    }

    public enum DirectionWeighting {
        LEFT,
        NONE,
        RIGHT;
    }

    public enum FollowMode {
        AUTO,
        BARITONE,
        YAWLOCK;
    }

    public enum NetherPathMode {
        AVERAGE,
        OTHER;
    }

    public enum OverworldFlightMode {
        ROCKETS,
        PITCH40,
        OTHER;
    }

    public enum SteeringMode {
        ROUTE_FINDER,
        AVERAGE;
    }

    public enum SearchBehavior {
        SWEEP,
        CIRCLE,
        STRAIGHT;
    }

    public enum TrailEndBehavior {
        DISABLE,
        FLY_TOWARDS_YAW,
        DISCONNECT;
    }
}
