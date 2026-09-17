package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.modules.regear.util.XaeroWaypointManager;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.registry.RegistryKey;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.mods.SupportMods;
import xaero.map.region.MapRegion;
import xaero.map.region.MapTileChunk;
import xaero.map.world.MapWorld;
import xaeroplus.feature.extensions.SeenChunksTrackingMapTileChunk;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.feature.waypoint.WaypointAPI;
import xaeroplus.settings.Settings;
import xaeroplus.util.ChunkUtils;

public class WaypointFollower extends Module {

    private static final String[] BOT_MANAGED_PREFIXES = {
        "AreaLoader",
        "StashFinder",
        "VanityESP",
        "TrailFollower"
    };

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgFlight = this.settings.createGroup("Flight");
    private final SettingGroup sgControl = this.settings.createGroup("Control");

    private final List<BlockPos> waypointsToFollow = new ArrayList<>();
    private boolean isInNether = false;
    private boolean isInEnd = false;
    private int currentWaypointIndex = 0;
    private BlockPos currentBaritoneTarget = null;
    /**
     * Attempts made at re-giving {@link #currentBaritoneTarget}; reset whenever the target changes, and
     * whenever the bot has moved since the last one.
     */
    private int baritoneRestartAttempts = 0;
    /** Where the bot stood when it last re-gave the goal: see {@link #BARITONE_RESTART_MOVED_BLOCKS}. */
    private BlockPos baritoneRestartPos = null;
    /** Ticks left before another re-give attempt at {@link #currentBaritoneTarget} may fire. */
    private int baritoneRestartCooldown = 0;
    private boolean isPaused = false;
    private int waypointsCompletedThisSession = 0;
    private double totalDistanceTraveled = 0.0;
    private Vec3d lastPlayerPos = null;
    private FlightMode activeFlightMode = FlightMode.None;
    private RocketFly rocketFly = null;
    private Pitch40 pitch40Util = null;
    private ElytraRecast elytraRecast = null;
    private int fireworkCooldown = 0;
    private int lastKnownWaypointCount = 0;
    private int reloadCheckTimer = 0;
    private int startupDelayTicks = 0;
    private boolean startupRecastTriggered = false;

    /**
     * Whether this module is the one that switched ElytraRecast on for the
     * startup. The startup always meant the recast to be temporary — it sets
     * an "auto-disable" flag on it — but no ElytraRecast this addon ever
     * shipped has had that setting, so the null-guard swallowed the intent
     * and the recast stayed armed for the whole flight, firing recovery
     * rockets mid-cruise. The follower now keeps the receipt and switches it
     * back off itself once the flight is established.
     */
    private boolean recastEnabledForStartup = false;
    private int glidingTicksAfterStartup = 0;
    private boolean baritoneActivatedAfterStartup = false;
    private int waypointLoadRetries = 0;

    /**
     * Volatile because it is the publication barrier for the list: the loader
     * thread fills {@code waypointsToFollow} and then sets this, and the render
     * thread reads it before touching the list through the HUD getters.
     */
    private volatile boolean waypointsFullyLoaded = false;
    private String lastDimensionKey = "";
    private Thread waypointLoadThread = null;
    private volatile boolean isDeactivating = false;
    private volatile boolean areaLoaderSteering = false;

    // ---- Route recording ----
    private double lastSeedOwX = Double.NaN;
    private double lastSeedOwZ = Double.NaN;
    private boolean seedSaveDirty;
    private long lastSeedSaveMs;

    /** How long recorded breadcrumbs may wait in memory before a disk save. */
    private static final long SEED_SAVE_MS = 15_000L;

    /**
     * Ticks between two attempts at re-giving Baritone the goal it dropped, and how many of those
     * attempts a single waypoint gets before this gives up on it — a spot Baritone cannot actually
     * land near (walled in, no safe block anywhere close) would otherwise be retried forever.
     * [proposé], to calibrate once this has been seen firing.
     */
    private static final int BARITONE_RESTART_DELAY_TICKS = 100;
    private static final int BARITONE_RESTART_MAX_ATTEMPTS = 3;
    /**
     * How far the bot has to be from where it last re-gave the goal for the attempts to start over:
     * somewhere new is a new try. The user's rule, after a bot whose three attempts were all spent in ten
     * seconds fell 38 blocks into open ground and stood there. The same distance Baritone's takeoff uses
     * for a new spot.
     */
    private static final int BARITONE_RESTART_MOVED_BLOCKS = 8;

    // ---- Read by the HUDs. Computed on the tick, never per frame, so three
    // elements asking the same questions cost one answer. ----
    private double smoothedSpeed;
    private int rocketCount;
    private int rocketScanTimer;
    private long activatedAtMs;
    private long lastCompletedAtMs;
    private boolean routeEndPending;

