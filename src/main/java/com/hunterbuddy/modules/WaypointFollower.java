package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
// Mlep class removed; use HunterBuddyAddon.HUNTER_BUDDY_CATEGORY directly
import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.modules.regear.util.XaeroWaypointManager;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
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

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaeroplus.feature.waypoint.WaypointAPI;
import xaeroplus.settings.Settings;

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
    private int glidingTicksAfterStartup = 0;
    private boolean baritoneActivatedAfterStartup = false;
    private int waypointLoadRetries = 0;
    private boolean waypointsFullyLoaded = false;
    private String lastDimensionKey = "";
    private Thread waypointLoadThread = null;
    private volatile boolean isDeactivating = false;
    private volatile boolean areaLoaderSteering = false;

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
        .description("Clears all follow waypoints (only works when Xaero's world map is open)")
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
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "waypoint-follower", "Advanced waypoint following system with multi-dimensional flight support");
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
        this.lastPlayerPos = mc.player.getPos();
        this.waypointsToFollow.clear();
        this.currentWaypointIndex = 0;
        this.startupDelayTicks = 0;
        this.startupRecastTriggered = false;
        this.glidingTicksAfterStartup = 0;
        this.baritoneActivatedAfterStartup = false;
        this.waypointLoadRetries = 0;
        this.waypointsFullyLoaded = false;
        this.lastDimensionKey = getCurrentDimensionKey();
        this.startAsyncWaypointLoad();
        this.activeFlightMode = FlightMode.None;
    }

    @Override
    public void onDeactivate() {
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
                removeCurrentWaypoint(current);
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
            this.removeCurrentWaypoint(current);
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
                return WaypointAPI.getMinimapWorld(World.OVERWORLD);
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
            List<Waypoint> toRemove = new ArrayList<>();

            for (Waypoint wp : currentSet.getWaypoints()) {
                if (wp.getName().startsWith(prefix)) {
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
                        this.info("Cleared " + toRemove.size() + " hunt waypoints (saved to disk)");
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

        Vec3d playerPos = player.getPos();
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
        this.waypointsToFollow.remove(waypoint);
        this.removeFollowWaypoint(waypoint);
        this.waypointsCompletedThisSession++;
        if (this.followMode.get() == FollowMode.Numerical && this.currentWaypointIndex >= this.waypointsToFollow.size()) {
            this.currentWaypointIndex = 0;
        }
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

        Vec3d playerPos = player.getPos();
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
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                int selected = ((com.hunterbuddy.modules.regear.mixin.accessor.PlayerInventoryAccessor) inv).getSelectedSlot();
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
            boolean needsNewGoal = this.currentBaritoneTarget == null || !this.currentBaritoneTarget.equals(nextWaypoint);

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

        Vec3d playerPos = player.getPos();
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

        Vec3d playerPos = player.getPos();
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

        double horizontalDistance = this.getHorizontalDistance(player.getPos(), targetPos);
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

        double horizontalDistance = this.getHorizontalDistance(player.getPos(), targetPos);
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

        double horizontalDistance = this.getHorizontalDistance(player.getPos(), targetPos);
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

        String currentDim = this.getCurrentDimensionKey();
        if (!currentDim.equals(this.lastDimensionKey)) {
            this.lastDimensionKey = currentDim;
            if (this.showChatMessages.get()) this.info("Dimension changed, reloading waypoints...");
            this.waypointsToFollow.clear();
            this.waypointLoadRetries = 0;
            this.waypointsFullyLoaded = false;
            this.lastPlayerPos = player.getPos();
            this.startAsyncWaypointLoad();
        }

        if (!this.waypointsFullyLoaded) return;

        if (mc.interactionManager == null || mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) return;

        if (this.lastPlayerPos != null) {
            double distance = player.getPos().distanceTo(this.lastPlayerPos);
            if (distance < 100.0) this.totalDistanceTraveled += distance;
        }
        this.lastPlayerPos = player.getPos();

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

        this.isInNether = this.isInNether(mc.world);
        this.isInEnd = this.isInEnd(mc.world);

        if (this.autoStartFlight.get() && !this.startupRecastTriggered) {
            if (!player.isFallFlying()) {
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
            if (player.isFallFlying()) {
                this.glidingTicksAfterStartup++;
                if (this.glidingTicksAfterStartup >= 2) {
                    this.baritoneActivatedAfterStartup = true;
                }
            }
            if (!this.baritoneActivatedAfterStartup) return;
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
            double dist = this.getHorizontalDistance(player.getPos(), this.getAdjustedWaypointPos(next));
            info.append(String.format(" - %.0fm", dist));
        }

        if (this.showStatistics.get() && this.waypointsCompletedThisSession > 0) {
            info.append(String.format(" | %d completed", this.waypointsCompletedThisSession));
        }
        return info.toString();
    }

    public enum FlightMode {
        RocketFly("RocketFly"), Pitch40("Pitch40"), None("None");
        private final String name;
        FlightMode(String name) { this.name = name; }
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
