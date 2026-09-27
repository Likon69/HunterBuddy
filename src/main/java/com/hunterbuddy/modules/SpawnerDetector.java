package com.hunterbuddy.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.mixin.accessors.FireworkRocketEntityAccessor;
import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.regear.util.ElytraTakeoff;
import com.hunterbuddy.modules.regear.util.MapUtil;
import com.hunterbuddy.modules.regear.util.PlacementUtils;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.modules.regear.util.XaeroWaypointManager;
import com.hunterbuddy.util.DugHoles;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AbstractTorchBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.EnchantingTableBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HangingSignBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.block.enums.TrialSpawnerState;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.Palette;
import net.minecraft.world.chunk.WorldChunk;

public class SpawnerDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScore = settings.createGroup("Score");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgAuto = settings.createGroup("Auto farm");

    private final Setting<Boolean> detectTorches = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-torches")
        .description("Compute the light signal (whitelist of placed light sources) around a site.")
        .defaultValue(true).build());

    private final Setting<Boolean> detectTrial = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-trial")
        .description("Report trial spawners that have left their waiting state.")
        .defaultValue(true).build());

    private final Setting<Boolean> requireChests = sgGeneral.add(new BoolSetting.Builder()
        .name("require-chests")
        .description("Hide a find with no container left around it. An emptied dungeon is not worth the detour.")
        .defaultValue(false).build());

    private final Setting<Boolean> ignoreBelowZero = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-below-zero")
        .description("Skip spawners below y 0: no box, no label, no log line. Rooms down there are 1.18+ generation, never 1.12 dungeons.")
        .defaultValue(true).build());

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Ticks between two evaluation passes. Only newly discovered spawners are looked at, so this can stay high.")
        .defaultValue(10).min(1).max(100).sliderRange(1, 40).build());

    private final Setting<Integer> chestRange = sgGeneral.add(new IntSetting.Builder()
        .name("chest-range")
        .description("How far around a spawner to look for containers, in blocks.")
        .defaultValue(16).min(4).max(32).sliderRange(4, 32).build());

    private final Setting<Integer> lightRange = sgGeneral.add(new IntSetting.Builder()
        .name("light-range")
        .description("How far around a room the light signal is checked, in blocks.")
        .defaultValue(6).min(1).max(16).sliderRange(1, 16)
        .visible(detectTorches::get).build());

    private final Setting<Integer> traceRange = sgGeneral.add(new IntSetting.Builder()
        .name("trace-range")
        .description("How far around a room the ender chest and player-block signals are checked, in blocks.")
        .defaultValue(8).min(1).max(32).sliderRange(1, 32).build());

    private final Setting<Integer> minMobs = sgGeneral.add(new IntSetting.Builder()
        .name("min-mobs")
        .description("Minimum number of the spawner's mob type around it to count the mobs signal.")
        .defaultValue(2).min(1).max(10).sliderRange(1, 10).build());

    private final Setting<List<Block>> containers = sgGeneral.add(new BlockListSetting.Builder()
        .name("containers")
        .description("Blocks that count as loot worth flying down for.")
        .defaultValue(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.SHULKER_BOX)
        .build());

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Print a line when a site is classified.")
        .defaultValue(true).build());

    private final Setting<Boolean> log = sgGeneral.add(new BoolSetting.Builder()
        .name("log")
        .description("Write detection and auto-farm lines to latest.log.")
        .defaultValue(false).build());

    private final Setting<Integer> weightLight = sgScore.add(new IntSetting.Builder()
        .name("weight-light")
        .description("Score weight of the light signal.")
        .defaultValue(3).min(0).max(10).sliderRange(0, 10).build());

    private final Setting<Integer> weightEnderChest = sgScore.add(new IntSetting.Builder()
        .name("weight-ender-chest")
        .description("Score weight of the ender chest signal.")
        .defaultValue(4).min(0).max(10).sliderRange(0, 10).build());

    private final Setting<Integer> weightPlayerBlocks = sgScore.add(new IntSetting.Builder()
        .name("weight-player-blocks")
        .description("Score weight of the player-block signal.")
        .defaultValue(3).min(0).max(10).sliderRange(0, 10).build());

    private final Setting<Integer> weightBrokenSpawner = sgScore.add(new IntSetting.Builder()
        .name("weight-broken-spawner")
        .description("Score weight of the broken-spawner signal.")
        .defaultValue(4).min(0).max(10).sliderRange(0, 10).build());

    private final Setting<Integer> weightMobs = sgScore.add(new IntSetting.Builder()
        .name("weight-mobs")
        .description("Score weight of the mobs signal.")
        .defaultValue(3).min(0).max(10).sliderRange(0, 10).build());

    private final Setting<Integer> weightTunnel = sgScore.add(new IntSetting.Builder()
        .name("weight-tunnel")
        .description("Score weight of the tunnel signal.")
        .defaultValue(3).min(0).max(10).sliderRange(0, 10).build());

    private final Setting<Integer> weightAir = sgScore.add(new IntSetting.Builder()
        .name("weight-air")
        .description("Score weight of the air signal.")
        .defaultValue(2).min(0).max(10).sliderRange(0, 10).build());

    private final Setting<Integer> triggerScore = sgScore.add(new IntSetting.Builder()
        .name("trigger-score")
        .description("Minimum score for a site to count as traced.")
        .defaultValue(3).min(1).max(30).sliderRange(1, 30).build());

    private final Setting<Double> renderDistance = sgRender.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("Stop drawing a find past this distance, in blocks.")
        .defaultValue(256.0).min(32.0).sliderRange(32.0, 1024.0).build());

    private final Setting<Integer> fadeTime = sgRender.add(new IntSetting.Builder()
        .name("fade-time")
        .description("Milliseconds a box takes to fade in and out.")
        .defaultValue(400).min(0).max(3000).sliderRange(0, 2000).build());

    private final Setting<Boolean> distanceFade = sgRender.add(new BoolSetting.Builder()
        .name("distance-fade")
        .description("Dim a box as it approaches the render distance, instead of having it vanish.")
        .defaultValue(true).build());

    private final Setting<Boolean> showLabel = sgRender.add(new BoolSetting.Builder()
        .name("show-label")
        .description("Draw the mob type, container count, score and signals above the box.")
        .defaultValue(true).build());

    private final Setting<Double> labelScale = sgRender.add(new DoubleSetting.Builder()
        .name("label-scale").description("Size of the label.")
        .defaultValue(1.0).min(0.3).sliderRange(0.3, 3.0).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("How the boxes are drawn.")
        .defaultValue(ShapeMode.Both).build());

    private final Setting<Boolean> showIntact = sgRender.add(new BoolSetting.Builder()
        .name("show-intact")
        .description("Draw sites that are not traced but still have containers.")
        .defaultValue(false).build());

    private final Setting<Boolean> showEmpty = sgRender.add(new BoolSetting.Builder()
        .name("show-empty")
        .description("Draw sites that are not traced and have no containers.")
        .defaultValue(false).build());

    private final Setting<SettingColor> lootedColor = sgColors.add(new ColorSetting.Builder()
        .name("dungeon-color").description("Traced, not strong, with containers.")
        .defaultValue(new SettingColor(255, 60, 60, 200)).build());

    private final Setting<SettingColor> emptyColor = sgColors.add(new ColorSetting.Builder()
        .name("emptied-color").description("Traced, not strong, no containers.")
        .defaultValue(new SettingColor(130, 130, 130, 140)).build());

    private final Setting<SettingColor> stashColor = sgColors.add(new ColorSetting.Builder()
        .name("stash-color")
        .description("Traced, strong, with containers.")
        .defaultValue(new SettingColor(255, 80, 255, 220)).build());

    private final Setting<SettingColor> torchColor = sgColors.add(new ColorSetting.Builder()
        .name("torches-color").description("Traced, strong, no containers.")
        .defaultValue(new SettingColor(255, 200, 60, 200)).build());

    private final Setting<SettingColor> trialColor = sgColors.add(new ColorSetting.Builder()
        .name("trial-color").description("An activated trial spawner.")
        .defaultValue(new SettingColor(120, 180, 255, 200)).build());

    private final Setting<SettingColor> intactColor = sgColors.add(new ColorSetting.Builder()
        .name("intact-color").description("Not traced, with containers.")
        .defaultValue(new SettingColor(80, 220, 120, 200)).build());

    private final Setting<SettingColor> emptyCatColor = sgColors.add(new ColorSetting.Builder()
        .name("empty-color").description("Not traced, no containers.")
        .defaultValue(new SettingColor(90, 90, 90, 120)).build());

    private final Setting<Boolean> autoFarm = sgAuto.add(new BoolSetting.Builder()
        .name("auto-farm")
        .description("Fly down to a triggered dungeon on its own, mine the spawner, loot the room, then fly back and resume.")
        .defaultValue(false)
        .onChanged(this::onAutoFarmChanged)
        .build());

    private final Setting<Boolean> autoFarmAll = sgAuto.add(new BoolSetting.Builder()
        .name("auto-farm-all")
        .description("Also farm dungeons with no human trace, with or without a spawner, as long as the room has at least one chest.")
        .defaultValue(false).build());

    private final Setting<Integer> autoMaxDistance = sgAuto.add(new IntSetting.Builder()
        .name("auto-max-distance")
        .description("Maximum horizontal distance to a triggered dungeon to leave for it, in blocks.")
        .defaultValue(256).min(32).max(512).sliderRange(32, 512).build());

    private final Setting<Double> autoMinHealth = sgAuto.add(new DoubleSetting.Builder()
        .name("auto-min-health")
        .description("Minimum health plus absorption to start a dungeon. Never used to stop one already started.")
        .defaultValue(14.0).min(4.0).max(36.0).sliderRange(4.0, 36.0).build());

    private final Setting<Integer> autoMinRockets = sgAuto.add(new IntSetting.Builder()
        .name("auto-min-rockets")
        .description("Minimum firework rockets across the whole inventory to leave.")
        .defaultValue(16).min(1).max(256).sliderRange(1, 256).build());

    private final Setting<Integer> autoMaxFall = sgAuto.add(new IntSetting.Builder()
        .name("auto-max-fall")
        .description("Largest air gap allowed in the column to dig, unless it ends on water.")
        .defaultValue(8).min(3).max(30).sliderRange(3, 30).build());

    private final Setting<Boolean> autoAvoidPlayers = sgAuto.add(new BoolSetting.Builder()
        .name("auto-avoid-players")
        .description("Do not leave, and abort, while a non-friend player is loaded.")
        .defaultValue(true).build());

    private final Setting<Integer> autoFlareHeight = sgAuto.add(new IntSetting.Builder()
        .name("auto-flare-height")
        .description("Height above the landing column where the glide flares out into the final descent.")
        .defaultValue(30).min(10).max(80).sliderRange(10, 80).build());

    private final Setting<Boolean> autoShaftFly = sgAuto.add(new BoolSetting.Builder()
        .name("auto-shaft-fly")
        .description("Exit through the dig shaft on rockets instead of walking back to the surface, when the shaft has no water and is not sky-blocked.")
        .defaultValue(true).build());

    public enum FlightModule { AreaLoader, RocketFly, WaypointFollower, None }

    private final Setting<FlightModule> flightModule = sgAuto.add(new EnumSetting.Builder<FlightModule>()
        .name("flight-module")
        .description("Module switched off while a dungeon is farmed and switched back on once the bot has climbed out. None takes off and leaves you gliding.")
        .defaultValue(FlightModule.AreaLoader).build());

    private final Setting<Integer> climbHeight = sgAuto.add(new IntSetting.Builder()
        .name("climb-height")
        .description("How many blocks to climb with rockets after leaving the hole, before handing over to the flight module.")
        .defaultValue(100).min(0).max(300).sliderRange(0, 300).build());

    private final Setting<Boolean> autoChainUnderground = sgAuto.add(new BoolSetting.Builder()
        .name("auto-chain-underground")
        .description("Walk underground to a qualifying neighbour room instead of ascending and re-diving, when one is close enough.")
        .defaultValue(true).build());

    private final Set<BlockPos> trialPending = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, Detection> trialFound = new ConcurrentHashMap<>();

    private final Set<BlockPos> rejected = ConcurrentHashMap.newKeySet();
    private final Set<Long> mossyScanned = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, Site> sites = new LinkedHashMap<>();
    private final Map<BlockPos, PendingSite> pendingEval = new LinkedHashMap<>();
    private final Set<BlockPos> approached = new java.util.HashSet<>();
    private final Set<BlockPos> sideHoleLogged = new java.util.HashSet<>();
    private final Set<BlockPos> sideHoleUseLogged = new java.util.HashSet<>();

    private long totalTicks;
    private int scanCooldown;
    private ClientWorld lastWorld;

    private enum AutoState {
        IDLE, PREPARE, APPROACH, FINAL, WALK, DIG, SIDE_DIG, ROOM, ROOM_CLIMB, TUNNEL, EXIT, SHAFT_ALIGN, SHAFT_FLY,
        SWIM_UP, WATER_JUMP, CLIMB, OBSIDIAN_PLATFORM, ASCEND, TAKEOFF_SPOT, TAKEOFF, RESUME, FAILED
    }

    private record ColumnPlan(int x, int z, int startY, int exitY, boolean water,
                               int blocksToBreak, int waterCells, int maxGap, boolean skyBlocked,
                               int holeTopY, BlockPos rim, boolean side, BlockPos sideEntry) {}

    private record ExitShaft(int x, int z, int bottomY, int exitY, boolean water, boolean skyBlocked, BlockPos entry) {}

    private record RimResult(BlockPos rim, int holeTopY) {}

    private record SideHoleReject(int x, int z, int bottomFeet, String reason) {}

    private record QualResult(String reject, ColumnPlan plan) {
        static QualResult ok(ColumnPlan plan) { return new QualResult(null, plan); }
        static QualResult reject(String reason) { return new QualResult(reason, null); }
    }

    private record VisitedEntry(int x, int y, int z, String outcome, long time) {}

    private static final int SKY_CHECK_HEIGHT = 200;

    private static final Set<Item> AUTO_THROWAWAY = Set.of(
        Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.STONE, Items.DIRT, Items.NETHERRACK);

    private final ElytraTakeoff takeoff = new ElytraTakeoff();
    private final Gson visitedGson = new GsonBuilder().setPrettyPrinting().create();
    private final Set<Long> visitedKeys = new java.util.HashSet<>();
    private final List<VisitedEntry> visitedEntries = new ArrayList<>();
    private final Map<BlockPos, Long> autoSkipLoggedAt = new HashMap<>();

    private AutoState state = AutoState.IDLE;
    private int stateTicks;
    private Site autoSite;
    private ColumnPlan autoColumn;
    private ExitShaft autoExitShaft;
    private Site autoStackedPartner;
    private boolean autoRunAbandoned;

    private int statRoomsFarmed;
    private int statSpawnersBroken;
    private int statChestsBroken;
    private int statShulkersFound;

    private boolean autoAreaLoaderWasOn, autoPitch40WasOn, autoElytraRecastWasOn;
    private boolean autoSavedAllowPlace;
    private int autoBaritoneRetries;
    private boolean autoAscendFromAbort;

    private boolean autoWalkDirect;
    private boolean autoWalkStepping;

    private List<BlockPos> autoRoomQueue = new ArrayList<>();
    private int autoRoomIndex;
    private int autoRoomSubState;
    private long autoRoomSubStateStartMs;
    private int autoRoomOpenAttempts;
    private int autoRoomWaitTicks;
    private long autoRoomBreakStartMs;
    private boolean autoRoomSpawnerHandled;
    private long autoRoomSpawnerStartMs;
    private BlockPos autoRoomSpawnerPos;
    private int autoRoomFoundShulkers;
    private int autoRoomFoundItems;
    private int autoRoomBreakTicks;
    private int autoRoomContainerGoneTicks;
    private Vec3d autoRoomBreakAnchor;
    private boolean autoRoomBreakRepositioning;
    private Vec3d autoRoomSpawnerAnchor;
    private boolean autoRoomSpawnerRepositioning;

    private final Set<BlockPos> autoRoomDebrisDeferred = new java.util.HashSet<>();
    private final Map<BlockPos, Integer> autoRoomDebrisBreaks = new HashMap<>();
    private final Set<BlockPos> autoRoomDebrisIgnored = new java.util.HashSet<>();
    private BlockPos autoRoomDebrisCurrent;
    private long autoRoomDebrisStartMs;
    private int autoRoomDebrisBroken;

    private final Map<BlockPos, Set<BlockPos>> autoApproachBadCells = new HashMap<>();
    private BlockPos autoApproachTarget;
    private BlockPos autoApproachCell;
    private int autoApproachTicks;
    private double autoApproachBaselineDist;

    private boolean autoExitWalkingLogged;

    private double autoClimbTargetY;
    private int autoResumeClimbTries;
    private boolean autoExitReturning;
    private boolean autoWaterJumpVertical;
    private int autoWalkDirectDryTicks;
    private int autoWalkDirectStuckTicks;
    private boolean autoWalkKeyLogged;
    private boolean autoFinalBlockedLogged;
    private boolean autoFinalDiving;
    private int autoFinalLastRocketTick;
    private int autoClimbLastRocketTick;
    private long autoOwnRocketFiredAtMs;
    private boolean autoOwnRocketSeenAlive;
    private double autoClimbLastHeight;
    private int autoClimbStuckAttempts;
    private boolean autoClimbStuck;
    private int autoClimbStuckStartTick;
    private int autoClimbStuckWaterReturns;

    private boolean autoPaused;
    private String autoPauseReason;
    private long autoPauseStartMs;
    private int autoPausedTicks;

    private int autoLastHurtTime;
    private long autoLastHitMs;
    private boolean autoHitThisTick;
    private Entity autoShaftChaseTarget;

    private BlockPos autoDigQueuedPos;
    private String autoDigQueuedBlockLabel;
    private long autoDigBlockStartMs;
    private long autoDigTotalStartMs;
    private int autoDigBlocksPlanned;
    private int autoDigBlockTicks;
    private long autoDigDisplacedSinceMs;

    private BlockPos autoSideDigBlockPos;
    private long autoSideDigLastSlowLogMs;

    private double autoHealthBeforeLanding;

    private BlockPos autoTakeoffSpot;
    private int autoTakeoffOutcome;
    private String autoTakeoffFailReason;
    private float autoTakeoffYaw;

    private BlockPos autoTunnelGoal;

    private double autoBreachBestDist;
    private int autoBreachBestElapsed;
    private int autoBreachCount;
    private BlockPos autoBreachPos;
    private String autoBreachLabel;

    private int autoShaftFlyAttempts;
    private boolean autoShaftFlyReroute;
    private int autoShaftFlyRerouteAttempts;
    private float autoShaftFlyYaw;
    private int autoShaftFlyOutcome;
    private String autoShaftFlyFailReason;
    private int autoShaftFlyRocketCount;
    private int autoShaftFlyLastRocketTick;
    private int autoShaftFlyLastRedeployTick;
    private int autoShaftFlyJumpTapTicks;
    private boolean autoShaftFlyStuck;
    private double autoShaftFlyStuckWindowY;
    private int autoShaftFlyStuckWindowTick;
    private int autoShaftFlyStuckSinceTick;
    private boolean autoShaftFlySlowClimb;
    private boolean autoShaftFlyPivot;
    private int autoShaftFlyPivotX;
    private int autoShaftFlyPivotZ;
    private boolean autoShaftFlyPivotNoEscapeLogged;

    private int autoShaftAlignCenteredTicks;

    private List<BlockPos> autoAscendTargets = List.of();

    private int autoWaterJumpAttempts;
    private int autoWaterJumpTapPhase;

    private BlockPos autoObsidianTarget;
    private int autoObsidianPhase;
    private int autoObsidianAttempts;
    private int autoObsidianWaitTicks;
    private long autoObsidianClimbStartMs;
    private Set<BlockPos> autoObsidianTried = new java.util.HashSet<>();

    private int autoRoomClimbMode;
    private int autoRoomClimbPillarPhase;
    private int autoRoomClimbJumpTapTicks;
    private boolean autoRoomClimbWater;
    private int autoRoomClimbFeetX;
    private int autoRoomClimbFeetY;
    private int autoRoomClimbFeetZ;
    private long autoRoomClimbStartMs;
    private List<BlockPos> autoRoomClimbBaritoneCandidates = List.of();
    private int autoRoomClimbBaritoneIndex;
    private long autoRoomClimbBaritoneTryStartMs;

    public SpawnerDetector() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "SpawnerDetector",
            "Finds classic monster room dungeons and scores the human traces around them.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WButton clearVisitedBtn = list.add(theme.button("Reset visited dungeons")).widget();
        clearVisitedBtn.action = this::clearVisited;
        return list;
    }

    private void purgeAllState() {
        trialPending.clear();
        trialFound.clear();
        rejected.clear();
        mossyScanned.clear();
        sites.clear();
        pendingEval.clear();
        approached.clear();
        sideHoleLogged.clear();
        sideHoleUseLogged.clear();
    }

    @Override
    public void onActivate() {
        purgeAllState();
        totalTicks = 0;
        scanCooldown = 0;
        resetAutoState();
        statRoomsFarmed = 0;
        statSpawnersBroken = 0;
        statChestsBroken = 0;
        statShulkersFound = 0;
        loadVisited();
        retryAbandonedSites();
    }

    @Override
    public void onDeactivate() {
        purgeAllState();
        if (state != AutoState.IDLE && state != AutoState.FAILED) {
            if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] disabled state={} - cleaning up", state);
            if (takeoff.isActive()) takeoff.cancel();
            BaritoneHelper.stopAllPathing();
            releaseAllAutoKeys();
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
        }
        resetAutoState();
    }

    private void resetAutoState() {
        state = AutoState.IDLE;
        stateTicks = 0;
        autoSite = null;
        autoColumn = null;
        autoExitShaft = null;
        autoStackedPartner = null;
        autoRunAbandoned = false;
        autoResumeClimbTries = 0;
        autoClimbStuckWaterReturns = 0;
        autoWaterJumpVertical = false;
        autoExitReturning = false;
        autoWalkStepping = false;
        autoRoomQueue = new ArrayList<>();
        autoRoomDebrisDeferred.clear();
        autoRoomDebrisBreaks.clear();
        autoRoomDebrisIgnored.clear();
        autoRoomDebrisCurrent = null;
        autoRoomDebrisStartMs = 0;
        autoRoomDebrisBroken = 0;
        autoApproachBadCells.clear();
        autoApproachTarget = null;
        autoApproachCell = null;
        autoLastHurtTime = 0;
        autoLastHitMs = 0;
        autoShaftChaseTarget = null;
    }

    public int getStatRoomsFarmed() { return statRoomsFarmed; }
    public int getStatSpawnersBroken() { return statSpawnersBroken; }
    public int getStatChestsBroken() { return statChestsBroken; }
    public int getStatShulkersFound() { return statShulkersFound; }
    public Site getAutoSite() { return autoSite; }
    public String getAutoStateName() { return state.name(); }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (takeoff.isActive()) takeoff.tickPre();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (takeoff.isActive()) takeoff.tickPost();

        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            purgeAllState();
            totalTicks = 0;
            scanCooldown = 0;
            if (state != AutoState.IDLE) {
                releaseAllAutoKeys();
                BaritoneHelper.stopAllPathing();
            }
            resetAutoState();
            loadVisited();
        }

        totalTicks++;
        if (totalTicks % 200 == 0) {
            pruneMossyScanned();
            pruneRejected();
        }

        autoTick();

        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }

        scanCooldown = scanInterval.get();

        sweepLoadedChunks();
        scanMossyChunks();
        evaluateTrialPending();
        evaluateSitesBudget();
        recheckMobSignals();

        if (autoFarm.get()) tryStartAutoFarm();
    }

    private void sweepLoadedChunks() {
        if (!(mc.world.getChunkManager() instanceof ClientChunkManager manager)) return;

        boolean overworld = mc.world.getRegistryKey() == net.minecraft.world.World.OVERWORLD;
        AtomicReferenceArray<WorldChunk> chunks = manager.chunks.chunks;

        for (int i = 0; i < chunks.length(); i++) {
            WorldChunk chunk = chunks.get(i);
            if (chunk == null) continue;

            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof TrialSpawnerBlockEntity) {
                    BlockPos pos = be.getPos().toImmutable();
                    if (!trialFound.containsKey(pos)) trialPending.add(pos);
                } else if (overworld && be instanceof MobSpawnerBlockEntity spawner) {
                    BlockPos pos = be.getPos().toImmutable();
                    if (sites.containsKey(pos) || rejected.contains(pos) || pendingEval.containsKey(pos)) continue;
                    if (ignoreBelowZero.get() && pos.getY() < 0) continue;

                    String id = spawnerEntityId(spawner);
                    String mob = shortRoomMobName(id);
                    if (mob == null) continue;

                    if (!roomChunksLoaded(pos)) continue;

                    Room room = findRoom(pos.getX(), pos.getY(), pos.getZ(), true);
                    if (room == null) {
                        rejected.add(pos);
                        if (log.get()) {
                            HunterBuddyAddon.LOG.info("[SpawnerDetector] no room {} {} {} mob={}",
                                pos.getX(), pos.getY(), pos.getZ(), mob);
                        }
                        continue;
                    }

                    removeOverlappingSpawnerless(room);
                    pendingEval.put(pos, new PendingSite(pos, true, mob, room));
                }
            }
        }
    }

    private boolean roomChunksLoaded(BlockPos pos) {
        for (int cx = (pos.getX() - 4) >> 4; cx <= (pos.getX() + 4) >> 4; cx++) {
            for (int cz = (pos.getZ() - 4) >> 4; cz <= (pos.getZ() + 4) >> 4; cz++) {
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) return false;
            }
        }
        return true;
    }

    private static String spawnerEntityId(MobSpawnerBlockEntity spawner) {
        try {
            var entry = spawner.getLogic().spawnEntry;
            if (entry == null) return null;
            String id = entry.getNbt().getString("id", "");
            return id.isEmpty() ? null : id;
        } catch (Exception e) {
            return null;
        }
    }

    private static String shortRoomMobName(String id) {
        if (id == null) return null;
        return switch (id) {
            case "minecraft:zombie" -> "zombie";
            case "minecraft:skeleton" -> "skeleton";
            case "minecraft:spider" -> "spider";
            default -> null;
        };
    }

    private void pruneMossyScanned() {
        Iterator<Long> it = mossyScanned.iterator();
        while (it.hasNext()) {
            long key = it.next();
            int cx = net.minecraft.util.math.ChunkPos.getPackedX(key);
            int cz = net.minecraft.util.math.ChunkPos.getPackedZ(key);
            if (!mc.world.isChunkLoaded(cx, cz)) it.remove();
        }
    }

    private void pruneRejected() {
        Iterator<BlockPos> it = rejected.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) it.remove();
        }
    }

    private void scanMossyChunks() {
        if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;
        if (!(mc.world.getChunkManager() instanceof ClientChunkManager manager)) return;

        AtomicReferenceArray<WorldChunk> chunks = manager.chunks.chunks;
        int examined = 0;

        for (int i = 0; i < chunks.length() && examined < 8; i++) {
            WorldChunk chunk = chunks.get(i);
            if (chunk == null) continue;

            long key = chunk.getPos().toLong();
            if (mossyScanned.contains(key)) continue;

            mossyScanned.add(key);
            examined++;

            scanChunkForMossy(chunk);
        }
    }

    private void scanChunkForMossy(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = chunk.getBottomY();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        for (int si = 0; si < sections.length; si++) {
            ChunkSection section = sections[si];
            if (section == null || section.isEmpty()) continue;
            if (!sectionHasMossy(section)) continue;

            int sectionBaseY = bottomY + si * 16;
            List<BlockPos> candidates = new ArrayList<>();

            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int ly = 0; ly < 16; ly++) {
                        if (section.getBlockState(lx, ly, lz).getBlock() != Blocks.MOSSY_COBBLESTONE) continue;

                        int wx = startX + lx, wy = sectionBaseY + ly, wz = startZ + lz;
                        if (ignoreBelowZero.get() && wy + 1 < 0) continue;
                        BlockPos above = new BlockPos(wx, wy + 1, wz);
                        if (isOpenState(mc.world.getBlockState(above), above)) {
                            candidates.add(new BlockPos(wx, wy, wz));
                        }
                    }
                }
            }

            processMossyCandidates(candidates);
        }
    }

    private static boolean sectionHasMossy(ChunkSection section) {
        var container = section.getBlockStateContainer();
        Palette<BlockState> palette = container.data.palette();
        int len = palette.getSize();
        for (int j = 0; j < len; j++)
            if (palette.get(j).getBlock() == Blocks.MOSSY_COBBLESTONE) return true;
        return false;
    }

    private void processMossyCandidates(List<BlockPos> candidates) {
        if (candidates.size() < 3) return;

        Map<Integer, List<BlockPos>> byY = new HashMap<>();
        for (BlockPos c : candidates) byY.computeIfAbsent(c.getY(), key -> new ArrayList<>()).add(c);

        for (List<BlockPos> plane : byY.values()) {
            boolean[] used = new boolean[plane.size()];

            for (int i = 0; i < plane.size(); i++) {
                if (used[i]) continue;

                List<Integer> group = new ArrayList<>();
                ArrayDeque<Integer> stack = new ArrayDeque<>();
                stack.push(i);
                used[i] = true;

                while (!stack.isEmpty()) {
                    int cur = stack.pop();
                    group.add(cur);
                    BlockPos cp = plane.get(cur);

                    for (int j = 0; j < plane.size(); j++) {
                        if (used[j]) continue;
                        BlockPos pj = plane.get(j);
                        if (Math.abs(pj.getX() - cp.getX()) <= 2 && Math.abs(pj.getZ() - cp.getZ()) <= 2) {
                            used[j] = true;
                            stack.push(j);
                        }
                    }
                }

                if (group.size() < 3) continue;

                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                int y = plane.get(group.get(0)).getY();
                for (int idx : group) {
                    BlockPos gp = plane.get(idx);
                    minX = Math.min(minX, gp.getX());
                    maxX = Math.max(maxX, gp.getX());
                    minZ = Math.min(minZ, gp.getZ());
                    maxZ = Math.max(maxZ, gp.getZ());
                }

                considerSpawnerlessCandidate((minX + maxX) / 2, y + 1, (minZ + maxZ) / 2);
            }
        }
    }

    private void considerSpawnerlessCandidate(int baseCx, int cy, int baseCz) {
        Room best = null;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Room r = findRoom(baseCx + dx, cy, baseCz + dz, false);
                if (r != null && (best == null || r.fit() > best.fit())) best = r;
            }
        }

        if (best == null) return;
        if (roomHasSpawnerBlock(best)) return;

        BlockPos center = new BlockPos(best.cx(), best.cy(), best.cz());
        if (sites.containsKey(center) || pendingEval.containsKey(center)) return;
        if (!spawnerlessCandidateWins(best)) return;
        removeOverlapping(best);

        pendingEval.put(center, new PendingSite(center, false, null, best));
    }

    private boolean roomHasSpawnerBlock(Room room) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = room.minX(); x <= room.maxX(); x++) {
            for (int y = room.minY(); y <= room.maxY(); y++) {
                for (int z = room.minZ(); z <= room.maxZ(); z++) {
                    if (mc.world.getBlockState(cursor.set(x, y, z)).getBlock() == Blocks.SPAWNER) return true;
                }
            }
        }
        return false;
    }

    private boolean spawnerlessCandidateWins(Room room) {
        for (Site s : sites.values()) {
            if (boxesOverlap(room, s.room) && (s.hasSpawner || s.room.fit() >= room.fit())) return false;
        }
        for (PendingSite ps : pendingEval.values()) {
            if (boxesOverlap(room, ps.room()) && (ps.hasSpawner() || ps.room().fit() >= room.fit())) return false;
        }
        return true;
    }

    private void removeOverlapping(Room room) {
        sites.entrySet().removeIf(e -> boxesOverlap(room, e.getValue().room));
        pendingEval.entrySet().removeIf(e -> boxesOverlap(room, e.getValue().room()));
    }

    private void removeOverlappingSpawnerless(Room room) {
        sites.entrySet().removeIf(e -> !e.getValue().hasSpawner && boxesOverlap(room, e.getValue().room));
        pendingEval.entrySet().removeIf(e -> !e.getValue().hasSpawner() && boxesOverlap(room, e.getValue().room()));
    }

    private static boolean boxesOverlap(Room a, Room b) {
        return a.minX() <= b.maxX() && a.maxX() >= b.minX()
            && a.minY() <= b.maxY() && a.maxY() >= b.minY()
            && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }

    private Room findRoom(int cx, int cy, int cz, boolean spawnerAtCenter) {
        Room best = null;
        double bestFit = -1;
        Room fallback = null;
        double fallbackFit = -1;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int k = 2; k <= 3; k++) {
            for (int p = 2; p <= 3; p++) {
                int floorMinX = cx - k - 1, floorMaxX = cx + k + 1;
                int floorMinZ = cz - p - 1, floorMaxZ = cz + p + 1;
                int interiorMinX = cx - k, interiorMaxX = cx + k;
                int interiorMinZ = cz - p, interiorMaxZ = cz + p;

                int aireSol = (floorMaxX - floorMinX + 1) * (floorMaxZ - floorMinZ + 1);
                int interiorArea = (interiorMaxX - interiorMinX + 1) * (interiorMaxZ - interiorMinZ + 1);
                int casesAnneau = 4 * (aireSol - interiorArea);
                int volumeInterieur = interiorArea * 4;

                int c = 0, m = 0;
                for (int x = floorMinX; x <= floorMaxX; x++) {
                    for (int z = floorMinZ; z <= floorMaxZ; z++) {
                        Block b = mc.world.getBlockState(cursor.set(x, cy - 1, z)).getBlock();
                        if (b == Blocks.COBBLESTONE || b == Blocks.MOSSY_COBBLESTONE) c++;
                        if (b == Blocks.MOSSY_COBBLESTONE) m++;
                    }
                }

                int w = 0;
                for (int y = cy; y <= cy + 3; y++) {
                    for (int x = floorMinX; x <= floorMaxX; x++) {
                        for (int z = floorMinZ; z <= floorMaxZ; z++) {
                            if (x >= interiorMinX && x <= interiorMaxX && z >= interiorMinZ && z <= interiorMaxZ) continue;
                            if (mc.world.getBlockState(cursor.set(x, y, z)).getBlock() == Blocks.COBBLESTONE) w++;
                        }
                    }
                }

                int open = 0, caveAir = 0, iCount = 0;
                for (int y = cy; y <= cy + 3; y++) {
                    for (int x = interiorMinX; x <= interiorMaxX; x++) {
                        for (int z = interiorMinZ; z <= interiorMaxZ; z++) {
                            cursor.set(x, y, z);
                            BlockState state = mc.world.getBlockState(cursor);
                            Block b = state.getBlock();
                            boolean isOpen = isOpenState(state, cursor);

                            if (isOpen) {
                                open++;
                                if (b == Blocks.CAVE_AIR) caveAir++;
                            }

                            if (isOpen || b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.SPAWNER) iCount++;
                        }
                    }
                }

                double anyFit = (double) c / aireSol + (double) w / casesAnneau;
                if (spawnerAtCenter && anyFit > fallbackFit) {
                    fallbackFit = anyFit;
                    boolean fallbackModern = open > 0 && caveAir * 2 >= open;
                    fallback = new Room(cx, cy, cz, k, p, floorMinX, cy - 1, floorMinZ, floorMaxX, cy + 4, floorMaxZ, fallbackModern, anyFit);
                }

                if (c < 0.5 * aireSol) continue;
                if (m < 3) continue;
                if (iCount < 0.6 * volumeInterieur) continue;
                if (w < 0.35 * casesAnneau) continue;

                int ceilingClosed = 0;
                for (int x = interiorMinX; x <= interiorMaxX; x++) {
                    for (int z = interiorMinZ; z <= interiorMaxZ; z++) {
                        cursor.set(x, cy + 4, z);
                        BlockState ceilState = mc.world.getBlockState(cursor);
                        boolean ceilOpen = isOpenState(ceilState, cursor);
                        boolean ceilFluid = !ceilState.getFluidState().isEmpty();
                        if (!ceilOpen && !ceilFluid) ceilingClosed++;
                    }
                }
                if (!spawnerAtCenter && ceilingClosed < 0.6 * interiorArea) continue;

                double fit = (double) c / aireSol + (double) w / casesAnneau;
                if (fit > bestFit) {
                    bestFit = fit;
                    boolean modern = open > 0 && caveAir * 2 >= open;
                    best = new Room(cx, cy, cz, k, p, floorMinX, cy - 1, floorMinZ, floorMaxX, cy + 4, floorMaxZ, modern, fit);
                }
            }
        }

        return best != null ? best : fallback;
    }

    private boolean isOpenState(BlockState state, BlockPos pos) {
        Block block = state.getBlock();
        if (state.getFluidState().isIn(FluidTags.LAVA)) return false;
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR) return true;
        if (state.getFluidState().isIn(FluidTags.WATER)) return true;
        return state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private void evaluateTrialPending() {
        Iterator<BlockPos> it = trialPending.iterator();

        while (it.hasNext()) {
            BlockPos pos = it.next();

            if (trialFound.containsKey(pos)) {
                it.remove();
                continue;
            }

            if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                it.remove();
                continue;
            }

            BlockEntity be = mc.world.getBlockEntity(pos);

            if (be instanceof TrialSpawnerBlockEntity trial) {
                classifyTrialSpawner(pos, trial);
            }

            it.remove();
        }
    }

    private void classifyTrialSpawner(BlockPos pos, TrialSpawnerBlockEntity trial) {
        if (!detectTrial.get()) return;
        if (trial.getSpawnerState() == TrialSpawnerState.WAITING_FOR_PLAYERS) return;

        int chests = countContainersAround(pos);
        if (requireChests.get() && chests == 0) return;

        record(pos, Category.TRIAL, "trial", chests);
    }

    private void record(BlockPos pos, Category category, String mob, int chests) {
        trialFound.put(pos, new Detection(category, mob, chests, new Animation(fadeTime.get())));
    }

    private void evaluateSitesBudget() {
        long start = System.nanoTime();
        List<PendingSite> requeue = new ArrayList<>();
        Iterator<Map.Entry<BlockPos, PendingSite>> it = pendingEval.entrySet().iterator();
        boolean evaluatedOne = false;

        while (it.hasNext()) {
            if (evaluatedOne && System.nanoTime() - start >= 4_000_000L) break;

            PendingSite ps = it.next().getValue();
            it.remove();
            evaluatedOne = true;

            if (evaluateSite(ps)) requeue.add(ps);
        }

        for (PendingSite ps : requeue) pendingEval.put(ps.center(), ps);
    }

    private boolean evaluateSite(PendingSite ps) {
        BlockPos center = ps.center();
        Room room = ps.room();

        int loadMinX = room.minX() - 12, loadMaxX = room.maxX() + 12;
        int loadMinZ = room.minZ() - 12, loadMaxZ = room.maxZ() + 12;
        int loadMinChunkX = loadMinX >> 4, loadMaxChunkX = loadMaxX >> 4;
        int loadMinChunkZ = loadMinZ >> 4, loadMaxChunkZ = loadMaxZ >> 4;

        boolean allLoaded = true;
        outer:
        for (int cx = loadMinChunkX; cx <= loadMaxChunkX; cx++) {
            for (int cz = loadMinChunkZ; cz <= loadMaxChunkZ; cz++) {
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) {
                    allLoaded = false;
                    break outer;
                }
            }
        }

        if (!allLoaded) {
            if (!mc.world.getChunkManager().isChunkLoaded(center.getX() >> 4, center.getZ() >> 4)) return false;
            return true;
        }

        int chests = countContainersAround(center);

        if (requireChests.get() && chests == 0) {
            if (ps.hasSpawner()) rejected.add(center);
            return false;
        }

        long t0 = System.nanoTime();

        LightHit lightHit = detectTorches.get() ? computeLight(room) : LightHit.NONE;
        boolean ender = computeEnderChest(room);
        PlayerBlocksResult pbr = computePlayerBlocks(room);
        TunnelResult tr = computeTunnel(room);
        int airCount = room.modern() ? countStrayAir(room) : 0;
        boolean air = room.modern() && airCount > 0;

        Site site = new Site(center, room, ps.hasSpawner(), ps.mob(), room.modern());
        site.containers = chests;
        site.light = lightHit.found();
        site.lightLabel = lightHit.label();
        site.lightPos = lightHit.pos();
        site.ender = ender;
        site.playerBlocks = pbr.any();
        site.playerBlockTypes = pbr.types();
        site.broken = !ps.hasSpawner();
        site.tunnel = tr.hit();
        site.tunnelFeature = tr.feature();
        site.air = air;
        site.airCount = airCount;
        site.roomContainers = computeRoomContainers(room);
        site.discoveredAt = System.currentTimeMillis();
        site.discoveredAtTick = totalTicks;
        site.animation = new Animation(fadeTime.get());

        if (ps.hasSpawner()) checkMobSignal(site);

        recomputeScoreAndCategory(site);

        sites.put(center, site);

        long evalMs = (System.nanoTime() - t0) / 1_000_000;
        logSite(site, evalMs);

        return false;
    }

    private void recheckMobSignals() {
        long now = System.currentTimeMillis();

        for (Site site : sites.values()) {
            if (!site.hasSpawner || site.mobs) continue;
            if (site.discoveredAtTick == totalTicks) continue;
            if (now - site.discoveredAt > 15_000) continue;

            long t0 = System.nanoTime();
            boolean hit = checkMobSignal(site);
            long evalMs = (System.nanoTime() - t0) / 1_000_000;

            if (hit) {
                site.mobs = true;
                recomputeScoreAndCategory(site);
                logSite(site, evalMs);
            }
        }
    }

    private boolean checkMobSignal(Site site) {
        if (mc.player == null) return false;

        BlockPos c = site.center;
        double distSq = mc.player.squaredDistanceTo(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5);

        if (distSq <= 17.0 * 17.0) {
            approached.add(c);
            return false;
        }

        if (approached.contains(c)) return false;

        EntityType<?> type = switch (site.mob) {
            case "zombie" -> EntityType.ZOMBIE;
            case "skeleton" -> EntityType.SKELETON;
            case "spider" -> EntityType.SPIDER;
            default -> null;
        };
        if (type == null) return false;

        int cx = c.getX(), cy = c.getY(), cz = c.getZ();
        Box box = new Box(cx - 5, cy - 1, cz - 5, cx + 6, cy + 3, cz + 6);
        int count = mc.world.getOtherEntities(null, box, e -> e.getType() == type).size();

        site.mobCount = count;
        return count >= minMobs.get();
    }

    private void recomputeScoreAndCategory(Site site) {
        int score = 0;
        if (site.light) score += weightLight.get();
        if (site.ender) score += weightEnderChest.get();
        if (site.playerBlocks) score += weightPlayerBlocks.get();
        if (site.broken) score += weightBrokenSpawner.get();
        if (site.mobs) score += weightMobs.get();
        if (site.tunnel) score += weightTunnel.get();
        if (site.air) score += weightAir.get();
        site.score = score;

        boolean traced = score >= triggerScore.get();
        boolean strong = site.light || site.ender || site.playerBlocks;
        int containerCount = site.containers;

        Category category;
        if (traced && strong && containerCount > 0) category = Category.STASH;
        else if (traced && strong) category = Category.LIT;
        else if (traced && containerCount > 0) category = Category.DUNGEON;
        else if (traced) category = Category.EMPTIED;
        else if (containerCount > 0) category = Category.INTACT;
        else category = Category.EMPTY;

        site.category = category;
    }

    private record LightHit(boolean found, String label, BlockPos pos) {
        static final LightHit NONE = new LightHit(false, null, null);
    }

    private LightHit computeLight(Room room) {
        int range = lightRange.get();
        boolean inNether = mc.world.getRegistryKey() == net.minecraft.world.World.NETHER;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = room.minX() - range; x <= room.maxX() + range; x++) {
            for (int y = room.minY() - range; y <= room.maxY() + range; y++) {
                for (int z = room.minZ() - range; z <= room.maxZ() + range; z++) {
                    cursor.set(x, y, z);
                    BlockState state = mc.world.getBlockState(cursor);
                    if (isWhitelistedLight(state, cursor, inNether)) {
                        return new LightHit(true, lightLabel(state.getBlock()), cursor.toImmutable());
                    }
                }
            }
        }

        return LightHit.NONE;
    }

    private boolean isWhitelistedLight(BlockState state, BlockPos pos, boolean inNether) {
        Block block = state.getBlock();

        if (block instanceof AbstractTorchBlock) return true;
        if (block instanceof LanternBlock) return true;

        if (block == Blocks.JACK_O_LANTERN || block == Blocks.REDSTONE_LAMP || block == Blocks.END_ROD
            || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE || block == Blocks.BEACON
            || block == Blocks.OCHRE_FROGLIGHT || block == Blocks.VERDANT_FROGLIGHT || block == Blocks.PEARLESCENT_FROGLIGHT) {
            return true;
        }

        if (block == Blocks.SEA_LANTERN) return !waterAdjacent(pos);
        if ((block == Blocks.GLOWSTONE || block == Blocks.SHROOMLIGHT) && !inNether) return true;

        return false;
    }

    private boolean waterAdjacent(BlockPos pos) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

        for (int[] d : dirs) {
            m.set(pos.getX() + d[0], pos.getY() + d[1], pos.getZ() + d[2]);
            if (mc.world.getFluidState(m).isIn(FluidTags.WATER)) return true;
        }

        return false;
    }

    private static String lightLabel(Block block) {
        if (block instanceof AbstractTorchBlock) return "torch";
        if (block instanceof LanternBlock) return "lantern";
        return Registries.BLOCK.getId(block).getPath();
    }

    private boolean computeEnderChest(Room room) {
        return anyBlockEntityInZone(room, traceRange.get(), be -> be instanceof EnderChestBlockEntity);
    }

    private boolean anyBlockEntityInZone(Room room, int range, Predicate<BlockEntity> test) {
        int minX = room.minX() - range, maxX = room.maxX() + range;
        int minY = room.minY() - range, maxY = room.maxY() + range;
        int minZ = room.minZ() - range, maxZ = room.maxZ() + range;

        int minChunkX = minX >> 4, maxChunkX = maxX >> 4, minChunkZ = minZ >> 4, maxChunkZ = maxZ >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!mc.world.isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = mc.world.getChunk(cx, cz);

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getPos();
                    if (p.getX() < minX || p.getX() > maxX || p.getY() < minY || p.getY() > maxY
                        || p.getZ() < minZ || p.getZ() > maxZ) continue;
                    if (test.test(be)) return true;
                }
            }
        }

        return false;
    }

    private record PlayerBlocksResult(boolean any, Set<String> types) {}

    private PlayerBlocksResult computePlayerBlocks(Room room) {
        int range = traceRange.get();
        Set<String> types = new TreeSet<>();

        collectPlayerBlockEntities(room, range, types);
        collectPlayerBlockStates(room, range, types);
        collectPlayerEntities(room, range, types);

        return new PlayerBlocksResult(!types.isEmpty(), types);
    }

    private void collectPlayerBlockEntities(Room room, int range, Set<String> types) {
        int minX = room.minX() - range, maxX = room.maxX() + range;
        int minY = room.minY() - range, maxY = room.maxY() + range;
        int minZ = room.minZ() - range, maxZ = room.maxZ() + range;

        int minChunkX = minX >> 4, maxChunkX = maxX >> 4, minChunkZ = minZ >> 4, maxChunkZ = maxZ >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!mc.world.isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = mc.world.getChunk(cx, cz);

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getPos();
                    if (p.getX() < minX || p.getX() > maxX || p.getY() < minY || p.getY() > maxY
                        || p.getZ() < minZ || p.getZ() > maxZ) continue;

                    String type = playerBlockEntityType(be);
                    if (type != null) types.add(type);
                }
            }
        }
    }

    private static String playerBlockEntityType(BlockEntity be) {
        if (be instanceof BedBlockEntity) return "bed";
        if (be instanceof AbstractFurnaceBlockEntity) return "furnace";
        if (be instanceof EnchantingTableBlockEntity) return "enchanting_table";
        if (be instanceof HangingSignBlockEntity) return "hanging_sign";
        if (be instanceof SignBlockEntity) return "sign";
        if (be instanceof BeaconBlockEntity) return "beacon";
        if (be instanceof HopperBlockEntity) return "hopper";
        if (be instanceof JukeboxBlockEntity) return "jukebox";
        if (be instanceof CrafterBlockEntity) return "crafter";
        if (be instanceof ShulkerBoxBlockEntity) return "shulker_box";
        return null;
    }

    private void collectPlayerBlockStates(Room room, int range, Set<String> types) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = room.minX() - range; x <= room.maxX() + range; x++) {
            for (int y = room.minY() - range; y <= room.maxY() + range; y++) {
                for (int z = room.minZ() - range; z <= room.maxZ() + range; z++) {
                    cursor.set(x, y, z);
                    String type = playerBlockType(mc.world.getBlockState(cursor).getBlock());
                    if (type != null) types.add(type);
                }
            }
        }
    }

    private static String playerBlockType(Block block) {
        if (block == Blocks.CRAFTING_TABLE) return "crafting_table";
        if (block == Blocks.ANVIL) return "anvil";
        if (block == Blocks.CHIPPED_ANVIL) return "chipped_anvil";
        if (block == Blocks.DAMAGED_ANVIL) return "damaged_anvil";
        if (block == Blocks.RESPAWN_ANCHOR) return "respawn_anchor";
        if (block == Blocks.LODESTONE) return "lodestone";
        if (block == Blocks.NOTE_BLOCK) return "note_block";
        if (block == Blocks.SCAFFOLDING) return "scaffolding";
        return null;
    }

    private void collectPlayerEntities(Room room, int range, Set<String> types) {
        Box box = new Box(
            room.minX() - range, room.minY() - range, room.minZ() - range,
            room.maxX() + range + 1, room.maxY() + range + 1, room.maxZ() + range + 1);

        for (Entity e : mc.world.getOtherEntities(null, box,
            en -> en instanceof ArmorStandEntity || en instanceof ItemFrameEntity)) {
            if (e instanceof GlowItemFrameEntity) types.add("glow_item_frame");
            else if (e instanceof ItemFrameEntity) types.add("item_frame");
            else if (e instanceof ArmorStandEntity) types.add("armor_stand");
        }
    }

    private record TunnelResult(boolean hit, DugHoles.Feature feature) {}

    private TunnelResult computeTunnel(Room room) {
        int minX = room.minX() - 8, maxX = room.maxX() + 8;
        int minZ = room.minZ() - 8, maxZ = room.maxZ() + 8;
        int sizeX = maxX - minX + 1, sizeZ = maxZ - minZ + 1;
        int minY = room.cy() - 7, maxY = room.cy() + 28;

        DugHoles.Params params = new DugHoles.Params(4, 4, 3, 8, 2, true, true, true, true);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        DugHoles.BlockSource src = (x, y, z) -> mc.world.getBlockState(cursor.set(x, y, z));

        List<DugHoles.Feature> features = DugHoles.analyze(src, minX, minZ, sizeX, sizeZ, 4, minY, maxY, params);

        boolean hit = false;
        DugHoles.Feature hitFeature = null;

        for (DugHoles.Feature f : features) {
            boolean overlaps = f.minX() - 1 <= room.maxX() && f.maxX() + 1 >= room.minX()
                && f.minY() - 1 <= room.maxY() && f.maxY() + 1 >= room.minY()
                && f.minZ() - 1 <= room.maxZ() && f.maxZ() + 1 >= room.minZ();
            if (!overlaps) continue;

            if (f.kind() != DugHoles.Kind.POCKET && !hit) {
                hit = true;
                hitFeature = f;
            }
        }

        return new TunnelResult(hit, hitFeature);
    }

    private int countStrayAir(Room room) {
        int count = 0;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int k = room.k(), p = room.p(), cx = room.cx(), cy = room.cy(), cz = room.cz();

        boolean hasFallingBlock = false;
        outer:
        for (int x = cx - k; x <= cx + k; x++) {
            for (int z = cz - p; z <= cz + p; z++) {
                for (int y = cy; y <= cy + 3; y++) {
                    Block b = mc.world.getBlockState(cursor.set(x, y, z)).getBlock();
                    if (b == Blocks.GRAVEL || b == Blocks.SAND || b == Blocks.RED_SAND) {
                        hasFallingBlock = true;
                        break outer;
                    }
                }
            }
        }

        for (int x = cx - k; x <= cx + k; x++) {
            for (int z = cz - p; z <= cz + p; z++) {
                for (int y = cy; y <= cy + 3; y++) {
                    if (x == cx && y == cy && z == cz) continue;
                    if (mc.world.getBlockState(cursor.set(x, y, z)).getBlock() == Blocks.AIR) count++;
                }
            }
        }

        for (int x = cx - k; x <= cx + k; x++) {
            for (int z = cz - p; z <= cz + p; z++) {
                if (mc.world.getBlockState(cursor.set(x, cy - 1, z)).getBlock() == Blocks.AIR) count++;
            }
        }

        if (!hasFallingBlock) {
            for (int x = cx - k; x <= cx + k; x++) {
                for (int z = cz - p; z <= cz + p; z++) {
                    if (mc.world.getBlockState(cursor.set(x, cy + 4, z)).getBlock() == Blocks.AIR) count++;
                }
            }
        }

        return count;
    }

    private List<BlockPos> computeRoomContainers(Room room) {
        List<BlockPos> result = new ArrayList<>();

        int minX = room.minX() - 1, maxX = room.maxX() + 1;
        int minY = room.minY() - 1, maxY = room.maxY() + 1;
        int minZ = room.minZ() - 1, maxZ = room.maxZ() + 1;

        int minChunkX = minX >> 4, maxChunkX = maxX >> 4, minChunkZ = minZ >> 4, maxChunkZ = maxZ >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!mc.world.isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = mc.world.getChunk(cx, cz);

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getPos();
                    if (p.getX() < minX || p.getX() > maxX || p.getY() < minY || p.getY() > maxY
                        || p.getZ() < minZ || p.getZ() > maxZ) continue;

                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity) {
                        result.add(p.toImmutable());
                    }
                }
            }
        }

        return result;
    }

    private int countContainersAround(BlockPos pos) {
        int range = chestRange.get();
        int count = 0;

        int minChunkX = (pos.getX() - range) >> 4;
        int maxChunkX = (pos.getX() + range) >> 4;
        int minChunkZ = (pos.getZ() - range) >> 4;
        int maxChunkZ = (pos.getZ() + range) >> 4;
        int rangeSq = range * range;

        List<Block> wanted = containers.get();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!mc.world.isChunkLoaded(cx, cz)) continue;

                WorldChunk chunk = mc.world.getChunk(cx, cz);

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.getPos().getSquaredDistance(pos) > rangeSq) continue;

                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                        || be instanceof ShulkerBoxBlockEntity
                        || wanted.contains(be.getCachedState().getBlock())) {
                        count++;
                    }
                }
            }
        }

        Box box = new Box(
            pos.getX() - range, pos.getY() - range, pos.getZ() - range,
            pos.getX() + range, pos.getY() + range, pos.getZ() + range);

        count += mc.world.getEntitiesByClass(ChestMinecartEntity.class, box, e -> true).size();
        count += mc.world.getEntitiesByClass(HopperMinecartEntity.class, box, e -> true).size();

        return count;
    }

    private String signalLetters(Site site) {
        StringBuilder sb = new StringBuilder();
        if (site.light) sb.append('L');
        if (site.ender) sb.append('E');
        if (site.playerBlocks) sb.append('P');
        if (site.broken) sb.append('B');
        if (site.mobs) sb.append('M');
        if (site.tunnel) sb.append('T');
        if (site.air) sb.append('A');
        return sb.toString();
    }

    private String tunnelLogValue(Site site) {
        if (!site.tunnel || site.tunnelFeature == null) return "none";
        DugHoles.Feature f = site.tunnelFeature;
        return f.kind() + "@" + f.minX() + "," + f.minY() + "," + f.minZ()
            + "-" + f.maxX() + "," + f.maxY() + "," + f.maxZ();
    }

    private String lightLogValue(Site site) {
        if (!site.light || site.lightPos == null) return "none";
        return site.lightLabel + "@" + site.lightPos.getX() + "," + site.lightPos.getY() + "," + site.lightPos.getZ();
    }

    private void logSite(Site site, long evalMs) {
        BlockPos c = site.center;
        String mobLabel = site.hasSpawner ? site.mob : "none";
        String roomSize = (2 * site.room.k() + 1) + "x" + (2 * site.room.p() + 1);

        if (log.get()) HunterBuddyAddon.LOG.info(
            "[SpawnerDetector] site {} {} {} mob={} spawner={} room={} modern={} chests={} roomChests={} score={} signals={} light={} blocks={} mobs={} tunnel={} air={} category={} evalMs={}",
            c.getX(), c.getY(), c.getZ(), mobLabel, site.hasSpawner ? "yes" : "no", roomSize,
            site.modern ? "yes" : "no", site.containers, site.roomContainers.size(), site.score,
            signalLetters(site), lightLogValue(site), site.playerBlockTypes, site.mobCount,
            tunnelLogValue(site), site.airCount, site.category.label, evalMs);
    }

    public Collection<Site> sites() {
        return Collections.unmodifiableCollection(sites.values());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (trialFound.isEmpty() && sites.isEmpty()) return;

        double maxDistance = renderDistance.get();
        double maxDistanceSq = maxDistance * maxDistance;
        Vec3d eye = mc.player.getEyePos();

        for (Map.Entry<BlockPos, Detection> entry : trialFound.entrySet()) {
            BlockPos pos = entry.getKey();
            Detection detection = entry.getValue();

            double distanceSq = eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

            detection.animation.setVisible(distanceSq <= maxDistanceSq);

            float factor = detection.animation.factor();
            if (factor <= 0.0f) continue;

            if (distanceFade.get()) {
                double distance = Math.sqrt(distanceSq);
                factor *= (float) (1.0 - Math.min(0.75, distance / maxDistance * 0.75));
            }

            SettingColor base = colorOf(detection.category);
            Color line = new Color(base.r, base.g, base.b, (int) (base.a * factor));
            Color side = new Color(base.r, base.g, base.b, (int) (base.a * factor * 0.25));

            event.renderer.box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                side, line, shapeMode.get(), 0);
        }

        for (Site site : sites.values()) {
            if (site.category == Category.INTACT && !showIntact.get()) continue;
            if (site.category == Category.EMPTY && !showEmpty.get()) continue;
            if (ignoreBelowZero.get() && site.center.getY() < 0) continue;

            BlockPos pos = site.center;
            double distanceSq = eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

            site.animation.setVisible(distanceSq <= maxDistanceSq);

            float factor = site.animation.factor();
            if (factor <= 0.0f) continue;

            if (distanceFade.get()) {
                double distance = Math.sqrt(distanceSq);
                factor *= (float) (1.0 - Math.min(0.75, distance / maxDistance * 0.75));
            }

            SettingColor base = colorOf(site.category);
            Color line = new Color(base.r, base.g, base.b, (int) (base.a * factor));
            Color side = new Color(base.r, base.g, base.b, (int) (base.a * factor * 0.25));

            event.renderer.box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                side, line, shapeMode.get(), 0);
        }
    }

    @EventHandler
    private void onRender2D(meteordevelopment.meteorclient.events.render.Render2DEvent event) {
        if (!showLabel.get() || mc.player == null || mc.world == null) return;
        if (trialFound.isEmpty() && sites.isEmpty()) return;

        double maxDistance = renderDistance.get();
        double maxDistanceSq = maxDistance * maxDistance;
        Vec3d eye = mc.player.getEyePos();

        for (Map.Entry<BlockPos, Detection> entry : trialFound.entrySet()) {
            BlockPos pos = entry.getKey();
            Detection detection = entry.getValue();

            if (eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistanceSq) continue;

            float factor = detection.animation.factor();
            if (factor <= 0.0f) continue;

            org.joml.Vector3d screen = new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5);
            if (!NametagUtils.to2D(screen, labelScale.get())) continue;

            String text = detection.mob + "  " + detection.chests + "x";
            int width = mc.textRenderer.getWidth(text);
            SettingColor base = colorOf(detection.category);

            NametagUtils.begin(screen, event.drawContext);
            event.drawContext.fill(-width / 2 - 2, -2, width / 2 + 2, 10,
                new Color(0, 0, 0, (int) (140 * factor)).getPacked());
            event.drawContext.drawText(mc.textRenderer, text, -width / 2, 0,
                new Color(base.r, base.g, base.b, (int) (255 * factor)).getPacked(), true);
            NametagUtils.end(event.drawContext);
        }

        for (Site site : sites.values()) {
            if (site.category == Category.INTACT && !showIntact.get()) continue;
            if (site.category == Category.EMPTY && !showEmpty.get()) continue;
            if (ignoreBelowZero.get() && site.center.getY() < 0) continue;

            BlockPos pos = site.center;
            if (eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistanceSq) continue;

            float factor = site.animation.factor();
            if (factor <= 0.0f) continue;

            org.joml.Vector3d screen = new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5);
            if (!NametagUtils.to2D(screen, labelScale.get())) continue;

            String text = (site.hasSpawner ? site.mob : "no spawner") + " " + site.containers + "x s" + site.score
                + " " + signalLetters(site);
            int width = mc.textRenderer.getWidth(text);
            SettingColor base = colorOf(site.category);

            NametagUtils.begin(screen, event.drawContext);
            event.drawContext.fill(-width / 2 - 2, -2, width / 2 + 2, 10,
                new Color(0, 0, 0, (int) (140 * factor)).getPacked());
            event.drawContext.drawText(mc.textRenderer, text, -width / 2, 0,
                new Color(base.r, base.g, base.b, (int) (255 * factor)).getPacked(), true);
            NametagUtils.end(event.drawContext);
        }
    }

    private AreaLoader areaLoaderModule() { return Modules.get().get(AreaLoader.class); }

    private Module selectedFlightModule() {
        return switch (flightModule.get()) {
            case AreaLoader -> Modules.get().get(AreaLoader.class);
            case RocketFly -> Modules.get().get(RocketFly.class);
            case WaypointFollower -> Modules.get().get(WaypointFollower.class);
            case None -> null;
        };
    }
    private Pitch40 pitch40Module() { return Modules.get().get(Pitch40.class); }
    private ElytraRecast elytraRecastModule() { return Modules.get().get(ElytraRecast.class); }
    private MlepMine mlepMineModule() { return Modules.get().get(MlepMine.class); }
    private AutoFlyingRegear autoFlyingRegearModule() { return Modules.get().get(AutoFlyingRegear.class); }
    private WaypointFollower waypointFollowerModule() { return Modules.get().get(WaypointFollower.class); }
    private AutoEatSync autoEatSyncModule() { return Modules.get().get(AutoEatSync.class); }
    private KillAuraPlus killAuraPlusModule() { return Modules.get().get(KillAuraPlus.class); }

    private File visitedFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "spawnerdetector"),
            meteordevelopment.meteorclient.utils.Utils.getFileWorldName()), "visited.json");
    }

    private void loadVisited() {
        visitedKeys.clear();
        visitedEntries.clear();
        File f = visitedFile();
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            VisitedEntry[] arr = visitedGson.fromJson(r, VisitedEntry[].class);
            if (arr != null) {
                for (VisitedEntry e : arr) {
                    visitedEntries.add(e);
                    visitedKeys.add(BlockPos.asLong(e.x(), e.y(), e.z()));
                }
            }
        } catch (Exception e) {
            if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] visited load failed {}", e.toString());
        }
    }

    private void saveVisited() {
        File f = visitedFile();
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            try (Writer w = new FileWriter(f)) {
                visitedGson.toJson(visitedEntries, w);
            }
        } catch (Exception e) {
            if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] visited save failed {}", e.toString());
        }
    }

    private void onAutoFarmChanged(Boolean v) {
        if (!Boolean.TRUE.equals(v) && state == AutoState.FAILED) state = AutoState.IDLE;
        if (Boolean.TRUE.equals(v)) retryAbandonedSites();
    }

    private void retryAbandonedSites() {
        List<VisitedEntry> kept = new ArrayList<>();
        Set<Long> keptKeys = new java.util.HashSet<>();
        int removed = 0;

        for (VisitedEntry e : visitedEntries) {
            String outcome = e.outcome();
            boolean abandoned = outcome != null && (outcome.startsWith("aborted:") || outcome.startsWith("failed:"));
            if (abandoned) {
                removed++;
                continue;
            }
            kept.add(e);
            keptKeys.add(BlockPos.asLong(e.x(), e.y(), e.z()));
        }

        if (removed == 0) return;

        visitedEntries.clear();
        visitedEntries.addAll(kept);
        visitedKeys.clear();
        visitedKeys.addAll(keptKeys);
        saveVisited();

        if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] retry abandoned sites removed={}", removed);
    }

    private void clearVisited() {
        int n = visitedEntries.size();
        visitedKeys.clear();
        visitedEntries.clear();
        saveVisited();
        info("Visited dungeons cleared (%d)", n);
        autoLog("CLEAR", mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN, "visited list cleared n=" + n);
    }

    private boolean isVisited(BlockPos pos) {
        return visitedKeys.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
    }

    private void markVisited(BlockPos pos, String outcome) {
        visitedKeys.add(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        visitedEntries.add(new VisitedEntry(pos.getX(), pos.getY(), pos.getZ(), outcome, System.currentTimeMillis()));
        saveVisited();
    }

    private void autoLog(String tag, BlockPos pos, String details) {
        if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] {} site={},{},{} {}",
            tag, pos.getX(), pos.getY(), pos.getZ(), details);
    }

    private void enterState(AutoState next, String details) {
        state = next;
        stateTicks = 0;
        autoLog(next.name(), autoSite.center, details);
    }

    private void enterDigState(String details) {
        enterState(autoColumn.side() ? AutoState.SIDE_DIG : AutoState.DIG, details);
    }

    private void logSkipOnce(BlockPos pos, String reason) {
        long now = System.currentTimeMillis();
        Long last = autoSkipLoggedAt.get(pos);
        if (last != null && now - last < 60_000L) return;
        autoSkipLoggedAt.put(pos, now);
        if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] skip {} {} {} reason={}", pos.getX(), pos.getY(), pos.getZ(), reason);
    }

    private void releaseAllAutoKeys() {
        Utils.setPressed(mc.options.forwardKey, false);
        Utils.setPressed(mc.options.backKey, false);
        Utils.setPressed(mc.options.leftKey, false);
        Utils.setPressed(mc.options.rightKey, false);
        Utils.setPressed(mc.options.sneakKey, false);
        Utils.setPressed(mc.options.jumpKey, false);
        Utils.setPressed(mc.options.attackKey, false);
        Utils.setPressed(mc.options.sprintKey, false);
        BaritoneHelper.clearInputs();
    }

    private IBaritone primaryBaritone() {
        if (!BaritoneHelper.isAvailable()) return null;
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasBlockAbove(int x, int y, int z, int count) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dy = 1; dy <= count; dy++) {
            cursor.set(x, y + dy, z);
            if (!isOpenState(mc.world.getBlockState(cursor), cursor)) return true;
        }
        return false;
    }

    private boolean isSurfaceSkippable(BlockState state, BlockPos pos) {
        if (isOpenState(state, pos)) return true;
        return state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS);
    }

    private boolean isLava(BlockState state) {
        return state.getFluidState().isIn(FluidTags.LAVA);
    }

    private boolean isLavaNearShaft(BlockPos below, BlockPos feet) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int[][] neighbors = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] n : neighbors) {
            cursor.set(below.getX() + n[0], below.getY(), below.getZ() + n[1]);
            if (isLava(mc.world.getBlockState(cursor))) return true;
        }

        for (int[] n : neighbors) {
            if (n[0] == 0 && n[1] == 0) continue;
            cursor.set(feet.getX() + n[0], feet.getY(), feet.getZ() + n[1]);
            if (isLava(mc.world.getBlockState(cursor))) return true;
        }

        return false;
    }

    private boolean columnHasWater(int x, int z, int fromY, int toY) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = fromY; y <= toY; y++) {
            if (mc.world.getFluidState(cursor.set(x, y, z)).isIn(FluidTags.WATER)) return true;
        }
        return false;
    }

    private static boolean insideInterior(Room room, int x, int z) {
        return x >= room.cx() - room.k() && x <= room.cx() + room.k()
            && z >= room.cz() - room.p() && z <= room.cz() + room.p();
    }

    private ColumnPlan evaluateColumn(Room room, int x, int z) {
        return evaluateColumn(room, x, z, autoMaxFall.get());
    }

    private ColumnPlan evaluateColumn(Room room, int x, int z, int maxFallAllowed) {
        if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return null;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        BlockState floorState = mc.world.getBlockState(cursor.set(x, room.cy() - 1, z));
        if (isOpenState(floorState, cursor)) {
            int fy = room.cy() - 1;
            BlockState s = floorState;
            while (isOpenState(s, cursor) && fy > mc.world.getBottomY()) {
                fy--;
                s = mc.world.getBlockState(cursor.set(x, fy, z));
            }
            if (isLava(s)) return null;
        }

        int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        BlockState topState = mc.world.getBlockState(cursor.set(x, topY, z));

        int startY, exitY;
        boolean water;

        if (topState.getFluidState().isIn(FluidTags.WATER)) {
            int waterTop = topY;
            int y = topY;
            while (y > room.cy() + 4) {
                BlockState s = mc.world.getBlockState(cursor.set(x, y - 1, z));
                if (!s.getFluidState().isIn(FluidTags.WATER) && !s.isAir()) break;
                y--;
            }
            int fond = y - 1;
            if (isLava(mc.world.getBlockState(cursor.set(x, fond, z)))) return null;
            startY = fond + 1;
            exitY = waterTop + 1;
            water = true;
            if (startY < room.cy() + 4) return null;
            if (startY == room.cy() + 4
                && (isLava(mc.world.getBlockState(cursor.set(x, startY, z)))
                    || isLava(mc.world.getBlockState(cursor.set(x + 1, startY, z)))
                    || isLava(mc.world.getBlockState(cursor.set(x - 1, startY, z)))
                    || isLava(mc.world.getBlockState(cursor.set(x, startY, z + 1)))
                    || isLava(mc.world.getBlockState(cursor.set(x, startY, z - 1))))) {
                return null;
            }
        } else {
            int y = topY;
            while (y > room.cy() + 4 && isSurfaceSkippable(mc.world.getBlockState(cursor.set(x, y, z)), cursor)) y--;
            startY = y + 1;
            exitY = y + 1;
            water = false;
        }

        if (startY < room.cy() - 16) return null;

        int[][] neighbors = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int blocksToBreak = 0, waterCells = 0, maxGap = 0, airRun = 0;

        for (int y = startY - 1; y >= room.cy() + 4; y--) {
            for (int[] n : neighbors) {
                cursor.set(x + n[0], y, z + n[1]);
                if (isLava(mc.world.getBlockState(cursor))) return null;
            }

            BlockState center = mc.world.getBlockState(cursor.set(x, y, z));
            boolean isFluidWater = center.getFluidState().isIn(FluidTags.WATER);

            if (isFluidWater) {
                waterCells++;
                airRun = 0;
            } else if (isOpenState(center, cursor)) {
                airRun++;
                maxGap = Math.max(maxGap, airRun);
            } else {
                if (airRun > maxFallAllowed) return null;
                airRun = 0;
                blocksToBreak++;
            }
        }

        boolean skyBlocked = false;
        for (int dy = 1; dy <= SKY_CHECK_HEIGHT; dy++) {
            cursor.set(x, exitY + dy, z);
            if (!isOpenState(mc.world.getBlockState(cursor), cursor)) {
                skyBlocked = true;
                break;
            }
        }

        BlockPos rim = null;
        int holeTopY = exitY;

        if (!water) {
            RimResult rr = computeRim(x, z, startY);
            rim = rr.rim();
            holeTopY = rr.holeTopY();
        }

        return new ColumnPlan(x, z, startY, exitY, water, blocksToBreak, waterCells, maxGap, skyBlocked, holeTopY, rim, false, null);
    }

    private RimResult computeRim(int x, int z, int startY) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos rim = null;
        int rimFeet = Integer.MIN_VALUE;
        double rimDistSq = Double.MAX_VALUE;
        int[][] rimNeighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] n : rimNeighbors) {
            int nx = x + n[0], nz = z + n[1];
            if (!mc.world.getChunkManager().isChunkLoaded(nx >> 4, nz >> 4)) continue;
            int g = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, nx, nz) - 1;
            BlockState gState = mc.world.getBlockState(cursor.set(nx, g, nz));
            while (g > mc.world.getBottomY() && isSurfaceSkippable(gState, cursor)) {
                g--;
                gState = mc.world.getBlockState(cursor.set(nx, g, nz));
            }
            int feet = g + 1;

            if (feet < startY + 2) continue;
            if (isOpenState(gState, cursor) || !gState.getFluidState().isEmpty()) continue;

            BlockState feetState = mc.world.getBlockState(cursor.set(nx, feet, nz));
            if (!isOpenState(feetState, cursor) || !feetState.getFluidState().isEmpty()) continue;
            BlockState headState = mc.world.getBlockState(cursor.set(nx, feet + 1, nz));
            if (!isOpenState(headState, cursor) || !headState.getFluidState().isEmpty()) continue;

            boolean bridgeClear = true;
            for (int y = startY; y <= feet + 1; y++) {
                BlockState bs = mc.world.getBlockState(cursor.set(x, y, z));
                if (!isOpenState(bs, cursor) || !bs.getFluidState().isEmpty()) { bridgeClear = false; break; }
            }
            if (!bridgeClear) continue;

            double dx = nx - mc.player.getX(), dz = nz - mc.player.getZ();
            double distSq = dx * dx + dz * dz;
            if (feet > rimFeet || (feet == rimFeet && distSq < rimDistSq)) {
                rimFeet = feet;
                rimDistSq = distSq;
                rim = new BlockPos(nx, feet, nz);
            }
        }

        int holeTopY = rim != null ? Math.max(startY, rim.getY()) : startY;
        return new RimResult(rim, holeTopY);
    }

    private ColumnPlan planColumn(Site site) {
        Room room = site.room;

        if (site.tunnelFeature != null && site.tunnelFeature.kind() == DugHoles.Kind.SHAFT) {
            DugHoles.Feature shaft = site.tunnelFeature;
            int shaftX = shaft.minX(), shaftZ = shaft.minZ();

            if (shaftX >= room.minX() && shaftX <= room.maxX() && shaftZ >= room.minZ() && shaftZ <= room.maxZ()) {
                ColumnPlan shaftPlan = evaluateColumn(room, shaftX, shaftZ, Integer.MAX_VALUE);
                if (shaftPlan != null) {
                    autoLog("PLAN", site.center, String.format("using existing shaft %d,%d", shaftX, shaftZ));
                    return shaftPlan;
                }
                autoLog("PLAN", site.center, String.format("existing shaft %d,%d refused", shaftX, shaftZ));
            }
        }

        List<int[]> candidates = new ArrayList<>();
        candidates.add(new int[]{room.cx(), room.cz()});

        List<int[]> ring = new ArrayList<>();
        for (int x = room.cx() - room.k(); x <= room.cx() + room.k(); x++) {
            for (int z = room.cz() - room.p(); z <= room.cz() + room.p(); z++) {
                if (x == room.cx() && z == room.cz()) continue;
                ring.add(new int[]{x, z});
            }
        }
        ring.sort(Comparator.comparingInt(a ->
            (a[0] - room.cx()) * (a[0] - room.cx()) + (a[1] - room.cz()) * (a[1] - room.cz())));
        candidates.addAll(ring);

        ColumnPlan bestBlocked = null;
        ColumnPlan column = null;
        for (int[] c : candidates) {
            ColumnPlan plan = evaluateColumn(room, c[0], c[1]);
            if (plan == null) continue;
            if (!plan.skyBlocked()) { column = plan; break; }
            if (bestBlocked == null) bestBlocked = plan;
        }
        if (column == null) column = bestBlocked;

        ColumnPlan side = findSideHole(site);
        if (side != null && (column == null || side.blocksToBreak() < column.blocksToBreak())) {
            if (sideHoleUseLogged.add(site.center)) autoLog("PLAN", site.center, String.format("using side hole %d,%d cost=%d instead of column cost=%s",
                side.x(), side.z(), side.blocksToBreak(),
                column != null ? String.valueOf(column.blocksToBreak()) : "none"));
            return side;
        }

        return column;
    }

    private ColumnPlan findSideHole(Site site) {
        Room room = site.room;
        int cx = room.cx(), cz = room.cz(), cy = room.cy(), k = room.k(), p = room.p();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        ColumnPlan best = null;
        int bestCost = Integer.MAX_VALUE;
        double bestDistSq = Double.MAX_VALUE;
        List<SideHoleReject> rejects = new ArrayList<>();

        for (int x = cx - k - 3; x <= cx + k + 3; x++) {
            boolean xAligned = x >= cx - k && x <= cx + k;

            for (int z = cz - p - 3; z <= cz + p + 3; z++) {
                boolean zAligned = z >= cz - p && z <= cz + p;
                if (xAligned && zAligned) continue;
                if (!xAligned && !zAligned) continue;
                if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;

                int y = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
                BlockState state = mc.world.getBlockState(cursor.set(x, y, z));
                while (y > cy - 2 && isSurfaceSkippable(state, cursor)) {
                    y--;
                    state = mc.world.getBlockState(cursor.set(x, y, z));
                }

                if (isLava(state)) {
                    rejects.add(new SideHoleReject(x, z, y + 1, "lava"));
                    continue;
                }

                int bottomFeet = y + 1;
                if (bottomFeet > cy + 6) {
                    rejects.add(new SideHoleReject(x, z, bottomFeet, "not deep"));
                    continue;
                }
                if (bottomFeet < cy) {
                    rejects.add(new SideHoleReject(x, z, bottomFeet, "too deep"));
                    continue;
                }

                int tx = Math.max(cx - k, Math.min(cx + k, x));
                int tz = Math.max(cz - p, Math.min(cz + p, z));

                BlockState entryState = mc.world.getBlockState(cursor.set(tx, cy, tz));
                if (entryState.getBlock() == Blocks.SPAWNER || isContainerPresent(cursor)) {
                    rejects.add(new SideHoleReject(x, z, bottomFeet, "entry blocked"));
                    continue;
                }

                List<BlockPos> horizCells = new ArrayList<>();
                int hx = x, hz = z;
                while (hx != tx || hz != tz) {
                    hx += Integer.signum(tx - hx);
                    hz += Integer.signum(tz - hz);
                    horizCells.add(new BlockPos(hx, cy, hz));
                }

                boolean tunnelBlocked = false;
                for (BlockPos hc : horizCells) {
                    for (int dyy = 0; dyy <= 1 && !tunnelBlocked; dyy++) {
                        cursor.set(hc.getX(), cy + dyy, hc.getZ());
                        if (mc.world.getBlockState(cursor).getBlock() == Blocks.SPAWNER || isContainerPresent(cursor)) tunnelBlocked = true;
                    }
                }
                if (tunnelBlocked) {
                    rejects.add(new SideHoleReject(x, z, bottomFeet, "tunnel blocked"));
                    continue;
                }

                int[][] neighbors = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                boolean lavaFound = false;

                outer:
                for (int vy = cy; vy <= bottomFeet - 1; vy++) {
                    for (int[] n : neighbors) {
                        if (isLava(mc.world.getBlockState(cursor.set(x + n[0], vy, z + n[1])))) { lavaFound = true; break outer; }
                    }
                }
                if (!lavaFound) {
                    outer2:
                    for (BlockPos hc : horizCells) {
                        for (int dyy = 0; dyy <= 1; dyy++) {
                            for (int[] n : neighbors) {
                                if (isLava(mc.world.getBlockState(cursor.set(hc.getX() + n[0], cy + dyy, hc.getZ() + n[1])))) { lavaFound = true; break outer2; }
                            }
                        }
                    }
                }
                if (lavaFound) {
                    rejects.add(new SideHoleReject(x, z, bottomFeet, "lava"));
                    continue;
                }

                boolean noFloor = isOpenState(mc.world.getBlockState(cursor.set(x, cy - 1, z)), cursor);
                for (BlockPos hc : horizCells) {
                    if (noFloor) break;
                    noFloor = isOpenState(mc.world.getBlockState(cursor.set(hc.getX(), cy - 1, hc.getZ())), cursor);
                }
                if (noFloor) {
                    rejects.add(new SideHoleReject(x, z, bottomFeet, "no floor"));
                    continue;
                }

                int cost = (bottomFeet - cy) + 2 * horizCells.size();
                double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
                double distSq = dx * dx + dz * dz;

                if (cost < bestCost || (cost == bestCost && distSq < bestDistSq)) {
                    int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
                    int exitY = topY + 1;
                    boolean water = mc.world.getBlockState(cursor.set(x, topY, z)).getFluidState().isIn(FluidTags.WATER);

                    boolean skyBlocked = false;
                    for (int dy = 1; dy <= SKY_CHECK_HEIGHT; dy++) {
                        cursor.set(x, exitY + dy, z);
                        if (!isOpenState(mc.world.getBlockState(cursor), cursor)) { skyBlocked = true; break; }
                    }

                    BlockPos rim = null;
                    int holeTopY = exitY;
                    if (!water) {
                        RimResult rr = computeRim(x, z, bottomFeet);
                        rim = rr.rim();
                        if (rim != null) holeTopY = rr.holeTopY();
                    }

                    if (!water && rim == null) {
                        rejects.add(new SideHoleReject(x, z, bottomFeet, "covered"));
                    } else {
                        bestCost = cost;
                        bestDistSq = distSq;
                        best = new ColumnPlan(x, z, bottomFeet, exitY, water, cost, 0, 0, skyBlocked, holeTopY, rim,
                            true, new BlockPos(tx, cy, tz));
                    }
                }
            }
        }

        if (sideHoleLogged.add(site.center)) {
            List<SideHoleReject> loggable = new ArrayList<>();
            for (SideHoleReject r : rejects) {
                if (r.bottomFeet() <= cy + 12) loggable.add(r);
            }
            loggable.sort(Comparator.comparingDouble(r ->
                (double) (r.x() - cx) * (r.x() - cx) + (double) (r.z() - cz) * (r.z() - cz)));

            StringBuilder sb = new StringBuilder("side holes ");
            if (best != null) {
                sb.append(String.format("best=%d,%d bottom=%d cost=%d", best.x(), best.z(), best.startY(), best.blocksToBreak()));
            } else {
                sb.append("best=none");
            }
            sb.append(" rejected:");
            if (loggable.isEmpty()) {
                sb.append(" none");
            } else {
                int limit = Math.min(6, loggable.size());
                for (int i = 0; i < limit; i++) {
                    SideHoleReject r = loggable.get(i);
                    if (i > 0) sb.append(";");
                    sb.append(String.format(" %d,%d %s", r.x(), r.z(), r.reason()));
                }
            }
            autoLog("PLAN", site.center, sb.toString());
        }

        return best;
    }

    private static boolean footprintOverlaps(Room a, Room b) {
        boolean xOverlap = a.cx() - a.k() <= b.cx() + b.k() && b.cx() - b.k() <= a.cx() + a.k();
        boolean zOverlap = a.cz() - a.p() <= b.cz() + b.p() && b.cz() - b.p() <= a.cz() + a.p();
        return xOverlap && zOverlap;
    }

    private static boolean verticallyStacked(Room a, Room b) {
        return a.maxY() < b.minY() || b.maxY() < a.minY();
    }

    private boolean qualifiesAsStackedPartner(Site site) {
        if (!qualifiesForAuto(site)) return false;
        if (isVisited(site.center)) return false;
        if (site.modern) return false;
        if (site.room.cy() < 0) return false;
        if (!mc.world.getChunkManager().isChunkLoaded(site.center.getX() >> 4, site.center.getZ() >> 4)) return false;
        if (isRoomFlooded(site.room)) return false;
        return true;
    }

    private Site findStackedPartner(Site chosen) {
        Site best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Site site : sites.values()) {
            if (site.center.equals(chosen.center)) continue;
            if (!qualifiesAsStackedPartner(site)) continue;
            if (!footprintOverlaps(chosen.room, site.room)) continue;
            if (!verticallyStacked(chosen.room, site.room)) continue;

            double dx = site.center.getX() - chosen.center.getX();
            double dz = site.center.getZ() - chosen.center.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = site;
            }
        }

        return best;
    }

    private boolean columnClearInRoom(Room room, int x, int z) {
        if (!insideInterior(room, x, z)) return false;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = room.cy(); y <= room.cy() + 3; y++) {
            cursor.set(x, y, z);
            BlockState state = mc.world.getBlockState(cursor);
            if (state.getBlock() == Blocks.SPAWNER) return false;
            if (isContainerPresent(cursor)) return false;
            if (!isOpenState(state, cursor)) return false;
        }
        return true;
    }

    private ColumnPlan planSharedColumn(Site upper, Site lower) {
        Room ur = upper.room, lr = lower.room;
        int minX = Math.max(ur.cx() - ur.k(), lr.cx() - lr.k());
        int maxX = Math.min(ur.cx() + ur.k(), lr.cx() + lr.k());
        int minZ = Math.max(ur.cz() - ur.p(), lr.cz() - lr.p());
        int maxZ = Math.min(ur.cz() + ur.p(), lr.cz() + lr.p());
        if (minX > maxX || minZ > maxZ) return null;

        List<int[]> candidates = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                candidates.add(new int[]{x, z});
            }
        }
        candidates.sort(Comparator.comparingInt(c ->
            (c[0] - ur.cx()) * (c[0] - ur.cx()) + (c[1] - ur.cz()) * (c[1] - ur.cz())));

        for (int[] c : candidates) {
            int x = c[0], z = c[1];
            if (!columnClearInRoom(ur, x, z) || !columnClearInRoom(lr, x, z)) continue;

            ColumnPlan upperPlan = evaluateColumn(ur, x, z);
            if (upperPlan == null) continue;

            ColumnPlan lowerPlan = evaluateColumn(lr, x, z);
            if (lowerPlan == null || !sharedColumnPassesGates(lowerPlan)) continue;

            return upperPlan;
        }

        return null;
    }

    private boolean sharedColumnPassesGates(ColumnPlan shared) {
        if (shared.water() && countObsidian() < 1) return false;
        if (shared.waterCells() >= 3 && mc.player.getAttributeValue(EntityAttributes.OXYGEN_BONUS) < 1.0) return false;
        return true;
    }

    private boolean otherPlayerPresent() {
        if (!autoAvoidPlayers.get()) return false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (Friends.get().isFriend(p)) continue;
            return true;
        }
        return false;
    }

    private boolean hasPickaxe() {
        for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
            if (stack.isIn(ItemTags.PICKAXES)) return true;
        }
        return mc.player.getEquippedStack(EquipmentSlot.OFFHAND).isIn(ItemTags.PICKAXES);
    }

    private int countRockets() {
        int count = 0;
        for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
            if (stack.getItem() == Items.FIREWORK_ROCKET) count += stack.getCount();
        }
        ItemStack off = mc.player.getEquippedStack(EquipmentSlot.OFFHAND);
        if (off.getItem() == Items.FIREWORK_ROCKET) count += off.getCount();
        return count;
    }

    private int countObsidian() {
        int count = 0;
        for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
            if (stack.getItem() == Items.OBSIDIAN) count += stack.getCount();
        }
        ItemStack off = mc.player.getEquippedStack(EquipmentSlot.OFFHAND);
        if (off.getItem() == Items.OBSIDIAN) count += off.getCount();
        return count;
    }

    private boolean hasFreeSlotOrThrowaway() {
        int throwaway = 0;
        boolean freeSlot = false;
        for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
            if (stack.isEmpty()) {
                freeSlot = true;
                continue;
            }
            if (AUTO_THROWAWAY.contains(stack.getItem())) throwaway += stack.getCount();
        }
        return freeSlot || throwaway >= 64;
    }

    private boolean qualifiesForAuto(Site site) {
        if (site.roomContainers.isEmpty()) return false;
        if (site.score >= triggerScore.get()) return true;
        return autoFarmAll.get();
    }

    private QualResult evaluateQualification(Site site) {
        return evaluateQualification(site, false, false);
    }

    private QualResult evaluateQualification(Site site, boolean walkChain) {
        return evaluateQualification(site, walkChain, false);
    }

    private QualResult evaluateQualification(Site site, boolean walkChain, boolean underground) {
        if (isVisited(site.center)) return QualResult.reject("visited");
        if (site.room.cy() < 0) return QualResult.reject("below y 0");
        if (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE)) {
            return QualResult.reject("mining fatigue");
        }
        if (!mc.world.getChunkManager().isChunkLoaded(site.center.getX() >> 4, site.center.getZ() >> 4)) {
            return QualResult.reject("chunk not loaded");
        }
        if (site.modern) return QualResult.reject("not 1.12");

        double dx = site.center.getX() + 0.5 - mc.player.getX();
        double dz = site.center.getZ() + 0.5 - mc.player.getZ();
        double dist = Math.hypot(dx, dz);

        boolean gliding = mc.player.isGliding();
        if (underground) {
            if (dist > 32) return QualResult.reject("too far to chain");
            double dy = Math.abs(site.room.cy() - mc.player.getBlockPos().getY());
            if (dy > 32) return QualResult.reject("too far to chain (vertical)");
            if (isRoomFlooded(site.room)) return QualResult.reject("flooded room");
        } else if (walkChain) {
            if (dist > 64) return QualResult.reject("too far to chain");
        } else {
            if (dist > autoMaxDistance.get()) return QualResult.reject("too far");
            if (!gliding && dist > 96) return QualResult.reject("too far to walk");
        }

        if (nearOceanMonument(site.center)) return QualResult.reject("near ocean monument");

        AutoFlyingRegear regear = autoFlyingRegearModule();
        if (regear != null && regear.isRunning()) return QualResult.reject("AutoFlyingRegear running");
        WaypointFollower wf = waypointFollowerModule();
        if (wf != null && wf.isActive() && flightModule.get() != FlightModule.WaypointFollower) {
            return QualResult.reject("WaypointFollower active");
        }

        MlepMine mine = mlepMineModule();
        if (mine == null || !mine.isActive()) return QualResult.reject("MlepMine inactive");
        if (!BaritoneHelper.isAvailable()) return QualResult.reject("Baritone unavailable");

        if (otherPlayerPresent()) return QualResult.reject("player nearby");

        double health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (health < autoMinHealth.get()) return QualResult.reject("low health");

        if (countRockets() < autoMinRockets.get()) return QualResult.reject("not enough rockets");
        if (!hasPickaxe()) return QualResult.reject("no pickaxe");
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return QualResult.reject("elytra not worn");

        if (underground) return QualResult.ok(null);

        ColumnPlan plan = planColumn(site);
        if (plan == null) return QualResult.reject("no safe column");

        if (plan.water() && countObsidian() < 1) {
            return QualResult.reject("no obsidian for ocean exit");
        }

        if (plan.waterCells() >= 3 && mc.player.getAttributeValue(EntityAttributes.OXYGEN_BONUS) < 1.0) {
            return QualResult.reject("no respiration for water column");
        }

        if (!walkChain && gliding) {
            double H = mc.player.getY() - plan.exitY();
            if (H < 0.2 * dist + 20) return QualResult.reject("trop bas pour planer");
        }

        return QualResult.ok(plan);
    }

    private void tryStartAutoFarm() {
        if (state != AutoState.IDLE) return;
        if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

        Site best = null;
        ColumnPlan bestPlan = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Site site : sites.values()) {
            if (!qualifiesForAuto(site)) continue;

            QualResult qr = evaluateQualification(site);
            if (qr.reject() != null) {
                logSkipOnce(site.center, qr.reject());
                continue;
            }

            double dx = site.center.getX() + 0.5 - mc.player.getX();
            double dz = site.center.getZ() + 0.5 - mc.player.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = site;
                bestPlan = qr.plan();
            }
        }

        if (best == null) return;

        Site partner = findStackedPartner(best);
        if (partner != null) {
            Site upper = partner.room.cy() > best.room.cy() ? partner : best;
            Site lower = upper == best ? partner : best;
            ColumnPlan shared = planSharedColumn(upper, lower);
            if (shared != null && sharedColumnPassesGates(shared)) {
                autoStackedPartner = lower;
                autoLog("PLAN", upper.center, String.format(
                    "vertical partner paired with lower=%d,%d,%d column=%d,%d",
                    lower.center.getX(), lower.center.getY(), lower.center.getZ(), shared.x(), shared.z()));
                startAutoRun(upper, shared);
                return;
            }
        }

        startAutoRun(best, bestPlan);
    }

    private void startAutoRun(Site site, ColumnPlan plan) {
        autoSite = site;
        autoColumn = plan;
        autoRunAbandoned = false;
        autoRoomFoundShulkers = 0;
        autoRoomFoundItems = 0;

        if (!hasFreeSlotOrThrowaway()) {
            autoLog("PREPARE", site.center, "inventory full, Baritone may not pillar");
        }

        com.hunterbuddy.util.HuntFeed.get().publish(
            com.hunterbuddy.util.HuntFeed.Type.SPAWNER,
            "Farm · " + (site.hasSpawner ? site.mob : "no spawner") + " · " + site.containers + " chests", site.center);

        if (chatFeedback.get()) {
            info("Farm spawner at %d %d %d (%d containers)",
                site.center.getX(), site.center.getY(), site.center.getZ(), site.containers);
        }

        double dx = site.center.getX() + 0.5 - mc.player.getX();
        double dz = site.center.getZ() + 0.5 - mc.player.getZ();
        double D = Math.hypot(dx, dz);
        double H = mc.player.getY() - plan.exitY();

        String rimStr = plan.rim() != null
            ? String.format("%d,%d,%d", plan.rim().getX(), plan.rim().getY(), plan.rim().getZ())
            : "none";

        String details = String.format(
            "column=%d,%d startY=%d exitY=%d water=%s blocksToBreak=%d waterCells=%d maxGap=%d skyBlocked=%s rim=%s holeTopY=%d H=%.1f D=%.1f rockets=%d health=%.1f oxygenBonus=%.1f",
            plan.x(), plan.z(), plan.startY(), plan.exitY(), plan.water(), plan.blocksToBreak(), plan.waterCells(),
            plan.maxGap(), plan.skyBlocked(), rimStr, plan.holeTopY(), H, D, countRockets(),
            mc.player.getHealth() + mc.player.getAbsorptionAmount(),
            mc.player.getAttributeValue(EntityAttributes.OXYGEN_BONUS));

        enterState(AutoState.PREPARE, details);
    }

    private void autoTick() {
        if (state == AutoState.IDLE) return;

        autoHitThisTick = mc.player.hurtTime > 0 && mc.player.hurtTime > autoLastHurtTime;
        if (autoHitThisTick) {
            autoLastHurtTime = mc.player.hurtTime;
            autoLastHitMs = System.currentTimeMillis();
            if (state == AutoState.SHAFT_ALIGN || state == AutoState.SHAFT_FLY) {
                autoLog(state.name(), autoSite.center, String.format("hit health=%.1f", mc.player.getHealth()));
            }
        }
        if (mc.player.hurtTime < autoLastHurtTime) {
            autoLastHurtTime = mc.player.hurtTime;
        }

        try {
            AutoFlyingRegear regear = autoFlyingRegearModule();
            if (regear != null && regear.isRunning()) {
                boolean tellAreaLoader = autoAreaLoaderWasOn;
                autoLog("YIELD", autoSite != null ? autoSite.center : mc.player.getBlockPos(), "AutoFlyingRegear took over");
                releaseAllAutoKeys();
                if (takeoff.isActive()) takeoff.cancel();
                resetAutoState();
                if (tellAreaLoader) warning("AutoFlyingRegear started - restart AreaLoader by hand if it was running.");
                return;
            }

            switch (state) {
                case PREPARE -> tickPrepare();
                case APPROACH -> tickApproach();
                case FINAL -> tickFinal();
                case WALK -> tickWalk();
                case DIG -> tickDig();
                case SIDE_DIG -> tickSideDig();
                case ROOM -> tickRoom();
                case ROOM_CLIMB -> tickRoomClimb();
                case TUNNEL -> tickTunnel();
                case EXIT -> tickExit();
                case SHAFT_ALIGN -> tickShaftAlign();
                case SHAFT_FLY -> tickShaftFly();
                case SWIM_UP -> tickSwimUp();
                case WATER_JUMP -> tickWaterJump();
                case CLIMB -> tickClimb();
                case OBSIDIAN_PLATFORM -> tickObsidianPlatform();
                case ASCEND -> tickAscend();
                case TAKEOFF_SPOT -> tickTakeoffSpot();
                case TAKEOFF -> tickTakeoff();
                case RESUME -> tickResume();
                default -> {}
            }
        } catch (Exception e) {
            if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] exception state={} error={}", state, e.toString());
            try { releaseAllAutoKeys(); } catch (Exception ignored) {}
            try { BaritoneHelper.stopAllPathing(); } catch (Exception ignored) {}
            try { BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace; } catch (Exception ignored) {}
            try { if (takeoff.isActive()) takeoff.cancel(); } catch (Exception ignored) {}
            state = AutoState.FAILED;
            stateTicks = 0;
        }
    }

    private void tickPrepare() {
        stateTicks++;

        if (stateTicks == 1) {
            Module fm = selectedFlightModule();
            Pitch40 p40 = pitch40Module();
            ElytraRecast er = elytraRecastModule();
            boolean fmOn = fm != null && fm.isActive();
            autoAreaLoaderWasOn = fmOn || flightModule.get() == FlightModule.None;
            autoPitch40WasOn = p40 != null && p40.isActive();
            autoElytraRecastWasOn = er != null && er.isActive();
            if (fmOn) fm.toggle();
            return;
        }

        if (stateTicks == 2) {
            Pitch40 p40 = pitch40Module();
            if (p40 != null && p40.isActive()) p40.toggle();
            return;
        }

        if (stateTicks == 3) {
            ElytraRecast er = elytraRecastModule();
            if (er != null && er.isActive()) er.toggle();
        }

        autoSavedAllowPlace = BaritoneAPI.getSettings().allowPlace.value;

        if (mc.player.isGliding()) {
            enterState(AutoState.APPROACH, "");
        } else {
            enterState(AutoState.WALK, "");
        }
    }

    private void applyGlideRotation(float yaw, float pitch) {
        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();
        float yawDiff = MathHelper.clamp(MathHelper.wrapDegrees(yaw - curYaw), -12.0f, 12.0f);
        float pitchDiff = MathHelper.clamp(pitch - curPitch, -12.0f, 12.0f);
        mc.player.setYaw(curYaw + yawDiff);
        mc.player.setPitch(MathHelper.clamp(curPitch + pitchDiff, -90.0f, 90.0f));
    }

    private void logFlightTelemetry(String tag, double H, double D, double speedH, double vy) {
        if (!log.get() || stateTicks % 10 != 0) return;
        HunterBuddyAddon.LOG.info(
            "[SpawnerDetector/auto] {} site={},{},{} H={} D={} yaw={} pitch={} vy={} speedH={} gliding={} onGround={} health={}",
            tag, autoSite.center.getX(), autoSite.center.getY(), autoSite.center.getZ(),
            String.format("%.1f", H), String.format("%.1f", D), String.format("%.1f", mc.player.getYaw()),
            String.format("%.1f", mc.player.getPitch()), String.format("%.2f", vy), String.format("%.2f", speedH),
            mc.player.isGliding(), mc.player.isOnGround(),
            String.format("%.1f", mc.player.getHealth() + mc.player.getAbsorptionAmount()));
    }

    private void logFlightTelemetry(String tag, double H, double D, double speedH, double vy, double px, double py, double pz) {
        if (!log.get() || stateTicks % 10 != 0) return;
        HunterBuddyAddon.LOG.info(
            "[SpawnerDetector/auto] {} site={},{},{} H={} D={} yaw={} pitch={} vy={} speedH={} gliding={} onGround={} health={} pos={},{},{}",
            tag, autoSite.center.getX(), autoSite.center.getY(), autoSite.center.getZ(),
            String.format("%.1f", H), String.format("%.1f", D), String.format("%.1f", mc.player.getYaw()),
            String.format("%.1f", mc.player.getPitch()), String.format("%.2f", vy), String.format("%.2f", speedH),
            mc.player.isGliding(), mc.player.isOnGround(),
            String.format("%.1f", mc.player.getHealth() + mc.player.getAbsorptionAmount()),
            String.format("%.1f", px), String.format("%.1f", py), String.format("%.1f", pz));
    }

    private void logNavTelemetry(String tag, BlockPos goal, int retries) {
        if (!log.get() || stateTicks % 40 != 0) return;
        BlockPos pos = mc.player.getBlockPos();
        IBaritone bt = primaryBaritone();
        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
        HunterBuddyAddon.LOG.info(
            "[SpawnerDetector/auto] {} site={},{},{} pos={},{},{} pathing={} goal={},{},{} retries={}",
            tag, autoSite.center.getX(), autoSite.center.getY(), autoSite.center.getZ(),
            pos.getX(), pos.getY(), pos.getZ(), pathing,
            goal.getX(), goal.getY(), goal.getZ(), retries);
    }

    private String playerPosLabel() {
        BlockPos p = mc.player.getBlockPos();
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private boolean rayBlocked(Vec3d from, Vec3d to) {
        HitResult hit = mc.world.raycast(new RaycastContext(
            from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return hit != null && hit.getType() == HitResult.Type.BLOCK;
    }

    private boolean pathToPointBlocked(double px, double py, double pz, double tx, double ty, double tz) {
        double dx = tx - px, dz = tz - pz;
        double horiz = Math.hypot(dx, dz);
        if (horiz < 1.0e-3) return rayBlocked(new Vec3d(px, py, pz), new Vec3d(tx, ty, tz));

        double offX = -dz / horiz * 0.3;
        double offZ = dx / horiz * 0.3;

        if (rayBlocked(new Vec3d(px, py, pz), new Vec3d(tx, ty, tz))) return true;
        if (rayBlocked(new Vec3d(px + offX, py, pz + offZ), new Vec3d(tx + offX, ty, tz + offZ))) return true;
        if (rayBlocked(new Vec3d(px - offX, py, pz - offZ), new Vec3d(tx - offX, ty, tz - offZ))) return true;
        return false;
    }

    private boolean terrainCloseAhead(double px, double py, double pz, float yaw) {
        double rad = Math.toRadians(yaw);
        double dirX = -Math.sin(rad), dirZ = Math.cos(rad);
        for (int d = 4; d <= 20; d += 4) {
            int sx = (int) Math.floor(px + dirX * d);
            int sz = (int) Math.floor(pz + dirZ * d);
            int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, sx, sz);
            if (py - top < 8) return true;
        }
        return false;
    }

    private boolean isWaterLandingZone() {
        ColumnPlan plan = autoColumn;
        if (plan == null || !plan.water()) return false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = plan.x() + dx, z = plan.z() + dz;
                if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return false;

                int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                BlockState top = mc.world.getBlockState(new BlockPos(x, topY - 1, z));
                if (!top.getFluidState().isIn(FluidTags.WATER)) return false;
            }
        }

        return true;
    }

    private boolean playerOverWater() {
        int x = MathHelper.floor(mc.player.getX());
        int z = MathHelper.floor(mc.player.getZ());
        if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return false;

        int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        return mc.world.getBlockState(new BlockPos(x, topY - 1, z)).getFluidState().isIn(FluidTags.WATER);
    }

    private double groundHeightBelowPlayer() {
        int x = MathHelper.floor(mc.player.getX());
        int z = MathHelper.floor(mc.player.getZ());
        if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return 0.0;

        int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        return mc.player.getY() - topY;
    }

    private void tickApproach() {
        stateTicks++;

        if (mc.player.isOnGround() || mc.player.isTouchingWater() || !mc.player.isGliding()) {
            enterState(AutoState.WALK, "landed");
            return;
        }

        if (stateTicks > 90 * 20) {
            abort("approach timeout");
            return;
        }

        ColumnPlan plan = autoColumn;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        double dx = (plan.x() + 0.5) - px, dz = (plan.z() + 0.5) - pz;
        double D = Math.hypot(dx, dz);
        double H = py - plan.exitY();
        Vec3d vel = mc.player.getVelocity();
        double speed = vel.length();
        double speedH = Math.hypot(vel.x, vel.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        if (D > 28) {
            double raw = Math.toDegrees(Math.atan2(H - autoFlareHeight.get(), D - 28));
            float pitch = (float) MathHelper.clamp(raw, -5.0, 45.0);
            if (speed > 2.2) pitch -= 15.0f;
            if (terrainCloseAhead(px, py, pz, yaw)) pitch -= 10.0f;
            if (speedH < 0.3) pitch = 30.0f;
            applyGlideRotation(yaw, pitch);
            logFlightTelemetry("APPROACH", H, D, speedH, vel.y);
        } else if (H > autoFlareHeight.get() + 10) {
            double angleToCenter = Math.toDegrees(Math.atan2(-dx, dz));
            double correction = MathHelper.clamp((D - 24) * 2, -30.0, 30.0);
            float heading = (float) MathHelper.wrapDegrees(angleToCenter + 90.0 + correction);
            float pitch = 35.0f;
            if (speed > 2.2) pitch -= 15.0f;
            applyGlideRotation(heading, pitch);
            logFlightTelemetry("SPIRAL", H, D, speedH, vel.y);
            if (H <= autoFlareHeight.get() + 5) {
                autoHealthBeforeLanding = mc.player.getHealth();
                enterState(AutoState.FINAL, String.format("H=%.1f D=%.1f", H, D));
            }
        } else if (isWaterLandingZone()) {
            autoHealthBeforeLanding = mc.player.getHealth();
            enterState(AutoState.FINAL, String.format("H=%.1f D=%.1f water", H, D));
        } else {
            autoHealthBeforeLanding = mc.player.getHealth();
            enterState(AutoState.FINAL, String.format("H=%.1f D=%.1f", H, D));
        }
    }

    private void tickFinal() {
        stateTicks++;

        if (mc.player.isInLava()) { abort("in lava"); return; }

        if (mc.player.isOnGround() || mc.player.isTouchingWater()) {
            double after = mc.player.getHealth();
            autoLog("FINAL", autoSite.center,
                String.format("landed health %.1f -> %.1f", autoHealthBeforeLanding, after));
            enterState(AutoState.WALK, "touched down");
            return;
        }

        if (stateTicks > 40 * 20) {
            abort("final timeout");
            return;
        }

        if (stateTicks == 1) {
            autoFinalBlockedLogged = false;
            autoFinalDiving = false;
            autoFinalLastRocketTick = -100;
        }

        if (!autoColumn.water()) {
            tickFinalGround();
            return;
        }

        ColumnPlan plan = autoColumn;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        double dx = (plan.x() + 0.5) - px;
        double dz = (plan.z() + 0.5) - pz;
        double D = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        Vec3d vel = mc.player.getVelocity();
        double speedH = Math.hypot(vel.x, vel.z);
        double targetY = plan.exitY();
        double H = py - targetY;

        boolean blocked = pathToPointBlocked(px, py, pz, plan.x() + 0.5, targetY + 1.0, plan.z() + 0.5);

        if (blocked) {
            if (!autoFinalBlockedLogged) {
                autoFinalBlockedLogged = true;
                autoLog("FINAL", autoSite.center, String.format("path blocked H=%.1f D=%.1f", H, D));
            }

            if (D <= 28 && H > autoFlareHeight.get() + 10) {
                enterState(AutoState.APPROACH, String.format("final blocked, spiraling H=%.1f D=%.1f", H, D));
                return;
            }

            applyGlideRotation(yaw, -45.0f);
            if (!ownRocketActive() && stateTicks - autoFinalLastRocketTick >= 20 && fireShaftRocket()) {
                autoFinalLastRocketTick = stateTicks;
            }
            logFlightTelemetry("FINAL", H, D, speedH, vel.y, px, py, pz);
            return;
        }

        autoFinalBlockedLogged = false;

        if (!autoFinalDiving) {
            autoFinalDiving = true;
            autoLog("FINAL", autoSite.center, String.format("diving into water H=%.1f D=%.1f", H, D));
        }
        float divePitch = (float) MathHelper.clamp(Math.toDegrees(Math.atan2(H, D)), 0.0, 85.0);
        applyGlideRotation(yaw, divePitch);
        logFlightTelemetry("FINAL", H, D, speedH, vel.y, px, py, pz);
    }

    private void tickFinalGround() {
        ColumnPlan plan = autoColumn;
        double dx = (plan.x() + 0.5) - mc.player.getX();
        double dz = (plan.z() + 0.5) - mc.player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        Vec3d vel = mc.player.getVelocity();
        double groundHeight = groundHeightBelowPlayer();
        boolean waterMode = isWaterLandingZone() && playerOverWater() && groundHeight > 20.0;

        double targetVy;
        float pitchMin, pitchMax;
        String mode;

        if (waterMode) {
            targetVy = -1.2;
            pitchMin = -35.0f;
            pitchMax = 60.0f;
            mode = "water";
        } else if (groundHeight > 20.0) {
            targetVy = -1.0;
            pitchMin = -35.0f;
            pitchMax = 45.0f;
            mode = "ground-fast";
        } else {
            targetVy = -0.35;
            pitchMin = -35.0f;
            pitchMax = 25.0f;
            mode = "ground";
        }

        double err = vel.y - targetVy;
        float pitch = mc.player.getPitch() + (float) MathHelper.clamp(err * 40.0, -6.0, 6.0);
        pitch = MathHelper.clamp(pitch, pitchMin, pitchMax);
        applyGlideRotation(yaw, pitch);

        double D = Math.hypot(dx, dz);
        double H = mc.player.getY() - plan.exitY();
        logFlightTelemetry("FINAL", H, D, Math.hypot(vel.x, vel.z), vel.y);
        if (log.get() && stateTicks % 10 == 0) {
            HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] FINAL mode={} groundHeight={}",
                mode, String.format("%.1f", groundHeight));
        }
    }

    private boolean centerOnColumn(int x, int z, double eps) {
        double dx = (x + 0.5) - mc.player.getX();
        double dz = (z + 0.5) - mc.player.getZ();
        double horiz = Math.hypot(dx, dz);
        if (horiz < eps) {
            Utils.setPressed(mc.options.forwardKey, false);
            return true;
        }
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yawDiff = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        if (Math.abs(yawDiff) > 5.0f) mc.player.setYaw(mc.player.getYaw() + Math.signum(yawDiff) * 5.0f);
        Utils.setPressed(mc.options.forwardKey, Math.abs(yawDiff) < 30.0f);
        return false;
    }

    private void startWalkBaritoneGoal() {
        autoBaritoneRetries = 0;
        IBaritone bt = primaryBaritone();
        if (bt != null) {
            BlockPos goal = autoColumn.rim() != null
                ? autoColumn.rim()
                : new BlockPos(autoColumn.x(), autoColumn.startY(), autoColumn.z());
            bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
        }
    }

    private void tickWalk() {
        stateTicks++;

        if (mc.player.isInLava()) { abort("in lava"); return; }

        if (stateTicks == 1) {
            autoWalkDirect = autoColumn.water() || mc.player.isTouchingWater();
            autoWalkStepping = false;
            autoWalkDirectDryTicks = 0;
            autoWalkDirectStuckTicks = 0;
            autoWalkKeyLogged = false;
            if (autoWalkDirect) {
                autoLog("WALK", autoSite.center, "water direct");
            } else {
                startWalkBaritoneGoal();
            }
        }

        if (stateTicks > 60 * 20) {
            abort("walk timeout");
            return;
        }

        if (autoWalkDirect && !mc.player.isTouchingWater() && mc.player.isOnGround()) {
            autoWalkDirectDryTicks++;
        } else {
            autoWalkDirectDryTicks = 0;
        }

        if (autoWalkDirect && !autoColumn.water() && mc.player.horizontalCollision && mc.player.isTouchingWater()) {
            autoWalkDirectStuckTicks++;
        } else {
            autoWalkDirectStuckTicks = 0;
        }

        if (autoWalkDirect && (autoWalkDirectDryTicks >= 20 || autoWalkDirectStuckTicks >= 60)) {
            String reason = autoWalkDirectDryTicks >= 20 ? "not in water" : "stuck against a block";
            autoWalkDirect = false;
            releaseAllAutoKeys();
            ColumnPlan c = autoColumn;
            if (c.water() || c.rim() == null) {
                RimResult rr = computeRim(c.x(), c.z(), c.startY());
                autoColumn = new ColumnPlan(c.x(), c.z(), c.startY(), c.exitY(), false, c.blocksToBreak(),
                    c.waterCells(), c.maxGap(), c.skyBlocked(), rr.rim() != null ? rr.holeTopY() : c.holeTopY(),
                    rr.rim() != null ? rr.rim() : c.rim(), c.side(), c.sideEntry());
            }
            BlockPos rim = autoColumn.rim();
            autoLog("WALK", autoSite.center, reason + ", walking with baritone rim="
                + (rim != null ? rim.getX() + "," + rim.getY() + "," + rim.getZ() : "none"));
            startWalkBaritoneGoal();
        }

        if (autoWalkDirect) {
            tickWalkDirect();
        } else {
            tickWalkBaritone();
        }
    }

    private void tickWalkDirect() {
        double dx = (autoColumn.x() + 0.5) - mc.player.getX();
        double dz = (autoColumn.z() + 0.5) - mc.player.getZ();
        double horiz = Math.hypot(dx, dz);

        boolean centered = recenterDig(autoColumn.x(), autoColumn.z(), 0.12);
        String phase;

        if (horiz > 0.5) {
            if (autoColumn.water()) {
                phase = "swim-descend";
                BlockPos feet = mc.player.getBlockPos();
                BlockPos below1 = feet.down();
                BlockPos below2 = feet.down(2);
                boolean onFloor = !isOpenState(mc.world.getBlockState(below1), below1);
                boolean nearFloor = onFloor || !isOpenState(mc.world.getBlockState(below2), below2);
                boolean horizCollision = mc.player.horizontalCollision;
                Utils.setPressed(mc.options.jumpKey, horizCollision || onFloor);
                Utils.setPressed(mc.options.sneakKey, !horizCollision && !nearFloor);
            } else {
                phase = "swim";
                boolean headUnderwater = mc.player.isSubmergedInWater();
                boolean holdJump = mc.player.horizontalCollision || headUnderwater;
                Utils.setPressed(mc.options.jumpKey, holdJump);
                Utils.setPressed(mc.options.sneakKey, false);
            }
        } else {
            phase = "sink";
            Utils.setPressed(mc.options.jumpKey, false);
            Utils.setPressed(mc.options.sneakKey, horiz <= 0.15);
        }

        int feetY = mc.player.getBlockPos().getY();
        boolean feetOk = Math.abs(feetY - autoColumn.startY()) <= 1
            || feetY <= autoSite.room.cy() + 3;
        if (mc.player.isOnGround() && feetOk && centered) {
            releaseAllAutoKeys();
            enterDigState("");
            return;
        }

        logNavTelemetry("WALK " + phase, new BlockPos(autoColumn.x(), autoColumn.startY(), autoColumn.z()), 0);
    }

    private void tickWalkBaritone() {
        double dx = (autoColumn.x() + 0.5) - mc.player.getX();
        double dz = (autoColumn.z() + 0.5) - mc.player.getZ();
        double horiz = Math.hypot(dx, dz);
        boolean feetOk = mc.player.getBlockPos().getY() == autoColumn.startY();

        IBaritone bt = primaryBaritone();

        if (horiz <= 0.5 && feetOk) {
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            if (centerOnColumn(autoColumn.x(), autoColumn.z(), 0.2)) {
                releaseAllAutoKeys();
                enterDigState("");
            }
            return;
        }

        BlockPos rim = autoColumn.rim();

        if (autoWalkStepping) {
            Utils.setPressed(mc.options.sneakKey, false);
            recenterDig(autoColumn.x(), autoColumn.z(), 0.15);

            int feetY = mc.player.getBlockPos().getY();
            if (mc.player.isOnGround() && feetY < rim.getY()) {
                if (centerOnColumn(autoColumn.x(), autoColumn.z(), 0.2)) {
                    releaseAllAutoKeys();
                    enterDigState("landed in hole y=" + feetY);
                }
            }
            return;
        }

        if (rim != null) {
            double rdx = (rim.getX() + 0.5) - mc.player.getX();
            double rdz = (rim.getZ() + 0.5) - mc.player.getZ();
            double rHoriz = Math.hypot(rdx, rdz);
            if (mc.player.isOnGround() && mc.player.getBlockPos().getY() == rim.getY() && rHoriz <= 0.5) {
                if (bt != null) bt.getPathingBehavior().cancelEverything();
                releaseAllAutoKeys();
                autoWalkStepping = true;
                autoLog("WALK", autoSite.center, String.format("step into hole from rim %d,%d,%d", rim.getX(), rim.getY(), rim.getZ()));
                return;
            }
        }

        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
        if (!pathing && stateTicks > 20 && autoBaritoneRetries < 3) {
            autoBaritoneRetries++;
            if (bt != null) {
                BlockPos goal = rim != null ? rim : new BlockPos(autoColumn.x(), autoColumn.startY(), autoColumn.z());
                bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
            }
        } else if (!pathing && autoBaritoneRetries >= 3) {
            int keyX = rim != null ? rim.getX() : autoColumn.x();
            int keyZ = rim != null ? rim.getZ() : autoColumn.z();
            if (!autoWalkKeyLogged) {
                autoWalkKeyLogged = true;
                autoLog("WALK", autoSite.center, String.format("baritone gave up, walking to %d,%d", keyX, keyZ));
            }
            Utils.setPressed(mc.options.sneakKey, false);
            recenterDig(keyX, keyZ, 0.2);
            Utils.setPressed(mc.options.jumpKey, mc.player.isOnGround() && mc.player.horizontalCollision);
        }

        logNavTelemetry("WALK", new BlockPos(autoColumn.x(), autoColumn.startY(), autoColumn.z()), autoBaritoneRetries);
    }

    private void recenterDig(int x, int z) {
        recenterDig(x, z, 0.25);
    }

    private boolean recenterDig(int x, int z, double eps) {
        double dx = (x + 0.5) - mc.player.getX();
        double dz = (z + 0.5) - mc.player.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist <= eps) {
            Utils.setPressed(mc.options.forwardKey, false);
            return true;
        }
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mc.player.setYaw(targetYaw);
        Utils.setPressed(mc.options.forwardKey, true);
        return false;
    }

    private boolean navigateExitColumn(int columnX, int columnZ, double columnEps, BlockPos entry, boolean holdJumpAlways) {
        BlockPos pos = mc.player.getBlockPos();

        if (entry != null && insideInterior(autoSite.room, pos.getX(), pos.getZ())
            && (pos.getX() != entry.getX() || pos.getZ() != entry.getZ())) {
            Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater());
            recenterDig(entry.getX(), entry.getZ(), 0.3);
            Utils.setPressed(mc.options.jumpKey, mc.player.isOnGround() && mc.player.horizontalCollision);
            return false;
        }

        if (entry != null && (pos.getX() != columnX || pos.getZ() != columnZ)) {
            Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater());
            recenterDig(columnX, columnZ, columnEps);
            Utils.setPressed(mc.options.jumpKey, mc.player.isOnGround() && mc.player.horizontalCollision);
            return false;
        }

        Utils.setPressed(mc.options.sneakKey, false);
        boolean centered = recenterDig(columnX, columnZ, columnEps);
        Utils.setPressed(mc.options.jumpKey, holdJumpAlways || mc.player.horizontalCollision);
        return centered;
    }

    private void shiftAutoPauseTimers(long pausedMs) {
        if (pausedMs <= 0) return;
        switch (state) {
            case DIG -> {
                autoDigTotalStartMs += pausedMs;
                autoDigBlockStartMs += pausedMs;
                if (autoDigDisplacedSinceMs != 0) autoDigDisplacedSinceMs += pausedMs;
            }
            case ROOM -> {
                autoRoomSpawnerStartMs += pausedMs;
                autoRoomSubStateStartMs += pausedMs;
                autoRoomBreakStartMs += pausedMs;
                if (autoRoomDebrisStartMs != 0) autoRoomDebrisStartMs += pausedMs;
            }
            case ROOM_CLIMB -> {
                autoRoomClimbStartMs += pausedMs;
                if (autoRoomClimbBaritoneTryStartMs != 0) autoRoomClimbBaritoneTryStartMs += pausedMs;
            }
            default -> {}
        }
    }

    private void endAutoPause() {
        shiftAutoPauseTimers(System.currentTimeMillis() - autoPauseStartMs);
        autoLog(state.name(), autoSite.center, "resumed");
        Utils.setPressed(mc.options.sneakKey, autoPauseOnSpawner());
        autoPaused = false;
        autoPauseReason = null;
        autoPauseStartMs = 0;
    }

    private boolean autoPauseOnSpawner() {
        if (state != AutoState.ROOM || autoRoomSpawnerPos == null) return false;
        if (mc.world.getBlockState(autoRoomSpawnerPos).getBlock() != Blocks.SPAWNER) return false;
        if (mc.player.getBlockPos().getY() != autoRoomSpawnerPos.getY() + 1) return false;
        double dx = mc.player.getX() - (autoRoomSpawnerPos.getX() + 0.5);
        double dz = mc.player.getZ() - (autoRoomSpawnerPos.getZ() + 0.5);
        return Math.abs(dx) <= 0.8 && Math.abs(dz) <= 0.8;
    }

    private boolean handleAutoPause(boolean combatEligible) {
        KillAuraPlus killAura = combatEligible ? killAuraPlusModule() : null;
        boolean inCombat = killAura != null && killAura.hasActiveTarget();

        if (!inCombat) {
            if (autoPaused) endAutoPause();
            return false;
        }

        String reason = "combat";
        long now = System.currentTimeMillis();

        if (!autoPaused) {
            autoPaused = true;
            autoPauseReason = reason;
            autoPauseStartMs = now;
            autoLog(state.name(), autoSite.center, "paused: " + reason);
        } else if (!reason.equals(autoPauseReason)) {
            endAutoPause();
            autoPaused = true;
            autoPauseReason = reason;
            autoPauseStartMs = now;
            autoLog(state.name(), autoSite.center, "paused: " + reason);
        } else if (now - autoPauseStartMs > 60_000L) {
            abort("combat too long");
            return true;
        }

        Utils.setPressed(mc.options.forwardKey, false);
        Utils.setPressed(mc.options.backKey, false);
        Utils.setPressed(mc.options.leftKey, false);
        Utils.setPressed(mc.options.rightKey, false);
        Utils.setPressed(mc.options.jumpKey, false);
        Utils.setPressed(mc.options.sprintKey, false);
        Utils.setPressed(mc.options.attackKey, false);
        Utils.setPressed(mc.options.sneakKey, autoPauseOnSpawner());
        autoPausedTicks++;
        return true;
    }

    private boolean insideRoomBounds(double x, double y, double z) {
        Room room = autoSite.room;
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        return bx >= room.minX() - 1 && bx <= room.maxX() + 1
            && bz >= room.minZ() - 1 && bz <= room.maxZ() + 1
            && y >= room.minY() - 1 && y <= room.maxY() + 1;
    }

    private boolean shaftSkeletonChaseable(Entity target) {
        return target instanceof SkeletonEntity
            && insideRoomBounds(target.getX(), target.getY(), target.getZ())
            && insideRoomBounds(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private void moveTowardShaftTarget(Entity target) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double distXZ = Math.hypot(dx, dz);
        float yaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setYaw(yaw);
        Utils.setPressed(mc.options.sprintKey, false);
        Utils.setPressed(mc.options.forwardKey, distXZ > 2.5);
        Utils.setPressed(mc.options.backKey, false);
        Utils.setPressed(mc.options.leftKey, false);
        Utils.setPressed(mc.options.rightKey, false);
        Utils.setPressed(mc.options.jumpKey, mc.player.isOnGround() && mc.player.horizontalCollision);
        Utils.setPressed(mc.options.attackKey, false);
        Utils.setPressed(mc.options.sneakKey, false);
    }

    private boolean handleShaftCombat() {
        boolean inCombat = autoLastHitMs != 0 && (System.currentTimeMillis() - autoLastHitMs) < 3000L;

        if (!inCombat) {
            if (autoPaused) endAutoPause();
            return false;
        }

        String reason = "combat";
        long now = System.currentTimeMillis();

        if (!autoPaused) {
            autoPaused = true;
            autoPauseReason = reason;
            autoPauseStartMs = now;
            autoLog(state.name(), autoSite.center, "paused: " + reason);
        } else if (!reason.equals(autoPauseReason)) {
            endAutoPause();
            autoPaused = true;
            autoPauseReason = reason;
            autoPauseStartMs = now;
            autoLog(state.name(), autoSite.center, "paused: " + reason);
        } else if (now - autoPauseStartMs > 60_000L) {
            abort("combat too long");
            return true;
        }

        Entity target = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof SkeletonEntity) || !entity.isAlive()) continue;
            if (!shaftSkeletonChaseable(entity)) continue;
            double distSq = entity.squaredDistanceTo(mc.player);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                target = entity;
            }
        }

        if (target != null) {
            if (target != autoShaftChaseTarget) {
                autoShaftChaseTarget = target;
                autoLog(state.name(), autoSite.center, String.format("chase id=%d dist=%.1f", target.getId(), Math.sqrt(bestDistSq)));
            }
            moveTowardShaftTarget(target);
        } else {
            autoShaftChaseTarget = null;
            releaseAllAutoKeys();
        }
        autoPausedTicks++;
        return true;
    }

    private void aimAtBlockCenter(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d target = Vec3d.ofCenter(pos);
        double diffX = target.x - eye.x;
        double diffY = target.y - eye.y;
        double diffZ = target.z - eye.z;
        float yaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0));
        float pitch = MathHelper.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, Math.hypot(diffX, diffZ))));
        mc.player.setYaw(yaw);
        mc.player.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
    }

    private Direction directionTowardsPlayer(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        double dx = eye.x - center.x;
        double dy = eye.y - center.y;
        double dz = eye.z - center.z;

        if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > Math.abs(dz)) {
            return dx > 0.0 ? Direction.EAST : Direction.WEST;
        } else if (Math.abs(dz) > Math.abs(dy)) {
            return dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            return dy > 0.0 ? Direction.UP : Direction.DOWN;
        }
    }

    private static boolean isMiningTool(ItemStack stack) {
        return stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS)
            || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.HOES);
    }

    private boolean holdMiningTool(String stateLabel, BlockState state) {
        if (isMiningTool(mc.player.getMainHandStack())) return true;

        int best = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isMiningTool(stack)) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        if (best < 0) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isIn(ItemTags.PICKAXES)) {
                    best = i;
                    break;
                }
            }
        }
        if (best < 0) return true;

        String was = Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath();
        InvUtils.swap(best, false);
        autoLog(stateLabel, autoSite.center, "tool back in hand slot=" + best + " was=" + was);
        return false;
    }

    private boolean autoMineTick(String stateLabel, BlockPos pos, boolean feetBelow) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof FluidBlock) {
            return true;
        }

        AutoEatSync eat = autoEatSyncModule();
        if (eat != null && eat.isEating()) {
            return false;
        }

        if (!holdMiningTool(stateLabel, state)) {
            return false;
        }

        if (feetBelow) {
            mc.player.setPitch(90.0f);
        } else {
            aimAtBlockCenter(pos);
        }

        Direction face = feetBelow ? Direction.UP : directionTowardsPlayer(pos);
        mc.interactionManager.updateBlockBreakingProgress(pos, face);
        mc.player.swingHand(Hand.MAIN_HAND);
        return false;
    }

    private void tickDig() {
        stateTicks++;

        if (stateTicks == 1) {
            ColumnPlan replanned = evaluateColumn(autoSite.room, autoColumn.x(), autoColumn.z());
            if (replanned == null) {
                autoLog("DIG", autoSite.center, "replan refused, keeping plan");
            } else {
                autoColumn = replanned;
            }
            autoDigTotalStartMs = System.currentTimeMillis();
            autoDigBlockStartMs = autoDigTotalStartMs;
            autoDigBlocksPlanned = Math.max(1, autoColumn.blocksToBreak());
            autoDigQueuedPos = null;
            autoDigQueuedBlockLabel = null;
            autoDigBlockTicks = 0;
            autoDigDisplacedSinceMs = 0;
            autoPaused = false;
            autoPauseReason = null;
            autoPauseStartMs = 0;
            autoPausedTicks = 0;
        }

        if (mc.player.isInLava()) { abort("lava"); return; }
        if (handleAutoPause(true)) return;
        if (otherPlayerPresent()) { abort("player appeared"); return; }
        boolean underwater = mc.player.isSubmergedInWater();

        boolean slowInWater = underwater && mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED) < 0.99;
        int mult = slowInWater ? 5 : 1;
        long perBlockTimeout = 15_000L * mult;
        long totalTimeout = Math.max(120_000L, 4_000L * autoDigBlocksPlanned) * mult;
        if (System.currentTimeMillis() - autoDigTotalStartMs > totalTimeout) { abort("dig timeout"); return; }

        recenterDig(autoColumn.x(), autoColumn.z(), mc.player.isTouchingWater() ? 0.12 : 0.25);

        int feetY = mc.player.getBlockPos().getY();
        int roomCy = autoSite.room.cy();
        BlockPos below = new BlockPos(autoColumn.x(), feetY - 1, autoColumn.z());
        BlockState belowState = mc.world.getBlockState(below);

        boolean groundArrival = mc.player.isOnGround()
            && (feetY <= roomCy || !(belowState.getBlock() instanceof net.minecraft.block.FallingBlock));

        if (below.getY() < roomCy - 1
            || (feetY <= roomCy + 3 && (groundArrival || isOpenState(belowState, below)))) {
            releaseAllAutoKeys();
            boolean water = autoColumn.water() || autoColumn.waterCells() > 0
                || columnHasWater(autoColumn.x(), autoColumn.z(), roomCy, autoColumn.holeTopY());
            autoExitShaft = new ExitShaft(autoColumn.x(), autoColumn.z(), roomCy,
                autoColumn.holeTopY(), water, autoColumn.skyBlocked(), null);
            enterState(AutoState.ROOM, "");
            return;
        }

        double gap = Math.hypot((autoColumn.x() + 0.5) - mc.player.getX(), (autoColumn.z() + 0.5) - mc.player.getZ());

        if (gap > 0.6) {
            Utils.setPressed(mc.options.attackKey, false);
            if (autoDigDisplacedSinceMs == 0) {
                autoDigDisplacedSinceMs = System.currentTimeMillis();
                autoLog("DIG", autoSite.center, String.format("displaced gap=%.2f", gap));
            } else if (System.currentTimeMillis() - autoDigDisplacedSinceMs > 10_000L) {
                abort("displaced from the column");
            }
            return;
        }
        autoDigDisplacedSinceMs = 0;

        if (isLavaNearShaft(below, mc.player.getBlockPos())) {
            abort("lava next to the shaft");
            return;
        }

        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater() && gap <= 0.15);

        if (belowState.getFluidState().isIn(FluidTags.WATER)) {
            handleDigBlockCleared(below, gap);
            return;
        }

        if (isOpenState(belowState, below)) {
            handleDigBlockCleared(below, gap);
            return;
        }

        if (autoEatSyncModule() != null && autoEatSyncModule().isEating()) {
            Utils.setPressed(mc.options.attackKey, false);
            return;
        }

        if (!below.equals(autoDigQueuedPos)) {
            if (!mc.player.isOnGround()) return;
            autoDigQueuedPos = below;
            autoDigQueuedBlockLabel = Registries.BLOCK.getId(belowState.getBlock()).getPath();
            autoDigBlockStartMs = System.currentTimeMillis();
            autoDigBlockTicks = 0;
        }

        autoDigBlockTicks++;
        autoMineTick("DIG", below, true);

        if (System.currentTimeMillis() - autoDigBlockStartMs > perBlockTimeout) {
            abort("dig block timeout");
        }
    }

    private void handleDigBlockCleared(BlockPos below, double gap) {
        Utils.setPressed(mc.options.attackKey, false);
        if (autoDigQueuedPos != null) {
            logDigBreak(below, autoDigQueuedBlockLabel, gap);
        }
        autoDigQueuedPos = null;
        autoDigBlockTicks = 0;
    }

    private void logDigBreak(BlockPos pos, String label, double gap) {
        autoLog("DIG", autoSite.center, String.format(
            "broken %d,%d,%d block=%s ticks=%d gap=%.2f onGround=%b submerged=%b submergedSpeed=%.2f",
            pos.getX(), pos.getY(), pos.getZ(), label, autoDigBlockTicks, gap,
            mc.player.isOnGround(), mc.player.isSubmergedInWater(),
            mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED)));
    }

    private boolean isContainerPresent(BlockPos pos) {
        Block b = mc.world.getBlockState(pos).getBlock();
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL || b instanceof ShulkerBoxBlock;
    }

    private void tickSideDig() {
        stateTicks++;

        if (stateTicks == 1) {
            autoSideDigBlockPos = null;
            autoSideDigLastSlowLogMs = 0;
        }

        if (mc.player.isInLava()) { abort("lava"); return; }
        if (handleAutoPause(true)) return;
        if (otherPlayerPresent()) { abort("player appeared"); return; }

        AutoEatSync eat = autoEatSyncModule();
        if (eat != null && eat.isEating()) return;

        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater());

        ColumnPlan col = autoColumn;
        Room room = autoSite.room;
        int cy = room.cy();
        BlockPos feet = mc.player.getBlockPos();
        int feetY = feet.getY();

        if (insideInterior(room, feet.getX(), feet.getZ()) && feetY >= cy && feetY <= cy + 3) {
            releaseAllAutoKeys();
            boolean water = col.water() || columnHasWater(col.x(), col.z(), cy, col.holeTopY());
            autoExitShaft = new ExitShaft(col.x(), col.z(), cy, col.holeTopY(), water, col.skyBlocked(), col.sideEntry());
            enterState(AutoState.ROOM, "side tunnel done");
            return;
        }

        String phase;
        BlockPos targetCell;

        if (feetY > cy) {
            phase = "descend";
            recenterDig(col.x(), col.z(), mc.player.isTouchingWater() ? 0.12 : 0.25);
            double gap = Math.hypot((col.x() + 0.5) - mc.player.getX(), (col.z() + 0.5) - mc.player.getZ());
            Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater() && gap <= 0.15);
            BlockPos below = new BlockPos(col.x(), feetY - 1, col.z());
            targetCell = below;
            BlockState belowState = mc.world.getBlockState(below);
            if (!isOpenState(belowState, below) && mc.player.isOnGround()) {
                trackSideDigBlock(below);
                autoMineTick("SIDE_DIG", below, true);
            }
        } else if (feetY == cy) {
            phase = "horizontal";
            BlockPos t = col.sideEntry();
            int nx = feet.getX();
            int nz = feet.getZ();
            if (feet.getX() != t.getX()) nx += Integer.signum(t.getX() - feet.getX());
            else nz += Integer.signum(t.getZ() - feet.getZ());
            BlockPos nHead = new BlockPos(nx, cy + 1, nz);
            BlockPos nFloor = new BlockPos(nx, cy, nz);
            targetCell = nFloor;

            BlockState headState = mc.world.getBlockState(nHead);
            if (headState.getBlock() == Blocks.SPAWNER || isContainerPresent(nHead)
                || mc.world.getBlockState(nFloor).getBlock() == Blocks.SPAWNER || isContainerPresent(nFloor)) {
                abort("side tunnel blocked by container");
                return;
            }
            if (!isOpenState(headState, nHead)) {
                trackSideDigBlock(nHead);
                autoMineTick("SIDE_DIG", nHead, false);
            } else {
                BlockState floorState = mc.world.getBlockState(nFloor);
                if (!isOpenState(floorState, nFloor)) {
                    trackSideDigBlock(nFloor);
                    autoMineTick("SIDE_DIG", nFloor, false);
                } else {
                    recenterDig(nx, nz, 0.2);
                }
            }
        } else {
            enterState(AutoState.ROOM_CLIMB, "fell below side tunnel");
            return;
        }

        if (log.get() && stateTicks % 40 == 0) {
            autoLog("SIDE_DIG", autoSite.center, String.format(
                "pos=%d,%d,%d phase=%s cell=%d,%d,%d water=%b",
                feet.getX(), feet.getY(), feet.getZ(), phase,
                targetCell.getX(), targetCell.getY(), targetCell.getZ(), mc.player.isTouchingWater()));
        }
    }

    private void trackSideDigBlock(BlockPos target) {
        long now = System.currentTimeMillis();
        if (!target.equals(autoSideDigBlockPos)) {
            autoSideDigBlockPos = target;
            autoSideDigLastSlowLogMs = now;
            return;
        }
        if (now - autoSideDigLastSlowLogMs >= 15_000L) {
            autoSideDigLastSlowLogMs = now;
            autoLog("SIDE_DIG", autoSite.center, String.format("side dig slow %d,%d,%d", target.getX(), target.getY(), target.getZ()));
        }
    }

    private BlockPos findRoomDebris() {
        Room room = autoSite.room;
        Vec3d eye = mc.player.getEyePos();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        boolean sawDebris = false;

        List<BlockPos> candidates = new ArrayList<>();

        if (autoRoomSpawnerPos != null) {
            int sx = autoRoomSpawnerPos.getX();
            int sy = autoRoomSpawnerPos.getY();
            int sz = autoRoomSpawnerPos.getZ();
            for (int dy = 0; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        candidates.add(new BlockPos(sx + dx, sy + dy, sz + dz));
                    }
                }
            }
            candidates.add(new BlockPos(sx, sy + 2, sz));
        }

        for (BlockPos chest : autoSite.roomContainers) candidates.add(chest.up());

        for (BlockPos pos : candidates) {
            if (pos.getX() < room.cx() - room.k() || pos.getX() > room.cx() + room.k()
                || pos.getY() < room.cy() || pos.getY() > room.cy() + 3
                || pos.getZ() < room.cz() - room.p() || pos.getZ() > room.cz() + room.p()) continue;

            BlockState state = mc.world.getBlockState(pos);
            if (isOpenState(state, pos)) continue;
            if (!state.getFluidState().isEmpty()) continue;
            if (state.getBlock() == Blocks.SPAWNER) continue;
            if (isContainerPresent(pos)) continue;

            if (autoRoomDebrisIgnored.contains(pos)) continue;
            sawDebris = true;
            if (autoRoomDebrisDeferred.contains(pos)) continue;
            double distSq = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = pos;
            }
        }

        if (best == null && sawDebris && !autoRoomDebrisDeferred.isEmpty()) {
            autoRoomDebrisDeferred.clear();
            return findRoomDebris();
        }

        return best;
    }

    private boolean interactWhenAligned(Vec3d lookTarget, BlockHitResult hit) {
        float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), lookTarget);
        RotationUtils.getInstance().setRotationFull(rot[0], rot[1], 120.0);
        if (!RotationUtils.getInstance().isAligned(3.0)) return false;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        return true;
    }

    private boolean alignedTowards(Vec3d lookTarget) {
        float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), lookTarget);
        RotationUtils.getInstance().setRotationFull(rot[0], rot[1], 120.0);
        return RotationUtils.getInstance().isAligned(3.0);
    }

    private void holdNonBlockItem() {
        ItemStack held = mc.player.getMainHandStack();
        if (!(held.getItem() instanceof BlockItem)) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && !(stack.getItem() instanceof BlockItem)) {
                ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(i);
                return;
            }
        }
    }

    private boolean readContainer(BlockPos target) {
        boolean isShulkerScreen = mc.currentScreen instanceof ShulkerBoxScreen;
        int containerSlots;
        if (isShulkerScreen) {
            containerSlots = 27;
        } else if (mc.currentScreen instanceof GenericContainerScreen gcs) {
            containerSlots = ((GenericContainerScreenHandler) gcs.getScreenHandler()).getRows() * 9;
        } else {
            mc.player.closeHandledScreen();
            return true;
        }

        var handler = mc.player.currentScreenHandler;
        int fullShulkers = 0, emptyShulkers = 0, piles = 0, items = 0;

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            piles++;
            items += stack.getCount();
            if (Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock) {
                ContainerComponent cc = stack.get(DataComponentTypes.CONTAINER);
                boolean full = cc != null && cc.iterateNonEmpty().iterator().hasNext();
                if (full) fullShulkers++; else emptyShulkers++;
            }
        }

        mc.player.closeHandledScreen();

        statShulkersFound += fullShulkers;

        String kindLabel = isShulkerScreen ? "shulker_box" : Registries.BLOCK.getId(mc.world.getBlockState(target).getBlock()).getPath();
        boolean keep = fullShulkers > 0;
        if (keep) {
            autoRoomFoundShulkers += fullShulkers;
            autoRoomFoundItems += piles;
            XaeroWaypointManager.addWaypoint("SpawnerDetector", target, "Dungeon shulkers " + fullShulkers, "DS", MapUtil.WpColor.Purple, false);
            info("Dungeon shulkers found: %d at %d %d %d", fullShulkers, target.getX(), target.getY(), target.getZ());
        }

        if (log.get()) HunterBuddyAddon.LOG.info(
            "[SpawnerDetector/auto] ROOM site={},{},{} container={},{},{} type={} piles={} fullShulkers={} emptyShulkers={} action={}",
            autoSite.center.getX(), autoSite.center.getY(), autoSite.center.getZ(),
            target.getX(), target.getY(), target.getZ(), kindLabel, piles, fullShulkers, emptyShulkers,
            keep ? "kept" : "breaking");

        return keep;
    }

    private BlockPos findSpawnerInRoom(Room room) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = room.cx() - room.k(); x <= room.cx() + room.k(); x++) {
            for (int z = room.cz() - room.p(); z <= room.cz() + room.p(); z++) {
                for (int y = room.cy(); y <= room.cy() + 3; y++) {
                    if (mc.world.getBlockState(cursor.set(x, y, z)).getBlock() == Blocks.SPAWNER) {
                        return cursor.toImmutable();
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findRoomApproachTarget(BlockPos container) {
        return findRoomApproachTarget(container, Set.of());
    }

    private BlockPos findRoomApproachTarget(BlockPos container, Set<BlockPos> exclude) {
        Room room = autoSite.room;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int[] d : dirs) {
            int x = container.getX() + d[0];
            int z = container.getZ() + d[1];
            if (x < room.minX() || x > room.maxX() || z < room.minZ() || z > room.maxZ()) continue;

            cursor.set(x, room.cy(), z);
            if (!isOpenState(mc.world.getBlockState(cursor), cursor)) continue;

            BlockPos feet = new BlockPos(x, room.cy(), z);
            if (exclude.contains(feet)) continue;
            double distSq = feet.getSquaredDistance(mc.player.getBlockPos());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = feet;
            }
        }

        return best;
    }

    private void approachBlock(BlockPos target) {
        if (!target.equals(autoApproachTarget)) {
            autoApproachTarget = target;
            autoApproachCell = null;
            autoApproachTicks = 0;
        }

        Set<BlockPos> bad = autoApproachBadCells.computeIfAbsent(target, t -> new java.util.HashSet<>());
        autoApproachTicks++;

        double eyeDist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(target));

        if (autoApproachTicks == 1) {
            autoApproachBaselineDist = eyeDist;
        } else if (autoApproachTicks % 100 == 0) {
            if (autoApproachBaselineDist - eyeDist < 0.5) {
                BlockPos stuckCell = autoApproachCell != null ? autoApproachCell : target;
                bad.add(stuckCell);
                autoApproachCell = null;
            }
            autoApproachBaselineDist = eyeDist;
        }

        if (autoApproachCell == null) {
            BlockPos next = findRoomApproachTarget(target, bad);
            if (next == null && !bad.isEmpty()) {
                bad.clear();
                next = findRoomApproachTarget(target, bad);
            }
            autoApproachCell = next;
            if (autoApproachTicks > 1) {
                BlockPos logCell = autoApproachCell != null ? autoApproachCell : target;
                autoLog("ROOM", target, String.format("approach stuck, switching to %d,%d,%d",
                    logCell.getX(), logCell.getY(), logCell.getZ()));
            }
        }

        BlockPos aimAt = autoApproachCell != null ? autoApproachCell : target;
        Vec3d cellCenter = Vec3d.ofCenter(aimAt);
        double dx = cellCenter.x - mc.player.getX();
        double dz = cellCenter.z - mc.player.getZ();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        Utils.setPressed(mc.options.forwardKey, true);

        boolean inWater = mc.player.isTouchingWater();
        if (inWater && mc.player.getBlockPos().getY() > autoSite.room.cy()) {
            Utils.setPressed(mc.options.sneakKey, true);
            Utils.setPressed(mc.options.jumpKey, false);
        } else {
            Utils.setPressed(mc.options.sneakKey, false);
            Utils.setPressed(mc.options.jumpKey, mc.player.horizontalCollision);
        }

        if (autoApproachTicks % 40 == 0) {
            autoLog("ROOM", target, String.format(
                "approach pos=%.1f,%.1f,%.1f cell=%d,%d,%d dist=%.2f water=%b submerged=%b ground=%b hcol=%b",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                aimAt.getX(), aimAt.getY(), aimAt.getZ(), eyeDist, inWater,
                mc.player.isSubmergedInWater(), mc.player.isOnGround(), mc.player.horizontalCollision));
        }
    }

    private void tickRoomSpawner() {
        if (autoRoomSpawnerPos == null || mc.world.getBlockState(autoRoomSpawnerPos).getBlock() != Blocks.SPAWNER) {
            if (autoRoomSpawnerPos != null) statSpawnersBroken++;
            Utils.setPressed(mc.options.attackKey, false);
            autoRoomSpawnerHandled = true;
            return;
        }

        double distSq = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(autoRoomSpawnerPos));
        double moved = autoRoomSpawnerAnchor == null ? 0.0
            : Math.hypot(mc.player.getX() - autoRoomSpawnerAnchor.x, mc.player.getZ() - autoRoomSpawnerAnchor.z);

        if (!autoRoomSpawnerRepositioning && (moved > 1.0 || distSq > 4.2 * 4.2)) {
            autoRoomSpawnerRepositioning = true;
            Utils.setPressed(mc.options.attackKey, false);
            if (autoRoomSpawnerAnchor != null) mc.interactionManager.cancelBlockBreaking();
            autoLog("ROOM", autoRoomSpawnerPos, String.format("spawner out of place moved=%.2f dist=%.2f, repositioning", moved, Math.sqrt(distSq)));
        }

        if (autoRoomSpawnerRepositioning) {
            if (distSq <= 3.0 * 3.0 && mc.player.isOnGround()) {
                releaseAllAutoKeys();
                autoRoomSpawnerRepositioning = false;
                autoRoomSpawnerAnchor = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                autoRoomSpawnerStartMs = System.currentTimeMillis();
                autoLog("ROOM", autoRoomSpawnerPos, "spawner repositioned, breaking again");
            } else {
                approachBlock(autoRoomSpawnerPos);
                return;
            }
        }

        if (autoRoomSpawnerAnchor == null) {
            autoRoomSpawnerAnchor = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }

        if (System.currentTimeMillis() - autoRoomSpawnerStartMs > 20_000L) {
            Utils.setPressed(mc.options.attackKey, false);
            autoRoomSpawnerHandled = true;
            autoLog("ROOM", autoRoomSpawnerPos, "spawner break timeout");
            return;
        }

        AutoEatSync eat = autoEatSyncModule();
        if (eat != null && eat.isEating()) return;

        int pickaxe = ensurePickaxeInHotbar(mc.world.getBlockState(autoRoomSpawnerPos));
        if (pickaxe >= 0 && mc.player.getInventory().getSelectedSlot() != pickaxe) {
            Utils.setPressed(mc.options.attackKey, false);
            InvUtils.swap(pickaxe, false);
            autoLog("ROOM", autoRoomSpawnerPos, "pickaxe in hand slot=" + pickaxe);
            return;
        }

        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater());
        if (autoMineTick("ROOM", autoRoomSpawnerPos, false)) {
            autoRoomSpawnerHandled = true;
        }
    }

    private int ensurePickaxeInHotbar(BlockState state) {
        int best = -1;
        float bestSpeed = 0.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isIn(ItemTags.PICKAXES)) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }

        int invBest = -1;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isIn(ItemTags.PICKAXES)) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                invBest = i;
            }
        }
        if (invBest < 0) return best;

        int target = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) { target = i; break; }
        }
        if (target < 0) target = best;
        if (target < 0) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES) || s.getItem() == Items.FIREWORK_ROCKET
                    || s.getItem() == Items.OBSIDIAN || s.getItem() == Items.ENCHANTED_GOLDEN_APPLE
                    || s.getItem() == Items.GOLDEN_APPLE || s.getItem() == Items.TOTEM_OF_UNDYING) continue;
                target = i;
                break;
            }
        }
        if (target < 0) return best;

        InvUtils.move().from(invBest).to(target);
        return target;
    }

    private void tickRoomContainers() {
        if (autoRoomIndex >= autoRoomQueue.size()) {
            BlockPos spawner = findSpawnerInRoom(autoSite.room);
            if (spawner != null) {
                autoRoomSpawnerPos = spawner;
                autoRoomSpawnerHandled = false;
                autoRoomSpawnerStartMs = System.currentTimeMillis();
                autoRoomSpawnerAnchor = null;
                autoRoomSpawnerRepositioning = false;
                autoLog("ROOM", spawner, "spawner still there, retry");
                return;
            }
            finishRoomAndAscend();
            return;
        }

        BlockPos target = autoRoomQueue.get(autoRoomIndex);

        if (autoRoomSubState == 5) {
            tickRoomContainerBreak(target);
            return;
        }

        if (!isContainerPresent(target)) {
            autoRoomIndex++;
            autoRoomSubState = 0;
            return;
        }

        switch (autoRoomSubState) {
            case 0 -> {
                autoRoomSubStateStartMs = System.currentTimeMillis();
                autoRoomOpenAttempts = 0;
                double distSq = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target));
                autoRoomSubState = distSq > 4.4 * 4.4 ? 1 : 2;
            }
            case 1 -> {
                double distSq = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target));
                if (distSq <= 4.2 * 4.2) {
                    releaseAllAutoKeys();
                    autoRoomSubState = 2;
                } else {
                    approachBlock(target);
                }
            }
            case 2 -> {
                if (autoEatSyncModule() != null && autoEatSyncModule().isEating()) return;
                holdNonBlockItem();
                Utils.setPressed(mc.options.sneakKey, false);
                Vec3d center = Vec3d.ofCenter(target);
                boolean aligned = interactWhenAligned(center, new BlockHitResult(center, Direction.UP, target, false));
                if (aligned) {
                    autoRoomSubStateStartMs = System.currentTimeMillis();
                    autoRoomOpenAttempts++;
                    autoRoomSubState = 3;
                }
            }
            case 3 -> {
                if (mc.currentScreen instanceof GenericContainerScreen || mc.currentScreen instanceof ShulkerBoxScreen) {
                    autoRoomWaitTicks = 5;
                    autoRoomSubState = 4;
                } else if (System.currentTimeMillis() - autoRoomSubStateStartMs > 3000L) {
                    if (autoRoomOpenAttempts < 2) {
                        autoRoomSubState = 2;
                    } else {
                        autoLog("ROOM", target, "container did not open, retry");
                        autoRoomOpenAttempts = 0;
                        autoRoomSubState = 0;
                    }
                }
            }
            case 4 -> {
                if (autoRoomWaitTicks > 0) { autoRoomWaitTicks--; return; }
                boolean kept = readContainer(target);
                if (kept) {
                    autoRoomIndex++;
                    autoRoomSubState = 0;
                } else {
                    autoRoomBreakStartMs = System.currentTimeMillis();
                    autoRoomBreakTicks = 0;
                    autoRoomContainerGoneTicks = 0;
                    autoRoomBreakAnchor = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    autoRoomBreakRepositioning = false;
                    autoRoomSubState = 5;
                }
            }
            default -> autoRoomSubState = 0;
        }
    }

    private void tickRoomContainerBreak(BlockPos target) {
        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater());
        autoRoomBreakTicks++;

        boolean present = isContainerPresent(target);
        if (!present) {
            autoRoomContainerGoneTicks++;
            if (autoRoomContainerGoneTicks >= 20) {
                Utils.setPressed(mc.options.attackKey, false);
                statChestsBroken++;
                autoLog("ROOM", target, "broken in " + autoRoomBreakTicks + " ticks");
                autoRoomIndex++;
                autoRoomSubState = 0;
                return;
            }
        } else {
            autoRoomContainerGoneTicks = 0;

            double distSq = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target));
            double moved = autoRoomBreakAnchor == null ? 0.0
                : Math.hypot(mc.player.getX() - autoRoomBreakAnchor.x, mc.player.getZ() - autoRoomBreakAnchor.z);

            if (!autoRoomBreakRepositioning && (moved > 1.0 || distSq > 4.2 * 4.2)) {
                autoRoomBreakRepositioning = true;
                Utils.setPressed(mc.options.attackKey, false);
                mc.interactionManager.cancelBlockBreaking();
                autoLog("ROOM", target, String.format("pushed away moved=%.2f dist=%.2f, repositioning", moved, Math.sqrt(distSq)));
            }

            if (autoRoomBreakRepositioning) {
                if (distSq <= 3.0 * 3.0 && mc.player.isOnGround()) {
                    releaseAllAutoKeys();
                    autoRoomBreakRepositioning = false;
                    autoRoomBreakAnchor = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    autoRoomBreakStartMs = System.currentTimeMillis();
                    autoLog("ROOM", target, "repositioned, breaking again");
                } else {
                    approachBlock(target);
                    return;
                }
            }
        }

        if (System.currentTimeMillis() - autoRoomBreakStartMs > 20_000L) {
            Utils.setPressed(mc.options.attackKey, false);
            autoRoomBreakRepositioning = false;
            autoLog("ROOM", target, "break failed, retry");
            autoRoomSubState = 0;
            return;
        }

        autoMineTick("ROOM", target, false);
    }

    private void finishRoomAndAscend() {
        releaseAllAutoKeys();

        if (autoRoomFoundShulkers > 0) {
            autoLog("ROOM", autoSite.center, "found=" + autoRoomFoundShulkers + " items=" + autoRoomFoundItems);
        }

        if (tryChainVertical()) return;
        if (autoChainUnderground.get() && tryChainUnderground(autoSite)) return;

        autoAscendFromAbort = false;
        enterState(AutoState.EXIT, "");
    }

    private boolean tryChainVertical() {
        Site lower = autoStackedPartner;
        autoStackedPartner = null;
        if (lower == null) return false;

        if (!qualifiesAsStackedPartner(lower)) {
            autoLog("CHAIN", autoSite.center, "no vertical chain target reason=partner invalid");
            return false;
        }

        BlockPos from = autoSite.center;
        markVisited(from, autoRunOutcome());
        autoLog("CHAIN", from, String.format("vertical next=%d,%d,%d",
            lower.center.getX(), lower.center.getY(), lower.center.getZ()));

        autoSite = lower;
        autoRunAbandoned = false;
        autoRoomFoundShulkers = 0;
        autoRoomFoundItems = 0;

        enterDigState("continuing shaft to lower room");
        return true;
    }

    private boolean isRoomFlooded(Room room) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int waterCells = 0;
        for (int x = room.cx() - room.k(); x <= room.cx() + room.k(); x++) {
            for (int z = room.cz() - room.p(); z <= room.cz() + room.p(); z++) {
                for (int y = room.cy(); y <= room.cy() + 1; y++) {
                    cursor.set(x, y, z);
                    if (mc.world.getFluidState(cursor).isIn(FluidTags.WATER)) {
                        waterCells++;
                        if (waterCells >= 3) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean tryChainUnderground(Site finishedSite) {
        if (isRoomFlooded(finishedSite.room)) {
            autoLog("CHAIN", finishedSite.center, "no underground chain target reason=flooded room");
            return false;
        }

        Site chainSite = null;
        double bestDistSq = Double.MAX_VALUE;
        String nearestRejectReason = null;
        double nearestRejectDistSq = Double.MAX_VALUE;

        for (Site site : sites.values()) {
            if (site.center.equals(finishedSite.center)) continue;
            if (!qualifiesForAuto(site)) continue;

            double dx = site.center.getX() + 0.5 - mc.player.getX();
            double dz = site.center.getZ() + 0.5 - mc.player.getZ();
            double distSq = dx * dx + dz * dz;

            QualResult qr = evaluateQualification(site, false, true);
            if (qr.reject() != null) {
                if (distSq < nearestRejectDistSq) {
                    nearestRejectDistSq = distSq;
                    nearestRejectReason = qr.reject();
                }
                continue;
            }

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                chainSite = site;
            }
        }

        if (chainSite == null) {
            autoLog("CHAIN", finishedSite.center, "no underground chain target"
                + (nearestRejectReason != null ? " reason=" + nearestRejectReason : ""));
            return false;
        }

        BlockPos from = finishedSite.center;
        double chainDist = Math.sqrt(bestDistSq);
        markVisited(from, autoRunOutcome());
        autoLog("CHAIN", from, String.format("underground next=%d,%d,%d dist=%.1f",
            chainSite.center.getX(), chainSite.center.getY(), chainSite.center.getZ(), chainDist));

        autoSite = chainSite;
        autoRunAbandoned = false;
        autoRoomFoundShulkers = 0;
        autoRoomFoundItems = 0;

        enterState(AutoState.TUNNEL, String.format("chained underground from %d,%d,%d dist=%.1f",
            from.getX(), from.getY(), from.getZ(), chainDist));
        return true;
    }

    private BlockPos findTunnelGoal(Room room) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        Set<BlockPos> containers = new java.util.HashSet<>(autoSite.roomContainers);
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        BlockPos spawner = findSpawnerInRoom(room);
        BlockPos preferred = spawner != null ? spawner.up() : new BlockPos(room.cx(), room.cy(), room.cz());
        BlockPos preferredHead = preferred.up();
        BlockPos preferredFloor = preferred.down();
        BlockState preferredFloorState = mc.world.getBlockState(preferredFloor);
        if (isOpenState(mc.world.getBlockState(preferred), preferred)
            && isOpenState(mc.world.getBlockState(preferredHead), preferredHead)
            && mc.world.getFluidState(preferred).isEmpty() && mc.world.getFluidState(preferredHead).isEmpty()
            && !isOpenState(preferredFloorState, preferredFloor) && !isLava(preferredFloorState)
            && !containers.contains(preferred)) {
            return preferred;
        }

        for (int x = room.cx() - room.k(); x <= room.cx() + room.k(); x++) {
            for (int z = room.cz() - room.p(); z <= room.cz() + room.p(); z++) {
                BlockPos feet = new BlockPos(x, room.cy(), z);
                if (containers.contains(feet)) continue;

                cursor.set(x, room.cy(), z);
                BlockState feetState = mc.world.getBlockState(cursor);
                if (feetState.getBlock() == Blocks.SPAWNER) continue;
                if (!isOpenState(feetState, cursor)) continue;

                cursor.set(x, room.cy() + 1, z);
                if (!isOpenState(mc.world.getBlockState(cursor), cursor)) continue;

                cursor.set(x, room.cy() - 1, z);
                BlockState floorState = mc.world.getBlockState(cursor);
                if (isOpenState(floorState, cursor) || isLava(floorState)) continue;
                if (!mc.world.getFluidState(feet).isEmpty() || !mc.world.getFluidState(feet.up()).isEmpty()) continue;

                double distSq = feet.getSquaredDistance(mc.player.getBlockPos());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = feet;
                }
            }
        }

        return best;
    }

    private void tickTunnel() {
        stateTicks++;

        if (stateTicks == 1) {
            autoBaritoneRetries = 0;
            autoPaused = false;
            autoPauseReason = null;
            autoPauseStartMs = 0;
            autoPausedTicks = 0;
            resetBreach();
            BaritoneAPI.getSettings().allowPlace.value = true;
            BlockPos goal = findTunnelGoal(autoSite.room);
            if (goal == null) {
                BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
                autoLog("TUNNEL", autoSite.center, "tunnel failed: no floor cell");
                markVisited(autoSite.center, "aborted:tunnel");
                enterState(AutoState.EXIT, "");
                return;
            }
            autoTunnelGoal = goal;
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(autoTunnelGoal));
        }

        if (mc.player.isInLava()) { abort("lava"); return; }
        if (handleAutoPause(false)) return;
        if (otherPlayerPresent()) { abort("player appeared"); return; }

        if ((stateTicks - autoPausedTicks) > 120 * 20) {
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
            autoLog("TUNNEL", autoSite.center, "tunnel failed");
            autoRunAbandoned = true;
            releaseAllAutoKeys();
            enterState(AutoState.EXIT, "");
            return;
        }

        double dxz = Math.hypot((autoTunnelGoal.getX() + 0.5) - mc.player.getX(), (autoTunnelGoal.getZ() + 0.5) - mc.player.getZ());
        int feetY = mc.player.getBlockPos().getY();

        if (dxz <= 1.0 && mc.player.isOnGround() && feetY >= autoSite.room.cy() && feetY <= autoSite.room.cy() + 3) {
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
            releaseAllAutoKeys();
            enterState(AutoState.ROOM, "");
            return;
        }

        IBaritone bt = primaryBaritone();
        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();

        if (!pathing && autoBreachPos == null && stateTicks > 20 && autoBaritoneRetries < 3) {
            autoBaritoneRetries++;
            if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(autoTunnelGoal));
        }

        tickBreach("TUNNEL", autoTunnelGoal);

        logNavTelemetry("TUNNEL", autoTunnelGoal, autoBaritoneRetries);
    }

    private boolean isTunnelProtected(BlockPos pos) {
        for (BlockPos p : new BlockPos[]{pos, pos.up()}) {
            if (mc.world.getBlockState(p).getBlock() == Blocks.SPAWNER || isContainerPresent(p)) return true;
        }
        return false;
    }

    private void resetBreach() {
        autoBreachCount = 0;
        autoBreachBestDist = Double.MAX_VALUE;
        autoBreachBestElapsed = 0;
        autoBreachPos = null;
    }

    private void tickBreach(String stateLabel, BlockPos goal) {
        double dist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(goal));
        int elapsed = stateTicks - autoPausedTicks;
        if (dist < autoBreachBestDist - 1.0) {
            autoBreachBestDist = dist;
            autoBreachBestElapsed = elapsed;
            return;
        }
        if (elapsed - autoBreachBestElapsed < 160 || autoBreachCount >= 6) return;

        BlockPos feet = mc.player.getBlockPos();
        BlockPos target;
        if (goal.getY() > feet.getY() + 1) {
            target = feet.up(2);
            int top = Math.min(goal.getY() + 1, feet.getY() + 9);
            for (int y = feet.getY() + 2; y <= top; y++) {
                BlockPos above = new BlockPos(feet.getX(), y, feet.getZ());
                if (!isOpenState(mc.world.getBlockState(above), above)) {
                    target = above;
                    break;
                }
            }
        } else {
            int dx = goal.getX() - feet.getX();
            int dz = goal.getZ() - feet.getZ();
            if (dx == 0 && dz == 0) return;
            BlockPos first = dx != 0 ? feet.add(Integer.signum(dx), 0, 0) : feet.add(0, 0, Integer.signum(dz));
            BlockPos alt = dx != 0 && dz != 0 ? feet.add(0, 0, Integer.signum(dz)) : null;
            BlockPos next = first;
            if (isTunnelProtected(first) && alt != null && !isTunnelProtected(alt)) next = alt;
            BlockPos head = next.up();
            target = !isOpenState(mc.world.getBlockState(head), head) ? head : next;
        }

        if (isTunnelProtected(target) || isOpenState(mc.world.getBlockState(target), target)) {
            autoBreachPos = null;
            autoBreachBestDist = dist;
            autoBreachBestElapsed = elapsed;
            return;
        }

        if (!target.equals(autoBreachPos)) {
            autoBreachPos = target;
            autoBreachLabel = Registries.BLOCK.getId(mc.world.getBlockState(target).getBlock()).getPath();
            IBaritone stopBt = primaryBaritone();
            if (stopBt != null) stopBt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
        }

        if (!autoMineTick(stateLabel, target, false)) return;

        autoBreachPos = null;
        autoBreachCount++;
        autoLog(stateLabel, target, "breach " + target.getX() + "," + target.getY() + "," + target.getZ() + " block=" + autoBreachLabel);
        IBaritone bt = primaryBaritone();
        if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
        autoBreachBestDist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(goal));
        autoBreachBestElapsed = elapsed;
    }

    private void tickRoomDebris(BlockPos pos) {
        int regenLimit = mc.world.getBlockState(pos).getBlock() instanceof net.minecraft.block.FallingBlock ? 16 : 2;
        if (!pos.equals(autoRoomDebrisCurrent) && autoRoomDebrisBreaks.getOrDefault(pos, 0) >= regenLimit) {
            autoRoomDebrisIgnored.add(pos);
            autoRoomDebrisCurrent = null;
            autoLog("ROOM", pos, "debris regenerates block="
                + Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).getPath() + ", ignored");
            return;
        }
        if (!pos.equals(autoRoomDebrisCurrent)) {
            autoRoomDebrisCurrent = pos;
            autoRoomDebrisStartMs = System.currentTimeMillis();
            autoLog("ROOM", pos, String.format("debris block=%s dist=%.2f",
                Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).getPath(),
                mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos))));
        }

        double eyeDist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
        if (eyeDist > 4.2) {
            Utils.setPressed(mc.options.attackKey, false);
            autoRoomDebrisStartMs = System.currentTimeMillis();
            approachBlock(pos);
            return;
        }

        releaseAllAutoKeys();
        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater());

        if (autoMineTick("ROOM", pos, false)) {
            autoRoomDebrisBreaks.merge(pos, 1, Integer::sum);
            autoRoomDebrisBroken++;
            autoRoomDebrisCurrent = null;
            if (findRoomDebris() == null) {
                autoLog("ROOM", autoSite.center, "debris cleared n=" + autoRoomDebrisBroken);
            }
            return;
        }

        if (System.currentTimeMillis() - autoRoomDebrisStartMs > 10_000L) {
            autoRoomDebrisDeferred.add(pos);
            autoLog("ROOM", pos, "debris deferred");
            autoRoomDebrisCurrent = null;
        }
    }

    private void tickRoom() {
        stateTicks++;

        if (stateTicks == 1) {
            int feetY = mc.player.getBlockPos().getY();
            if (feetY < autoSite.room.cy()) {
                enterState(AutoState.ROOM_CLIMB, "feet below room floor");
                return;
            }

            autoRoomQueue = new ArrayList<>(autoSite.roomContainers);
            autoRoomQueue.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getBlockPos())));
            autoRoomFoundShulkers = 0;
            autoRoomFoundItems = 0;
            autoRoomSpawnerPos = findSpawnerInRoom(autoSite.room);
            autoLog("ROOM", autoSite.center, "spawner=" + (autoRoomSpawnerPos != null
                ? autoRoomSpawnerPos.getX() + "," + autoRoomSpawnerPos.getY() + "," + autoRoomSpawnerPos.getZ()
                : "none"));
            autoRoomSpawnerHandled = false;
            autoRoomSpawnerStartMs = System.currentTimeMillis();
            autoRoomSpawnerAnchor = null;
            autoRoomSpawnerRepositioning = false;
            autoRoomIndex = 0;
            autoRoomSubState = 0;
            autoRoomDebrisDeferred.clear();
            autoRoomDebrisBreaks.clear();
            autoRoomDebrisIgnored.clear();
            autoRoomDebrisCurrent = null;
            autoRoomDebrisStartMs = 0;
            autoRoomDebrisBroken = 0;
            autoPaused = false;
            autoPauseReason = null;
            autoPauseStartMs = 0;
            autoPausedTicks = 0;
        }

        if (mc.player.isOnGround() && mc.player.getBlockPos().getY() < autoSite.room.cy()) {
            if (mc.currentScreen instanceof GenericContainerScreen || mc.currentScreen instanceof ShulkerBoxScreen) {
                mc.player.closeHandledScreen();
            }
            releaseAllAutoKeys();
            BaritoneHelper.stopAllPathing();
            enterState(AutoState.ROOM_CLIMB, "fell below room floor");
            return;
        }

        if (handleAutoPause(true)) return;

        BlockPos debris = findRoomDebris();
        if (debris != null) {
            long now = System.currentTimeMillis();
            if (!autoRoomSpawnerHandled) autoRoomSpawnerStartMs = now;
            if (autoRoomSubState == 5) autoRoomBreakStartMs = now;
            if (mc.currentScreen instanceof GenericContainerScreen || mc.currentScreen instanceof ShulkerBoxScreen) {
                mc.player.closeHandledScreen();
                autoRoomSubState = 0;
            }
            tickRoomDebris(debris);
            return;
        }

        if (!autoRoomSpawnerHandled) {
            tickRoomSpawner();
            return;
        }

        tickRoomContainers();
    }

    private void tickRoomClimb() {
        stateTicks++;

        if (stateTicks == 1) {
            autoRoomClimbMode = 0;
            autoRoomClimbPillarPhase = 0;
            autoRoomClimbJumpTapTicks = 0;
            autoRoomClimbWater = false;
            autoRoomClimbStartMs = System.currentTimeMillis();
            autoRoomClimbBaritoneCandidates = List.of();
            autoRoomClimbBaritoneIndex = 0;
            autoRoomClimbBaritoneTryStartMs = 0;
            autoPaused = false;
            autoPauseReason = null;
            autoPauseStartMs = 0;
            autoPausedTicks = 0;
        }

        if (handleAutoPause(true)) return;

        if (autoRoomClimbMode == 0) {
            tickRoomClimbPillar();
        } else {
            tickRoomClimbBaritone();
        }
    }

    private BlockPos findRoomFloorHole() {
        Room room = autoSite.room;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int x = room.cx() - room.k(); x <= room.cx() + room.k(); x++) {
            for (int z = room.cz() - room.p(); z <= room.cz() + room.p(); z++) {
                cursor.set(x, room.minY(), z);
                if (!isOpenState(mc.world.getBlockState(cursor), cursor)) continue;
                double dx = (x + 0.5) - mc.player.getX(), dz = (z + 0.5) - mc.player.getZ();
                double distSq = dx * dx + dz * dz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = cursor.toImmutable();
                }
            }
        }
        return best;
    }

    private void tickRoomClimbPillar() {
        BlockPos feet = mc.player.getBlockPos();
        if (autoRoomClimbPillarPhase == 0 && feet.getY() >= autoSite.room.cy() && mc.player.isOnGround()) {
            releaseAllAutoKeys();
            enterState(AutoState.ROOM, "climb complete");
            return;
        }

        if (mc.player.isTouchingWater()) autoRoomClimbWater = true;

        if (stateTicks % 20 == 0) {
            autoLog("ROOM_CLIMB", autoSite.center, String.format("pos=%.1f,%.1f,%.1f water=%b touching=%b submerged=%b ground=%b phase=%d",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), autoRoomClimbWater, mc.player.isTouchingWater(),
                mc.player.isSubmergedInWater(), mc.player.isOnGround(), autoRoomClimbPillarPhase));
        }

        if (autoRoomClimbWater) {
            autoRoomClimbPillarPhase = 0;
            if (feet.getY() >= autoSite.room.cy()) {
                releaseAllAutoKeys();
                enterState(AutoState.ROOM, "climb complete");
                return;
            }
            BlockPos hole = findRoomFloorHole();
            if (hole != null) {
                double hdx = (hole.getX() + 0.5) - mc.player.getX();
                double hdz = (hole.getZ() + 0.5) - mc.player.getZ();
                if (Math.hypot(hdx, hdz) > 0.3) {
                    mc.player.setYaw((float) Math.toDegrees(Math.atan2(-hdx, hdz)));
                }
            }
            mc.player.setPitch(-30.0f);
            Utils.setPressed(mc.options.sneakKey, false);
            Utils.setPressed(mc.options.sprintKey, false);
            Utils.setPressed(mc.options.forwardKey, true);
            Utils.setPressed(mc.options.jumpKey, true);
            if (System.currentTimeMillis() - autoRoomClimbStartMs > 20_000L) {
                abandonRoomClimbPillar("timeout");
            }
            return;
        }

        mc.player.setPitch(90.0f);
        Utils.setPressed(mc.options.forwardKey, false);
        Utils.setPressed(mc.options.sprintKey, false);

        if (autoRoomClimbPillarPhase == 0 && mc.player.isOnGround()) {
            if (hasBlockAbove(feet.getX(), feet.getY() + 1, feet.getZ(), 2)) {
                abandonRoomClimbPillar("blocked above");
                return;
            }

            int slot = ensureObsidianInHotbar();
            if (slot < 0) {
                abandonRoomClimbPillar("no obsidian");
                return;
            }
            InvUtils.swap(slot, false);

            autoRoomClimbFeetX = feet.getX();
            autoRoomClimbFeetY = feet.getY();
            autoRoomClimbFeetZ = feet.getZ();
            autoRoomClimbJumpTapTicks = 1;
            autoRoomClimbPillarPhase = 1;
        }

        if (autoRoomClimbPillarPhase == 1 && mc.player.getY() >= autoRoomClimbFeetY + 1.0) {
            BlockPos supportPos = new BlockPos(autoRoomClimbFeetX, autoRoomClimbFeetY - 1, autoRoomClimbFeetZ);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(
                new Vec3d(autoRoomClimbFeetX + 0.5, autoRoomClimbFeetY - 1 + 1.0, autoRoomClimbFeetZ + 0.5),
                Direction.UP, supportPos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            autoLog("ROOM_CLIMB", autoSite.center, "climb pillar placed y=" + autoRoomClimbFeetY);
            autoRoomClimbStartMs = System.currentTimeMillis();
            autoRoomClimbPillarPhase = 0;
        }

        if (autoRoomClimbJumpTapTicks > 0) {
            Utils.setPressed(mc.options.jumpKey, true);
            autoRoomClimbJumpTapTicks--;
        } else {
            Utils.setPressed(mc.options.jumpKey, false);
        }

        if (System.currentTimeMillis() - autoRoomClimbStartMs > 20_000L) {
            abandonRoomClimbPillar("timeout");
        }
    }

    private void abandonRoomClimbPillar(String reason) {
        releaseAllAutoKeys();
        autoLog("ROOM_CLIMB", autoSite.center, "pillar abandoned: " + reason);

        autoRoomClimbMode = 1;
        autoRoomClimbBaritoneCandidates = findRoomClimbCandidates(autoSite.room);
        autoRoomClimbBaritoneIndex = 0;
        autoRoomClimbBaritoneTryStartMs = 0;
        autoBaritoneRetries = 0;
        BaritoneAPI.getSettings().allowPlace.value = true;
    }

    private void tickRoomClimbBaritone() {
        if (autoRoomClimbBaritoneIndex >= autoRoomClimbBaritoneCandidates.size()) {
            roomClimbFailed();
            return;
        }

        BlockPos goal = autoRoomClimbBaritoneCandidates.get(autoRoomClimbBaritoneIndex);

        if (autoRoomClimbBaritoneTryStartMs == 0) {
            autoRoomClimbBaritoneTryStartMs = System.currentTimeMillis();
            autoBaritoneRetries = 0;
            autoLog("ROOM_CLIMB", autoSite.center, "climb baritone try " + (autoRoomClimbBaritoneIndex + 1)
                + " goal=" + goal.getX() + "," + goal.getY() + "," + goal.getZ());
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
        }

        Room room = autoSite.room;
        BlockPos feet = mc.player.getBlockPos();
        boolean inInterior = feet.getX() >= room.cx() - room.k() && feet.getX() <= room.cx() + room.k()
            && feet.getZ() >= room.cz() - room.p() && feet.getZ() <= room.cz() + room.p();

        if (feet.getY() == room.cy() && inInterior && mc.player.isOnGround()) {
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
            releaseAllAutoKeys();
            BaritoneHelper.stopAllPathing();
            enterState(AutoState.ROOM, "climb baritone arrived");
            return;
        }

        long tryElapsed = System.currentTimeMillis() - autoRoomClimbBaritoneTryStartMs;
        if (tryElapsed > 30_000L) {
            BaritoneHelper.stopAllPathing();
            autoRoomClimbBaritoneIndex++;
            autoRoomClimbBaritoneTryStartMs = 0;
            return;
        }

        IBaritone bt = primaryBaritone();
        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
        if (!pathing && tryElapsed > 1000L && autoBaritoneRetries < 3) {
            autoBaritoneRetries++;
            if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
        }

        logNavTelemetry("ROOM_CLIMB", goal, autoBaritoneRetries);
    }

    private void roomClimbFailed() {
        autoLog("ROOM_CLIMB", autoSite.center, "climb failed, retry");
        BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
        releaseAllAutoKeys();
        BaritoneHelper.stopAllPathing();
        enterState(AutoState.ROOM_CLIMB, "retry");
    }

    private List<BlockPos> findRoomClimbCandidates(Room room) {
        List<BlockPos> open = new ArrayList<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = room.cx() - room.k(); x <= room.cx() + room.k(); x++) {
            for (int z = room.cz() - room.p(); z <= room.cz() + room.p(); z++) {
                cursor.set(x, room.cy(), z);
                if (!isOpenState(mc.world.getBlockState(cursor), cursor)) continue;
                cursor.set(x, room.cy() + 1, z);
                if (!isOpenState(mc.world.getBlockState(cursor), cursor)) continue;
                open.add(new BlockPos(x, room.cy(), z));
            }
        }

        open.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getBlockPos())));

        List<BlockPos> picked = new ArrayList<>();
        Set<Integer> usedSides = new java.util.HashSet<>();
        for (BlockPos p : open) {
            if (picked.size() >= 3) break;
            int side = climbSideBucket(room, p);
            if (usedSides.contains(side)) continue;
            usedSides.add(side);
            picked.add(p);
        }
        for (BlockPos p : open) {
            if (picked.size() >= 3) break;
            if (!picked.contains(p)) picked.add(p);
        }

        return picked;
    }

    private static int climbSideBucket(Room room, BlockPos p) {
        double dx = p.getX() - room.cx();
        double dz = p.getZ() - room.cz();
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360.0;
        return ((int) ((angle + 45.0) / 90.0)) % 4;
    }

    private boolean tryChainWalk(Site finishedSite) {
        Site chainSite = null;
        ColumnPlan chainPlan = null;
        double chainDist = 0;
        double bestDistSq = Double.MAX_VALUE;
        String nearestRejectReason = null;
        double nearestRejectDistSq = Double.MAX_VALUE;

        for (Site site : sites.values()) {
            if (site.center.equals(finishedSite.center)) continue;
            if (!qualifiesForAuto(site)) continue;

            double dx = site.center.getX() + 0.5 - mc.player.getX();
            double dz = site.center.getZ() + 0.5 - mc.player.getZ();
            double distSq = dx * dx + dz * dz;

            QualResult qr = evaluateQualification(site, true);
            if (qr.reject() != null) {
                if (distSq < nearestRejectDistSq) {
                    nearestRejectDistSq = distSq;
                    nearestRejectReason = qr.reject();
                }
                continue;
            }

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                chainSite = site;
                chainPlan = qr.plan();
                chainDist = Math.sqrt(distSq);
            }
        }

        if (chainSite == null) {
            autoLog("CHAIN", finishedSite.center, "no chain target"
                + (nearestRejectReason != null ? " reason=" + nearestRejectReason : ""));
            return false;
        }

        BlockPos from = finishedSite.center;
        markVisited(from, autoRunOutcome());
        autoLog("CHAIN", from, String.format("next=%d,%d,%d dist=%.1f",
            chainSite.center.getX(), chainSite.center.getY(), chainSite.center.getZ(), chainDist));

        autoSite = chainSite;
        autoColumn = chainPlan;
        autoRunAbandoned = false;
        autoRoomFoundShulkers = 0;
        autoRoomFoundItems = 0;

        enterState(AutoState.WALK, String.format("chained from %d,%d,%d dist=%.1f",
            from.getX(), from.getY(), from.getZ(), chainDist));
        return true;
    }

    private void enterAscendOrSwimUp(String details) {
        boolean water = (autoExitShaft != null && autoExitShaft.water()) || autoColumn.water();
        if (water) {
            enterState(AutoState.SWIM_UP, details);
        } else {
            enterState(AutoState.ASCEND, details);
        }
    }

    private void tickExit() {
        stateTicks++;

        if (autoExitShaft != null) {
            BlockPos shaftBottom = new BlockPos(autoExitShaft.x(), autoExitShaft.bottomY(), autoExitShaft.z());
            double farDxz = Math.hypot((autoExitShaft.x() + 0.5) - mc.player.getX(), (autoExitShaft.z() + 0.5) - mc.player.getZ());

            if (stateTicks == 1) {
                boolean roomWater = mc.player.isTouchingWater()
                    && columnHasWater(autoExitShaft.x(), autoExitShaft.z(), autoExitShaft.bottomY(), autoExitShaft.exitY());
                autoExitReturning = farDxz > 4.0 && !roomWater;
                if (autoExitReturning) {
                    autoBaritoneRetries = 0;
                    resetBreach();
                    autoLog("EXIT", autoSite.center, String.format("returning to shaft %d,%d,%d with baritone dist=%.1f",
                        shaftBottom.getX(), shaftBottom.getY(), shaftBottom.getZ(), farDxz));
                    IBaritone bt = primaryBaritone();
                    if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(shaftBottom));
                }
            }

            if (autoExitReturning) {
                double rdy = Math.abs(mc.player.getY() - autoExitShaft.bottomY());
                IBaritone bt = primaryBaritone();
                if (farDxz <= 1.5 && rdy <= 1.5) {
                    if (bt != null) bt.getPathingBehavior().cancelEverything();
                    releaseAllAutoKeys();
                    autoExitReturning = false;
                    enterState(AutoState.EXIT, "back at shaft");
                    return;
                }
                if (stateTicks > 180 * 20) {
                    if (bt != null) bt.getPathingBehavior().cancelEverything();
                    releaseAllAutoKeys();
                    autoExitReturning = false;
                    enterAscendOrSwimUp("return to shaft timeout");
                    return;
                }
                boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
                if (!pathing && autoBreachPos == null && stateTicks > 20 && stateTicks % 40 == 0) {
                    autoBaritoneRetries++;
                    if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(shaftBottom));
                }
                tickBreach("EXIT", shaftBottom);
                logNavTelemetry("EXIT return", shaftBottom, autoBaritoneRetries);
                return;
            }
        }

        if (mc.player.isTouchingWater() && autoExitShaft != null
            && columnHasWater(autoExitShaft.x(), autoExitShaft.z(), autoExitShaft.bottomY(), autoExitShaft.exitY())) {
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            enterState(AutoState.SWIM_UP, "water in room");
            return;
        }

        if (stateTicks == 1) {
            autoShaftFlyAttempts = 0;
            autoShaftFlyRerouteAttempts = 0;
            autoExitWalkingLogged = false;
            if (!autoAreaLoaderWasOn) {
                enterAscendOrSwimUp("no flight module, staying on foot");
                return;
            }
            boolean eligible = autoShaftFly.get()
                && autoExitShaft != null
                && !autoExitShaft.water()
                && !autoExitShaft.skyBlocked()
                && countRockets() >= 4;
            if (!eligible) {
                enterAscendOrSwimUp("shaft-fly not eligible");
                return;
            }
            autoBaritoneRetries = 0;
            IBaritone bt = primaryBaritone();
            if (bt != null) {
                bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(
                    new BlockPos(autoExitShaft.x(), autoExitShaft.bottomY(), autoExitShaft.z())));
            }
        }

        if (stateTicks > 120 * 20) {
            releaseAllAutoKeys();
            enterAscendOrSwimUp("exit walk timeout");
            return;
        }

        double dxz = Math.hypot((autoExitShaft.x() + 0.5) - mc.player.getX(), (autoExitShaft.z() + 0.5) - mc.player.getZ());
        double dy = Math.abs(mc.player.getY() - autoExitShaft.bottomY());

        if (dxz <= 0.5 && dy <= 0.5) {
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            enterState(AutoState.SHAFT_ALIGN, "");
            return;
        }

        IBaritone bt = primaryBaritone();
        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
        if (!pathing && autoBaritoneRetries >= 3) {
            if (!autoExitWalkingLogged) {
                autoExitWalkingLogged = true;
                autoLog("EXIT", autoSite.center, "baritone gave up, walking to shaft");
            }
            navigateExitColumn(autoExitShaft.x(), autoExitShaft.z(), 0.3, autoExitShaft.entry(), false);
        } else if (!pathing && stateTicks > 20 && autoBaritoneRetries < 3) {
            autoBaritoneRetries++;
            if (bt != null) {
                bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(
                    new BlockPos(autoExitShaft.x(), autoExitShaft.bottomY(), autoExitShaft.z())));
            }
        }

        logNavTelemetry("EXIT", new BlockPos(autoExitShaft.x(), autoExitShaft.bottomY(), autoExitShaft.z()), autoBaritoneRetries);
    }

    private int shaftBlockedY(ExitShaft shaft) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = mc.player.getBlockPos().getY() + 1; y <= shaft.exitY(); y++) {
            cursor.set(shaft.x(), y, shaft.z());
            BlockState state = mc.world.getBlockState(cursor);
            if (!isOpenState(state, cursor) || state.getFluidState().isIn(FluidTags.WATER)) return y;
        }
        return -1;
    }

    private void tickShaftAlign() {
        stateTicks++;

        if (stateTicks == 1) {
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            autoShaftAlignCenteredTicks = 0;
            autoPausedTicks = 0;
        }

        if (handleShaftCombat()) return;

        double alignDist = Math.hypot((autoExitShaft.x() + 0.5) - mc.player.getX(), (autoExitShaft.z() + 0.5) - mc.player.getZ());
        Utils.setPressed(mc.options.sneakKey, alignDist > 0.12 && alignDist < 0.6 && mc.player.isOnGround() && !mc.player.isTouchingWater());
        boolean centered = recenterDig(autoExitShaft.x(), autoExitShaft.z(), 0.12);
        double speedH = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
        autoShaftAlignCenteredTicks = (centered && speedH < 0.02) ? autoShaftAlignCenteredTicks + 1 : 0;

        int blockedY = shaftBlockedY(autoExitShaft);
        if (blockedY != -1) {
            boolean blockedByWater = mc.world.getBlockState(
                new BlockPos(autoExitShaft.x(), blockedY, autoExitShaft.z())).getFluidState().isIn(FluidTags.WATER);
            autoLog("SHAFT_ALIGN", autoSite.center, "shaft blocked at y=" + blockedY);
            releaseAllAutoKeys();
            if (blockedByWater) {
                enterState(AutoState.SWIM_UP, "shaft blocked by water");
            } else {
                enterState(AutoState.ASCEND, "shaft blocked");
            }
            return;
        }

        if ((stateTicks - autoPausedTicks) > 20 * 20) {
            releaseAllAutoKeys();
            enterState(AutoState.ASCEND, "shaft align timeout");
            return;
        }

        if (autoShaftAlignCenteredTicks < 5) return;

        boolean eating = autoEatSyncModule() != null && autoEatSyncModule().isEating();
        if (eating) return;

        int slot = ensureRocketInHotbar();
        if (slot < 0) {
            releaseAllAutoKeys();
            enterState(AutoState.ASCEND, "no rockets for shaft fly");
            return;
        }

        releaseAllAutoKeys();
        enterState(AutoState.SHAFT_FLY, "");
    }

    private boolean ownRocketBoosting() {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity firework
                && firework.isAlive()
                && ((FireworkRocketEntityAccessor) firework).getShooter() == mc.player) {
                return true;
            }
        }
        return false;
    }

    private boolean ownRocketActive() {
        if (ownRocketBoosting()) {
            autoOwnRocketSeenAlive = true;
            return true;
        }
        if (autoOwnRocketFiredAtMs == 0) return false;
        if (autoOwnRocketSeenAlive) {
            autoOwnRocketFiredAtMs = 0;
            autoOwnRocketSeenAlive = false;
            return false;
        }
        if (System.currentTimeMillis() - autoOwnRocketFiredAtMs < 2000L) return true;
        autoOwnRocketFiredAtMs = 0;
        return false;
    }

    private boolean fireShaftRocket() {
        int slot = ensureRocketInHotbar();
        if (slot < 0) return false;
        ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        autoOwnRocketFiredAtMs = System.currentTimeMillis();
        autoOwnRocketSeenAlive = false;
        return true;
    }

    private boolean shaftPivotPathOpen(int x0, int z0, int x1, int z1, int y0, int y1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        if (steps <= 0) return true;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int i = 1; i <= steps; i++) {
            int x = x0 + Math.round((x1 - x0) * (float) i / steps);
            int z = z0 + Math.round((z1 - z0) * (float) i / steps);
            if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return false;
            for (int y = y0; y <= y1; y++) {
                cursor.set(x, y, z);
                if (!isOpenState(mc.world.getBlockState(cursor), cursor)) return false;
            }
        }
        return true;
    }

    private BlockPos findShaftPivotEscape(int px, int feetY, int pz) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos best = null;
        int bestDistSq = Integer.MAX_VALUE;

        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq < 1 || distSq > 25 || distSq >= bestDistSq) continue;
                int x = px + dx, z = pz + dz;
                if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;

                cursor.set(x, feetY, z);
                if (!isOpenState(mc.world.getBlockState(cursor), cursor)) continue;
                cursor.set(x, feetY + 1, z);
                if (!isOpenState(mc.world.getBlockState(cursor), cursor)) continue;

                int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                if (top > feetY) continue;

                if (!shaftPivotPathOpen(px, pz, x, z, feetY, feetY + 1)) continue;

                bestDistSq = distSq;
                best = new BlockPos(x, feetY, z);
            }
        }

        return best;
    }

    private void tickShaftFly() {
        stateTicks++;

        if (stateTicks == 1) {
            autoShaftFlyYaw = mc.player.getYaw();
            autoShaftFlyOutcome = 0;
            autoShaftFlyRocketCount = 0;
            autoShaftFlyLastRocketTick = -100;
            autoShaftFlyLastRedeployTick = -100;
            autoShaftFlyJumpTapTicks = 0;
            autoShaftFlyStuck = false;
            autoShaftFlyStuckWindowY = mc.player.getY();
            autoShaftFlyStuckWindowTick = stateTicks;
            autoShaftFlyStuckSinceTick = 0;
            autoShaftFlySlowClimb = false;
            autoShaftFlyPivot = false;
            autoShaftFlyPivotX = 0;
            autoShaftFlyPivotZ = 0;
            autoShaftFlyPivotNoEscapeLogged = false;
            autoShaftFlyReroute = false;

            int slot = ensureRocketInHotbar();
            if (slot < 0) {
                releaseAllAutoKeys();
                enterState(AutoState.ASCEND, "no rockets for shaft fly");
                return;
            }

            takeoff.start(slot, new ElytraTakeoff.Listener() {
                @Override
                public void onTakeoffSuccess(double speedBps) {
                    autoShaftFlyOutcome = 1;
                }

                @Override
                public void onTakeoffFailed(String reason) {
                    autoShaftFlyOutcome = 2;
                    autoShaftFlyFailReason = reason;
                }

                @Override
                public void onTakeoffDebug(String message) {
                    autoLog("SHAFT_FLY", autoSite.center, "slot=" + slot + " " + message);
                    if (message.startsWith("Retry in ") && !mc.player.isGliding()) {
                        autoShaftFlyReroute = true;
                    }
                }
            });
        }

        mc.player.setYaw(autoShaftFlyYaw);
        mc.player.setPitch(-90.0f);

        if (autoHitThisTick && !mc.player.isGliding() && autoShaftFlyOutcome != 1) {
            if (takeoff.isActive()) takeoff.cancel();
            releaseAllAutoKeys();
            enterState(AutoState.SHAFT_ALIGN, "hit during takeoff");
            return;
        }

        if (stateTicks > 25 * 20) {
            if (takeoff.isActive()) takeoff.cancel();
            releaseAllAutoKeys();
            enterState(AutoState.ASCEND, "shaft fly timeout");
            return;
        }

        if (autoShaftFlyReroute) {
            autoShaftFlyReroute = false;
            if (takeoff.isActive()) takeoff.cancel();
            releaseAllAutoKeys();
            autoShaftFlyRerouteAttempts++;
            if (autoShaftFlyRerouteAttempts >= 5) {
                enterState(AutoState.ASCEND, "shaft fly retries exhausted");
            } else {
                enterState(AutoState.SHAFT_ALIGN, "shaft fly retry " + autoShaftFlyRerouteAttempts);
            }
            return;
        }

        if (autoShaftFlyOutcome == 2) {
            String reason = autoShaftFlyFailReason;
            autoShaftFlyOutcome = 0;
            releaseAllAutoKeys();
            enterState(AutoState.ASCEND, "shaft fly failed: " + reason);
            return;
        }

        boolean gliding = mc.player.isGliding();
        boolean deployedOrSuccess = autoShaftFlyOutcome == 1 || gliding;

        if (deployedOrSuccess) {
            double y = mc.player.getY();
            double vy = mc.player.getVelocity().y;
            boolean onGround = mc.player.isOnGround();

            if (y >= autoExitShaft.exitY() + 4) {
                mc.player.setPitch(-90.0f);
                autoLog("SHAFT_FLY", autoSite.center, "out of shaft");
                if (takeoff.isActive()) takeoff.cancel();
                releaseAllAutoKeys();
                autoClimbTargetY = autoExitShaft.exitY() + climbHeight.get();
                enterState(AutoState.CLIMB, "");
                return;
            }

            if (onGround) {
                if (takeoff.isActive()) takeoff.cancel();
                releaseAllAutoKeys();
                autoShaftFlyAttempts++;
                if (autoShaftFlyAttempts >= 2) {
                    enterState(AutoState.ASCEND, "shaft fly retries exhausted");
                } else {
                    enterState(AutoState.SHAFT_ALIGN, "shaft fly retry " + autoShaftFlyAttempts);
                }
                return;
            }

            if (stateTicks - autoShaftFlyStuckWindowTick >= 10) {
                boolean climbed = (y - autoShaftFlyStuckWindowY) > 0.2;
                autoShaftFlySlowClimb = gliding && !climbed;
                autoShaftFlyStuck = autoShaftFlySlowClimb && y < autoExitShaft.bottomY() + 4;
                autoShaftFlyStuckWindowY = y;
                autoShaftFlyStuckWindowTick = stateTicks;
                if (autoShaftFlyStuck && autoShaftFlyStuckSinceTick == 0) autoShaftFlyStuckSinceTick = stateTicks;
                if (!autoShaftFlyStuck) autoShaftFlyStuckSinceTick = 0;
            }

            if (autoShaftFlyStuck && gliding && vy > 0.0) {
                autoShaftFlyStuck = false;
                autoShaftFlyStuckSinceTick = 0;
            }

            int feetY = mc.player.getBlockPos().getY();
            int px = mc.player.getBlockPos().getX();
            int pz = mc.player.getBlockPos().getZ();

            if (autoShaftFlyPivot) {
                int topAbove = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, px, pz);
                if (topAbove <= feetY) {
                    autoShaftFlyPivot = false;
                    autoLog("SHAFT_FLY", autoSite.center, "pivot done");
                    autoShaftFlyStuck = false;
                    autoShaftFlyStuckSinceTick = 0;
                    autoShaftFlyStuckWindowY = y;
                    autoShaftFlyStuckWindowTick = stateTicks;
                }
            }

            if (!autoShaftFlyPivot) {
                boolean blockedAboveColumn = false;
                if (gliding && y >= autoExitShaft.exitY() - 1) {
                    BlockPos.Mutable aboveCursor = new BlockPos.Mutable();
                    boolean columnBlocked = false;
                    for (int dy = 2; dy <= 4; dy++) {
                        aboveCursor.set(px, feetY + dy, pz);
                        if (!isOpenState(mc.world.getBlockState(aboveCursor), aboveCursor)) { columnBlocked = true; break; }
                    }
                    blockedAboveColumn = columnBlocked || autoShaftFlySlowClimb;
                }

                if (blockedAboveColumn) {
                    if (!autoShaftFlyPivotNoEscapeLogged) {
                        BlockPos escape = findShaftPivotEscape(px, feetY, pz);
                        if (escape != null) {
                            autoShaftFlyPivot = true;
                            autoShaftFlyPivotX = escape.getX();
                            autoShaftFlyPivotZ = escape.getZ();
                            double dist = Math.sqrt((double) escape.getSquaredDistance(new BlockPos(px, feetY, pz)));
                            autoLog("SHAFT_FLY", autoSite.center, String.format("blocked above at y=%.1f pivot toward %d,%d dist=%.1f",
                                y, escape.getX(), escape.getZ(), dist));
                        } else {
                            autoShaftFlyPivotNoEscapeLogged = true;
                            autoLog("SHAFT_FLY", autoSite.center, "blocked above, no escape");
                        }
                    }
                } else {
                    autoShaftFlyPivotNoEscapeLogged = false;
                }
            }

            if (autoShaftFlyPivot) {
                double pdx = (autoShaftFlyPivotX + 0.5) - mc.player.getX();
                double pdz = (autoShaftFlyPivotZ + 0.5) - mc.player.getZ();
                mc.player.setYaw((float) Math.toDegrees(Math.atan2(-pdx, pdz)));
                mc.player.setPitch(-10.0f);

                double horizSpeed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
                if (gliding && horizSpeed < 0.3 && stateTicks - autoShaftFlyLastRocketTick >= 20 && !takeoff.isActive()) {
                    if (fireShaftRocket()) {
                        autoShaftFlyRocketCount++;
                        autoShaftFlyLastRocketTick = stateTicks;
                        autoLog("SHAFT_FLY", autoSite.center, "rocket #" + autoShaftFlyRocketCount + " y=" + String.format("%.1f", y));
                    }
                }
            } else {
                if (autoShaftFlyStuck) {
                    mc.player.setPitch(-80.0f);
                    double dx = (autoExitShaft.x() + 0.5) - mc.player.getX();
                    double dz = (autoExitShaft.z() + 0.5) - mc.player.getZ();
                    mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));

                    if (stateTicks - autoShaftFlyStuckSinceTick >= 60) {
                        autoLog("SHAFT_FLY", autoSite.center, "stuck under ceiling, falling back");
                        if (takeoff.isActive()) takeoff.cancel();
                        releaseAllAutoKeys();
                        autoShaftFlyStuck = false;
                        autoShaftFlyStuckSinceTick = 0;
                        autoShaftFlyAttempts++;
                        if (autoShaftFlyAttempts >= 2) {
                            enterState(AutoState.ASCEND, "shaft fly stuck retries exhausted");
                        } else {
                            enterState(AutoState.SHAFT_ALIGN, "shaft fly stuck retry " + autoShaftFlyAttempts);
                        }
                        return;
                    }
                } else {
                    mc.player.setPitch(-90.0f);
                }

                int rocketInterval = autoShaftFlyStuck ? 20 : 12;
                if (gliding && vy < 0.8 && stateTicks - autoShaftFlyLastRocketTick >= rocketInterval && !takeoff.isActive()) {
                    if (fireShaftRocket()) {
                        autoShaftFlyRocketCount++;
                        autoShaftFlyLastRocketTick = stateTicks;
                        autoLog("SHAFT_FLY", autoSite.center, "rocket #" + autoShaftFlyRocketCount + " y=" + String.format("%.1f", y));
                    }
                } else if (!gliding && !onGround && vy < -0.2
                    && autoShaftFlyJumpTapTicks <= 0 && stateTicks - autoShaftFlyLastRedeployTick >= 4) {
                    autoShaftFlyJumpTapTicks = 1;
                    autoShaftFlyLastRedeployTick = stateTicks;
                    autoLog("SHAFT_FLY", autoSite.center, String.format("redeploy y=%.1f vy=%.2f", y, vy));
                }
            }

            if (log.get() && stateTicks % 5 == 0) {
                HunterBuddyAddon.LOG.info(
                    "[SpawnerDetector/auto] SHAFT_FLY site={},{},{} y={} vy={} gliding={} onGround={} rockets={} health={} horizCollision={}",
                    autoSite.center.getX(), autoSite.center.getY(), autoSite.center.getZ(),
                    String.format("%.1f", y), String.format("%.2f", vy), gliding, onGround, autoShaftFlyRocketCount,
                    String.format("%.1f", mc.player.getHealth() + mc.player.getAbsorptionAmount()),
                    mc.player.horizontalCollision);
            }
        }

        if (!takeoff.isActive()) {
            if (autoShaftFlyJumpTapTicks > 0) {
                Utils.setPressed(mc.options.jumpKey, true);
                autoShaftFlyJumpTapTicks--;
            } else {
                Utils.setPressed(mc.options.jumpKey, false);
            }
        }
    }

    private void tickSwimUp() {
        stateTicks++;

        if (stateTicks > 60 * 20) {
            releaseAllAutoKeys();
            enterState(AutoState.ASCEND, "swim up timeout");
            return;
        }

        int swimUpX = autoExitShaft != null ? autoExitShaft.x() : autoColumn.x();
        int swimUpZ = autoExitShaft != null ? autoExitShaft.z() : autoColumn.z();
        int swimUpExitY = autoExitShaft != null ? autoExitShaft.exitY() : autoColumn.exitY();
        BlockPos swimUpEntry = autoExitShaft != null ? autoExitShaft.entry() : null;

        navigateExitColumn(swimUpX, swimUpZ, 0.2, swimUpEntry, true);

        if (mc.player.getAir() < 40 && stateTicks % 20 == 0) {
            autoLog("SWIM_UP", autoSite.center,
                String.format("low air y=%.1f air=%d", mc.player.getY(), mc.player.getAir()));
        }

        if (stateTicks % 20 == 0) {
            autoLog("SWIM_UP", autoSite.center, String.format("x=%.1f y=%.1f z=%.1f submerged=%b touchingWater=%b",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.isSubmergedInWater(), mc.player.isTouchingWater()));
        }

        boolean surfaced = !mc.player.isSubmergedInWater() && mc.player.getY() >= swimUpExitY - 1;
        if (surfaced) {
            releaseAllAutoKeys();
            autoLog("SWIM_UP", autoSite.center, String.format("surfaced x=%.1f z=%.1f", mc.player.getX(), mc.player.getZ()));
            if (!autoAreaLoaderWasOn) {
                enterState(AutoState.OBSIDIAN_PLATFORM, "no flight module, platform only");
            } else {
                autoWaterJumpAttempts = 0;
                autoWaterJumpVertical = false;
                enterState(AutoState.WATER_JUMP, "surfaced");
            }
            return;
        }

        if (autoAreaLoaderWasOn && stateTicks > 20 && !mc.player.isSubmergedInWater()
            && mc.player.getY() < swimUpExitY - 3
            && mc.player.getBlockX() == swimUpX && mc.player.getBlockZ() == swimUpZ
            && dryOpenColumn(swimUpX, swimUpZ, mc.player.getBlockPos().getY() + 2, swimUpExitY)) {
            releaseAllAutoKeys();
            autoWaterJumpAttempts = 0;
            autoWaterJumpVertical = true;
            enterState(AutoState.WATER_JUMP, "pool under dry shaft");
        }
    }

    private final Map<BlockPos, Boolean> monumentCache = new HashMap<>();

    private boolean nearOceanMonument(BlockPos center) {
        Boolean cached = monumentCache.get(center);
        if (cached != null) return cached;
        int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
        boolean allLoaded = true;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ccx + dx, ccz + dz);
                if (chunk == null) {
                    allLoaded = false;
                    continue;
                }
                for (ChunkSection section : chunk.getSectionArray()) {
                    if (section == null || section.isEmpty()) continue;
                    if (section.hasAny(st -> st.isOf(Blocks.DARK_PRISMARINE) || st.isOf(Blocks.PRISMARINE_BRICKS))) {
                        monumentCache.put(center, true);
                        return true;
                    }
                }
            }
        }
        if (allLoaded) monumentCache.put(center, false);
        return false;
    }

    private boolean dryOpenColumn(int x, int z, int fromY, int toY) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = fromY; y <= toY && y <= fromY + 320; y++) {
            cursor.set(x, y, z);
            BlockState state = mc.world.getBlockState(cursor);
            if (!isOpenState(state, cursor) || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private void tickWaterJump() {
        stateTicks++;

        if (stateTicks == 1) {
            autoWaterJumpTapPhase = 0;
        }

        mc.player.setPitch(autoWaterJumpVertical ? -90.0f : -60.0f);

        if (mc.player.isGliding()) {
            fireShaftRocket();
            releaseAllAutoKeys();
            autoLog("WATER_JUMP", autoSite.center,
                String.format("gliding, rocket fired x=%.1f z=%.1f", mc.player.getX(), mc.player.getZ()));
            autoClimbTargetY = mc.player.getY() + climbHeight.get();
            enterState(AutoState.CLIMB, "");
            return;
        }

        if (stateTicks > 40) {
            releaseAllAutoKeys();
            autoWaterJumpAttempts++;
            autoLog("WATER_JUMP", autoSite.center, String.format("attempt %d failed to glide x=%.1f z=%.1f",
                autoWaterJumpAttempts, mc.player.getX(), mc.player.getZ()));
            if (autoWaterJumpAttempts >= 3) {
                enterState(AutoState.OBSIDIAN_PLATFORM, "water jump attempts exhausted");
            } else {
                enterState(AutoState.WATER_JUMP, "retry " + autoWaterJumpAttempts);
            }
            return;
        }

        boolean airborne = !mc.player.isTouchingWater() && !mc.player.isOnGround();

        if (autoWaterJumpTapPhase == 0) {
            if (airborne) {
                Utils.setPressed(mc.options.jumpKey, false);
                autoWaterJumpTapPhase = 1;
            } else {
                Utils.setPressed(mc.options.jumpKey, true);
            }
        } else if (autoWaterJumpTapPhase == 1) {
            Utils.setPressed(mc.options.jumpKey, true);
            autoWaterJumpTapPhase = 2;
        }
    }

    private void tickClimb() {
        stateTicks++;

        if (stateTicks == 1) {
            autoClimbLastRocketTick = -100;
            autoClimbLastHeight = mc.player.getY();
            autoClimbStuckAttempts = 0;
            autoClimbStuck = false;
        }

        if (autoClimbStuck) {
            mc.player.setPitch(20.0f);
            if (stateTicks - autoClimbStuckStartTick >= 20 * 10) {
                releaseAllAutoKeys();
                enterState(AutoState.ASCEND, "climb stuck");
            } else if (!mc.player.isGliding()) {
                releaseAllAutoKeys();
                if (mc.player.isTouchingWater() && autoClimbStuckWaterReturns < 3) {
                    autoClimbStuckWaterReturns++;
                    enterState(AutoState.SWIM_UP, "climb stuck in water");
                } else {
                    enterState(AutoState.ASCEND, "climb stuck");
                }
            }
            return;
        }

        mc.player.setPitch(-90.0f);

        if (!mc.player.isGliding()) {
            autoLog("CLIMB", autoSite.center, "not gliding");
            enterState(AutoState.RESUME, "");
            return;
        }

        double y = mc.player.getY();
        double vy = mc.player.getVelocity().y;

        if (y >= autoClimbTargetY) {
            mc.player.setPitch(-35.0f);
            autoLog("CLIMB", autoSite.center, "climb done y=" + String.format("%.1f", y));
            enterState(AutoState.RESUME, "");
            return;
        }

        if (stateTicks > 20 * 20) {
            enterState(AutoState.RESUME, "climb timeout");
            return;
        }

        if (vy < 0.8 && stateTicks - autoClimbLastRocketTick >= 10 && !ownRocketActive()) {
            if (fireShaftRocket()) {
                autoClimbLastRocketTick = stateTicks;
                if (y - autoClimbLastHeight < 1.0) {
                    autoClimbStuckAttempts++;
                } else {
                    autoClimbStuckAttempts = 0;
                }
                autoClimbLastHeight = y;

                if (autoClimbStuckAttempts >= 3) {
                    autoLog("CLIMB", autoSite.center, String.format("climb stuck x=%.1f z=%.1f y=%.1f",
                        mc.player.getX(), mc.player.getZ(), y));
                    autoClimbStuck = true;
                    autoClimbStuckStartTick = stateTicks;
                    return;
                }
            }
        }

        if (log.get() && stateTicks % 20 == 0) {
            HunterBuddyAddon.LOG.info(
                "[SpawnerDetector/auto] CLIMB site={},{},{} x={} z={} y={} vy={} gliding={}",
                autoSite.center.getX(), autoSite.center.getY(), autoSite.center.getZ(),
                String.format("%.1f", mc.player.getX()), String.format("%.1f", mc.player.getZ()),
                String.format("%.1f", y), String.format("%.2f", vy), mc.player.isGliding());
        }
    }

    private int surfaceWaterY(int x, int z) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        cursor.set(x, topY, z);
        return mc.world.getFluidState(cursor).isIn(FluidTags.WATER) ? topY : Integer.MIN_VALUE;
    }

    private BlockPos findObsidianPlatformTarget(BlockPos feet, int w, Set<BlockPos> exclude) {
        float yaw = mc.player.getYaw();
        double lookX = -Math.sin(Math.toRadians(yaw));
        double lookZ = Math.cos(Math.toRadians(yaw));

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        BlockPos best = null;
        double bestDot = -Double.MAX_VALUE;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int[] d : dirs) {
            BlockPos p = new BlockPos(feet.getX() + d[0], w, feet.getZ() + d[1]);
            if (exclude.contains(p)) continue;

            BlockState state = mc.world.getBlockState(p);
            boolean waterOrAir = state.getFluidState().isIn(FluidTags.WATER) || state.isAir();
            if (!waterOrAir) continue;

            cursor.set(p.getX(), p.getY() + 1, p.getZ());
            if (!mc.world.getBlockState(cursor).isAir()) continue;
            cursor.set(p.getX(), p.getY() + 2, p.getZ());
            if (!mc.world.getBlockState(cursor).isAir()) continue;

            double dot = d[0] * lookX + d[1] * lookZ;
            if (dot > bestDot) {
                bestDot = dot;
                best = p;
            }
        }

        return best;
    }

    private int ensureObsidianInHotbar() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (hotbar.found() && !hotbar.isOffhand()) return hotbar.slot();

        FindItemResult inv = InvUtils.find(Items.OBSIDIAN);
        if (!inv.found()) return -1;

        int target = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) { target = i; break; }
        }
        if (target < 0) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (!s.isIn(ItemTags.PICKAXES) && s.getItem() != Items.FIREWORK_ROCKET) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) return -1;

        InvUtils.move().from(inv.slot()).to(target);
        return target;
    }

    private void obsidianPlatformFailed(String reason) {
        autoLog("OBSIDIAN_PLATFORM", autoSite.center, "abandoning: " + reason);
        if (findAscendTakeoffPoints(autoColumn.x(), autoColumn.z(), 48).isEmpty()) {
            fail("stuck in ocean");
        } else {
            autoAscendFromAbort = true;
            enterState(AutoState.ASCEND, reason);
        }
    }

    private void tickObsidianPlatform() {
        stateTicks++;

        if (stateTicks == 1) {
            autoObsidianAttempts = 0;
            autoObsidianTried = new java.util.HashSet<>();
            autoObsidianPhase = 0;
        }

        if (autoObsidianPhase == 0) {
            int slot = ensureObsidianInHotbar();
            if (slot < 0) {
                releaseAllAutoKeys();
                obsidianPlatformFailed("no obsidian for ocean exit");
                return;
            }
            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);

            BlockPos feet = mc.player.getBlockPos();
            int w = surfaceWaterY(feet.getX(), feet.getZ());
            if (w == Integer.MIN_VALUE) {
                releaseAllAutoKeys();
                obsidianPlatformFailed("no surface water found");
                return;
            }

            BlockPos target = findObsidianPlatformTarget(feet, w, autoObsidianTried);
            if (target == null) {
                releaseAllAutoKeys();
                obsidianPlatformFailed("no platform spot");
                return;
            }

            autoObsidianTarget = target;
            autoObsidianWaitTicks = 0;
            autoObsidianPhase = 1;
            return;
        }

        if (autoObsidianPhase == 1) {
            Vec3d center = Vec3d.ofCenter(autoObsidianTarget);
            if (alignedTowards(center)) {
                PlacementUtils.grimPlace(new BlockHitResult(center, Direction.UP, autoObsidianTarget, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                autoObsidianWaitTicks = 0;
                autoObsidianPhase = 2;
            }
            return;
        }

        if (autoObsidianPhase == 2) {
            if (mc.world.getBlockState(autoObsidianTarget).isOf(Blocks.OBSIDIAN)) {
                autoLog("OBSIDIAN_PLATFORM", autoSite.center, "placed " + autoObsidianTarget.getX() + ","
                    + autoObsidianTarget.getY() + "," + autoObsidianTarget.getZ());
                autoObsidianClimbStartMs = System.currentTimeMillis();
                autoObsidianPhase = 3;
                return;
            }

            autoObsidianWaitTicks++;
            if (autoObsidianWaitTicks >= 10) {
                autoObsidianAttempts++;
                autoObsidianTried.add(autoObsidianTarget);
                autoLog("OBSIDIAN_PLATFORM", autoSite.center, "attempt " + autoObsidianAttempts
                    + " failed at " + autoObsidianTarget.getX() + "," + autoObsidianTarget.getY() + "," + autoObsidianTarget.getZ());
                if (autoObsidianAttempts >= 3) {
                    releaseAllAutoKeys();
                    obsidianPlatformFailed("obsidian placement failed");
                } else {
                    autoObsidianPhase = 0;
                }
            }
            return;
        }

        if (autoObsidianPhase == 3) {
            BlockPos target = autoObsidianTarget;
            float yaw = (float) Math.toDegrees(Math.atan2(
                -((target.getX() + 0.5) - mc.player.getX()), (target.getZ() + 0.5) - mc.player.getZ()));
            mc.player.setYaw(yaw);
            Utils.setPressed(mc.options.forwardKey, true);
            Utils.setPressed(mc.options.jumpKey, true);

            boolean onTarget = mc.player.isOnGround()
                && mc.player.getBlockPos().getY() == target.getY() + 1
                && !mc.player.isTouchingWater();

            if (onTarget) {
                releaseAllAutoKeys();
                if (!autoAreaLoaderWasOn) {
                    enterState(AutoState.RESUME, "staying on foot");
                } else {
                    enterState(AutoState.TAKEOFF, "");
                }
                return;
            }

            if (System.currentTimeMillis() - autoObsidianClimbStartMs > 6000L) {
                releaseAllAutoKeys();
                obsidianPlatformFailed("obsidian climb timeout");
            }
        }
    }

    private boolean isClearTakeoffGround(int x, int z, int groundY) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int nx = x + dx, nz = z + dz;
                if (!mc.world.getChunkManager().isChunkLoaded(nx >> 4, nz >> 4)) return false;
                int ng = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, nx, nz);
                if (ng > groundY + 1) return false;
                for (int dy = 1; dy <= 2; dy++) {
                    cursor.set(nx, groundY + dy, nz);
                    if (!isOpenState(mc.world.getBlockState(cursor), cursor)) return false;
                }
            }
        }
        return true;
    }

    private List<BlockPos> findAscendTakeoffPoints(int cx, int cz, int radius) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;
                int x = cx + dx, z = cz + dz;
                int groundY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);

                cursor.set(x, groundY - 1, z);
                BlockState belowState = mc.world.getBlockState(cursor);
                if (belowState.getCollisionShape(mc.world, cursor).isEmpty()) continue;
                if (!belowState.getFluidState().isEmpty()) continue;
                if (belowState.isIn(BlockTags.LEAVES)) continue;

                cursor.set(x, groundY, z);
                if (mc.world.getFluidState(cursor).isIn(FluidTags.WATER)) continue;

                boolean open = true;
                for (int dy = 0; dy < 12 && open; dy++) {
                    cursor.set(x, groundY + dy, z);
                    if (!isOpenState(mc.world.getBlockState(cursor), cursor)) open = false;
                }
                if (open && isClearTakeoffGround(x, z, groundY)) candidates.add(new BlockPos(x, groundY, z));
            }
        }

        candidates.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getBlockPos())));
        return new ArrayList<>(candidates.subList(0, Math.min(12, candidates.size())));
    }

    private Goal buildAscendGoal() {
        if (autoAscendTargets.isEmpty()) {
            return new GoalBlock(new BlockPos(autoColumn.x(), autoColumn.exitY(), autoColumn.z()));
        }
        Goal[] goals = new Goal[autoAscendTargets.size()];
        for (int i = 0; i < autoAscendTargets.size(); i++) goals[i] = new GoalBlock(autoAscendTargets.get(i));
        return new GoalComposite(goals);
    }

    private void tickAscend() {
        stateTicks++;

        if (stateTicks == 1) {
            BaritoneAPI.getSettings().allowPlace.value = true;
            autoBaritoneRetries = 0;
            int ascendRadius = autoColumn.water() ? 48 : 16;
            autoAscendTargets = findAscendTakeoffPoints(mc.player.getBlockX(), mc.player.getBlockZ(), ascendRadius);
            BlockPos nearest = autoAscendTargets.isEmpty() ? null : autoAscendTargets.get(0);
            autoLog("ASCEND", autoSite.center, "takeoff points found=" + autoAscendTargets.size()
                + (nearest != null ? " nearest=" + nearest.getX() + "," + nearest.getY() + "," + nearest.getZ() : ""));
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(buildAscendGoal());
        }

        if (stateTicks > 120 * 20) { fail("ascend timeout"); return; }

        if (autoAreaLoaderWasOn && mc.player.isTouchingWater() && !mc.player.isSubmergedInWater() && isClearSkyHere()) {
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
            IBaritone surfacedBt = primaryBaritone();
            if (surfacedBt != null) surfacedBt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            autoWaterJumpAttempts = 0;
            autoWaterJumpVertical = false;
            enterState(AutoState.WATER_JUMP, "surfaced during ascend");
            return;
        }

        boolean arrived = false;
        BlockPos arrivedAt = null;

        if (!autoAscendTargets.isEmpty()) {
            BlockPos feet = mc.player.getBlockPos();
            for (BlockPos p : autoAscendTargets) {
                double dxz = Math.hypot((p.getX() + 0.5) - mc.player.getX(), (p.getZ() + 0.5) - mc.player.getZ());
                double dy = Math.abs(mc.player.getY() - p.getY());
                if (dxz <= 1.0 && dy <= 1.0) {
                    arrived = mc.player.isOnGround() && !hasBlockAbove(feet.getX(), feet.getY(), feet.getZ(), SKY_CHECK_HEIGHT);
                    arrivedAt = p;
                    break;
                }
            }
        } else {
            double dxz = Math.hypot((autoColumn.x() + 0.5) - mc.player.getX(), (autoColumn.z() + 0.5) - mc.player.getZ());
            double dy = Math.abs(mc.player.getY() - autoColumn.exitY());
            arrived = dxz <= 1.0 && dy <= 1.0 && mc.player.isOnGround();
            arrivedAt = new BlockPos(autoColumn.x(), autoColumn.exitY(), autoColumn.z());
        }

        if (arrived) {
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
            autoLog("ASCEND", autoSite.center, "reached " + arrivedAt.getX() + "," + arrivedAt.getY() + "," + arrivedAt.getZ());

            if (!autoAscendFromAbort && tryChainWalk(autoSite)) return;

            if (!autoAreaLoaderWasOn) {
                enterState(AutoState.RESUME, "staying on foot");
                return;
            }

            if (!autoAscendTargets.isEmpty()) {
                enterState(AutoState.TAKEOFF, "");
                return;
            }

            boolean blocked = autoColumn.skyBlocked() || hasBlockAbove(autoColumn.x(), autoColumn.exitY(), autoColumn.z(), SKY_CHECK_HEIGHT);
            if (blocked) enterState(AutoState.TAKEOFF_SPOT, "");
            else enterState(AutoState.TAKEOFF, "");
            return;
        }

        IBaritone bt = primaryBaritone();
        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
        if (!pathing && stateTicks > 20 && autoBaritoneRetries < 5) {
            autoBaritoneRetries++;
            if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(buildAscendGoal());
        }

        logNavTelemetry("ASCEND", autoAscendTargets.isEmpty()
            ? new BlockPos(autoColumn.x(), autoColumn.exitY(), autoColumn.z())
            : autoAscendTargets.get(0), autoBaritoneRetries);
    }

    private BlockPos findTakeoffSpot() {
        int cx = mc.player.getBlockX(), cz = mc.player.getBlockZ();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq > 256 || distSq >= bestDistSq) continue;
                int x = cx + dx, z = cz + dz;
                int groundY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                boolean open = true;
                for (int dy = 0; dy < 12 && open; dy++) {
                    cursor.set(x, groundY + dy, z);
                    if (!isOpenState(mc.world.getBlockState(cursor), cursor)) open = false;
                }
                if (!open) continue;
                if (!isClearTakeoffGround(x, z, groundY)) continue;
                best = new BlockPos(x, groundY, z);
                bestDistSq = distSq;
            }
        }

        return best;
    }

    private void tickTakeoffSpot() {
        stateTicks++;

        if (stateTicks == 1) {
            BlockPos spot = findTakeoffSpot();
            if (spot == null) { fail("no takeoff spot"); return; }
            autoTakeoffSpot = spot;
            autoLog("TAKEOFF_SPOT", autoSite.center, String.format("spot=%d,%d,%d dist=%.1f",
                spot.getX(), spot.getY(), spot.getZ(),
                Math.hypot((spot.getX() + 0.5) - mc.player.getX(), (spot.getZ() + 0.5) - mc.player.getZ())));
            boolean alreadyThere = mc.player.getBlockX() == spot.getX() && mc.player.getBlockZ() == spot.getZ()
                && Math.abs(mc.player.getY() - spot.getY()) <= 1.5;
            IBaritone bt = primaryBaritone();
            if (bt != null && !alreadyThere) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(spot));
        }

        if (stateTicks > 30 * 20) { fail("takeoff spot timeout"); return; }

        double dist = Math.hypot((autoTakeoffSpot.getX() + 0.5) - mc.player.getX(), (autoTakeoffSpot.getZ() + 0.5) - mc.player.getZ());
        if (dist <= 1.0 && Math.abs(mc.player.getY() - autoTakeoffSpot.getY()) <= 1.5 && mc.player.isOnGround()) {
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            enterState(AutoState.TAKEOFF, "");
            return;
        }

        logNavTelemetry("TAKEOFF_SPOT", autoTakeoffSpot, 0);
    }

    private int ensureRocketInHotbar() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (hotbar.found() && !hotbar.isOffhand()) return hotbar.slot();

        FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
        if (!inv.found()) return -1;

        int target = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) { target = i; break; }
        }
        if (target < 0) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (!s.isIn(ItemTags.PICKAXES) && !s.isIn(ItemTags.SWORDS)
                    && s.get(DataComponentTypes.FOOD) == null) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) return -1;

        InvUtils.move().from(inv.slot()).to(target);
        return target;
    }

    private void tickTakeoff() {
        stateTicks++;

        if (stateTicks == 1) {
            autoTakeoffYaw = mc.player.getYaw();
            int slot = ensureRocketInHotbar();
            if (slot < 0) { fail("no rockets for takeoff"); return; }
            autoTakeoffOutcome = 0;
            takeoff.start(slot, new ElytraTakeoff.Listener() {
                @Override
                public void onTakeoffSuccess(double speedBps) {
                    autoTakeoffOutcome = 1;
                }

                @Override
                public void onTakeoffFailed(String reason) {
                    autoTakeoffOutcome = 2;
                    autoTakeoffFailReason = reason;
                }

                @Override
                public void onTakeoffDebug(String message) {
                    autoLog("TAKEOFF", autoSite.center, "slot=" + slot + " " + message);
                }
            });
        }

        mc.player.setYaw(autoTakeoffYaw);
        mc.player.setPitch(-60.0f);

        if (stateTicks > 40 * 20) {
            if (takeoff.isActive()) takeoff.cancel();
            fail("takeoff timeout");
            return;
        }

        if (autoTakeoffOutcome == 1) {
            autoTakeoffOutcome = 0;
            autoClimbTargetY = mc.player.getY() + climbHeight.get();
            enterState(AutoState.CLIMB, "takeoff done");
        } else if (autoTakeoffOutcome == 2) {
            String r = autoTakeoffFailReason;
            autoTakeoffOutcome = 0;
            fail("takeoff failed: " + r);
        }
    }

    private String autoRunOutcome() {
        return autoRoomFoundShulkers > 0 ? "shulkers:" + autoRoomFoundShulkers : "empty";
    }

    private boolean isClearSkyHere() {
        BlockPos feet = mc.player.getBlockPos();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!mc.world.getChunkManager().isChunkLoaded((feet.getX() + dx) >> 4, (feet.getZ() + dz) >> 4)) return false;
                for (int dy = 2; dy <= 8; dy++) {
                    cursor.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    if (!isOpenState(mc.world.getBlockState(cursor), cursor)) return false;
                }
            }
        }
        return true;
    }

    private void tickResume() {
        stateTicks++;

        if (stateTicks == 1 && autoAreaLoaderWasOn && !isClearSkyHere()) {
            releaseAllAutoKeys();
            if (mc.player.isGliding() && autoResumeClimbTries < 3) {
                autoResumeClimbTries++;
                autoClimbTargetY = mc.player.getY() + climbHeight.get();
                enterState(AutoState.CLIMB, "not clear here, climbing before AreaLoader");
            } else {
                enterState(AutoState.ASCEND, "not clear here, AreaLoader not re-enabled");
            }
            return;
        }

        if (stateTicks == 1) {
            if (autoAreaLoaderWasOn) {
                Module fm = selectedFlightModule();
                if (fm != null && !fm.isActive()) {
                    fm.toggle();
                    autoLog("RESUME", autoSite.center, "re-enabled " + fm.name);
                }
            }
            return;
        }

        if (stateTicks == 2) {
            if (autoPitch40WasOn) {
                Pitch40 p40 = pitch40Module();
                if (p40 != null && !p40.isActive()) {
                    p40.toggle();
                    autoLog("RESUME", autoSite.center, "re-enabled Pitch40");
                }
            }
            if (autoElytraRecastWasOn) {
                ElytraRecast er = elytraRecastModule();
                if (er != null && !er.isActive()) {
                    er.toggle();
                    autoLog("RESUME", autoSite.center, "re-enabled ElytraRecast");
                }
            }
        }

        if (stateTicks >= 2) {
            BlockPos finished = autoSite.center;
            if (!autoRunAbandoned) {
                markVisited(finished, autoRunOutcome());
                statRoomsFarmed++;
            }
            autoLog("IDLE", finished, "run complete");
            resetAutoState();
        }
    }

    private void abort(String reason) {
        if (state == AutoState.SWIM_UP || state == AutoState.WATER_JUMP || state == AutoState.CLIMB
            || state == AutoState.OBSIDIAN_PLATFORM
            || state == AutoState.ASCEND || state == AutoState.TAKEOFF_SPOT || state == AutoState.TAKEOFF) {
            autoLog("ABORT", autoSite.center, "state=" + state + " reason=" + reason + " (ignored, already exiting) player=" + playerPosLabel());
            return;
        }

        AutoState from = state;
        autoLog("ABORT", autoSite.center, "state=" + from + " reason=" + reason + " player=" + playerPosLabel());

        BaritoneHelper.stopAllPathing();
        releaseAllAutoKeys();
        markVisited(autoSite.center, "aborted:" + reason);
        autoRunAbandoned = true;

        boolean gliding = mc.player.isGliding();
        int feetY = mc.player.getBlockPos().getY();

        if (!gliding && (mc.player.isTouchingWater() || autoColumn.water())) {
            if (mc.player.isSubmergedInWater() || feetY < autoColumn.exitY() - 1) {
                autoAscendFromAbort = true;
                enterAscendOrSwimUp("from ABORT (" + reason + ")");
            } else {
                autoAscendFromAbort = true;
                if (!autoAreaLoaderWasOn) {
                    enterState(AutoState.OBSIDIAN_PLATFORM, "no flight module, platform only");
                } else {
                    autoWaterJumpAttempts = 0;
                    autoWaterJumpVertical = false;
                    enterState(AutoState.WATER_JUMP, "from ABORT (" + reason + ")");
                }
            }
        } else if (gliding) {
            enterState(AutoState.RESUME, "from ABORT (" + reason + ")");
        } else if (feetY >= autoColumn.exitY() - 1) {
            if (!autoAreaLoaderWasOn) {
                enterState(AutoState.RESUME, "staying on foot");
            } else {
                boolean blocked = autoColumn.skyBlocked()
                    || hasBlockAbove(mc.player.getBlockPos().getX(), feetY, mc.player.getBlockPos().getZ(), SKY_CHECK_HEIGHT);
                if (blocked) enterState(AutoState.TAKEOFF_SPOT, "from ABORT (" + reason + ")");
                else enterState(AutoState.TAKEOFF, "from ABORT (" + reason + ")");
            }
        } else {
            autoAscendFromAbort = true;
            enterState(AutoState.ASCEND, "from ABORT (" + reason + ")");
        }
    }

    private void fail(String reason) {
        if (state == AutoState.FAILED) return;

        if (autoSite != null) {
            autoLog("FAILED", autoSite.center, "reason=" + reason + " player=" + playerPosLabel());
            markVisited(autoSite.center, "failed:" + reason);
            autoRunAbandoned = true;
        } else {
            if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] FAILED reason={} player={}", reason, playerPosLabel());
        }
        error("SpawnerDetector auto-farm failed: " + reason);

        releaseAllAutoKeys();
        if (takeoff.isActive()) takeoff.cancel();
        BaritoneHelper.stopAllPathing();
        BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;

        state = AutoState.FAILED;
        stateTicks = 0;
    }

    private SettingColor colorOf(Category category) {
        return switch (category) {
            case DUNGEON -> lootedColor.get();
            case EMPTIED -> emptyColor.get();
            case STASH -> stashColor.get();
            case LIT -> torchColor.get();
            case TRIAL -> trialColor.get();
            case INTACT -> intactColor.get();
            case EMPTY -> emptyCatColor.get();
        };
    }

    public enum Category {
        STASH("Stash"),
        DUNGEON("Dungeon"),
        LIT("Lit"),
        EMPTIED("Emptied"),
        TRIAL("Trial"),
        INTACT("Intact"),
        EMPTY("Empty");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private record Detection(Category category, String mob, int chests, Animation animation) {
    }

    private record PendingSite(BlockPos center, boolean hasSpawner, String mob, Room room) {}

    public record Room(int cx, int cy, int cz, int k, int p,
                        int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                        boolean modern, double fit) {}

    public static final class Site {
        public final BlockPos center;
        public final Room room;
        public final boolean hasSpawner;
        public final String mob;
        public final boolean modern;

        public int containers;
        public boolean light;
        public String lightLabel;
        public BlockPos lightPos;
        public boolean ender;
        public boolean playerBlocks;
        public Set<String> playerBlockTypes = Set.of();
        public boolean broken;
        public boolean mobs;
        public int mobCount;
        public boolean tunnel;
        public DugHoles.Feature tunnelFeature;
        public boolean air;
        public int airCount;
        public int score;
        public Category category;
        public List<BlockPos> roomContainers = List.of();
        public long discoveredAt;
        public long discoveredAtTick;

        Animation animation;

        Site(BlockPos center, Room room, boolean hasSpawner, String mob, boolean modern) {
            this.center = center;
            this.room = room;
            this.hasSpawner = hasSpawner;
            this.mob = mob;
            this.modern = modern;
        }
    }

    private static final class Animation {
        private final long duration;
        private boolean visible = false;
        private long changedAt = System.currentTimeMillis();

        Animation(long duration) {
            this.duration = duration;
        }

        void setVisible(boolean visible) {
            if (this.visible != visible) {
                this.visible = visible;
                this.changedAt = System.currentTimeMillis();
            }
        }

        float factor() {
            if (duration <= 0) return visible ? 1.0f : 0.0f;

            float progress = Math.min(1.0f, (System.currentTimeMillis() - changedAt) / (float) duration);
            return visible ? progress : 1.0f - progress;
        }
    }
}
