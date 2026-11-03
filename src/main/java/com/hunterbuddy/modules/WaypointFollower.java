package com.hunterbuddy.modules;

import com.hunterbuddy.util.QuietClicks;
import com.hunterbuddy.util.Rockets;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.waypointfollow.BotWaypoints;
import com.hunterbuddy.modules.waypointfollow.ElytraRecastStarter;
import com.hunterbuddy.modules.waypointfollow.ElytraRecastStarter.Ascent;
import com.hunterbuddy.modules.waypointfollow.FlightModules;
import com.hunterbuddy.modules.waypointfollow.FollowerRocket;
import com.hunterbuddy.modules.waypointfollow.FollowerWaypointWorld;
import com.hunterbuddy.modules.waypointfollow.LavaEscapeTerrain;
import com.hunterbuddy.modules.waypointfollow.WaypointLoader;
import com.hunterbuddy.modules.waypointfollow.WaypointOrdering;
import com.hunterbuddy.modules.waypointfollow.XaeroMapFocus;
import com.hunterbuddy.modules.waypointfollow.YawSteering;
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
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
    private static final long SEED_SAVE_MS = 15000L;
    private static final int BARITONE_RESTART_DELAY_TICKS = 100;
    private static final int BARITONE_RESTART_MAX_ATTEMPTS = 3;
    private static final int LAVA_ESCAPE_REPATH_TICKS = 80;
    private static final int LAVA_ESCAPE_ARM_TICKS = 20;
    private static final int LAVA_ESCAPE_GIVE_UP_TICKS = 600;
    private static final int LAVA_ESCAPE_BLOCKS = 4;
    private static final int BARITONE_RESTART_MOVED_BLOCKS = 8;
    private static final int RELOAD_CHECK_TICKS = 40;
    private static final double PORTAL_JUMP_BLOCKS = 100.0;
    private static final double SPEED_SMOOTHING = 0.01;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final float STEEP_CLIMB_PITCH = -35.0F;
    private static final double FALLING_VELOCITY = -0.05;
    private static final int GLIDE_CONFIRM_TICKS = 2;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgFlight = this.settings.createGroup("Flight");
    private final SettingGroup sgControl = this.settings.createGroup("Control");

    private final Setting<String> waypointPrefix = this.sgGeneral.add(new StringSetting.Builder()
        .name("waypoint-prefix")
        .description("Name prefix of the waypoints to follow.")
        .defaultValue("Hunt_")
        .build());

    private final Setting<FollowMode> followMode = this.sgGeneral.add(new EnumSetting.Builder<FollowMode>()
        .name("follow-mode")
        .description("Closest goes to the nearest waypoint first, Numerical goes in numeric order.")
        .defaultValue(FollowMode.Numerical)
        .build());

    private final Setting<RouteEndAction> routeEnd = this.sgGeneral.add(new EnumSetting.Builder<RouteEndAction>()
        .name("route-end")
        .description("What to do once the last waypoint is reached.")
        .defaultValue(RouteEndAction.OFF_MODULES)
        .build());

    private final Setting<Double> reachDistance = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("reach-distance")
        .description("Horizontal distance at which a waypoint counts as reached.")
        .defaultValue(20.0)
        .min(1.0)
        .max(50.0)
        .sliderRange(1.0, 100.0)
        .build());

    private final Setting<Boolean> showStatistics = this.sgGeneral.add(new BoolSetting.Builder()
        .name("show-statistics")
        .description("Shows the route progress on the action bar.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> showChatMessages = this.sgGeneral.add(new BoolSetting.Builder()
        .name("show-chat-messages")
        .description("Reports what the follower is doing in the chat.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> completionSound = this.sgGeneral.add(new BoolSetting.Builder()
        .name("completion-sound")
        .description("Plays a sound when every waypoint has been reached.")
        .defaultValue(true)
        .build());

    private final Setting<Keybind> addWaypointKey = this.sgControl.add(new KeybindSetting.Builder()
        .name("add-waypoint-key")
        .description("Adds a hunt waypoint at the mouse position on Xaero's map.")
        .defaultValue(Keybind.fromKey(82))
        .build());

    public final Setting<Keybind> clearWaypoints = this.sgControl.add(new KeybindSetting.Builder()
        .name("clear-waypoints")
        .description("Clears every hunt waypoint and every route mark.")
        .defaultValue(Keybind.none())
        .action(this::clearAllFollowWaypointsAction)
        .build());

    public final Setting<Keybind> skipWaypointKey = this.sgControl.add(new KeybindSetting.Builder()
        .name("skip-waypoint")
        .description("Skips the waypoint being flown to.")
        .defaultValue(Keybind.none())
        .action(this::skipCurrentWaypointAction)
        .build());

    public final Setting<Keybind> pauseResumeKey = this.sgControl.add(new KeybindSetting.Builder()
        .name("pause-resume")
        .description("Pauses or resumes the follower.")
        .defaultValue(Keybind.none())
        .action(this::togglePause)
        .build());

    private final Setting<Integer> seedSpacing = this.sgControl.add(new IntSetting.Builder()
        .name("seed-spacing")
        .description("Overworld blocks between two seeded waypoints. Two overworld portals closer than 128 blocks link to each other.")
        .defaultValue(2000)
        .min(128)
        .max(16000)
        .sliderRange(500, 8000)
        .build());

    private final Setting<SeedMode> seedMode = this.sgControl.add(new EnumSetting.Builder<SeedMode>()
        .name("seed-mode")
        .description("Spacing marks every seed-spacing blocks of the walk, PortalPatches only where the map holds a full portal square.")
        .defaultValue(SeedMode.PortalPatches)
        .build());

    private final Setting<Integer> portalRadius = this.sgControl.add(new IntSetting.Builder()
        .name("portal-radius")
        .description("Side in chunks of the mapped square that says a portal could stand there.")
        .defaultValue(21)
        .min(7)
        .max(41)
        .sliderRange(11, 31)
        .visible(() -> this.seedMode.get() == SeedMode.PortalPatches)
        .build());

    private final Setting<String> routePrefix = this.sgControl.add(new StringSetting.Builder()
        .name("route-prefix")
        .description("Name prefix of the control marks that seed-route reads.")
        .defaultValue("Route_")
        .build());

    private final Setting<Keybind> addRouteKey = this.sgControl.add(new KeybindSetting.Builder()
        .name("add-route-key")
        .description("Drops a route control mark at the map mouse position, numbered in drop order.")
        .defaultValue(Keybind.none())
        .build());

    private final Setting<Keybind> seedRouteKey = this.sgControl.add(new KeybindSetting.Builder()
        .name("seed-route")
        .description("Fills a hunt chain along the control marks.")
        .defaultValue(Keybind.none())
        .action(this::seedRouteFromMap)
        .build());

    private final Setting<Boolean> recordRoute = this.sgControl.add(new BoolSetting.Builder()
        .name("nether-record-route")
        .description("Recording session: the module never flies, and in the Nether it drops a hunt waypoint every seed-spacing blocks along your own flight.")
        .defaultValue(false)
        .build());

    private final Setting<NetherFlightMode> netherFlightMode = this.sgFlight.add(new EnumSetting.Builder<NetherFlightMode>()
        .name("nether-flight-mode")
        .description("How the Nether is crossed.")
        .defaultValue(NetherFlightMode.Baritone)
        .build());

    private final Setting<FlightMode> overworldFlightMode = this.sgFlight.add(new EnumSetting.Builder<FlightMode>()
        .name("overworld-flight-mode")
        .description("How the Overworld is crossed.")
        .defaultValue(FlightMode.Pitch40)
        .build());

    private final Setting<FlightMode> endFlightMode = this.sgFlight.add(new EnumSetting.Builder<FlightMode>()
        .name("end-flight-mode")
        .description("How the End is crossed.")
        .defaultValue(FlightMode.Pitch40)
        .build());

    private final Setting<Boolean> snapRotation = this.sgFlight.add(new BoolSetting.Builder()
        .name("snap-rotation")
        .description("Snaps the yaw to the heading instantly.")
        .defaultValue(false)
        .build());

    private final Setting<Double> rotationSpeed = this.sgFlight.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Fraction of the yaw error corrected per tick.")
        .defaultValue(0.1)
        .min(0.01)
        .max(1.0)
        .sliderRange(0.01, 0.5)
        .visible(() -> !this.snapRotation.get())
        .build());

    private final Setting<Double> yawDeadzone = this.sgFlight.add(new DoubleSetting.Builder()
        .name("yaw-deadzone")
        .description("Yaw error in degrees below which no correction is made.")
        .defaultValue(0.5)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 5.0)
        .build());

    private final Setting<Double> netherReachMultiplier = this.sgFlight.add(new DoubleSetting.Builder()
        .name("nether-reach-multiplier")
        .description("Multiplies the reach distance in the Nether.")
        .defaultValue(5.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .build());

    private final Setting<Boolean> autoStartFlight = this.sgFlight.add(new BoolSetting.Builder()
        .name("auto-start-flight")
        .description("Lets ElytraRecast start the flight after a relog or an activation.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> startupDelay = this.sgFlight.add(new IntSetting.Builder()
        .name("startup-delay")
        .description("Ticks to wait after activation before starting the flight.")
        .defaultValue(5)
        .min(0)
        .max(40)
        .sliderRange(0, 40)
        .visible(this.autoStartFlight::get)
        .build());

    private final List<BlockPos> waypointsToFollow = new ArrayList<>();
    private final FlightModules flightModules = new FlightModules();
    private final FollowerRocket rocket = new FollowerRocket();
    private final WaypointLoader loader = new WaypointLoader(
        () -> this.waypointsFullyLoaded, this::attemptWaypointLoad, this::reportLoadStalled, this::reportLoadError);

    private boolean isInNether;
    private boolean isInEnd;
    private int currentWaypointIndex;
    private BlockPos currentBaritoneTarget;
    private int baritoneRestartAttempts;
    private BlockPos baritoneRestartPos;
    private int baritoneRestartCooldown;
    private boolean isPaused;
    private int waypointsCompletedThisSession;
    private double totalDistanceTraveled;
    private Vec3d lastPlayerPos;
    private FlightMode activeFlightMode = FlightMode.None;
    private int lastKnownWaypointCount;
    private int reloadCheckTimer;
    private int startupDelayTicks;
    private boolean startupRecastTriggered;
    private boolean recastEnabledForStartup;
    private int glidingTicksAfterStartup;
    private boolean baritoneActivatedAfterStartup;
    private int waypointLoadRetries;
    private BlockPos lavaEscapeGoal;
    private int lavaEscapeCooldown;
    private Direction lavaEscapeDirection;
    private BlockPos lavaEscapeMinePos;
    private int lavaEscapeMineTicks;
    private int lavaEscapeLavaTicks;
    private int lavaEscapeTotalTicks;
    private boolean lavaEscapeEngaged;
    private volatile boolean waypointsFullyLoaded;
    private String lastDimensionKey = "";
    private volatile boolean isDeactivating;
    private volatile boolean areaLoaderSteering;
    private double lastSeedOwX = Double.NaN;
    private double lastSeedOwZ = Double.NaN;
    private boolean seedSaveDirty;
    private long lastSeedSaveMs;
    private double smoothedSpeed;
    private int rocketCount;
    private int rocketScanTimer;
    private long activatedAtMs;
    private long lastCompletedAtMs;
    private boolean routeEndPending;

    public WaypointFollower() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "waypoint-follower",
            "Advanced waypoint following system with multi-dimensional flight support.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null || mc.player == null) return;
        this.flightModules.bind();
        if (FollowerWaypointWorld.enablePreferOverworld()) this.say("Enabled XaeroPlus 'Prefer Overworld Waypoints'");
        this.resetSession();
        this.startAsyncWaypointLoad();
        this.activeFlightMode = FlightMode.None;
    }

    @Override
    public void onDeactivate() {
        this.flushSeedSave();
        this.isDeactivating = true;
        this.areaLoaderSteering = false;
        this.stopFlightMode();
        this.stopBaritone();
        this.releaseMovementKeys();
        RotationUtils.getInstance().clearRotations();
        this.loader.stop();
        this.flightModules.releaseAll();
        this.clearRouteState();
        this.reportSessionSummary();
    }

    private void resetSession() {
        this.resetSessionFlags();
        this.resetRouteList();
        this.resetStartup();
        this.resetLoadState();
        this.resetTrackingStats();
    }

    private void resetSessionFlags() {
        this.isDeactivating = false;
        this.areaLoaderSteering = this.isAreaLoaderActive();
        this.refreshDimensionFlags();
        this.isPaused = false;
        this.waypointsCompletedThisSession = 0;
        this.totalDistanceTraveled = 0.0;
        this.lastPlayerPos = mc.player.getEntityPos();
    }

    private void resetRouteList() {
        this.waypointsToFollow.clear();
        this.currentWaypointIndex = 0;
        this.recentRemovals.clear();
    }

    private void resetLoadState() {
        this.waypointLoadRetries = 0;
        this.waypointsFullyLoaded = false;
        this.lastDimensionKey = this.getCurrentDimensionKey();
    }

    private void resetTrackingStats() {
        this.lastSeedOwX = Double.NaN;
        this.lastSeedOwZ = Double.NaN;
        this.smoothedSpeed = 0.0;
        this.rocketCount = 0;
        this.rocketScanTimer = 0;
        this.activatedAtMs = System.currentTimeMillis();
        this.lastCompletedAtMs = 0L;
        this.routeEndPending = false;
    }

    private void resetStartup() {
        this.startupDelayTicks = 0;
        this.startupRecastTriggered = false;
        this.recastEnabledForStartup = false;
        this.baritoneActivatedAfterStartup = false;
        this.glidingTicksAfterStartup = 0;
    }

    private void clearRouteState() {
        this.waypointsToFollow.clear();
        this.currentBaritoneTarget = null;
        this.activeFlightMode = FlightMode.None;
        this.rocket.reset();
        this.isPaused = false;
        this.waypointsFullyLoaded = false;
        this.resetStartup();
    }

    private void reportSessionSummary() {
        if (this.waypointsCompletedThisSession == 0) return;
        this.say("Session over: " + this.waypointsCompletedThisSession + " waypoints reached, "
            + String.format("%.1f", this.totalDistanceTraveled) + " blocks traveled");
    }

    private boolean isAreaLoaderActive() {
        AreaLoader areaLoader = Modules.get().get(AreaLoader.class);
        return areaLoader != null && areaLoader.isActive();
    }

    private void say(String message) {
        if (this.showChatMessages.get()) this.info(message);
    }

    private void sayWarning(String message) {
        if (this.showChatMessages.get()) this.warning(message);
    }

    private void sayError(String message) {
        if (this.showChatMessages.get()) this.error(message);
    }

    private void releaseMovementKeys() {
        Utils.setPressed(mc.options.forwardKey, false);
        Utils.setPressed(mc.options.backKey, false);
        Utils.setPressed(mc.options.leftKey, false);
        Utils.setPressed(mc.options.rightKey, false);
        Utils.setPressed(mc.options.jumpKey, false);
        Utils.setPressed(mc.options.sneakKey, false);
    }

    public void releaseAreaLoaderSteering() {
        this.areaLoaderSteering = false;
        this.purgeBotManagedWaypoints();
        this.stopBaritone();
        this.releaseMovementKeys();
        RotationUtils.getInstance().clearRotations();
    }

    public void untrackWaypointsByNamePrefix(String namePrefix) {
        if (namePrefix == null || namePrefix.isEmpty()) return;
        this.removeBotPositions(BotWaypoints.positionsFor(this.getWaypointWorld(), namePrefix));
    }

    public void purgeBotManagedWaypoints() {
        this.removeBotPositions(BotWaypoints.positionsForAll(this.getWaypointWorld()));
    }

    private void removeBotPositions(Set<BlockPos> botPositions) {
        boolean flyingToBot = this.currentBaritoneTarget != null && botPositions.contains(this.currentBaritoneTarget);
        if (flyingToBot) this.stopBaritone();
        this.waypointsToFollow.removeAll(botPositions);
        this.pruneUntrackableWaypoints();
    }

    private void pruneUntrackableWaypoints() {
        if (this.waypointsToFollow.isEmpty()) return;
        MinimapWorld world = this.getWaypointWorld();
        this.waypointsToFollow.removeAll(BotWaypoints.positionsForAll(world));
        try {
            WaypointSet set = FollowerWaypointWorld.currentSet(world);
            if (set == null) return;
            Set<BlockPos> valid = WaypointOrdering.enabledPositions(set, this.waypointPrefix.get());
            this.waypointsToFollow.removeIf(position -> !valid.contains(position));
        } catch (Exception ignored) {
        }
    }

    private boolean movedSinceLastRestart() {
        return this.baritoneRestartPos != null && mc.player != null
            && mc.player.getBlockPos().getSquaredDistance(this.baritoneRestartPos)
                > BARITONE_RESTART_MOVED_BLOCKS * BARITONE_RESTART_MOVED_BLOCKS;
    }

    private void clearAllFollowWaypointsAction() {
        if (XaeroMapFocus.textFieldFocused()) return;
        this.clearAllFollowWaypoints();
        this.waypointsToFollow.clear();
        this.currentWaypointIndex = 0;
        this.say("Cleared every hunt waypoint and route mark");
    }

    private void skipCurrentWaypointAction() {
        if (XaeroMapFocus.textFieldFocused() || !this.isActive()) return;
        int remaining = this.skipCurrentWaypoint();
        if (remaining >= 0) this.say("Waypoint skipped, " + remaining + " remaining");
    }

    private void togglePause() {
        if (XaeroMapFocus.textFieldFocused() || !this.isActive()) return;
        this.isPaused = !this.isPaused;
        if (!this.isPaused) {
            this.say("Resumed");
            return;
        }
        this.stopFlightMode();
        this.stopBaritone();
        this.say("Paused");
    }

    public int skipCurrentWaypoint() {
        BlockPos next = this.getNextWaypoint();
        if (next == null) return -1;
        this.removeCurrentWaypoint(next, Outcome.SKIPPED);
        return this.waypointsToFollow.size();
    }

    public void pause() {
        if (this.isPaused) return;
        this.isPaused = true;
        this.stopFlightMode();
        this.stopBaritone();
    }

    public int getNextWaypointNumber() {
        try {
            WaypointSet set = FollowerWaypointWorld.currentSet(this.getWaypointWorld());
            return set == null ? 0 : WaypointOrdering.nextNumber(set, this.waypointPrefix.get());
        } catch (Exception e) {
            return this.waypointsToFollow.size();
        }
    }

    public int getTotalHuntWaypointCount() {
        try {
            WaypointSet set = FollowerWaypointWorld.currentSet(this.getWaypointWorld());
            return set == null ? 0 : WaypointOrdering.countFollowable(set, this.waypointPrefix.get());
        } catch (Exception e) {
            return this.waypointsToFollow.size();
        }
    }

    public void addWaypointToTrack(int x, int y, int z) {
        this.addWaypointToFollow(new BlockPos(x, y, z));
        if (this.isActive()) this.say("Waypoint added to the tracking list");
    }

    private boolean isInNether(World world) {
        return world != null && world.getRegistryKey() == World.NETHER;
    }

    private boolean isInEnd(World world) {
        return world != null && world.getRegistryKey() == World.END;
    }

    private void refreshDimensionFlags() {
        this.isInNether = this.isInNether(mc.world);
        this.isInEnd = this.isInEnd(mc.world);
    }

    private String getCurrentDimensionKey() {
        return mc.world == null ? "unknown" : mc.world.getRegistryKey().getValue().toString();
    }

    private boolean isPreferOverworldWaypointsEnabled() {
        return FollowerWaypointWorld.preferOverworld();
    }

    private MinimapWorld getWaypointWorld() {
        return this.isInEnd ? FollowerWaypointWorld.resolveInEnd() : FollowerWaypointWorld.resolve();
    }

    private double getDimensionDivision() {
        return FollowerWaypointWorld.dimensionDivision();
    }

    private void startAsyncWaypointLoad() {
        this.loader.start();
    }

    private int extractOrderNumber(String waypointName, String prefix) {
        return WaypointOrdering.orderNumber(waypointName, prefix);
    }

    private void attemptWaypointLoad() {
        this.waypointLoadRetries++;
        FollowerWaypointWorld.requestRefresh();
        try {
            List<BlockPos> loaded = WaypointOrdering.loadOrdered(this.getWaypointWorld(), this.waypointPrefix.get());
            this.reportOnGameThread(() -> this.applyLoaded(loaded));
        } catch (Exception e) {
            this.reportLoadFailure(e);
        }
    }

    private void applyLoaded(List<BlockPos> loaded) {
        try {
            if (loaded == null) {
                this.giveUpWhenRetriesRunOut();
            } else {
                this.publishLoaded(loaded);
            }
        } catch (Exception e) {
            this.reportLoadFailure(e);
        }
    }

    private void giveUpWhenRetriesRunOut() {
        if (this.waypointLoadRetries < WaypointLoader.MAX_ATTEMPTS) return;
        this.reportOnGameThread(() -> this.sayWarning("Xaero waypoints could not be reached, giving up loading"));
        this.waypointsFullyLoaded = true;
    }

    private void publishLoaded(List<BlockPos> loaded) {
        this.waypointsToFollow.clear();
        this.currentWaypointIndex = 0;
        loaded.forEach(this::addWaypointToFollow);
        this.waypointsFullyLoaded = true;
        this.lastKnownWaypointCount = this.getTotalHuntWaypointCount();
        this.announceLoaded(loaded.size());
    }

    private void announceLoaded(int count) {
        String origin = this.isInEnd ? " from the End" : "";
        String text = count > 0
            ? "Loaded " + count + " hunt waypoints" + origin + " (attempt " + this.waypointLoadRetries + ")"
            : "No waypoints starting with " + this.waypointPrefix.get() + " were found";
        this.reportOnGameThread(() -> this.say(text));
    }

    private void reportLoadFailure(Exception failure) {
        if (this.waypointLoadRetries < WaypointLoader.MAX_ATTEMPTS) return;
        this.reportOnGameThread(() -> this.sayError("Waypoint loading failed: " + failure.getMessage()));
    }

    private void reportLoadStalled() {
        this.reportOnGameThread(() -> this.sayWarning(
            "Waypoints may not have loaded, open the world map by hand to force a refresh"));
    }

    private void reportLoadError(String message) {
        this.reportOnGameThread(() -> this.sayError("Waypoint loader error: " + message));
    }

    private void reportOnGameThread(Runnable report) {
        if (mc.isOnThread()) {
            report.run();
        } else {
            mc.execute(report);
        }
    }

    private void addWaypointToFollow(BlockPos pos) {
        if (!this.waypointsToFollow.contains(pos)) this.waypointsToFollow.add(pos);
    }

    private BlockPos getNextWaypoint() {
        if (this.waypointsToFollow.isEmpty()) return null;
        if (this.followMode.get() == FollowMode.Closest) return this.getClosestWaypoint();
        if (this.currentWaypointIndex >= this.waypointsToFollow.size()) this.currentWaypointIndex = 0;
        return this.waypointsToFollow.get(this.currentWaypointIndex);
    }

    private BlockPos getClosestWaypoint() {
        if (mc.player == null) return null;
        Vec3d at = mc.player.getEntityPos();
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (BlockPos waypoint : this.waypointsToFollow) {
            double distance = this.getHorizontalDistance(at, Vec3d.ofCenter(waypoint));
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = waypoint;
            }
        }
        return closest;
    }

    private void checkAndReloadWaypoints() {
        if (!this.waypointsFullyLoaded) return;
        if (++this.reloadCheckTimer < RELOAD_CHECK_TICKS) return;
        this.reloadCheckTimer = 0;
        FollowerWaypointWorld.requestRefresh();
        int count = this.getTotalHuntWaypointCount();
        if (count == this.lastKnownWaypointCount) return;
        this.waypointsToFollow.clear();
        this.waypointLoadRetries = 0;
        this.attemptWaypointLoad();
        this.lastKnownWaypointCount = count;
    }

    public void clearAllFollowWaypoints() {
        try {
            MinimapWorld world = this.getWaypointWorld();
            WaypointSet set = FollowerWaypointWorld.currentSet(world);
            if (set == null) {
                this.sayWarning("Xaero waypoints are not available");
                return;
            }
            int removed = this.removeNamedWaypoints(set);
            this.saveAfterClear(world, removed);
            FollowerWaypointWorld.requestRefresh();
        } catch (Exception e) {
            this.sayError("Could not clear the waypoints: " + e.getMessage());
        }
    }

    private int removeNamedWaypoints(WaypointSet set) {
        String huntPrefix = this.waypointPrefix.get();
        String routeMark = this.routePrefix.get();
        List<Waypoint> doomed = new ArrayList<>();
        for (Waypoint waypoint : set.getWaypoints()) {
            String name = waypoint.getName();
            boolean hunt = name != null && name.startsWith(huntPrefix);
            boolean route = name != null && !routeMark.isEmpty() && name.startsWith(routeMark);
            if (hunt || route) doomed.add(waypoint);
        }
        doomed.forEach(set::remove);
        return doomed.size();
    }

    private void saveAfterClear(MinimapWorld world, int removed) {
        try {
            FollowerWaypointWorld.save(world);
            this.say("Removed " + removed + " waypoints and saved them to disk");
        } catch (Exception e) {
            this.sayError("Could not save the waypoint world: " + e.getMessage());
        }
    }

    private void removeFollowWaypoint(BlockPos pos) {
        MinimapWorld world = this.getWaypointWorld();
        WaypointSet set = FollowerWaypointWorld.currentSet(world);
        if (set == null) return;
        Waypoint match = WaypointOrdering.findAt(set, this.waypointPrefix.get(), pos);
        if (match != null) FollowerWaypointWorld.removeAndSave(world, set, match);
    }

    private void saveWaypointWorld(MinimapWorld waypointWorld) {
        FollowerWaypointWorld.saveQuietly(waypointWorld);
    }

    private static OldChunks resolveOldChunks() {
        try {
            return Settings.REGISTRY.oldChunksEnabledSetting.get() ? ModuleManager.getModule(OldChunks.class) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void startFlightMode(FlightMode mode) {
        if (mode == this.activeFlightMode) return;
        this.stopFlightMode();
        if (mode == FlightMode.None) {
            this.activeFlightMode = FlightMode.None;
            return;
        }
        if (this.flightModules.enable(mode)) this.activeFlightMode = mode;
    }

    private void stopFlightMode() {
        this.flightModules.disable(this.activeFlightMode);
        this.activeFlightMode = FlightMode.None;
    }

    private void rotateYawTowards(Vec3d targetPos) {
        if (this.snapRotation.get()) {
            YawSteering.snap(targetPos, this.yawDeadzone.get());
        } else {
            YawSteering.smooth(targetPos, this.yawDeadzone.get(), this.rotationSpeed.get());
        }
    }

    private double getHorizontalDistance(Vec3d from, Vec3d to) {
        return Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
    }

    private Vec3d getAdjustedWaypointPos(BlockPos waypoint) {
        return new Vec3d(this.toPlayerDimensionCoord(waypoint.getX()) + 0.5, waypoint.getY() + 0.5,
            this.toPlayerDimensionCoord(waypoint.getZ()) + 0.5);
    }

    private boolean hasElytraEquipped() {
        return mc.player != null && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private boolean isTextFieldFocusedInXaeroMap() {
        return XaeroMapFocus.textFieldFocused();
    }

    public double hudElytraPercent() {
        if (!this.hasElytraEquipped()) return -1.0;
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        int maxDamage = chest.getMaxDamage();
        if (maxDamage <= 0) return -1.0;
        return (double) (maxDamage - chest.getDamage()) / maxDamage * 100.0;
    }

    private void triggerStartupRecast() {
        ElytraRecast recast = this.flightModules.elytraRecast();
        if (recast == null) {
            this.sayError("ElytraRecast not found");
            return;
        }
        if (recast.isActive()) return;
        this.say("Starting the flight with ElytraRecast");
        this.recastEnabledForStartup = ElytraRecastStarter.start(recast, this.startupAscends() ? Ascent.CLIMB : Ascent.LEVEL);
    }

    private boolean startupAscends() {
        return !this.isInNether && this.dimensionFlightMode() == FlightMode.Pitch40;
    }

    private FlightMode dimensionFlightMode() {
        return this.isInEnd ? this.endFlightMode.get() : this.overworldFlightMode.get();
    }

    private Direction directionToward(BlockPos from, BlockPos to) {
        return LavaEscapeTerrain.directionToward(from, to);
    }

    private boolean isLavaEscapeOpen(BlockPos pos) {
        return LavaEscapeTerrain.isOpen(pos);
    }

    private boolean isLavaEscapeBreakable(BlockPos pos) {
        return LavaEscapeTerrain.isBreakable(pos);
    }

    private void handleNetherPathfinding() {
        if (this.netherFlightMode.get() == NetherFlightMode.Baritone) {
            this.handleNetherBaritonePathfinding();
            return;
        }
        this.handleNetherManualPathfinding();
    }

    private void handleNetherBaritonePathfinding() {
        BlockPos nextWaypoint = this.getNextWaypoint();
        if (nextWaypoint == null) return;
        if (this.handleLavaFootEscape(nextWaypoint)) return;
        if (!this.driveBaritoneToward(nextWaypoint)) return;
        this.completeBaritoneArrival(nextWaypoint);
    }

    private boolean driveBaritoneToward(BlockPos waypoint) {
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
            if (this.needsNewBaritoneGoal(waypoint, baritone)) this.giveBaritoneGoal(baritone, waypoint);
            return true;
        } catch (Exception e) {
            this.sayError("Baritone error: " + e.getMessage());
            return false;
        }
    }

    private boolean needsNewBaritoneGoal(BlockPos waypoint, IBaritone baritone) {
        if (!waypoint.equals(this.currentBaritoneTarget)) {
            this.baritoneRestartAttempts = 0;
            this.baritoneRestartCooldown = 0;
            this.baritoneRestartPos = null;
            return true;
        }
        if (this.baritoneRestartCooldown > 0) {
            this.baritoneRestartCooldown--;
            return false;
        }
        if (!this.shouldRestartBaritone(baritone)) return false;
        this.registerBaritoneRestart();
        return true;
    }

    private boolean shouldRestartBaritone(IBaritone baritone) {
        boolean flying = baritone.getElytraProcess().isActive();
        boolean attemptsLeft = this.baritoneRestartAttempts < BARITONE_RESTART_MAX_ATTEMPTS || this.movedSinceLastRestart();
        return !flying && mc.player.isOnGround() && this.rocketCount > 0 && attemptsLeft;
    }

    private void registerBaritoneRestart() {
        if (this.movedSinceLastRestart()) this.baritoneRestartAttempts = 0;
        this.baritoneRestartAttempts++;
        this.baritoneRestartCooldown = BARITONE_RESTART_DELAY_TICKS;
        this.baritoneRestartPos = mc.player.getBlockPos();
        this.say("Elytra process gone while grounded with rockets left, giving the goal again ("
            + this.baritoneRestartAttempts + "/" + BARITONE_RESTART_MAX_ATTEMPTS + ")");
    }

    private void giveBaritoneGoal(IBaritone baritone, BlockPos waypoint) {
        int goalX = this.toNetherCoord(waypoint.getX());
        int goalZ = this.toNetherCoord(waypoint.getZ());
        this.currentBaritoneTarget = waypoint;
        baritone.getElytraProcess().pathTo(new GoalXZ(goalX, goalZ));
        this.say("Baritone elytra goal set to " + goalX + ", " + goalZ);
    }

    private void completeBaritoneArrival(BlockPos waypoint) {
        if (mc.player == null) return;
        Vec3d at = mc.player.getEntityPos();
        double distance = Math.hypot(at.getX() - this.toNetherCoord(waypoint.getX()),
            at.getZ() - this.toNetherCoord(waypoint.getZ()));
        if (distance > this.netherReach()) return;
        this.removeCurrentWaypoint(waypoint);
        this.currentBaritoneTarget = null;
    }

    private double netherReach() {
        return this.reachDistance.get() * this.netherReachMultiplier.get();
    }

    private void handleNetherManualPathfinding() {
        BlockPos waypoint = this.getNextWaypoint();
        if (waypoint == null || mc.player == null) return;
        Vec3d target = new Vec3d(this.toNetherCoord(waypoint.getX()), waypoint.getY(), this.toNetherCoord(waypoint.getZ()));
        this.rotateYawTowards(target);
        if (this.getHorizontalDistance(mc.player.getEntityPos(), target) >= this.netherReach()) return;
        this.removeCurrentWaypoint(waypoint);
        this.celebrateWhenManualRouteEnds();
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

    private boolean flightModuleReady(FlightMode mode) {
        if (this.flightModules.moduleOf(mode).isActive()) return true;
        if (!this.hasElytraEquipped()) return false;
        this.startFlightMode(mode);
        return true;
    }

    private void handleRocketFlyPathfinding(BlockPos nextWaypoint) {
        if (this.flightModules.moduleOf(FlightMode.RocketFly) == null) return;
        if (!this.flightModuleReady(FlightMode.RocketFly)) return;
        this.flyTowardAndFinish(nextWaypoint);
    }

    private void handlePitch40Pathfinding(BlockPos nextWaypoint) {
        Pitch40 pitch40 = this.flightModules.pitch40();
        if (pitch40 == null || mc.player == null) return;
        if (!this.flightModuleReady(FlightMode.Pitch40)) return;
        if (pitch40.autoFirework != null && !pitch40.autoFirework.get()) pitch40.autoFirework.set(true);
        this.rotateYawTowards(this.getAdjustedWaypointPos(nextWaypoint));
        if (this.isClimbingAndFalling()) this.rocket.use();
        this.finishIfArrived(nextWaypoint);
    }

    private boolean isClimbingAndFalling() {
        return mc.player.getPitch() < STEEP_CLIMB_PITCH && mc.player.getVelocity().y < FALLING_VELOCITY;
    }

    private void flyTowardAndFinish(BlockPos waypoint) {
        if (mc.player == null) return;
        this.rotateYawTowards(this.getAdjustedWaypointPos(waypoint));
        this.finishIfArrived(waypoint);
    }

    private void finishIfArrived(BlockPos waypoint) {
        Vec3d target = this.getAdjustedWaypointPos(waypoint);
        if (this.getHorizontalDistance(mc.player.getEntityPos(), target) >= this.reachDistance.get()) return;
        this.removeCurrentWaypoint(waypoint);
        if (!this.waypointsToFollow.isEmpty()) return;
        this.stopFlightMode();
        this.say("All waypoints reached");
        this.playCompletionSound();
    }

    private void handleManualPathfinding(BlockPos nextWaypoint) {
        if (mc.player == null) return;
        Vec3d target = this.getAdjustedWaypointPos(nextWaypoint);
        this.rotateYawTowards(target);
        if (this.getHorizontalDistance(mc.player.getEntityPos(), target) >= this.reachDistance.get()) return;
        this.removeCurrentWaypoint(nextWaypoint);
        this.celebrateWhenManualRouteEnds();
    }

    private void celebrateWhenManualRouteEnds() {
        if (!this.waypointsToFollow.isEmpty() || !this.showChatMessages.get()) return;
        this.info("All waypoints reached");
        this.playCompletionSound();
    }

    private void playCompletionSound() {
        if (!this.completionSound.get() || mc.player == null) return;
        mc.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!this.isActive() || this.isDeactivating || mc.player == null || mc.world == null) return;
        if (this.consumeRouteEnd()) return;
        this.handleDimensionChange();
        this.updateRockets(mc.player);
        this.refreshDimensionFlags();
        if (this.recordRoute.get()) {
            this.recordRoutePass(mc.player);
            return;
        }
        if (this.canFollowThisTick()) this.followRoute();
    }

    private void followRoute() {
        this.trackTravel();
        this.checkAndReloadWaypoints();
        this.pruneUntrackableWaypoints();
        if (this.areaLoaderSteering || this.holdsRoute()) return;
        this.rocket.tick();
        if (!this.runStartup()) return;
        this.handBackRecast();
        this.dispatchPathfinding();
        this.showStatisticsBar();
    }

    private boolean holdsRoute() {
        if (!this.waypointsToFollow.isEmpty() && !this.isPaused) return false;
        if (this.currentBaritoneTarget != null) this.stopBaritone();
        return true;
    }

    private boolean consumeRouteEnd() {
        if (!this.routeEndPending) return false;
        this.routeEndPending = false;
        this.applyRouteEndAction();
        return true;
    }

    private void handleDimensionChange() {
        String key = this.getCurrentDimensionKey();
        if (key.equals(this.lastDimensionKey)) return;
        this.lastDimensionKey = key;
        this.flushSeedSave();
        this.lastSeedOwX = Double.NaN;
        this.lastSeedOwZ = Double.NaN;
        this.say("Dimension changed, reloading the waypoints");
        this.waypointsToFollow.clear();
        this.waypointLoadRetries = 0;
        this.waypointsFullyLoaded = false;
        this.lastPlayerPos = mc.player.getEntityPos();
        this.startAsyncWaypointLoad();
    }

    private void recordRoutePass(ClientPlayerEntity player) {
        if (this.activeFlightMode != FlightMode.None) this.stopFlightMode();
        if (this.currentBaritoneTarget != null) this.stopBaritone();
        if (this.isInNether) {
            this.recordBreadcrumb(player);
            return;
        }
        this.flushSeedSaveIfDue();
    }

    private boolean canFollowThisTick() {
        return this.waypointsFullyLoaded && mc.interactionManager != null
            && mc.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR;
    }

    private void trackTravel() {
        Vec3d now = mc.player.getEntityPos();
        if (this.lastPlayerPos != null) this.accumulateTravel(now);
        this.lastPlayerPos = now;
    }

    private void accumulateTravel(Vec3d now) {
        double moved = now.distanceTo(this.lastPlayerPos);
        if (moved >= PORTAL_JUMP_BLOCKS) return;
        this.totalDistanceTraveled += moved;
        double step = this.getHorizontalDistance(this.lastPlayerPos, now) * TICKS_PER_SECOND;
        this.smoothedSpeed = this.smoothedSpeed <= 0.0 ? step : this.smoothedSpeed + SPEED_SMOOTHING * (step - this.smoothedSpeed);
    }

    private boolean runStartup() {
        if (this.autoStartFlight.get() && !this.startupRecastTriggered && !this.beginStartup()) return false;
        return this.confirmGliding();
    }

    private boolean beginStartup() {
        if (this.baritoneTakesOffByItself() || mc.player.isGliding()) {
            this.startupRecastTriggered = true;
            this.baritoneActivatedAfterStartup = true;
            return true;
        }
        this.startupDelayTicks++;
        if (this.startupDelayTicks >= this.startupDelay.get()) this.launchStartupFlight();
        return false;
    }

    private boolean baritoneTakesOffByItself() {
        return this.isInNether && this.netherFlightMode.get() == NetherFlightMode.Baritone
            && BaritoneAPI.getSettings().elytraAutoJump.value;
    }

    private void launchStartupFlight() {
        if (this.hasElytraEquipped()) {
            this.triggerStartupRecast();
        } else {
            this.sayError("No elytra equipped, cannot auto-start the flight");
        }
        this.startupRecastTriggered = true;
    }

    private boolean confirmGliding() {
        if (!this.startupRecastTriggered || this.baritoneActivatedAfterStartup) return true;
        if (mc.player.isGliding() && ++this.glidingTicksAfterStartup >= GLIDE_CONFIRM_TICKS) {
            this.baritoneActivatedAfterStartup = true;
        }
        return this.baritoneActivatedAfterStartup;
    }

    private void handBackRecast() {
        ElytraRecast recast = this.flightModules.elytraRecast();
        if (!this.recastEnabledForStartup || recast == null) return;
        if (!recast.isActive()) {
            this.recastEnabledForStartup = false;
        } else if (!recast.isRecovering()) {
            recast.toggle();
            this.recastEnabledForStartup = false;
            this.say("Flight established, ElytraRecast released");
        }
    }

    private void dispatchPathfinding() {
        if (this.isInNether) {
            this.handleNetherPathfinding();
        } else if (this.isInEnd) {
            this.handleEndPathfinding();
        } else {
            this.handleOverworldPathfinding();
        }
    }

    private void showStatisticsBar() {
        if (!this.showStatistics.get() || this.waypointsToFollow.isEmpty()) return;
        StringBuilder bar = new StringBuilder(this.waypointsToFollow.size() + " waypoints");
        if (this.waypointsCompletedThisSession > 0) bar.append(" | ").append(this.waypointsCompletedThisSession).append(" done");
        bar.append(" | ").append(String.format("%.0f", this.totalDistanceTraveled)).append(" blocks traveled");
        mc.player.sendMessage(Text.literal(bar.toString()), true);
    }

    @Override
    public String getInfoString() {
        if (this.waypointsToFollow.isEmpty()) return "No waypoints";
        StringBuilder info = new StringBuilder();
        if (this.isPaused) info.append("[PAUSED] ");
        info.append(this.waypointsToFollow.size()).append(" waypoints");
        info.append(" [").append(this.flightModeLabel()).append("]");
        this.appendDistance(info);
        if (this.showStatistics.get() && this.waypointsCompletedThisSession > 0) {
            info.append(" | ").append(this.waypointsCompletedThisSession).append(" completed");
        }
        return info.toString();
    }

    private Object flightModeLabel() {
        if (this.isInNether) return this.netherFlightMode.get();
        return this.dimensionFlightMode();
    }

    private void appendDistance(StringBuilder info) {
        BlockPos next = this.getNextWaypoint();
        if (next == null || mc.player == null) return;
        double distance = this.getHorizontalDistance(mc.player.getEntityPos(), this.getAdjustedWaypointPos(next));
        info.append(" - ").append(String.format("%.0f", distance)).append("m");
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

    private void stopBaritone() {
        BaritoneHelper.stopAllPathing();
        this.currentBaritoneTarget = null;
        this.baritoneRestartAttempts = 0;
        this.baritoneRestartCooldown = 0;
        this.baritoneRestartPos = null;
        this.lavaEscapeGoal = null;
        this.lavaEscapeCooldown = 0;
        this.lavaEscapeDirection = null;
        this.lavaEscapeMinePos = null;
        this.lavaEscapeMineTicks = 0;
        this.lavaEscapeLavaTicks = 0;
        this.lavaEscapeTotalTicks = 0;
        this.lavaEscapeEngaged = false;
    }

    public String getWaypointPrefix() {
        return this.waypointPrefix.get();
    }

    public void clearTrackedWaypoints() {
        this.waypointsToFollow.clear();
        this.currentWaypointIndex = 0;
    }

    public boolean isPaused() {
        return this.isPaused;
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

    private int toPlayerDimensionCoord(int waypointCoord) {
        return (int) (waypointCoord * getDimensionDivision());
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

    private boolean handleLavaFootEscape(BlockPos nextWaypoint) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;

        // A second of lava under the feet, not one tick of it. Skimming a lake
        // on the way past dips into lava for a tick or two dozens of times a
        // session, and every one of those used to cancel Baritone and take the
        // flight over for nothing.
        if (!this.shouldUseLavaFootEscape(player)) {
            this.lavaEscapeLavaTicks = Math.max(0, this.lavaEscapeLavaTicks - 4);
            if (this.lavaEscapeGoal != null || this.lavaEscapeEngaged) {
                this.endLavaEscape();
            }

            return false;
        }

        if (this.lavaEscapeGoal == null && ++this.lavaEscapeLavaTicks < LAVA_ESCAPE_ARM_TICKS) {
            return false;
        }

        // Handed back rather than held forever: five minutes of this ran on
        // 2026-09-22 without getting out, and Baritone could do nothing the
        // whole time because the escape cancelled it twenty times a second.
        if (this.lavaEscapeEngaged && ++this.lavaEscapeTotalTicks > LAVA_ESCAPE_GIVE_UP_TICKS) {
            if (this.showChatMessages.get()) {
                this.info("Lava escape got nowhere in thirty seconds, handing the flight back to Baritone");
            }

            this.endLavaEscape();
            this.lavaEscapeLavaTicks = 0;
            return false;
        }

        try {
            IBaritone baritoneInstance = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (this.lavaEscapeCooldown <= 0 || this.lavaEscapeGoal == null) {
                BlockPos escape = this.pickLavaEscapeGoal(nextWaypoint);
                this.lavaEscapeGoal = escape;
                this.lavaEscapeDirection = this.directionToward(player.getBlockPos(), escape);
                this.lavaEscapeCooldown = LAVA_ESCAPE_REPATH_TICKS;
                this.currentBaritoneTarget = null;
                this.lavaEscapeMinePos = null;
                this.lavaEscapeMineTicks = 0;

                if (this.showChatMessages.get()) {
                    this.info("Lava trap detected - stopping elytra and mining out toward "
                        + escape.getX() + ", " + escape.getY() + ", " + escape.getZ());
                }
            } else {
                this.lavaEscapeCooldown--;
            }

            // Once, on the way in. Cancelling every tick wrote 5720 lines of
            // "ok force canceled" into one session and left Baritone no tick
            // of its own to work with.
            if (!this.lavaEscapeEngaged) {
                this.lavaEscapeEngaged = true;
                this.lavaEscapeTotalTicks = 0;
                baritoneInstance.getPathingBehavior().cancelEverything();
                if (BaritoneHelper.hasElytraProcess()) {
                    baritoneInstance.getCommandManager().execute("forcecancel");
                }

                baritoneInstance.getCustomGoalProcess().setGoal(null);
                baritoneInstance.getInputOverrideHandler().clearAllKeys();
            }

            this.mineLavaEscapeTunnel();
        } catch (Exception e) {
            if (this.showChatMessages.get()) this.error("Lava escape error: " + e.getMessage());
        }

        return true;
    }

    private void endLavaEscape() {
        this.lavaEscapeGoal = null;
        this.lavaEscapeCooldown = 0;
        this.lavaEscapeDirection = null;
        this.lavaEscapeMinePos = null;
        this.lavaEscapeMineTicks = 0;
        this.lavaEscapeEngaged = false;
        this.lavaEscapeTotalTicks = 0;
        Utils.setPressed(mc.options.forwardKey, false);
        Utils.setPressed(mc.options.jumpKey, false);
    }

    /**
     * Leave normal Baritone flight alone around lava lakes. Foot escape is for lava with walls or
     * collision around the player, where rockets only pin him into the same block.
     */
    private boolean shouldUseLavaFootEscape(ClientPlayerEntity player) {
        if (!player.isInLava()) {
            return false;
        }

        BlockPos feet = player.getBlockPos();
        int walls = 0;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos adjacent = feet.offset(direction);
            BlockState state = mc.world.getBlockState(adjacent);
            if (!state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA)
                    && !state.getCollisionShape(mc.world, adjacent).isEmpty()) walls++;
        }

        return walls >= 2 || player.horizontalCollision || player.verticalCollision || this.lavaEscapeGoal != null;
    }

    private BlockPos pickLavaEscapeGoal(BlockPos nextWaypoint) {
        ClientPlayerEntity player = mc.player;
        BlockPos feet = player.getBlockPos();
        int goalX = this.toNetherCoord(nextWaypoint.getX());
        int goalZ = this.toNetherCoord(nextWaypoint.getZ());
        Direction primary;
        Direction secondary;

        int dx = goalX - feet.getX();
        int dz = goalZ - feet.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            primary = dx >= 0 ? Direction.EAST : Direction.WEST;
            secondary = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            primary = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
            secondary = dx >= 0 ? Direction.EAST : Direction.WEST;
        }

        Direction[] choices = new Direction[] {
            primary,
            secondary,
            primary.getOpposite(),
            secondary.getOpposite()
        };

        for (Direction direction : choices) {
            if (this.lavaEscapeBreakTarget(feet, direction) != null || this.lavaEscapePassageOpen(feet, direction)) {
                return feet.offset(direction, LAVA_ESCAPE_BLOCKS);
            }
        }

        for (Direction direction : choices) {
            BlockPos candidate = feet.offset(direction, LAVA_ESCAPE_BLOCKS);
            if (!mc.world.getBlockState(candidate).getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA)
                    && !mc.world.getBlockState(candidate.up()).getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA)) {
                return candidate;
            }
        }

        return feet.offset(primary, LAVA_ESCAPE_BLOCKS);
    }

    private void mineLavaEscapeTunnel() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null || mc.world == null) return;

        BlockPos feet = player.getBlockPos();
        Direction direction = this.lavaEscapeDirection;
        if (direction == null) {
            direction = this.lavaEscapeGoal != null ? this.directionToward(feet, this.lavaEscapeGoal) : Direction.NORTH;
            this.lavaEscapeDirection = direction;
        }

        BlockPos breakTarget = this.lavaEscapeBreakTarget(feet, direction);
        if (breakTarget == null) {
            Utils.setPressed(mc.options.forwardKey, true);
            // Pulsed, never held: a jump the server sees held counts as movement
            // and cancels every inventory click behind it, which is exactly the
            // slot change AutoEatSync needs to get a fire resistance apple down
            // while standing in the lava this is escaping from.
            Utils.setPressed(mc.options.jumpKey, player.age % 2L == 0L);
            this.rotateFlat(direction);
            return;
        }

        if (!breakTarget.equals(this.lavaEscapeMinePos)) {
            this.lavaEscapeMinePos = breakTarget;
            this.lavaEscapeMineTicks = 0;
        }

        Direction face = direction.getOpposite();

        // Handed to the mine module, which holds the pickaxe itself for the
        // packet mine and never touches the selected hotbar slot. Mining bare
        // handed got nowhere on anything harder than netherrack, and the block
        // in front of a lava pocket is rarely netherrack.
        HMine mine = Modules.get().get(HMine.class);
        if (mine != null && mine.isActive()) {
            if (this.lavaEscapeMineTicks % 40 == 0) {
                mine.queueMiningData(mine.new MiningData(breakTarget, face));
            }

            Utils.setPressed(mc.options.forwardKey, true);
            Utils.setPressed(mc.options.jumpKey, player.age % 2L == 0L);
            this.lavaEscapeMineTicks++;
            return;
        }

        Vec3d target = Vec3d.ofCenter(breakTarget);
        RotationUtils.rotate(RotationUtils.getYaw(target), RotationUtils.getPitch(target));
        if (!RotationUtils.getInstance().isAligned(8.0)) return;

        if (this.lavaEscapeMineTicks == 0) {
            meteordevelopment.meteorclient.utils.player.FindItemResult pickaxe = InvUtils.find(
                itemStack -> itemStack.isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)
            );
            if (pickaxe.found() && pickaxe.isHotbar()) {
                InvUtils.swap(pickaxe.slot(), false);
            }
        }

        if (this.lavaEscapeMineTicks % 20 == 0) {
            mc.interactionManager.attackBlock(breakTarget, face);
        } else {
            mc.interactionManager.updateBlockBreakingProgress(breakTarget, face);
        }

        player.swingHand(Hand.MAIN_HAND);
        Utils.setPressed(mc.options.forwardKey, true);
        Utils.setPressed(mc.options.jumpKey, player.age % 2L == 0L);
        this.lavaEscapeMineTicks++;
    }

    private BlockPos lavaEscapeBreakTarget(BlockPos feet, Direction direction) {
        BlockPos frontFeet = feet.offset(direction);
        if (this.isLavaEscapeBreakable(frontFeet)) return frontFeet;

        BlockPos frontHead = frontFeet.up();
        if (this.isLavaEscapeBreakable(frontHead)) return frontHead;

        BlockPos currentHead = feet.up();
        if (this.isLavaEscapeBreakable(currentHead)) return currentHead;

        return null;
    }

    private boolean lavaEscapePassageOpen(BlockPos feet, Direction direction) {
        BlockPos frontFeet = feet.offset(direction);
        BlockPos frontHead = frontFeet.up();
        return this.isLavaEscapeOpen(frontFeet) && this.isLavaEscapeOpen(frontHead);
    }

    private void rotateFlat(Direction direction) {
        float yaw = switch (direction) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> mc.player != null ? mc.player.getYaw() : 0.0F;
        };
        RotationUtils.rotate(yaw, 0.0F);
    }

    private void handleOverworldPathfinding() {
        this.handleDimensionPathfinding(this.overworldFlightMode.get());
    }

    private void handleEndPathfinding() {
        this.handleDimensionPathfinding(this.endFlightMode.get());
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
        RocketFly,
        Pitch40,
        None
    }

    public enum SeedMode {
        Spacing,
        PortalPatches
    }

    public enum FollowMode {
        Closest,
        Numerical
    }

    public enum NetherFlightMode {
        Baritone,
        None
    }
}