    private final Setting<String> waypointPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("waypoint-prefix")
        .description("Prefix for waypoints that should be followed (e.g., 'Hunt_')")
        .defaultValue("Hunt_")
        .build());

    private final Setting<FollowMode> followMode = sgGeneral.add(new EnumSetting.Builder<FollowMode>()
        .name("follow-mode")
        .description("How to follow waypoints: Closest goes to nearest waypoint, Numerical follows in order")
        .defaultValue(FollowMode.Numerical)
        .build());

    private final Setting<RouteEndAction> routeEnd = sgGeneral.add(new EnumSetting.Builder<RouteEndAction>()
        .name("route-end")
        .description("What to do once the last waypoint has been reached, wherever that happens, in the air or on the ground: OFF_MODULES switches this module off, DISCONNECT leaves the server. A waypoint removed with the skip button does not count as reaching the end.")
        .defaultValue(RouteEndAction.OFF_MODULES)
        .build());

    private final Setting<Double> reachDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach-distance")
        .description("Horizontal distance to consider a waypoint reached (ignores Y level)")
        .defaultValue(20.0)
        .range(1.0, 50.0)
        .sliderRange(1.0, 100.0)
        .build());

    private final Setting<Boolean> showStatistics = sgGeneral.add(new BoolSetting.Builder()
        .name("show-statistics")
        .description("Show session statistics in info string")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> showChatMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("show-chat-messages")
        .description("Show chat notifications")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> completionSound = sgGeneral.add(new BoolSetting.Builder()
        .name("completion-sound")
        .description("Play a sound when all waypoints have been reached")
        .defaultValue(true)
        .build());

    private final Setting<Keybind> addWaypointKey = sgControl.add(new KeybindSetting.Builder()
        .name("add-waypoint-key")
        .description("Key to add a hunt waypoint at mouse position on Xaero's map")
        .defaultValue(Keybind.fromKey(82))
        .build());

    public final Setting<Keybind> clearWaypoints = sgControl.add(new KeybindSetting.Builder()
        .name("clear-waypoints")
        .description("Clears every hunt waypoint and every route mark, so the next hunt starts from a clean map.")
        .defaultValue(Keybind.none())
        .action(this::clearAllFollowWaypointsAction)
        .build());

    public final Setting<Keybind> skipWaypointKey = sgControl.add(new KeybindSetting.Builder()
        .name("skip-waypoint")
        .description("Skip to next waypoint in the list")
        .defaultValue(Keybind.none())
        .action(this::skipCurrentWaypointAction)
        .build());

    public final Setting<Keybind> pauseResumeKey = sgControl.add(new KeybindSetting.Builder()
        .name("pause-resume")
        .description("Pause or resume waypoint following")
        .defaultValue(Keybind.none())
        .action(this::togglePause)
        .build());

    private final Setting<Integer> seedSpacing = sgControl.add(new IntSetting.Builder()
        .name("seed-spacing")
        .description("Overworld blocks between two seeded waypoints. The game only guarantees a floor -- two overworld portals closer than 128 blocks link to each other, so no skip interval is ever under that. Measure the gap between two portal patches of one journey and set it here: travellers keep their rhythm.")
        .defaultValue(2000)
        .range(128, 16000)
        .sliderRange(500, 8000)
        .build());

    private final Setting<SeedMode> seedMode = sgControl.add(new EnumSetting.Builder<SeedMode>()
        .name("seed-mode")
        .description("Where seed-route drops its marks along the corridor it walks. Spacing puts one every seed-spacing blocks, wherever the walk happens to be. PortalPatches puts one only where the map holds a full square of portal-radius chunks -- the shape a traveller leaves standing still by a portal, which a flight swath never fills.")
        .defaultValue(SeedMode.PortalPatches)
        .build());

    private final Setting<Integer> portalRadius = sgControl.add(new IntSetting.Builder()
        .name("portal-radius")
        .description("Side, in chunks, of the square of mapped chunks that says a portal could stand there. XaeroPlus's own portal detection uses 15; a 1.12 ribbon flown at speed is about 24 chunks wide, so 15 swallows the whole ribbon and 21 picks out the bulges. Measured on the trail of 2026-08-31: 15 gives one patch for the whole ribbon, 21 gives one every 31 to 39 chunks, 25 gives almost none. Raise it if you get marks all along, lower it if you get none.")
        .defaultValue(21)
        .range(7, 41)
        .sliderRange(11, 31)
        .visible(() -> this.seedMode.get() == SeedMode.PortalPatches)
        .build());

    private final Setting<String> routePrefix = sgControl.add(new StringSetting.Builder()
        .name("route-prefix")
        .description("Prefix of the control waypoints seed-route reads. Put Route_1 where the trail starts, Route_2 at the first bend, and so on to the end: the chain is filled straight from one to the next, so every bend of the ribbon deserves its own mark.")
        .defaultValue("Route_")
        .build());

    private final Setting<Keybind> addRouteKey = sgControl.add(new KeybindSetting.Builder()
        .name("add-route-key")
        .description("Key to drop a route-prefix control mark at the mouse position on Xaero's map -- the marks seed-route walks. Same gesture as add-waypoint-key, different prefix, numbered Route_1, Route_2... in the order you drop them.")
        .defaultValue(Keybind.none())
        .build());

    private final Setting<Keybind> seedRouteKey = sgControl.add(new KeybindSetting.Builder()
        .name("seed-route")
        .description("Fills a hunt chain along the control marks on the map, map open or closed, module on or off. Reads every route-prefix mark in numeric order, loads the map regions along each pair from the disk, then walks from one mark to the next through the chunks the map actually holds, laying a hunt waypoint every seed-spacing blocks of that walk plus one on each end. Mark the ribbon's start, its bends and its end, press once, wait for the chat line. The control marks stay where you put them. Refilling a partly covered path numbers the gap-fillers after the existing chain; Closest mode flies that right anyway.")
        .defaultValue(Keybind.none())
        .action(this::seedRouteFromMap)
        .build());

    private final Setting<Boolean> recordRoute = sgControl.add(new BoolSetting.Builder()
        .name("nether-record-route")
        .description("A recording session. While this is on the module never flies: in the Nether it drops a hunt waypoint every seed-spacing blocks along whatever you fly yourself, curves included, in overworld coordinates; above ground it keeps its hands off, so surfacing through a portal to look at a suspect spot does not end the trace -- dive again and it carries on. Untick the box when the trail is done: the follower then flies the overworld exactly above the route you traced. The chain only ever grows along your own flight; nothing is read from the map and nothing is placed anywhere else on it.")
        .defaultValue(false)
        .build());

    private final Setting<NetherFlightMode> netherFlightMode = sgFlight.add(new EnumSetting.Builder<NetherFlightMode>()
        .name("nether-flight-mode")
        .description("Flight mode to use in the Nether")
        .defaultValue(NetherFlightMode.Baritone)
        .build());

    private final Setting<FlightMode> overworldFlightMode = sgFlight.add(new EnumSetting.Builder<FlightMode>()
        .name("overworld-flight-mode")
        .description("Flight mode to use in the Overworld")
        .defaultValue(FlightMode.Pitch40)
        .build());

    private final Setting<FlightMode> endFlightMode = sgFlight.add(new EnumSetting.Builder<FlightMode>()
        .name("end-flight-mode")
        .description("Flight mode to use in the End")
        .defaultValue(FlightMode.Pitch40)
        .build());

    private final Setting<Boolean> snapRotation = sgFlight.add(new BoolSetting.Builder()
        .name("snap-rotation")
        .description("Instantly snap yaw to the exact heading toward the waypoint.")
        .defaultValue(false)
        .build());

    private final Setting<Double> rotationSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Fraction of the yaw error corrected each tick.")
        .defaultValue(0.1)
        .range(0.01, 1.0)
        .sliderRange(0.01, 0.5)
        .visible(() -> !snapRotation.get())
        .build());

    private final Setting<Double> yawDeadzone = sgFlight.add(new DoubleSetting.Builder()
        .name("yaw-deadzone")
        .description("Yaw error (degrees) below which no correction is applied.")
        .defaultValue(0.5)
        .range(0.0, 10.0)
        .sliderRange(0.0, 5.0)
        .build());

    private final Setting<Double> netherReachMultiplier = sgFlight.add(new DoubleSetting.Builder()
        .name("nether-reach-multiplier")
        .description("Multiplier for reach distance in the Nether.")
        .defaultValue(5.0)
        .range(1.0, 10.0)
        .sliderRange(1.0, 10.0)
        .build());

    private final Setting<Boolean> autoStartFlight = sgFlight.add(new BoolSetting.Builder()
        .name("auto-start-flight")
        .description("Automatically trigger ElytraRecast to start flying after relog/activation.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> startupDelay = sgFlight.add(new IntSetting.Builder()
        .name("startup-delay")
        .description("Ticks to wait after activation before starting flight.")
        .defaultValue(5)
        .range(0, 40)
        .sliderRange(0, 40)
        .visible(autoStartFlight::get)
        .build());

    public WaypointFollower() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "waypoint-follower", "Advanced waypoint following system with multi-dimensional flight support");
    }

    @Override
    public void onActivate() {
        if (mc.world == null || mc.player == null) return;

        this.rocketFly = Modules.get().get(RocketFly.class);
        this.pitch40Util = Modules.get().get(Pitch40.class);
        this.elytraRecast = Modules.get().get(ElytraRecast.class);

        try {
            if (!Settings.REGISTRY.owAutoWaypointDimension.get()) {
                Settings.REGISTRY.owAutoWaypointDimension.setValue(true);
                if (showChatMessages.get()) info("Enabled XaeroPlus 'Prefer Overworld Waypoints'");
            }
        } catch (Exception ignored) {}

        this.isDeactivating = false;
        AreaLoader areaLoader = Modules.get().get(AreaLoader.class);
        this.areaLoaderSteering = areaLoader != null && areaLoader.isActive();
        this.isInNether = mc.world.getRegistryKey() == World.NETHER;
        this.isInEnd = mc.world.getRegistryKey() == World.END;
        this.isPaused = false;
        this.waypointsCompletedThisSession = 0;
        this.totalDistanceTraveled = 0.0;
        this.lastPlayerPos = mc.player.getEntityPos();
        this.waypointsToFollow.clear();
        this.currentWaypointIndex = 0;
        this.startupDelayTicks = 0;
        this.recentRemovals.clear();
        this.startupRecastTriggered = false;
        this.recastEnabledForStartup = false;
        this.glidingTicksAfterStartup = 0;
        this.baritoneActivatedAfterStartup = false;
        this.waypointLoadRetries = 0;
        this.waypointsFullyLoaded = false;
        this.lastDimensionKey = getCurrentDimensionKey();
        this.lastSeedOwX = Double.NaN;
        this.lastSeedOwZ = Double.NaN;
        this.smoothedSpeed = 0.0;
        this.rocketCount = 0;
        this.rocketScanTimer = 0;
        this.activatedAtMs = System.currentTimeMillis();
        this.lastCompletedAtMs = 0L;
        this.routeEndPending = false;
        this.startAsyncWaypointLoad();
        this.activeFlightMode = FlightMode.None;
    }

    @Override
    public void onDeactivate() {
        this.flushSeedSave();
        this.isDeactivating = true;
        this.areaLoaderSteering = false;
        stopFlightMode();
        stopBaritone();
        this.releaseMovementKeys();
        RotationUtils.getInstance().clearRotations();

        if (waypointLoadThread != null && waypointLoadThread.isAlive()) {
            waypointLoadThread.interrupt();
        }

        if (rocketFly != null && rocketFly.isActive()) rocketFly.toggle();
        if (pitch40Util != null && pitch40Util.isActive()) pitch40Util.toggle();
        if (elytraRecast != null && elytraRecast.isActive()) elytraRecast.toggle();

        this.waypointsToFollow.clear();
        this.currentBaritoneTarget = null;
        this.activeFlightMode = FlightMode.None;
        this.fireworkCooldown = 0;
        this.isPaused = false;
        this.waypointsFullyLoaded = false;
        this.startupRecastTriggered = false;
        this.recastEnabledForStartup = false;
        this.baritoneActivatedAfterStartup = false;

        if (showChatMessages.get() && waypointsCompletedThisSession > 0) {
            info(String.format("Session completed: %d waypoints, %.1f blocks traveled", waypointsCompletedThisSession, totalDistanceTraveled));
        }
    }

    private void releaseMovementKeys() {
        Utils.setPressed(mc.options.forwardKey, false);
        Utils.setPressed(mc.options.backKey, false);
        Utils.setPressed(mc.options.leftKey, false);
        Utils.setPressed(mc.options.rightKey, false);
        Utils.setPressed(mc.options.jumpKey, false);
        Utils.setPressed(mc.options.sneakKey, false);
    }

    public void setAreaLoaderSteering(boolean active) {
        this.areaLoaderSteering = active;
    }

    public boolean isAreaLoaderSteering() {
        return this.areaLoaderSteering;
    }

    public boolean isManagingPitch40() {
        return this.isActive() && this.activeFlightMode == FlightMode.Pitch40;
    }

    public boolean isManagingRocketFly() {
        return this.isActive() && this.activeFlightMode == FlightMode.RocketFly;
    }

    public void releaseAreaLoaderSteering() {
        this.areaLoaderSteering = false;
        this.purgeBotManagedWaypoints();
        this.stopBaritone();
        this.releaseMovementKeys();
        RotationUtils.getInstance().clearRotations();
    }

    public void untrackWaypointsByNamePrefix(String namePrefix) {
        if (namePrefix == null || namePrefix.isEmpty()) {
            return;
        }

        Set<BlockPos> botPositions = this.collectBotManagedPositions(namePrefix);
        if (this.currentBaritoneTarget != null && botPositions.contains(this.currentBaritoneTarget)) {
            this.stopBaritone();
        }

        this.waypointsToFollow.removeIf(botPositions::contains);
        this.pruneUntrackableWaypoints();
    }

    public void purgeBotManagedWaypoints() {
        Set<BlockPos> botPositions = this.collectAllBotManagedPositions();
        if (this.currentBaritoneTarget != null && botPositions.contains(this.currentBaritoneTarget)) {
            this.stopBaritone();
        }

        this.waypointsToFollow.removeIf(botPositions::contains);
        this.pruneUntrackableWaypoints();
    }

    private Set<BlockPos> collectAllBotManagedPositions() {
        Set<BlockPos> botPositions = new HashSet<>();

        for (String prefix : BOT_MANAGED_PREFIXES) {
            botPositions.addAll(this.collectBotManagedPositions(prefix));
        }

        return botPositions;
    }

    private Set<BlockPos> collectBotManagedPositions(String prefix) {
        Set<BlockPos> botPositions = new HashSet<>();
        botPositions.addAll(XaeroWaypointManager.getTrackedPositions(prefix));

        try {
            MinimapWorld waypointWorld = this.getWaypointWorld();
            if (waypointWorld != null) {
                WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
                if (currentSet != null) {
                    for (Waypoint wp : currentSet.getWaypoints()) {
                        if (this.isBotManagedWaypointName(wp.getName(), prefix)) {
                            botPositions.add(new BlockPos(wp.getX(), wp.getY(), wp.getZ()));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return botPositions;
    }

    private boolean isBotManagedWaypointName(String name, String prefix) {
        return name != null && (name.startsWith(prefix) || name.startsWith(prefix + " - ") || name.startsWith(prefix + "-"));
    }

    private boolean isBotManagedWaypoint(String name) {
        if (name == null) {
            return false;
        }

        for (String prefix : BOT_MANAGED_PREFIXES) {
            if (this.isBotManagedWaypointName(name, prefix)) {
                return true;
            }
        }

        return false;
    }

    private boolean shouldFollowWaypoint(String name) {
        if (name == null || this.isBotManagedWaypoint(name)) {
            return false;
        }

        return name.startsWith(this.waypointPrefix.get());
    }

    private void pruneUntrackableWaypoints() {
        if (this.waypointsToFollow.isEmpty()) {
            return;
        }

        this.waypointsToFollow.removeIf(this.collectAllBotManagedPositions()::contains);

        try {
            Set<BlockPos> valid = new HashSet<>();
            MinimapWorld waypointWorld = this.getWaypointWorld();
            if (waypointWorld == null) {
                return;
            }

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) {
                return;
            }

            for (Waypoint wp : currentSet.getWaypoints()) {
                if (this.shouldFollowWaypoint(wp.getName()) && !wp.isDisabled()) {
                    valid.add(new BlockPos(wp.getX(), wp.getY(), wp.getZ()));
                }
            }

            this.waypointsToFollow.removeIf(pos -> !valid.contains(pos));
        } catch (Exception ignored) {}
    }

    private void stopBaritone() {
        BaritoneHelper.stopAllPathing();
        this.currentBaritoneTarget = null;
        this.baritoneRestartAttempts = 0;
        this.baritoneRestartCooldown = 0;
        this.baritoneRestartPos = null;
    }

    /** Whether the bot is more than {@link #BARITONE_RESTART_MOVED_BLOCKS} from where it last re-gave the goal. */
    private boolean movedSinceLastRestart() {
        return this.baritoneRestartPos != null && mc.player != null
            && mc.player.getBlockPos().getSquaredDistance(this.baritoneRestartPos)
                > BARITONE_RESTART_MOVED_BLOCKS * BARITONE_RESTART_MOVED_BLOCKS;
    }

    private void clearAllFollowWaypointsAction() {
        if (isTextFieldFocusedInXaeroMap()) return;
        clearAllFollowWaypoints();
        waypointsToFollow.clear();
        currentWaypointIndex = 0;
        if (showChatMessages.get()) info("Cleared all hunt waypoints");
    }

    private void skipCurrentWaypointAction() {
        if (isTextFieldFocusedInXaeroMap()) return;
        if (isActive() && !waypointsToFollow.isEmpty()) {
            BlockPos current = getNextWaypoint();
            if (current != null) {
                removeCurrentWaypoint(current, WaypointFollower.Outcome.SKIPPED);
                if (showChatMessages.get()) info("Skipped waypoint. " + waypointsToFollow.size() + " remaining.");
            }
        }
    }

    private void togglePause() {
        if (isTextFieldFocusedInXaeroMap()) return;
        if (isActive()) {
            isPaused = !isPaused;
            if (isPaused) {
                stopFlightMode();
                stopBaritone();
                if (showChatMessages.get()) info("Waypoint following paused");
            } else if (showChatMessages.get()) {
                info("Waypoint following resumed");
            }
        }
    }

    public String getWaypointPrefix() {
        return this.waypointPrefix.get();
    }

    public void clearTrackedWaypoints() {
        this.waypointsToFollow.clear();
        this.currentWaypointIndex = 0;
    }

    public int skipCurrentWaypoint() {
        if (this.waypointsToFollow.isEmpty()) return -1;
        BlockPos current = this.getNextWaypoint();
        if (current != null) {
            this.removeCurrentWaypoint(current, WaypointFollower.Outcome.SKIPPED);
            return this.waypointsToFollow.size();
        }
        return -1;
    }

    public boolean isPaused() {
        return this.isPaused;
    }

    public void pause() {
        if (!this.isPaused) {
            this.isPaused = true;
            this.stopFlightMode();
            this.stopBaritone();
        }
    }

    public void resume() {
        this.isPaused = false;
    }

    public boolean shouldShowChatMessages() {
        return this.showChatMessages.get();
    }

    public int getWaypointCount() {
        return this.getNextWaypointNumber();
    }

    public int getAddWaypointKeyCode() {
        return this.addWaypointKey.get().getValue();
    }

    public int getAddRouteKeyCode() {
        return this.addRouteKey.get().getValue();
    }

    public int getSeedRouteKeyCode() {
        return this.seedRouteKey.get().getValue();
    }

    public String getRoutePrefix() {
        return this.routePrefix.get();
    }

    /** Next free route number, starting at 1: the walking order of seed-route. */
    public int getNextRouteNumber() {
        try {
            MinimapWorld waypointWorld = this.getWaypointWorld();
            if (waypointWorld == null) return 1;

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) return 1;

            String prefix = this.routePrefix.get();
            int highest = 0;

            for (Waypoint waypoint : currentSet.getWaypoints()) {
                String name = waypoint.getName();
                if (name == null || !name.startsWith(prefix)) continue;

                int n = this.extractOrderNumber(name, prefix);
                if (n != Integer.MAX_VALUE && n > highest) highest = n;
            }

            return highest + 1;
        } catch (Exception e) {
            return 1;
        }
    }

    public int getNextWaypointNumber() {
        try {
            MinimapWorld waypointWorld = this.getWaypointWorld();
            if (waypointWorld == null) return 0;

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) return 0;

            String prefix = this.waypointPrefix.get();
            int highestNumber = -1;

            for (Waypoint waypoint : currentSet.getWaypoints()) {
                if (this.shouldFollowWaypoint(waypoint.getName())) {
                    try {
                        String suffix = waypoint.getName().substring(prefix.length());
                        int num = Integer.parseInt(suffix);
                        if (num > highestNumber) highestNumber = num;
                    } catch (NumberFormatException ignored) {}
                }
            }
            return highestNumber + 1;
        } catch (Exception e) {
            return this.waypointsToFollow.size();
        }
    }

    public int getTotalHuntWaypointCount() {
        try {
            MinimapWorld waypointWorld = this.getWaypointWorld();
            if (waypointWorld == null) return 0;

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) return 0;

            String prefix = this.waypointPrefix.get();
            int count = 0;

            for (Waypoint waypoint : currentSet.getWaypoints()) {
                if (this.shouldFollowWaypoint(waypoint.getName())) count++;
            }
            return count;
        } catch (Exception e) {
            return this.waypointsToFollow.size();
        }
    }

    public void addWaypointToTrack(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!this.waypointsToFollow.contains(pos)) {
            this.waypointsToFollow.add(pos);
            if (this.isActive() && this.showChatMessages.get()) {
                this.info("Added waypoint to tracking list");
            }
        }
    }

    private boolean isInNether(World world) {
        return world != null && world.getRegistryKey() == World.NETHER;
    }

    private boolean isInEnd(World world) {
        return world != null && world.getRegistryKey() == World.END;
    }

    private String getCurrentDimensionKey() {
        return mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "unknown";
    }

    private boolean isPreferOverworldWaypointsEnabled() {
        try {
            return Settings.REGISTRY.owAutoWaypointDimension.get();
        } catch (Exception e) {
            return false;
        }
    }

    private MinimapWorld getWaypointWorld() {
        try {
            MinimapSession session = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) {
                return WaypointAPI.getMinimapWorld(mc.world != null ? mc.world.getRegistryKey() : World.OVERWORLD);
            }

            if (isPreferOverworldWaypointsEnabled()) {
                return resolveOverworldWaypointWorld();
            }
            if (isInEnd) {
                MinimapWorld endWorld = WaypointAPI.getMinimapWorld(World.END);
                if (endWorld != null) return endWorld;
            }

            MinimapWorld current = session.getWorldManager().getCurrentWorld();
            return current != null ? current : WaypointAPI.getMinimapWorld(mc.world.getRegistryKey());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The overworld waypoint world, and never a new one.
     *
     * <p>XaeroPlus's lookup builds and registers a fresh world when the container
     * it searches holds no overworld -- which is the case the moment "Nether" is
     * picked in the map's dimension menu -- and that is how a second "Overworld"
     * turned up in the list, with marks split between the two. The world Xaero
     * would pick on its own is the one every waypoint already lives in.
     */
    public static MinimapWorld resolveOverworldWaypointWorld() {
        try {
            MinimapSession session = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) return null;

            MinimapWorldManager manager = session.getWorldManager();
            if (manager == null) return null;

            MinimapWorld current = manager.getCurrentWorld();
            if (current != null && current.getDimId() == World.OVERWORLD) return current;

            MinimapWorld auto = manager.getAutoWorld();
            if (auto != null && auto.getDimId() == World.OVERWORLD) return auto;

            MinimapWorldRootContainer root = manager.getCurrentRootContainer();
            if (root != null) {
                for (MinimapWorld world : root.getWorlds()) {
                    if (world.getDimId() == World.OVERWORLD) return world;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private int toNetherCoord(int coord) {
        return this.isPreferOverworldWaypointsEnabled() ? coord / 8 : coord;
    }

    private double getDimensionDivision() {
        try {
            MinimapSession session = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) return 1.0;

            MinimapWorld currentWorld = session.getWorldManager().getCurrentWorld();
            return currentWorld == null ? 1.0 : session.getDimensionHelper().getDimensionDivision(currentWorld);
        } catch (Exception e) {
            return 1.0;
        }
    }

    private int toPlayerDimensionCoord(int waypointCoord) {
        return (int) (waypointCoord * getDimensionDivision());
    }

    private void startAsyncWaypointLoad() {
        if (this.waypointLoadThread != null && this.waypointLoadThread.isAlive()) {
            this.waypointLoadThread.interrupt();
        }

        this.waypointLoadThread = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    try {
                        SupportMods.xaeroMinimap.requestWaypointsRefresh();
                        Thread.sleep(250L);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Exception ignored) {}
                }

                for (int attempt = 0; attempt < 10 && !this.waypointsFullyLoaded; attempt++) {
                    Thread.sleep(300L);
                    this.attemptWaypointLoad();
                }

                if (!this.waypointsFullyLoaded && this.showChatMessages.get()) {
                    this.mc.execute(() -> this.warning("Waypoints may not have loaded - try opening world map manually"));
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                if (this.showChatMessages.get()) {
                    this.mc.execute(() -> this.error("Waypoint loading error: " + e.getMessage()));
                }
            }
        }, "WaypointFollower-Loader");
        this.waypointLoadThread.setDaemon(true);
        this.waypointLoadThread.start();
    }

    private int extractOrderNumber(String waypointName, String prefix) {
        try {
            return Integer.parseInt(waypointName.substring(prefix.length()));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return Integer.MAX_VALUE;
        }
    }

    private void attemptWaypointLoad() {
        this.waypointLoadRetries++;
        try {
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
            MinimapWorld waypointWorld = this.getWaypointWorld();
            boolean fromEnd = this.isInEnd;
            if (waypointWorld == null) {
                if (this.waypointLoadRetries >= 10) {
                    if (this.showChatMessages.get()) {
                        this.mc.execute(() -> this.error("Waypoint world is null after " + this.waypointLoadRetries + " retries"));
                    }
                    this.waypointsFullyLoaded = true;
                }
                return;
            }

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) {
                if (this.waypointLoadRetries >= 10) {
                    if (this.showChatMessages.get()) {
                        this.mc.execute(() -> this.error("Current waypoint set is null after " + this.waypointLoadRetries + " retries"));
                    }
                    this.waypointsFullyLoaded = true;
                }
                return;
            }

            String prefix = this.waypointPrefix.get();
            List<Waypoint> huntWaypoints = new ArrayList<>();

            for (Waypoint wp : currentSet.getWaypoints()) {
                if (this.shouldFollowWaypoint(wp.getName()) && !wp.isDisabled()) {
                    huntWaypoints.add(wp);
                }
            }

            huntWaypoints.sort((wp1, wp2) -> Integer.compare(
                this.extractOrderNumber(wp1.getName(), prefix),
                this.extractOrderNumber(wp2.getName(), prefix)
            ));

            this.waypointsToFollow.clear();
            this.currentWaypointIndex = 0;

            for (Waypoint wp : huntWaypoints) {
                this.addWaypointToFollow(new BlockPos(wp.getX(), wp.getY(), wp.getZ()));
            }

            this.waypointsFullyLoaded = true;
            this.lastKnownWaypointCount = this.getTotalHuntWaypointCount();
            if (this.showChatMessages.get()) {
                int size = this.waypointsToFollow.size();
                int retries = this.waypointLoadRetries;
                this.mc.execute(() -> {
                    if (size > 0) {
                        String source = fromEnd ? " from end" : "";
                        this.info("Loaded " + size + " hunt waypoints" + source + " (attempt " + retries + ")");
                    } else {
                        this.info("No waypoints with prefix '" + prefix + "' found");
                    }
                });
            }
        } catch (Exception e) {
            if (this.showChatMessages.get() && this.waypointLoadRetries >= 10) {
                this.mc.execute(() -> this.error("Failed to load waypoints from Xaero: " + e.getMessage()));
            }
        }
    }

    public void clearAllFollowWaypoints() {
        try {
            MinimapWorld waypointWorld = this.getWaypointWorld();
            if (waypointWorld == null) {
                if (this.showChatMessages.get()) this.warning("Could not get waypoint world for clearing");
                return;
            }

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) {
                if (this.showChatMessages.get()) this.warning("Could not get waypoint set for clearing");
                return;
            }

            String prefix = this.waypointPrefix.get();
            String marks = this.routePrefix.get();
            List<Waypoint> toRemove = new ArrayList<>();

            // Route marks go with the hunt waypoints: a new hunt starts from a
            // clean map, and a mark left from the last one bends the next fill
            // through it.
            for (Waypoint wp : currentSet.getWaypoints()) {
                String name = wp.getName();
                if (name == null) continue;
                if (name.startsWith(prefix) || (!marks.isEmpty() && name.startsWith(marks))) {
                    toRemove.add(wp);
                }
            }

            for (Waypoint wp : toRemove) {
                currentSet.remove(wp);
            }

            try {
                MinimapSession minimapSession = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
                if (minimapSession != null) {
                    minimapSession.getWorldManagerIO().saveWorld(waypointWorld);
                    if (this.showChatMessages.get()) {
                        this.info("Cleared " + toRemove.size() + " hunt waypoints and route marks (saved to disk)");
                    }
                }
            } catch (Exception saveEx) {
                if (this.showChatMessages.get()) {
                    this.error("Failed to save waypoints to disk: " + saveEx.getMessage());
                }
            }

            try {
                SupportMods.xaeroMinimap.requestWaypointsRefresh();
            } catch (Exception ignored) {}
        } catch (Exception e) {
            if (this.showChatMessages.get()) {
                this.error("Failed to clear waypoints: " + e.getMessage());
            }
        }
    }

    private void removeFollowWaypoint(BlockPos pos) {
        try {
            MinimapWorld waypointWorld = this.getWaypointWorld();
            if (waypointWorld == null) return;

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) return;

            String prefix = this.waypointPrefix.get();
            Waypoint toRemove = null;

            for (Waypoint wp : currentSet.getWaypoints()) {
                if (wp.getName().startsWith(prefix)) {
                    BlockPos p = new BlockPos(wp.getX(), wp.getY(), wp.getZ());
                    if (p.equals(pos)) {
                        toRemove = wp;
                        break;
                    }
                }
            }

            if (toRemove != null) {
                currentSet.remove(toRemove);
                try {
                    MinimapSession minimapSession = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
                    if (minimapSession != null) {
                        minimapSession.getWorldManagerIO().saveWorld(waypointWorld);
                    }
                } catch (Exception ignored) {}
                try {
                    SupportMods.xaeroMinimap.requestWaypointsRefresh();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void addWaypointToFollow(BlockPos pos) {
        if (!this.waypointsToFollow.contains(pos)) {
            this.waypointsToFollow.add(pos);
        }
    }

    private BlockPos getNextWaypoint() {
        if (this.waypointsToFollow.isEmpty()) return null;
        if (this.followMode.get() == FollowMode.Closest) return this.getClosestWaypoint();
        if (this.currentWaypointIndex >= this.waypointsToFollow.size()) this.currentWaypointIndex = 0;
        return this.waypointsToFollow.get(this.currentWaypointIndex);
    }

    private BlockPos getClosestWaypoint() {
        ClientPlayerEntity player = mc.player;
        if (player == null || this.waypointsToFollow.isEmpty()) return null;

        Vec3d playerPos = player.getEntityPos();
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (BlockPos waypoint : this.waypointsToFollow) {
            double distance = playerPos.squaredDistanceTo(Vec3d.ofCenter(waypoint));
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = waypoint;
            }
        }
        return closest;
    }

    private void removeCurrentWaypoint(BlockPos waypoint) {
        this.removeCurrentWaypoint(waypoint, WaypointFollower.Outcome.ARRIVED);
    }

    private void removeCurrentWaypoint(BlockPos waypoint, WaypointFollower.Outcome outcome) {
        int index = this.waypointsCompletedThisSession;
        this.waypointsToFollow.remove(waypoint);
        this.removeFollowWaypoint(waypoint);
        this.waypointsCompletedThisSession++;
        this.lastCompletedAtMs = System.currentTimeMillis();
        this.pushRemoval(new WaypointFollower.Removal(waypoint, index, outcome, this.lastCompletedAtMs));
        if (this.followMode.get() == FollowMode.Numerical && this.currentWaypointIndex >= this.waypointsToFollow.size()) {
            this.currentWaypointIndex = 0;
        }

        if (outcome == WaypointFollower.Outcome.ARRIVED && this.waypointsToFollow.isEmpty()) {
            this.routeEndPending = true;
        }
    }

    private void applyRouteEndAction() {
        if (this.routeEnd.get() == RouteEndAction.DISCONNECT && mc.player != null) {
            this.info("All waypoints reached - leaving the server");
            mc.player.networkHandler.onDisconnect(
                new net.minecraft.network.packet.s2c.common.DisconnectS2CPacket(
                    net.minecraft.text.Text.literal("[WaypointFollower] all waypoints reached")
                )
            );
            return;
        }

        this.info("All waypoints reached - switching off");
        if (this.isActive()) this.toggle();
    }

    /** Whether a waypoint left the list by being reached or by the skip button. */
    public enum Outcome {
        ARRIVED,
        SKIPPED
    }

    /** What to do once the last waypoint has been reached. */
    public enum RouteEndAction {
        OFF_MODULES,
        DISCONNECT
    }

    /** One waypoint leaving the route, for the HUD to draw a mark as it slides off. */
    public record Removal(BlockPos pos, int index, WaypointFollower.Outcome outcome, long atMs) {}

    private final java.util.ArrayDeque<WaypointFollower.Removal> recentRemovals = new java.util.ArrayDeque<>();

    private void pushRemoval(WaypointFollower.Removal removal) {
        this.recentRemovals.addLast(removal);
        while (this.recentRemovals.size() > 6) this.recentRemovals.pollFirst();
    }

    /** The last few waypoint exits, oldest first, for the marks that ride the deposit slide. */
    public java.util.List<WaypointFollower.Removal> hudRecentRemovals() {
        return new java.util.ArrayList<>(this.recentRemovals);
    }

    /** The most recent waypoint exit, or null. */
    public WaypointFollower.Removal hudLastRemoval() {
        return this.recentRemovals.peekLast();
    }

    private void checkAndReloadWaypoints() {
        if (!this.waypointsFullyLoaded) return;
        this.reloadCheckTimer++;
        if (this.reloadCheckTimer >= 40) {
            this.reloadCheckTimer = 0;
            try {
                SupportMods.xaeroMinimap.requestWaypointsRefresh();
            } catch (Exception ignored) {}

            int currentCount = this.getTotalHuntWaypointCount();
            if (currentCount != this.lastKnownWaypointCount) {
                this.waypointsToFollow.clear();
                this.waypointLoadRetries = 0;
                this.attemptWaypointLoad();
                this.lastKnownWaypointCount = currentCount;
            }
        }
    }

    private void startFlightMode(FlightMode mode) {
        if (mode == this.activeFlightMode) return;
        this.stopFlightMode();

        switch (mode) {
            case RocketFly -> {
                if (this.rocketFly != null && !this.rocketFly.isActive()) {
                    this.rocketFly.toggle();
                    this.activeFlightMode = FlightMode.RocketFly;
                }
            }
            case Pitch40 -> {
                if (this.pitch40Util != null && !this.pitch40Util.isActive()) {
                    this.pitch40Util.toggle();
                    this.activeFlightMode = FlightMode.Pitch40;
                }
            }
            case None -> this.activeFlightMode = FlightMode.None;
        }
    }

    private void stopFlightMode() {
        if (this.activeFlightMode == FlightMode.RocketFly && this.rocketFly != null && this.rocketFly.isActive()) {
            this.rocketFly.toggle();
        } else if (this.activeFlightMode == FlightMode.Pitch40 && this.pitch40Util != null && this.pitch40Util.isActive()) {
            this.pitch40Util.toggle();
        }
        this.activeFlightMode = FlightMode.None;
    }

    private void rotateYawTowards(Vec3d targetPos) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        Vec3d playerPos = player.getEntityPos();
        double deltaX = targetPos.getX() - playerPos.getX();
        double deltaZ = targetPos.getZ() - playerPos.getZ();
        float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
        float currentYaw = player.getYaw();
        float yawDiff = targetYaw - currentYaw;

        while (yawDiff > 180.0F) yawDiff -= 360.0F;
        while (yawDiff < -180.0F) yawDiff += 360.0F;

        if (Math.abs(yawDiff) > yawDeadzone.get().floatValue()) {
            if (snapRotation.get()) {
                player.setYaw(targetYaw);
            } else {
                player.setYaw(currentYaw + yawDiff * rotationSpeed.get().floatValue());
            }
        }
    }

    private double getHorizontalDistance(Vec3d from, Vec3d to) {
        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private Vec3d getAdjustedWaypointPos(BlockPos waypoint) {
        int adjustedX = this.toPlayerDimensionCoord(waypoint.getX());
        int adjustedZ = this.toPlayerDimensionCoord(waypoint.getZ());
        return new Vec3d(adjustedX + 0.5, waypoint.getY() + 0.5, adjustedZ + 0.5);
    }

    private void useFireworkRocket() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null || fireworkCooldown > 0) return;

        var inv = player.getInventory();

        // Hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                InvUtils.swap(i, true);
                mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
                InvUtils.swapBack();
                fireworkCooldown = 10;
                return;
            }
        }

        // Main Inventory (slots 9-35)
        for (int i = 9; i < inv.getMainStacks().size(); i++) {
            if (inv.getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                int selected = inv.getSelectedSlot();
                InvUtils.move().from(i).to(selected);
                mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
                InvUtils.move().from(selected).to(i);
                fireworkCooldown = 10;
                return;
            }
        }
    }

    private boolean hasElytraEquipped() {
        ClientPlayerEntity player = mc.player;
        return player != null && player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
    }

    private boolean isTextFieldFocusedInXaeroMap() {
        if (mc.currentScreen == null) return false;
        if (!mc.currentScreen.getClass().getName().equals("xaero.map.gui.GuiMap")) return false;

        Element focused = mc.currentScreen.getFocused();
        return focused instanceof TextFieldWidget || (focused != null && focused.getClass().getName().contains("TextField"));
    }

    private void triggerStartupRecast() {
        if (this.elytraRecast == null) {
            if (this.showChatMessages.get()) this.error("ElytraRecast module not found for startup.");
            return;
        }
        if (this.elytraRecast.isActive()) return;

        try {
            Setting<Boolean> waitForFall = (Setting<Boolean>) this.elytraRecast.settings.get("wait-for-fall");
            Setting<Boolean> ascendMode = (Setting<Boolean>) this.elytraRecast.settings.get("ascend-mode");
            Setting<Boolean> autoDisable = (Setting<Boolean>) this.elytraRecast.settings.get("auto-disable");

            if (waitForFall != null) waitForFall.set(false);
            if (autoDisable != null) autoDisable.set(true);

            if (ascendMode != null) {
                if (this.isInNether) {
                    ascendMode.set(false);
                } else {
                    FlightMode mode = this.isInEnd ? this.endFlightMode.get() : this.overworldFlightMode.get();
                    ascendMode.set(mode == FlightMode.Pitch40);
                }
            }

            if (this.showChatMessages.get()) this.info("Starting flight via ElytraRecast...");
            this.elytraRecast.toggle();
            this.recastEnabledForStartup = this.elytraRecast.isActive();
        } catch (Exception ignored) {}
    }

    private void handleNetherPathfinding() {
        if (this.netherFlightMode.get() == NetherFlightMode.Baritone) {
            this.handleNetherBaritonePathfinding();
        } else {
            this.handleNetherManualPathfinding();
        }
    }

    private void handleNetherBaritonePathfinding() {
        if (this.waypointsToFollow.isEmpty()) return;
        BlockPos nextWaypoint = this.getNextWaypoint();
        if (nextWaypoint == null) return;

        try {
            IBaritone baritoneInstance = BaritoneAPI.getProvider().getPrimaryBaritone();
            BaritoneAPI.getSettings().elytraTermsAccepted.value = true;

            int goalX = this.toNetherCoord(nextWaypoint.getX());
            int goalZ = this.toNetherCoord(nextWaypoint.getZ());
            boolean sameTarget = this.currentBaritoneTarget != null && this.currentBaritoneTarget.equals(nextWaypoint);
            boolean needsNewGoal = !sameTarget;

            if (!sameTarget) {
                // A genuinely new waypoint: this is not a retry of the last one, so it gets a full
                // fresh cap rather than inheriting whatever the previous spot used up.
                this.baritoneRestartAttempts = 0;
                this.baritoneRestartCooldown = 0;
                this.baritoneRestartPos = null;
            } else if (this.baritoneRestartCooldown > 0) {
                this.baritoneRestartCooldown--;
            } else if (!baritoneInstance.getElytraProcess().isActive()
                    && mc.player != null && mc.player.isOnGround()
                    && this.rocketCount > 0
                    && (this.baritoneRestartAttempts < BARITONE_RESTART_MAX_ATTEMPTS || this.movedSinceLastRestart())) {
                if (this.movedSinceLastRestart()) {
                    // somewhere new since the last attempt: a new try, with the whole cap again
                    this.baritoneRestartAttempts = 0;
                }
                // Baritone dropped this goal on its own (a crash, an emergency landing, a manual
                // #stop, a dimension change tearing the process down) without the waypoint itself
                // changing, so the check above never re-fires. Left alone, any such drop simply
                // ends the flight — the bot stands there with fireworks left and nowhere to go,
                // and there will be others. Only when actually grounded, so this never fights a
                // Baritone that is still mid-air and just between ticks of its own bookkeeping.
                needsNewGoal = true;
                this.baritoneRestartAttempts++;
                this.baritoneRestartCooldown = BARITONE_RESTART_DELAY_TICKS;
                this.baritoneRestartPos = mc.player.getBlockPos();
                if (this.showChatMessages.get()) {
                    this.info("Baritone's elytra process is gone and I'm grounded with rockets left "
                        + "- giving it the goal again (" + this.baritoneRestartAttempts + "/" + BARITONE_RESTART_MAX_ATTEMPTS + ")");
                }
            }

            if (needsNewGoal) {
                this.currentBaritoneTarget = nextWaypoint;
                GoalXZ goal = new GoalXZ(goalX, goalZ);
                baritoneInstance.getElytraProcess().pathTo(goal);
                if (this.showChatMessages.get()) this.info("Set Baritone elytra goal to: " + goalX + ", " + goalZ);
            }
        } catch (Exception e) {
            if (this.showChatMessages.get()) this.error("Baritone error: " + e.getMessage());
            return;
        }

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        Vec3d playerPos = player.getEntityPos();
        int checkX = this.toNetherCoord(nextWaypoint.getX());
        int checkZ = this.toNetherCoord(nextWaypoint.getZ());
        double horizontalDistance = Math.sqrt(Math.pow(checkX - playerPos.getX(), 2) + Math.pow(checkZ - playerPos.getZ(), 2));
        double effectiveReach = this.reachDistance.get() * this.netherReachMultiplier.get();

        if (horizontalDistance <= effectiveReach) {
            this.removeCurrentWaypoint(nextWaypoint);
            this.currentBaritoneTarget = null;
        }
    }

    private void handleNetherManualPathfinding() {
        BlockPos nextWaypoint = this.getNextWaypoint();
        if (nextWaypoint == null) return;

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        Vec3d scaledTarget = new Vec3d(this.toNetherCoord(nextWaypoint.getX()), nextWaypoint.getY(), this.toNetherCoord(nextWaypoint.getZ()));
        this.rotateYawTowards(scaledTarget);

        Vec3d playerPos = player.getEntityPos();
        double horizontalDistance = this.getHorizontalDistance(playerPos, scaledTarget);
        double effectiveReach = this.reachDistance.get() * this.netherReachMultiplier.get();

        if (horizontalDistance < effectiveReach) {
            this.removeCurrentWaypoint(nextWaypoint);
            if (this.waypointsToFollow.isEmpty() && this.showChatMessages.get()) {
                this.info("All waypoints reached");
                this.playCompletionSound();
            }
        }
    }

    private void handleOverworldPathfinding() {
        this.handleDimensionPathfinding(this.overworldFlightMode.get());
    }

    private void handleEndPathfinding() {
        this.handleDimensionPathfinding(this.endFlightMode.get());
    }

    private void handleDimensionPathfinding(FlightMode mode) {
        BlockPos nextWaypoint = this.getNextWaypoint();
        if (nextWaypoint == null) return;

        switch (mode) {
            case RocketFly -> this.handleRocketFlyPathfinding(nextWaypoint);
            case Pitch40 -> this.handlePitch40Pathfinding(nextWaypoint);
            case None -> this.handleManualPathfinding(nextWaypoint);
        }
    }

    private void handleRocketFlyPathfinding(BlockPos nextWaypoint) {
        if (this.rocketFly == null) return;
        if (!this.rocketFly.isActive()) {
            if (!this.hasElytraEquipped()) return;
            this.startFlightMode(FlightMode.RocketFly);
        }

        Vec3d targetPos = this.getAdjustedWaypointPos(nextWaypoint);
        this.rotateYawTowards(targetPos);

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        double horizontalDistance = this.getHorizontalDistance(player.getEntityPos(), targetPos);
        if (horizontalDistance < this.reachDistance.get()) {
            this.removeCurrentWaypoint(nextWaypoint);
            if (this.waypointsToFollow.isEmpty()) {
                this.stopFlightMode();
                if (this.showChatMessages.get()) this.info("All waypoints reached");
                this.playCompletionSound();
            }
        }
    }

    private void handlePitch40Pathfinding(BlockPos nextWaypoint) {
        if (this.pitch40Util == null) return;
        if (!this.pitch40Util.isActive()) {
            if (!this.hasElytraEquipped()) return;
            this.startFlightMode(FlightMode.Pitch40);
        }

        if (this.pitch40Util.autoFirework != null && !this.pitch40Util.autoFirework.get()) {
            this.pitch40Util.autoFirework.set(true);
        }

        Vec3d targetPos = this.getAdjustedWaypointPos(nextWaypoint);
        this.rotateYawTowards(targetPos);

        ClientPlayerEntity player = mc.player;
        if (player != null && player.getPitch() < -35.0F && player.getVelocity().getY() < -0.05) {
            this.useFireworkRocket();
        }

        double horizontalDistance = this.getHorizontalDistance(player.getEntityPos(), targetPos);
        if (horizontalDistance < this.reachDistance.get()) {
            this.removeCurrentWaypoint(nextWaypoint);
            if (this.waypointsToFollow.isEmpty()) {
                this.stopFlightMode();
                if (this.showChatMessages.get()) this.info("All waypoints reached");
                this.playCompletionSound();
            }
        }
    }

    private void handleManualPathfinding(BlockPos nextWaypoint) {
        Vec3d targetPos = this.getAdjustedWaypointPos(nextWaypoint);
        this.rotateYawTowards(targetPos);

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        double horizontalDistance = this.getHorizontalDistance(player.getEntityPos(), targetPos);
        if (horizontalDistance < this.reachDistance.get()) {
            this.removeCurrentWaypoint(nextWaypoint);
            if (this.waypointsToFollow.isEmpty() && this.showChatMessages.get()) {
                this.info("All waypoints reached");
                this.playCompletionSound();
            }
        }
    }

    private void playCompletionSound() {
        ClientPlayerEntity player = mc.player;
        if (this.completionSound.get() && player != null) {
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!this.isActive()) {
            return;
        }

        ClientPlayerEntity player = mc.player;
        if (isDeactivating || player == null || mc.world == null) return;

        if (this.routeEndPending) {
            this.routeEndPending = false;
            this.applyRouteEndAction();
            return;
        }

        String currentDim = this.getCurrentDimensionKey();
        if (!currentDim.equals(this.lastDimensionKey)) {
            this.lastDimensionKey = currentDim;
            this.flushSeedSave();
            this.lastSeedOwX = Double.NaN;
            this.lastSeedOwZ = Double.NaN;
            if (this.showChatMessages.get()) this.info("Dimension changed, reloading waypoints...");
            this.waypointsToFollow.clear();
            this.waypointLoadRetries = 0;
            this.waypointsFullyLoaded = false;
            this.lastPlayerPos = player.getEntityPos();
            this.startAsyncWaypointLoad();
        }

        this.updateRockets(player);

        // Read before the recording branch, or a dive with the module already
        // on would leave them describing the dimension you came from.
        this.isInNether = this.isInNether(mc.world);
        this.isInEnd = this.isInEnd(mc.world);

        // A recording session replaces flying entirely, in every dimension:
        // below, you fly the ribbon yourself and the module only writes down
        // where you went; above, it keeps its hands off, because surfacing
        // through a portal to look at a suspect spot is part of following a
        // trail, not the end of it. The session ends when the box is unticked
        // -- and only then does the chain get flown.
        if (this.recordRoute.get()) {
            if (this.activeFlightMode != FlightMode.None) this.stopFlightMode();
            if (this.currentBaritoneTarget != null) this.stopBaritone();

            if (this.isInNether) this.recordBreadcrumb(player);
            else this.flushSeedSaveIfDue();

            return;
        }

        if (!this.waypointsFullyLoaded) return;

        if (mc.interactionManager == null || mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) return;

        if (this.lastPlayerPos != null) {
            Vec3d at = player.getEntityPos();
            double distance = at.distanceTo(this.lastPlayerPos);
            if (distance < 100.0) {
                this.totalDistanceTraveled += distance;

                // Smoothed over roughly five seconds, for the HUDs' arrival
                // time. Elytra speed swings by a third between boosts, and an
                // estimate built on the reading of the moment never settles.
                double step = Math.hypot(at.x - this.lastPlayerPos.x, at.z - this.lastPlayerPos.z) * 20.0;
                if (this.smoothedSpeed <= 0.0) this.smoothedSpeed = step;
                else this.smoothedSpeed += 0.01 * (step - this.smoothedSpeed);
            }
        }
        this.lastPlayerPos = player.getEntityPos();

        this.checkAndReloadWaypoints();
        this.pruneUntrackableWaypoints();

        if (this.areaLoaderSteering) {
            return;
        }

        if (this.waypointsToFollow.isEmpty() || this.isPaused) {
            if (this.currentBaritoneTarget != null) {
                this.stopBaritone();
            }
            return;
        }

        if (this.fireworkCooldown > 0) this.fireworkCooldown--;

        if (this.autoStartFlight.get() && !this.startupRecastTriggered) {
            // In the Nether with Baritone flying, Baritone leaves the ground by itself (its
            // standing takeoff), so ElytraRecast would only spam rockets in front of it and fly
            // blind until it caught up. Only when Baritone is actually allowed to: with
            // elytraAutoJump off it would sit there, and Recast is still the way up.
            if (this.isInNether && this.netherFlightMode.get() == NetherFlightMode.Baritone
                    && BaritoneAPI.getSettings().elytraAutoJump.value) {
                this.startupRecastTriggered = true;
                this.baritoneActivatedAfterStartup = true;
            } else if (!player.isGliding()) {
                this.startupDelayTicks++;
                if (this.startupDelayTicks >= this.startupDelay.get()) {
                    if (this.hasElytraEquipped()) {
                        this.triggerStartupRecast();
                    } else if (this.showChatMessages.get()) {
                        this.error("No elytra equipped, cannot auto-start flight.");
                    }
                    this.startupRecastTriggered = true;
                }
                return;
            }
            this.startupRecastTriggered = true;
            this.baritoneActivatedAfterStartup = true;
        }

        if (this.startupRecastTriggered && !this.baritoneActivatedAfterStartup) {
            if (player.isGliding()) {
                this.glidingTicksAfterStartup++;
                if (this.glidingTicksAfterStartup >= 2) {
                    this.baritoneActivatedAfterStartup = true;
                }
            }
            if (!this.baritoneActivatedAfterStartup) return;
        }

        // Recast was borrowed to leave the ground; the flight is established
        // and this module's own mode flies now, so it is handed back the
        // moment its recovery sequence is over. This is the missing half of
        // the startup contract — enabled to take off, disabled once flying —
        // that the dead "auto-disable" lookup was always meant to deliver.
        if (this.recastEnabledForStartup && this.elytraRecast != null) {
            if (!this.elytraRecast.isActive()) {
                this.recastEnabledForStartup = false;
            } else if (!this.elytraRecast.isRecovering()) {
                this.elytraRecast.toggle();
                this.recastEnabledForStartup = false;
                if (this.showChatMessages.get()) this.info("Flight established - ElytraRecast released.");
            }
        }

        if (this.isInNether) {
            this.handleNetherPathfinding();
        } else if (this.isInEnd) {
            this.handleEndPathfinding();
        } else {
            this.handleOverworldPathfinding();
        }

        if (this.showStatistics.get() && !this.waypointsToFollow.isEmpty()) {
            StringBuilder stats = new StringBuilder();
            stats.append(this.waypointsToFollow.size()).append(" waypoints");
            if (this.waypointsCompletedThisSession > 0) stats.append(String.format(" | %d done", this.waypointsCompletedThisSession));
            stats.append(String.format(" | %.0f blocks traveled", this.totalDistanceTraveled));
            player.sendMessage(Text.literal(stats.toString()), true);
        }
    }

    @Override
    public String getInfoString() {
        if (this.waypointsToFollow.isEmpty()) return "No waypoints";

        StringBuilder info = new StringBuilder();
        if (this.isPaused) info.append("[PAUSED] ");

        info.append(this.waypointsToFollow.size()).append(" waypoints");
        String modeInfo = this.isInNether ? " [" + this.netherFlightMode.get() + "]" : (this.isInEnd ? " [" + this.endFlightMode.get() + "]" : " [" + this.overworldFlightMode.get() + "]");
        info.append(modeInfo);

        BlockPos next = this.getNextWaypoint();
        ClientPlayerEntity player = mc.player;
        if (next != null && player != null) {
            double dist = this.getHorizontalDistance(player.getEntityPos(), this.getAdjustedWaypointPos(next));
            info.append(String.format(" - %.0fm", dist));
        }

        if (this.showStatistics.get() && this.waypointsCompletedThisSession > 0) {
            info.append(String.format(" | %d completed", this.waypointsCompletedThisSession));
        }
        return info.toString();
    }

    // ---- Seeding ----

    /**
     * Fills the hunt chain along the mapped ribbon between the control marks.
     *
     * <p>The marks say where the ribbon starts, bends and ends; the map says
     * where it actually runs. Between one mark and the next the walk is a path
     * search over the chunks Xaero has on disk — the same "seen tile" answer
     * XaeroPlus reads for its portal detection — so the chain follows the
     * corridor the old traveller loaded, bend for bend, and never crosses
     * ground nobody ever generated. A hunt waypoint goes down every
     * seed-spacing blocks of that walk, always on a mapped chunk.
     *
     * <p>The world map only keeps in memory the regions it is drawing, so the
     * regions along the route are asked for from the disk first and the trace
     * runs once they are in — a small job on the tick bus, see
     * {@link RouteSeedJob}. When a stretch still cannot be traced the walk
     * stops there and says between which two marks; what is already placed is
     * skipped on the next press and the numbering carries on.
     *
     * <p>Reached two ways: the Meteor keybind with no screen open, and the map
     * screen's own key handling (the GuiMap mixin) when the map is open, since
     * Meteor fires no shortcut then.
     */
    public void seedRouteFromMap() {
        if (this.isTextFieldFocusedInXaeroMap()) return;
        if (mc.player == null || mc.world == null) return;

        // Every answer below reaches the chat whatever show-chat-messages
        // says: that switch quiets the flight's running commentary, and a key
        // pressed by hand that says nothing back reads as a key that did nothing.
        // A second press stops the walk. It can run for minutes on a long
        // ribbon, and a key that only answers "still busy" leaves no way out
        // of a fill started on the wrong marks.
        if (this.seedJob != null) {
            this.info("Route fill stopped on request.");
            this.seedJob.end(false);
            return;
        }

        try {
            if (!Settings.REGISTRY.owAutoWaypointDimension.get()) {
                Settings.REGISTRY.owAutoWaypointDimension.setValue(true);
                this.info("Enabled XaeroPlus 'Prefer Overworld Waypoints'");
            }
        } catch (Exception e) {
            this.warning("Could not reach XaeroPlus's settings; nothing placed.");
            return;
        }

        MinimapWorld waypointWorld = this.getWaypointWorld();
        WaypointSet set = waypointWorld == null ? null : waypointWorld.getCurrentWaypointSet();

        if (set == null) {
            this.warning("Xaero's waypoints are not ready yet; nothing placed.");
            return;
        }

        String prefix = this.routePrefix.get();
        if (prefix.isEmpty()) {
            this.warning("route-prefix is empty; nothing to read.");
            return;
        }

        List<Waypoint> controls = new ArrayList<>();
        for (Waypoint wp : set.getWaypoints()) {
            if (wp.getName() == null || !wp.getName().startsWith(prefix)) continue;
            if (this.extractOrderNumber(wp.getName(), prefix) == Integer.MAX_VALUE) continue;
            controls.add(wp);
        }

        if (controls.size() < 2) {
            this.warning("Need at least two %sN marks on the map to fill a route (found %d).".formatted(prefix, controls.size()));
            return;
        }

        controls.sort((a, b) -> Integer.compare(
            this.extractOrderNumber(a.getName(), prefix),
            this.extractOrderNumber(b.getName(), prefix)));

        // The map being looked at is the map being read: its dimension sets the
        // frame the search runs in and the scale of the stored coordinates.
        MapProcessor mapProcessor = null;
        RegistryKey<World> viewed = World.OVERWORLD;

        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session != null) {
                mapProcessor = session.getMapProcessor();
                MapWorld mapWorld = mapProcessor == null ? null : mapProcessor.getMapWorld();
                if (mapWorld != null && mapWorld.getCurrentDimensionId() != null) {
                    viewed = mapWorld.getCurrentDimensionId();
                }
            }
        } catch (Exception ignored) {}

        if (mapProcessor == null) {
            this.warning("Xaero's world map has no session yet; nothing placed.");
            return;
        }

        // Until the map has finished listing its region files every lookup
        // answers null, whatever is on the disk: a walk started then reports
        // the first mark as standing on unmapped ground, which is a lie about
        // the map rather than about the mark.
        try {
            if (!mapProcessor.getMapSaveLoad().isRegionDetectionComplete()) {
                this.warning("Xaero is still listing its region files; try again in a few seconds.");
                return;
            }
        } catch (Exception ignored) {}

        // Stored coordinates are overworld ones; the search runs in the viewed
        // map's own blocks, eight times smaller below.
        boolean netherView = viewed == World.NETHER;
        double toMap = netherView ? 1.0 / 8.0 : 1.0;
        double toStored = netherView ? 8.0 : 1.0;
        int spacingMap = Math.max(16, (int) Math.round(this.seedSpacing.get() * toMap));

        // Named, so a mark left over from an earlier attempt shows at once: the
        // walk goes through every mark in numeric order, and an old Route_3
        // still on the map bends today's route through yesterday's.
        // The rule in force goes in the line, so a fill that placed two marks
        // or two hundred says on its own which knob to turn.
        this.info("Route fill: %d mark(s), %s to %s, %s.".formatted(
            controls.size(), controls.get(0).getName(), controls.get(controls.size() - 1).getName(),
            this.seedMode.get() == SeedMode.PortalPatches
                ? "one waypoint per %d-chunk portal footprint%s".formatted(
                    this.portalRadius.get(),
                    resolveOldChunks() == null ? "" : ", skipping the modern chunks you loaded yourself")
                : "one waypoint every %d blocks".formatted(this.seedSpacing.get())));

        // One stretch at a time: its band of regions is asked for, traced the
        // moment it is in, and only then the next. Asking for the whole route at
        // once queued hundreds of regions behind the map's own loading, and half
        // of them were still not in after two minutes.
        this.seedJob = new RouteSeedJob(this, mapProcessor, mapProcessor.getCurrentCaveLayer(), viewed, controls,
            toMap, toStored, spacingMap, waypointWorld, set, this.getNextWaypointNumber(),
            this.seedSpacing.get() * 0.4,
            this.seedMode.get() == SeedMode.PortalPatches ? this.portalRadius.get() : 0);

        if (!this.seedJob.startStretch()) {
            this.seedJob = null;
            return;
        }

        MeteorClient.EVENT_BUS.subscribe(this.seedJob);
    }

    /**
     * Whether a region's tiles are actually in memory, not merely its object.
     *
     * <p>The map keeps the object of every region it has shown and frees the
     * tiles behind it when the view moves on; "loaded" can still answer yes
     * while there is nothing to read. A tile chunk at load state two is the
     * proof the pixels -- and XaeroPlus's seen flags with them -- are in.
     */
    private static boolean regionTilesReady(MapRegion region) {
        // Four is the map's own "gave up on this file".
        if (region.getLoadState() == 4) return true;
        if (!region.isLoaded()) return false;
        if (!region.hasHadTerrain()) return true;

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                MapTileChunk chunk = region.getChunk(i, j);
                if (chunk != null && chunk.getLoadState() == 2) return true;
            }
        }

        return false;
    }

    /**
     * One window of the walk: from where the trace stands, as far along the
     * mapped corridor as the regions in memory allow, toward the next mark.
     *
     * <p>A stretch of any length is walked this way, window after window: the
     * regions around the current point are loaded, the corridor is searched
     * inside them, the walk moves to the point of that corridor closest to the
     * next mark, and the window moves with it. Nothing is ever asked of the
     * disk beyond the window, so a ribbon hundreds of kilometres long costs no
     * more memory than one three regions wide.
     *
     * @return 1 when the next mark is reached, 0 when the walk advanced and
     *         needs the next window, -1 when it cannot go on
     */
    private int advanceStretch(RouteSeedJob job) {
        java.util.HashMap<Long, Boolean> seen = new java.util.HashMap<>();
        Waypoint a = job.controls.get(job.stretch);
        Waypoint b = job.controls.get(job.stretch + 1);
        int gx = (int) Math.floor(b.getX() * job.toMap) >> 4;
        int gz = (int) Math.floor(b.getZ() * job.toMap) >> 4;

        int curX = (int) (job.cur >> 32);
        int curZ = (int) job.cur;

        // The first window of a stretch settles the start on mapped ground: a
        // mark dropped a little beside the ribbon must not void the stretch.
        if (!job.started) {
            long snapped = this.nearestSeenChunk(job.mapProcessor, job.caveLayer, seen, curX, curZ, 6);

            if (snapped == Long.MIN_VALUE) {
                this.warning("%s is not on mapped ground (nothing mapped within six chunks of it).".formatted(a.getName()));
                return -1;
            }

            job.cur = snapped;
            job.started = true;
            curX = (int) (snapped >> 32);
            curZ = (int) snapped;

            // The trail's start gets its own mark, like its end: the point the
            // traveller came in at is a portal candidate at least as good as
            // the one they left by.
            if (job.stretch == 0) {
                int x = (int) Math.round(((curX << 4) + 8) * job.toStored);
                int z = (int) Math.round(((curZ << 4) + 8) * job.toStored);

                if (!this.huntWaypointNear(job.set, job.huntPrefix, x, z, job.tooClose)) {
                    this.addHuntWaypoint(job.set, job.huntPrefix, job.firstNumber + job.placed, x, z);
                    job.placed++;
                }
            }
        }

        int rx = curX >> 5;
        int rz = curZ >> 5;
        int minX = (rx - RouteSeedJob.WINDOW) << 5;
        int maxX = ((rx + RouteSeedJob.WINDOW) << 5) + 31;
        int minZ = (rz - RouteSeedJob.WINDOW) << 5;
        int maxZ = ((rz + RouteSeedJob.WINDOW) << 5) + 31;

        boolean goalInside = gx >= minX && gx <= maxX && gz >= minZ && gz <= maxZ;
        long goal = chunkKey(gx, gz);

        if (goalInside) {
            long snappedGoal = this.nearestSeenChunk(job.mapProcessor, job.caveLayer, seen, gx, gz, 6);

            if (snappedGoal == Long.MIN_VALUE) {
                this.warning("%s is not on mapped ground (nothing mapped within six chunks of it).".formatted(b.getName()));
                return -1;
            }

            // The snap can step off the window's own edge, and a goal outside
            // the box is one the search can never reach: kept, it would call a
            // healthy corridor a dead end one window short of the mark.
            int snappedX = (int) (snappedGoal >> 32);
            int snappedZ = (int) snappedGoal;
            if (snappedX >= minX && snappedX <= maxX && snappedZ >= minZ && snappedZ <= maxZ) goal = snappedGoal;
        }

        List<long[]> path = this.traceInBox(job.mapProcessor, job.caveLayer, seen, curX, curZ,
            (int) (goal >> 32), (int) goal, minX, maxX, minZ, maxZ);

        long[] end = path.get(path.size() - 1);
        boolean reached = chunkKey((int) end[0], (int) end[1]) == goal;

        if (!reached) {
            // The walk has to gain ground on the mark, or the corridor ends
            // here as far as this map knows.
            double before = Math.hypot(curX - gx, curZ - gz);
            double after = Math.hypot(end[0] - gx, end[1] - gz);

            if (before - after < 4.0) {
                this.warning("The mapped corridor toward %s ends about %d chunks short of it: a gap in the ribbon, or a bend wider than the search window -- put a mark past it."
                    .formatted(b.getName(), (int) after));
                return -1;
            }
        }

        if (job.patchRadius > 0) {
            // A mark per portal footprint, not per so many blocks. A traveller
            // flying leaves a swath as wide as their render distance; one who
            // stops by a portal leaves a full square. So the walk marks a chunk
            // only where a whole square of mapped, not-mine-and-modern chunks
            // fits around it -- and once per footprint, at its middle, not at
            // every chunk of it.
            for (int k = 0; k < path.size(); k++) {
                long[] p = path.get(k);

                if (this.isPatchCentre(job, seen, (int) p[0], (int) p[1])) {
                    job.patchRun.add(p);
                    continue;
                }

                if (this.dropPatchMark(job)) return -1;
            }
        } else {
            // Marks every spacing of the walk, at the centre of mapped chunks; the
            // carry runs across windows and stretches, so the spacing is continuous.
            for (int k = 1; k < path.size(); k++) {
                long[] p = path.get(k);
                long[] q = path.get(k - 1);
                job.carry += Math.hypot(p[0] - q[0], p[1] - q[1]) * 16.0;
                if (job.carry < job.spacingMap) continue;
                job.carry = 0.0;

                int x = (int) Math.round(((p[0] << 4) + 8) * job.toStored);
                int z = (int) Math.round(((p[1] << 4) + 8) * job.toStored);
                if (this.huntWaypointNear(job.set, job.huntPrefix, x, z, job.tooClose)) continue;

                this.addHuntWaypoint(job.set, job.huntPrefix, job.firstNumber + job.placed, x, z);
                job.placed++;

                if (job.placed >= 1000) {
                    this.warning("A thousand marks placed -- stopping here; press again to carry on.");
                    return -1;
                }
            }
        }

        job.cur = chunkKey((int) end[0], (int) end[1]);
        job.windows++;

        if (reached) return 1;

        if (job.windows > 600) {
            this.warning("Six hundred windows walked without reaching %s; stopping here.".formatted(b.getName()));
            return -1;
        }

        return 0;
    }

    /**
     * Closes the fill: the far end's mark, the route marks consumed, the save.
     *
     * <p>The route marks go once the whole route is filled. Left on the map
     * they are read again by the next fill, in numeric order, and a mark from
     * last week's trail bends this week's route through it -- which is exactly
     * how a two-mark route came out as an eleven-mark zigzag. A fill that
     * stopped early keeps them, so the next press can carry on.
     */
    private void finishRoute(RouteSeedJob job, boolean wholeRoute) {
        int consumed = 0;

        // A footprint the walk was still inside when it stopped is a footprint
        // all the same: it only ever waits for the walk to leave it.
        this.dropPatchMark(job);

        if (wholeRoute) {
            // The end mark's own spot may sit a little beside the ribbon; the
            // walk's last chunk is where the trail actually ends, and it is
            // mapped ground by construction.
            int endX = (int) Math.round((((int) (job.cur >> 32) << 4) + 8) * job.toStored);
            int endZ = (int) Math.round((((int) job.cur << 4) + 8) * job.toStored);

            if (!this.huntWaypointNear(job.set, job.huntPrefix, endX, endZ, job.tooClose)) {
                this.addHuntWaypoint(job.set, job.huntPrefix, job.firstNumber + job.placed, endX, endZ);
                job.placed++;
            }

            for (Waypoint mark : job.controls) {
                try {
                    job.set.remove(mark);
                    consumed++;
                } catch (Exception ignored) {}
            }
        }

        if (job.placed > 0 || consumed > 0) {
            this.saveWaypointWorld(job.waypointWorld);

            try {
                SupportMods.xaeroMinimap.requestWaypointsRefresh();
            } catch (Exception ignored) {}
        }

        if (wholeRoute) {
            this.info("Filled %d waypoint(s) from %s to %s; the %d route mark(s) were removed."
                .formatted(job.placed, job.controls.get(0).getName(), job.controls.get(job.controls.size() - 1).getName(), consumed));
        } else {
            this.info("Filled %d waypoint(s) before stopping; the route marks stay for the next press.".formatted(job.placed));
        }
    }

    private RouteSeedJob seedJob;

    /**
     * XaeroPlus's Old Chunks module, or null when it cannot help.
     *
     * <p>Its two stores only answer for ground your own client has received,
     * and only while the module is on -- asked with it off they are empty, and
     * an empty answer would quietly drop the filter rather than the marks.
     */
    private static OldChunks resolveOldChunks() {
        try {
            if (!Settings.REGISTRY.oldChunksEnabledSetting.get()) return null;
            return ModuleManager.getModule(OldChunks.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Walks the route one stretch at a time: asks the disk for the stretch's
     * regions, traces it the moment they are in, then moves to the next.
     *
     * <p>Subscribed to the tick bus on its own rather than through the module's
     * handlers: the fill is pressed with the module off as often as on, and the
     * module's own tick does not run then.
     */
    private static final class RouteSeedJob {
        private final WaypointFollower follower;
        private final MapProcessor mapProcessor;
        private final int caveLayer;
        private final RegistryKey<World> viewed;
        private final List<Waypoint> controls;
        private final double toMap;
        private final double toStored;
        private final int spacingMap;
        private final MinimapWorld waypointWorld;
        private final WaypointSet set;
        private final String huntPrefix;
        private final int firstNumber;
        private final double tooClose;

        /** Side of the square that says "a portal could stand here", 0 for plain spacing. */
        private final int patchRadius;
        /** XaeroPlus's old/modern chunk stores, null when Old Chunks is off. */
        private final OldChunks oldChunks;
        /** The footprint the walk is crossing right now, one mark at its middle. */
        private final List<long[]> patchRun = new ArrayList<>();

        /** Regions either side of the walk's current point that are kept in memory. */
        private static final int WINDOW = 3;

        private final List<MapRegion> awaited = new ArrayList<>();
        private int stretch;
        private int placed;
        private double carry;
        private long deadline;
        private long cur;
        private boolean started;
        private int windows;
        private int loaded;
        private long lastReportMs;

        private RouteSeedJob(WaypointFollower follower, MapProcessor mapProcessor, int caveLayer, RegistryKey<World> viewed,
                             List<Waypoint> controls,
                             double toMap, double toStored, int spacingMap, MinimapWorld waypointWorld, WaypointSet set,
                             int firstNumber, double tooClose, int patchRadius) {
            this.patchRadius = patchRadius;
            this.oldChunks = resolveOldChunks();
            this.follower = follower;
            this.mapProcessor = mapProcessor;
            this.caveLayer = caveLayer;
            this.viewed = viewed;
            this.controls = controls;
            this.toMap = toMap;
            this.toStored = toStored;
            this.spacingMap = spacingMap;
            this.waypointWorld = waypointWorld;
            this.set = set;
            this.huntPrefix = follower.waypointPrefix.get();
            this.firstNumber = firstNumber;
            this.tooClose = tooClose;
        }

        /** Puts the walk on the stretch's first mark and asks for its first window. */
        private boolean startStretch() {
            Waypoint a = this.controls.get(this.stretch);
            this.cur = chunkKey((int) Math.floor(a.getX() * this.toMap) >> 4, (int) Math.floor(a.getZ() * this.toMap) >> 4);
            this.started = false;
            this.windows = 0;
            this.requestWindow();
            return true;
        }

        /** Asks the disk for the regions around the walk's current point. */
        private void requestWindow() {
            int rx = (int) (this.cur >> 32) >> 5;
            int rz = (int) this.cur >> 5;
            this.awaited.clear();

            for (int dx = -WINDOW; dx <= WINDOW; dx++) {
                for (int dz = -WINDOW; dz <= WINDOW; dz++) {
                    try {
                        // A region the map has ever shown keeps its object with the
                        // tiles freed and its file entry consumed -- so "is there a
                        // file" says no for exactly the regions worth reloading. The
                        // object comes first; only a region never shown since launch
                        // is created from its file entry; one with neither is ground
                        // nobody mapped.
                        MapRegion region = this.mapProcessor.getLeafMapRegion(this.caveLayer, rx + dx, rz + dz, false);

                        if (region == null) {
                            if (!this.mapProcessor.regionDetectionExists(this.caveLayer, rx + dx, rz + dz)) continue;
                            region = this.mapProcessor.getLeafMapRegion(this.caveLayer, rx + dx, rz + dz, true);
                            if (region == null) continue;
                        }

                        if (regionTilesReady(region)) continue;

                        // Prioritised, because the unprioritised path drops the
                        // request outright while the map is busy loading something else.
                        this.mapProcessor.getMapSaveLoad().requestLoad(region, "hunterbuddy seed-route", true);
                        this.awaited.add(region);
                    } catch (Exception ignored) {}
                }
            }

            this.loaded += this.awaited.size();
            // Xaero's loader reads one region file per pass of its own thread,
            // measured at about a region a second on this map: a flat minute
            // is under the cost of a first window and would time it out every
            // time. Four seconds a region, a minute at least.
            this.deadline = System.currentTimeMillis() + Math.max(60_000L, this.awaited.size() * 4_000L);
        }

        @EventHandler
        private void onTick(TickEvent.Post event) {
            // Every region lookup below resolves through the map's *current*
            // dimension: step through a portal, or move the cave slider, and
            // the same coordinates start reading another world's map -- which
            // would walk a corridor that is not there and mark the void.
            if (this.follower.mapViewChanged(this.mapProcessor, this.viewed, this.caveLayer)) {
                this.follower.warning("The map changed dimension while the route was filling; stopped there.");
                this.end(false);
                return;
            }

            boolean ready = true;

            for (MapRegion region : this.awaited) {
                if (!regionTilesReady(region)) {
                    ready = false;
                    break;
                }
            }

            if (!ready && System.currentTimeMillis() < this.deadline) return;

            if (!ready) {
                int pending = 0;
                for (MapRegion region : this.awaited) {
                    if (!regionTilesReady(region)) pending++;
                }

                this.follower.warning("%d map region(s) did not load in time; walking on with what is there.".formatted(pending));
            }

            int result = this.follower.advanceStretch(this);

            if (result < 0) {
                this.end(false);
                return;
            }

            if (result > 0) {
                this.follower.dropPatchMark(this);
                this.stretch++;

                if (this.stretch >= this.controls.size() - 1) {
                    this.end(true);
                    return;
                }

                this.startStretch();
                return;
            }

            // A line now and then, so a long walk does not look like a hang.
            long now = System.currentTimeMillis();
            if (now - this.lastReportMs > 20_000L) {
                this.lastReportMs = now;
                this.follower.info("Route fill: %d mark(s) so far, %d window(s) walked, %d region(s) loaded, heading for %s..."
                    .formatted(this.placed, this.windows, this.loaded, this.controls.get(this.stretch + 1).getName()));
            }

            this.requestWindow();
        }

        private void end(boolean wholeRoute) {
            MeteorClient.EVENT_BUS.unsubscribe(this);
            this.follower.seedJob = null;
            this.follower.finishRoute(this, wholeRoute);
        }
    }

    /**
     * Whether a whole portal-radius square of trail chunks fits around this one.
     *
     * <p>The same shape XaeroPlus reads for its own portal detection, with the
     * square centred on the chunk rather than cornered on it, so the mark that
     * comes out of it lands in the middle of the footprint instead of on its
     * edge. A ribbon flown at speed is as wide as a render distance and never
     * fills the square; the ground a traveller stood on by a portal does.
     */
    private boolean isPatchCentre(RouteSeedJob job, java.util.Map<Long, Boolean> seen, int chunkX, int chunkZ) {
        int half = job.patchRadius / 2;

        for (int dx = 0; dx < job.patchRadius; dx++) {
            for (int dz = 0; dz < job.patchRadius; dz++) {
                int x = chunkX - half + dx;
                int z = chunkZ - half + dz;
                if (!this.isChunkSeenOnMap(job.mapProcessor, job.caveLayer, seen, x, z)) return false;
                if (this.isOwnModernChunk(job, x, z)) return false;
            }
        }

        return true;
    }

    /**
     * Whether this chunk is one you loaded yourself and it is not 1.12 ground.
     *
     * <p>XaeroPlus files every chunk your client receives in one of two stores:
     * old, or modern. A chunk in the modern one is one you brought into the
     * world yourself on a detour beside the ribbon -- the traveller's own
     * ground is 1.12 by definition, and ground you never flew is in neither
     * store, so it stays eligible. Answers false whenever Old Chunks is off,
     * which simply leaves the square test on its own.
     */
    private boolean isOwnModernChunk(RouteSeedJob job, int chunkX, int chunkZ) {
        if (job.oldChunks == null) return false;

        try {
            return job.oldChunks.isOldChunkInverse(chunkX, chunkZ, job.viewed);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Closes the footprint being crossed: one mark, at its middle.
     *
     * @return true when the fill has to stop here
     */
    private boolean dropPatchMark(RouteSeedJob job) {
        if (job.patchRun.isEmpty()) return false;

        long[] middle = job.patchRun.get(job.patchRun.size() / 2);
        job.patchRun.clear();

        int x = (int) Math.round(((middle[0] << 4) + 8) * job.toStored);
        int z = (int) Math.round(((middle[1] << 4) + 8) * job.toStored);
        if (this.huntWaypointNear(job.set, job.huntPrefix, x, z, job.tooClose)) return false;

        this.addHuntWaypoint(job.set, job.huntPrefix, job.firstNumber + job.placed, x, z);
        job.placed++;

        if (job.placed >= 1000) {
            this.warning("A thousand marks placed -- stopping here; press again to carry on.");
            return true;
        }

        return false;
    }

    /** Whether the map is still showing the world the fill was started on. */
    private boolean mapViewChanged(MapProcessor mapProcessor, RegistryKey<World> viewed, int caveLayer) {
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null || session.getMapProcessor() != mapProcessor) return true;
            if (mapProcessor.getCurrentCaveLayer() != caveLayer) return true;

            MapWorld mapWorld = mapProcessor.getMapWorld();
            if (mapWorld == null) return true;

            RegistryKey<World> now = mapWorld.getCurrentDimensionId();
            return now != null && now != viewed;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * The walk over mapped chunks from one chunk toward another, inside a box.
     *
     * <p>Eight-connected A* with the straight-line distance as heuristic: a
     * ribbon a few chunks wide is a corridor, and the search hugs it. When the
     * goal is reached the path ends on it; when it is not -- outside the box,
     * or cut off -- the path ends on the explored chunk closest to it, which
     * on a corridor is where the corridor leaves the box toward the goal. The
     * caller reads which of the two it got from the last element. Capped so a
     * hopeless search costs a moment, not a freeze; each chunk's answer is
     * asked of the map once per walk through the shared cache.
     */
    private List<long[]> traceInBox(MapProcessor mapProcessor, int caveLayer, java.util.Map<Long, Boolean> seen,
                                    int sx, int sz, int gx, int gz, int minX, int maxX, int minZ, int maxZ) {
        long start = chunkKey(sx, sz);
        long goal = chunkKey(gx, gz);

        java.util.PriorityQueue<double[]> open = new java.util.PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
        java.util.HashMap<Long, Double> best = new java.util.HashMap<>();
        java.util.HashMap<Long, Long> from = new java.util.HashMap<>();
        java.util.HashSet<Long> closed = new java.util.HashSet<>();

        best.put(start, 0.0);
        open.add(new double[]{Math.hypot(sx - gx, sz - gz), 0.0, sx, sz});

        long nearest = start;
        double nearestDistance = Math.hypot(sx - gx, sz - gz);
        int expanded = 0;

        while (!open.isEmpty()) {
            double[] node = open.poll();
            int x = (int) node[2];
            int z = (int) node[3];
            long key = chunkKey(x, z);

            if (!closed.add(key)) continue;
            if (key == goal) return this.rebuildPath(from, start, goal);
            if (++expanded > 60_000) break;

            double distance = Math.hypot(x - gx, z - gz);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = key;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    int nx = x + dx;
                    int nz = z + dz;
                    if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;

                    long next = chunkKey(nx, nz);
                    if (closed.contains(next)) continue;
                    if (!this.isChunkSeenOnMap(mapProcessor, caveLayer, seen, nx, nz)) continue;

                    double g = node[1] + (dx != 0 && dz != 0 ? 1.41421356 : 1.0);
                    Double known = best.get(next);
                    if (known != null && known <= g) continue;

                    best.put(next, g);
                    from.put(next, key);
                    open.add(new double[]{g + Math.hypot(nx - gx, nz - gz), g, nx, nz});
                }
            }
        }

        return this.rebuildPath(from, start, nearest);
    }

    private List<long[]> rebuildPath(java.util.Map<Long, Long> from, long start, long goal) {
        List<long[]> path = new ArrayList<>();
        long at = goal;

        while (true) {
            path.add(new long[]{(int) (at >> 32), (int) at});
            if (at == start) break;
            Long previous = from.get(at);
            if (previous == null) break;
            at = previous;
        }

        java.util.Collections.reverse(path);
        return path;
    }

    /** The chunk itself if mapped, else the nearest mapped one within the radius, else MIN_VALUE. */
    private long nearestSeenChunk(MapProcessor mapProcessor, int caveLayer, java.util.Map<Long, Boolean> seen, int x, int z, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    if (this.isChunkSeenOnMap(mapProcessor, caveLayer, seen, x + dx, z + dz)) return chunkKey(x + dx, z + dz);
                }
            }
        }

        return Long.MIN_VALUE;
    }

    /**
     * Whether the world map holds this chunk — the same answer XaeroPlus reads
     * for its portal detection, from the region the map has in memory.
     */
    private boolean isChunkSeenOnMap(MapProcessor mapProcessor, int caveLayer, java.util.Map<Long, Boolean> seen, int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        Boolean cached = seen.get(key);
        if (cached != null) return cached;

        boolean answer = false;

        try {
            MapRegion region = mapProcessor.getLeafMapRegion(
                caveLayer,
                ChunkUtils.chunkCoordToMapRegionCoord(chunkX),
                ChunkUtils.chunkCoordToMapRegionCoord(chunkZ),
                false);

            if (region != null) {
                MapTileChunk chunk = region.getChunk(
                    ChunkUtils.chunkCoordToMapTileChunkCoordLocal(chunkX),
                    ChunkUtils.chunkCoordToMapTileChunkCoordLocal(chunkZ));

                if (chunk != null) {
                    answer = ((SeenChunksTrackingMapTileChunk) chunk).getSeenTiles()
                        [ChunkUtils.chunkCoordToMapTileCoordLocal(chunkX)][ChunkUtils.chunkCoordToMapTileCoordLocal(chunkZ)];
                }
            }
        } catch (Exception ignored) {}

        seen.put(key, answer);
        return answer;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * One breadcrumb along whatever you fly, at most one per seed-spacing.
     *
     * <p>The distance is measured in overworld blocks from the last mark, not
     * from where the module thinks you should be: the chain records the flight
     * that actually happened, curves and all. Saves are batched — writing the
     * world file on every mark is the thing that used to freeze the game.
     */
    private void recordBreadcrumb(ClientPlayerEntity player) {
        Vec3d at = player.getEntityPos();
        double owX = at.getX() * 8.0;
        double owZ = at.getZ() * 8.0;

        if (!Double.isNaN(this.lastSeedOwX)
            && Math.hypot(owX - this.lastSeedOwX, owZ - this.lastSeedOwZ) < this.seedSpacing.get()) {
            this.flushSeedSaveIfDue();
            return;
        }

        // Forced again at every drop, not only at activation: unticked
        // mid-session, the world resolved below becomes the Nether's own, and
        // an overworld position written there lands eight times too far on the
        // map -- the exact thing this feature promises never to do. Unreachable
        // settings mean an unknowable frame, so nothing is placed at all.
        try {
            if (!Settings.REGISTRY.owAutoWaypointDimension.get()) {
                Settings.REGISTRY.owAutoWaypointDimension.setValue(true);
                if (this.showChatMessages.get()) this.info("Enabled XaeroPlus 'Prefer Overworld Waypoints'");
            }
        } catch (Exception e) {
            return;
        }

        MinimapWorld waypointWorld = this.getWaypointWorld();
        if (waypointWorld == null) return;

        WaypointSet set = waypointWorld.getCurrentWaypointSet();
        if (set == null) return;

        // The mark is owed from here whatever happens below: a spot already
        // held still means "this stretch is covered", and asking again every
        // tick until the answer changes would scan the set twenty times a
        // second for nothing.
        this.lastSeedOwX = owX;
        this.lastSeedOwZ = owZ;

        String prefix = this.waypointPrefix.get();
        int x = (int) Math.round(owX);
        int z = (int) Math.round(owZ);

        if (this.huntWaypointNear(set, prefix, x, z, this.seedSpacing.get() * 0.4)) return;

        this.addHuntWaypoint(set, prefix, this.getNextWaypointNumber(), x, z);
        this.seedSaveDirty = true;

        if (this.showChatMessages.get()) {
            this.info("Route mark %d dropped.".formatted(this.getNextWaypointNumber() - 1));
        }

        try {
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
        } catch (Exception ignored) {}

        this.flushSeedSaveIfDue();
    }

    private boolean huntWaypointNear(WaypointSet set, String prefix, int x, int z, double radius) {
        for (Waypoint wp : set.getWaypoints()) {
            if (wp.getName() == null || !wp.getName().startsWith(prefix)) continue;
            if (Math.hypot(wp.getX() - x, wp.getZ() - z) < radius) return true;
        }

        return false;
    }

    private void addHuntWaypoint(WaypointSet set, String prefix, int number, int x, int z) {
        String symbol = prefix.isEmpty() ? "H" : String.valueOf(Character.toUpperCase(prefix.charAt(0)));
        int y = mc.player == null ? 64 : (int) mc.player.getEntityPos().getY();
        set.add(new Waypoint(x, y, z, prefix + number, symbol, 14, 0, false));
    }

    private void flushSeedSaveIfDue() {
        if (!this.seedSaveDirty) return;
        if (System.currentTimeMillis() - this.lastSeedSaveMs < SEED_SAVE_MS) return;
        this.flushSeedSave();
    }

    private void flushSeedSave() {
        if (!this.seedSaveDirty) return;

        this.seedSaveDirty = false;
        this.lastSeedSaveMs = System.currentTimeMillis();

        MinimapWorld waypointWorld = this.getWaypointWorld();
        if (waypointWorld != null) this.saveWaypointWorld(waypointWorld);
    }

    private void saveWaypointWorld(MinimapWorld waypointWorld) {
        try {
            MinimapSession session = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session != null) session.getWorldManagerIO().saveWorld(waypointWorld);
        } catch (Exception ignored) {}
    }

    // ---- HUD readings ----

    /**
     * What the follower is doing, or why it is not, one answer at a time.
     *
     * <p>The order is the priority: the first condition that holds wins, so a
     * paused follower with an empty rocket box reads PAUSED — the thing the
     * player did on purpose — and not the shortage underneath it.
     */
    public enum HudState {
        RECORDING,
        LOADING,
        PAUSED,
        AREA_LOADER,
        NO_WAYPOINTS,
        NO_ELYTRA,
        NO_ROCKETS,
        STARTING,
        WAIT_GLIDE,
        FLYING
    }

    public HudState hudState() {
        if (this.recordRoute.get()) return HudState.RECORDING;
        if (!this.waypointsFullyLoaded) return HudState.LOADING;
        if (this.isPaused) return HudState.PAUSED;
        if (this.areaLoaderSteering) return HudState.AREA_LOADER;
        if (this.waypointsToFollow.isEmpty()) return HudState.NO_WAYPOINTS;
        if (this.flightNeedsElytra() && !this.hasElytraEquipped()) return HudState.NO_ELYTRA;
        if (this.flightNeedsElytra() && this.rocketCount == 0) return HudState.NO_ROCKETS;
        if (this.autoStartFlight.get() && !this.startupRecastTriggered) return HudState.STARTING;
        if (this.startupRecastTriggered && !this.baritoneActivatedAfterStartup) return HudState.WAIT_GLIDE;
        return HudState.FLYING;
    }

    /** Whether, where we are now, this module flies by handing Baritone the goal. */
    public boolean fliesWithBaritone() {
        // Read off the world, not the cached flag: this is asked while the module is switched off.
        return mc.world != null && this.isInNether(mc.world) && this.netherFlightMode.get() == NetherFlightMode.Baritone;
    }

    /** Whether the way this dimension is flown involves wings at all. */
    private boolean flightNeedsElytra() {
        if (this.isInNether) return this.netherFlightMode.get() == NetherFlightMode.Baritone;
        FlightMode mode = this.isInEnd ? this.endFlightMode.get() : this.overworldFlightMode.get();
        return mode != FlightMode.None || this.autoStartFlight.get();
    }

    /** The flight mode this dimension actually uses, for the state line. */
    public String hudModeName() {
        if (this.isInNether) return this.netherFlightMode.get() == NetherFlightMode.Baritone ? "BARITONE" : "MANUAL";
        FlightMode mode = this.isInEnd ? this.endFlightMode.get() : this.overworldFlightMode.get();
        return mode == FlightMode.None ? "MANUAL" : mode.toString().toUpperCase(java.util.Locale.ROOT);
    }

    public String hudDimensionName() {
        if (this.isInNether) return "NETHER";
        if (this.isInEnd) return "END";
        return "OVERWORLD";
    }

    /** The waypoint being flown to, in the player's own dimension, or null. */
    public Vec3d hudTargetPos() {
        // Empty answers while the loader thread still owns the list: these
        // getters run on the render thread every frame, and reading a list the
        // loader is clearing is an IndexOutOfBounds in the middle of a frame.
        if (!this.waypointsFullyLoaded) return null;

        BlockPos next = this.getNextWaypoint();
        if (next == null) return null;

        if (this.isInNether) {
            return new Vec3d(this.toNetherCoord(next.getX()) + 0.5, next.getY() + 0.5,
                this.toNetherCoord(next.getZ()) + 0.5);
        }

        return this.getAdjustedWaypointPos(next);
    }

    /** Horizontal blocks to the current waypoint, or -1 without one. */
    public double hudDistance() {
        Vec3d target = this.hudTargetPos();
        if (target == null || mc.player == null) return -1.0;
        return this.getHorizontalDistance(mc.player.getEntityPos(), target);
    }

    /** Yaw toward the current waypoint, or NaN without one. */
    public float hudTargetYaw() {
        Vec3d target = this.hudTargetPos();
        if (target == null || mc.player == null) return Float.NaN;
        return this.hudYawToward(target);
    }

    /** Any waypoint of the list, in the player's own dimension. */
    public Vec3d hudPosOf(BlockPos waypoint) {
        return this.isInNether
            ? new Vec3d(this.toNetherCoord(waypoint.getX()) + 0.5, waypoint.getY() + 0.5,
                this.toNetherCoord(waypoint.getZ()) + 0.5)
            : this.getAdjustedWaypointPos(waypoint);
    }

    /** Yaw toward any waypoint of the list, dimension conversion included. */
    public float hudYawTo(BlockPos waypoint) {
        if (mc.player == null) return Float.NaN;
        return this.hudYawToward(this.hudPosOf(waypoint));
    }

    private float hudYawToward(Vec3d target) {
        Vec3d at = mc.player.getEntityPos();
        return (float) (Math.atan2(target.getZ() - at.getZ(), target.getX() - at.getX()) * 180.0 / Math.PI) - 90.0F;
    }

    /** The reach that will count as arrived, in the player's dimension. */
    public double hudReach() {
        return this.reachDistance.get() * (this.isInNether ? this.netherReachMultiplier.get() : 1.0);
    }

    public double hudYawDeadzone() {
        return this.yawDeadzone.get();
    }

    /** Blocks per second, smoothed over about five seconds. */
    public double hudSpeed() {
        return this.smoothedSpeed;
    }

    /** Seconds to the current waypoint, or -1 while there is no speed to divide by. */
    public int hudEtaSeconds() {
        double distance = this.hudDistance();
        if (distance < 0.0 || this.smoothedSpeed < 2.0) return -1;
        return (int) (distance / this.smoothedSpeed);
    }

    /** The remaining route, in following order. Read-only, never copied per frame. */
    public List<BlockPos> hudRoute() {
        if (!this.waypointsFullyLoaded) return List.of();
        return java.util.Collections.unmodifiableList(this.waypointsToFollow);
    }

    /** Index of the current target inside {@link #hudRoute}, or -1. */
    public int hudRouteIndex() {
        if (!this.waypointsFullyLoaded) return -1;
        BlockPos next = this.getNextWaypoint();
        return next == null ? -1 : this.waypointsToFollow.indexOf(next);
    }

    /** Whether the route is visited nearest-first rather than in numeric order. */
    public boolean hudClosestMode() {
        return this.followMode.get() == FollowMode.Closest;
    }

    public int hudRemaining() {
        return this.waypointsFullyLoaded ? this.waypointsToFollow.size() : 0;
    }

    public int hudCompleted() {
        return this.waypointsCompletedThisSession;
    }

    public int hudRockets() {
        return this.rocketCount;
    }

    /** Durability of the worn elytra in percent, or -1 when none is worn. */
    public double hudElytraPercent() {
        if (mc.player == null) return -1.0;
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA || chest.getMaxDamage() <= 0) return -1.0;
        return (chest.getMaxDamage() - chest.getDamage()) / (double) chest.getMaxDamage() * 100.0;
    }

    public int hudLoadRetries() {
        return this.waypointLoadRetries;
    }

    public long hudSessionSeconds() {
        return this.activatedAtMs == 0L ? 0L : (System.currentTimeMillis() - this.activatedAtMs) / 1000L;
    }

    public double hudBlocksTraveled() {
        return this.totalDistanceTraveled;
    }

    /** When the last waypoint was reached, for the fade on the route line. */
    public long hudLastCompletedAt() {
        return this.lastCompletedAtMs;
    }

    /**
     * The firework count, refreshed once a second.
     *
     * <p>Scanned rather than tracked, because rockets leave the inventory by
     * more hands than this module's own; once a second because thirty-six
     * stacks twenty times a second is a price for no gain.
     */
    private void updateRockets(ClientPlayerEntity player) {
        if (--this.rocketScanTimer > 0) return;
        this.rocketScanTimer = 20;

        int count = 0;
        var inv = player.getInventory();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(Items.FIREWORK_ROCKET)) count += stack.getCount();
        }

        ItemStack off = player.getOffHandStack();
        if (off.isOf(Items.FIREWORK_ROCKET)) count += off.getCount();

        this.rocketCount = count;
    }

    public enum FlightMode {
        RocketFly("RocketFly"), Pitch40("Pitch40"), None("None");
        private final String name;
        FlightMode(String name) { this.name = name; }
        @Override public String toString() { return this.name; }
    }

    public enum SeedMode {
        Spacing("Spacing"), PortalPatches("PortalPatches");
        private final String name;
        SeedMode(String name) { this.name = name; }
        @Override public String toString() { return this.name; }
    }

    public enum FollowMode {
        Closest("Closest"), Numerical("Numerical");
        private final String name;
        FollowMode(String name) { this.name = name; }
        @Override public String toString() { return this.name; }
    }

    public enum NetherFlightMode {
        Baritone("Baritone"), None("None");
        private final String name;
        NetherFlightMode(String name) { this.name = name; }
        @Override public String toString() { return this.name; }
    }
}
