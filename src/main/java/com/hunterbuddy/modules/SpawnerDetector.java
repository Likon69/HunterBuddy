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
import com.hunterbuddy.util.ShipwreckDetector;
import com.hunterbuddy.util.ShipwreckSite;
import com.hunterbuddy.util.ShipwreckRun;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalNear;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.block.AbstractRailBlock;
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
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvent;
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
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.PaletteNewChunks;

public class SpawnerDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRoomShape = settings.createGroup("Room shape");
    private final SettingGroup sgScore = settings.createGroup("Score");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgAuto = settings.createGroup("Auto farm");
    private final SettingGroup sgSounds = settings.createGroup("Sounds");

    private final Setting<Boolean> detectTorches = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-torches")
        .description("Compute the light signal (whitelist of placed light sources) around a site.")
        .defaultValue(true).build());

    private final Setting<Boolean> detectShipwrecks = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-shipwrecks")
        .description("Detect shipwrecks and farm their chests with auto-farm.")
        .defaultValue(false).build());

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

    private final Setting<Integer> roomFloorMin = sgRoomShape.add(new IntSetting.Builder()
        .name("room-floor-min")
        .description("Minimum cobblestone or mossy cobblestone floor coverage for room shape matching, in percent.")
        .defaultValue(50).min(0).max(100).sliderRange(0, 100).build());

    private final Setting<Integer> roomMossyMin = sgRoomShape.add(new IntSetting.Builder()
        .name("room-mossy-min")
        .description("Minimum mossy cobblestone count for room shape matching at y 63 and above.")
        .defaultValue(3).min(0).max(16).sliderRange(0, 10).build());

    private final Setting<Integer> roomInteriorMin = sgRoomShape.add(new IntSetting.Builder()
        .name("room-interior-min")
        .description("Minimum open or replaceable room interior coverage for room shape matching, in percent.")
        .defaultValue(60).min(0).max(100).sliderRange(0, 100).build());

    private final Setting<Integer> roomWallsMin = sgRoomShape.add(new IntSetting.Builder()
        .name("room-walls-min")
        .description("Minimum cobblestone wall coverage for room shape matching, in percent.")
        .defaultValue(35).min(0).max(100).sliderRange(0, 100).build());

    private final Setting<Integer> roomCeilingMin = sgRoomShape.add(new IntSetting.Builder()
        .name("room-ceiling-min")
        .description("Minimum closed ceiling coverage for spawnerless room shape matching, in percent.")
        .defaultValue(60).min(0).max(100).sliderRange(0, 100).build());

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

    private final Setting<Integer> weightFilled = sgScore.add(new IntSetting.Builder()
        .name("weight-filled")
        .description("Score weight of the filled-shaft signal.")
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

    private final Setting<Boolean> dynamicScale = sgRender.add(new BoolSetting.Builder()
        .name("dynamic-scale").description("Grow the label progressively with distance, from label-scale up close to dynamic-max-scale at the render distance.")
        .defaultValue(true).build());

    private final Setting<Double> dynamicMaxScale = sgRender.add(new DoubleSetting.Builder()
        .name("dynamic-max-scale").description("Label size reached at the render distance when dynamic-scale is on.")
        .defaultValue(2.0).min(0.3).sliderRange(0.3, 3.0).visible(dynamicScale::get).build());

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

    public enum LootedStyle { DIM, CROSS, HIDE }

    private final Setting<LootedStyle> lootedStyle = sgRender.add(new EnumSetting.Builder<LootedStyle>()
        .name("looted-style")
        .description("How a room already farmed with success is drawn.")
        .defaultValue(LootedStyle.DIM).build());

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

    private final Setting<SettingColor> farmedColor = sgColors.add(new ColorSetting.Builder()
        .name("looted-color").description("Box color for a room already farmed with success (dim/cross looted-style).")
        .defaultValue(new SettingColor(150, 150, 150, 90)).build());

    private final Setting<SettingColor> shipwreckColor = sgColors.add(new ColorSetting.Builder()
        .name("shipwreck-color").description("Detected shipwrecks and chests that have not been looted.")
        .defaultValue(new SettingColor(80, 180, 255, 200)).build());

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
        .defaultValue(256).min(32).max(1000).sliderRange(32, 1000).build());

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
        .defaultValue(8).min(3).sliderRange(3, 200).build());

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

    public enum FlightModule { AreaLoader, RocketFly, WaypointFollower, TrailFollower, Fly300km, None }

    private final Setting<FlightModule> flightModule = sgAuto.add(new EnumSetting.Builder<FlightModule>()
        .name("flight-module")
        .description("Module switched off while a dungeon is farmed and switched back on once the bot has climbed out. None takes off and leaves you gliding.")
        .defaultValue(FlightModule.AreaLoader).build());

    private final Setting<Integer> climbHeight = sgAuto.add(new IntSetting.Builder()
        .name("climb-height")
        .description("How many blocks to climb with rockets after leaving the hole, before handing over to the flight module.")
        .defaultValue(100).min(0).max(300).sliderRange(0, 300).build());

    private final Setting<Integer> soloCruiseRocketInterval = sgAuto.add(new IntSetting.Builder()
        .name("solo-cruise-rocket-interval")
        .description("Minimum ticks between cruise rockets in solo mode.")
        .defaultValue(60).min(20).max(200).sliderRange(20, 120).build());

    private final Setting<Integer> soloCruiseStuckTime = sgAuto.add(new IntSetting.Builder()
        .name("solo-cruise-stuck-time")
        .description("Seconds without meaningful cruise progress before a solo target is skipped.")
        .defaultValue(20).min(5).max(120).sliderRange(5, 60).build());

    private final Setting<Boolean> autoChainUnderground = sgAuto.add(new BoolSetting.Builder()
        .name("auto-chain-underground")
        .description("Walk underground to a qualifying neighbour room instead of ascending and re-diving, when one is close enough.")
        .defaultValue(true).build());

    private final Setting<Boolean> soundFound = sgSounds.add(new BoolSetting.Builder()
        .name("sound-found")
        .description("Play a sound when a room worth farming is found.")
        .defaultValue(true).build());

    private final Setting<Boolean> soundFailed = sgSounds.add(new BoolSetting.Builder()
        .name("sound-failed")
        .description("Play a sound when a room run is aborted or fails.")
        .defaultValue(true).build());

    private final Setting<Boolean> soundShulker = sgSounds.add(new BoolSetting.Builder()
        .name("sound-shulker")
        .description("Play a sound when a chest holds full shulkers.")
        .defaultValue(true).build());

    private final Setting<Double> soundVolume = sgSounds.add(new DoubleSetting.Builder()
        .name("sound-volume")
        .description("Volume of SpawnerDetector sounds.")
        .defaultValue(0.8)
        .min(0.0)
        .sliderRange(0.0, 1.0)
        .build());

    private final Set<BlockPos> trialPending = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, Detection> trialFound = new ConcurrentHashMap<>();

    private final Set<BlockPos> rejected = ConcurrentHashMap.newKeySet();
    private final Set<Long> mossyScanned = ConcurrentHashMap.newKeySet();
    private int mossyScanCursor = 0;
    private final Set<BlockPos> spawnerlessWaiting = ConcurrentHashMap.newKeySet();
    private final Map<Long, List<BlockPos>> mossyByChunk = new ConcurrentHashMap<>();
    private final Map<BlockPos, Site> sites = new LinkedHashMap<>();
    private final Map<BlockPos, PendingSite> pendingEval = new LinkedHashMap<>();
    private final Set<BlockPos> approached = new java.util.HashSet<>();
    private final Set<BlockPos> sideHoleLogged = new java.util.HashSet<>();
    private final Set<BlockPos> sideHoleUseLogged = new java.util.HashSet<>();
    private final Set<BlockPos> entryShaftLogged = new java.util.HashSet<>();
    private final Set<BlockPos> noSafeColumnLogged = new java.util.HashSet<>();
    private final Set<BlockPos> lowGlideLogged = new java.util.HashSet<>();
    private final Set<BlockPos> floatingBlockLogged = new java.util.HashSet<>();
    private final Set<BlockPos> lidBlockLogged = new java.util.HashSet<>();
    private final Set<BlockPos> shaftPivotFailureLogged = new java.util.HashSet<>();
    private final Set<BlockPos> jungleTempleSkipLogged = new java.util.HashSet<>();
    private final Set<BlockPos> newChunkSkipLogged = new java.util.HashSet<>();
    private final Set<BlockPos> autoAbandonedOnce = new java.util.HashSet<>();
    private final Set<BlockPos> autoSoloRejectedThisSession = new java.util.HashSet<>();
    private final Set<BlockPos> autoSoundedSites = new java.util.HashSet<>();
    private final Map<String, Long> autoSoundLastPlayed = new HashMap<>();

    private long totalTicks;
    private int scanCooldown;
    private ClientWorld lastWorld;

    private enum AutoState {
        IDLE, PREPARE, APPROACH, FINAL, WALK, DIG, SIDE_DIG, ROOM, ROOM_CLIMB, TUNNEL, EXIT, SHAFT_ALIGN, SHAFT_FLY,
        SWIM_UP, WATER_JUMP, CLIMB, OBSIDIAN_PLATFORM, ASCEND, SELF_ASCEND, TAKEOFF_SPOT, TAKEOFF, SOLO_CRUISE, RESUME, FAILED
    }

    private enum ShaftDryPhase {
        NONE, UNDER_JUMP, SIDE_PLACE, SIDE_MOVE, BRIDGE_PLACE, SHAFT_MOVE
    }

    private record ColumnPlan(int x, int z, int startY, int exitY, boolean water,
                               int blocksToBreak, int waterCells, int maxGap, boolean skyBlocked,
                               int holeTopY, BlockPos rim, boolean side, BlockPos sideEntry) {}

    private record ColumnEval(ColumnPlan plan, String rejectReason) {}

    private record ColumnReject(int x, int z, String reason) {}

    private record ExitShaft(int x, int z, int bottomY, int exitY, boolean water, boolean skyBlocked, BlockPos entry) {}

    private record RimResult(BlockPos rim, int holeTopY) {}

    private record SideHoleReject(int x, int z, int bottomFeet, String reason) {}

    private record SideHoleCost(int bottomFeet, int tx, int tz, int cost, int vertCost, String rejectReason) {}

    private record SideHoleAttempt(ColumnPlan plan, String rejectReason) {}

    private record QualResult(String reject, ColumnPlan plan) {
        static QualResult ok(ColumnPlan plan) { return new QualResult(null, plan); }
        static QualResult reject(String reason) { return new QualResult(reason, null); }
    }

    private record VisitedEntry(int x, int y, int z, String outcome, long time) {}

    private ShipwreckRun autoShipwreckRun;
    private long autoShipwreckDigProgressMs;
    private double autoShipwreckDigProgressY;
    private int autoShipwreckDigProgressMined;
    private BlockPos autoShipwreckNextChest;
    private BlockPos autoShipwreckEntryColumn;
    private BlockPos autoShipwreckEmergencyGoal;
    private long autoShipwreckEmergencySearchMs;
    private long autoShipwreckEmergencyGoalMs;
    private boolean autoShipwreckPlatformTried;
    private long autoShipwreckPlatformStartedMs;
    private float autoShipwreckEscapeYaw;
    private String autoShipwreckChestOutcome;
    private boolean autoShipwreckDeferred;
    private final Set<BlockPos> autoShipwreckFailedExits = new HashSet<>();
    private List<Block> autoShipwreckDisallowedBreaking;
    private final Map<ShipwreckSite, Animation> shipwreckAnimations = new HashMap<>();

    private static final long AUTO_NO_PROGRESS_MS = 180_000L;
    private static final long AUTO_PROGRESS_PAUSE_MS = 60_000L;
    private Vec3d autoProgressPos;
    private long autoProgressTickMs;
    private long autoProgressElapsedMs;
    private long autoProgressPausedMs;
    private long autoProgressEventsSeen;
    private int autoProgressMined;
    private final AtomicLong autoProgressServerEvents = new AtomicLong();
    private final Set<BlockPos> autoProgressPendingBreaks = ConcurrentHashMap.newKeySet();
    private int autoWatchdogExitMethods;
    private BlockPos autoWatchdogAbandonedSite;
    private Vec3d autoWatchdogIdlePos;
    private final Map<BlockPos, Vec3d> autoWatchdogRetryOrigins = new HashMap<>();

    private static final int SKY_CHECK_HEIGHT = 200;
    private static final int DETACHED_TUNNEL_MIN_LENGTH = 8;
    private static final int LAND_RING_RADIUS = 8;
    private static final int MOSSY_SCAN_CHUNKS_PER_PASS = 64;
    private static final int[][] SELF_ASCEND_DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    private static final Set<Item> AUTO_THROWAWAY = Set.of(
        Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.STONE, Items.DIRT, Items.NETHERRACK);

    private final ElytraTakeoff takeoff = new ElytraTakeoff();
    private final Gson visitedGson = new GsonBuilder().setPrettyPrinting().create();
    private final Set<Long> visitedKeys = new java.util.HashSet<>();
    private final List<VisitedEntry> visitedEntries = new ArrayList<>();
    private final Set<Long> exitLootedKeys = new java.util.HashSet<>();
    private final Map<BlockPos, Long> autoSkipLoggedAt = new HashMap<>();
    private final Set<ShipwreckSite> autoShipwreckAgeSkipped = new HashSet<>();
    private boolean autoShipwreckPaletteMissingLogged;
    private String roomRejectInfo;
    private double roomRejectFit = -1;
    private String spawnerlessLossInfo;

    private AutoState state = AutoState.IDLE;
    private int stateTicks;
    private Site autoSite;
    private ColumnPlan autoColumn;
    private boolean autoChestplateAllowed;
    private ExitShaft autoExitShaft;
    private boolean autoExitShaftForeign;
    private Site autoStackedPartner;
    private boolean autoRunAbandoned;
    private boolean autoSoloMode;
    private boolean autoSoloTransitOnly;
    private Site autoSoloTarget;
    private double autoSoloBestDistSq;
    private int autoSoloBestDistTick;
    private int autoSoloNearUnloadedTicks;
    private int autoSoloLastRocketTick;
    private double autoSoloCruiseAltitude;

    private int statRoomsFarmed;
    private int statSpawnersBroken;
    private int statChestsBroken;
    private int statShulkersFound;

    private boolean autoAreaLoaderWasOn, autoPitch40WasOn, autoElytraRecastWasOn;
    private float autoTrailYaw;
    private boolean autoTrailYawSaved;
    private float autoWaypointYaw;
    private boolean autoWaypointYawSaved;
    private boolean autoMemoryChaseFromFlightModule;
    private boolean autoSavedAllowPlace;
    private List<Item> autoSavedThrowawayItems;
    private int autoBaritoneRetries;
    private int autoFailRecoveries;
    private boolean autoAscendFromAbort;

    private boolean autoWalkDirect;
    private boolean autoWalkStepping;
    private boolean autoWalkSwimOverride;

    private BlockPos autoLandSpot;
    private int autoLandSpotAttempts;
    private boolean autoLandSpotGaveUpLogged;
    private int autoWaterDiveClearTicks;

    private List<BlockPos> autoRoomQueue = new ArrayList<>();
    private int autoRoomIndex;
    private int autoRoomSubState;
    private long autoRoomSubStateStartMs;
    private int autoRoomOpenAttempts;
    private int autoRoomContainerFails;
    private int autoRoomWaitTicks;
    private long autoRoomBreakStartMs;
    private boolean autoRoomSpawnerHandled;
    private long autoRoomSpawnerStartMs;
    private BlockPos autoRoomSpawnerPos;
    private int autoRoomFoundShulkers;
    private boolean autoRoomNotOpened;
    private int autoRoomFoundItems;
    private int autoRoomBreakTicks;
    private int autoRoomContainerGoneTicks;
    private Vec3d autoRoomBreakAnchor;
    private boolean autoRoomBreakRepositioning;
    private Vec3d autoRoomSpawnerAnchor;
    private boolean autoRoomSpawnerRepositioning;

    private BlockPos autoRoomStepOffTarget;
    private BlockPos autoRoomStepOffCell;
    private Set<BlockPos> autoRoomStepOffTried = new java.util.HashSet<>();
    private int autoRoomStepOffTicks;
    private double autoRoomStepOffBaselineDist;
    private BlockPos autoRoomStepOffNoCellLoggedTarget;

    private BlockPos autoRoomPillarTarget;
    private int autoRoomPillarPhase;
    private int autoRoomPillarFeetX;
    private int autoRoomPillarFeetY;
    private int autoRoomPillarFeetZ;

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
    private BlockPos autoApproachBreakPos;
    private String autoApproachBreakLabel;
    private int autoApproachBreakCount;

    private boolean autoExitWalkingLogged;
    private int autoExitIdleTicks;
    private int autoExitStuckTicks;
    private int autoExitStuckCount;
    private Vec3d autoExitStuckAnchor;
    private boolean autoExitStuckBreaking;
    private BlockPos autoExitStuckBreakPos;
    private String autoExitStuckBreakLabel;
    private int autoExitStuckBreakTicks;

    private double autoClimbTargetY;
    private int autoResumeClimbTries;
    private int autoClimbLostGlideRetries;
    private int autoClimbRedeployTries;
    private int autoClimbRedeployTick;
    private boolean autoExitReturning;
    private BlockPos autoExitReturnDryGoal;
    private int autoExitNoPathChecks;
    private boolean autoExitSelfWalk;
    private double autoExitSelfBestDist;
    private int autoExitSelfStuckTicks;
    private BlockPos autoExitBreakPos;
    private String autoExitBreakLabel;
    private boolean autoExitRetracingTrail;
    private int autoExitTrailTicks;
    private int autoExitTrailSkipped;
    private int autoExitTrailTotalTicks;
    private int autoExitTrailBudgetTicks;
    private BlockPos autoExitTrailBreakPos;
    private String autoExitTrailBreakLabel;
    private boolean autoExitReturnSettingsSaved;
    private boolean autoExitReturnSavedAllowBreak;
    private List<Block> autoExitReturnSavedAllowBreakAnyway;
    private int autoExitReturnTicks;
    private boolean autoExitTrailDirectTried;
    private BlockPos autoExitTrailGoal;
    private boolean autoWaterJumpVertical;
    private int autoWalkDirectDryTicks;
    private int autoWalkDirectStuckTicks;
    private int autoWalkDirectSwimStuckTicks;
    private BlockPos autoWalkDirectSwimStuckPos;
    private boolean autoWalkDirectSwimBreaking;
    private int autoWalkDirectSwimBreakStage;
    private BlockPos autoWalkDirectSwimBreakTarget;
    private String autoWalkDirectSwimBreakBlockName;
    private int autoWalkDirectSwimBrokenTotal;
    private boolean autoWalkKeyLogged;
    private int autoWalkStuckTicks;
    private int autoWalkIdleTicks;
    private BlockPos autoWalkBoxPos;
    private int autoWalkBoxTicks;
    private double autoWalkStuckLastDist;
    private boolean autoWalkStuckUseBreak;
    private boolean autoWalkStuckBreaking;
    private BlockPos autoWalkStuckBreakPos;
    private String autoWalkStuckBreakLabel;
    private double autoWalkBestDist;
    private int autoWalkBestDistTick;
    private int autoWalkSinkBestY;
    private int autoWalkSinkBestTick;
    private DugHoles.Feature autoWalkTestedShaft;
    private boolean autoWalkReapproach;
    private int autoWalkReapproachCount;
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
    private final Map<BlockPos, Integer> autoWaterTakeoffFailures = new HashMap<>();
    private double autoClimbWindowY;
    private int autoClimbWindowTick;

    private boolean autoPaused;
    private String autoPauseReason;
    private long autoPauseStartMs;
    private int autoPausedTicks;

    private int autoLastHurtTime;
    private long autoLastHitMs;
    private boolean autoHitThisTick;
    private final Set<Integer> autoHostileIds = new HashSet<>();
    private Entity autoShaftChaseTarget;
    private double autoShaftChaseLastDist;
    private int autoShaftChaseStuckTicks;
    private boolean autoShaftChaseBaritoneActive;
    private BlockPos autoShaftChaseBaritoneGoalPos;
    private boolean autoShaftReturning;
    private int autoShaftReturnStartTick;
    private int autoShaftReturnRelaunches;

    private BlockPos autoDigQueuedPos;
    private final Map<BlockPos, Integer> autoDigBlockRetries = new HashMap<>();
    private String autoDigQueuedBlockLabel;
    private long autoDigBlockStartMs;
    private long autoDigTotalStartMs;
    private int autoDigBlocksPlanned;
    private int autoDigBlockTicks;
    private long autoDigDisplacedSinceMs;

    private BlockPos autoSideDigBlockPos;
    private long autoSideDigLastSlowLogMs;

    private BlockPos autoExitPathCell;
    private int autoExitPathStep;

    private double autoHealthBeforeLanding;

    private BlockPos autoTakeoffSpot;
    private int autoTakeoffOutcome;
    private String autoTakeoffFailReason;
    private float autoTakeoffYaw;
    private boolean autoTakeoffFought;
    private int autoTakeoffRetries;
    private Set<BlockPos> autoAscendFailedPoints = new java.util.HashSet<>();

    private BlockPos autoTunnelGoal;
    private BlockPos autoTunnelStep;
    private boolean autoTunnelBaritone;
    private boolean autoTunnelDirectRetried;
    private int autoTunnelRescuePlacements;
    private BlockPos autoTunnelRescuePlacePos;
    private List<Block> autoTunnelDisallowedBreaking;
    private double autoTunnelBestDist;
    private int autoTunnelBestElapsed;
    private final List<BlockPos> autoTunnelTrail = new ArrayList<>();

    private double autoBreachBestDist;
    private int autoBreachBestElapsed;
    private int autoBreachCount;
    private BlockPos autoBreachPos;
    private String autoBreachLabel;
    private BlockPos autoShipwreckWalkStart;
    private boolean autoShipwreckWalkRisen;
    private boolean autoShipwreckRiseFromHole;
    private double autoShipwreckRiseBestY;
    private int autoShipwreckRiseBestTick;
    private double autoShipwreckSideBestGap;
    private int autoShipwreckSideBestTick;
    private BlockPos autoShipwreckSideSpot;
    private boolean autoShipwreckSideTried;
    private int autoShipwreckSideStartTick;
    private List<BlockPos> autoShipwreckPath;
    private int autoShipwreckPathIndex;
    private int autoShipwreckPathProgressTick;
    private int autoShipwreckPathReplans;
    private boolean autoShipwreckPathOff;
    private int autoShipwreckPathRetryTick;
    private double autoShipwreckWalkBestGap;
    private double autoShipwreckWalkBestReach;
    private int autoShipwreckWalkProgressTick;

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
    private int autoShaftFlyPivotCollisionTicks;
    private ShaftDryPhase autoShaftDryPhase = ShaftDryPhase.NONE;
    private int autoShaftDryStartTick;
    private int autoShaftDryUnderPlacements;
    private boolean autoShaftDryBridgeUsed;
    private int autoShaftDryBridgeBlocks;
    private BlockPos autoShaftDryPlacePos;
    private BlockPos autoShaftDrySidePos;
    private BlockPos autoShaftDryBridgePos;
    private BlockPos autoShaftDryVerifyPos;
    private String autoShaftDryVerifyKind;
    private ShaftDryPhase autoShaftDryVerifyNextPhase = ShaftDryPhase.NONE;
    private int autoShaftDryVerifyTick;
    private int autoShaftDryVerifyRetries;
    private String autoShaftDryBlockLabel;
    private boolean autoShaftDryPlaceHardFail;

    private int autoShaftAlignCenteredTicks;
    private BlockPos autoShaftAlignRoomBlockPos;
    private int autoShaftAlignRoomBlockTick;

    private List<BlockPos> autoAscendTargets = List.of();

    private BlockPos autoAscendStuckPos;
    private int autoAscendStuckTicks;
    private int autoAscendRadius;
    private int autoAscendTimeoutBaseTick;
    private int autoAscendSurfacedCount;
    private BlockPos autoAscendArrivedAt;

    private int autoSelfAscendDirIndex;
    private final boolean[] autoSelfAscendTried = new boolean[4];
    private int autoSelfAscendBestY;
    private int autoSelfAscendBestYTick;
    private double autoSelfAscendLastY;
    private int autoSelfAscendProgressTick;
    private BlockPos autoSelfAscendStepFrom;
    private BlockPos autoSelfAscendStepTarget;
    private BlockPos autoSelfAscendBreakPos;
    private String autoSelfAscendBreakLabel;
    private BlockPos autoSelfAscendPlacePos;
    private int autoSelfAscendPlaceTick;
    private int autoSelfAscendPlaceAttempts;
    private String autoSelfAscendPlaceLabel;
    private int autoSelfAscendLastTurnTick;
    private boolean autoSelfAscendPillarMode;
    private boolean autoSelfAscendForcePillar;
    private final Map<BlockPos, Integer> autoSelfAscendBreakCounts = new HashMap<>();

    private int autoWaterJumpAttempts;
    private int autoWaterJumpTapPhase;
    private BlockPos autoWaterJumpClimbPos;
    private long autoWaterJumpClimbStartMs;

    private boolean autoElytraWaitLogged;

    private double autoSwimUpLastY;
    private Vec3d autoSwimUpProgressPos;
    private boolean autoSwimUpOnColumn;
    private int autoSwimUpProgressTick;
    private int autoSwimUpMouthY;
    private int autoSwimUpOutOfWaterTicks;
    private BlockPos autoSwimUpBreakPos;
    private String autoSwimUpBreakLabel;

    private BlockPos autoObsidianTarget;
    private int autoObsidianPhase;
    private int autoObsidianAttempts;
    private int autoObsidianWaitTicks;
    private long autoObsidianClimbStartMs;
    private Set<BlockPos> autoObsidianTried = new java.util.HashSet<>();

    private int autoRoomClimbMode;
    private int autoRoomClimbPillarPhase;
    private int autoRoomClimbPillarStallTicks;
    private boolean autoRoomClimbWater;
    private int autoRoomClimbFeetX;
    private int autoRoomClimbFeetY;
    private int autoRoomClimbFeetZ;
    private long autoRoomClimbStartMs;
    private List<BlockPos> autoRoomClimbBaritoneCandidates = List.of();
    private int autoRoomClimbBaritoneIndex;
    private long autoRoomClimbBaritoneTryStartMs;
    private Vec3d autoRoomClimbBaritoneMoveAnchor;
    private long autoRoomClimbBaritoneMoveAnchorMs;
    private BlockPos autoRoomClimbSwimTarget;
    private boolean autoRoomClimbSwimIsFloorHole;
    private long autoRoomClimbSwimStartMs;

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
        shipwreckAnimations.clear();
        autoWatchdogRetryOrigins.clear();
        autoWatchdogIdlePos = null;
        trialPending.clear();
        trialFound.clear();
        rejected.clear();
        mossyScanned.clear();
        mossyScanCursor = 0;
        spawnerlessWaiting.clear();
        mossyByChunk.clear();
        sites.clear();
        pendingEval.clear();
        approached.clear();
        sideHoleLogged.clear();
        sideHoleUseLogged.clear();
        entryShaftLogged.clear();
        noSafeColumnLogged.clear();
        lowGlideLogged.clear();
        floatingBlockLogged.clear();
        lidBlockLogged.clear();
        shaftPivotFailureLogged.clear();
        jungleTempleSkipLogged.clear();
        newChunkSkipLogged.clear();
        autoSoloRejectedThisSession.clear();
        autoSoundedSites.clear();
        jungleTempleLowestYCache.clear();
        monumentCache.clear();
    }

    private void playAutoSound(String name, Setting<Boolean> toggle) {
        if (!toggle.get() || mc.player == null) return;

        long now = System.currentTimeMillis();
        Long last = autoSoundLastPlayed.get(name);
        if (last != null && now - last < 3_000L) return;
        autoSoundLastPlayed.put(name, now);

        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvent.of(Identifier.of("hunterbuddy", name)),
            1.0f,
            soundVolume.get().floatValue()
        ));
    }

    @Override
    public void onActivate() {
        purgeAllState();
        totalTicks = 0;
        scanCooldown = 0;
        autoShipwreckAgeSkipped.clear();
        autoShipwreckPaletteMissingLogged = false;
        resetAutoState();
        loadVisited();
        retryAbandonedSites();
    }

    @Override
    public void onDeactivate() {
        purgeAllState();
        setChestplateHold(false);
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
        restoreShipwreckSettings();
        autoShipwreckRun = null;
        autoShipwreckNextChest = null;
        autoShipwreckEntryColumn = null;
        autoShipwreckEmergencyGoal = null;
        autoShipwreckFailedExits.clear();
        autoShipwreckPlatformTried = false;
        autoShipwreckChestOutcome = null;
        autoShipwreckDeferred = false;
        autoProgressPos = null;
        autoProgressPendingBreaks.clear();
        autoWatchdogExitMethods = 0;
        autoWatchdogAbandonedSite = null;
        restoreExitReturnSettings();
        restoreAutoThrowawayItems();
        state = AutoState.IDLE;
        setChestplateHold(false);
        stateTicks = 0;
        autoSite = null;
        autoColumn = null;
        autoExitShaft = null;
        autoExitReturnDryGoal = null;
        autoExitShaftForeign = false;
        autoTunnelTrail.clear();
        autoExitRetracingTrail = false;
        autoExitTrailTicks = 0;
        autoExitTrailSkipped = 0;
        autoExitTrailTotalTicks = 0;
        autoExitTrailBudgetTicks = 0;
        autoExitTrailBreakPos = null;
        autoExitTrailBreakLabel = null;
        autoStackedPartner = null;
        autoRunAbandoned = false;
        autoSoloMode = false;
        autoSoloTransitOnly = false;
        autoSoloTarget = null;
        autoSoloBestDistSq = Double.MAX_VALUE;
        autoSoloBestDistTick = 0;
        autoSoloNearUnloadedTicks = 0;
        autoSoloLastRocketTick = -100;
        autoSoloCruiseAltitude = 0.0;
        autoMemoryChaseFromFlightModule = false;
        autoTrailYawSaved = false;
        autoWaypointYawSaved = false;
        autoFailRecoveries = 0;
        autoResumeClimbTries = 0;
        autoClimbStuckWaterReturns = 0;
        autoWaterTakeoffFailures.clear();
        autoWaterJumpVertical = false;
        autoWaterJumpClimbPos = null;
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
        autoApproachBreakPos = null;
        autoApproachBreakLabel = null;
        autoApproachBreakCount = 0;
        autoLastHurtTime = 0;
        autoLastHitMs = 0;
        autoHostileIds.clear();
        autoShaftChaseTarget = null;
        autoShaftChaseLastDist = 0;
        autoShaftChaseStuckTicks = 0;
        autoShaftChaseBaritoneActive = false;
        autoShaftChaseBaritoneGoalPos = null;
        resetShaftDryRecovery();
        autoShaftReturning = false;
        autoLandSpot = null;
        autoLandSpotAttempts = 0;
        autoLandSpotGaveUpLogged = false;
        autoClimbLostGlideRetries = 0;
        autoTakeoffRetries = 0;
        autoAscendFailedPoints.clear();
        autoAscendRadius = 0;
        autoAscendTimeoutBaseTick = 0;
        autoAscendSurfacedCount = 0;
        autoAscendArrivedAt = null;
        resetSelfAscendState(true);
        autoWalkBestDist = -1;
        autoWalkBestDistTick = 0;
        autoWalkSinkBestTick = -1;
        autoWalkTestedShaft = null;
        autoWalkReapproach = false;
        autoWalkReapproachCount = 0;
    }

    private void resetSelfAscendState(boolean clearTried) {
        autoSelfAscendDirIndex = 0;
        if (clearTried) for (int i = 0; i < autoSelfAscendTried.length; i++) autoSelfAscendTried[i] = false;
        autoSelfAscendBestY = 0;
        autoSelfAscendBestYTick = 0;
        autoSelfAscendLastY = 0.0;
        autoSelfAscendProgressTick = 0;
        autoSelfAscendStepFrom = null;
        autoSelfAscendStepTarget = null;
        autoSelfAscendBreakPos = null;
        autoSelfAscendBreakLabel = null;
        autoSelfAscendPlacePos = null;
        autoSelfAscendPlaceTick = 0;
        autoSelfAscendPlaceAttempts = 0;
        autoSelfAscendPlaceLabel = null;
        autoSelfAscendLastTurnTick = 0;
        autoSelfAscendPillarMode = false;
        autoSelfAscendBreakCounts.clear();
    }

    public int getStatRoomsFarmed() { return statRoomsFarmed; }
    public int getStatSpawnersBroken() { return statSpawnersBroken; }
    public int getStatChestsBroken() { return statChestsBroken; }
    public int getStatShulkersFound() { return statShulkersFound; }
    public Site getAutoSite() { return autoSite; }
    public String getAutoStateName() { return state.name(); }

    public boolean hasAutoColumn() { return autoColumn != null; }
    public int getAutoColumnX() { return autoColumn != null ? autoColumn.x() : 0; }
    public int getAutoColumnZ() { return autoColumn != null ? autoColumn.z() : 0; }
    public int getAutoColumnStartY() { return autoColumn != null ? autoColumn.startY() : 0; }
    public int getAutoColumnExitY() { return autoColumn != null ? autoColumn.exitY() : 0; }
    public BlockPos getAutoColumnRim() { return autoColumn != null ? autoColumn.rim() : null; }
    public int getAutoDigBlocksPlanned() { return autoDigBlocksPlanned; }

    public boolean hasAutoExitShaft() { return autoExitShaft != null; }
    public int getAutoExitShaftX() { return autoExitShaft != null ? autoExitShaft.x() : 0; }
    public int getAutoExitShaftZ() { return autoExitShaft != null ? autoExitShaft.z() : 0; }
    public int getAutoExitShaftBottomY() { return autoExitShaft != null ? autoExitShaft.bottomY() : 0; }
    public int getAutoExitShaftExitY() { return autoExitShaft != null ? autoExitShaft.exitY() : 0; }
    public BlockPos getAutoExitShaftEntry() { return autoExitShaft != null ? autoExitShaft.entry() : null; }

    public BlockPos getAutoTunnelGoal() { return autoTunnelGoal; }
    public BlockPos getAutoSideDigBlockPos() { return autoSideDigBlockPos; }

    public int getAutoRoomContainerIndex() { return autoRoomIndex; }
    public int getAutoRoomContainerTotal() { return autoRoomQueue.size(); }
    public boolean isAutoRoomSpawnerHandled() { return autoRoomSpawnerHandled; }
    public int getAutoRoomOpenAttempts() { return autoRoomOpenAttempts; }
    public int getAutoRoomClimbBaritoneIndex() { return autoRoomClimbBaritoneIndex; }
    public int getAutoRoomClimbBaritoneCandidateCount() { return autoRoomClimbBaritoneCandidates.size(); }

    public int getAutoShaftFlyRerouteAttempts() { return autoShaftFlyRerouteAttempts; }
    public int getAutoShaftFlyAttempts() { return autoShaftFlyAttempts; }
    public int getAutoWaterJumpAttempts() { return autoWaterJumpAttempts; }

    public double getAutoClimbTargetY() { return autoClimbTargetY; }
    public int getAutoClimbStuckAttempts() { return autoClimbStuckAttempts; }
    public boolean isAutoClimbStuck() { return autoClimbStuck; }

    public BlockPos getAutoObsidianTarget() { return autoObsidianTarget; }
    public int getAutoObsidianAttempts() { return autoObsidianAttempts; }

    public BlockPos getAutoAscendNearestTarget() { return autoAscendTargets.isEmpty() ? null : autoAscendTargets.get(0); }
    public int getAutoAscendTargetCount() { return autoAscendTargets.size(); }

    public BlockPos getAutoTakeoffSpot() { return autoTakeoffSpot; }
    public int getAutoBaritoneRetries() { return autoBaritoneRetries; }

    public boolean isAutoAreaLoaderHandoff() { return autoAreaLoaderWasOn && flightModule.get() == FlightModule.AreaLoader; }
    public boolean isBusyFarming() { return isActive() && state != AutoState.IDLE; }
    public boolean isAutoPaused() { return autoPaused; }
    public boolean isAutoSoloTransitOnly() { return autoSoloTransitOnly; }
    public String getAutoPauseReason() { return autoPauseReason; }
    public Entity getAutoShaftChaseTarget() { return autoShaftChaseTarget; }

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
        if (detectShipwrecks.get()) ShipwreckDetector.tick(mc.world, this::isVisited);

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
                if (detectShipwrecks.get() && overworld && be instanceof ChestBlockEntity) ShipwreckDetector.collect(mc.world, be.getPos());
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

    private boolean spawnerlessChunksLoaded(int baseCx, int baseCz) {
        for (int cx = (baseCx - 9) >> 4; cx <= (baseCx + 9) >> 4; cx++) {
            for (int cz = (baseCz - 9) >> 4; cz <= (baseCz + 9) >> 4; cz++) {
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
            if (!mc.world.isChunkLoaded(cx, cz)) {
                it.remove();
                mossyByChunk.remove(key);
            }
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

        processSpawnerlessWaiting();

        AtomicReferenceArray<WorldChunk> chunks = manager.chunks.chunks;
        int len = chunks.length();
        int examined = 0;
        long scanStart = System.nanoTime();

        int i = len == 0 ? 0 : mossyScanCursor % len;
        for (int steps = 0; steps < len && examined < MOSSY_SCAN_CHUNKS_PER_PASS; steps++) {
            if (System.nanoTime() - scanStart > 4_000_000L) break;

            WorldChunk chunk = chunks.get(i);
            if (chunk != null) {
                long key = chunk.getPos().toLong();
                if (!mossyScanned.contains(key)) {
                    mossyScanned.add(key);
                    examined++;

                    scanChunkForMossy(chunk);
                }
            }

            i = (i + 1) % len;
        }
        mossyScanCursor = i;
    }

    private void processSpawnerlessWaiting() {
        Iterator<BlockPos> it = spawnerlessWaiting.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                logSkipOnce(pos, "spawnerless waiting chunk unloaded");
                long key = net.minecraft.util.math.ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
                mossyScanned.remove(key);
                mossyByChunk.remove(key);
                it.remove();
            } else if (spawnerlessChunksLoaded(pos.getX(), pos.getZ())) {
                it.remove();
                considerSpawnerlessCandidate(pos.getX(), pos.getY(), pos.getZ());
            }
        }
    }

    private void scanChunkForMossy(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = chunk.getBottomY();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        long chunkKey = chunk.getPos().toLong();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        List<List<BlockPos>> sectionCandidates = new ArrayList<>();
        List<BlockPos> chunkCandidates = new ArrayList<>();

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
                        if (wy < 63) {
                            candidates.add(new BlockPos(wx, wy, wz));
                            continue;
                        }
                        BlockPos above = new BlockPos(wx, wy + 1, wz);
                        BlockState aboveState = mc.world.getBlockState(above);
                        if (isOpenState(aboveState, above) || aboveState.getBlock() instanceof net.minecraft.block.FallingBlock || aboveState.getBlock() == Blocks.COBBLESTONE) {
                            candidates.add(new BlockPos(wx, wy, wz));
                        }
                    }
                }
            }

            sectionCandidates.add(candidates);
            chunkCandidates.addAll(candidates);
        }

        mossyByChunk.put(chunkKey, chunkCandidates);

        for (List<BlockPos> candidates : sectionCandidates) {
            processMossyCandidates(withNeighbourMossy(candidates, startX, startZ, chunkX, chunkZ));
        }
    }

    private List<BlockPos> withNeighbourMossy(List<BlockPos> candidates, int startX, int startZ, int chunkX, int chunkZ) {
        Set<Integer> ys = new java.util.HashSet<>();
        for (BlockPos c : candidates) ys.add(c.getY());

        List<BlockPos> merged = new ArrayList<>(candidates);
        for (int dcx = -1; dcx <= 1; dcx++) {
            for (int dcz = -1; dcz <= 1; dcz++) {
                if (dcx == 0 && dcz == 0) continue;

                List<BlockPos> neighbour = mossyByChunk.get(net.minecraft.util.math.ChunkPos.toLong(chunkX + dcx, chunkZ + dcz));
                if (neighbour == null) continue;

                for (BlockPos n : neighbour) {
                    if (n.getX() < startX - 3 || n.getX() > startX + 18) continue;
                    if (n.getZ() < startZ - 3 || n.getZ() > startZ + 18) continue;
                    if (!ys.contains(n.getY())) continue;
                    if (!merged.contains(n)) merged.add(n);
                }
            }
        }
        return merged;
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
        Map<Integer, List<BlockPos>> byY = new HashMap<>();
        for (BlockPos c : candidates) byY.computeIfAbsent(c.getY(), key -> new ArrayList<>()).add(c);

        for (List<BlockPos> plane : byY.values()) {
            boolean[] used = new boolean[plane.size()];
            int minGroupSize = plane.get(0).getY() < 63 ? 1 : roomMossyMin.get();

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

                if (group.size() < minGroupSize) {
                    logSkipOnce(plane.get(group.get(0)), "spawnerless mossy group n=" + group.size() + " need " + minGroupSize);
                    continue;
                }

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
        BlockPos basePos = new BlockPos(baseCx, cy, baseCz);
        for (Site s : sites.values()) {
            if (baseCx >= s.room.minX() + 1 && baseCx <= s.room.maxX() - 1 && baseCz >= s.room.minZ() + 1 && baseCz <= s.room.maxZ() - 1
                && ((cy - 1 >= s.room.minY() && cy - 1 <= s.room.maxY()) || (cy >= s.room.minY() && cy <= s.room.maxY()))) {
                logSkipOnce(basePos, String.format("spawnerless inside site %d,%d,%d", s.center.getX(), s.center.getY(), s.center.getZ()));
                return;
            }
        }
        for (PendingSite ps : pendingEval.values()) {
            if (baseCx >= ps.room().minX() + 1 && baseCx <= ps.room().maxX() - 1 && baseCz >= ps.room().minZ() + 1 && baseCz <= ps.room().maxZ() - 1
                && ((cy - 1 >= ps.room().minY() && cy - 1 <= ps.room().maxY()) || (cy >= ps.room().minY() && cy <= ps.room().maxY()))) {
                BlockPos pos = ps.center();
                logSkipOnce(basePos, String.format("spawnerless inside pending %d,%d,%d", pos.getX(), pos.getY(), pos.getZ()));
                return;
            }
        }

        if (!spawnerlessChunksLoaded(baseCx, baseCz)) {
            logSkipOnce(basePos, "spawnerless waiting chunks not loaded");
            spawnerlessWaiting.add(basePos);
            return;
        }

        Room best = null;
        roomRejectFit = -1;
        roomRejectInfo = null;

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                Room r = findRoom(baseCx + dx, cy, baseCz + dz, false);
                if (r != null && (best == null || r.fit() > best.fit())) best = r;
            }
        }

        if (best == null) best = findLooseBoxRoom(baseCx, cy, baseCz);
        if (best == null) {
            logSkipOnce(new BlockPos(baseCx, cy, baseCz), "spawnerless no room " + (roomRejectInfo != null ? roomRejectInfo : "no shape"));
            return;
        }
        BlockPos center = new BlockPos(best.cx(), best.cy(), best.cz());
        if (roomHasSpawnerBlock(best)) {
            logSkipOnce(center, String.format("spawnerless room has spawner block center=%d,%d,%d", center.getX(), center.getY(), center.getZ()));
            return;
        }

        if (newChunkSkipLogged.contains(center)) return;
        if (sites.containsKey(center)) {
            logSkipOnce(center, String.format("spawnerless center already site %d,%d,%d", center.getX(), center.getY(), center.getZ()));
            return;
        }
        if (pendingEval.containsKey(center)) {
            logSkipOnce(center, String.format("spawnerless center already pending %d,%d,%d", center.getX(), center.getY(), center.getZ()));
            return;
        }
        spawnerlessLossInfo = null;
        if (!spawnerlessCandidateWins(best)) {
            logSkipOnce(center, spawnerlessLossInfo != null ? spawnerlessLossInfo : "spawnerless loses to an overlapping room");
            return;
        }
        removeOverlapping(best);

        pendingEval.put(center, new PendingSite(center, false, null, best));
    }

    private Room findLooseBoxRoom(int baseCx, int cy, int baseCz) {
        int floorY = cy - 1;
        int cobble = 0;
        Block clueBlock = null;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = baseCx - 6; x <= baseCx + 6; x++) {
            for (int z = baseCz - 6; z <= baseCz + 6; z++) {
                for (int y = floorY; y <= cy + 4; y++) {
                    Block b = mc.world.getBlockState(cursor.set(x, y, z)).getBlock();
                    if (b == Blocks.COBBLESTONE) cobble++;
                    if (clueBlock == null && isLooseBoxClue(b)) clueBlock = b;
                }
            }
        }

        if (cobble < 20 || clueBlock == null) return null;

        Room best = null;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                Room r = findRoom(baseCx + dx, cy, baseCz + dz, true);
                if (r != null && (best == null || r.fit() > best.fit())) best = r;
            }
        }

        boolean accepted = best != null && best.fit() >= 0.6;

        if (log.get()) {
            HunterBuddyAddon.LOG.info("[SpawnerDetector] loose box {} {} {} cobble={} clue={} fit={} {}",
                baseCx, cy, baseCz, cobble, Registries.BLOCK.getId(clueBlock).getPath(),
                best == null ? "none" : String.format("%.2f", best.fit()), accepted ? "accepted" : "rejected");
        }

        if (!accepted) return null;
        return best;
    }

    private static boolean isLooseBoxClue(Block b) {
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL || b instanceof ShulkerBoxBlock
            || b instanceof AbstractTorchBlock || b instanceof LanternBlock;
    }

    private String villageHouseClue(Room room) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = room.minX() - 2; x <= room.maxX() + 2; x++) {
            for (int z = room.minZ() - 2; z <= room.maxZ() + 2; z++) {
                for (int y = room.minY(); y <= room.maxY() + 3; y++) {
                    Block b = mc.world.getBlockState(cursor.set(x, y, z)).getBlock();
                    if (b instanceof net.minecraft.block.TrapdoorBlock) return "trapdoor";
                    if (b instanceof net.minecraft.block.AbstractCauldronBlock) return "cauldron";
                }
            }
        }
        int columns = 0, wooden = 0;
        for (int x = room.minX() + 1; x <= room.maxX() - 1; x++) {
            for (int z = room.minZ() + 1; z <= room.maxZ() - 1; z++) {
                columns++;
                for (int y = room.cy() + 1; y <= room.cy() + 7; y++) {
                    BlockState state = mc.world.getBlockState(cursor.set(x, y, z));
                    if (isOpenState(state, cursor)) continue;
                    if (state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.LOGS)
                        || state.isIn(BlockTags.WOODEN_STAIRS) || state.isIn(BlockTags.WOODEN_SLABS)) wooden++;
                    break;
                }
            }
        }
        return wooden * 2 >= columns ? "wooden roof" : null;
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
        spawnerlessLossInfo = null;
        for (Site s : sites.values()) {
            if (boxesOverlap(room, s.room) && (s.hasSpawner || s.room.fit() >= room.fit() || isVisited(s.center)
                || (state != AutoState.IDLE && autoSite != null && autoSite.center.equals(s.center)))) {
                spawnerlessLossInfo = String.format("spawnerless loses to site %d,%d,%d spawner=%s",
                    s.center.getX(), s.center.getY(), s.center.getZ(), s.hasSpawner ? "yes" : "no");
                return false;
            }
        }
        for (PendingSite ps : pendingEval.values()) {
            if (boxesOverlap(room, ps.room()) && (ps.hasSpawner() || ps.room().fit() >= room.fit())) {
                BlockPos pos = ps.center();
                spawnerlessLossInfo = String.format("spawnerless loses to pending %d,%d,%d spawner=%s",
                    pos.getX(), pos.getY(), pos.getZ(), ps.hasSpawner() ? "yes" : "no");
                return false;
            }
        }
        for (VisitedEntry e : visitedEntries) {
            if (e.x() >= room.minX() && e.x() <= room.maxX()
                && e.y() >= room.minY() && e.y() <= room.maxY()
                && e.z() >= room.minZ() && e.z() <= room.maxZ()) {
                spawnerlessLossInfo = String.format("spawnerless loses to visited %d,%d,%d", e.x(), e.y(), e.z());
                return false;
            }
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
        return a.minX() + 1 <= b.maxX() - 1 && a.maxX() - 1 >= b.minX() + 1
            && a.minY() <= b.maxY() && a.maxY() >= b.minY()
            && a.minZ() + 1 <= b.maxZ() - 1 && a.maxZ() - 1 >= b.minZ() + 1;
    }

    private Room findRoom(int cx, int cy, int cz, boolean spawnerAtCenter) {
        Room best = null;
        double bestFit = -1;
        Room fallback = null;
        double fallbackFit = -1;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int mMin = cy - 1 < 63 ? 1 : roomMossyMin.get();
        double floorMin = roomFloorMin.get() / 100.0;
        double interiorMin = roomInteriorMin.get() / 100.0;
        double wallsMin = roomWallsMin.get() / 100.0;
        double ceilingMin = roomCeilingMin.get() / 100.0;

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

                int open = 0, caveAir = 0, iCount = 0, fallen = 0;
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

                            if (isOpen || b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.SPAWNER
                                || b instanceof net.minecraft.block.FallingBlock) iCount++;

                            if (b instanceof net.minecraft.block.FallingBlock) fallen++;
                        }
                    }
                }

                double anyFit = (double) c / aireSol + (double) w / casesAnneau;
                if (spawnerAtCenter && anyFit > fallbackFit) {
                    fallbackFit = anyFit;
                    boolean fallbackModern = open > 0 && caveAir * 2 >= open;
                    fallback = new Room(cx, cy, cz, k, p, floorMinX, cy - 1, floorMinZ, floorMaxX, cy + 4, floorMaxZ, fallbackModern, anyFit);
                }

                boolean legacyRoomWithoutWalls = c >= 0.9 * aireSol && m >= 0.5 * c && iCount >= 0.9 * volumeInterieur;
                String shapeFail = c < floorMin * aireSol ? "floor" : m < mMin ? "mossy" : iCount < interiorMin * volumeInterieur ? "interior"
                    : w < wallsMin * casesAnneau && !legacyRoomWithoutWalls ? "walls" : null;
                if (shapeFail != null && !spawnerAtCenter && anyFit > roomRejectFit) {
                    roomRejectFit = anyFit;
                    roomRejectInfo = String.format("center=%d,%d,%d size=%dx%d fail=%s floor=%d/%d mossy=%d/%d open=%d/%d walls=%d/%d",
                        cx, cy, cz, 2 * k + 1, 2 * p + 1, shapeFail, c, aireSol, m, mMin, iCount, volumeInterieur, w, casesAnneau);
                }

                if (c < floorMin * aireSol) continue;
                if (m < mMin) continue;
                if (iCount < interiorMin * volumeInterieur) continue;
                if (w < wallsMin * casesAnneau && !legacyRoomWithoutWalls) continue;

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
                int openCeiling = interiorArea - ceilingClosed;
                if (!spawnerAtCenter && ceilingClosed < ceilingMin * interiorArea && !(fallen >= 3 && fallen * 2 >= openCeiling)) {
                    if (anyFit > roomRejectFit) {
                        roomRejectFit = anyFit;
                        roomRejectInfo = String.format("center=%d,%d,%d size=%dx%d fail=ceiling ceiling=%d/%d fallen=%d floor=%d/%d walls=%d/%d",
                            cx, cy, cz, 2 * k + 1, 2 * p + 1, ceilingClosed, interiorArea, fallen, c, aireSol, w, casesAnneau);
                    }
                    continue;
                }

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
            if (!mc.world.getChunkManager().isChunkLoaded(center.getX() >> 4, center.getZ() >> 4)) {
                for (int cx = room.minX() >> 4; cx <= (room.maxX() >> 4); cx++) {
                    for (int cz = room.minZ() >> 4; cz <= (room.maxZ() >> 4); cz++) {
                        long key = net.minecraft.util.math.ChunkPos.toLong(cx, cz);
                        mossyScanned.remove(key);
                        mossyByChunk.remove(key);
                    }
                }
                logSkipOnce(center, "spawnerless pending dropped: center chunk unloaded, rescan on reload");
                return false;
            }
            return true;
        }

        int chests = countContainersAround(center);

        if (requireChests.get() && chests == 0) {
            if (ps.hasSpawner()) rejected.add(center);
            else logSkipOnce(center, String.format("spawnerless pending dropped: no container counted within %d", chestRange.get()));
            return false;
        }

        if (!jungleTempleChunksLoaded(center)) return true;

        Integer jungleTempleY = jungleTempleLowestY(center);
        if (!ps.hasSpawner() && jungleTempleY != null && room.maxY() >= jungleTempleY - 3) {
            rejected.add(center);
            if (jungleTempleSkipLogged.add(center)) {
                autoLog("SITE", center, "skipped: jungle temple");
            }
            return false;
        }

        if (!ps.hasSpawner()) {
            if (newChunkSkipLogged.contains(center)) {
                rejected.add(center);
                return false;
            }
            String houseClue = villageHouseClue(room);
            if (houseClue != null) {
                rejected.add(center);
                logSkipOnce(center, "spawnerless village house " + houseClue);
                return false;
            }
            int deepslateY = -1;
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(center.getX() >> 4, center.getZ() >> 4);
            if (chunk.getBottomY() <= 0) {
                int sectionIndex = chunk.getSectionIndex(0);
                ChunkSection[] sections = chunk.getSectionArray();
                if (sectionIndex >= 0 && sectionIndex < sections.length) {
                    ChunkSection section = sections[sectionIndex];
                    if (section != null && !section.isEmpty()
                        && section.hasAny(st -> st.isOf(Blocks.DEEPSLATE) || st.isOf(Blocks.COBBLED_DEEPSLATE))) {
                        for (int y = 5; y < 16 && deepslateY < 0; y++) {
                            for (int x = 0; x < 16 && deepslateY < 0; x++) {
                                for (int z = 0; z < 16; z++) {
                                    BlockState state = section.getBlockState(x, y, z);
                                    if (state.isOf(Blocks.DEEPSLATE) || state.isOf(Blocks.COBBLED_DEEPSLATE)) {
                                        deepslateY = y;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (deepslateY >= 0) {
                rejected.add(center);
                if (newChunkSkipLogged.add(center)) {
                    autoLog("SITE", center, String.format("skipped: spawnerless room in a new chunk (deepslate at y=%d)", deepslateY));
                }
                return false;
            }
        }

        long t0 = System.nanoTime();

        LightHit lightHit = detectTorches.get() ? computeLight(room) : LightHit.NONE;
        boolean ender = computeEnderChest(room);
        PlayerBlocksResult pbr = computePlayerBlocks(room);
        TunnelResult tr = computeTunnel(room);
        FilledShaftResult filled = computeFilledShaft(room);
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
        site.entryShafts = tr.entryShafts();
        site.filled = filled.hit();
        site.filledShaft = filled;
        site.air = air;
        site.airCount = airCount;
        site.roomContainers = computeRoomContainers(room);
        if (hasMisplacedContainer(room)) {
            site.playerBlocks = true;
            site.playerBlockTypes.add("placed chest");
        }
        site.discoveredAt = System.currentTimeMillis();
        site.discoveredAtTick = totalTicks;
        site.animation = new Animation(fadeTime.get());

        if (ps.hasSpawner()) checkMobSignal(site);

        recomputeScoreAndCategory(site);

        sites.put(center, site);

        long evalMs = (System.nanoTime() - t0) / 1_000_000;
        logSite(site, evalMs);
        if (qualifiesForAuto(site) && !isVisited(site.center) && autoSoundedSites.add(site.center)) {
            playAutoSound("spawner_found", soundFound);
        }

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
                if (qualifiesForAuto(site) && !isVisited(site.center) && autoSoundedSites.add(site.center)) {
                    playAutoSound("spawner_found", soundFound);
                }
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
        if (site.filled) score += weightFilled.get();
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

        if (block instanceof AbstractTorchBlock) return !nearMineshaftBlock(pos);
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

    private boolean nearMineshaftBlock(BlockPos pos) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = pos.getX() - 3; x <= pos.getX() + 3; x++) {
            for (int y = pos.getY() - 3; y <= pos.getY() + 3; y++) {
                for (int z = pos.getZ() - 3; z <= pos.getZ() + 3; z++) {
                    Block block = mc.world.getBlockState(m.set(x, y, z)).getBlock();
                    if (block instanceof AbstractRailBlock || block == Blocks.OAK_FENCE || block == Blocks.COBWEB) return true;
                }
            }
        }
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

    private record TunnelResult(boolean hit, DugHoles.Feature feature, List<DugHoles.Feature> entryShafts) {}

    private static int tunnelFeatureLength(DugHoles.Feature f, List<DugHoles.Feature> features) {
        return switch (f.kind()) {
            case SHAFT -> f.maxY() - f.minY() + 1;
            case TUNNEL -> Math.max(f.maxX() - f.minX(), f.maxZ() - f.minZ()) + 1;
            case STAIRS -> stairChainLength(f, features);
            case POCKET -> 0;
        };
    }

    private static boolean traceTunnelEligible(DugHoles.Feature f, List<DugHoles.Feature> features, boolean overlaps) {
        return switch (f.kind()) {
            case SHAFT -> {
                int length = tunnelFeatureLength(f, features);
                yield length >= 5 && (overlaps || length >= DETACHED_TUNNEL_MIN_LENGTH);
            }
            case TUNNEL -> {
                int length = tunnelFeatureLength(f, features);
                yield overlaps || length >= DETACHED_TUNNEL_MIN_LENGTH;
            }
            default -> false;
        };
    }

    private static int stairChainLength(DugHoles.Feature f, List<DugHoles.Feature> features) {
        boolean[] seen = new boolean[features.size()];
        int[] stack = new int[features.size()];
        int top = 0;
        int count = 0;

        for (int i = 0; i < features.size(); i++) {
            if (features.get(i) == f) {
                seen[i] = true;
                stack[top++] = i;
                break;
            }
        }

        while (top > 0) {
            DugHoles.Feature current = features.get(stack[--top]);
            count++;
            for (int i = 0; i < features.size(); i++) {
                DugHoles.Feature next = features.get(i);
                if (!seen[i] && stairStepsTouch(current, next)) {
                    seen[i] = true;
                    stack[top++] = i;
                }
            }
        }

        return count;
    }

    private static boolean stairStepsTouch(DugHoles.Feature a, DugHoles.Feature b) {
        if (a.kind() != DugHoles.Kind.STAIRS || b.kind() != DugHoles.Kind.STAIRS) return false;
        boolean adjacentX = Math.abs(a.minX() - b.minX()) == 1 && a.minZ() == b.minZ();
        boolean adjacentZ = Math.abs(a.minZ() - b.minZ()) == 1 && a.minX() == b.minX();
        return (adjacentX || adjacentZ) && Math.abs(a.minY() - b.minY()) == 1;
    }

    private TunnelResult computeTunnel(Room room) {
        int minX = room.minX() - 15, maxX = room.maxX() + 15;
        int minZ = room.minZ() - 15, maxZ = room.maxZ() + 15;
        int sizeX = maxX - minX + 1, sizeZ = maxZ - minZ + 1;
        int minY = room.cy() - 7, maxY = room.cy() + 28;

        DugHoles.Params params = new DugHoles.Params(4, 4, 3, 8, 2, true, true, true, true);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        DugHoles.BlockSource src = (x, y, z) -> mc.world.getBlockState(cursor.set(x, y, z));

        List<DugHoles.Feature> features = DugHoles.analyze(src, minX, minZ, sizeX, sizeZ, 4, minY, maxY, params);

        boolean hit = false;
        boolean hitOverlaps = false;
        int hitDistSq = Integer.MAX_VALUE;
        DugHoles.Feature hitFeature = null;
        List<DugHoles.Feature> entryShafts = new ArrayList<>();

        for (DugHoles.Feature f : features) {
            boolean overlaps = f.minX() - 1 <= room.maxX() && f.maxX() + 1 >= room.minX()
                && f.minY() - 1 <= room.maxY() && f.maxY() + 1 >= room.minY()
                && f.minZ() - 1 <= room.maxZ() && f.maxZ() + 1 >= room.minZ();
            int distX = f.maxX() < room.minX() ? room.minX() - f.maxX() : (f.minX() > room.maxX() ? f.minX() - room.maxX() : 0);
            int distZ = f.maxZ() < room.minZ() ? room.minZ() - f.maxZ() : (f.minZ() > room.maxZ() ? f.minZ() - room.maxZ() : 0);
            int distSq = distX * distX + distZ * distZ;

            boolean hitEligible = traceTunnelEligible(f, features, overlaps);

            if (hitEligible
                && (!hit || (!hitOverlaps && (overlaps || distSq < hitDistSq)))) {
                hit = true;
                hitOverlaps = overlaps;
                hitDistSq = distSq;
                hitFeature = f;
            }

            if (f.kind() == DugHoles.Kind.SHAFT) {
                int sx = f.minX(), sz = f.minZ();
                int shaftDistX = sx < room.minX() ? room.minX() - sx : (sx > room.maxX() ? sx - room.maxX() : 0);
                int shaftDistZ = sz < room.minZ() ? room.minZ() - sz : (sz > room.maxZ() ? sz - room.maxZ() : 0);
                if (Math.max(shaftDistX, shaftDistZ) <= 8 && f.maxY() >= room.cy() + 3 && f.minY() <= room.cy() + 6) {
                    entryShafts.add(f);
                }
            }
        }

        return new TunnelResult(hit, hitFeature, entryShafts);
    }

    private record FilledShaftResult(boolean hit, int x, int minY, int maxY, int z, String block, int length) {}

    private FilledShaftResult computeFilledShaft(Room room) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int bestLen = 0;
        int bestX = 0, bestZ = 0, bestMinY = 0, bestMaxY = 0;
        String bestBlock = "none";
        int minY = room.cy() + 5;

        for (int x = room.minX() - 2; x <= room.maxX() + 2; x++) {
            for (int z = room.minZ() - 2; z <= room.maxZ() + 2; z++) {
                int maxY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
                int runStart = 0, runLen = 0, cobble = 0, netherrack = 0, deepslate = 0;
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    Block block = countedFilledBlock(mc.world.getBlockState(cursor), cursor);
                    if (block == null) {
                        if (runLen > bestLen) {
                            bestLen = runLen;
                            bestX = x;
                            bestZ = z;
                            bestMinY = runStart;
                            bestMaxY = y - 1;
                            bestBlock = filledBlockLabel(cobble, netherrack, deepslate);
                        }
                        runLen = 0;
                        cobble = 0;
                        netherrack = 0;
                        deepslate = 0;
                        continue;
                    }
                    if (runLen == 0) runStart = y;
                    runLen++;
                    if (block == Blocks.COBBLESTONE) cobble++;
                    else if (block == Blocks.NETHERRACK) netherrack++;
                    else deepslate++;
                }
                if (runLen > bestLen) {
                    bestLen = runLen;
                    bestX = x;
                    bestZ = z;
                    bestMinY = runStart;
                    bestMaxY = maxY;
                    bestBlock = filledBlockLabel(cobble, netherrack, deepslate);
                }
            }
        }

        if (bestLen < 4) return new FilledShaftResult(false, 0, 0, 0, 0, "none", 0);
        return new FilledShaftResult(true, bestX, bestMinY, bestMaxY, bestZ, bestBlock, bestLen);
    }

    private Block countedFilledBlock(BlockState state, BlockPos pos) {
        Block block = state.getBlock();
        if (block != Blocks.COBBLESTONE && block != Blocks.NETHERRACK && block != Blocks.COBBLED_DEEPSLATE) return null;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (isFilledShaftWallBlock(mc.world.getBlockState(pos.offset(dir)).getBlock())) return null;
        }
        return waterOrLavaAdjacent(pos) ? null : block;
    }

    private boolean isFilledShaftWallBlock(Block block) {
        return block == Blocks.COBBLESTONE
            || block == Blocks.MOSSY_COBBLESTONE
            || block == Blocks.NETHERRACK
            || block == Blocks.COBBLED_DEEPSLATE;
    }

    private boolean waterOrLavaAdjacent(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.offset(dir);
            if (mc.world.getFluidState(side).isIn(FluidTags.WATER) || mc.world.getFluidState(side).isIn(FluidTags.LAVA)) return true;
        }
        return false;
    }

    private String filledBlockLabel(int cobble, int netherrack, int deepslate) {
        if (netherrack > cobble && netherrack >= deepslate) return Registries.BLOCK.getId(Blocks.NETHERRACK).getPath();
        if (deepslate > cobble && deepslate > netherrack) return Registries.BLOCK.getId(Blocks.COBBLED_DEEPSLATE).getPath();
        return Registries.BLOCK.getId(Blocks.COBBLESTONE).getPath();
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

    private boolean hasMisplacedContainer(Room room) {
        int cx = room.cx(), cy = room.cy(), cz = room.cz(), k = room.k(), p = room.p();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int plainChests = 0;

        for (int x = cx - k; x <= cx + k; x++) {
            for (int z = cz - p; z <= cz + p; z++) {
                for (int y = cy; y <= cy + 3; y++) {
                    Block b = mc.world.getBlockState(cursor.set(x, y, z)).getBlock();
                    boolean isChest = b == Blocks.CHEST;
                    boolean isOtherContainer = b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL || b instanceof ShulkerBoxBlock;
                    if (!isChest && !isOtherContainer) continue;
                    if (isOtherContainer) return true;

                    plainChests++;
                    if (y != cy) return true;

                }
            }
        }

        return plainChests >= 4;
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
        if (site.filled) sb.append('F');
        if (site.air) sb.append('A');
        return sb.toString();
    }

    private String tunnelLogValue(Site site) {
        if (!site.tunnel || site.tunnelFeature == null) return "none";
        DugHoles.Feature f = site.tunnelFeature;
        return f.kind() + "@" + f.minX() + "," + f.minY() + "," + f.minZ()
            + "-" + f.maxX() + "," + f.maxY() + "," + f.maxZ();
    }

    private String filledLogValue(Site site) {
        if (!site.filled || site.filledShaft == null) return "none";
        FilledShaftResult f = site.filledShaft;
        return f.x() + "," + f.minY() + ".." + f.maxY() + "," + f.z()
            + " block=" + f.block() + " n=" + f.length();
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
            "[SpawnerDetector] site {} {} {} mob={} spawner={} room={} modern={} chests={} roomChests={} score={} signals={} light={} blocks={} mobs={} tunnel={} filled={} air={} category={} evalMs={}",
            c.getX(), c.getY(), c.getZ(), mobLabel, site.hasSpawner ? "yes" : "no", roomSize,
            site.modern ? "yes" : "no", site.containers, site.roomContainers.size(), site.score,
            signalLetters(site), lightLogValue(site), site.playerBlockTypes, site.mobCount,
            tunnelLogValue(site), filledLogValue(site), site.airCount, site.category.label, evalMs);
    }

    public Collection<Site> sites() {
        return Collections.unmodifiableCollection(sites.values());
    }

    private static final double LABEL_SCALE_FLOOR = 0.8;
    private static final int LABEL_MARGIN = 3;
    private static final int LABEL_STRIPE_WIDTH = 2;
    private static final int LABEL_BG_ALPHA = 190;
    private static final float LABEL_LINE2_SCALE = 0.82f;
    private static final Color AMBER = new Color(255, 176, 40);
    private static final Color LOOTED_GREEN = new Color(90, 220, 120);

    private double effectiveLabelScale(double distance) {
        double base = Math.max(labelScale.get(), LABEL_SCALE_FLOOR);
        if (!dynamicScale.get()) return base;
        double t = MathHelper.clamp(distance / Math.max(1.0, renderDistance.get()), 0.0, 1.0);
        double top = Math.max(base, dynamicMaxScale.get());
        return base + (top - base) * t;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String pluralWord(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private String signalWords(Site site) {
        List<String> words = new ArrayList<>();
        if (site.light) words.add("light");
        if (site.ender) words.add("ender chest");
        if (site.playerBlocks) words.add("player blocks");
        if (site.broken) words.add("broken spawner");
        if (site.mobs) words.add("mobs");
        if (site.tunnel) words.add("tunnel");
        if (site.air) words.add("air");
        return String.join(", ", words);
    }

    private static boolean isFailureOutcome(String outcome) {
        return outcome != null && (outcome.startsWith("failed:") || outcome.startsWith("aborted:"));
    }

    private Map<Long, String> latestOutcomes() {
        Map<Long, String> outcomes = new HashMap<>();
        Map<Long, Long> times = new HashMap<>();
        for (VisitedEntry e : visitedEntries) {
            long key = BlockPos.asLong(e.x(), e.y(), e.z());
            Long prevTime = times.get(key);
            if (prevTime == null || e.time() >= prevTime) {
                times.put(key, e.time());
                outcomes.put(key, e.outcome());
            }
        }
        return outcomes;
    }

    private static String shortFailReason(String outcome) {
        if (outcome == null) return "";
        String reason = outcome;
        int colon = reason.indexOf(':');
        if (colon >= 0) reason = reason.substring(colon + 1);
        if (reason.length() > 24) reason = reason.substring(0, 24) + "...";
        return reason;
    }

    private void drawTopCross(Render3DEvent event, BlockPos pos, Color color) {
        double minX = pos.getX(), minY = pos.getY() + 1.0, minZ = pos.getZ();
        double maxX = pos.getX() + 1.0, maxZ = pos.getZ() + 1.0;
        event.renderer.line(minX, minY, minZ, maxX, minY, maxZ, color);
        event.renderer.line(maxX, minY, minZ, minX, minY, maxZ, color);
    }

    private void drawTwoLineLabel(DrawContext ctx, String line1, String line2, Color stripe, float factor) {
        var tr = mc.textRenderer;
        int w1 = tr.getWidth(line1);
        int w2raw = line2 != null ? tr.getWidth(line2) : 0;
        int w2 = line2 != null ? (int) Math.ceil(w2raw * LABEL_LINE2_SCALE) : 0;
        int textWidth = Math.max(w1, w2);
        int fh = tr.fontHeight;
        int line2h = line2 != null ? (int) Math.ceil(fh * LABEL_LINE2_SCALE) : 0;

        int innerW = textWidth + LABEL_MARGIN * 2 + LABEL_STRIPE_WIDTH;
        int innerH = LABEL_MARGIN * 2 + fh + (line2 != null ? line2h + 1 : 0);

        int left = -innerW / 2;
        int top = -2;
        int right = left + innerW;
        int bottom = top + innerH;

        ctx.fill(left, top, right, bottom, new Color(0, 0, 0, (int) (LABEL_BG_ALPHA * factor)).getPacked());
        ctx.fill(left, top, left + LABEL_STRIPE_WIDTH, bottom,
            new Color(stripe.r, stripe.g, stripe.b, (int) (255 * factor)).getPacked());

        int textX = left + LABEL_STRIPE_WIDTH + LABEL_MARGIN;
        int textY = top + LABEL_MARGIN - 1;
        ctx.drawText(tr, line1, textX, textY, new Color(255, 255, 255, (int) (255 * factor)).getPacked(), true);

        if (line2 != null) {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(textX, textY + fh + 1);
            ctx.getMatrices().scale(LABEL_LINE2_SCALE, LABEL_LINE2_SCALE);
            ctx.drawText(tr, line2, 0, 0, new Color(200, 200, 200, (int) (235 * factor)).getPacked(), true);
            ctx.getMatrices().popMatrix();
        }
    }

    private static final Identifier HEAD_ZOMBIE = Identifier.of("minecraft", "textures/entity/zombie/zombie.png");
    private static final Identifier HEAD_SKELETON = Identifier.of("minecraft", "textures/entity/skeleton/skeleton.png");
    private static final Identifier HEAD_SPIDER = Identifier.of("minecraft", "textures/entity/spider/spider.png");
    private static final Identifier HEAD_CAVE_SPIDER = Identifier.of("minecraft", "textures/entity/spider/cave_spider.png");

    private record MobHeadIcon(Identifier texture, int u, int v, int texWidth, int texHeight, boolean hat) {}

    private static MobHeadIcon headIconFor(String mob) {
        if (mob == null) return null;
        return switch (mob) {
            case "zombie" -> new MobHeadIcon(HEAD_ZOMBIE, 8, 8, 64, 64, true);
            case "skeleton" -> new MobHeadIcon(HEAD_SKELETON, 8, 8, 64, 32, false);
            case "spider" -> new MobHeadIcon(HEAD_SPIDER, 40, 12, 64, 32, false);
            case "cave_spider" -> new MobHeadIcon(HEAD_CAVE_SPIDER, 40, 12, 64, 32, false);
            default -> null;
        };
    }

    private void drawTwoLineLabel(DrawContext ctx, MobHeadIcon icon, String line1Suffix, String line2, Color stripe, float factor) {
        var tr = mc.textRenderer;
        int fh = tr.fontHeight;
        int iconSize = fh;
        int iconGap = 2;
        int suffixWidth = tr.getWidth(line1Suffix);
        int w1 = iconSize + iconGap + suffixWidth;
        int w2raw = line2 != null ? tr.getWidth(line2) : 0;
        int w2 = line2 != null ? (int) Math.ceil(w2raw * LABEL_LINE2_SCALE) : 0;
        int textWidth = Math.max(w1, w2);
        int line2h = line2 != null ? (int) Math.ceil(fh * LABEL_LINE2_SCALE) : 0;

        int innerW = textWidth + LABEL_MARGIN * 2 + LABEL_STRIPE_WIDTH;
        int innerH = LABEL_MARGIN * 2 + fh + (line2 != null ? line2h + 1 : 0);

        int left = -innerW / 2;
        int top = -2;
        int right = left + innerW;
        int bottom = top + innerH;

        int alpha = (int) (255 * factor);

        ctx.fill(left, top, right, bottom, new Color(0, 0, 0, (int) (LABEL_BG_ALPHA * factor)).getPacked());
        ctx.fill(left, top, left + LABEL_STRIPE_WIDTH, bottom,
            new Color(stripe.r, stripe.g, stripe.b, (int) (255 * factor)).getPacked());

        int iconX = left + LABEL_STRIPE_WIDTH + LABEL_MARGIN;
        int iconY = top + LABEL_MARGIN - 1;
        int tint = new Color(255, 255, 255, alpha).getPacked();

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, icon.texture(), iconX, iconY, icon.u(), icon.v(),
            iconSize, iconSize, 8, 8, icon.texWidth(), icon.texHeight(), tint);
        if (icon.hat()) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, icon.texture(), iconX, iconY, 40, 8,
                iconSize, iconSize, 8, 8, icon.texWidth(), icon.texHeight(), tint);
        }

        int textX = iconX + iconSize + iconGap;
        int textY = iconY;
        ctx.drawText(tr, line1Suffix, textX, textY, new Color(255, 255, 255, alpha).getPacked(), true);

        if (line2 != null) {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(iconX, textY + fh + 1);
            ctx.getMatrices().scale(LABEL_LINE2_SCALE, LABEL_LINE2_SCALE);
            ctx.drawText(tr, line2, 0, 0, new Color(200, 200, 200, (int) (235 * factor)).getPacked(), true);
            ctx.getMatrices().popMatrix();
        }
    }

    private void drawSimpleLabel(DrawContext ctx, String text, Color textColor, float factor) {
        var tr = mc.textRenderer;
        int width = tr.getWidth(text);
        int left = -width / 2 - LABEL_MARGIN;
        int right = width / 2 + LABEL_MARGIN;
        int top = -2;
        int bottom = top + tr.fontHeight + LABEL_MARGIN * 2 - 1;

        ctx.fill(left, top, right, bottom, new Color(0, 0, 0, (int) (LABEL_BG_ALPHA * factor)).getPacked());
        ctx.drawText(tr, text, -width / 2, top + LABEL_MARGIN - 1,
            new Color(textColor.r, textColor.g, textColor.b, (int) (255 * factor)).getPacked(), true);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (trialFound.isEmpty() && sites.isEmpty() && !detectShipwrecks.get()) return;

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

        Map<Long, String> outcomes = latestOutcomes();

        for (Site site : sites.values()) {
            if (site.category == Category.INTACT && !showIntact.get()) continue;
            if (site.category == Category.EMPTY && !showEmpty.get()) continue;
            if (ignoreBelowZero.get() && site.center.getY() < 0) continue;

            BlockPos pos = site.center;
            long siteKey = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
            String outcome = outcomes.get(siteKey);
            boolean failed = isFailureOutcome(outcome);
            boolean looted = outcome != null ? !failed : exitLootedKeys.contains(siteKey);

            if (looted && lootedStyle.get() == LootedStyle.HIDE) continue;

            double distanceSq = eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

            site.animation.setVisible(distanceSq <= maxDistanceSq);

            float factor = site.animation.factor();
            if (factor <= 0.0f) continue;

            if (distanceFade.get()) {
                double distance = Math.sqrt(distanceSq);
                factor *= (float) (1.0 - Math.min(0.75, distance / maxDistance * 0.75));
            }

            SettingColor base = looted ? farmedColor.get() : colorOf(site.category);
            Color line = new Color(base.r, base.g, base.b, (int) (base.a * factor));
            Color side = new Color(base.r, base.g, base.b, (int) (base.a * factor * 0.25));

            event.renderer.box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                side, line, shapeMode.get(), 0);

            if (failed) {
                drawTopCross(event, pos, new Color(AMBER.r, AMBER.g, AMBER.b, (int) (255 * factor)));
            } else if (looted && lootedStyle.get() == LootedStyle.CROSS) {
                drawTopCross(event, pos, line);
            }
        }
        if (detectShipwrecks.get()) renderShipwrecks(event, outcomes, eye, maxDistance, maxDistanceSq);
    }

    @EventHandler
    private void onRender2D(meteordevelopment.meteorclient.events.render.Render2DEvent event) {
        if (!showLabel.get() || mc.player == null || mc.world == null) return;
        if (trialFound.isEmpty() && sites.isEmpty() && !detectShipwrecks.get()) return;

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
            if (!NametagUtils.to2D(screen, effectiveLabelScale(Math.sqrt(eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))))) continue;

            String line2 = detection.category.label;
            SettingColor base = colorOf(detection.category);

            NametagUtils.begin(screen, event.drawContext);
            MobHeadIcon icon = headIconFor(detection.mob);
            if (icon != null) {
                drawTwoLineLabel(event.drawContext, icon, " · " + pluralWord(detection.chests, "chest"), line2,
                    new Color(base.r, base.g, base.b), factor);
            } else {
                String line1 = capitalize(detection.mob) + " · " + pluralWord(detection.chests, "chest");
                drawTwoLineLabel(event.drawContext, line1, line2, new Color(base.r, base.g, base.b), factor);
            }
            NametagUtils.end(event.drawContext);
        }

        Map<Long, String> outcomes = latestOutcomes();

        for (Site site : sites.values()) {
            if (site.category == Category.INTACT && !showIntact.get()) continue;
            if (site.category == Category.EMPTY && !showEmpty.get()) continue;
            if (ignoreBelowZero.get() && site.center.getY() < 0) continue;

            BlockPos pos = site.center;
            long siteKey = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
            String outcome = outcomes.get(siteKey);
            boolean failed = isFailureOutcome(outcome);
            boolean looted = outcome != null ? !failed : exitLootedKeys.contains(siteKey);

            if (looted && lootedStyle.get() == LootedStyle.HIDE) continue;
            if (looted && lootedStyle.get() == LootedStyle.CROSS) continue;

            if (eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistanceSq) continue;

            float factor = site.animation.factor();
            if (factor <= 0.0f) continue;

            org.joml.Vector3d screen = new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5);
            if (!NametagUtils.to2D(screen, effectiveLabelScale(Math.sqrt(eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))))) continue;

            NametagUtils.begin(screen, event.drawContext);

            if (failed) {
                String text = "✗ failed · " + shortFailReason(outcome);
                drawSimpleLabel(event.drawContext, text, AMBER, factor);
            } else if (looted) {
                drawSimpleLabel(event.drawContext, "✓ looted", LOOTED_GREEN, factor);
            } else {
                StringBuilder line2b = new StringBuilder();
                line2b.append(site.category.label).append(" · score ").append(site.score);
                String signals = signalWords(site);
                if (!signals.isEmpty()) line2b.append(" · ").append(signals);

                SettingColor base = colorOf(site.category);
                MobHeadIcon icon = site.hasSpawner ? headIconFor(site.mob) : null;
                if (icon != null) {
                    drawTwoLineLabel(event.drawContext, icon, " · " + pluralWord(site.containers, "chest"),
                        line2b.toString(), new Color(base.r, base.g, base.b), factor);
                } else {
                    String line1 = (site.hasSpawner ? capitalize(site.mob) : "no spawner")
                        + " · " + pluralWord(site.containers, "chest");
                    drawTwoLineLabel(event.drawContext, line1, line2b.toString(), new Color(base.r, base.g, base.b), factor);
                }
            }

            NametagUtils.end(event.drawContext);
        }
        if (detectShipwrecks.get()) renderShipwreckLabels(event, outcomes, eye, maxDistance, maxDistanceSq);
    }

    private boolean isShipwreckLooted(BlockPos chest, Map<Long, String> outcomes) {
        String outcome = outcomes.get(chest.asLong());
        return "shipwreck:processed".equals(outcome) || "shipwreck:shulkers".equals(outcome) || "shipwreck:chest absent".equals(outcome);
    }

    private int shipwreckLootedChests(ShipwreckSite wreck, Map<Long, String> outcomes) {
        int looted = 0;
        for (ShipwreckSite.Chest chest : wreck.chests()) {
            if (chest.status() == ShipwreckSite.ChestStatus.PRESENT
                && (isShipwreckLooted(chest.pos(), outcomes) || isFailureOutcome(outcomes.get(chest.pos().asLong())))) looted++;
        }
        return looted;
    }

    private int shipwreckPresentChests(ShipwreckSite wreck) {
        int present = 0;
        for (ShipwreckSite.Chest chest : wreck.chests()) if (chest.status() == ShipwreckSite.ChestStatus.PRESENT) present++;
        return present;
    }

    private float shipwreckRenderFactor(ShipwreckSite wreck, double distanceSq, double maxDistance, double maxDistanceSq) {
        Animation animation = shipwreckAnimations.computeIfAbsent(wreck, ignored -> new Animation(fadeTime.get()));
        animation.setVisible(distanceSq <= maxDistanceSq);
        float factor = animation.factor();
        if (distanceFade.get()) factor *= (float) (1.0 - Math.min(0.75, Math.sqrt(distanceSq) / maxDistance * 0.75));
        return factor;
    }

    private void renderShipwrecks(Render3DEvent event, Map<Long, String> outcomes, Vec3d eye, double maxDistance, double maxDistanceSq) {
        if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;
        for (ShipwreckSite wreck : ShipwreckDetector.foundSites()) {
            ShipwreckSite.Bounds bounds = wreck.bounds();
            if (bounds == null || !shipwreckInOldChunk(wreck)) continue;
            int present = shipwreckPresentChests(wreck);
            boolean looted = present > 0 && shipwreckLootedChests(wreck, outcomes) == present;
            if (looted && lootedStyle.get() == LootedStyle.HIDE) continue;
            double distanceSq = eye.squaredDistanceTo((bounds.minX() + bounds.maxX() + 1.0) / 2,
                (bounds.minY() + bounds.maxY() + 1.0) / 2, (bounds.minZ() + bounds.maxZ() + 1.0) / 2);
            float factor = shipwreckRenderFactor(wreck, distanceSq, maxDistance, maxDistanceSq);
            if (factor <= 0.0f) continue;
            SettingColor base = looted ? farmedColor.get() : shipwreckColor.get();
            Color line = new Color(base.r, base.g, base.b, (int) (base.a * factor));
            event.renderer.box(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1.0, bounds.maxY() + 1.0,
                bounds.maxZ() + 1.0, new Color(0, 0, 0, 0), line, ShapeMode.Lines, 0);
            for (ShipwreckSite.Chest chest : wreck.chests()) {
                if (chest.status() != ShipwreckSite.ChestStatus.PRESENT) continue;
                boolean chestLooted = isShipwreckLooted(chest.pos(), outcomes);
                boolean chestFailed = isFailureOutcome(outcomes.get(chest.pos().asLong()));
                if (chestLooted && lootedStyle.get() == LootedStyle.HIDE) continue;
                base = chestLooted ? farmedColor.get() : shipwreckColor.get();
                line = new Color(base.r, base.g, base.b, (int) (base.a * factor));
                Color side = new Color(base.r, base.g, base.b, (int) (base.a * factor * 0.25));
                BlockPos pos = chest.pos();
                event.renderer.box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0,
                    pos.getZ() + 1.0, side, line, shapeMode.get(), 0);
                if (chestFailed) drawTopCross(event, pos, new Color(AMBER.r, AMBER.g, AMBER.b, (int) (255 * factor)));
                else if (chestLooted && lootedStyle.get() == LootedStyle.CROSS) drawTopCross(event, pos, line);
            }
        }
    }

    private boolean shipwreckInOldChunk(ShipwreckSite wreck) {
        PaletteNewChunks module = ModuleManager.getModule(PaletteNewChunks.class);
        if (module == null) return true;
        for (ShipwreckSite.Chest chest : wreck.chests()) {
            if (chest.status() == ShipwreckSite.ChestStatus.PRESENT
                && module.isInverseNewChunk(chest.pos().getX() >> 4, chest.pos().getZ() >> 4, net.minecraft.world.World.OVERWORLD)) return true;
        }
        return false;
    }

    private void renderShipwreckLabels(meteordevelopment.meteorclient.events.render.Render2DEvent event,
                                      Map<Long, String> outcomes, Vec3d eye, double maxDistance, double maxDistanceSq) {
        if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;
        for (ShipwreckSite wreck : ShipwreckDetector.foundSites()) {
            ShipwreckSite.Bounds bounds = wreck.bounds();
            if (bounds == null || !shipwreckInOldChunk(wreck)) continue;
            int present = shipwreckPresentChests(wreck), looted = shipwreckLootedChests(wreck, outcomes);
            boolean complete = present > 0 && looted == present;
            if (complete && lootedStyle.get() == LootedStyle.HIDE) continue;
            double x = (bounds.minX() + bounds.maxX() + 1.0) / 2, z = (bounds.minZ() + bounds.maxZ() + 1.0) / 2;
            double distanceSq = eye.squaredDistanceTo(x, (bounds.minY() + bounds.maxY() + 1.0) / 2, z);
            if (distanceSq > maxDistanceSq) continue;
            float factor = shipwreckRenderFactor(wreck, distanceSq, maxDistance, maxDistanceSq);
            if (factor <= 0.0f) continue;
            org.joml.Vector3d screen = new org.joml.Vector3d(x, bounds.maxY() + 1.4, z);
            if (!NametagUtils.to2D(screen, effectiveLabelScale(Math.sqrt(distanceSq)))) continue;
            SettingColor base = complete ? farmedColor.get() : shipwreckColor.get();
            NametagUtils.begin(screen, event.drawContext);
            drawSimpleLabel(event.drawContext, "Shipwreck " + looted + "/" + present + " chests", new Color(base.r, base.g, base.b), factor);
            NametagUtils.end(event.drawContext);
        }
    }

    private AreaLoader areaLoaderModule() { return Modules.get().get(AreaLoader.class); }

    private Module selectedFlightModule() {
        return switch (flightModule.get()) {
            case AreaLoader -> Modules.get().get(AreaLoader.class);
            case RocketFly -> Modules.get().get(RocketFly.class);
            case WaypointFollower -> Modules.get().get(WaypointFollower.class);
            case TrailFollower -> Modules.get().get(TrailFollower.class);
            case Fly300km -> Modules.get().get(com.hunterbuddy.modules.Fly300km.class);
            case None -> null;
        };
    }
    private boolean fly300kmActiveButNotSelected() {
        Module fly = Modules.get().get(com.hunterbuddy.modules.Fly300km.class);
        return fly != null && fly.isActive() && flightModule.get() != FlightModule.Fly300km;
    }
    private Pitch40 pitch40Module() { return Modules.get().get(Pitch40.class); }
    private ElytraRecast elytraRecastModule() { return Modules.get().get(ElytraRecast.class); }
    private HMine mlepMineModule() { return Modules.get().get(HMine.class); }
    private AutoFlyingRegear autoFlyingRegearModule() { return Modules.get().get(AutoFlyingRegear.class); }
    private WaypointFollower waypointFollowerModule() { return Modules.get().get(WaypointFollower.class); }
    private AutoEatSync autoEatSyncModule() { return Modules.get().get(AutoEatSync.class); }
    private KillAuraPlus killAuraPlusModule() { return Modules.get().get(KillAuraPlus.class); }
    private ElytraSwap elytraSwapModule() { return Modules.get().get(ElytraSwap.class); }

    private void setChestplateHold(boolean hold) {
        ElytraSwap es = elytraSwapModule();
        if (es != null) es.setChestplateHold(hold);
    }

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
        exitLootedKeys.remove(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        saveVisited();
    }

    private void autoLog(String tag, BlockPos pos, String details) {
        if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] {} site={},{},{} {}",
            tag, pos.getX(), pos.getY(), pos.getZ(), details);
    }

    private void enterState(AutoState next, String details) {
        if (isShipwreck() && autoShipwreckRun != null && autoShipwreckRun.returning()
            && (next == AutoState.WATER_JUMP || next == AutoState.CLIMB || next == AutoState.OBSIDIAN_PLATFORM
                || next == AutoState.TAKEOFF || next == AutoState.TAKEOFF_SPOT || next == AutoState.SHAFT_ALIGN || next == AutoState.SHAFT_FLY
                || next == AutoState.TUNNEL || next == AutoState.ROOM_CLIMB)) {
            next = mc.player.isTouchingWater() ? AutoState.SWIM_UP : AutoState.ASCEND;
        }
        if (state == AutoState.EXIT && next != AutoState.EXIT) restoreExitReturnSettings();
        if (state == AutoState.TUNNEL && next != AutoState.TUNNEL) restoreTunnelSettings();
        if (autoSite != null && next != AutoState.RESUME && next != AutoState.FAILED) protectAutoThrowawayItems();
        state = next;
        stateTicks = 0;
        setChestplateHold(autoChestplateAllowed && (next == AutoState.DIG || next == AutoState.SIDE_DIG
            || next == AutoState.ROOM || next == AutoState.ROOM_CLIMB || next == AutoState.TUNNEL));
        autoLog(next.name(), autoSite.center, details);
    }

    private void protectAutoThrowawayItems() {
        if (autoSavedThrowawayItems != null) return;
        autoSavedThrowawayItems = BaritoneAPI.getSettings().acceptableThrowawayItems.value;
        List<Item> allowed = new ArrayList<>(autoSavedThrowawayItems);
        BaritoneAPI.getSettings().acceptableThrowawayItems.value = allowed;
    }

    private void restoreAutoThrowawayItems() {
        restoreTunnelSettings();
        if (autoSavedThrowawayItems == null) return;
        BaritoneAPI.getSettings().acceptableThrowawayItems.value = autoSavedThrowawayItems;
        autoSavedThrowawayItems = null;
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

    private boolean waitingForElytra(String stateLabel) {
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            autoElytraWaitLogged = false;
            return false;
        }
        if (!InvUtils.find(Items.ELYTRA).found()) {
            autoElytraWaitLogged = false;
            return false;
        }
        if (!autoElytraWaitLogged) {
            autoLog(stateLabel, autoSite.center, "waiting for elytra");
            autoElytraWaitLogged = true;
        }
        if (mc.player.isTouchingWater()) {
            Utils.setPressed(mc.options.jumpKey, true);
        } else {
            releaseAllAutoKeys();
        }
        return true;
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

    private boolean hasTwentyDryOpenBelow(int x, int y, int z, BlockPos.Mutable cursor) {
        for (int dy = 1; dy <= 20; dy++) {
            cursor.set(x, y - dy, z);
            BlockState state = mc.world.getBlockState(cursor);
            if (!isOpenState(state, cursor) || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private int descendPastFloatingBlocks(BlockPos siteCenter, int x, int z, int y, int minY, boolean skipWater, BlockPos.Mutable cursor) {
        while (y > minY) {
            cursor.set(x, y, z);
            BlockState state = mc.world.getBlockState(cursor);
            if ((!state.getFluidState().isIn(FluidTags.WATER) || skipWater) && isSurfaceSkippable(state, cursor)) {
                y--;
                continue;
            }
            if (!hasTwentyDryOpenBelow(x, y, z, cursor)) break;
            boolean supported = true;
            for (int dy = 1; dy <= 2 && supported; dy++) {
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    cursor.set(x + dir.getOffsetX(), y - dy, z + dir.getOffsetZ());
                    BlockState belowNeighbor = mc.world.getBlockState(cursor);
                    if (isOpenState(belowNeighbor, cursor) && belowNeighbor.getFluidState().isEmpty()) {
                        supported = false;
                        break;
                    }
                }
            }
            if (supported) {
                if (siteCenter != null && lidBlockLogged.add(siteCenter)) {
                    autoLog("PLAN", siteCenter, String.format("column %d,%d lid at y=%d kept as top", x, z, y));
                }
                break;
            }
            if (siteCenter != null && floatingBlockLogged.add(siteCenter)) {
                autoLog("PLAN", siteCenter, String.format("column %d,%d floating block at y=%d ignored", x, z, y));
            }
            y--;
        }
        return y;
    }

    private int descendPastFloatingBlockTop(BlockPos siteCenter, int x, int z, int y, int minY, BlockPos.Mutable cursor) {
        while (y > minY && hasTwentyDryOpenBelow(x, y, z, cursor)) {
            boolean supported = true;
            for (int dy = 1; dy <= 2 && supported; dy++) {
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    cursor.set(x + dir.getOffsetX(), y - dy, z + dir.getOffsetZ());
                    BlockState belowNeighbor = mc.world.getBlockState(cursor);
                    if (isOpenState(belowNeighbor, cursor) && belowNeighbor.getFluidState().isEmpty()) {
                        supported = false;
                        break;
                    }
                }
            }
            if (supported) {
                if (siteCenter != null && lidBlockLogged.add(siteCenter)) {
                    autoLog("PLAN", siteCenter, String.format("column %d,%d lid at y=%d kept as top", x, z, y));
                }
                break;
            }
            if (siteCenter != null && floatingBlockLogged.add(siteCenter)) {
                autoLog("PLAN", siteCenter, String.format("column %d,%d floating block at y=%d ignored", x, z, y));
            }
            y--;
            while (y > minY) {
                cursor.set(x, y, z);
                BlockState state = mc.world.getBlockState(cursor);
                if (!isOpenState(state, cursor) || !state.getFluidState().isEmpty()) break;
                y--;
            }
        }
        return y;
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
        return evaluateColumnResult(room, x, z, maxFallAllowed).plan();
    }

    private ColumnEval evaluateColumnResult(Room room, int x, int z, int maxFallAllowed) {
        return evaluateColumnResult(null, room, x, z, maxFallAllowed, false);
    }

    private ColumnEval evaluateColumnResult(BlockPos siteCenter, Room room, int x, int z, int maxFallAllowed, boolean ignoreCobweb) {
        if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return new ColumnEval(null, "chunk not loaded");
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        BlockState floorState = mc.world.getBlockState(cursor.set(x, room.cy() - 1, z));
        if (isOpenState(floorState, cursor)) {
            int fy = room.cy() - 1;
            BlockState s = floorState;
            while (isOpenState(s, cursor) && fy > mc.world.getBottomY()) {
                fy--;
                s = mc.world.getBlockState(cursor.set(x, fy, z));
            }
            if (isLava(s)) return new ColumnEval(null, "lava");
        }

        int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        topY = descendPastFloatingBlocks(siteCenter, x, z, topY, room.cy() + 4, false, cursor);
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
            if (isLava(mc.world.getBlockState(cursor.set(x, fond, z)))) return new ColumnEval(null, "lava");
            startY = fond + 1;
            exitY = waterTop + 1;
            water = true;
            if (startY < room.cy() + 4) return new ColumnEval(null, "too low start");
            if (startY == room.cy() + 4
                && (isLava(mc.world.getBlockState(cursor.set(x, startY, z)))
                    || isLava(mc.world.getBlockState(cursor.set(x + 1, startY, z)))
                    || isLava(mc.world.getBlockState(cursor.set(x - 1, startY, z)))
                    || isLava(mc.world.getBlockState(cursor.set(x, startY, z + 1)))
                    || isLava(mc.world.getBlockState(cursor.set(x, startY, z - 1))))) {
                return new ColumnEval(null, "lava");
            }
        } else {
            int y = topY;
            startY = y + 1;
            exitY = y + 1;
            water = false;
        }

        if (startY < room.cy() - 16) return new ColumnEval(null, "too low start");

        int[][] neighbors = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int blocksToBreak = 0, waterCells = 0, maxGap = 0, airRun = 0;

        for (int y = startY - 1; y >= room.cy() + 4; y--) {
            for (int[] n : neighbors) {
                cursor.set(x + n[0], y, z + n[1]);
                if (isLava(mc.world.getBlockState(cursor))) return new ColumnEval(null, "lava");
            }

            BlockState center = mc.world.getBlockState(cursor.set(x, y, z));
            if (!ignoreCobweb && center.getBlock() == Blocks.COBWEB) return new ColumnEval(null, "cobweb");
            boolean isFluidWater = center.getFluidState().isIn(FluidTags.WATER);

            if (isFluidWater) {
                waterCells++;
                airRun = 0;
            } else if (isOpenState(center, cursor)) {
                airRun++;
                maxGap = Math.max(maxGap, airRun);
            } else {
                if (airRun > maxFallAllowed) return new ColumnEval(null, "fall " + airRun);
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
        } else {
            int dryTopY = computeRim(x, z, startY).holeTopY();
            if (dryTopY - exitY >= 5 && dryOpenColumn(x, z, exitY, dryTopY)) holeTopY = dryTopY;
        }

        return new ColumnEval(new ColumnPlan(x, z, startY, exitY, water, blocksToBreak, waterCells, maxGap, skyBlocked, holeTopY, rim, false, null), null);
    }

    private RimResult computeRim(int x, int z, int startY) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos rim = null;
        int rimFeet = Integer.MAX_VALUE;
        int topFeet = Integer.MIN_VALUE;
        double rimDistSq = Double.MAX_VALUE;
        int[][] rimNeighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] n : rimNeighbors) {
            int nx = x + n[0], nz = z + n[1];
            if (!mc.world.getChunkManager().isChunkLoaded(nx >> 4, nz >> 4)) continue;
            int g = descendPastFloatingBlocks(null, nx, nz, mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, nx, nz) - 1, mc.world.getBottomY(), true, cursor);
            BlockState gState = mc.world.getBlockState(cursor.set(nx, g, nz));
            int feet = g + 1;

            if (feet < startY + 2) continue;
            if (isOpenState(gState, cursor) || !gState.getFluidState().isEmpty()) continue;
            topFeet = Math.max(topFeet, feet);

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
            if (feet < rimFeet || (feet == rimFeet && distSq < rimDistSq)) {
                rimFeet = feet;
                rimDistSq = distSq;
                rim = new BlockPos(nx, feet, nz);
            }
        }

        int holeTopY = Math.max(startY, topFeet);
        return new RimResult(rim, holeTopY);
    }

    private BlockPos probeLandingSpot(int x, int z, int holeTopY) {
        if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return null;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        int y = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        BlockState state = mc.world.getBlockState(cursor.set(x, y, z));
        int steps = 0;
        while (steps < 12 && isSurfaceSkippable(state, cursor) && y > mc.world.getBottomY()) {
            y--;
            steps++;
            state = mc.world.getBlockState(cursor.set(x, y, z));
        }
        if (isLava(state) || isSurfaceSkippable(state, cursor)) return null;

        int groundY = y + 1;
        if (holeTopY - groundY > 12) return null;
        if (groundY - holeTopY > 6) return null;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!mc.world.getChunkManager().isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return null;
                for (int dy = 1; dy <= 9; dy++) {
                    cursor.set(x + dx, groundY + dy, z + dz);
                    if (!isOpenState(mc.world.getBlockState(cursor), cursor)) return null;
                }
            }
        }

        return new BlockPos(x, groundY, z);
    }

    private BlockPos chooseLandSpot(ColumnPlan plan, int ringRadius) {
        int holeTopY = plan.water() ? plan.startY() : (plan.rim() != null ? plan.rim().getY() : plan.holeTopY());
        BlockPos best = probeLandingSpot(plan.x(), plan.z(), holeTopY);
        double bestDistSq = best != null ? 0.0 : Double.MAX_VALUE;

        for (int i = 0; i < 12; i++) {
            double ang = Math.toRadians(i * 30.0);
            int cx = plan.x() + (int) Math.round(Math.cos(ang) * ringRadius);
            int cz = plan.z() + (int) Math.round(Math.sin(ang) * ringRadius);
            double distSq = (double) (cx - plan.x()) * (cx - plan.x()) + (double) (cz - plan.z()) * (cz - plan.z());
            if (distSq >= bestDistSq) continue;

            BlockPos spot = probeLandingSpot(cx, cz, holeTopY);
            if (spot == null) continue;

            best = spot;
            bestDistSq = distSq;
        }

        return best;
    }

    private ColumnPlan evaluateExistingShaft(Site site, DugHoles.Feature shaft, boolean straightDown, boolean logShafts) {
        Room room = site.room;
        int sx = shaft.minX(), sz = shaft.minZ();
        int shaftOpen = shaft.maxY() - shaft.minY() + 1;
        if (shaftOpen < 4) {
            if (logShafts) autoLog("PLAN", site.center, String.format("existing shaft %d,%d refused: too short", sx, sz));
            return null;
        }
        boolean insideInterior = sx >= room.cx() - room.k() && sx <= room.cx() + room.k()
            && sz >= room.cz() - room.p() && sz <= room.cz() + room.p();

        ColumnPlan shaftPlan;

        if (insideInterior) {
            if (floorOpenBelow(room, sx, sz)) {
                if (logShafts) autoLog("PLAN", site.center, String.format("existing shaft %d,%d refused: open floor below", sx, sz));
                return null;
            }
            ColumnEval shaftEval = evaluateColumnResult(site.center, room, sx, sz, autoMaxFall.get(), false);
            shaftPlan = shaftEval.plan();
            if (shaftPlan == null) {
                if (logShafts) autoLog("PLAN", site.center, String.format("existing shaft %d,%d refused: %s", sx, sz,
                    shaftEval.rejectReason() != null ? shaftEval.rejectReason() : "plan null"));
                return null;
            }
            BlockPos.Mutable fallCursor = new BlockPos.Mutable();
            int fallRun = 0;
            boolean shaftCobweb = false;
            for (int fy = (shaftPlan.rim() != null ? shaftPlan.rim().getY() : shaftPlan.holeTopY()) - 1; fy >= shaftPlan.startY(); fy--) {
                BlockState fallState = mc.world.getBlockState(fallCursor.set(sx, fy, sz));
                if (fallState.getBlock() == Blocks.COBWEB) shaftCobweb = true;
                if (fallState.getFluidState().isIn(FluidTags.WATER)) {
                    fallRun = 0;
                } else if (isOpenState(fallState, fallCursor)) {
                    fallRun++;
                }
            }
            if (shaftCobweb) {
                if (logShafts) autoLog("PLAN", site.center, String.format("existing shaft %d,%d refused: cobweb", sx, sz));
                return null;
            }
            if (fallRun > autoMaxFall.get()) {
                if (logShafts) autoLog("PLAN", site.center, String.format("existing shaft %d,%d refused: fall", sx, sz));
                return null;
            }
        } else {
            if (straightDown) return null;
            SideHoleAttempt attempt = evaluateSideHolePosition(site.center, room, sx, sz);
            if (attempt.plan() == null) {
                if (logShafts) autoLog("PLAN", site.center, String.format("existing shaft %d,%d refused: %s", sx, sz,
                    attempt.rejectReason() != null ? attempt.rejectReason() : "no plan"));
                return null;
            }
            shaftPlan = attempt.plan();
        }

        return shaftPlan;
    }

    private ColumnPlan planColumn(Site site) {
        if (site.shipwreck != null) return planShipwreckColumn(site.center);
        Room room = site.room;
        boolean logShafts = entryShaftLogged.add(site.center);
        boolean straightDown = autoAbandonedOnce.contains(site.center);

        ColumnPlan bestShaftPlan = null;
        String bestShaftLabel = null;
        int bestShaftOpen = 0;

        for (DugHoles.Feature shaft : site.entryShafts) {
            ColumnPlan shaftPlan = evaluateExistingShaft(site, shaft, straightDown, logShafts);
            if (shaftPlan == null) continue;

            if (bestShaftPlan == null || shaftPlan.blocksToBreak() < bestShaftPlan.blocksToBreak()) {
                bestShaftPlan = shaftPlan;
                bestShaftLabel = insideInterior(room, shaft.minX(), shaft.minZ()) ? "inside" : "beside";
                bestShaftOpen = shaft.maxY() - shaft.minY() + 1;
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
        List<ColumnReject> columnRejects = new ArrayList<>();
        ColumnPlan fallFallback = null;
        String fallFallbackReason = null;
        ColumnPlan openFloorFallback = null;
        ColumnPlan cobwebFallback = null;
        boolean cobwebOnlyFallback = true;
        for (int[] c : candidates) {
            if (floorOpenBelow(room, c[0], c[1])) {
                columnRejects.add(new ColumnReject(c[0], c[1], "open floor below"));
                cobwebOnlyFallback = false;
                if (openFloorFallback == null) {
                    ColumnPlan plan = evaluateColumnResult(site.center, room, c[0], c[1], autoMaxFall.get(), false).plan();
                    if (plan != null) openFloorFallback = plan;
                }
                continue;
            }
            ColumnEval eval = evaluateColumnResult(site.center, room, c[0], c[1], autoMaxFall.get(), false);
            ColumnPlan plan = eval.plan();
            if (plan == null) {
                String reason = eval.rejectReason() != null ? eval.rejectReason() : "no plan";
                columnRejects.add(new ColumnReject(c[0], c[1], reason));
                if (!"cobweb".equals(reason)) cobwebOnlyFallback = false;
                if (fallFallback == null && reason.startsWith("fall ")) {
                    ColumnPlan retry = evaluateColumnResult(site.center, room, c[0], c[1], Integer.MAX_VALUE, false).plan();
                    if (retry != null) {
                        fallFallback = retry;
                        fallFallbackReason = reason;
                    }
                } else if (cobwebFallback == null && "cobweb".equals(reason)) {
                    ColumnPlan retry = evaluateColumnResult(site.center, room, c[0], c[1], autoMaxFall.get(), true).plan();
                    if (retry != null) cobwebFallback = retry;
                }
                continue;
            }
            if (!plan.skyBlocked()) {
                if (column == null || betterColumnPlan(room, plan, column)) column = plan;
            } else if (bestBlocked == null || betterColumnPlan(room, plan, bestBlocked)) {
                bestBlocked = plan;
            }
        }
        if (column == null) column = bestBlocked;

        if (bestShaftPlan != null) {
            if (bestShaftPlan.blocksToBreak() <= 5 || column == null || column.skyBlocked()
                || bestShaftPlan.blocksToBreak() < column.blocksToBreak()) {
                if (sideHoleUseLogged.add(site.center)) autoLog("PLAN", site.center, String.format("using existing shaft %d,%d %s open=%d missing=%d",
                    bestShaftPlan.x(), bestShaftPlan.z(), bestShaftLabel, bestShaftOpen, bestShaftPlan.blocksToBreak()));
                return bestShaftPlan;
            }
            if (logShafts) autoLog("PLAN", site.center, String.format("existing shaft %d,%d not used: missing=%d column=%d",
                bestShaftPlan.x(), bestShaftPlan.z(), bestShaftPlan.blocksToBreak(), column.blocksToBreak()));
        }

        ColumnPlan side = straightDown ? null : findSideHole(site);
        if (side != null && (column == null || column.skyBlocked() || side.blocksToBreak() + 10 <= column.blocksToBreak())) {
            if (sideHoleUseLogged.add(site.center)) autoLog("PLAN", site.center, String.format("using side hole %d,%d cost=%d instead of column cost=%s",
                side.x(), side.z(), side.blocksToBreak(),
                column != null ? String.valueOf(column.blocksToBreak()) : "none"));
            return side;
        }

        if (column == null) {
            ColumnPlan fallback = fallFallback != null ? fallFallback : openFloorFallback != null ? openFloorFallback
                : cobwebOnlyFallback ? cobwebFallback : null;
            String fallbackReason = fallFallback != null ? fallFallbackReason : openFloorFallback != null ? "open floor below" : "cobweb";
            if (noSafeColumnLogged.add(site.center)) {
                for (ColumnReject reject : columnRejects) {
                    autoLog("PLAN", site.center, String.format("column %d,%d refused: %s", reject.x(), reject.z(), reject.reason()));
                }
                if (fallback != null) {
                    autoLog("PLAN", site.center, String.format("no safe column, digging anyway at %d,%d reason=%s",
                        fallback.x(), fallback.z(), fallbackReason));
                }
            }
            if (fallback != null) return fallback;
        }

        return column;
    }

    private boolean betterColumnPlan(Room room, ColumnPlan candidate, ColumnPlan current) {
        if (candidate.blocksToBreak() != current.blocksToBreak()) return candidate.blocksToBreak() < current.blocksToBreak();
        int cdx = candidate.x() - room.cx();
        int cdz = candidate.z() - room.cz();
        int bdx = current.x() - room.cx();
        int bdz = current.z() - room.cz();
        return cdx * cdx + cdz * cdz < bdx * bdx + bdz * bdz;
    }

    private boolean floorOpenBelow(Room room, int x, int z) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockState floorState = mc.world.getBlockState(cursor.set(x, room.cy() - 1, z));
        return isOpenState(floorState, cursor) || !floorState.getFluidState().isEmpty();
    }

    private SideHoleAttempt evaluateSideHolePosition(Room room, int x, int z) {
        return evaluateSideHolePosition(null, room, x, z);
    }

    private SideHoleAttempt evaluateSideHolePosition(BlockPos siteCenter, Room room, int x, int z) {
        SideHoleCost sc = computeSideHoleCost(siteCenter, room, x, z);
        if (sc == null) return new SideHoleAttempt(null, null);
        if (sc.rejectReason() != null) return new SideHoleAttempt(null, sc.rejectReason());
        return finishSideHolePlan(siteCenter, room, x, z, sc, false);
    }

    private SideHoleCost computeSideHoleCost(Room room, int x, int z) {
        return computeSideHoleCost(null, room, x, z);
    }

    private SideHoleCost computeSideHoleCost(BlockPos siteCenter, Room room, int x, int z) {
        int cx = room.cx(), cz = room.cz(), cy = room.cy(), k = room.k(), p = room.p();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return null;

        int y = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        y = descendPastFloatingBlocks(siteCenter, x, z, y, cy - 13, true, cursor);
        BlockState state = mc.world.getBlockState(cursor.set(x, y, z));

        if (isLava(state)) return new SideHoleCost(y + 1, 0, 0, 0, 0, "lava");

        int bottomFeet = y + 1;

        int tx = Math.max(cx - k, Math.min(cx + k, x));
        int tz = Math.max(cz - p, Math.min(cz + p, z));

        BlockPos entry = resolveSideHoleEntry(room, x, z, tx, tz, cursor);
        if (entry == null) {
            return new SideHoleCost(bottomFeet, tx, tz, 0, 0, "entry blocked");
        }
        tx = entry.getX();
        tz = entry.getZ();

        if (bottomFeet < cy) {
            if (bottomFeet < cy - 12) return new SideHoleCost(bottomFeet, tx, tz, 0, 0, "too deep");
            SideHoleCost deep = computeDeepSideHoleCost(room, x, z, bottomFeet, tx, tz, cursor);
            if (deep.rejectReason() != null) return deep;
            return deep;
        }

        List<BlockPos> horizCells = new ArrayList<>();
        int hx = x, hz = z;
        if (sideEntryAlongZ(room, x, z)) {
            while (hz != tz) {
                hz += Integer.signum(tz - hz);
                horizCells.add(new BlockPos(hx, cy, hz));
            }
        }
        while (hx != tx) {
            hx += Integer.signum(tx - hx);
            horizCells.add(new BlockPos(hx, cy, hz));
        }
        while (hz != tz) {
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
        cursor.set(x, cy + 1, z);
        if (mc.world.getBlockState(cursor).getBlock() == Blocks.SPAWNER || isContainerPresent(cursor)) tunnelBlocked = true;
        if (tunnelBlocked) return new SideHoleCost(bottomFeet, tx, tz, 0, 0, "tunnel blocked");

        int[][] neighbors = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean lavaFound = false;
        boolean fallReject = false;
        int vertBlocksToBreak = 0, vertAirRun = 0;
        boolean cobwebFound = false;

        outer:
        for (int vy = bottomFeet - 1; vy >= cy; vy--) {
            for (int[] n : neighbors) {
                if (isLava(mc.world.getBlockState(cursor.set(x + n[0], vy, z + n[1])))) { lavaFound = true; break outer; }
            }
            BlockState vCenter = mc.world.getBlockState(cursor.set(x, vy, z));
            if (vCenter.getBlock() == Blocks.COBWEB) cobwebFound = true;
            if (isOpenState(vCenter, cursor)) {
                vertAirRun++;
            } else {
                if (vertAirRun > autoMaxFall.get()) { fallReject = true; break; }
                vertAirRun = 0;
                vertBlocksToBreak++;
                if (vertBlocksToBreak > 20) break;
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
        if (!lavaFound) {
            for (int[] n : neighbors) {
                if (isLava(mc.world.getBlockState(cursor.set(x + n[0], cy + 1, z + n[1])))) { lavaFound = true; break; }
            }
        }
        if (lavaFound) return new SideHoleCost(bottomFeet, tx, tz, 0, vertBlocksToBreak, "lava");
        if (fallReject) return new SideHoleCost(bottomFeet, tx, tz, 0, vertBlocksToBreak, "fall");

        boolean noFloor = isOpenState(mc.world.getBlockState(cursor.set(x, cy - 1, z)), cursor);
        for (BlockPos hc : horizCells) {
            if (noFloor) break;
            noFloor = isOpenState(mc.world.getBlockState(cursor.set(hc.getX(), cy - 1, hc.getZ())), cursor);
        }
        if (noFloor) return new SideHoleCost(bottomFeet, tx, tz, 0, vertBlocksToBreak, "no floor");

        int tunnelBlocksToBreak = 0;
        for (BlockPos hc : horizCells) {
            for (int dyy = 0; dyy <= 1; dyy++) {
                cursor.set(hc.getX(), cy + dyy, hc.getZ());
                BlockState tunnelState = mc.world.getBlockState(cursor);
                if (tunnelState.getBlock() == Blocks.COBWEB) cobwebFound = true;
                if (!isOpenState(tunnelState, cursor)) tunnelBlocksToBreak++;
            }
        }

        int cost = vertBlocksToBreak + tunnelBlocksToBreak;
        if (cost > 20) return new SideHoleCost(bottomFeet, tx, tz, cost, vertBlocksToBreak, "cost cap");
        if (cobwebFound) return new SideHoleCost(bottomFeet, tx, tz, cost, vertBlocksToBreak, "cobweb");

        return new SideHoleCost(bottomFeet, tx, tz, cost, vertBlocksToBreak, null);
    }

    private SideHoleCost computeDeepSideHoleCost(Room room, int x, int z, int bottomFeet, int tx, int tz, BlockPos.Mutable cursor) {
        int cy = room.cy();
        RimResult rr = computeRim(x, z, bottomFeet);
        int entryY = rr.rim() != null ? rr.rim().getY() : bottomFeet;
        int fallRun = 0;
        int entryBlocks = 0;
        for (int vy = entryY - 1; vy >= bottomFeet; vy--) {
            BlockState shaftState = mc.world.getBlockState(cursor.set(x, vy, z));
            if (isLava(shaftState)) return new SideHoleCost(bottomFeet, tx, tz, 0, entryBlocks, "lava");
            if (shaftState.getBlock() == Blocks.COBWEB) return new SideHoleCost(bottomFeet, tx, tz, 0, entryBlocks, "cobweb");
            if (shaftState.getFluidState().isIn(FluidTags.WATER)) {
                fallRun = 0;
            } else if (isOpenState(shaftState, cursor)) {
                fallRun++;
            } else {
                entryBlocks++;
            }
            if (fallRun > autoMaxFall.get()) return new SideHoleCost(bottomFeet, tx, tz, 0, entryBlocks, "fall");
        }
        int horizontal = Math.abs(x - tx) + Math.abs(z - tz);
        int cost = entryBlocks + (cy - bottomFeet) + horizontal;
        return new SideHoleCost(bottomFeet, tx, tz, cost, entryBlocks, null);
    }

    private boolean sideEntryAlongZ(Room room, int x, int z) {
        boolean outsideX = x < room.cx() - room.k() || x > room.cx() + room.k();
        boolean outsideZ = z < room.cz() - room.p() || z > room.cz() + room.p();
        return !(outsideZ && (!outsideX || Math.abs(z - room.cz()) >= Math.abs(x - room.cx())));
    }

    private BlockPos resolveSideHoleEntry(Room room, int x, int z, int tx, int tz, BlockPos.Mutable cursor) {
        if (!sideHoleEntryBlocked(tx, room.cy(), tz, cursor)) return new BlockPos(tx, room.cy(), tz);

        boolean alongZ = sideEntryAlongZ(room, x, z);
        int axisX = alongZ ? 0 : 1;
        int axisZ = alongZ ? 1 : 0;

        int[][] tries = {{axisX, axisZ}, {-axisX, -axisZ}};
        for (int[] t : tries) {
            int nx = tx + t[0];
            int nz = tz + t[1];
            if (nx < room.cx() - room.k() || nx > room.cx() + room.k() || nz < room.cz() - room.p() || nz > room.cz() + room.p()) continue;
            if (!sideHoleEntryBlocked(nx, room.cy(), nz, cursor)) return new BlockPos(nx, room.cy(), nz);
        }
        return null;
    }

    private boolean sideHoleEntryBlocked(int x, int y, int z, BlockPos.Mutable cursor) {
        cursor.set(x, y, z);
        BlockState state = mc.world.getBlockState(cursor);
        return state.getBlock() == Blocks.SPAWNER || isContainerPresent(cursor);
    }

    private SideHoleAttempt finishSideHolePlan(Room room, int x, int z, SideHoleCost sc) {
        return finishSideHolePlan(null, room, x, z, sc, false);
    }

    private SideHoleAttempt finishSideHolePlan(BlockPos siteCenter, Room room, int x, int z, SideHoleCost sc, boolean ignoreCobweb) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        topY = descendPastFloatingBlockTop(siteCenter, x, z, topY, room.cy() - 2, cursor);
        int exitY = topY + 1;
        boolean water = mc.world.getBlockState(cursor.set(x, topY, z)).getFluidState().isIn(FluidTags.WATER);
        boolean cushion = water && !mc.world.getBlockState(cursor.set(x, topY - 1, z)).getFluidState().isIn(FluidTags.WATER);
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (cushion && mc.world.getBlockState(cursor.set(x + dir.getOffsetX(), topY, z + dir.getOffsetZ())).getFluidState().isIn(FluidTags.WATER)) cushion = false;
        }
        if (cushion) water = false;

        BlockPos rim = null;
        int holeTopY = exitY;
        if (!water) {
            RimResult rr = computeRim(x, z, cushion ? topY + 1 : sc.bottomFeet());
            rim = rr.rim();
            if (rim != null) holeTopY = rr.holeTopY();
        } else {
            int dryTopY = computeRim(x, z, sc.bottomFeet()).holeTopY();
            if (dryTopY - exitY >= 5 && dryOpenColumn(x, z, exitY, dryTopY)) holeTopY = dryTopY;
        }

        if (rim == null && !water && holeTopY <= sc.bottomFeet() + 3) return new SideHoleAttempt(null, "open hole without rim");

        boolean skyBlocked = false;
        for (int sy = holeTopY; sy <= exitY + SKY_CHECK_HEIGHT; sy++) {
            cursor.set(x, sy, z);
            if (!isOpenState(mc.world.getBlockState(cursor), cursor)) { skyBlocked = true; break; }
        }
        if (skyBlocked) return new SideHoleAttempt(null, "sky blocked");

        int entryY = rim != null ? rim.getY() : holeTopY;

        int fallRun = 0;
        for (int fy = entryY - 1; fy >= sc.bottomFeet(); fy--) {
            BlockState fallState = mc.world.getBlockState(cursor.set(x, fy, z));
            if (!ignoreCobweb && fallState.getBlock() == Blocks.COBWEB) return new SideHoleAttempt(null, "cobweb");
            if (fallState.getFluidState().isIn(FluidTags.WATER)) {
                fallRun = 0;
            } else if (isOpenState(fallState, cursor)) {
                fallRun++;
            }
        }
        if (fallRun > autoMaxFall.get()) return new SideHoleAttempt(null, "fall");

        return new SideHoleAttempt(new ColumnPlan(x, z, sc.bottomFeet(), exitY, water, sc.cost(), 0, 0, skyBlocked, holeTopY, rim,
            true, new BlockPos(sc.tx(), room.cy(), sc.tz())), null);
    }

    private ColumnPlan findSideHole(Site site) {
        Room room = site.room;
        int cx = room.cx(), cz = room.cz(), cy = room.cy(), k = room.k(), p = room.p();

        ColumnPlan best = null;
        int bestCost = Integer.MAX_VALUE;
        double bestDistSq = Double.MAX_VALUE;
        ColumnPlan cobwebFallback = null;
        int cobwebFallbackCost = Integer.MAX_VALUE;
        double cobwebFallbackDistSq = Double.MAX_VALUE;
        boolean cobwebOnlyFallback = true;
        List<SideHoleReject> rejects = new ArrayList<>();

        for (int x = cx - k - 7; x <= cx + k + 7; x++) {
            boolean xAligned = x >= cx - k && x <= cx + k;

            for (int z = cz - p - 7; z <= cz + p + 7; z++) {
                boolean zAligned = z >= cz - p && z <= cz + p;
                if (xAligned && zAligned) continue;

                SideHoleCost sc = computeSideHoleCost(site.center, room, x, z);
                if (sc == null) continue;
                if (sc.rejectReason() != null) {
                    rejects.add(new SideHoleReject(x, z, sc.bottomFeet(), sc.rejectReason()));
                    if (!"cobweb".equals(sc.rejectReason())) cobwebOnlyFallback = false;
                    if ("cobweb".equals(sc.rejectReason())) {
                        double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
                        double distSq = dx * dx + dz * dz;
                        if (sc.cost() < cobwebFallbackCost || (sc.cost() == cobwebFallbackCost && distSq < cobwebFallbackDistSq)) {
                            SideHoleAttempt retry = finishSideHolePlan(site.center, room, x, z, sc, true);
                            if (retry.plan() != null) {
                                cobwebFallbackCost = sc.cost();
                                cobwebFallbackDistSq = distSq;
                                cobwebFallback = retry.plan();
                            }
                        }
                    }
                    continue;
                }

                double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
                double distSq = dx * dx + dz * dz;

                if (sc.cost() < bestCost || (sc.cost() == bestCost && distSq < bestDistSq)) {
                    SideHoleAttempt attempt = finishSideHolePlan(site.center, room, x, z, sc, false);
                    if (attempt.plan() == null) {
                        rejects.add(new SideHoleReject(x, z, sc.bottomFeet(), attempt.rejectReason()));
                        if (!"cobweb".equals(attempt.rejectReason())) cobwebOnlyFallback = false;
                        if ("cobweb".equals(attempt.rejectReason())) {
                            SideHoleAttempt retry = finishSideHolePlan(site.center, room, x, z, sc, true);
                            if (retry.plan() != null) {
                                cobwebFallbackCost = sc.cost();
                                cobwebFallbackDistSq = distSq;
                                cobwebFallback = retry.plan();
                            }
                        }
                        continue;
                    }
                    bestCost = sc.cost();
                    bestDistSq = distSq;
                    best = attempt.plan();
                }
            }
        }
        if (best == null && cobwebOnlyFallback) best = cobwebFallback;

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

    private boolean isShipwreck() {
        return autoSite != null && autoSite.shipwreck != null;
    }

    private List<Site> shipwreckTargets() {
        List<Site> targets = new ArrayList<>();
        if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD || !autoFarm.get() || !detectShipwrecks.get()) return targets;
        PaletteNewChunks module = ModuleManager.getModule(PaletteNewChunks.class);
        if (module == null) {
            if (!autoShipwreckPaletteMissingLogged) {
                autoShipwreckPaletteMissingLogged = true;
                if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] skip shipwrecks reason=PaletteNewChunks unavailable");
            }
            return targets;
        }
        for (ShipwreckSite wreck : ShipwreckDetector.foundSites()) {
            ShipwreckSite.Chest chest = wreck.nearestChest(mc.player.getX(), mc.player.getY(), mc.player.getZ(), this::isVisited);
            if (chest == null) continue;
            int chunkX = chest.pos().getX() >> 4, chunkZ = chest.pos().getZ() >> 4;
            if (!module.isInverseNewChunk(chunkX, chunkZ, net.minecraft.world.World.OVERWORLD)) {
                if (autoShipwreckAgeSkipped.add(wreck)) logSkipOnce(wreck.origin(),
                    module.isNewChunk(chunkX, chunkZ, net.minecraft.world.World.OVERWORLD) ? "fresh chunk" : "chunk age unknown");
                continue;
            }
            targets.add(new Site(wreck, chest.pos()));
        }
        return targets;
    }

    private void logShipwreckChest(Site site) {
        int index = 0, count = 0;
        for (ShipwreckSite.Chest chest : site.shipwreck.chests()) {
            if (chest.status() != ShipwreckSite.ChestStatus.PRESENT) continue;
            count++;
            if (chest.pos().equals(site.center)) index = count;
        }
        autoLog("CHEST", site.center, "chest " + index + "/" + count);
    }

    private QualResult evaluateShipwreckQualification(Site site, boolean walkChain) {
        if (!qualifiesForAuto(site)) return QualResult.reject("visited or detection disabled");
        if (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE)) return QualResult.reject("mining fatigue");
        if (!mc.world.getChunkManager().isChunkLoaded(site.center.getX() >> 4, site.center.getZ() >> 4)) return QualResult.reject("chunk not loaded");
        if (!mc.world.getBlockState(site.center).isOf(Blocks.CHEST)) return QualResult.reject("chest absent");
        double dist = Math.hypot(site.center.getX() + 0.5 - mc.player.getX(), site.center.getZ() + 0.5 - mc.player.getZ());
        if (dist > (walkChain ? 64 : autoMaxDistance.get())) return QualResult.reject("too far");
        if (!walkChain && !mc.player.isGliding() && dist > 96) return QualResult.reject("too far to walk");
        AutoFlyingRegear regear = autoFlyingRegearModule();
        if (regear != null && regear.isRunning()) return QualResult.reject("AutoFlyingRegear running");
        if (regear != null && regear.wantsRegear()) return QualResult.reject("AutoFlyingRegear pending");
        WaypointFollower wf = waypointFollowerModule();
        if (wf != null && wf.isActive() && flightModule.get() != FlightModule.WaypointFollower) return QualResult.reject("WaypointFollower active");
        TrailFollower tf = Modules.get().get(TrailFollower.class);
        if (tf != null && tf.isActive() && flightModule.get() != FlightModule.TrailFollower) return QualResult.reject("TrailFollower active");
        if (fly300kmActiveButNotSelected()) return QualResult.reject("300kmfly active");
        HMine mine = mlepMineModule();
        if (mine == null || !mine.isActive()) return QualResult.reject("MlepMine inactive");
        if (!BaritoneHelper.isAvailable()) return QualResult.reject("Baritone unavailable");
        if (!walkChain && autoWatchdogRetryBlocked(site)) return QualResult.reject("watchdog: leave in flight before retry");
        if (!walkChain && otherPlayerPresent()) return QualResult.reject("player nearby");
        if (!walkChain && mc.player.getHealth() + mc.player.getAbsorptionAmount() < autoMinHealth.get()) return QualResult.reject("low health");
        if (countRockets() < autoMinRockets.get()) return QualResult.reject("not enough rockets");
        if (!hasPickaxe()) return QualResult.reject("no pickaxe");
        if (!walkChain && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return QualResult.reject("elytra not worn");
        ColumnPlan plan = planColumn(site);
        if (plan == null) return QualResult.reject("no safe column");
        if (plan.water() && countObsidian() < 1) return QualResult.reject("no obsidian for ocean exit");
        return QualResult.ok(plan);
    }

    private boolean isShipwreckPassable(BlockState state, BlockPos pos) {
        return !state.getFluidState().isIn(FluidTags.LAVA) && state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private ColumnPlan planShipwreckColumn(BlockPos chest) {
        int x = chest.getX(), z = chest.getZ();
        if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return null;
        if (!mc.world.getBlockState(chest).isOf(Blocks.CHEST)) return null;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int top = Math.max(chest.getY() + 1, descendPastFloatingBlocks(chest, x, z,
            mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1, chest.getY(), false, cursor) + 1);
        int blocks = 0, water = 0, gap = 0, maxGap = 0;
        for (int y = chest.getY() + 1; y < top; y++) {
            cursor.set(x, y, z);
            BlockState state = mc.world.getBlockState(cursor);
            if (state.getFluidState().isIn(FluidTags.LAVA) || state.getBlock() == Blocks.COBWEB || state.getHardness(mc.world, cursor) < 0) return null;
            if (state.getFluidState().isIn(FluidTags.WATER) && isShipwreckPassable(state, cursor)) {
                water++;
                gap = 0;
            } else if (isShipwreckPassable(state, cursor)) {
                maxGap = Math.max(maxGap, ++gap);
            } else {
                blocks++;
                gap = 0;
            }
        }
        if (water == 0 && maxGap > autoMaxFall.get()) return null;
        return new ColumnPlan(x, z, chest.getY() + 1, top, water > 0, blocks, water, maxGap, false,
            top, new BlockPos(x, top, z), false, null);
    }

    private void saveShipwreckSettings() {
        if (autoShipwreckDisallowedBreaking != null) return;
        autoShipwreckDisallowedBreaking = BaritoneAPI.getSettings().blocksToDisallowBreaking.value;
        List<Block> protectedBlocks = new ArrayList<>(autoShipwreckDisallowedBreaking);
        if (!protectedBlocks.contains(Blocks.CHEST)) protectedBlocks.add(Blocks.CHEST);
        BaritoneAPI.getSettings().blocksToDisallowBreaking.value = protectedBlocks;
    }

    private void restoreShipwreckSettings() {
        if (autoShipwreckDisallowedBreaking == null) return;
        BaritoneAPI.getSettings().blocksToDisallowBreaking.value = autoShipwreckDisallowedBreaking;
        autoShipwreckDisallowedBreaking = null;
    }

    private void stopShipwreckAction() {
        if (mc.currentScreen != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
        releaseAllAutoKeys();
        mc.interactionManager.cancelBlockBreaking();
        BaritoneHelper.stopAllPathing();
        if (takeoff.isActive()) takeoff.cancel();
        if (autoPaused) endAutoPause();
        autoChestplateAllowed = false;
        setChestplateHold(false);
        autoWalkReapproach = false;
        autoStackedPartner = null;
    }

    private void shipwreckExitColumn(boolean atPlayer) {
        int x = atPlayer ? mc.player.getBlockX() : autoColumn.x();
        int z = atPlayer ? mc.player.getBlockZ() : autoColumn.z();
        int top = Math.max(mc.player.getBlockY() + 1, surfaceTopY(x, z));
        autoExitShaft = new ExitShaft(x, z, Math.min(mc.player.getBlockY(), autoSite.center.getY() + 1), top,
            mc.player.isTouchingWater() || autoColumn.water(), false, null);
        autoExitShaftForeign = false;
        autoTunnelTrail.clear();
    }

    private boolean shipwreckPriority(long now) {
        if (autoSoloTransitOnly) return false;
        if (autoShipwreckRun.isEmergency()) {
            if (state == AutoState.TAKEOFF || state == AutoState.CLIMB) {
                autoShipwreckRun.endEmergency();
                restoreShipwreckSettings();
                return false;
            }
            if (state == AutoState.OBSIDIAN_PLATFORM) {
                if (now - autoShipwreckPlatformStartedMs < 30_000L) return false;
                obsidianPlatformFailed("shipwreck platform timeout");
                return true;
            }
            tickShipwreckEmergency();
            return true;
        }
        if (!mc.player.isGliding() && state != AutoState.TAKEOFF && state != AutoState.TAKEOFF_SPOT
            && state != AutoState.CLIMB && state != AutoState.RESUME && state != AutoState.WATER_JUMP
            && state != AutoState.OBSIDIAN_PLATFORM && autoShipwreckRun.departureExpired(now)) {
            startShipwreckEmergency("departure timeout");
            return true;
        }
        if (autoShipwreckDeferred) return false;
        if (autoShipwreckRun.returnExpired(now)) {
            abandonRemainingShipwreckChests("surface timeout");
            autoShipwreckRun.depart(now);
            recoverShipwreckExit("surface timeout");
            return true;
        }
        if (!autoPaused && !isVisited(autoSite.center) && autoShipwreckRun.chestExpired(now)) {
            abandonShipwreckChest("duration", now);
            return true;
        }
        if (!isVisited(autoSite.center) && !autoShipwreckRun.returning()
            && (mc.player.isInLava() || mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE))) {
            abandonShipwreckChest("safety", now);
            return true;
        }
        if (autoShipwreckRun.returning()) {
            if (resumeShipwreckAtSurface()) return true;
            if (state != AutoState.SWIM_UP && state != AutoState.EXIT && state != AutoState.ASCEND && state != AutoState.SELF_ASCEND) {
                enterAscendOrSwimUp("shipwreck surface before next chest");
                return true;
            }
        }
        return false;
    }

    private void finishShipwreckChest(String outcome, long now) {
        if (!"chest absent".equals(outcome) && !"processed".equals(outcome) && !"shulkers".equals(outcome)) {
            leaveShipwreckForLater(outcome, now);
            return;
        }
        stopShipwreckAction();
        if (!isVisited(autoSite.center)) markVisited(autoSite.center, "shipwreck:" + outcome);
        autoLog("CHEST", autoSite.center, "chest visited reason=" + outcome);
        ShipwreckSite.Chest next = autoSite.shipwreck.nearestChest(mc.player.getX(), mc.player.getY(), mc.player.getZ(), this::isVisited);
        autoShipwreckNextChest = next == null ? null : next.pos();
        autoRunAbandoned = !outcome.equals("processed") && !outcome.equals("shulkers");
        autoAscendFromAbort = false;
        if (next != null) {
            if (mc.player.isTouchingWater()) {
                if (startNextShipwreckChest("next chest, underwater travel")) return;
            } else {
                autoShipwreckRun.beginReturn(now);
                shipwreckExitColumn(false);
                autoLog("CHAIN", autoSite.center, "next chest " + next.pos().toShortString() + ", surface first");
                enterState(AutoState.EXIT, "shipwreck chest complete");
                return;
            }
        }
        departShipwreck(now);
    }

    private void departShipwreck(long now) {
        autoShipwreckRun.depart(now);
        restoreShipwreckSettings();
        autoLog("EXIT", autoSite.center, "shipwreck done");
        if (mc.player.isTouchingWater()) {
            if (!shipwreckSkyOpen() && autoShipwreckEntryColumn != null) {
                enterState(AutoState.WALK, "shipwreck return to entry column");
                return;
            }
            shipwreckExitColumn(true);
        }
        enterState(AutoState.EXIT, "shipwreck chest complete");
    }

    private boolean shipwreckSkyOpen() {
        int x = mc.player.getBlockX(), z = mc.player.getBlockZ();
        int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = mc.player.getBlockY() + 1; y < top; y++) {
            cursor.set(x, y, z);
            if (!mc.world.getBlockState(cursor).getCollisionShape(mc.world, cursor).isEmpty()) return false;
        }
        return true;
    }

    private void leaveShipwreckForLater(String reason, long now) {
        if (!autoSoloTransitOnly && !autoShipwreckDeferred && autoShipwreckNextChest == null
            && !isVisited(autoSite.center) && !autoAbandonedOnce.add(autoSite.center)) {
            stopShipwreckAction();
            markVisited(autoSite.center, "failed:shipwreck " + reason);
            autoLog("CHEST", autoSite.center, "marked failed reason=" + reason);
            autoRunAbandoned = true;
            autoAscendFromAbort = false;
            if (startNextShipwreckChest("next chest after failure")) return;
            departShipwreck(now);
            return;
        }
        stopShipwreckAction();
        restoreShipwreckSettings();
        autoShipwreckDeferred = true;
        autoShipwreckNextChest = null;
        autoRunAbandoned = true;
        autoAscendFromAbort = true;
        autoWatchdogAbandonedSite = autoSite.center;
        Vec3d origin = mc.player.getEntityPos();
        for (ShipwreckSite.Chest chest : autoSite.shipwreck.chests()) {
            if (chest.status() == ShipwreckSite.ChestStatus.PRESENT && !isVisited(chest.pos())) {
                autoWatchdogRetryOrigins.putIfAbsent(chest.pos(), origin);
            }
        }
        autoShipwreckRun.depart(now);
        if (mc.player.isTouchingWater()) shipwreckExitColumn(true);
        autoLog("EXIT", autoSite.center, "shipwreck deferred reason=" + reason);
        enterState(AutoState.EXIT, "shipwreck retry on another pass");
    }

    private void abandonShipwreckChest(String reason, long now) {
        if (autoShipwreckDeferred) {
            recoverShipwreckExit(reason);
            return;
        }
        if (isVisited(autoSite.center)) return;
        finishShipwreckChest(reason, now);
    }

    private void abandonRemainingShipwreckChests(String reason) {
        leaveShipwreckForLater(reason, System.currentTimeMillis());
    }

    private boolean resumeShipwreckAtSurface() {
        if (autoShipwreckRun == null || !autoShipwreckRun.returning()) return false;
        int top = autoExitShaft == null ? autoColumn.exitY() : autoExitShaft.exitY();
        if (mc.player.isSubmergedInWater() || (mc.player.getY() < top - 1 && !isTrueSkyHere())) return false;
        releaseAllAutoKeys();
        BaritoneHelper.stopAllPathing();
        Utils.setPressed(mc.options.jumpKey, mc.player.isTouchingWater());
        autoShipwreckRun.endReturn();
        if (startNextShipwreckChest("next chest, surface travel")) return true;
        autoShipwreckRun.depart(System.currentTimeMillis());
        restoreShipwreckSettings();
        autoLog("EXIT", autoSite.center, "shipwreck done");
        enterState(AutoState.EXIT, "shipwreck all chests visited");
        return true;
    }

    private boolean startNextShipwreckChest(String details) {
        ShipwreckSite wreck = autoSite.shipwreck;
        Site target = null;
        ColumnPlan plan = null;
        while (true) {
            ShipwreckSite.Chest next = wreck.nearestChest(mc.player.getX(), mc.player.getY(), mc.player.getZ(), this::isVisited);
            if (next == null) break;
            autoShipwreckNextChest = next.pos();
            target = new Site(wreck, autoShipwreckNextChest);
            QualResult qr = evaluateShipwreckQualification(target, true);
            if (qr.reject() == null) {
                plan = qr.plan();
                break;
            }
            if (!"chest absent".equals(qr.reject())) {
                autoLog("CHEST", next.pos(), "chest deferred reason=" + qr.reject());
                leaveShipwreckForLater(qr.reject(), System.currentTimeMillis());
                return true;
            }
            markVisited(next.pos(), "shipwreck:" + qr.reject());
            autoLog("CHEST", next.pos(), "chest visited reason=" + qr.reject());
            target = null;
        }
        autoShipwreckNextChest = null;
        if (target == null || plan == null) return false;
        autoShipwreckRun.startChest(System.currentTimeMillis());
        autoLog("CHAIN", target.center, details);
        autoSite = target;
        autoColumn = plan;
        autoExitShaft = null;
        autoExitShaftForeign = false;
        autoRunAbandoned = false;
        autoRoomFoundShulkers = 0;
        autoRoomNotOpened = false;
        autoRoomFoundItems = 0;
        autoShipwreckChestOutcome = null;
        autoChestplateAllowed = !plan.water();
        autoAscendFromAbort = false;
        autoWalkReapproach = false;
        logShipwreckChest(target);
        autoShipwreckRiseFromHole = mc.player.isSubmergedInWater();
        enterState(AutoState.WALK, details);
        return true;
    }

    private boolean shipwreckChestInReach() {
        return isContainerPresent(autoSite.center) && mc.player.canInteractWithBlockAt(autoSite.center, -0.5)
            && !net.minecraft.block.ChestBlock.isChestBlocked(mc.world, autoSite.center);
    }

    private BlockPos findShipwreckSideSpot() {
        BlockPos chest = autoSite.center;
        Vec3d chestCenter = Vec3d.ofCenter(chest);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = chest.add(dx, dy, dz);
                    if (Vec3d.ofCenter(p).distanceTo(chestCenter) > 3.8) continue;
                    BlockState state = mc.world.getBlockState(p);
                    if (!state.getFluidState().isIn(FluidTags.WATER) || !isShipwreckPassable(state, p)) continue;
                    boolean clear = false;
                    int topY = mc.world.getBottomY() + mc.world.getHeight() - 1;
                    for (int y = p.getY() + 1; y <= topY; y++) {
                        cursor.set(p.getX(), y, p.getZ());
                        BlockState above = mc.world.getBlockState(cursor);
                        if (above.getFluidState().isIn(FluidTags.WATER)) {
                            if (!isShipwreckPassable(above, cursor)) break;
                            continue;
                        }
                        clear = above.isAir();
                        break;
                    }
                    if (!clear) continue;
                    double dist = Math.hypot(p.getX() + 0.5 - mc.player.getX(), p.getZ() + 0.5 - mc.player.getZ());
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = p.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean shipwreckPassableAt(int[] box, int x, int y, int z) {
        if (x < box[0] || x > box[3] || y < box[1] || y > box[4] || z < box[2] || z > box[5]) return false;
        BlockPos pos = new BlockPos(x, y, z);
        return isShipwreckPassable(mc.world.getBlockState(pos), pos);
    }

    private byte shipwreckCellType(Map<Long, Byte> cache, int[] box, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        Byte cached = cache.get(key);
        if (cached != null) return cached;
        byte type = 0;
        if (shipwreckPassableAt(box, x, y, z) && shipwreckPassableAt(box, x, y + 1, z)) {
            if (mc.world.getBlockState(new BlockPos(x, y, z)).getFluidState().isIn(FluidTags.WATER)) {
                type = 1;
            } else if (y - 1 >= box[1]) {
                BlockPos below = new BlockPos(x, y - 1, z);
                BlockState belowState = mc.world.getBlockState(below);
                if (!isShipwreckPassable(belowState, below) && !belowState.getFluidState().isIn(FluidTags.WATER)
                    && !belowState.getFluidState().isIn(FluidTags.LAVA) && belowState.getBlock() != Blocks.MAGMA_BLOCK) type = 2;
            }
        }
        cache.put(key, type);
        return type;
    }

    private List<BlockPos> findShipwreckPath(boolean toEntry, int x, int z) {
        ShipwreckSite.Bounds b = autoSite.shipwreck.bounds();
        BlockPos me = mc.player.getBlockPos();
        int[] box = {
            Math.min(b.minX() - 4, me.getX() - 2), Math.min(b.minY() - 2, me.getY() - 2), Math.min(b.minZ() - 4, me.getZ() - 2),
            Math.max(b.maxX() + 4, me.getX() + 2), Math.max(b.maxY() + 3, me.getY() + 2), Math.max(b.maxZ() + 4, me.getZ() + 2)
        };
        if (box[3] - box[0] + 1 > 64 || box[4] - box[1] + 1 > 64 || box[5] - box[2] + 1 > 64) return null;
        Map<Long, Byte> cache = new HashMap<>();
        BlockPos start = null;
        for (BlockPos candidate : new BlockPos[] {me, me.up(), me.down()}) {
            if (shipwreckCellType(cache, box, candidate.getX(), candidate.getY(), candidate.getZ()) != 0) {
                start = candidate;
                break;
            }
        }
        if (start == null) return null;
        Vec3d chestCenter = Vec3d.ofCenter(autoSite.center);
        int chestY = autoSite.center.getY();
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        java.util.PriorityQueue<Map.Entry<Long, Double>> queue = new java.util.PriorityQueue<>(Map.Entry.comparingByValue());
        long startKey = start.asLong();
        best.put(startKey, 0.0);
        queue.add(new java.util.AbstractMap.SimpleEntry<>(startKey, 0.0));
        int expanded = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        while (!queue.isEmpty()) {
            Map.Entry<Long, Double> entry = queue.poll();
            long key = entry.getKey();
            if (!closed.add(key)) continue;
            if (++expanded > 30000) return null;
            double cost = entry.getValue();
            BlockPos a = BlockPos.fromLong(key);
            int ax = a.getX(), ay = a.getY(), az = a.getZ();
            boolean goal;
            if (toEntry) {
                goal = ax == x && az == z;
            } else {
                goal = (ax == x && az == z && ay > chestY)
                    || new Vec3d(ax + 0.5, ay + 1.62, az + 0.5).distanceTo(chestCenter) <= 3.6;
            }
            if (goal) {
                List<BlockPos> path = new ArrayList<>();
                Long cursor = key;
                while (cursor != null) {
                    path.add(BlockPos.fromLong(cursor));
                    cursor = parent.get(cursor);
                }
                Collections.reverse(path);
                return path;
            }
            byte ta = shipwreckCellType(cache, box, ax, ay, az);
            for (int[] d : dirs) {
                int dx = d[0], dz = d[1];
                boolean diagonal = dx != 0 && dz != 0;
                int bx = ax + dx, bz = az + dz;
                byte tb = shipwreckCellType(cache, box, bx, ay, bz);
                if (tb != 0) {
                    boolean open = !diagonal
                        || (shipwreckPassableAt(box, ax + dx, ay, az) && shipwreckPassableAt(box, ax + dx, ay + 1, az)
                        && shipwreckPassableAt(box, ax, ay, az + dz) && shipwreckPassableAt(box, ax, ay + 1, az + dz));
                    if (open) {
                        boolean swim = ta == 1 || tb == 1;
                        double step = swim ? (diagonal ? 2.26 : 1.6) : (diagonal ? 1.41 : 1.0);
                        shipwreckRelax(best, parent, queue, key, BlockPos.asLong(bx, ay, bz), cost + step);
                    }
                }
                if (diagonal) continue;
                if (shipwreckCellType(cache, box, bx, ay + 1, bz) == 2 && shipwreckPassableAt(box, ax, ay + 2, az)
                    && (ta == 2 || (ta == 1 && !mc.world.getBlockState(new BlockPos(ax, ay + 1, az)).getFluidState().isIn(FluidTags.WATER)))) {
                    shipwreckRelax(best, parent, queue, key, BlockPos.asLong(bx, ay + 1, bz), cost + (ta == 1 ? 1.6 : 1.0) + 1.0);
                }
                if (tb == 0 && shipwreckPassableAt(box, bx, ay, bz) && shipwreckPassableAt(box, bx, ay + 1, bz)
                    && !mc.world.getBlockState(new BlockPos(bx, ay, bz)).getFluidState().isIn(FluidTags.WATER)) {
                    for (int k = 1; k <= 20; k++) {
                        int fy = ay - k;
                        if (!shipwreckPassableAt(box, bx, fy, bz)) break;
                        byte tf = shipwreckCellType(cache, box, bx, fy, bz);
                        if (tf == 0) continue;
                        if (tf == 1 || k <= 3) {
                            shipwreckRelax(best, parent, queue, key, BlockPos.asLong(bx, fy, bz), cost + (tf == 1 ? 1.6 : 1.0) + 0.5);
                        }
                        break;
                    }
                }
            }
            if (ta == 1) {
                if (shipwreckCellType(cache, box, ax, ay + 1, az) == 1) shipwreckRelax(best, parent, queue, key, BlockPos.asLong(ax, ay + 1, az), cost + 1.6);
                if (shipwreckCellType(cache, box, ax, ay - 1, az) == 1) shipwreckRelax(best, parent, queue, key, BlockPos.asLong(ax, ay - 1, az), cost + 1.6);
            }
        }
        return null;
    }

    private void shipwreckRelax(Map<Long, Double> best, Map<Long, Long> parent,
                                java.util.PriorityQueue<Map.Entry<Long, Double>> queue, long from, long to, double cost) {
        Double known = best.get(to);
        if (known != null && known <= cost) return;
        best.put(to, cost);
        parent.put(to, from);
        queue.add(new java.util.AbstractMap.SimpleEntry<>(to, cost));
    }

    private boolean followShipwreckPath() {
        List<BlockPos> path = autoShipwreckPath;
        int last = path.size() - 1;
        for (int i = Math.min(autoShipwreckPathIndex + 4, last); i > autoShipwreckPathIndex; i--) {
            BlockPos w = path.get(i);
            if (mc.player.getBlockX() == w.getX() && mc.player.getBlockZ() == w.getZ() && Math.abs(mc.player.getY() - w.getY()) < 0.6) {
                autoShipwreckPathIndex = i;
                autoShipwreckPathProgressTick = stateTicks;
                break;
            }
        }
        if (stateTicks - autoShipwreckPathProgressTick >= 40) {
            autoLog("WALK", autoSite.center, "shipwreck path stalled, replanning");
            autoShipwreckPath = null;
            autoShipwreckPathReplans++;
            return false;
        }
        BlockPos t = path.get(Math.min(autoShipwreckPathIndex + 1, last));
        boolean touching = mc.player.isTouchingWater();
        releaseAllAutoKeys();
        recenterDig(t.getX(), t.getZ(), 0.12);
        mc.player.setPitch(0.0f);
        Utils.setPressed(mc.options.sprintKey, false);
        Utils.setPressed(mc.options.jumpKey, t.getY() > mc.player.getY() + 0.3
            || (!touching && mc.player.isOnGround() && mc.player.horizontalCollision));
        Utils.setPressed(mc.options.sneakKey, touching
            && mc.world.getBlockState(t).getFluidState().isIn(FluidTags.WATER) && t.getY() < mc.player.getY() - 0.3);
        return true;
    }

    private boolean shipwreckPathStep(boolean returningToEntry, int x, int z) {
        if (!autoShipwreckPathOff && autoShipwreckPathReplans >= 3) {
            autoShipwreckPathOff = true;
            autoShipwreckPath = null;
            autoLog("WALK", autoSite.center, "shipwreck path given up, straight swim");
        }
        if (autoShipwreckPathOff) return false;
        if (autoShipwreckPath == null && stateTicks >= autoShipwreckPathRetryTick) {
            List<BlockPos> found = findShipwreckPath(returningToEntry, x, z);
            if (found == null) {
                autoShipwreckPathRetryTick = stateTicks + 40;
                return false;
            }
            autoShipwreckPath = found;
            autoShipwreckPathIndex = 0;
            autoShipwreckPathProgressTick = stateTicks;
            autoLog("WALK", autoSite.center, "shipwreck path nodes=" + found.size());
        }
        if (autoShipwreckPath != null) return followShipwreckPath();
        return false;
    }

    private void tickShipwreckWalk() {
        stateTicks++;
        if (stateTicks == 1) {
            resetBreach();
            autoShipwreckWalkStart = mc.player.getBlockPos();
            autoShipwreckWalkRisen = !autoShipwreckRiseFromHole;
            autoShipwreckRiseFromHole = false;
            autoShipwreckRiseBestY = mc.player.getY();
            autoShipwreckRiseBestTick = 1;
            autoShipwreckSideBestTick = 1;
            autoShipwreckSideSpot = null;
            autoShipwreckSideTried = false;
            autoShipwreckSideStartTick = 0;
            autoShipwreckPath = null;
            autoShipwreckPathIndex = 0;
            autoShipwreckPathProgressTick = 0;
            autoShipwreckPathReplans = 0;
            autoShipwreckPathOff = false;
            autoShipwreckPathRetryTick = 0;
            autoShipwreckWalkProgressTick = 1;
        }
        saveShipwreckSettings();
        if (mc.player.isInLava()) { abort("lava"); return; }
        boolean returningToEntry = autoShipwreckRun.departing() && !autoShipwreckDeferred && autoShipwreckEntryColumn != null;
        int x = returningToEntry ? autoShipwreckEntryColumn.getX() : autoColumn.x();
        int z = returningToEntry ? autoShipwreckEntryColumn.getZ() : autoColumn.z();
        double gap = Math.hypot(x + 0.5 - mc.player.getX(), z + 0.5 - mc.player.getZ());
        if (stateTicks == 1) autoShipwreckSideBestGap = gap;
        double walkReach = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(autoSite.center));
        if (stateTicks == 1) {
            autoShipwreckWalkBestGap = gap;
            autoShipwreckWalkBestReach = walkReach;
        }
        boolean walkProgress = gap < autoShipwreckWalkBestGap - 0.3
            || (!returningToEntry && walkReach < autoShipwreckWalkBestReach - 0.3)
            || (!autoShipwreckWalkRisen && mc.player.getY() > autoShipwreckRiseBestY + 0.1)
            || (autoShipwreckPath != null && autoShipwreckPathProgressTick >= stateTicks - 1);
        if (walkProgress) {
            autoShipwreckWalkBestGap = Math.min(autoShipwreckWalkBestGap, gap);
            autoShipwreckWalkBestReach = Math.min(autoShipwreckWalkBestReach, walkReach);
            autoShipwreckWalkProgressTick = stateTicks;
        }
        if (stateTicks - autoShipwreckWalkProgressTick > 200) { abort("walk no progress"); return; }
        int goalY = mc.player.isTouchingWater() ? autoSite.shipwreck.bounds().maxY() + 1 : autoColumn.exitY();
        BlockPos goal = new BlockPos(x, goalY, z);
        IBaritone bt = primaryBaritone();
        if (stateTicks % 40 == 0) logNavTelemetry(String.format("WALK shipwreck gap=%.2f water=%s calculating=%s path=%d/%d", gap,
            mc.player.isTouchingWater(), bt != null && bt.getPathingBehavior().getInProgress().isPresent(),
            autoShipwreckPath == null ? 0 : autoShipwreckPathIndex, autoShipwreckPath == null ? 0 : autoShipwreckPath.size()), goal, 0);
        if (!returningToEntry && shipwreckChestInReach()) {
            releaseAllAutoKeys();
            BaritoneHelper.stopAllPathing();
            shipwreckExitColumn(true);
            enterState(AutoState.ROOM, "shipwreck chest in reach");
            return;
        }
        if (gap <= 0.15) {
            releaseAllAutoKeys();
            BaritoneHelper.stopAllPathing();
            if (returningToEntry) {
                shipwreckExitColumn(true);
                enterState(AutoState.EXIT, "shipwreck entry column reached");
            } else {
                enterDigState("shipwreck above current chest");
            }
            return;
        }
        if (stateTicks > 60 * 20) { abort("walk timeout"); return; }
        if (mc.player.isTouchingWater()) {
            if (bt != null && (bt.getPathingBehavior().isPathing() || bt.getPathingBehavior().getInProgress().isPresent())) BaritoneHelper.stopAllPathing();
            releaseAllAutoKeys();
            if (shipwreckPathStep(returningToEntry, x, z)) {
                autoShipwreckWalkRisen = true;
                return;
            }
            double y = mc.player.getY();
            if (y > autoShipwreckRiseBestY + 0.1) {
                autoShipwreckRiseBestY = y;
                autoShipwreckRiseBestTick = stateTicks;
            }
            if (y >= goalY - 0.5 || !mc.player.isSubmergedInWater() || stateTicks - autoShipwreckRiseBestTick >= 20) autoShipwreckWalkRisen = true;
            boolean rising = !autoShipwreckWalkRisen;
            BlockPos start = autoShipwreckWalkStart != null ? autoShipwreckWalkStart : mc.player.getBlockPos();
            if (rising) {
                recenterDig(start.getX(), start.getZ(), 0.12);
                mc.player.setPitch(0.0f);
                Utils.setPressed(mc.options.jumpKey, true);
                Utils.setPressed(mc.options.sprintKey, false);
                autoShipwreckSideBestGap = gap;
                autoShipwreckSideBestTick = stateTicks;
                return;
            }
            if (!returningToEntry) {
                if (gap < autoShipwreckSideBestGap - 0.3) {
                    autoShipwreckSideBestGap = gap;
                    autoShipwreckSideBestTick = stateTicks;
                }
                if (autoShipwreckSideSpot == null && !autoShipwreckSideTried && stateTicks - autoShipwreckSideBestTick >= 40) {
                    autoShipwreckSideTried = true;
                    BlockPos spot = findShipwreckSideSpot();
                    if (spot != null) {
                        autoShipwreckSideSpot = spot;
                        autoShipwreckSideStartTick = stateTicks;
                        autoLog("WALK", spot, "shipwreck side approach");
                    }
                }
                if (autoShipwreckSideSpot != null && stateTicks - autoShipwreckSideStartTick > 200) {
                    autoLog("WALK", autoShipwreckSideSpot, "shipwreck side approach gave up");
                    autoShipwreckSideSpot = null;
                }
                if (autoShipwreckSideSpot != null) {
                    BlockPos spot = autoShipwreckSideSpot;
                    recenterDig(spot.getX(), spot.getZ(), 0.12);
                    mc.player.setPitch(0.0f);
                    Utils.setPressed(mc.options.sprintKey, false);
                    double spotGap = Math.hypot(spot.getX() + 0.5 - mc.player.getX(), spot.getZ() + 0.5 - mc.player.getZ());
                    if (spotGap < 0.3) {
                        Utils.setPressed(mc.options.jumpKey, false);
                        Utils.setPressed(mc.options.sneakKey, y > spot.getY() + 0.2);
                    } else {
                        Utils.setPressed(mc.options.jumpKey, mc.player.horizontalCollision);
                        Utils.setPressed(mc.options.sneakKey, false);
                    }
                    return;
                }
            }
            recenterDig(x, z, 0.12);
            mc.player.setPitch(0.0f);
            Utils.setPressed(mc.options.jumpKey, y < goalY - 0.15 || mc.player.horizontalCollision);
            Utils.setPressed(mc.options.sneakKey, y > goalY + 0.6);
            Utils.setPressed(mc.options.sprintKey, gap > 2 && (y >= goalY - 0.5 || !mc.player.isSubmergedInWater()));
        } else {
            boolean overWater = mc.world.getBlockState(new BlockPos(x, goalY - 1, z)).getFluidState().isIn(FluidTags.WATER);
            if ((autoShipwreckPath != null || overWater) && shipwreckPathStep(returningToEntry, x, z)) {
                if (bt != null && (bt.getPathingBehavior().isPathing() || bt.getPathingBehavior().getInProgress().isPresent())) BaritoneHelper.stopAllPathing();
                resetBreach();
                return;
            }
            boolean breaching = autoBreachPos != null;
            tickBreach("WALK", goal);
            if (autoBreachPos != null) return;
            if (breaching && bt != null) {
                releaseAllAutoKeys();
                bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                return;
            }
            if (gap < 1.5) {
                if (bt != null) bt.getPathingBehavior().cancelEverything();
                recenterDig(autoColumn.x(), autoColumn.z(), 0.12);
                Utils.setPressed(mc.options.jumpKey, mc.player.horizontalCollision);
            } else if (bt != null && (stateTicks == 1 || stateTicks % 100 == 0)) {
                bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
            }
        }
    }

    private void tickShipwreckDig() {
        stateTicks++;
        if (stateTicks == 1) {
            if (autoShipwreckEntryColumn == null) autoShipwreckEntryColumn = new BlockPos(autoColumn.x(), autoColumn.exitY(), autoColumn.z());
            if (!autoShipwreckRun.attempt()) { abort("attempts"); return; }
            autoDigQueuedPos = null;
            autoDigBlockRetries.clear();
            autoDigBlockStartMs = System.currentTimeMillis();
            autoShipwreckDigProgressMs = autoDigBlockStartMs;
            autoShipwreckDigProgressY = mc.player.getY();
            autoShipwreckDigProgressMined = com.hunterbuddy.util.SessionStats.get().blocksMined();
        }
        if (!mc.world.getChunkManager().isChunkLoaded(autoSite.center.getX() >> 4, autoSite.center.getZ() >> 4)) { abort("chunk not loaded"); return; }
        if (!mc.world.getBlockState(autoSite.center).isOf(Blocks.CHEST)) { abort("chest absent"); return; }
        if (handleAutoPause(true)) return;
        if (otherPlayerPresent()) { abort("player appeared"); return; }
        if (shipwreckChestInReach()) {
            releaseAllAutoKeys();
            mc.interactionManager.cancelBlockBreaking();
            shipwreckExitColumn(false);
            enterState(AutoState.ROOM, "shipwreck chest in reach");
            return;
        }
        long now = System.currentTimeMillis();
        int mined = com.hunterbuddy.util.SessionStats.get().blocksMined();
        if (mc.player.getY() <= autoShipwreckDigProgressY - 0.5 || mined > autoShipwreckDigProgressMined
            || autoDigQueuedPos != null) {
            autoShipwreckDigProgressMs = now;
            autoShipwreckDigProgressY = mc.player.getY();
            autoShipwreckDigProgressMined = mined;
        } else if (now - autoShipwreckDigProgressMs >= 10_000L) {
            releaseAllAutoKeys();
            mc.interactionManager.cancelBlockBreaking();
            enterState(AutoState.WALK, "shipwreck dig no progress");
            return;
        }
        long perBlockTimeout = 15_000L;
        if (mc.player.isSubmergedInWater() && mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED) < 0.99) perBlockTimeout *= 5;
        recenterDig(autoColumn.x(), autoColumn.z(), 0.12);
        Utils.setPressed(mc.options.jumpKey, false);
        Utils.setPressed(mc.options.sprintKey, false);
        mc.player.setSprinting(false);
        double gap = Math.hypot(autoColumn.x() + 0.5 - mc.player.getX(), autoColumn.z() + 0.5 - mc.player.getZ());
        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater() && gap <= 0.15);
        if (stateTicks % 40 == 0) logNavTelemetry(String.format("DIG shipwreck gap=%.2f onGround=%s water=%s submerged=%s shift=%s below=%s", gap,
            mc.player.isOnGround(), mc.player.isTouchingWater(), mc.player.isSubmergedInWater(), mc.options.sneakKey.isPressed(),
            Registries.BLOCK.getId(mc.world.getBlockState(mc.player.getBlockPos().down()).getBlock()).getPath()), autoSite.center,
            autoDigQueuedPos == null ? 0 : autoDigBlockRetries.getOrDefault(autoDigQueuedPos, 0));
        if (gap <= 0.15 && standingOnContainer(autoSite.center)) {
            releaseAllAutoKeys();
            mc.interactionManager.cancelBlockBreaking();
            shipwreckExitColumn(false);
            enterState(AutoState.ROOM, "shipwreck standing on chest");
            return;
        }
        if (mc.player.getY() < autoSite.center.getY() + 0.5) { enterState(AutoState.WALK, "shipwreck recenter below chest"); return; }
        if (gap > 0.6) {
            if (stateTicks > 60) enterState(AutoState.WALK, "shipwreck recenter at surface");
            return;
        }
        int y = Math.max(autoSite.center.getY() + 1, (int) Math.floor(mc.player.getY() - 0.05));
        BlockPos below = new BlockPos(autoColumn.x(), y, autoColumn.z());
        BlockState state = mc.world.getBlockState(below);
        if (isLavaNearShaft(below, mc.player.getBlockPos())) { abort("lava next to the shaft"); return; }
        if (isShipwreckPassable(state, below)) {
            autoDigQueuedPos = null;
            return;
        }
        if (state.getFluidState().isIn(FluidTags.LAVA) || state.getHardness(mc.world, below) < 0) { abort("unsafe column"); return; }
        if (!below.equals(autoDigQueuedPos)) {
            if (!mc.player.isOnGround()) return;
            autoDigQueuedPos = below;
            autoDigBlockStartMs = System.currentTimeMillis();
        }
        autoMineTick("DIG", below, true);
        if (autoDigQueuedPos != null && now - autoDigBlockStartMs > perBlockTimeout) {
            if (autoDigBlockRetries.merge(autoDigQueuedPos.toImmutable(), 1, Integer::sum) >= 2) {
                abort("dig block timeout");
            } else {
                Utils.setPressed(mc.options.attackKey, false);
                autoDigQueuedPos = null;
                autoDigBlockStartMs = now;
                autoLog("DIG", autoSite.center, "dig block retry");
            }
        }
    }

    private void tickShipwreckRoom() {
        stateTicks++;
        if (stateTicks == 1) {
            autoRoomQueue = new ArrayList<>(List.of(autoSite.center));
            autoRoomSpawnerPos = null;
            autoRoomSpawnerHandled = true;
            autoRoomFoundShulkers = 0;
            autoRoomNotOpened = false;
            autoRoomFoundItems = 0;
            autoRoomIndex = 0;
            autoRoomContainerFails = 0;
            autoRoomSubState = 0;
            autoRoomPillarTarget = null;
            autoRoomPillarPhase = 0;
            autoRoomStepOffTarget = null;
            autoRoomStepOffCell = null;
            autoRoomStepOffNoCellLoggedTarget = null;
            autoApproachTarget = null;
            autoApproachBadCells.clear();
            autoPaused = false;
            autoPauseReason = null;
            autoPauseStartMs = 0;
            autoPausedTicks = 0;
        }
        if (handleAutoPause(true)) return;
        tickRoomContainers();
        if (state == AutoState.ROOM && autoRoomIndex >= autoRoomQueue.size()) finishRoomAndAscend();
    }

    private void recoverShipwreckExit(String reason) {
        stopShipwreckAction();
        if (autoShipwreckRun == null) autoShipwreckRun = new ShipwreckRun(System.currentTimeMillis());
        if (autoShipwreckRun.isEmergency()) {
            if (state == AutoState.OBSIDIAN_PLATFORM) {
                obsidianPlatformFailed(reason);
                return;
            }
            if (autoShipwreckEmergencyGoal != null) autoShipwreckFailedExits.add(autoShipwreckEmergencyGoal);
            else autoShipwreckEscapeYaw = MathHelper.wrapDegrees(autoShipwreckEscapeYaw + 90.0f);
            autoShipwreckEmergencyGoal = null;
            return;
        }
        if (!autoShipwreckRun.recoverExit()) {
            startShipwreckEmergency(reason);
            return;
        }
        autoLog("WATCHDOG", autoSite.center, "shipwreck exit recovery " + autoShipwreckRun.exitRecoveries() + "/3 reason=" + reason);
        if (mc.player.isSubmergedInWater()) {
            shipwreckExitColumn(true);
            enterState(AutoState.SWIM_UP, "shipwreck exit recovery");
        } else if (mc.player.isTouchingWater() && autoShipwreckRun.returning() && resumeShipwreckAtSurface()) {
            return;
        } else {
            enterState(autoShipwreckRun.exitRecoveries() == 2 ? AutoState.SELF_ASCEND : AutoState.ASCEND, "shipwreck alternate exit");
        }
    }

    private void startShipwreckEmergency(String reason) {
        stopShipwreckAction();
        restoreShipwreckSettings();
        if (!autoShipwreckDeferred) abandonRemainingShipwreckChests(reason);
        autoShipwreckRun.depart(System.currentTimeMillis());
        autoShipwreckRun.emergency();
        autoShipwreckEscapeYaw = mc.player.getYaw();
        autoShipwreckEmergencySearchMs = 0;
        autoShipwreckEmergencyGoal = null;
        autoLog("WATCHDOG", autoSite.center, "shipwreck recovery exhausted, surface and leave on foot reason=" + reason);
        enterState(AutoState.WALK, "shipwreck leave safely");
    }

    private void tickShipwreckEmergency() {
        long now = System.currentTimeMillis();
        if (autoShipwreckEmergencyGoal != null && now - autoShipwreckEmergencyGoalMs >= 30_000L) {
            autoShipwreckFailedExits.add(autoShipwreckEmergencyGoal);
            autoShipwreckEmergencyGoal = null;
            BaritoneHelper.stopAllPathing();
        }
        if (!mc.player.isTouchingWater() && mc.player.isOnGround() && isTrueSkyHere()) {
            restoreShipwreckSettings();
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
            autoShipwreckRun.endEmergency();
            enterState(AutoState.ASCEND, "shipwreck safe on dry ground");
            return;
        }
        if (mc.player.isSubmergedInWater()) {
            mc.player.setYaw(autoShipwreckEscapeYaw);
            mc.player.setPitch(-90.0f);
            Utils.setPressed(mc.options.forwardKey, true);
            Utils.setPressed(mc.options.jumpKey, true);
            Utils.setPressed(mc.options.sneakKey, false);
            Utils.setPressed(mc.options.sprintKey, true);
            BlockPos head = mc.player.getBlockPos().up(2);
            if (!isShipwreckPassable(mc.world.getBlockState(head), head)) autoMineTick("SWIM_UP", head, false);
            return;
        }
        if (autoShipwreckEmergencyGoal == null && now - autoShipwreckEmergencySearchMs >= 5_000L) {
            autoShipwreckEmergencySearchMs = now;
            for (BlockPos point : findAscendTakeoffPoints(mc.player.getBlockX(), mc.player.getBlockZ(), 96)) {
                if (autoShipwreckFailedExits.contains(point) || mc.world.getFluidState(point.down()).isIn(FluidTags.WATER)) continue;
                autoShipwreckEmergencyGoal = point;
                autoShipwreckEmergencyGoalMs = now;
                break;
            }
            if (autoShipwreckEmergencyGoal != null) {
                IBaritone bt = primaryBaritone();
                if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(autoShipwreckEmergencyGoal));
            } else if (!autoShipwreckPlatformTried) {
                autoShipwreckPlatformTried = true;
                autoShipwreckPlatformStartedMs = now;
                restoreShipwreckSettings();
                enterState(AutoState.OBSIDIAN_PLATFORM, "shipwreck no shore, dry platform");
                return;
            }
        }
        if (mc.player.isTouchingWater()) {
            if (autoShipwreckEmergencyGoal != null) {
                recenterDig(autoShipwreckEmergencyGoal.getX(), autoShipwreckEmergencyGoal.getZ(), 0.3);
            } else {
                mc.player.setYaw(autoShipwreckEscapeYaw);
                Utils.setPressed(mc.options.forwardKey, true);
            }
            mc.player.setPitch(0.0f);
            Utils.setPressed(mc.options.jumpKey, true);
            Utils.setPressed(mc.options.sneakKey, false);
        }
    }

    private boolean qualifiesForAuto(Site site) {
        if (site.shipwreck != null) return detectShipwrecks.get() && !isVisited(site.center);
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
        if (site.shipwreck != null) return evaluateShipwreckQualification(site, walkChain);
        if (isVisited(site.center)) return QualResult.reject("visited");
        if (site.room.cy() < 0) return QualResult.reject("below y 0");
        if (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE)) {
            return QualResult.reject("mining fatigue");
        }
        if (!mc.world.getChunkManager().isChunkLoaded(site.center.getX() >> 4, site.center.getZ() >> 4)) {
            return QualResult.reject("chunk not loaded");
        }

        double dx = site.center.getX() + 0.5 - mc.player.getX();
        double dz = site.center.getZ() + 0.5 - mc.player.getZ();
        double dist = Math.hypot(dx, dz);

        boolean gliding = mc.player.isGliding();
        if (underground) {
            if (dist > 32) return QualResult.reject("too far to chain");
            double dy = Math.abs(site.room.cy() - mc.player.getBlockPos().getY());
            if (dy > 32) return QualResult.reject("too far to chain (vertical)");
        } else if (walkChain) {
            if (dist > 64) return QualResult.reject("too far to chain");
        } else {
            if (dist > autoMaxDistance.get()) return QualResult.reject("too far");
            if (!gliding && dist > 96) return QualResult.reject("too far to walk");
        }

        if (nearOceanMonument(site.center)) return QualResult.reject("near ocean monument");
        if (isJunglePyramidSite(site)) return QualResult.reject("near jungle temple");

        AutoFlyingRegear regear = autoFlyingRegearModule();
        if (regear != null && regear.isRunning()) return QualResult.reject("AutoFlyingRegear running");
        if (regear != null && regear.wantsRegear()) return QualResult.reject("AutoFlyingRegear pending");
        WaypointFollower wf = waypointFollowerModule();
        if (wf != null && wf.isActive() && flightModule.get() != FlightModule.WaypointFollower) {
            return QualResult.reject("WaypointFollower active");
        }
        TrailFollower tf = Modules.get().get(TrailFollower.class);
        if (tf != null && tf.isActive() && flightModule.get() != FlightModule.TrailFollower) {
            return QualResult.reject("TrailFollower active");
        }
        if (fly300kmActiveButNotSelected()) {
            return QualResult.reject("300kmfly active");
        }

        HMine mine = mlepMineModule();
        if (mine == null || !mine.isActive()) return QualResult.reject("MlepMine inactive");
        if (!BaritoneHelper.isAvailable()) return QualResult.reject("Baritone unavailable");

        if (autoWatchdogRetryBlocked(site)) return QualResult.reject("watchdog: leave in flight before retry");
        if (otherPlayerPresent()) return QualResult.reject("player nearby");

        double health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (health < autoMinHealth.get()) return QualResult.reject("low health");

        if (countRockets() < autoMinRockets.get()) return QualResult.reject("not enough rockets");
        if (!hasPickaxe()) return QualResult.reject("no pickaxe");
        if (!underground && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return QualResult.reject("elytra not worn");

        if (underground) return QualResult.ok(null);

        ColumnPlan plan = planColumn(site);
        if (plan == null) return QualResult.reject("no safe column");

        if (plan.water() && countObsidian() < 1) {
            return QualResult.reject("no obsidian for ocean exit");
        }

        if (plan.waterCells() >= 3 && mc.player.getAttributeValue(EntityAttributes.OXYGEN_BONUS) < 1.0) {
            return QualResult.reject("no respiration for water column");
        }

        return QualResult.ok(plan);
    }

    private boolean configuredSoloMode() {
        Module fm = selectedFlightModule();
        return flightModule.get() == FlightModule.None || fm == null || !fm.isActive();
    }

    private boolean soloMemoryCandidate(Site site) {
        if (autoWatchdogRetryBlocked(site)) return false;
        AutoFlyingRegear regear = autoFlyingRegearModule();
        if (regear != null && regear.wantsRegear()) return false;
        TrailFollower tf = Modules.get().get(TrailFollower.class);
        if (tf != null && tf.isActive() && flightModule.get() != FlightModule.TrailFollower) return false;
        if (fly300kmActiveButNotSelected()) return false;
        if (!qualifiesForAuto(site)) return false;
        if (isVisited(site.center)) return false;
        if (autoSoloRejectedThisSession.contains(site.center)) return false;
        if (countRockets() <= autoMinRockets.get()) return false;
        if (site.shipwreck == null && site.room.cy() < 0) return false;
        double dx = site.center.getX() + 0.5 - mc.player.getX();
        double dz = site.center.getZ() + 0.5 - mc.player.getZ();
        return Math.hypot(dx, dz) <= autoMaxDistance.get();
    }

    private int soloMemoryTargetCount() {
        int count = 0;
        for (Site site : sites.values()) {
            if (soloMemoryCandidate(site)) count++;
        }
        return count;
    }

    private Site findSoloMemoryTarget() {
        Site best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Site site : sites.values()) {
            if (!soloMemoryCandidate(site)) continue;
            double dx = site.center.getX() + 0.5 - mc.player.getX();
            double dz = site.center.getZ() + 0.5 - mc.player.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = site;
            }
        }
        for (Site site : shipwreckTargets()) {
            if (!soloMemoryCandidate(site)) continue;
            double dx = site.center.getX() + 0.5 - mc.player.getX();
            double dz = site.center.getZ() + 0.5 - mc.player.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = site;
            }
        }
        return best;
    }

    private ColumnPlan soloTravelColumn() {
        BlockPos feet = mc.player.getBlockPos();
        int y = feet.getY();
        return new ColumnPlan(feet.getX(), feet.getZ(), y, y, false, 0, 0, 0, false, y, null, false, null);
    }

    private void startSoloTravel(Site target, String details) {
        autoShipwreckRun = null;
        autoShipwreckDeferred = false;
        restoreShipwreckSettings();
        autoSoloMode = true;
        autoSoloTransitOnly = true;
        autoSoloTarget = target;
        autoSite = target;
        autoColumn = soloTravelColumn();
        autoChestplateAllowed = false;
        autoSoloBestDistSq = Double.MAX_VALUE;
        autoSoloBestDistTick = 0;
        autoSoloNearUnloadedTicks = 0;
        autoSoloLastRocketTick = -100;
        autoSoloCruiseAltitude = Math.max(mc.player.getY(), autoClimbTargetY);
        autoLog("SOLO", target.center, String.format("target %d,%d,%d remaining=%d %s",
            target.center.getX(), target.center.getY(), target.center.getZ(), soloMemoryTargetCount(), details));
        if (mc.player.isGliding()) {
            enterState(AutoState.SOLO_CRUISE, details);
        } else if (mc.player.isTouchingWater()) {
            autoWaterJumpAttempts = 0;
            autoWaterJumpVertical = false;
            enterState(AutoState.WATER_JUMP, "solo water takeoff");
        } else if (isClearSkyHere()) {
            autoAscendArrivedAt = null;
            enterState(AutoState.TAKEOFF, "solo takeoff");
        } else {
            enterState(AutoState.TAKEOFF_SPOT, "solo takeoff spot");
        }
    }

    private void tryStartAutoFarm() {
        if (state != AutoState.IDLE) return;
        if (autoWatchdogIdlePos != null) {
            if (mc.player.getEntityPos().squaredDistanceTo(autoWatchdogIdlePos) < 4.0) return;
            autoWatchdogIdlePos = null;
        }
        if (mc.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;
        AutoFlyingRegear regear = autoFlyingRegearModule();
        if (regear != null && regear.isRunning()) return;

        Site best = null;
        ColumnPlan bestPlan = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Site site : sites.values()) {
            if (!qualifiesForAuto(site)) {
                if (!site.roomContainers.isEmpty()) {
                    logSkipOnce(site.center, "score too low score=" + site.score);
                }
                continue;
            }

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

        for (Site site : shipwreckTargets()) {
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

        if (best == null) {
            if (configuredSoloMode()) {
                Site target = findSoloMemoryTarget();
                if (target != null) startSoloTravel(target, "from idle");
            } else {
                Module fm = selectedFlightModule();
                Site target = findSoloMemoryTarget();
                if (fm != null && fm.isActive() && target != null) {
                    if (fm instanceof TrailFollower tf && fm.isActive()) {
                        autoTrailYaw = tf.headingYaw();
                        autoTrailYawSaved = true;
                    } else if (fm instanceof WaypointFollower) {
                        autoWaypointYaw = mc.player.getYaw();
                        autoWaypointYawSaved = true;
                    }
                    fm.toggle();
                    autoMemoryChaseFromFlightModule = true;
                    autoAreaLoaderWasOn = true;
                    autoLog("SOLO", target.center, "remembered target within auto-max-distance, " + fm.name + " paused");
                    startSoloTravel(target, "from " + fm.name);
                }
            }
            return;
        }

        if (best.shipwreck != null) {
            startAutoRun(best, bestPlan);
            return;
        }

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
        if (site.shipwreck != null) {
            for (ShipwreckSite.Chest chest : site.shipwreck.chests()) {
                autoWatchdogRetryOrigins.remove(chest.pos());
            }
            autoShipwreckRun = new ShipwreckRun(System.currentTimeMillis());
            autoShipwreckNextChest = null;
            autoShipwreckEntryColumn = null;
            autoShipwreckEmergencyGoal = null;
            autoShipwreckFailedExits.clear();
            autoShipwreckPlatformTried = false;
            autoShipwreckChestOutcome = null;
            autoShipwreckDeferred = false;
            autoSavedAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
            autoLog("PREPARE", site.center, "shipwreck target model=" + site.shipwreck.key().model());
            logShipwreckChest(site);
        } else {
            autoShipwreckRun = null;
            restoreShipwreckSettings();
        }
        autoWatchdogExitMethods = 0;
        autoWatchdogAbandonedSite = null;
        autoSite = site;
        autoColumn = plan;
        autoSoloMode = configuredSoloMode();
        autoSoloTransitOnly = false;
        autoSoloTarget = null;
        autoHostileIds.clear();
        autoChestplateAllowed = !plan.water();
        autoRunAbandoned = false;
        autoRoomFoundShulkers = 0;
        autoRoomNotOpened = false;
        autoRoomFoundItems = 0;
        autoAscendFromAbort = false;
        autoAscendSurfacedCount = 0;

        if (!hasFreeSlotOrThrowaway()) {
            autoLog("PREPARE", site.center, "inventory full, Baritone may not pillar");
        }

        if (site.shipwreck != null) {
            com.hunterbuddy.util.HuntFeed.get().publish(com.hunterbuddy.util.HuntFeed.Type.SPAWNER, "Farm shipwreck", site.center);
            if (chatFeedback.get()) info("Farm shipwreck chest at %d %d %d", site.center.getX(), site.center.getY(), site.center.getZ());
        } else {
        com.hunterbuddy.util.HuntFeed.get().publish(
            com.hunterbuddy.util.HuntFeed.Type.SPAWNER,
            "Farm · " + (site.hasSpawner ? site.mob : "no spawner") + " · " + site.containers + " chests", site.center);

        if (chatFeedback.get()) {
            info("Farm spawner at %d %d %d (%d containers)",
                site.center.getX(), site.center.getY(), site.center.getZ(), site.containers);
        }

        }

        double dx = site.center.getX() + 0.5 - mc.player.getX();
        double dz = site.center.getZ() + 0.5 - mc.player.getZ();
        double D = Math.hypot(dx, dz);
        double H = mc.player.getY() - plan.exitY();
        double need = 0.2 * D + 20;
        if (mc.player.isGliding() && H < need && lowGlideLogged.add(site.center)) {
            autoLog("PLAN", site.center, String.format("low glide H=%.1f need=%.1f, going anyway", H, need));
        }

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

    @EventHandler
    private void onAutoProgressPacketSent(PacketEvent.Sent event) {
        if (state == AutoState.IDLE || mc.world == null) return;
        if (event.packet instanceof PlayerActionC2SPacket packet
            && (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                || packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK)) {
            BlockState block = mc.world.getBlockState(packet.getPos());
            if (!block.isAir() && !(block.getBlock() instanceof FluidBlock)) {
                autoProgressPendingBreaks.add(packet.getPos().toImmutable());
            }
        }
    }

    @EventHandler(priority = -1000)
    private void onAutoProgressPacketReceive(PacketEvent.Receive event) {
        if (event.isCancelled() || state == AutoState.IDLE || mc.player == null || mc.world == null) return;
        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            confirmAutoProgressBreak(packet.getPos(), packet.getState());
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::confirmAutoProgressBreak);
        } else if (event.packet instanceof ItemPickupAnimationS2CPacket packet
            && packet.getCollectorEntityId() == mc.player.getId() && packet.getStackAmount() > 0
            && mc.world.getEntityById(packet.getEntityId()) instanceof ItemEntity) {
            autoProgressServerEvents.incrementAndGet();
        }
    }

    private void confirmAutoProgressBreak(BlockPos pos, BlockState block) {
        if ((block.isAir() || block.getBlock() instanceof FluidBlock) && autoProgressPendingBreaks.remove(pos)) {
            autoProgressServerEvents.incrementAndGet();
        }
    }

    private boolean autoWatchdogRetryBlocked(Site site) {
        Vec3d origin = autoWatchdogRetryOrigins.get(site.center);
        if (origin == null) return false;
        double dx = mc.player.getX() - origin.x;
        double dz = mc.player.getZ() - origin.z;
        if (!mc.player.isGliding() || dx * dx + dz * dz < 32.0 * 32.0) return true;
        autoWatchdogRetryOrigins.remove(site.center);
        return false;
    }

    private boolean autoNoProgressWatchdog(long now) {
        Vec3d pos = mc.player.getEntityPos();
        int mined = com.hunterbuddy.util.SessionStats.get().blocksMined();
        long events = autoProgressServerEvents.get();
        double cruiseGain = 0.0;
        if (state == AutoState.SOLO_CRUISE && mc.player.isGliding() && autoSoloTarget != null && autoProgressPos != null) {
            double targetX = autoSoloTarget.center.getX() + 0.5;
            double targetZ = autoSoloTarget.center.getZ() + 0.5;
            cruiseGain = Math.hypot(autoProgressPos.x - targetX, autoProgressPos.z - targetZ)
                - Math.hypot(pos.x - targetX, pos.z - targetZ);
        }
        if (autoProgressPos == null || cruiseGain >= 2.0
            || (!mc.player.isGliding() && (mined > autoProgressMined || events != autoProgressEventsSeen))) {
            autoProgressPos = pos;
            autoProgressTickMs = now;
            autoProgressElapsedMs = 0;
            autoProgressPausedMs = 0;
            autoProgressMined = mined;
            autoProgressEventsSeen = events;
            return false;
        }
        long elapsed = Math.max(0L, now - autoProgressTickMs);
        autoProgressTickMs = now;
        autoProgressMined = mined;
        autoProgressEventsSeen = events;
        AutoEatSync eat = autoEatSyncModule();
        if (autoPaused || (eat != null && eat.isEating())) {
            long excluded = Math.min(elapsed, AUTO_PROGRESS_PAUSE_MS - autoProgressPausedMs);
            autoProgressPausedMs += excluded;
            elapsed -= excluded;
        }
        autoProgressElapsedMs += elapsed;
        if (autoProgressElapsedMs < AUTO_NO_PROGRESS_MS) return false;
        autoProgressElapsedMs = 0;
        autoProgressPausedMs = 0;
        recoverAutoNoProgress();
        return true;
    }

    private void recoverAutoNoProgress() {
        if (isShipwreck() && autoShipwreckRun != null) {
            autoLog("WATCHDOG", autoSite.center, "shipwreck no progress 180s state=" + state);
            if (!isVisited(autoSite.center)) abandonShipwreckChest("watchdog", System.currentTimeMillis());
            else recoverShipwreckExit("watchdog");
            return;
        }
        AutoState from = state;
        BlockPos center = autoSite != null ? autoSite.center : mc.player.getBlockPos();
        autoLog("WATCHDOG", center, "no progress 180s state=" + from + " player=" + playerPosLabel());
        restoreExitReturnSettings();
        if (takeoff.isActive()) takeoff.cancel();
        BaritoneHelper.stopAllPathing();
        releaseAllAutoKeys();
        mc.interactionManager.cancelBlockBreaking();
        if (mc.currentScreen instanceof GenericContainerScreen || mc.currentScreen instanceof ShulkerBoxScreen) {
            mc.player.closeHandledScreen();
        }
        if (autoPaused) endAutoPause();
        autoChestplateAllowed = false;
        setChestplateHold(false);
        autoRunAbandoned = true;
        autoAscendFromAbort = true;
        autoWalkReapproach = false;
        autoStackedPartner = null;
        if (autoSite == null || autoColumn == null) {
            finishAutoWatchdog("missing exit plan");
            return;
        }
        if (!center.equals(autoWatchdogAbandonedSite)) {
            autoWatchdogAbandonedSite = center;
            if (autoAbandonedOnce.add(center)) {
                autoWatchdogRetryOrigins.put(center, mc.player.getEntityPos());
                autoLog("WATCHDOG", center, "abandoned once, retry after flying 32 blocks away");
            } else if (!isVisited(center)) {
                markVisited(center, "abandoned twice");
                autoLog("WATCHDOG", center, "abandoned twice, marked failed");
            }
        }
        autoWatchdogExitMethods |= switch (from) {
            case SWIM_UP, WATER_JUMP, OBSIDIAN_PLATFORM -> 1;
            case ASCEND, TAKEOFF_SPOT -> 2;
            case SELF_ASCEND -> 4;
            case TAKEOFF, CLIMB, SHAFT_ALIGN, SHAFT_FLY -> 8;
            default -> 0;
        };
        if (mc.player.isGliding()) {
            if ((autoWatchdogExitMethods & 16) == 0) {
                autoWatchdogExitMethods |= 16;
                if (autoSoloMode) beginSoloCruise("watchdog, leave site in flight");
                else enterState(AutoState.RESUME, "watchdog, leave site in flight");
                return;
            }
        } else if (mc.player.isOnGround() || mc.player.isTouchingWater()) {
            if (mc.player.isTouchingWater() && (autoWatchdogExitMethods & 1) == 0) {
                autoWatchdogExitMethods |= 1;
                autoWaterJumpAttempts = 0;
                autoWaterJumpVertical = false;
                boolean surfaced = !mc.player.isSubmergedInWater() && isClearSkyHere();
                enterState(surfaced ? AutoState.WATER_JUMP : AutoState.SWIM_UP, "watchdog, water exit");
                return;
            }
            if (mc.player.isOnGround() && !mc.player.isTouchingWater() && isTrueSkyHere() && isClearSkyHere()
                && (autoWatchdogExitMethods & 8) == 0) {
                autoWatchdogExitMethods |= 8;
                autoAscendArrivedAt = null;
                enterState(AutoState.TAKEOFF, "watchdog, surface takeoff");
                return;
            }
            if ((autoWatchdogExitMethods & 2) == 0) {
                autoWatchdogExitMethods |= 2;
                enterState(AutoState.ASCEND, "watchdog, alternate exit via baritone");
                return;
            }
            if ((autoWatchdogExitMethods & 4) == 0) {
                autoWatchdogExitMethods |= 4;
                enterState(AutoState.SELF_ASCEND, "watchdog, alternate exit by hand");
                return;
            }
        }
        finishAutoWatchdog("exit methods exhausted or airborne without glide");
    }

    private void finishAutoWatchdog(String reason) {
        if (isShipwreck() && mc.player.isTouchingWater()) {
            recoverShipwreckExit(reason);
            return;
        }
        autoLog("WATCHDOG", autoSite != null ? autoSite.center : mc.player.getBlockPos(), reason + ", IDLE until moved 2 blocks");
        restoreExitReturnSettings();
        restoreAutoThrowawayItems();
        BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
        BaritoneHelper.stopAllPathing();
        releaseAllAutoKeys();
        if (takeoff.isActive()) takeoff.cancel();
        autoWatchdogIdlePos = mc.player.getEntityPos();
        resetAutoState();
    }

    private void autoTick() {
        if (state == AutoState.IDLE) return;

        autoHitThisTick = mc.player.hurtTime > 0 && mc.player.hurtTime > autoLastHurtTime;
        if (autoHitThisTick) {
            autoLastHurtTime = mc.player.hurtTime;
            autoLastHitMs = System.currentTimeMillis();
            DamageSource hitSource = mc.player.getRecentDamageSource();
            Entity hitAttacker = hitSource != null ? hitSource.getAttacker() : null;
            if (hitAttacker instanceof AbstractSkeletonEntity || hitAttacker instanceof WitchEntity) {
                autoHostileIds.add(hitAttacker.getId());
            }
            if (state == AutoState.SHAFT_ALIGN || state == AutoState.SHAFT_FLY) {
                autoLog(state.name(), autoSite.center, String.format("hit health=%.1f", mc.player.getHealth()));
            }
        }
        if (mc.player.hurtTime < autoLastHurtTime) {
            autoLastHurtTime = mc.player.hurtTime;
        }

        try {
            AutoFlyingRegear regear = autoFlyingRegearModule();
            if (regear != null && regear.isRunning() && isShipwreck() && mc.player.isTouchingWater()) {
                if (!autoShipwreckDeferred && !isVisited(autoSite.center)) abandonShipwreckChest("regear", System.currentTimeMillis());
            } else {
            if (regear != null && regear.isRunning()) {
                boolean tellAreaLoader = autoAreaLoaderWasOn;
                Module fm = selectedFlightModule();
                autoLog("YIELD", autoSite != null ? autoSite.center : mc.player.getBlockPos(), "AutoFlyingRegear took over");
                releaseAllAutoKeys();
                if (takeoff.isActive()) takeoff.cancel();
                BaritoneHelper.stopAllPathing();
                resetAutoState();
                if (tellAreaLoader && fm != null) warning("AutoFlyingRegear started - restart " + fm.name + " by hand if it was running.");
                return;
            }
            }

            if (autoNoProgressWatchdog(System.currentTimeMillis())) return;
            if (isShipwreck() && autoShipwreckRun != null && shipwreckPriority(System.currentTimeMillis())) return;

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
                case SELF_ASCEND -> tickSelfAscend();
                case TAKEOFF_SPOT -> tickTakeoffSpot();
                case TAKEOFF -> tickTakeoff();
                case SOLO_CRUISE -> tickSoloCruise();
                case RESUME -> tickResume();
                case FAILED -> tickFailed();
                default -> {}
            }
        } catch (Exception e) {
            if (log.get()) HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] exception state={} error={}", state, e.toString());
            try { restoreExitReturnSettings(); } catch (Exception ignored) {}
            try { releaseAllAutoKeys(); } catch (Exception ignored) {}
            try { BaritoneHelper.stopAllPathing(); } catch (Exception ignored) {}
            try { BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace; } catch (Exception ignored) {}
            try { restoreAutoThrowawayItems(); } catch (Exception ignored) {}
            try { if (takeoff.isActive()) takeoff.cancel(); } catch (Exception ignored) {}
            playAutoSound("spawner_failed", soundFailed);
            state = AutoState.FAILED;
            setChestplateHold(false);
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
            autoAreaLoaderWasOn = fmOn || autoMemoryChaseFromFlightModule;
            autoPitch40WasOn = p40 != null && p40.isActive() && !(fmOn && fm instanceof TrailFollower);
            autoElytraRecastWasOn = er != null && er.isActive();
            if (fmOn) {
                if (fm instanceof TrailFollower tf && fm.isActive()) {
                    autoTrailYaw = tf.headingYaw();
                    autoTrailYawSaved = true;
                } else if (fm instanceof WaypointFollower) {
                    autoWaypointYaw = mc.player.getYaw();
                    autoWaypointYawSaved = true;
                }
                fm.toggle();
            }
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
            autoLandSpotAttempts = 0;
            autoLandSpotGaveUpLogged = false;
            autoWaterDiveClearTicks = 0;
            if (autoColumn.water()) {
                autoLandSpot = null;
            } else {
                autoLandSpot = chooseLandSpot(autoColumn, LAND_RING_RADIUS);
                if (autoLandSpot != null) {
                    double dist = Math.hypot(autoLandSpot.getX() - autoColumn.x(), autoLandSpot.getZ() - autoColumn.z());
                    autoLog("LAND", autoSite.center, String.format("spot %d,%d,%d dist=%.1f",
                        autoLandSpot.getX(), autoLandSpot.getY(), autoLandSpot.getZ(), dist));
                } else {
                    autoLog("LAND", autoSite.center, "no clear spot");
                }
            }
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

    private boolean landSpotGateOk() {
        if (autoColumn.water() || autoLandSpot == null) return true;

        if (autoLandSpotAttempts >= 2) {
            if (!autoLandSpotGaveUpLogged) {
                autoLandSpotGaveUpLogged = true;
                autoLog("APPROACH", autoSite.center, "land spot path blocked, continuing anyway");
            }
            return true;
        }

        boolean blocked = pathToPointBlocked(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
            autoLandSpot.getX() + 0.5, autoLandSpot.getY() + 1.0, autoLandSpot.getZ() + 0.5);
        if (!blocked) return true;

        autoLandSpotAttempts++;
        BlockPos wider = chooseLandSpot(autoColumn, LAND_RING_RADIUS + autoLandSpotAttempts * 4);
        if (wider != null) autoLandSpot = wider;
        autoLog("APPROACH", autoSite.center, "land spot path blocked, retry " + autoLandSpotAttempts + " ring widened");
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
        double H = py - (plan.water() ? plan.exitY() : (plan.rim() != null ? plan.rim().getY() : plan.holeTopY()));
        Vec3d vel = mc.player.getVelocity();
        double speed = vel.length();
        double speedH = Math.hypot(vel.x, vel.z);
        BlockPos land = autoLandSpot;
        double yawDx = land != null ? (land.getX() + 0.5) - px : dx;
        double yawDz = land != null ? (land.getZ() + 0.5) - pz : dz;
        float yaw = (float) Math.toDegrees(Math.atan2(-yawDx, yawDz));

        if (D > 28) {
            double raw = Math.toDegrees(Math.atan2(H - autoFlareHeight.get(), D - 28));
            float pitch = (float) MathHelper.clamp(raw, -5.0, 45.0);
            if (speed > 2.2) pitch -= 15.0f;
            if (terrainCloseAhead(px, py, pz, yaw)) pitch -= 10.0f;
            if (speedH < 0.3) pitch = 30.0f;
            applyGlideRotation(yaw, pitch);
            logFlightTelemetry("APPROACH", H, D, speedH, vel.y);
        } else if (H > autoFlareHeight.get() + 10) {
            boolean diveClear = isWaterLandingZone() && !pathToPointBlocked(px, py, pz, plan.x() + 0.5, plan.exitY() + 1.0, plan.z() + 0.5);
            autoWaterDiveClearTicks = diveClear ? autoWaterDiveClearTicks + 1 : 0;
            if (autoWaterDiveClearTicks >= 10) {
                autoWaterDiveClearTicks = 0;
                autoHealthBeforeLanding = mc.player.getHealth();
                enterState(AutoState.FINAL, String.format("H=%.1f D=%.1f dive clear", H, D));
                return;
            }

            double angleToCenter = Math.toDegrees(Math.atan2(-yawDx, yawDz));
            double correction = MathHelper.clamp((D - 24) * 2, -30.0, 30.0);
            float heading = (float) MathHelper.wrapDegrees(angleToCenter + 90.0 + correction);
            float pitch = 35.0f;
            if (speed > 2.2) pitch -= 15.0f;
            applyGlideRotation(heading, pitch);
            logFlightTelemetry("SPIRAL", H, D, speedH, vel.y);
            if (H <= autoFlareHeight.get() + 5 && landSpotGateOk()) {
                autoHealthBeforeLanding = mc.player.getHealth();
                enterState(AutoState.FINAL, String.format("H=%.1f D=%.1f", H, D));
            }
        } else if (isWaterLandingZone()) {
            if (landSpotGateOk()) {
                autoHealthBeforeLanding = mc.player.getHealth();
                enterState(AutoState.FINAL, String.format("H=%.1f D=%.1f water", H, D));
            }
        } else if (landSpotGateOk()) {
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
                autoWaterDiveClearTicks = 0;
                enterState(AutoState.APPROACH, String.format("final blocked, spiraling H=%.1f D=%.1f", H, D));
                return;
            }

            applyGlideRotation(yaw, -45.0f);
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
        BlockPos land = autoLandSpot;
        double yawDx = land != null ? (land.getX() + 0.5) - mc.player.getX() : dx;
        double yawDz = land != null ? (land.getZ() + 0.5) - mc.player.getZ() : dz;
        float yaw = (float) Math.toDegrees(Math.atan2(-yawDx, yawDz));

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
        autoWalkBoxPos = null;
        autoWalkBoxTicks = 0;
        autoBaritoneRetries = 0;
        IBaritone bt = primaryBaritone();
        if (bt != null) {
            Goal goal = autoColumn.rim() != null && mc.player.getBlockPos().getY() > autoColumn.startY() + 3
                ? new GoalBlock(autoColumn.rim())
                : autoColumn.water() && mc.player.getBlockPos().getY() >= autoColumn.exitY() - 1
                    ? new GoalBlock(new BlockPos(autoColumn.x(), autoColumn.exitY() - 1, autoColumn.z()))
                    : !autoColumn.water() && autoColumn.rim() == null && mc.player.getBlockPos().getY() > autoColumn.startY() + 3
                        ? new GoalNear(new BlockPos(autoColumn.x(), autoColumn.holeTopY(), autoColumn.z()), 1)
                        : new GoalBlock(new BlockPos(autoColumn.x(), autoColumn.startY(), autoColumn.z()));
            bt.getCustomGoalProcess().setGoalAndPath(goal);
        }
    }

    private void tickWalk() {
        if (isShipwreck() && !autoSoloTransitOnly) {
            tickShipwreckWalk();
            return;
        }
        stateTicks++;

        if (mc.player.isInLava()) { abort("in lava"); return; }

        if (stateTicks == 1) {
            double startHoriz = Math.hypot((autoColumn.x() + 0.5) - mc.player.getX(), (autoColumn.z() + 0.5) - mc.player.getZ());
            autoWalkDirect = autoColumn.water() && mc.player.isTouchingWater();
            autoWalkStepping = false;
            autoWalkSwimOverride = false;
            autoWalkDirectDryTicks = 0;
            autoWalkDirectStuckTicks = 0;
            autoWalkDirectSwimStuckTicks = 0;
            autoWalkDirectSwimStuckPos = null;
            autoWalkDirectSwimBreaking = false;
            autoWalkDirectSwimBreakStage = 0;
            autoWalkDirectSwimBreakTarget = null;
            autoWalkDirectSwimBrokenTotal = 0;
            autoWalkKeyLogged = false;
            autoWalkStuckTicks = 0;
            autoWalkIdleTicks = 0;
            autoWalkBoxPos = null;
            autoWalkBoxTicks = 0;
            autoWalkStuckLastDist = -1;
            autoWalkStuckUseBreak = false;
            autoWalkStuckBreaking = false;
            autoWalkStuckBreakPos = null;
            autoWalkStuckBreakLabel = null;
            autoWalkBestDist = -1;
            autoWalkBestDistTick = stateTicks;
            autoWalkSinkBestTick = -1;
            autoWalkTestedShaft = null;
            if (autoWalkDirect) {
                autoLog("WALK", autoSite.center, "water direct");
            } else if (autoColumn.water()) {
                autoLog("WALK", autoSite.center, String.format("water hole, landed on ground, baritone first dist=%.1f", startHoriz));
                startWalkBaritoneGoal();
            } else {
                startWalkBaritoneGoal();
            }
        }

        double walkHoriz = Math.hypot((autoColumn.x() + 0.5) - mc.player.getX(), (autoColumn.z() + 0.5) - mc.player.getZ());
        if (autoWalkBestDist < 0 || walkHoriz <= autoWalkBestDist - 1.0) {
            autoWalkBestDist = walkHoriz;
            autoWalkBestDistTick = stateTicks;
        }
        int sinkFeetY = mc.player.getBlockPos().getY();
        if (autoWalkDirect && (autoColumn.water() || autoWalkSwimOverride) && walkHoriz <= 0.5) {
            if (autoWalkSinkBestTick < 0 || sinkFeetY < autoWalkSinkBestY) {
                autoWalkSinkBestY = sinkFeetY;
                autoWalkSinkBestTick = stateTicks;
            }
        } else {
            autoWalkSinkBestTick = -1;
        }
        boolean sinkStuck = autoWalkSinkBestTick >= 0 && sinkFeetY > autoColumn.startY() + 1 && stateTicks - autoWalkSinkBestTick > 100;

        if (stateTicks - autoWalkBestDistTick > 60 * 20 || sinkStuck) {
            if (!(autoAreaLoaderWasOn || autoSoloMode) || autoWalkReapproachCount >= 3) {
                abort("walk timeout");
                return;
            }
            autoWalkReapproachCount++;
            autoWalkReapproach = true;
            releaseAllAutoKeys();
            IBaritone walkBt = primaryBaritone();
            if (walkBt != null) walkBt.getPathingBehavior().cancelEverything();
            autoLog("WALK", autoSite.center, sinkStuck
                ? String.format("sink stuck 5s at y=%d, re-approach %d by air", sinkFeetY, autoWalkReapproachCount)
                : "no progress 60s, re-approach " + autoWalkReapproachCount + " by air");
            if (isClearSkyHere()) {
                if (mc.player.isTouchingWater()) {
                    autoWaterJumpAttempts = 0;
                    autoWaterJumpVertical = false;
                    enterState(AutoState.WATER_JUMP, "");
                } else {
                    autoAscendArrivedAt = null;
                    enterState(AutoState.TAKEOFF, "");
                }
            } else {
                enterState(AutoState.TAKEOFF_SPOT, "");
            }
            return;
        }

        if (autoWalkDirect && !mc.player.isTouchingWater() && mc.player.isOnGround()) {
            autoWalkDirectDryTicks++;
        } else {
            autoWalkDirectDryTicks = 0;
        }

        if (autoWalkDirect && !(autoColumn.water() || autoWalkSwimOverride) && mc.player.horizontalCollision && mc.player.isTouchingWater()) {
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

    private int waterHoleMouthY(ColumnPlan c) {
        int highestFloorY = Integer.MIN_VALUE;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            int nx = c.x() + dir.getOffsetX();
            int nz = c.z() + dir.getOffsetZ();
            for (int y = c.exitY() - 1; y >= c.startY(); y--) {
                cursor.set(nx, y, nz);
                BlockState state = mc.world.getBlockState(cursor);
                if (state.getFluidState().isIn(FluidTags.WATER) || isOpenState(state, cursor)) continue;
                if (state.getFluidState().isEmpty()) highestFloorY = Math.max(highestFloorY, y);
                break;
            }
        }
        return highestFloorY != Integer.MIN_VALUE ? highestFloorY + 1 : c.startY() + 1;
    }

    private void tickWalkDirect() {
        double dx = (autoColumn.x() + 0.5) - mc.player.getX();
        double dz = (autoColumn.z() + 0.5) - mc.player.getZ();
        double horiz = Math.hypot(dx, dz);

        boolean centered = recenterDig(autoColumn.x(), autoColumn.z(), 0.12);
        String phase;

        if (horiz > 0.5) {
            if (autoColumn.water() || autoWalkSwimOverride) {
                phase = "swim-descend";
                BlockPos feet = mc.player.getBlockPos();
                BlockPos below1 = feet.down();
                BlockPos below2 = feet.down(2);
                boolean onFloor = !isOpenState(mc.world.getBlockState(below1), below1);
                boolean nearFloor = onFloor || !isOpenState(mc.world.getBlockState(below2), below2);
                boolean horizCollision = mc.player.horizontalCollision;
                boolean sprintSwim = horiz > 2.0
                    && mc.player.isTouchingWater()
                    && mc.player.getHungerManager().getFoodLevel() > 6;
                if (sprintSwim) {
                    Vec3d eye = mc.player.getEyePos();
                    double pitchX = (autoColumn.x() + 0.5) - eye.x;
                    double pitchY = (waterHoleMouthY(autoColumn) + 0.5) - eye.y;
                    double pitchZ = (autoColumn.z() + 0.5) - eye.z;
                    float pitch = MathHelper.wrapDegrees((float) -Math.toDegrees(Math.atan2(pitchY, Math.hypot(pitchX, pitchZ))));
                    mc.player.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
                    Utils.setPressed(mc.options.sprintKey, true);
                    Utils.setPressed(mc.options.forwardKey, true);
                    Utils.setPressed(mc.options.sneakKey, false);
                    Utils.setPressed(mc.options.jumpKey, horizCollision);
                } else {
                    Utils.setPressed(mc.options.sprintKey, false);
                    Utils.setPressed(mc.options.jumpKey, horizCollision || onFloor);
                    Utils.setPressed(mc.options.sneakKey, !horizCollision && !nearFloor && !mc.player.isOnGround());
                }
            } else {
                phase = "swim";
                boolean headUnderwater = mc.player.isSubmergedInWater();
                boolean holdJump = mc.player.horizontalCollision || headUnderwater;
                Utils.setPressed(mc.options.sprintKey, false);
                Utils.setPressed(mc.options.jumpKey, holdJump);
                Utils.setPressed(mc.options.sneakKey, false);
            }
        } else {
            phase = "sink";
            Utils.setPressed(mc.options.sprintKey, false);
            Utils.setPressed(mc.options.jumpKey, false);
            Utils.setPressed(mc.options.sneakKey, horiz <= 0.15);
        }

        if ((autoColumn.water() || autoWalkSwimOverride) && (phase.equals("swim-descend") || phase.equals("sink"))) {
            tickSwimStuckBreak();
        } else {
            autoWalkDirectSwimStuckTicks = 0;
            autoWalkDirectSwimStuckPos = null;
            autoWalkDirectSwimBreaking = false;
            autoWalkDirectSwimBreakTarget = null;
        }

        int feetY = mc.player.getBlockPos().getY();
        boolean feetOk = Math.abs(feetY - autoColumn.startY()) <= 1
            || feetY <= autoSite.room.cy() + 3;
        BlockPos sinkBelow = new BlockPos(autoColumn.x(), feetY - 1, autoColumn.z());
        BlockState sinkBelowState = mc.world.getBlockState(sinkBelow);
        boolean onColumnBlock = !isOpenState(sinkBelowState, sinkBelow) && sinkBelowState.getFluidState().isEmpty();
        if (mc.player.isOnGround() && (feetOk || onColumnBlock) && centered) {
            releaseAllAutoKeys();
            enterDigState("");
            return;
        }

        logNavTelemetry("WALK " + phase, new BlockPos(autoColumn.x(), autoColumn.startY(), autoColumn.z()), 0);
    }

    private void tickSwimStuckBreak() {
        if (autoWalkDirectSwimBreaking) {
            swimBreakStep();
            return;
        }
        BlockPos curPos = mc.player.getBlockPos();
        if (mc.player.horizontalCollision && curPos.equals(autoWalkDirectSwimStuckPos)) {
            autoWalkDirectSwimStuckTicks++;
        } else {
            autoWalkDirectSwimStuckTicks = 0;
        }
        autoWalkDirectSwimStuckPos = curPos;
        if (autoWalkDirectSwimStuckTicks >= 45 && autoWalkDirectSwimBrokenTotal < 6) {
            autoWalkDirectSwimBreaking = true;
            autoWalkDirectSwimBreakStage = 0;
            autoWalkDirectSwimBreakTarget = null;
        }
    }

    private BlockPos swimBlockedCell(int stage) {
        BlockPos feet = mc.player.getBlockPos();
        int nx = feet.getX();
        int nz = feet.getZ();
        if (feet.getX() != autoColumn.x()) nx += Integer.signum(autoColumn.x() - feet.getX());
        else nz += Integer.signum(autoColumn.z() - feet.getZ());
        return new BlockPos(nx, feet.getY() + stage, nz);
    }

    private void swimBreakStep() {
        BlockPos target = swimBlockedCell(autoWalkDirectSwimBreakStage);
        BlockState state = mc.world.getBlockState(target);
        if (isOpenState(state, target)) {
            if (target.equals(autoWalkDirectSwimBreakTarget)) {
                autoWalkDirectSwimBrokenTotal++;
                autoLog("WALK", autoSite.center, String.format("swim blocked, broken %d,%d,%d block=%s",
                    target.getX(), target.getY(), target.getZ(), autoWalkDirectSwimBreakBlockName));
            }
            autoWalkDirectSwimBreakTarget = null;
            if (autoWalkDirectSwimBreakStage == 0) {
                autoWalkDirectSwimBreakStage = 1;
                return;
            }
            autoWalkDirectSwimBreaking = false;
            autoWalkDirectSwimStuckTicks = 0;
            autoWalkDirectSwimStuckPos = null;
            return;
        }
        if (autoWalkDirectSwimBrokenTotal >= 6 || isLavaNearShaft(target, target)) {
            autoWalkDirectSwimBreaking = false;
            autoWalkDirectSwimStuckTicks = 0;
            autoWalkDirectSwimStuckPos = null;
            autoWalkDirectSwimBreakTarget = null;
            return;
        }
        autoWalkDirectSwimBreakTarget = target;
        autoWalkDirectSwimBreakBlockName = Registries.BLOCK.getId(state.getBlock()).getPath();
        autoMineTick("WALK", target, false);
    }

    private boolean waterAllTheWayToGoal() {
        int x = autoColumn.x();
        int z = autoColumn.z();
        int startY = autoColumn.startY();
        int top = MathHelper.floor(mc.player.getY());
        if (top <= startY) return false;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = Math.min(top, startY + 40); y > startY; y--) {
            if (!mc.world.getFluidState(cursor.set(x, y, z)).isIn(FluidTags.WATER)) return false;
        }
        return true;
    }

    private void tickWalkBaritone() {
        IBaritone bt = primaryBaritone();
        BlockPos feet = mc.player.getBlockPos();
        if (mc.player.isOnGround() || mc.player.isTouchingWater()) {
            for (DugHoles.Feature shaft : autoSite.entryShafts) {
                if ((shaft.minX() == autoColumn.x() && shaft.minZ() == autoColumn.z())
                    || feet.getX() < shaft.minX() || feet.getX() > shaft.maxX()
                    || feet.getZ() < shaft.minZ() || feet.getZ() > shaft.maxZ()
                    || feet.getY() < autoSite.room.cy() || feet.getY() > shaft.maxY()) continue;
                if (autoWalkTestedShaft != null && shaft.minX() == autoWalkTestedShaft.minX()
                    && shaft.minZ() == autoWalkTestedShaft.minZ()) break;
                autoWalkTestedShaft = shaft;
                ColumnPlan plan = evaluateExistingShaft(autoSite, shaft, autoAbandonedOnce.contains(autoSite.center), true);
                if (plan != null) {
                    autoColumn = plan;
                    autoLog("WALK", autoSite.center, "fell into existing shaft " + plan.x() + "," + plan.z() + ", using it");
                    if (bt != null) bt.getPathingBehavior().cancelEverything();
                }
                break;
            }
        }
        double dx = (autoColumn.x() + 0.5) - mc.player.getX();
        double dz = (autoColumn.z() + 0.5) - mc.player.getZ();
        double horiz = Math.hypot(dx, dz);
        boolean feetOk = mc.player.getBlockPos().getY() == autoColumn.startY()
            || (!autoColumn.water() && autoColumn.rim() == null && mc.player.isOnGround()
                && mc.player.getBlockPos().getY() > autoColumn.startY());

        if (horiz <= 0.5 && feetOk) {
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            if (centerOnColumn(autoColumn.x(), autoColumn.z(), 0.2)) {
                releaseAllAutoKeys();
                enterDigState("");
            }
            return;
        }

        int feetYNow = mc.player.getBlockPos().getY();
        boolean belowHoleTop = (!autoColumn.water() && autoColumn.rim() == null
            ? feetYNow <= autoColumn.holeTopY() : feetYNow < autoColumn.startY()) && feetYNow >= autoSite.room.cy();
        if (horiz <= 0.5 && belowHoleTop) {
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            if (centerOnColumn(autoColumn.x(), autoColumn.z(), 0.2)) {
                releaseAllAutoKeys();
                enterDigState("in column below hole top y=" + feetYNow + ", digging from here");
            }
            return;
        }

        BlockPos rim = autoColumn.rim();

        if (autoWalkStepping) {
            Utils.setPressed(mc.options.sneakKey, false);
            recenterDig(autoColumn.x(), autoColumn.z(), 0.15);

            int feetY = (int) Math.floor(mc.player.getY() + 0.5);
            if (mc.player.isOnGround() && feetY < rim.getY()) {
                if (centerOnColumn(autoColumn.x(), autoColumn.z(), 0.2)) {
                    releaseAllAutoKeys();
                    enterDigState("landed in hole y=" + feetY);
                }
            }
            return;
        }

        BlockPos gateRim = mc.player.getBlockPos().getY() > autoColumn.startY() + 3 ? rim : null;

        if (gateRim != null) {
            double rdx = (gateRim.getX() + 0.5) - mc.player.getX();
            double rdz = (gateRim.getZ() + 0.5) - mc.player.getZ();
            double rHoriz = Math.hypot(rdx, rdz);
            int rimFeetY = (int) Math.floor(mc.player.getY() + 0.5);
            if (mc.player.isOnGround() && rimFeetY <= gateRim.getY() && rimFeetY >= gateRim.getY() - 3 && rHoriz <= 0.5) {
                if (bt != null) bt.getPathingBehavior().cancelEverything();
                releaseAllAutoKeys();
                autoWalkStepping = true;
                autoLog("WALK", autoSite.center, String.format("step into hole from rim %d,%d,%d", gateRim.getX(), gateRim.getY(), gateRim.getZ()));
                return;
            }
        }

        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
        if (!pathing && mc.player.isTouchingWater() && waterAllTheWayToGoal()) {
            releaseAllAutoKeys();
            autoWalkDirect = true;
            autoWalkSwimOverride = true;
            autoWalkDirectDryTicks = 0;
            autoWalkDirectStuckTicks = 0;
            autoLog("WALK", autoSite.center, "baritone stopped in water, swimming to goal");
            return;
        }
        if (autoColumn.water() && mc.player.isTouchingWater() && horiz <= 2.0
            && !pathing && (bt == null || !bt.getCustomGoalProcess().isActive())) {
            releaseAllAutoKeys();
            autoWalkDirect = true;
            autoWalkDirectDryTicks = 0;
            autoWalkDirectStuckTicks = 0;
            autoWalkDirectSwimStuckTicks = 0;
            autoWalkDirectSwimStuckPos = null;
            autoWalkDirectSwimBreaking = false;
            autoWalkDirectSwimBreakStage = 0;
            autoWalkDirectSwimBreakTarget = null;
            autoLog("WALK", autoSite.center, "near water hole, swimming down");
            return;
        }
        boolean grounded = mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isClimbing();
        boolean baritoneGaveUp = bt == null || !bt.getCustomGoalProcess().isActive();
        int nearKeyX = gateRim != null ? gateRim.getX() : autoColumn.x();
        int nearKeyZ = gateRim != null ? gateRim.getZ() : autoColumn.z();
        int nearKeyY = gateRim != null ? gateRim.getY()
            : !autoColumn.water() && autoColumn.rim() == null && mc.player.getBlockPos().getY() > autoColumn.startY() + 3
                ? autoColumn.holeTopY() : autoColumn.startY();
        int feetY = (int) Math.floor(mc.player.getY() + 0.5);
        boolean nearHole = Math.hypot((nearKeyX + 0.5) - mc.player.getX(), (nearKeyZ + 0.5) - mc.player.getZ()) <= 3.0
            && (feetY == nearKeyY || (gateRim == null && feetY == nearKeyY - 1)
                || (!pathing && grounded && gateRim == null && autoColumn.water()
                    && Math.abs(feetY - autoColumn.exitY()) <= 1));
        if (pathing && nearHole && stateTicks - autoWalkBestDistTick > 100) {
            bt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            autoLog("WALK", autoSite.center, "near hole, baritone no progress 5s");
            return;
        }
        if (pathing) {
            BlockPos stuckFeet = mc.player.getBlockPos();
            if (autoWalkBoxPos != null
                && Math.abs(stuckFeet.getX() - autoWalkBoxPos.getX()) <= 2
                && Math.abs(stuckFeet.getY() - autoWalkBoxPos.getY()) <= 2
                && Math.abs(stuckFeet.getZ() - autoWalkBoxPos.getZ()) <= 2) {
                autoWalkBoxTicks++;
            } else {
                autoWalkBoxPos = stuckFeet;
                autoWalkBoxTicks = 0;
            }
        } else if (baritoneGaveUp) {
            autoWalkBoxPos = null;
            autoWalkBoxTicks = 0;
        }
        if (autoWalkBoxTicks >= 240) {
            bt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            autoWalkBoxPos = null;
            autoWalkBoxTicks = 0;
            autoLog("WALK", autoSite.center, "baritone no progress 12s");
            return;
        }
        if (!pathing && !baritoneGaveUp) {
            autoWalkIdleTicks++;
            if (nearHole || autoWalkIdleTicks >= 100) {
                bt.getPathingBehavior().cancelEverything();
                baritoneGaveUp = true;
                autoWalkIdleTicks = 0;
                if (!nearHole) autoLog("WALK", autoSite.center, "baritone idle 5s, restarting");
            }
        } else {
            autoWalkIdleTicks = 0;
        }
        if (!pathing && baritoneGaveUp && grounded && stateTicks > 20 && autoBaritoneRetries < 3 && !nearHole) {
            autoBaritoneRetries++;
            if (bt != null) {
                Goal goal = gateRim != null ? new GoalBlock(gateRim)
                    : autoColumn.water() && mc.player.getBlockPos().getY() >= autoColumn.exitY() - 1
                        ? new GoalBlock(new BlockPos(autoColumn.x(), autoColumn.exitY() - 1, autoColumn.z()))
                        : !autoColumn.water() && autoColumn.rim() == null && mc.player.getBlockPos().getY() > autoColumn.startY() + 3
                            ? new GoalNear(new BlockPos(autoColumn.x(), autoColumn.holeTopY(), autoColumn.z()), 1)
                            : new GoalBlock(new BlockPos(autoColumn.x(), autoColumn.startY(), autoColumn.z()));
                bt.getCustomGoalProcess().setGoalAndPath(goal);
            }
        } else if (!pathing && baritoneGaveUp && (autoBaritoneRetries >= 3 || nearHole)) {
            int keyX = nearKeyX;
            int keyZ = nearKeyZ;
            if (!autoWalkKeyLogged) {
                autoWalkKeyLogged = true;
                autoLog("WALK", autoSite.center, String.format(nearHole ? "near hole, walking straight to %d,%d" : "baritone gave up, walking to %d,%d", keyX, keyZ));
            }
            if (autoWalkStuckBreaking) {
                Utils.setPressed(mc.options.forwardKey, false);
                Utils.setPressed(mc.options.jumpKey, false);
                Utils.setPressed(mc.options.sneakKey, false);
                BlockState breakState = mc.world.getBlockState(autoWalkStuckBreakPos);
                if (isOpenState(breakState, autoWalkStuckBreakPos)) {
                    autoLog("WALK", autoSite.center, String.format("walk stuck, broken %d,%d,%d block=%s",
                        autoWalkStuckBreakPos.getX(), autoWalkStuckBreakPos.getY(), autoWalkStuckBreakPos.getZ(), autoWalkStuckBreakLabel));
                    BlockPos next = findWalkStuckObstacle(keyX, keyZ);
                    if (next != null) {
                        BlockState nextState = mc.world.getBlockState(next);
                        autoWalkStuckBreakPos = next;
                        autoWalkStuckBreakLabel = Registries.BLOCK.getId(nextState.getBlock()).getPath();
                        autoLog("WALK", autoSite.center, String.format("walk stuck, breaking %d,%d,%d block=%s (next)",
                            next.getX(), next.getY(), next.getZ(), autoWalkStuckBreakLabel));
                    } else {
                        autoWalkStuckBreaking = false;
                        autoWalkStuckBreakPos = null;
                        autoWalkStuckTicks = 0;
                        autoWalkStuckLastDist = -1;
                    }
                } else {
                    autoMineTick("WALK", autoWalkStuckBreakPos, false);
                }
            } else {
                Utils.setPressed(mc.options.sneakKey, false);
                recenterDig(keyX, keyZ, 0.2);
                Utils.setPressed(mc.options.jumpKey, mc.player.horizontalCollision && (mc.player.isOnGround() || mc.player.isTouchingWater()));

                autoWalkStuckTicks++;
                if (autoWalkStuckTicks >= 40) {
                    double dist = Math.hypot((keyX + 0.5) - mc.player.getX(), (keyZ + 0.5) - mc.player.getZ());
                    if (autoWalkStuckLastDist >= 0 && autoWalkStuckLastDist - dist < 0.5) {
                        if (!autoWalkStuckUseBreak && !nearHole && dist > 3.0) {
                            autoBaritoneRetries = 0;
                            autoWalkKeyLogged = false;
                            autoWalkStuckUseBreak = true;
                            releaseAllAutoKeys();
                            autoLog("WALK", autoSite.center, "walk stuck, baritone again");
                        } else {
                            BlockPos obstacle = findWalkStuckObstacle(keyX, keyZ);
                            if (obstacle != null) {
                                BlockState obstacleState = mc.world.getBlockState(obstacle);
                                autoWalkStuckBreaking = true;
                                autoWalkStuckBreakPos = obstacle;
                                autoWalkStuckBreakLabel = Registries.BLOCK.getId(obstacleState.getBlock()).getPath();
                                autoWalkStuckUseBreak = false;
                                autoLog("WALK", autoSite.center, String.format("walk stuck, breaking %d,%d,%d block=%s",
                                    obstacle.getX(), obstacle.getY(), obstacle.getZ(), autoWalkStuckBreakLabel));
                            } else {
                                autoBaritoneRetries = 0;
                                autoWalkKeyLogged = false;
                                releaseAllAutoKeys();
                                autoLog("WALK", autoSite.center, "walk stuck, baritone again");
                            }
                        }
                        autoWalkStuckTicks = 0;
                        autoWalkStuckLastDist = -1;
                    } else {
                        autoWalkStuckLastDist = dist;
                        autoWalkStuckTicks = 0;
                    }
                }
            }
        }

        logNavTelemetry("WALK", new BlockPos(autoColumn.x(), autoColumn.startY(), autoColumn.z()), autoBaritoneRetries);
    }

    private boolean canBreakWalkStuckObstacle(BlockPos obstacle) {
        BlockState obstacleState = mc.world.getBlockState(obstacle);
        return obstacleState.getBlock() != Blocks.SPAWNER
            && !isContainerPresent(obstacle)
            && !isLava(obstacleState)
            && !isLavaNearShaft(obstacle, obstacle.up())
            && obstacle.getY() >= mc.player.getBlockPos().getY()
            && mc.player.getEyePos().distanceTo(Vec3d.ofCenter(obstacle)) <= 4.2;
    }

    private BlockPos walkStuckCandidate(BlockPos feet, int stepX, int stepZ) {
        BlockPos cell = feet.add(stepX, 0, stepZ);
        BlockPos headCell = cell.up();
        if (!isOpenState(mc.world.getBlockState(headCell), headCell) && canBreakWalkStuckObstacle(headCell)) {
            return headCell;
        }
        if (!isOpenState(mc.world.getBlockState(cell), cell) && canBreakWalkStuckObstacle(cell)) {
            return cell;
        }
        return null;
    }

    private BlockPos findWalkStuckObstacle(int keyX, int keyZ) {
        BlockPos feet = mc.player.getBlockPos();
        int dx = keyX - feet.getX();
        int dz = keyZ - feet.getZ();
        if (dx == 0 && dz == 0) return null;
        boolean xFirst = Math.abs(dx) >= Math.abs(dz);
        BlockPos candidate = xFirst
            ? walkStuckCandidate(feet, Integer.signum(dx), 0)
            : walkStuckCandidate(feet, 0, Integer.signum(dz));
        if (candidate == null && dx != 0 && dz != 0) {
            candidate = xFirst
                ? walkStuckCandidate(feet, 0, Integer.signum(dz))
                : walkStuckCandidate(feet, Integer.signum(dx), 0);
        }
        return candidate;
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
        boolean pressForward = true;
        if (mc.player.isTouchingWater()) {
            Vec3d velocity = mc.player.getVelocity();
            double towardTarget = (velocity.x * dx + velocity.z * dz) / dist;
            double drag = mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.DOLPHINS_GRACE) ? 0.96
                : mc.player.isSprinting() ? 0.9 : 0.8;
            double coast = Math.max(0.0, towardTarget) / (1.0 - drag);
            pressForward = dist - coast > eps;
        }
        Utils.setPressed(mc.options.forwardKey, pressForward);
        return false;
    }

    private boolean navigateExitColumn(int columnX, int columnZ, double columnEps, BlockPos entry, boolean holdJumpAlways) {
        BlockPos pos = mc.player.getBlockPos();

        if (entry != null && (pos.getX() != columnX || pos.getZ() != columnZ)) {
            boolean angled = entry.getX() != columnX && entry.getZ() != columnZ;
            boolean stageOne = angled && pos.getZ() != columnZ;
            boolean axisX = angled ? !stageOne : entry.getX() != columnX;
            int fixedCoord = angled ? (stageOne ? entry.getX() : columnZ) : (axisX ? entry.getZ() : entry.getX());
            int alongTarget = angled ? (stageOne ? columnZ : columnX) : (axisX ? columnX : columnZ);
            int alongPos = axisX ? pos.getX() : pos.getZ();
            int offAxisPos = axisX ? pos.getZ() : pos.getX();

            int nextAlong = (offAxisPos == fixedCoord && alongPos != alongTarget)
                ? alongPos + Integer.signum(alongTarget - alongPos) : alongPos;
            int nx = axisX ? nextAlong : fixedCoord;
            int nz = axisX ? fixedCoord : nextAlong;

            if (autoExitPathCell == null || autoExitPathCell.getX() != nx || autoExitPathCell.getZ() != nz) {
                autoExitPathCell = new BlockPos(nx, entry.getY(), nz);
                autoExitPathStep++;
                autoLog("EXIT", autoSite.center, String.format("exit path step %d x=%d,z=%d", autoExitPathStep, nx, nz));
            }

            Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater() && !mc.player.isOnGround() && !mc.player.horizontalCollision);
            recenterDig(nx, nz, 0.3);
            Utils.setPressed(mc.options.jumpKey, mc.player.horizontalCollision && (mc.player.isTouchingWater() || mc.player.isOnGround()));
            return false;
        }

        Utils.setPressed(mc.options.sneakKey, false);
        boolean centered = recenterDig(columnX, columnZ, columnEps);
        Utils.setPressed(mc.options.jumpKey, holdJumpAlways || mc.player.horizontalCollision);
        return centered;
    }

    private void shiftAutoPauseTimers(long pausedMs) {
        if (pausedMs <= 0) return;
        if (isShipwreck() && autoShipwreckRun != null) autoShipwreckRun.shiftChestTimer(pausedMs);
        switch (state) {
            case DIG -> {
                autoDigTotalStartMs += pausedMs;
                autoDigBlockStartMs += pausedMs;
                autoShipwreckDigProgressMs += pausedMs;
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
        if (isShipwreck()) return Math.abs(x - autoSite.center.getX()) <= 8
            && Math.abs(y - autoSite.center.getY()) <= 8 && Math.abs(z - autoSite.center.getZ()) <= 8;
        Room room = autoSite.room;
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        return bx >= room.minX() - 6 && bx <= room.maxX() + 6
            && bz >= room.minZ() - 6 && bz <= room.maxZ() + 6
            && y >= room.minY() - 6 && y <= room.maxY() + 6;
    }

    private boolean shaftSkeletonChaseable(Entity target) {
        return (target instanceof AbstractSkeletonEntity || target instanceof WitchEntity)
            && ((insideRoomBounds(target.getX(), target.getY(), target.getZ())
                && insideRoomBounds(mc.player.getX(), mc.player.getY(), mc.player.getZ()))
                || (autoExitShaft != null
                    && Math.abs(target.getX() - (autoExitShaft.x() + 0.5)) <= 10.0
                    && Math.abs(target.getY() - autoExitShaft.bottomY()) <= 10.0
                    && Math.abs(target.getZ() - (autoExitShaft.z() + 0.5)) <= 10.0
                    && Math.abs(mc.player.getX() - (autoExitShaft.x() + 0.5)) <= 10.0
                    && Math.abs(mc.player.getY() - autoExitShaft.bottomY()) <= 10.0
                    && Math.abs(mc.player.getZ() - (autoExitShaft.z() + 0.5)) <= 10.0));
    }

    private boolean shaftSkeletonAiming(Entity target) {
        return target instanceof AbstractSkeletonEntity skeleton
            && skeleton.isAlive()
            && skeleton.isAttacking()
            && skeleton.canSee(mc.player);
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

    private void cancelShaftChaseBaritone() {
        if (!autoShaftChaseBaritoneActive) return;
        IBaritone bt = primaryBaritone();
        if (bt != null) bt.getPathingBehavior().cancelEverything();
        autoShaftChaseBaritoneActive = false;
        autoShaftChaseBaritoneGoalPos = null;
    }

    private boolean handleShaftCombat() {
        boolean recentHit = autoLastHitMs != 0 && (System.currentTimeMillis() - autoLastHitMs) < 3000L;
        boolean skeletonChaseable = false;
        for (Entity entity : mc.world.getEntities()) {
            boolean known = autoHostileIds.contains(entity.getId());
            if ((known || shaftSkeletonAiming(entity)) && entity.isAlive() && shaftSkeletonChaseable(entity)) {
                if (!known) autoHostileIds.add(entity.getId());
                skeletonChaseable = true;
                break;
            }
        }
        boolean inCombat = recentHit || skeletonChaseable;

        if (!inCombat) {
            if (autoPaused) endAutoPause();
            cancelShaftChaseBaritone();
            autoShaftChaseStuckTicks = 0;
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

        DamageSource recentSource = mc.player.getRecentDamageSource();
        Entity attacker = recentSource != null ? recentSource.getAttacker() : null;
        Entity target = ((attacker instanceof AbstractSkeletonEntity || attacker instanceof WitchEntity) && attacker.isAlive() && shaftSkeletonChaseable(attacker))
            ? attacker : null;
        double bestDistSq = target != null ? target.squaredDistanceTo(mc.player) : Double.MAX_VALUE;
        if (target == null) {
            for (Entity entity : mc.world.getEntities()) {
                boolean known = autoHostileIds.contains(entity.getId());
                if ((!known && !shaftSkeletonAiming(entity)) || !entity.isAlive()) continue;
                if (!shaftSkeletonChaseable(entity)) continue;
                if (!known) autoHostileIds.add(entity.getId());
                double distSq = entity.squaredDistanceTo(mc.player);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    target = entity;
                }
            }
        }

        if (target != null) {
            double dist = Math.sqrt(bestDistSq);
            if (target != autoShaftChaseTarget) {
                cancelShaftChaseBaritone();
                autoShaftChaseTarget = target;
                autoShaftChaseLastDist = dist;
                autoShaftChaseStuckTicks = 0;
                autoLog(state.name(), autoSite.center, String.format("chase id=%d dist=%.1f", target.getId(), dist));
            } else if (dist <= autoShaftChaseLastDist - 0.5) {
                autoShaftChaseLastDist = dist;
                autoShaftChaseStuckTicks = 0;
            } else {
                autoShaftChaseStuckTicks++;
            }

            BlockPos targetPos = target.getBlockPos();
            if (!autoShaftChaseBaritoneActive && autoShaftChaseStuckTicks >= 40) {
                IBaritone bt = primaryBaritone();
                if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalNear(targetPos, 2));
                autoShaftChaseBaritoneActive = true;
                autoShaftChaseBaritoneGoalPos = targetPos;
                autoLog(state.name(), autoSite.center, String.format("chase via baritone id=%d dist=%.1f", target.getId(), dist));
            }

            if (autoShaftChaseBaritoneActive) {
                if (autoShaftChaseBaritoneGoalPos == null || targetPos.getSquaredDistance(autoShaftChaseBaritoneGoalPos) > 4.0) {
                    IBaritone bt = primaryBaritone();
                    if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalNear(targetPos, 2));
                    autoShaftChaseBaritoneGoalPos = targetPos;
                }
                releaseAllAutoKeys();
            } else {
                moveTowardShaftTarget(target);
            }
        } else {
            cancelShaftChaseBaritone();
            autoShaftChaseTarget = null;
            autoShaftChaseStuckTicks = 0;
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
        if (isShipwreck() && mc.world.getBlockState(pos).getBlock() == Blocks.CHEST
            && !(state == AutoState.ROOM && pos.equals(autoSite.center))) return false;
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
        if (isShipwreck()) {
            tickShipwreckDig();
            return;
        }
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
            autoDigBlockRetries.clear();
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
        long now = System.currentTimeMillis();
        long totalTimeout = Math.max(120_000L, 4_000L * autoDigBlocksPlanned) * mult;
        if (System.currentTimeMillis() - autoDigTotalStartMs > totalTimeout) { abort("dig timeout"); return; }

        recenterDig(autoColumn.x(), autoColumn.z(), 0.12);
        if (mc.player.isTouchingWater()) { Utils.setPressed(mc.options.sprintKey, false); mc.player.setSprinting(false); }

        int feetY = mc.player.getBlockPos().getY();
        int roomCy = autoSite.room.cy();
        BlockPos below = new BlockPos(autoColumn.x(), feetY - 1, autoColumn.z());
        BlockState belowState = mc.world.getBlockState(below);
        double gap = Math.hypot((autoColumn.x() + 0.5) - mc.player.getX(), (autoColumn.z() + 0.5) - mc.player.getZ());

        if (feetY > roomCy && below.getY() >= roomCy && below.getY() <= roomCy + 3 && roomColumnClearable(below, belowState)) {
            if (gap > 0.6) {
                Utils.setPressed(mc.options.attackKey, false);
                if (autoDigDisplacedSinceMs == 0) {
                    autoDigDisplacedSinceMs = System.currentTimeMillis();
                    autoLog("DIG", autoSite.center, String.format("displaced gap=%.2f", gap));
                } else if (System.currentTimeMillis() - autoDigDisplacedSinceMs > 3_000L) {
                    releaseAllAutoKeys();
                    enterState(AutoState.WALK, String.format("displaced gap=%.2f, back to the column", gap));
                }
                return;
            }
            autoDigDisplacedSinceMs = 0;
            if (!below.equals(autoDigQueuedPos)) {
                if (!mc.player.isOnGround()) return;
                autoDigQueuedPos = below;
                autoDigQueuedBlockLabel = Registries.BLOCK.getId(belowState.getBlock()).getPath();
                autoDigBlockStartMs = System.currentTimeMillis();
                autoDigBlockTicks = 0;
            }
            autoDigBlockTicks++;
            if (autoMineTick("DIG", below, true)) handleDigBlockCleared(below, gap);
            if (autoDigQueuedPos != null && now - autoDigBlockStartMs > perBlockTimeout) {
                if (autoDigBlockRetries.merge(autoDigQueuedPos.toImmutable(), 1, Integer::sum) >= 2) {
                    abort("dig block timeout");
                } else {
                    Utils.setPressed(mc.options.attackKey, false);
                    autoDigQueuedPos = null;
                    autoDigBlockStartMs = now;
                    autoLog("DIG", autoSite.center, "dig block retry");
                }
            }
            return;
        }

        boolean groundArrival = mc.player.isOnGround()
            && (feetY <= roomCy || !(belowState.getBlock() instanceof net.minecraft.block.FallingBlock));

        if (below.getY() < roomCy - 1
            || (feetY <= roomCy + 3 && (groundArrival || isOpenState(belowState, below)))) {
            releaseAllAutoKeys();
            boolean water = autoColumn.water() || autoColumn.waterCells() > 0
                || columnHasWater(autoColumn.x(), autoColumn.z(), roomCy, autoColumn.holeTopY());
            autoExitShaft = new ExitShaft(autoColumn.x(), autoColumn.z(), roomCy,
                autoColumn.holeTopY(), water, autoColumn.skyBlocked(), null);
            autoExitShaftForeign = false;
            autoTunnelTrail.clear();
            enterState(AutoState.ROOM, "");
            return;
        }

        if (gap > 0.6) {
            Utils.setPressed(mc.options.attackKey, false);
            if (autoDigDisplacedSinceMs == 0) {
                autoDigDisplacedSinceMs = System.currentTimeMillis();
                autoLog("DIG", autoSite.center, String.format("displaced gap=%.2f", gap));
            } else if (System.currentTimeMillis() - autoDigDisplacedSinceMs > 3_000L) {
                releaseAllAutoKeys();
                enterState(AutoState.WALK, String.format("displaced gap=%.2f, back to the column", gap));
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

        if (autoDigQueuedPos != null && now - autoDigBlockStartMs > perBlockTimeout) {
            if (autoDigBlockRetries.merge(autoDigQueuedPos.toImmutable(), 1, Integer::sum) >= 2) {
                abort("dig block timeout");
            } else {
                Utils.setPressed(mc.options.attackKey, false);
                autoDigQueuedPos = null;
                autoDigBlockStartMs = now;
                autoLog("DIG", autoSite.center, "dig block retry");
            }
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

    private boolean roomColumnClearable(BlockPos pos, BlockState state) {
        if (isOpenState(state, pos) || !state.getFluidState().isEmpty()) return false;
        if (state.getBlock() == Blocks.SPAWNER || isContainerPresent(pos)) return false;
        if (isLava(state) || isLavaNearShaft(pos, pos.up())) return false;
        return true;
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
        boolean deepSide = isDeepSidePlan(col, room);
        BlockPos feet = mc.player.getBlockPos();
        int feetY = feet.getY();

        if (insideInterior(room, feet.getX(), feet.getZ()) && feetY >= cy && feetY <= cy + 3) {
            releaseAllAutoKeys();
            boolean water = col.water() || columnHasWater(col.x(), col.z(), cy, col.holeTopY());
            autoExitShaft = new ExitShaft(col.x(), col.z(), cy, col.holeTopY(), water, col.skyBlocked(), col.sideEntry());
            autoExitShaftForeign = false;
            autoTunnelTrail.clear();
            enterState(AutoState.ROOM, "side tunnel done");
            return;
        }

        String phase;
        BlockPos targetCell;

        if (feetY > (deepSide ? col.startY() : cy)) {
            phase = deepSide ? "deep-descend" : "descend";
            recenterDig(col.x(), col.z(), mc.player.isTouchingWater() ? 0.12 : 0.25);
            double gap = Math.hypot((col.x() + 0.5) - mc.player.getX(), (col.z() + 0.5) - mc.player.getZ());
            Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater() && gap <= 0.15);
            BlockPos below = new BlockPos(col.x(), feetY - 1, col.z());
            targetCell = below;
            BlockState belowState = mc.world.getBlockState(below);
            if (!isOpenState(belowState, below) && mc.player.isOnGround()) {
                trackSideDigBlock(below);
                if (state != AutoState.SIDE_DIG) return;
                autoMineTick("SIDE_DIG", below, true);
            }
        } else if (deepSide) {
            phase = "deep-bottom";
            targetCell = new BlockPos(col.x(), feetY, col.z());
            recenterDig(col.x(), col.z(), mc.player.isTouchingWater() ? 0.12 : 0.25);
            if (feetY < cy && (mc.player.isOnGround() || mc.player.isTouchingWater())) {
                enterTunnelFromDeepSideShaft(col, feetY);
                return;
            }
        } else if (feetY == cy) {
            phase = "horizontal";
            BlockPos t = col.sideEntry();
            int nx = feet.getX();
            int nz = feet.getZ();
            if (sideEntryAlongZ(room, col.x(), col.z())) {
                if (feet.getZ() != t.getZ()) nz += Integer.signum(t.getZ() - feet.getZ());
                else nx += Integer.signum(t.getX() - feet.getX());
            } else if (feet.getX() != t.getX()) nx += Integer.signum(t.getX() - feet.getX());
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
                if (state != AutoState.SIDE_DIG) return;
                autoMineTick("SIDE_DIG", nHead, false);
            } else {
                BlockState floorState = mc.world.getBlockState(nFloor);
                trackSideDigBlock(nFloor);
                if (state != AutoState.SIDE_DIG) return;
                if (!isOpenState(floorState, nFloor)) {
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

    private boolean isDeepSidePlan(ColumnPlan plan, Room room) {
        return plan.side() && plan.startY() < room.cy();
    }

    private void enterTunnelFromDeepSideShaft(ColumnPlan col, int feetY) {
        releaseAllAutoKeys();
        boolean water = col.water() || mc.player.isTouchingWater() || columnHasWater(col.x(), col.z(), col.startY(), col.holeTopY());
        autoExitShaft = new ExitShaft(col.x(), col.z(), feetY, col.exitY(), water, col.skyBlocked(), null);
        autoExitShaftForeign = false;
        autoTunnelTrail.clear();
        autoTunnelStep = mc.player.getBlockPos();
        enterState(AutoState.TUNNEL, "deep side shaft bottom");
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
            autoLog("SIDE_DIG", autoSite.center, String.format("side dig slow %d,%d,%d block=%s",
                target.getX(), target.getY(), target.getZ(), Registries.BLOCK.getId(mc.world.getBlockState(target).getBlock())));
            abort("dig block timeout");
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

            if (!autoSite.roomContainers.contains(pos.down())) {
                HitResult losHit = mc.world.raycast(new RaycastContext(
                    eye, Vec3d.ofCenter(pos), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                if (losHit instanceof BlockHitResult && losHit.getType() == HitResult.Type.BLOCK
                    && !((BlockHitResult) losHit).getBlockPos().equals(pos)) continue;
            }

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
            playAutoSound("shulker_found", soundShulker);
            autoRoomFoundShulkers += fullShulkers;
            autoRoomFoundItems += piles;
            if (isShipwreck()) {
                XaeroWaypointManager.addWaypoint("SpawnerDetector", target, "Shipwreck shulkers " + fullShulkers, "SW", MapUtil.WpColor.Purple, false);
                info("Shipwreck shulkers found: %d at %d %d %d", fullShulkers, target.getX(), target.getY(), target.getZ());
            } else {
            XaeroWaypointManager.addWaypoint("SpawnerDetector", target, "Dungeon shulkers " + fullShulkers, "DS", MapUtil.WpColor.Purple, false);
            info("Dungeon shulkers found: %d at %d %d %d", fullShulkers, target.getX(), target.getY(), target.getZ());
            }
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

        if (best != null) return best;

        Vec3d containerCenter = Vec3d.ofCenter(container);
        for (int x = room.minX(); x <= room.maxX(); x++) {
            for (int z = room.minZ(); z <= room.maxZ(); z++) {
                BlockPos feet = new BlockPos(x, room.cy(), z);
                if (exclude.contains(feet)) continue;
                if (!isOpenState(mc.world.getBlockState(feet), feet)) continue;
                BlockPos head = feet.up();
                if (!isOpenState(mc.world.getBlockState(head), head)) continue;
                BlockPos floor = feet.down();
                BlockState floorState = mc.world.getBlockState(floor);
                if (isOpenState(floorState, floor) || !floorState.getFluidState().isEmpty() || isLava(floorState)) continue;
                if (new Vec3d(x + 0.5, room.cy() + 1.62, z + 0.5).squaredDistanceTo(containerCenter) > 4.2 * 4.2) continue;
                double distSq = feet.getSquaredDistance(mc.player.getBlockPos());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = feet;
                }
            }
        }

        return best;
    }

    private void approachBlock(BlockPos target) {
        if (isShipwreck()) {
            recenterDig(target.getX(), target.getZ(), 0.3);
            Utils.setPressed(mc.options.jumpKey, mc.player.isTouchingWater() && mc.player.getY() < target.getY());
            Utils.setPressed(mc.options.sneakKey, false);
            return;
        }
        if (!target.equals(autoApproachTarget)) {
            autoApproachTarget = target;
            autoApproachCell = null;
            autoApproachTicks = 0;
            autoApproachBreakPos = null;
            autoApproachBreakLabel = null;
            autoApproachBreakCount = 0;
        }

        Set<BlockPos> bad = autoApproachBadCells.computeIfAbsent(target, t -> new java.util.HashSet<>());
        autoApproachTicks++;

        double eyeDist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(target));

        if (autoApproachTicks == 1) {
            autoApproachBaselineDist = eyeDist;
        } else if (autoApproachBreakPos == null && autoApproachTicks % 100 == 0) {
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

        if (autoApproachBreakPos != null) {
            BlockState breakState = mc.world.getBlockState(autoApproachBreakPos);
            if (isOpenState(breakState, autoApproachBreakPos)) {
                autoLog("ROOM", target, String.format("approach blocked, broken %d,%d,%d block=%s",
                    autoApproachBreakPos.getX(), autoApproachBreakPos.getY(), autoApproachBreakPos.getZ(), autoApproachBreakLabel));
                autoApproachBreakCount++;
                autoRoomDebrisBreaks.merge(autoApproachBreakPos, 1, Integer::sum);
                autoApproachBreakPos = null;
                autoApproachBaselineDist = eyeDist;
                autoApproachTicks = 1;
            } else if (autoApproachTicks > 200) {
                autoLog("ROOM", target, String.format("approach blocked, %d,%d,%d not broken in 10s block=%s, leaving it",
                    autoApproachBreakPos.getX(), autoApproachBreakPos.getY(), autoApproachBreakPos.getZ(), autoApproachBreakLabel));
                autoRoomDebrisBreaks.put(autoApproachBreakPos, 16);
                autoApproachBreakPos = null;
                autoApproachBaselineDist = eyeDist;
                autoApproachTicks = 1;
            } else {
                Utils.setPressed(mc.options.forwardKey, false);
                Utils.setPressed(mc.options.jumpKey, false);
                Utils.setPressed(mc.options.sneakKey, false);
                autoMineTick("ROOM", autoApproachBreakPos, false);
                return;
            }
        } else {
            HitResult obstacleHit = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(), Vec3d.ofCenter(aimAt), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            BlockPos obstacle = null;
            if (obstacleHit instanceof BlockHitResult && obstacleHit.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = ((BlockHitResult) obstacleHit).getBlockPos();
                if (canBreakApproachObstacle(hitPos, target, aimAt)) {
                    obstacle = hitPos;
                }
            }
            if (obstacle == null && mc.player.horizontalCollision) {
                obstacle = findApproachFallbackObstacle(target, aimAt);
            }
            if (obstacle != null) {
                BlockState obstacleState = mc.world.getBlockState(obstacle);
                autoApproachBreakPos = obstacle;
                autoApproachTicks = 1;
                autoApproachBreakLabel = Registries.BLOCK.getId(obstacleState.getBlock()).getPath();
                autoLog("ROOM", target, String.format("approach blocked at %d,%d,%d block=%s, breaking",
                    obstacle.getX(), obstacle.getY(), obstacle.getZ(), autoApproachBreakLabel));
                Utils.setPressed(mc.options.forwardKey, false);
                Utils.setPressed(mc.options.jumpKey, false);
                autoMineTick("ROOM", autoApproachBreakPos, false);
                return;
            }
        }

        BlockPos approachFeet = mc.player.getBlockPos();
        double aimDxz = Math.hypot((aimAt.getX() + 0.5) - mc.player.getX(), (aimAt.getZ() + 0.5) - mc.player.getZ());
        BlockPos approachCeiling = approachFeet.up(2);
        if (!mc.player.isTouchingWater() && aimAt.getY() > approachFeet.getY() && aimDxz <= 0.7
            && isOpenState(mc.world.getBlockState(approachCeiling), approachCeiling) && mc.world.getFluidState(approachCeiling).isEmpty()) {
            Utils.setPressed(mc.options.forwardKey, false);
            Utils.setPressed(mc.options.sprintKey, false);
            Utils.setPressed(mc.options.sneakKey, false);
            Utils.setPressed(mc.options.jumpKey, true);
            mc.player.setPitch(90.0f);
            if (!mc.player.isOnGround() && mc.player.getVelocity().y <= 0.05) {
                BlockPos placePos = approachFeet.down();
                BlockState placeState = mc.world.getBlockState(placePos);
                if (isOpenState(placeState, placePos) && placeState.getFluidState().isEmpty()
                    && selfAscendCanPlaceStep(placePos) && ensureObsidianInHotbar() >= 0) {
                    tickSelfAscendPlaceStep(placePos);
                }
            }
            return;
        }

        Vec3d cellCenter = Vec3d.ofCenter(aimAt);
        double dx = cellCenter.x - mc.player.getX();
        double dz = cellCenter.z - mc.player.getZ();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        Utils.setPressed(mc.options.forwardKey, true);

        boolean inWater = mc.player.isTouchingWater();
        if (inWater && mc.player.getBlockPos().getY() > autoSite.room.cy() && !mc.player.isOnGround()) {
            Utils.setPressed(mc.options.sneakKey, true);
            Utils.setPressed(mc.options.jumpKey, false);
        } else {
            Utils.setPressed(mc.options.sneakKey, false);
            Utils.setPressed(mc.options.jumpKey, mc.player.horizontalCollision
                || (inWater && mc.player.getBlockPos().getY() < autoSite.room.cy()));
        }

        if (autoApproachTicks % 40 == 0) {
            autoLog("ROOM", target, String.format(
                "approach pos=%.1f,%.1f,%.1f cell=%d,%d,%d dist=%.2f water=%b submerged=%b ground=%b hcol=%b",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                aimAt.getX(), aimAt.getY(), aimAt.getZ(), eyeDist, inWater,
                mc.player.isSubmergedInWater(), mc.player.isOnGround(), mc.player.horizontalCollision));
        }
    }

    private boolean canBreakApproachObstacle(BlockPos obstacle, BlockPos target, BlockPos aimAt) {
        if (obstacle.equals(target) || obstacle.equals(aimAt)) return false;
        BlockState obstacleState = mc.world.getBlockState(obstacle);
        boolean falling = obstacleState.getBlock() instanceof net.minecraft.block.FallingBlock;
        return obstacleState.getBlock() != Blocks.SPAWNER
            && !isContainerPresent(obstacle)
            && !isLava(obstacleState)
            && !isLavaNearShaft(obstacle, obstacle.up())
            && obstacle.getY() >= mc.player.getBlockPos().getY()
            && mc.player.getEyePos().distanceTo(Vec3d.ofCenter(obstacle)) <= 4.2
            && (!falling || autoApproachBreakCount < 16)
            && autoRoomDebrisBreaks.getOrDefault(obstacle, 0) < (falling ? 16 : 2);
    }

    private BlockPos approachFallbackCandidate(BlockPos feet, int stepX, int stepZ, BlockPos target, BlockPos aimAt) {
        BlockPos cell = feet.add(stepX, 0, stepZ);
        if (!isOpenState(mc.world.getBlockState(cell), cell) && canBreakApproachObstacle(cell, target, aimAt)) {
            return cell;
        }
        BlockPos headCell = cell.up();
        if (!isOpenState(mc.world.getBlockState(headCell), headCell) && canBreakApproachObstacle(headCell, target, aimAt)) {
            return headCell;
        }
        return null;
    }

    private BlockPos findApproachFallbackObstacle(BlockPos target, BlockPos aimAt) {
        BlockPos feet = mc.player.getBlockPos();
        int dx = aimAt.getX() - feet.getX();
        int dz = aimAt.getZ() - feet.getZ();
        if (dx == 0 && dz == 0) return null;
        boolean xFirst = Math.abs(dx) >= Math.abs(dz);
        BlockPos candidate = xFirst
            ? approachFallbackCandidate(feet, Integer.signum(dx), 0, target, aimAt)
            : approachFallbackCandidate(feet, 0, Integer.signum(dz), target, aimAt);
        if (candidate == null && dx != 0 && dz != 0) {
            candidate = xFirst
                ? approachFallbackCandidate(feet, 0, Integer.signum(dz), target, aimAt)
                : approachFallbackCandidate(feet, Integer.signum(dx), 0, target, aimAt);
        }
        return candidate;
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

        if (!autoRoomSpawnerRepositioning && (moved > 1.0 || distSq > 4.4 * 4.4)) {
            autoRoomSpawnerRepositioning = true;
            Utils.setPressed(mc.options.attackKey, false);
            if (autoRoomSpawnerAnchor != null) mc.interactionManager.cancelBlockBreaking();
            autoLog("ROOM", autoRoomSpawnerPos, String.format("spawner out of place moved=%.2f dist=%.2f, repositioning", moved, Math.sqrt(distSq)));
        }

        if (autoRoomSpawnerRepositioning) {
            boolean readyToResume = distSq <= 4.2 * 4.2 && (mc.player.isOnGround() || mc.player.isTouchingWater());
            if (readyToResume) {
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

        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater() && !isOpenState(mc.world.getBlockState(mc.player.getBlockPos().down()), mc.player.getBlockPos().down()));
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
            if (isShipwreck()) {
                finishRoomAndAscend();
                return;
            }
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

        if (tickStepOffContainer(target)) return;

        if (autoRoomSubState == 5) {
            tickRoomContainerBreak(target);
            return;
        }

        if (!isContainerPresent(target)) {
            autoRoomIndex++;
            autoRoomContainerFails = 0;
            autoRoomSubState = 0;
            autoRoomPillarTarget = null;
            autoRoomPillarPhase = 0;
            return;
        }

        switch (autoRoomSubState) {
            case 0 -> {
                autoRoomSubStateStartMs = System.currentTimeMillis();
                autoRoomOpenAttempts = 0;
                double distSq = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target));
                autoRoomSubState = (isShipwreck() ? !mc.player.canInteractWithBlockAt(target, 0.0) : distSq > 4.2 * 4.2) ? 1 : 2;
            }
            case 1 -> {
                double distSq = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target));
                if (isShipwreck() ? mc.player.canInteractWithBlockAt(target, 0.0) : distSq <= 4.2 * 4.2) {
                    releaseAllAutoKeys();
                    autoRoomPillarTarget = null;
                    autoRoomPillarPhase = 0;
                    autoRoomSubState = 2;
                } else if (tickRoomContainerPillar(target)) {
                    return;
                } else if (isShipwreck()) {
                    releaseAllAutoKeys();
                    enterState(AutoState.WALK, "shipwreck chest out of reach, back to the column");
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
                } else if (System.currentTimeMillis() - autoRoomSubStateStartMs > 1000L) {
                    if (autoRoomOpenAttempts < 20) {
                        autoRoomSubState = 2;
                    } else {
                        autoLog("ROOM", target, "container did not open, left unbroken");
                        autoRoomNotOpened = true;
                        autoRoomIndex++;
                        autoRoomContainerFails = 0;
                        autoRoomSubState = 0;
                        autoRoomPillarTarget = null;
                        autoRoomPillarPhase = 0;
                        if (isShipwreck()) autoShipwreckChestOutcome = "not opened";
                    }
                }
            }
            case 4 -> {
                if (autoRoomWaitTicks > 0) { autoRoomWaitTicks--; return; }
                if (mc.player.currentScreenHandler.getRevision() == 0) {
                    if (System.currentTimeMillis() - autoRoomSubStateStartMs < 8000L) return;
                    mc.player.closeHandledScreen();
                    autoLog("ROOM", target, "container contents never arrived, left unbroken");
                    autoRoomNotOpened = true;
                    autoRoomIndex++;
                    autoRoomContainerFails = 0;
                    autoRoomSubState = 0;
                    autoRoomPillarTarget = null;
                    autoRoomPillarPhase = 0;
                    if (isShipwreck()) autoShipwreckChestOutcome = "not opened";
                    return;
                }
                boolean kept = readContainer(target);
                if (kept) {
                    autoRoomIndex++;
                    autoRoomContainerFails = 0;
                    autoRoomSubState = 0;
                    autoRoomPillarTarget = null;
                    autoRoomPillarPhase = 0;
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
        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater() && !isOpenState(mc.world.getBlockState(mc.player.getBlockPos().down()), mc.player.getBlockPos().down()));
        autoRoomBreakTicks++;

        boolean present = isContainerPresent(target);
        if (!present) {
            autoRoomContainerGoneTicks++;
            if (autoRoomContainerGoneTicks >= 20) {
                Utils.setPressed(mc.options.attackKey, false);
                statChestsBroken++;
                autoLog("ROOM", target, "broken in " + autoRoomBreakTicks + " ticks");
                autoRoomIndex++;
                autoRoomContainerFails = 0;
                autoRoomSubState = 0;
                autoRoomPillarTarget = null;
                autoRoomPillarPhase = 0;
                return;
            }
        } else {
            autoRoomContainerGoneTicks = 0;

            double distSq = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target));
            double moved = autoRoomBreakAnchor == null ? 0.0
                : Math.hypot(mc.player.getX() - autoRoomBreakAnchor.x, mc.player.getZ() - autoRoomBreakAnchor.z);

            if (!autoRoomBreakRepositioning && !isShipwreck() && (moved > 1.0 || distSq > 4.4 * 4.4)) {
                autoRoomBreakRepositioning = true;
                Utils.setPressed(mc.options.attackKey, false);
                mc.interactionManager.cancelBlockBreaking();
                autoLog("ROOM", target, String.format("pushed away moved=%.2f dist=%.2f, repositioning", moved, Math.sqrt(distSq)));
            }

            if (System.currentTimeMillis() - autoRoomBreakStartMs > 20_000L) {
                Utils.setPressed(mc.options.attackKey, false);
                autoRoomBreakRepositioning = false;
                autoRoomContainerFails++;
                if (autoRoomContainerFails >= 2) {
                    autoRoomIndex++;
                    autoRoomContainerFails = 0;
                    autoRoomPillarTarget = null;
                    autoRoomPillarPhase = 0;
                    if (isShipwreck()) autoShipwreckChestOutcome = "break attempts";
                    autoLog("ROOM", target, "container skipped after 2 failed breaks");
                } else {
                    autoLog("ROOM", target, "break failed, retry");
                }
                autoRoomSubState = 0;
                return;
            }

            if (autoRoomBreakRepositioning) {
                boolean readyToResume = distSq <= 4.2 * 4.2 && (mc.player.isOnGround() || mc.player.isTouchingWater());
                if (readyToResume) {
                    releaseAllAutoKeys();
                    autoRoomBreakRepositioning = false;
                    autoRoomBreakAnchor = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    autoRoomBreakStartMs = System.currentTimeMillis();
                    autoLog("ROOM", target, "repositioned, breaking again");
                } else if (tickRoomContainerPillar(target)) {
                    return;
                } else {
                    approachBlock(target);
                    return;
                }
            }
        }

        autoMineTick("ROOM", target, false);
    }

    private boolean containerOutOfReachHigh(BlockPos target) {
        double eyeY = mc.player.getEyePos().y;
        double centerY = target.getY() + 0.5;
        if (centerY - eyeY <= 3.9) return false;
        if (!mc.player.isOnGround()) return false;
        double dx = (target.getX() + 0.5) - mc.player.getX();
        double dz = (target.getZ() + 0.5) - mc.player.getZ();
        return Math.hypot(dx, dz) <= 1.5;
    }

    private boolean tickRoomContainerPillar(BlockPos target) {
        boolean inProgress = target.equals(autoRoomPillarTarget);
        if (!inProgress) {
            if (!containerOutOfReachHigh(target)) return false;
            releaseAllAutoKeys();
            autoRoomPillarTarget = target;
            autoRoomPillarPhase = 0;
        }

        BlockPos feet = mc.player.getBlockPos();

        mc.player.setPitch(90.0f);
        Utils.setPressed(mc.options.forwardKey, false);
        Utils.setPressed(mc.options.sprintKey, false);

        if (autoRoomPillarPhase == 0) {
            if (hasBlockAbove(feet.getX(), feet.getY() + 1, feet.getZ(), 2)) {
                autoRoomPillarTarget = null;
                return false;
            }

            int slot = ensureObsidianInHotbar();
            if (slot < 0) {
                if (isShipwreck()) autoShipwreckChestOutcome = "no pillar block";
                autoLog("ROOM", target, "container out of reach, no block to pillar");
                autoRoomPillarTarget = null;
                autoRoomPillarPhase = 0;
                autoRoomIndex++;
                autoRoomContainerFails = 0;
                autoRoomSubState = 0;
                return true;
            }
            InvUtils.swap(slot, false);

            autoRoomPillarFeetX = feet.getX();
            autoRoomPillarFeetY = feet.getY();
            autoRoomPillarFeetZ = feet.getZ();
            autoRoomPillarPhase = 1;
        }

        if (autoRoomPillarPhase == 1) {
            if (mc.player.getY() < autoRoomPillarFeetY + 1.0) {
                Utils.setPressed(mc.options.jumpKey, true);
                return true;
            }
            Utils.setPressed(mc.options.jumpKey, false);
            BlockPos supportPos = new BlockPos(autoRoomPillarFeetX, autoRoomPillarFeetY - 1, autoRoomPillarFeetZ);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(
                new Vec3d(autoRoomPillarFeetX + 0.5, autoRoomPillarFeetY - 1 + 1.0, autoRoomPillarFeetZ + 0.5),
                Direction.UP, supportPos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            autoLog("ROOM", target, "container pillar placed y=" + autoRoomPillarFeetY);
            autoRoomPillarPhase = 2;
        }

        if (!mc.player.isOnGround()) return true;

        autoRoomPillarPhase = 0;
        if (containerOutOfReachHigh(target)) return true;
        autoRoomPillarTarget = null;
        return false;
    }

    private boolean standingOnContainer(BlockPos target) {
        if (!mc.player.isOnGround()) return false;
        double y = mc.player.getY();
        if (y < target.getY() + 0.5 || y > target.getY() + 1.2) return false;
        double dx = mc.player.getX() - (target.getX() + 0.5);
        double dz = mc.player.getZ() - (target.getZ() + 0.5);
        return Math.abs(dx) < 0.8 && Math.abs(dz) < 0.8;
    }

    private boolean isValidStepOffCell(BlockPos c, BlockPos target) {
        BlockPos below = new BlockPos(c.getX(), c.getY() - 1, c.getZ());
        BlockState belowState = mc.world.getBlockState(below);
        if (isOpenState(belowState, below) || !belowState.getFluidState().isEmpty()) return false;
        if (isContainerPresent(below) || belowState.getBlock() == Blocks.SPAWNER) return false;
        if (isLava(belowState) || isLavaNearShaft(below, c)) return false;
        if (!isOpenState(mc.world.getBlockState(c), c)) return false;
        BlockPos head = c.up();
        if (!isOpenState(mc.world.getBlockState(head), head)) return false;
        Vec3d eyes = new Vec3d(c.getX() + 0.5, c.getY() + 1.62, c.getZ() + 0.5);
        return eyes.squaredDistanceTo(Vec3d.ofCenter(target)) <= 4.2 * 4.2;
    }

    private BlockPos findStepOffCell(BlockPos target, Set<BlockPos> excluded) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (dx * dx + dz * dz > 9) continue;
                BlockPos c = new BlockPos(target.getX() + dx, target.getY(), target.getZ() + dz);
                if (excluded.contains(c)) continue;
                if (!isValidStepOffCell(c, target)) continue;
                double distSq = c.getSquaredDistance(mc.player.getBlockPos());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = c;
                }
            }
        }
        return best;
    }

    private boolean tickStepOffContainer(BlockPos target) {
        if (mc.player.isTouchingWater() || isShipwreck()) {
            if (autoRoomStepOffTarget != null || autoRoomStepOffCell != null) releaseAllAutoKeys();
            autoRoomStepOffTarget = null;
            autoRoomStepOffCell = null;
            return false;
        }

        boolean walking = target.equals(autoRoomStepOffTarget);
        if (!walking && !standingOnContainer(target)) return false;

        if (!walking) {
            Utils.setPressed(mc.options.attackKey, false);
            mc.interactionManager.cancelBlockBreaking();
            autoRoomStepOffTarget = target;
            autoRoomStepOffTried = new java.util.HashSet<>();
            autoRoomStepOffCell = findStepOffCell(target, autoRoomStepOffTried);
            autoRoomStepOffTicks = 0;
            autoRoomStepOffBaselineDist = Double.MAX_VALUE;
            if (autoRoomStepOffCell != null) {
                autoLog("ROOM", target, String.format("stepping off container to %d,%d,%d",
                    autoRoomStepOffCell.getX(), autoRoomStepOffCell.getY(), autoRoomStepOffCell.getZ()));
            }
        }

        Utils.setPressed(mc.options.attackKey, false);

        if (autoRoomStepOffCell == null) {
            if (!target.equals(autoRoomStepOffNoCellLoggedTarget)) {
                autoLog("ROOM", target, "standing on container, no cell to step off");
                autoRoomStepOffNoCellLoggedTarget = target;
            }
            autoRoomStepOffTarget = null;
            return false;
        }

        BlockPos feet = mc.player.getBlockPos();
        if (feet.getX() == autoRoomStepOffCell.getX() && feet.getZ() == autoRoomStepOffCell.getZ() && mc.player.isOnGround()) {
            releaseAllAutoKeys();
            autoRoomStepOffTarget = null;
            autoRoomStepOffCell = null;
            return false;
        }

        double dist = Math.hypot((autoRoomStepOffCell.getX() + 0.5) - mc.player.getX(),
            (autoRoomStepOffCell.getZ() + 0.5) - mc.player.getZ());
        autoRoomStepOffTicks++;
        if (autoRoomStepOffTicks == 1) {
            autoRoomStepOffBaselineDist = dist;
        } else if (autoRoomStepOffTicks % 40 == 0) {
            if (autoRoomStepOffBaselineDist - dist < 0.3) {
                autoRoomStepOffTried.add(autoRoomStepOffCell);
                autoRoomStepOffCell = findStepOffCell(target, autoRoomStepOffTried);
                autoRoomStepOffTicks = 0;
                if (autoRoomStepOffCell == null) {
                    if (!target.equals(autoRoomStepOffNoCellLoggedTarget)) {
                        autoLog("ROOM", target, "standing on container, no cell to step off");
                        autoRoomStepOffNoCellLoggedTarget = target;
                    }
                    autoRoomStepOffTarget = null;
                    return false;
                }
                autoLog("ROOM", target, String.format("stepping off container to %d,%d,%d",
                    autoRoomStepOffCell.getX(), autoRoomStepOffCell.getY(), autoRoomStepOffCell.getZ()));
            } else {
                autoRoomStepOffBaselineDist = dist;
            }
        }

        recenterDig(autoRoomStepOffCell.getX(), autoRoomStepOffCell.getZ(), 0.2);
        Utils.setPressed(mc.options.jumpKey, mc.player.horizontalCollision);
        return true;
    }

    private void finishRoomAndAscend() {
        if (isShipwreck()) {
            finishShipwreckChest(autoShipwreckChestOutcome != null ? autoShipwreckChestOutcome
                : autoRoomFoundShulkers > 0 ? "shulkers" : "processed", System.currentTimeMillis());
            return;
        }
        if (!autoRoomNotOpened) exitLootedKeys.add(BlockPos.asLong(autoSite.center.getX(), autoSite.center.getY(), autoSite.center.getZ()));
        releaseAllAutoKeys();

        if (autoRoomFoundShulkers > 0) {
            autoLog("ROOM", autoSite.center, "found=" + autoRoomFoundShulkers + " items=" + autoRoomFoundItems);
        }

        if (autoWatchdogAbandonedSite == null && tryChainVertical()) return;
        if (autoWatchdogAbandonedSite == null && autoChainUnderground.get() && tryChainUnderground(autoSite)) return;

        autoAscendFromAbort = autoWatchdogAbandonedSite != null;
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
        if (!autoRunAbandoned) statRoomsFarmed++;
        autoLog("CHAIN", from, String.format("vertical next=%d,%d,%d",
            lower.center.getX(), lower.center.getY(), lower.center.getZ()));

        autoSite = lower;
        autoRunAbandoned = false;
        autoRoomFoundShulkers = 0;
        autoRoomNotOpened = false;
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
        boolean finishedFlooded = isRoomFlooded(finishedSite.room);

        Site chainSite = null;
        double bestDistSq = Double.MAX_VALUE;
        String nearestRejectReason = null;
        double nearestRejectDistSq = Double.MAX_VALUE;

        for (Site site : sites.values()) {
            if (site.center.equals(finishedSite.center)) continue;

            double dx = site.center.getX() + 0.5 - mc.player.getX();
            double dy = site.center.getY() + 0.5 - mc.player.getY();
            double dz = site.center.getZ() + 0.5 - mc.player.getZ();
            double distSq = dx * dx + dz * dz;

            if (finishedFlooded && distSq + dy * dy > 32.0 * 32.0) continue;

            if (!qualifiesForAuto(site)) {
                if (!site.roomContainers.isEmpty() && distSq <= 32.0 * 32.0 && distSq < nearestRejectDistSq) {
                    nearestRejectDistSq = distSq;
                    nearestRejectReason = "score too low";
                }
                continue;
            }

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
            String reason = finishedFlooded ? " reason=flooded room, nothing within 32"
                : (nearestRejectReason != null ? " reason=" + nearestRejectReason : "");
            if (finishedFlooded && nearestRejectReason != null) {
                reason += ", nearest=" + nearestRejectReason;
            }
            autoLog("CHAIN", finishedSite.center, "no underground chain target"
                + reason);
            return false;
        }

        BlockPos from = finishedSite.center;
        double chainDist = Math.sqrt(bestDistSq);
        markVisited(from, autoRunOutcome());
        if (!autoRunAbandoned) statRoomsFarmed++;
        autoLog("CHAIN", from, String.format("underground next=%d,%d,%d dist=%.1f",
            chainSite.center.getX(), chainSite.center.getY(), chainSite.center.getZ(), chainDist));

        autoTunnelStep = new BlockPos(mc.player.getBlockX(), finishedSite.room.cy(), mc.player.getBlockZ());
        autoSite = chainSite;
        autoRunAbandoned = false;
        autoRoomFoundShulkers = 0;
        autoRoomNotOpened = false;
        autoRoomFoundItems = 0;

        enterState(AutoState.TUNNEL, String.format("chained underground from %d,%d,%d dist=%.1f",
            from.getX(), from.getY(), from.getZ(), chainDist));
        return true;
    }

    private boolean isTunnelOpen(BlockPos pos) {
        BlockState block = mc.world.getBlockState(pos);
        return !isLava(block) && block.getCollisionShape(mc.world, pos).isEmpty();
    }

    private BlockPos findTunnelGoal(Room room) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int x = room.cx() - room.k(); x <= room.cx() + room.k(); x++) {
            for (int z = room.cz() - room.p(); z <= room.cz() + room.p(); z++) {
                BlockPos feet = new BlockPos(x, room.cy(), z);
                if (!isTunnelOpen(feet) || !isTunnelOpen(feet.up()) || isTunnelProtected(feet)) continue;
                BlockState floor = mc.world.getBlockState(feet.down());
                if (floor.getCollisionShape(mc.world, feet.down()).isEmpty() || isLava(floor)) continue;
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
        IBaritone bt = primaryBaritone();
        if (stateTicks == 1) {
            autoBaritoneRetries = 0;
            autoTunnelBaritone = false;
            autoTunnelDirectRetried = false;
            autoTunnelRescuePlacements = 0;
            autoTunnelRescuePlacePos = null;
            resetShaftDryRecovery();
            autoPaused = false;
            autoPauseReason = null;
            autoPauseStartMs = 0;
            autoPausedTicks = 0;
            resetBreach();
            releaseAllAutoKeys();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            BaritoneAPI.getSettings().allowPlace.value = false;
            autoTunnelGoal = findTunnelGoal(autoSite.room);
            if (autoTunnelGoal == null) {
                autoLog("TUNNEL", autoSite.center, "no floor cell, aiming at the room center");
                autoTunnelGoal = new BlockPos(autoSite.room.cx(), autoSite.room.cy(), autoSite.room.cz());
            }
            autoTunnelBestDist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(autoTunnelGoal));
            autoTunnelBestElapsed = 0;
            autoLog("TUNNEL", autoSite.center, "direct goal=" + autoTunnelGoal.toShortString());
        }

        if (mc.player.isInLava()) { abort("lava"); return; }
        if (handleAutoPause(false)) return;
        if (otherPlayerPresent()) { abort("player appeared"); return; }
        AutoEatSync eat = autoEatSyncModule();
        if (eat != null && eat.isEating()) {
            releaseAllAutoKeys();
            autoTunnelBestElapsed = stateTicks - autoPausedTicks;
            return;
        }
        recordTunnelTrail();

        BlockPos feet = mc.player.getBlockPos();
        int elapsed = stateTicks - autoPausedTicks;
        boolean atFloor = Math.abs(mc.player.getY() - autoSite.room.cy()) <= 0.2;
        if ((mc.player.isOnGround() || mc.player.isTouchingWater()) && atFloor
            && insideInterior(autoSite.room, feet.getX(), feet.getZ())
            && isTunnelOpen(feet) && isTunnelOpen(feet.up())) {
            releaseAllAutoKeys();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            autoExitShaftForeign = autoExitShaft != null;
            enterState(AutoState.ROOM, "");
            return;
        }

        double dist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(autoTunnelGoal));
        if (dist < autoTunnelBestDist - 0.2) {
            autoTunnelBestDist = dist;
            autoTunnelBestElapsed = elapsed;
        }
        if (!autoTunnelBaritone) {
            tickTunnelDirect();
            logNavTelemetry("TUNNEL", autoTunnelGoal, 0);
            return;
        }

        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
        boolean calculating = bt != null && !bt.getPathingBehavior().getInProgress().isEmpty();
        boolean failed = !pathing && !calculating && (bt == null || !bt.getCustomGoalProcess().isActive());
        if (failed && autoBaritoneRetries < 3) {
            autoBaritoneRetries++;
            releaseAllAutoKeys();
            if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(autoTunnelGoal));
        } else if ((failed && elapsed - autoTunnelBestElapsed >= 10 * 20)
            || elapsed - autoTunnelBestElapsed > 30 * 20) {
            if (!autoTunnelDirectRetried && Math.abs(feet.getX() - autoTunnelGoal.getX()) <= 2
                && Math.abs(feet.getY() - autoTunnelGoal.getY()) <= 2 && Math.abs(feet.getZ() - autoTunnelGoal.getZ()) <= 2) {
                autoTunnelDirectRetried = true;
                releaseAllAutoKeys();
                if (bt != null) bt.getPathingBehavior().cancelEverything();
                autoTunnelBaritone = false;
                autoTunnelStep = feet;
                autoTunnelBestElapsed = elapsed;
                autoLog("TUNNEL", autoSite.center, "baritone failed, direct rescue from " + feet.toShortString());
                return;
            }
            abandonTunnel();
            return;
        }
        logNavTelemetry("TUNNEL", autoTunnelGoal, autoBaritoneRetries);
    }

    private void tickTunnelDirect() {
        releaseAllAutoKeys();
        mc.player.setSprinting(false);
        int elapsed = stateTicks - autoPausedTicks;
        if (autoBreachPos != null && isTunnelOpen(autoBreachPos)) {
            autoLog("TUNNEL", autoSite.center, "direct broken=" + autoBreachPos.toShortString() + " block=" + autoBreachLabel);
            autoBreachPos = null;
            autoTunnelBestElapsed = elapsed;
            mc.interactionManager.cancelBlockBreaking();
        }
        int stallLimit = 10 * 20;
        if (autoBreachPos != null && mc.player.isSubmergedInWater() && mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED) < 0.99) stallLimit *= 5;
        if (elapsed - autoTunnelBestElapsed > stallLimit) {
            startTunnelBaritone(autoBreachPos != null ? autoBreachPos : autoTunnelStep, "direct passage stalled");
            return;
        }

        BlockPos feet = mc.player.getBlockPos();
        double stepDist = Math.hypot(autoTunnelStep.getX() + 0.5 - mc.player.getX(), autoTunnelStep.getZ() + 0.5 - mc.player.getZ());
        if (stepDist <= 0.2 && Math.abs(mc.player.getY() - autoTunnelStep.getY()) <= 0.2
            && (mc.player.isOnGround() || mc.player.isTouchingWater())) {
            int dx = autoTunnelGoal.getX() - autoTunnelStep.getX();
            int dz = autoTunnelGoal.getZ() - autoTunnelStep.getZ();
            int dy = Integer.signum(autoTunnelGoal.getY() - autoTunnelStep.getY());
            BlockPos xFirst = autoTunnelStep.add(Integer.signum(dx), dy, 0);
            BlockPos next = dx != 0 && !(dz != 0 && isTunnelProtected(xFirst))
                ? xFirst
                : autoTunnelStep.add(0, dy, Integer.signum(dz));
            if (dy > 0 && Math.abs(dx) + Math.abs(dz) > autoTunnelGoal.getY() - autoTunnelStep.getY()
                && tunnelStepNeedsFloor(next) && !isTunnelProtected(next.down())) next = next.down();
            autoTunnelStep = next;
            autoTunnelBestElapsed = elapsed;
        }

        int top = Math.max(feet.getY(), autoTunnelStep.getY()) + 1;
        List<BlockPos> clearance = new ArrayList<>();
        if (autoTunnelStep.getY() > feet.getY()) clearance.add(feet.up(2));
        for (int y = top; y >= autoTunnelStep.getY(); y--) {
            clearance.add(new BlockPos(autoTunnelStep.getX(), y, autoTunnelStep.getZ()));
        }
        for (BlockPos pos : clearance) {
            BlockState block = mc.world.getBlockState(pos);
            if (!mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
                || block.getBlock() == Blocks.SPAWNER || isContainerPresent(pos) || isLavaNearShaft(pos, pos.up())
                || isLava(mc.world.getBlockState(pos.up()))) {
                startTunnelBaritone(pos, "protected or unsafe cell");
                return;
            }
            if (!isTunnelOpen(pos) && block.getHardness(mc.world, pos) < 0) {
                startTunnelBaritone(pos, "unbreakable cell");
                return;
            }
        }

        BlockPos floor = autoTunnelStep.down();
        BlockState floorState = mc.world.getBlockState(floor);
        if (isLava(floorState) || isLavaNearShaft(floor, autoTunnelStep)) {
            startTunnelBaritone(floor, "lava below passage");
            return;
        }
        boolean needsFloor = tunnelStepNeedsFloor(autoTunnelStep);
        boolean placed = floor.equals(autoTunnelRescuePlacePos);
        Direction side = needsFloor && autoTunnelDirectRetried ? selfAscendPlaceSide(floor) : null;
        if (needsFloor && (!autoTunnelDirectRetried || (!placed && autoTunnelRescuePlacements >= 2)
            || Math.abs(feet.getX() - autoTunnelGoal.getX()) > 2 || Math.abs(feet.getY() - autoTunnelGoal.getY()) > 2
            || Math.abs(feet.getZ() - autoTunnelGoal.getZ()) > 2
            || !floorState.isReplaceable() || side == null || (!placed && ensureObsidianInHotbar() < 0))) {
            startTunnelBaritone(floor, autoTunnelDirectRetried ? "direct rescue cannot place" : "step without floor");
            return;
        }
        for (BlockPos pos : clearance) {
            if (isTunnelOpen(pos)) continue;
            if (!pos.equals(autoBreachPos)) {
                autoBreachPos = pos;
                autoBreachLabel = Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).getPath();
                autoLog("TUNNEL", autoSite.center, "direct breaking=" + pos.toShortString() + " block=" + autoBreachLabel);
            }
            autoMineTick("TUNNEL", pos, false);
            return;
        }

        if (needsFloor) {
            int slot = ensureObsidianInHotbar();
            if (mc.player.getBoundingBox().intersects(new Box(floor))) {
                recenterDig(feet.getX(), feet.getZ(), 0.2);
                Utils.setPressed(mc.options.jumpKey, autoTunnelStep.getY() > mc.player.getY());
                return;
            }
            if (!placed) InvUtils.swap(slot, false);
            BlockPos support = floor.offset(side.getOpposite());
            Vec3d hitPos = Vec3d.ofCenter(support).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            Utils.setPressed(mc.options.sneakKey, true);
            if (alignedTowards(hitPos) && !placed && stateTicks % 5 == 0 && interactWhenAligned(hitPos, new BlockHitResult(hitPos, side, support, false))) {
                autoTunnelRescuePlacements++;
                autoTunnelRescuePlacePos = floor;
                mc.player.swingHand(Hand.MAIN_HAND);
                autoLog("TUNNEL", autoSite.center, "direct placed obsidian=" + floor.toShortString());
            }
            return;
        }

        recenterDig(autoTunnelStep.getX(), autoTunnelStep.getZ(), 0.2);
        mc.player.setPitch(0.0f);
        boolean rising = autoTunnelStep.getY() > mc.player.getY() + 0.2;
        Utils.setPressed(mc.options.jumpKey, rising);
        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater() && !mc.player.isOnGround()
            && mc.player.getY() > autoTunnelStep.getY() + 0.1);
        if (stateTicks % 40 == 0) autoLog("TUNNEL", autoSite.center,
            "direct step=" + autoTunnelStep.toShortString() + " water=" + mc.player.isTouchingWater() + " jump=" + rising);
    }

    private void abandonTunnel() {
        autoLog("TUNNEL", autoSite.center, "tunnel failed: no path around blocked direct passage");
        autoRunAbandoned = true;
        releaseAllAutoKeys();
        mc.interactionManager.cancelBlockBreaking();
        IBaritone bt = primaryBaritone();
        if (bt != null) bt.getPathingBehavior().cancelEverything();
        if (autoExitShaft != null && !autoTunnelTrail.isEmpty()) autoExitShaftForeign = true;
        enterState(AutoState.EXIT, "");
    }

    private void startTunnelBaritone(BlockPos blocked, String reason) {
        if (autoTunnelDirectRetried) {
            abandonTunnel();
            return;
        }
        releaseAllAutoKeys();
        mc.interactionManager.cancelBlockBreaking();
        autoBreachPos = null;
        autoTunnelBaritone = true;
        autoTunnelBestElapsed = stateTicks - autoPausedTicks;
        autoLog("TUNNEL", autoSite.center, "direct blocked=" + blocked.toShortString()
            + " block=" + Registries.BLOCK.getId(mc.world.getBlockState(blocked).getBlock()).getPath()
            + " reason=" + reason + ", baritone fallback");
        BaritoneAPI.getSettings().allowPlace.value = false;
        autoTunnelDisallowedBreaking = BaritoneAPI.getSettings().blocksToDisallowBreaking.value;
        List<Block> protectedBlocks = new ArrayList<>(autoTunnelDisallowedBreaking);
        protectedBlocks.addAll(List.of(Blocks.SPAWNER, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL));
        for (Block block : Registries.BLOCK) {
            if (block instanceof ShulkerBoxBlock) protectedBlocks.add(block);
        }
        BaritoneAPI.getSettings().blocksToDisallowBreaking.value = protectedBlocks;
        IBaritone bt = primaryBaritone();
        if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(autoTunnelGoal));
    }

    private void restoreTunnelSettings() {
        if (state == AutoState.TUNNEL || autoTunnelDisallowedBreaking != null) {
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
        }
        if (autoTunnelDisallowedBreaking != null) {
            BaritoneAPI.getSettings().blocksToDisallowBreaking.value = autoTunnelDisallowedBreaking;
            autoTunnelDisallowedBreaking = null;
        }
    }

    private void recordTunnelTrail() {
        BlockPos feet = mc.player.getBlockPos();
        if (autoTunnelTrail.isEmpty() || !autoTunnelTrail.get(autoTunnelTrail.size() - 1).equals(feet)) {
            autoTunnelTrail.add(feet);
            if (autoTunnelTrail.size() > 4000) autoTunnelTrail.remove(0);
        }
    }

    private boolean tunnelStepNeedsFloor(BlockPos step) {
        BlockPos floor = step.down();
        BlockState floorState = mc.world.getBlockState(floor);
        return floorState.getCollisionShape(mc.world, floor).isEmpty()
            && (floorState.getFluidState().isEmpty() || mc.world.getFluidState(step).isEmpty());
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
        double horizontalToGoal = Math.hypot(goal.getX() - feet.getX(), goal.getZ() - feet.getZ());
        if (goal.getY() > feet.getY() + 1 && horizontalToGoal <= 2.0) {
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
        int regenLimit = mc.world.getBlockState(pos).getBlock() instanceof net.minecraft.block.FallingBlock
            ? (autoSite.roomContainers.contains(pos.down()) ? Integer.MAX_VALUE : 16) : 2;
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
            if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= 4.2) autoRoomDebrisBreaks.merge(pos, 1, Integer::sum);
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
        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater() && !isOpenState(mc.world.getBlockState(mc.player.getBlockPos().down()), mc.player.getBlockPos().down()));

        if (autoMineTick("ROOM", pos, false)) {
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
        if (isShipwreck()) {
            tickShipwreckRoom();
            return;
        }
        stateTicks++;

        if (stateTicks == 1) {
            int feetY = mc.player.getBlockPos().getY();
            if (feetY < autoSite.room.cy() - 2) {
                enterState(AutoState.ROOM_CLIMB, "feet below room floor");
                return;
            }

            autoSite.roomContainers = computeRoomContainers(autoSite.room);
            autoRoomQueue = new ArrayList<>(autoSite.roomContainers);
            autoRoomQueue.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getBlockPos())));
            autoRoomFoundShulkers = 0;
            autoRoomNotOpened = false;
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
            autoRoomContainerFails = 0;
            autoRoomSubState = 0;
            autoRoomPillarTarget = null;
            autoRoomPillarPhase = 0;
            autoRoomStepOffTarget = null;
            autoRoomStepOffCell = null;
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

        if ((stateTicks - autoPausedTicks) > 180 * 20) {
            autoLog("ROOM", autoSite.center, "room timeout 180s, leaving");
            releaseAllAutoKeys();
            finishRoomAndAscend();
            return;
        }

        if ((mc.player.isOnGround() || mc.player.isSubmergedInWater()) && mc.player.getBlockPos().getY() < autoSite.room.cy() - 2) {
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
            autoRoomClimbPillarStallTicks = 0;
            autoRoomClimbWater = false;
            autoRoomClimbStartMs = System.currentTimeMillis();
            autoRoomClimbBaritoneCandidates = List.of();
            autoRoomClimbBaritoneIndex = 0;
            autoRoomClimbBaritoneTryStartMs = 0;
            autoRoomClimbSwimTarget = null;
            autoRoomClimbSwimIsFloorHole = false;
            autoRoomClimbSwimStartMs = 0;
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

    private BlockPos findRoomClimbLedge() {
        Room room = autoSite.room;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int x = room.cx() - room.k(); x <= room.cx() + room.k(); x++) {
            for (int z = room.cz() - room.p(); z <= room.cz() + room.p(); z++) {
                cursor.set(x, room.cy() - 1, z);
                BlockState floorState = mc.world.getBlockState(cursor);
                if (isOpenState(floorState, cursor) || !floorState.getFluidState().isEmpty()) continue;
                cursor.set(x, room.cy(), z);
                if (!isOpenState(mc.world.getBlockState(cursor), cursor)) continue;
                cursor.set(x, room.cy() + 1, z);
                if (!isOpenState(mc.world.getBlockState(cursor), cursor)) continue;
                double dx = (x + 0.5) - mc.player.getX(), dz = (z + 0.5) - mc.player.getZ();
                double distSq = dx * dx + dz * dz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = new BlockPos(x, room.cy(), z);
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

            if (autoRoomClimbSwimStartMs == 0) {
                BlockPos.Mutable underCursor = new BlockPos.Mutable();
                underCursor.set(feet.getX(), autoSite.room.minY(), feet.getZ());
                boolean underOpening = isOpenState(mc.world.getBlockState(underCursor), underCursor);
                if (underOpening) {
                    autoRoomClimbSwimTarget = findRoomClimbLedge();
                    autoRoomClimbSwimIsFloorHole = false;
                    if (autoRoomClimbSwimTarget == null) {
                        autoRoomClimbSwimTarget = findRoomFloorHole();
                        autoRoomClimbSwimIsFloorHole = true;
                    }
                } else {
                    autoRoomClimbSwimTarget = findRoomFloorHole();
                    autoRoomClimbSwimIsFloorHole = true;
                    if (autoRoomClimbSwimTarget == null) {
                        autoRoomClimbSwimTarget = findRoomClimbLedge();
                        autoRoomClimbSwimIsFloorHole = false;
                    }
                }
                autoRoomClimbSwimStartMs = System.currentTimeMillis();
                if (autoRoomClimbSwimTarget == null) {
                    abandonRoomClimbPillar("no ledge");
                    return;
                }
                autoLog("ROOM_CLIMB", autoSite.center, (autoRoomClimbSwimIsFloorHole ? "swimming to floor hole " : "swimming to ledge ")
                    + autoRoomClimbSwimTarget.getX() + "," + autoRoomClimbSwimTarget.getY() + "," + autoRoomClimbSwimTarget.getZ());
            }

            if (autoRoomClimbSwimIsFloorHole && feet.getX() == autoRoomClimbSwimTarget.getX()
                    && feet.getZ() == autoRoomClimbSwimTarget.getZ() && feet.getY() >= autoSite.room.minY()) {
                BlockPos ledge = findRoomClimbLedge();
                if (ledge == null) {
                    autoRoomClimbSwimIsFloorHole = false;
                } else {
                    autoRoomClimbSwimTarget = ledge;
                    autoRoomClimbSwimIsFloorHole = false;
                    autoLog("ROOM_CLIMB", autoSite.center, "swimming to ledge " + autoRoomClimbSwimTarget.getX()
                        + "," + autoRoomClimbSwimTarget.getY() + "," + autoRoomClimbSwimTarget.getZ());
                }
            }

            if ((mc.player.isOnGround() && !mc.player.isTouchingWater())
                || (feet.getY() >= autoSite.room.cy() && feet.getX() == autoRoomClimbSwimTarget.getX()
                    && feet.getZ() == autoRoomClimbSwimTarget.getZ())) {
                releaseAllAutoKeys();
                enterState(AutoState.ROOM, "climb complete (swim)");
                return;
            }

            if (System.currentTimeMillis() - autoRoomClimbSwimStartMs > 10_000L) {
                abandonRoomClimbPillar("swim timeout");
                return;
            }

            double sdx = (autoRoomClimbSwimTarget.getX() + 0.5) - mc.player.getX();
            double sdz = (autoRoomClimbSwimTarget.getZ() + 0.5) - mc.player.getZ();
            if (Math.hypot(sdx, sdz) > 0.3) {
                mc.player.setYaw((float) Math.toDegrees(Math.atan2(-sdx, sdz)));
            }
            mc.player.setPitch(-30.0f);
            Utils.setPressed(mc.options.sneakKey, false);
            Utils.setPressed(mc.options.sprintKey, false);
            Utils.setPressed(mc.options.forwardKey, true);
            Utils.setPressed(mc.options.jumpKey, true);
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
            autoRoomClimbPillarStallTicks = 0;
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

        if (autoRoomClimbPillarPhase == 1) {
            if (mc.player.getY() < autoRoomClimbFeetY + 1.0) {
                Utils.setPressed(mc.options.jumpKey, true);
                if (mc.player.isOnGround()) {
                    autoRoomClimbPillarStallTicks++;
                    if (autoRoomClimbPillarStallTicks >= 20) {
                        abandonRoomClimbPillar("jump blocked");
                        return;
                    }
                } else {
                    autoRoomClimbPillarStallTicks = 0;
                }
            } else {
                Utils.setPressed(mc.options.jumpKey, false);
            }
        } else {
            Utils.setPressed(mc.options.jumpKey, false);
            autoRoomClimbPillarStallTicks = 0;
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
            autoRoomClimbBaritoneMoveAnchor = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            autoRoomClimbBaritoneMoveAnchorMs = System.currentTimeMillis();
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

        if (feet.getY() >= room.cy() && feet.getY() <= room.cy() + 2 && inInterior && mc.player.isOnGround()) {
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
            releaseAllAutoKeys();
            BaritoneHelper.stopAllPathing();
            enterState(AutoState.ROOM, "climb baritone arrived");
            return;
        }

        double movedFromAnchor = autoRoomClimbBaritoneMoveAnchor == null ? 0.0
            : autoRoomClimbBaritoneMoveAnchor.distanceTo(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        if (movedFromAnchor >= 1.0) {
            autoRoomClimbBaritoneMoveAnchor = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            autoRoomClimbBaritoneMoveAnchorMs = System.currentTimeMillis();
        }

        long tryElapsed = System.currentTimeMillis() - autoRoomClimbBaritoneTryStartMs;
        boolean stalled = System.currentTimeMillis() - autoRoomClimbBaritoneMoveAnchorMs >= 10_000L;
        if (tryElapsed > 30_000L && stalled) {
            BaritoneHelper.stopAllPathing();
            autoRoomClimbBaritoneIndex++;
            autoRoomClimbBaritoneTryStartMs = 0;
            return;
        }

        IBaritone bt = primaryBaritone();
        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
        boolean goalActive = bt != null && bt.getCustomGoalProcess().isActive();
        if (!pathing && !goalActive && tryElapsed > 1000L) {
            autoBaritoneRetries++;
            if (autoBaritoneRetries >= 3) {
                BaritoneHelper.stopAllPathing();
                autoRoomClimbBaritoneIndex++;
                autoRoomClimbBaritoneTryStartMs = 0;
                logNavTelemetry("ROOM_CLIMB", goal, autoBaritoneRetries);
                return;
            }
            if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
        }

        logNavTelemetry("ROOM_CLIMB", goal, autoBaritoneRetries);
    }

    private void roomClimbFailed() {
        autoLog("ROOM_CLIMB", autoSite.center, "climb failed, leaving");
        BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
        releaseAllAutoKeys();
        BaritoneHelper.stopAllPathing();
        finishRoomAndAscend();
    }

    private List<BlockPos> findRoomClimbCandidates(Room room) {
        List<BlockPos> open = new ArrayList<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = room.cx() - room.k(); x <= room.cx() + room.k(); x++) {
            for (int z = room.cz() - room.p(); z <= room.cz() + room.p(); z++) {
                cursor.set(x, room.cy() - 1, z);
                BlockState floorState = mc.world.getBlockState(cursor);
                if (isOpenState(floorState, cursor) || !floorState.getFluidState().isEmpty()) continue;
                if (isContainerPresent(cursor) || floorState.getBlock() == Blocks.SPAWNER) continue;
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
        if (finishedSite.shipwreck != null) return resumeShipwreckAtSurface();
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
        if (!autoRunAbandoned) statRoomsFarmed++;
        autoLog("CHAIN", from, String.format("next=%d,%d,%d dist=%.1f",
            chainSite.center.getX(), chainSite.center.getY(), chainSite.center.getZ(), chainDist));

        autoSite = chainSite;
        autoColumn = chainPlan;
        autoChestplateAllowed = !chainPlan.water();
        autoRunAbandoned = false;
        autoRoomFoundShulkers = 0;
        autoRoomNotOpened = false;
        autoRoomFoundItems = 0;
        autoClimbLostGlideRetries = 0;
        autoTakeoffRetries = 0;
        autoAscendFailedPoints.clear();
        autoWaterTakeoffFailures.clear();
        autoAscendRadius = 0;
        autoAscendSurfacedCount = 0;
        autoWalkReapproach = false;
        autoWalkReapproachCount = 0;

        enterState(AutoState.WALK, String.format("chained from %d,%d,%d dist=%.1f",
            from.getX(), from.getY(), from.getZ(), chainDist));
        return true;
    }

    private void enterAscendOrSwimUp(String details) {
        if (autoExitShaftForeign && autoExitShaft != null) {
            double farDxz = Math.hypot((autoExitShaft.x() + 0.5) - mc.player.getX(), (autoExitShaft.z() + 0.5) - mc.player.getZ());
            if (farDxz > 4.0 && !autoTunnelTrail.isEmpty()) {
                enterState(AutoState.EXIT, details);
                return;
            }
            if (farDxz > 4.0) {
                enterState(AutoState.ASCEND, details);
                return;
            }
        }
        boolean water = (autoExitShaft != null && autoExitShaft.water()) || autoColumn.water();
        if (water) {
            enterState(AutoState.SWIM_UP, details);
        } else {
            enterState(AutoState.ASCEND, details);
        }
    }

    private void tickExitTunnelTrail() {
        if (autoTunnelTrail.isEmpty()) {
            finishExitTunnelTrail(false, "trail done");
            return;
        }

        autoExitTrailTotalTicks++;
        if (autoExitTrailBudgetTicks > 0 && autoExitTrailTotalTicks > autoExitTrailBudgetTicks) {
            autoLog("EXIT", autoSite.center, "trail timeout");
            finishExitTunnelTrail(true, "trail timeout");
            return;
        }

        if (autoExitReturnSettingsSaved && autoExitShaft != null && mc.player.isTouchingWater()
            && Math.hypot(autoExitShaft.x() + 0.5 - mc.player.getX(), autoExitShaft.z() + 0.5 - mc.player.getZ()) <= 1.5
            && mc.player.getY() >= autoExitShaft.bottomY() - 2 && mc.player.getY() <= autoExitShaft.exitY()
            && columnHasWater(autoExitShaft.x(), autoExitShaft.z(), autoExitShaft.bottomY(), autoExitShaft.exitY())) {
            finishExitTunnelTrail(false, "trail done");
            return;
        }

        BlockPos feet = mc.player.getBlockPos();
        BlockPos target = autoTunnelTrail.get(autoTunnelTrail.size() - 1);
        boolean descending = feet.getX() == target.getX() && feet.getZ() == target.getZ() && target.getY() < feet.getY();
        if (descending) Utils.setPressed(mc.options.forwardKey, false);
        for (int i = autoTunnelTrail.size() - 2; i >= 0; i--) {
            BlockPos p = autoTunnelTrail.get(i);
            int d = Math.max(Math.abs(p.getX() - feet.getX()), Math.max(Math.abs(p.getY() - feet.getY()), Math.abs(p.getZ() - feet.getZ())));
            if (d <= 1) {
                if (autoExitReturnSettingsSaved && i == 0
                    && (Math.hypot(p.getX() + 0.5 - mc.player.getX(), p.getZ() + 0.5 - mc.player.getZ()) > 1.5
                        || Math.abs(p.getY() - mc.player.getY()) > 1.5)) continue;
                removeTunnelTrailFrom(i);
                autoExitTrailTicks = 0;
                autoExitTrailSkipped = 0;
                autoExitTrailBreakPos = null;
                autoExitTrailBreakLabel = null;
                if (autoTunnelTrail.isEmpty()) finishExitTunnelTrail(false, "trail done");
                return;
            }
        }

        double dx = (target.getX() + 0.5) - mc.player.getX();
        double dz = (target.getZ() + 0.5) - mc.player.getZ();
        double dxz = Math.hypot(dx, dz);
        int dy = target.getY() - feet.getY();
        if (feet.getX() == target.getX() && feet.getZ() == target.getZ() && Math.abs(dy) <= 1) {
            autoTunnelTrail.remove(autoTunnelTrail.size() - 1);
            autoExitTrailTicks = 0;
            autoExitTrailSkipped = 0;
            autoExitTrailBreakPos = null;
            autoExitTrailBreakLabel = null;
            if (autoTunnelTrail.isEmpty()) finishExitTunnelTrail(false, "trail done");
            return;
        }

        if (autoExitReturnSettingsSaved && !autoExitTrailDirectTried && !mc.player.isTouchingWater() && dy >= 2 && mc.world.getFluidState(target).isEmpty()) {
            autoLog("EXIT", autoSite.center, String.format("trail climbs dy=%d to %d,%d,%d, back with baritone",
                dy, target.getX(), target.getY(), target.getZ()));
            autoExitTrailDirectTried = true;
            BaritoneAPI.getSettings().allowBreak.value = autoExitReturnSavedAllowBreak;
            BaritoneAPI.getSettings().allowBreakAnyway.value = autoExitReturnSavedAllowBreakAnyway;
        }

        autoExitTrailTicks++;
        if (autoExitTrailTicks >= 200) {
            if (autoExitReturnSettingsSaved) {
                finishExitTunnelTrail(true, "trail blocked, ascend");
                return;
            }
            autoLog("EXIT", autoSite.center, String.format("trail skip %d,%d,%d",
                target.getX(), target.getY(), target.getZ()));
            autoTunnelTrail.remove(autoTunnelTrail.size() - 1);
            autoExitTrailTicks = 0;
            autoExitTrailBreakPos = null;
            autoExitTrailBreakLabel = null;
            autoExitTrailSkipped++;
            if (autoExitTrailSkipped >= 3) {
                autoLog("EXIT", autoSite.center, "trail abandoned");
                finishExitTunnelTrail(true, "trail abandoned");
            } else if (autoTunnelTrail.isEmpty()) {
                finishExitTunnelTrail(false, "trail done");
            }
            return;
        }

        if (autoExitReturnSettingsSaved && autoExitTrailDirectTried) {
            if (autoExitTrailGoal == null || !autoTunnelTrail.contains(autoExitTrailGoal)) {
                autoExitTrailGoal = autoTunnelTrail.get(0);
                autoExitNoPathChecks = 0;
                autoExitSelfWalk = false;
                autoExitSelfBestDist = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(Vec3d.ofBottomCenter(autoExitTrailGoal));
                autoExitSelfStuckTicks = 0;
                releaseAllAutoKeys();
                IBaritone bt = primaryBaritone();
                if (bt != null) {
                    bt.getPathingBehavior().cancelEverything();
                    bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(autoExitTrailGoal));
                }
                autoLog("EXIT", autoSite.center, String.format("trail goal %d,%d,%d",
                    autoExitTrailGoal.getX(), autoExitTrailGoal.getY(), autoExitTrailGoal.getZ()));
                return;
            }
            if (!autoExitSelfWalk) {
                IBaritone bt = primaryBaritone();
                boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
                if (bt != null && !bt.getPathingBehavior().getInProgress().isEmpty()) {
                    logNavTelemetry("EXIT trail", autoExitTrailGoal, 0);
                    return;
                }
                autoExitNoPathChecks = pathing ? 0 : autoExitNoPathChecks + 1;
                double dist = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(Vec3d.ofBottomCenter(autoExitTrailGoal));
                if (dist < autoExitSelfBestDist - 0.3) {
                    autoExitSelfBestDist = dist;
                    autoExitSelfStuckTicks = 0;
                } else {
                    autoExitSelfStuckTicks++;
                }
                if (autoExitNoPathChecks < 40 && autoExitSelfStuckTicks < 80) {
                    logNavTelemetry("EXIT trail", autoExitTrailGoal, 0);
                    return;
                }
                if (bt != null) bt.getPathingBehavior().cancelEverything();
                releaseAllAutoKeys();
                double playerToGoal = Math.hypot(autoExitTrailGoal.getX() + 0.5 - mc.player.getX(), autoExitTrailGoal.getZ() + 0.5 - mc.player.getZ());
                int keep = autoTunnelTrail.size();
                while (keep > 1) {
                    BlockPos p = autoTunnelTrail.get(keep - 1);
                    if (Math.hypot(p.getX() - autoExitTrailGoal.getX(), p.getZ() - autoExitTrailGoal.getZ()) < playerToGoal - 0.5) break;
                    keep--;
                }
                removeTunnelTrailFrom(keep);
                autoExitSelfWalk = true;
                autoLog("EXIT", autoSite.center, "trail baritone stalled, retracing with keys");
            }
        }

        if (autoExitTrailBreakPos != null) {
            BlockState breakState = mc.world.getBlockState(autoExitTrailBreakPos);
            if (isOpenState(breakState, autoExitTrailBreakPos)) {
                autoLog("EXIT", autoSite.center, String.format("trail broken %d,%d,%d block=%s",
                    autoExitTrailBreakPos.getX(), autoExitTrailBreakPos.getY(), autoExitTrailBreakPos.getZ(), autoExitTrailBreakLabel));
                autoExitTrailBreakPos = null;
                autoExitTrailBreakLabel = null;
                if (!autoExitReturnSettingsSaved) autoExitTrailTicks = 0;
                autoExitTrailSkipped = 0;
            } else {
                releaseAllAutoKeys();
                autoMineTick("EXIT", autoExitTrailBreakPos, descending && autoExitTrailBreakPos.equals(feet.down()));
                return;
            }
        }

        BlockPos below = feet.down();
        BlockState belowState = mc.world.getBlockState(below);
        boolean descendNow = descending && !mc.player.isTouchingWater() && mc.player.isOnGround()
            && !isOpenState(belowState, below) && belowState.getHardness(mc.world, below) >= 0
            && !isTunnelProtected(below) && !isLavaNearShaft(below, feet) && !isLavaNearShaft(below.down(), below);
        if (descendNow || autoExitTrailTicks >= 40) {
            BlockPos obstacle = descendNow ? below : findTrailRetraceObstacle(target);
            if (obstacle != null) {
                autoExitTrailBreakPos = obstacle;
                autoExitTrailBreakLabel = Registries.BLOCK.getId(mc.world.getBlockState(obstacle).getBlock()).getPath();
                autoLog("EXIT", autoSite.center, String.format("trail blocked at %d,%d,%d block=%s, breaking",
                    obstacle.getX(), obstacle.getY(), obstacle.getZ(), autoExitTrailBreakLabel));
                releaseAllAutoKeys();
                autoMineTick("EXIT", autoExitTrailBreakPos, descending && autoExitTrailBreakPos.equals(below));
                return;
            }
        }

        if (!mc.player.isTouchingWater() && target.getY() > feet.getY() && dxz <= 0.7) {
            BlockPos belowTarget = target.down();
            BlockState belowTargetState = mc.world.getBlockState(belowTarget);
            if (isOpenState(belowTargetState, belowTarget) || target.getY() - feet.getY() >= 2) {
                Utils.setPressed(mc.options.forwardKey, false);
                Utils.setPressed(mc.options.sprintKey, false);
                Utils.setPressed(mc.options.sneakKey, false);
                Utils.setPressed(mc.options.jumpKey, true);
                mc.player.setPitch(90.0f);
                if (!mc.player.isOnGround() && mc.player.getVelocity().y <= 0.05) {
                    BlockPos placePos = mc.player.getBlockPos().down();
                    BlockState placeState = mc.world.getBlockState(placePos);
                    if (isOpenState(placeState, placePos) && placeState.getFluidState().isEmpty() && ensureObsidianInHotbar() >= 0) {
                        tickSelfAscendPlaceStep(placePos);
                    }
                }
                return;
            }
        }

        if (!descending) mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        Utils.setPressed(mc.options.forwardKey, !descending);
        Utils.setPressed(mc.options.sprintKey, false);
        Utils.setPressed(mc.options.jumpKey, target.getY() > feet.getY() || mc.player.horizontalCollision);
        Utils.setPressed(mc.options.sneakKey, mc.player.isTouchingWater() && !mc.player.isOnGround() && target.getY() < feet.getY());
    }

    private void removeTunnelTrailFrom(int index) {
        for (int i = autoTunnelTrail.size() - 1; i >= index; i--) autoTunnelTrail.remove(i);
    }

    private void finishExitTunnelTrail(boolean selfAscend, String details) {
        boolean chainReturn = autoExitReturnSettingsSaved;
        boolean shaftNear = chainReturn && selfAscend && autoExitShaft != null
            && Math.hypot((autoExitShaft.x() + 0.5) - mc.player.getX(), (autoExitShaft.z() + 0.5) - mc.player.getZ()) <= 4.0
            && Math.abs(mc.player.getY() - autoExitShaft.bottomY()) <= 4.0;
        restoreExitReturnSettings();
        autoExitRetracingTrail = false;
        autoTunnelTrail.clear();
        autoExitShaftForeign = selfAscend && !shaftNear;
        autoExitTrailTicks = 0;
        autoExitTrailSkipped = 0;
        autoExitTrailTotalTicks = 0;
        autoExitTrailBudgetTicks = 0;
        autoExitTrailBreakPos = null;
        autoExitTrailBreakLabel = null;
        releaseAllAutoKeys();
        if (shaftNear) {
            autoShaftFlyAttempts = 0;
            autoShaftFlyRerouteAttempts = 0;
            resetShaftDryRecovery();
            enterState(autoExitShaft.water() ? AutoState.SWIM_UP : AutoState.SHAFT_ALIGN, details + ", shaft near");
            return;
        }
        enterState(chainReturn && selfAscend ? AutoState.ASCEND : AutoState.EXIT, details);
    }

    private void restoreExitReturnSettings() {
        if (!autoExitReturnSettingsSaved) return;
        IBaritone bt = primaryBaritone();
        if (bt != null) bt.getPathingBehavior().cancelEverything();
        if (autoPaused && state == AutoState.EXIT) endAutoPause();
        BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
        BaritoneAPI.getSettings().allowBreak.value = autoExitReturnSavedAllowBreak;
        BaritoneAPI.getSettings().allowBreakAnyway.value = autoExitReturnSavedAllowBreakAnyway;
        autoExitReturnSettingsSaved = false;
        autoExitReturnSavedAllowBreakAnyway = null;
        autoExitReturnTicks = 0;
        autoExitTrailDirectTried = false;
        autoExitTrailGoal = null;
    }

    private void retryExitTunnelTrail() {
        IBaritone bt = primaryBaritone();
        if (bt != null) bt.getPathingBehavior().cancelEverything();
        releaseAllAutoKeys();
        autoExitReturning = false;
        autoExitRetracingTrail = true;
        autoExitTrailGoal = null;
        autoExitTrailTicks = 0;
        autoExitTrailBreakPos = null;
        autoExitTrailBreakLabel = null;
        BaritoneAPI.getSettings().allowPlace.value = true;
        autoLog("EXIT", autoSite.center, "shaft baritone failed, returning along tunnel trail");
    }

    private BlockPos findTrailRetraceObstacle(BlockPos target) {
        BlockPos feet = mc.player.getBlockPos();
        int dx = Integer.signum(target.getX() - feet.getX());
        int dz = Integer.signum(target.getZ() - feet.getZ());
        if (dx != 0 && dz != 0) {
            if (Math.abs(target.getX() - feet.getX()) >= Math.abs(target.getZ() - feet.getZ())) dz = 0;
            else dx = 0;
        }
        if (dx == 0 && dz == 0) {
            if (target.getY() >= feet.getY() + 2) return trailRetraceCandidate(feet.up(2));
            if (target.getY() > feet.getY()) {
                BlockPos candidate = trailRetraceCandidate(feet.up());
                return candidate != null || !autoExitReturnSettingsSaved ? candidate : trailRetraceCandidate(feet.up(2));
            }
            if (target.getY() < feet.getY()) return trailRetraceCandidate(feet.down());
            return null;
        }
        if (target.getY() >= feet.getY() + 2) return null;
        if (autoExitReturnSettingsSaved && Math.abs(target.getY() - feet.getY()) > 1) return null;
        BlockPos side = new BlockPos(feet.getX() + dx, feet.getY(), feet.getZ() + dz);
        if (autoExitReturnSettingsSaved && !autoTunnelTrail.contains(side)) return null;
        BlockPos candidate = trailRetraceCandidate(side);
        if (candidate != null) return candidate;
        return trailRetraceCandidate(side.up());
    }

    private BlockPos trailRetraceCandidate(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (isOpenState(state, pos)) return null;
        if (state.getBlock() == Blocks.SPAWNER || isContainerPresent(pos)) return null;
        if (isLava(state) || isLavaNearShaft(pos, pos.up())) return null;
        return pos;
    }

    private BlockPos findDryExitReturnGoal(BlockPos shaftBottom) {
        BlockPos best = null;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            for (int distance = 1; distance <= 12; distance++) {
                BlockPos wall = shaftBottom.offset(dir, distance);
                if (!mc.world.isChunkLoaded(wall.getX() >> 4, wall.getZ() >> 4)
                    || mc.world.getBlockState(wall).isOf(Blocks.SPAWNER) || isContainerPresent(wall)
                    || mc.world.getBlockState(wall.up()).isOf(Blocks.SPAWNER) || isContainerPresent(wall.up())) break;
                if (mc.world.getFluidState(wall).isIn(FluidTags.WATER)
                    || mc.world.getFluidState(wall.up()).isIn(FluidTags.WATER)) continue;
                if (isOpenState(mc.world.getBlockState(wall), wall)) break;
                BlockPos goal = wall.offset(dir);
                boolean safe = true;
                for (BlockPos pos : BlockPos.iterate(goal.getX() - 1, goal.getY() - 1, goal.getZ() - 1,
                    goal.getX() + 1, goal.getY() + 2, goal.getZ() + 1)) {
                    if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
                        || !mc.world.getFluidState(pos).isEmpty()
                        || mc.world.getBlockState(pos).isOf(Blocks.SPAWNER) || isContainerPresent(pos)) {
                        safe = false;
                        break;
                    }
                }
                if (safe && !isOpenState(mc.world.getBlockState(goal.down()), goal.down())
                    && (best == null || goal.getSquaredDistance(shaftBottom) < best.getSquaredDistance(shaftBottom))) best = goal;
                break;
            }
        }
        return best;
    }

    private void tickExit() {
        if (isShipwreck() && autoShipwreckRun != null && autoShipwreckRun.returning()) {
            releaseAllAutoKeys();
            BaritoneHelper.stopAllPathing();
            enterAscendOrSwimUp("shipwreck surface before next chest");
            return;
        }
        stateTicks++;
        if (stateTicks == 1) {
            autoShaftFlyAttempts = 0;
            autoShaftFlyRerouteAttempts = 0;
        }
        if (autoExitReturnSettingsSaved && ++autoExitReturnTicks > 60 * 20) {
            finishExitTunnelTrail(true, "return to shaft timeout");
            return;
        }

        if (autoExitShaft != null) {
            BlockPos shaftBottom = new BlockPos(autoExitShaft.x(), autoExitShaft.bottomY(), autoExitShaft.z());
            double farDxz = Math.hypot((autoExitShaft.x() + 0.5) - mc.player.getX(), (autoExitShaft.z() + 0.5) - mc.player.getZ());

            if (stateTicks == 1 && !autoExitReturnSettingsSaved) {
                autoExitReturnDryGoal = null;
                autoExitRetracingTrail = false;
                autoExitTrailTicks = 0;
                autoExitTrailSkipped = 0;
                autoExitTrailTotalTicks = 0;
                autoExitTrailBudgetTicks = 0;
                autoExitTrailBreakPos = null;
                autoExitTrailBreakLabel = null;
                if (autoExitShaftForeign && farDxz <= 4.0
                    && (autoTunnelTrail.isEmpty() || Math.abs(mc.player.getY() - autoExitShaft.bottomY()) <= 1.5)) {
                    autoExitShaftForeign = false;
                    autoTunnelTrail.clear();
                }
                boolean foreign = autoExitShaftForeign && (farDxz > 4.0 || !autoTunnelTrail.isEmpty());
                if (foreign) {
                    ColumnPlan own = planColumn(autoSite);
                    int ownCy = autoSite.room.cy();
                    if (own != null && !own.side() && !own.skyBlocked()
                        && own.startY() <= ownCy + 6 && own.holeTopY() > own.startY()
                        && (own.water() ? own.blocksToBreak() == 0 && (!autoRunAbandoned
                            || (Math.hypot((own.x() + 0.5) - mc.player.getX(), (own.z() + 0.5) - mc.player.getZ()) <= 4.0
                                && Math.abs(mc.player.getY() - ownCy) <= 4.0))
                                        : own.waterCells() == 0 && own.blocksToBreak() <= 2)) {
                        autoExitShaft = new ExitShaft(own.x(), own.z(), ownCy, own.holeTopY(), own.water(), false, null);
                        autoExitShaftForeign = false;
                        autoTunnelTrail.clear();
                        releaseAllAutoKeys();
                        IBaritone bt = primaryBaritone();
                        if (bt != null) bt.getPathingBehavior().cancelEverything();
                        autoLog("EXIT", autoSite.center, String.format("own shaft %d,%d missing=%d top=%d, exit through it",
                            own.x(), own.z(), own.blocksToBreak(), own.holeTopY()));
                        enterState(AutoState.EXIT, "own shaft");
                        return;
                    }
                    if (!autoTunnelTrail.isEmpty()) {
                        releaseAllAutoKeys();
                        IBaritone bt = primaryBaritone();
                        if (bt != null) bt.getPathingBehavior().cancelEverything();
                        autoExitReturnSavedAllowBreak = BaritoneAPI.getSettings().allowBreak.value;
                        autoExitReturnSavedAllowBreakAnyway = BaritoneAPI.getSettings().allowBreakAnyway.value;
                        autoExitReturnSettingsSaved = true;
                        BaritoneAPI.getSettings().allowBreak.value = false;
                        BaritoneAPI.getSettings().allowBreakAnyway.value = new ArrayList<>();
                        autoExitReturnTicks = 1;
                        autoExitTrailDirectTried = false;
                        autoExitTrailGoal = null;
                        if (!autoTunnelTrail.get(0).equals(shaftBottom)) autoTunnelTrail.add(0, shaftBottom);
                        autoExitRetracingTrail = true;
                        autoExitReturning = false;
                        autoExitTrailBudgetTicks = 60 * 20;
                        autoLog("EXIT", autoSite.center, "retracing tunnel trail n=" + autoTunnelTrail.size());
                    } else {
                        autoExitReturning = false;
                        releaseAllAutoKeys();
                        autoLog("EXIT", autoSite.center, "exit shaft belongs to the previous room, no tunnel trail, ascend");
                        enterState(AutoState.ASCEND, "exit shaft belongs to the previous room");
                        return;
                    }
                } else {
                    boolean roomWater = mc.player.isTouchingWater()
                        && columnHasWater(autoExitShaft.x(), autoExitShaft.z(), autoExitShaft.bottomY(), autoExitShaft.exitY());
                    autoExitReturning = (farDxz > 4.0 || mc.player.getY() < autoExitShaft.bottomY() - 1.5)
                        && !roomWater && autoExitShaft.entry() == null;
                }
                autoExitNoPathChecks = 0;
                autoExitSelfWalk = false;
                autoExitSelfBestDist = farDxz;
                autoExitSelfStuckTicks = 0;
                autoExitBreakPos = null;
                autoExitBreakLabel = null;
                if (autoExitReturning) {
                    autoBaritoneRetries = 0;
                    resetBreach();
                    autoLog("EXIT", autoSite.center, String.format("returning to shaft %d,%d,%d with baritone dist=%.1f",
                        shaftBottom.getX(), shaftBottom.getY(), shaftBottom.getZ(), farDxz));
                    if (mc.world.getFluidState(shaftBottom).isIn(FluidTags.WATER)
                        || mc.world.getFluidState(shaftBottom.up()).isIn(FluidTags.WATER)) {
                        autoExitReturnDryGoal = findDryExitReturnGoal(shaftBottom);
                        if (autoExitReturnDryGoal == null) {
                            autoExitReturning = false;
                            releaseAllAutoKeys();
                            if (farDxz <= 8.0) {
                                enterState(AutoState.SWIM_UP, "no dry return goal, swim to the flooded shaft");
                                return;
                            }
                            enterState(AutoState.ASCEND, "no dry return goal outside flooded room");
                            return;
                        }
                        autoLog("EXIT", autoSite.center, String.format("dry return goal %d,%d,%d", autoExitReturnDryGoal.getX(), autoExitReturnDryGoal.getY(), autoExitReturnDryGoal.getZ()));
                    }
                    IBaritone bt = primaryBaritone();
                    if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(autoExitReturnDryGoal != null ? autoExitReturnDryGoal : shaftBottom));
                }
            }

            if (autoExitReturnSettingsSaved) {
                boolean wasPaused = autoPaused;
                if (handleAutoPause(true)) {
                    IBaritone bt = primaryBaritone();
                    if (bt != null) bt.getPathingBehavior().cancelEverything();
                    return;
                }
                if (wasPaused) {
                    IBaritone bt = primaryBaritone();
                    BlockPos goal = autoExitReturning ? shaftBottom : autoExitTrailGoal;
                    if (autoExitRetracingTrail && goal != null && !autoExitSelfWalk) {
                        autoExitSelfStuckTicks = 0;
                        autoExitNoPathChecks = 0;
                        autoExitSelfBestDist = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(Vec3d.ofBottomCenter(goal));
                    }
                    if (bt != null && goal != null && !autoExitSelfWalk) {
                        bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                    }
                }
            }

            if (autoExitRetracingTrail) {
                tickExitTunnelTrail();
                return;
            }

            if (autoExitReturning) {
                boolean returnWater = mc.player.isTouchingWater()
                    && columnHasWater(autoExitShaft.x(), autoExitShaft.z(), autoExitShaft.bottomY(), autoExitShaft.exitY());
                double rdy = Math.abs(mc.player.getY() - autoExitShaft.bottomY());
                IBaritone bt = primaryBaritone();
                if (farDxz <= 1.5 && rdy <= 1.5 && (autoExitReturnDryGoal == null || mc.player.isTouchingWater())) {
                    if (bt != null) bt.getPathingBehavior().cancelEverything();
                    releaseAllAutoKeys();
                    autoExitReturning = false;
                    if (autoExitReturnSettingsSaved) {
                        autoTunnelTrail.clear();
                        autoExitShaftForeign = false;
                        restoreExitReturnSettings();
                    }
                    enterState(AutoState.EXIT, "back at shaft");
                    return;
                }
                if (stateTicks > 60 * 20) {
                    if (bt != null) bt.getPathingBehavior().cancelEverything();
                    releaseAllAutoKeys();
                    autoExitReturning = false;
                    if (autoExitReturnDryGoal != null && !mc.player.isTouchingWater()) {
                        enterState(returnWater ? AutoState.SWIM_UP : AutoState.ASCEND, "dry return to flooded room timeout");
                        return;
                    }
                    if (farDxz > 4.0) {
                        enterState(returnWater ? AutoState.SWIM_UP : AutoState.ASCEND, "return to shaft timeout");
                        return;
                    }
                    autoExitShaftForeign = false;
                    enterAscendOrSwimUp("return to shaft timeout");
                    return;
                }

                if (farDxz <= 1.5 && mc.player.isTouchingWater()
                    && columnHasWater(autoExitShaft.x(), autoExitShaft.z(), autoExitShaft.bottomY(), autoExitShaft.exitY())) {
                    releaseAllAutoKeys();
                    autoExitReturning = false;
                    autoExitSelfWalk = false;
                    enterState(AutoState.SWIM_UP, "water on the way back");
                    return;
                }

                if (autoExitReturnDryGoal != null && !autoExitSelfWalk && mc.player.getBlockPos().equals(autoExitReturnDryGoal)) {
                    if (bt != null) bt.getPathingBehavior().cancelEverything();
                    releaseAllAutoKeys();
                    autoExitSelfWalk = true;
                    autoExitSelfBestDist = farDxz;
                    autoExitSelfStuckTicks = 0;
                    autoLog("EXIT", autoSite.center, "at dry return goal, entering flooded room");
                }

                if (autoExitSelfWalk) {
                    if (farDxz <= 1.5 && mc.player.getY() < autoExitShaft.bottomY()) {
                        releaseAllAutoKeys();
                        autoExitSelfWalk = false;
                        autoExitBreakPos = null;
                        autoExitNoPathChecks = 0;
                        autoLog("EXIT", autoSite.center, String.format("under the shaft dy=%.1f, back to baritone and breach", rdy));
                        return;
                    }

                    if (autoExitBreakPos != null) {
                        BlockState breakState = mc.world.getBlockState(autoExitBreakPos);
                        if (isOpenState(breakState, autoExitBreakPos)) {
                            autoLog("EXIT", autoSite.center, String.format("exit blocked, broken %d,%d,%d block=%s",
                                autoExitBreakPos.getX(), autoExitBreakPos.getY(), autoExitBreakPos.getZ(), autoExitBreakLabel));
                            autoExitBreakPos = null;
                            autoExitSelfStuckTicks = 0;
                            autoExitSelfBestDist = farDxz;
                        } else {
                            Utils.setPressed(mc.options.forwardKey, false);
                            Utils.setPressed(mc.options.jumpKey, false);
                            Utils.setPressed(mc.options.sneakKey, false);
                            autoMineTick("EXIT", autoExitBreakPos, false);
                            return;
                        }
                    }

                    Utils.setPressed(mc.options.sneakKey, false);
                    recenterDig(autoExitShaft.x(), autoExitShaft.z(), 0.3);
                    Utils.setPressed(mc.options.jumpKey, (mc.player.isOnGround() && mc.player.horizontalCollision)
                        || (autoExitReturnDryGoal != null && mc.player.isTouchingWater()));

                    if (farDxz <= autoExitSelfBestDist - 0.3) {
                        autoExitSelfBestDist = farDxz;
                        autoExitSelfStuckTicks = 0;
                    } else if (mc.player.horizontalCollision) {
                        autoExitSelfStuckTicks++;
                    }
                    if (autoExitSelfStuckTicks >= 40) {
                        BlockPos obstacle = findApproachFallbackObstacle(shaftBottom, shaftBottom);
                        if (obstacle != null) {
                            autoExitBreakPos = obstacle;
                            autoExitBreakLabel = Registries.BLOCK.getId(mc.world.getBlockState(obstacle).getBlock()).getPath();
                            autoLog("EXIT", autoSite.center, String.format("exit blocked at %d,%d,%d block=%s, breaking",
                                obstacle.getX(), obstacle.getY(), obstacle.getZ(), autoExitBreakLabel));
                            autoExitSelfStuckTicks = 0;
                            Utils.setPressed(mc.options.forwardKey, false);
                            Utils.setPressed(mc.options.jumpKey, false);
                            autoMineTick("EXIT", autoExitBreakPos, false);
                            return;
                        } else if (autoExitSelfStuckTicks >= 200) {
                            releaseAllAutoKeys();
                            autoExitReturning = false;
                            autoExitSelfWalk = false;
                            if (autoExitReturnDryGoal != null && !mc.player.isTouchingWater()) {
                                enterState(returnWater ? AutoState.SWIM_UP : AutoState.ASCEND, "dry return to flooded room stuck");
                                return;
                            }
                            if (farDxz > 4.0) {
                                enterState(returnWater ? AutoState.SWIM_UP : AutoState.ASCEND, "walk back stuck");
                                return;
                            }
                            autoExitShaftForeign = false;
                            enterAscendOrSwimUp("walk back stuck");
                            return;
                        }
                    }

                    logNavTelemetry("EXIT return", shaftBottom, autoBaritoneRetries);
                    return;
                }

                boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
                if (autoExitReturnSettingsSaved) {
                    double dist = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(Vec3d.ofBottomCenter(shaftBottom));
                    if (dist < autoExitSelfBestDist - 0.3) {
                        autoExitSelfBestDist = dist;
                        autoExitSelfStuckTicks = 0;
                    } else {
                        autoExitSelfStuckTicks++;
                    }
                    if (autoExitSelfStuckTicks >= 200) {
                        retryExitTunnelTrail();
                        return;
                    }
                }
                if (!pathing && autoBreachPos == null && stateTicks > 20 && stateTicks % 40 == 0) {
                    autoExitNoPathChecks++;
                    if (autoExitNoPathChecks >= 2 && !autoExitSelfWalk
                        && (farDxz > 1.5 || mc.player.getY() >= autoExitShaft.bottomY())) {
                        if (bt != null) bt.getPathingBehavior().cancelEverything();
                        releaseAllAutoKeys();
                        if (autoExitReturnSettingsSaved) {
                            retryExitTunnelTrail();
                            return;
                        }
                        if (autoExitReturnDryGoal != null) {
                            autoExitReturning = false;
                            enterState(returnWater ? AutoState.SWIM_UP : AutoState.ASCEND, "no path to dry return goal outside flooded room");
                            return;
                        }
                        autoExitSelfWalk = true;
                        autoApproachBreakCount = 0;
                        autoExitSelfBestDist = farDxz;
                        autoLog("EXIT", autoSite.center, String.format("baritone found no path, walking to the shaft dist=%.1f", farDxz));
                        return;
                    }
                    autoBaritoneRetries++;
                    if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(autoExitReturnDryGoal != null ? autoExitReturnDryGoal : shaftBottom));
                } else if (pathing && stateTicks % 40 == 0) {
                    autoExitNoPathChecks = 0;
                }
                if (!autoExitReturnSettingsSaved && autoExitReturnDryGoal == null) tickBreach("EXIT", shaftBottom);
                logNavTelemetry("EXIT return", autoExitReturnDryGoal != null ? autoExitReturnDryGoal : shaftBottom, autoBaritoneRetries);
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
            autoExitWalkingLogged = false;
            autoExitPathCell = null;
            autoExitIdleTicks = 0;
            autoExitStuckTicks = 0;
            autoExitStuckCount = 0;
            autoExitStuckAnchor = null;
            autoExitStuckBreaking = false;
            autoExitStuckBreakPos = null;
            autoExitStuckBreakLabel = null;
            autoExitStuckBreakTicks = 0;
            autoExitPathStep = 0;
            if (!autoAreaLoaderWasOn && !autoSoloMode) {
                enterAscendOrSwimUp("no flight module, staying on foot");
                return;
            }
            boolean eligible = autoShaftFly.get()
                && autoExitShaft != null
                && (!autoExitShaft.water() || shaftWaterOnlyAtTop(autoExitShaft))
                && (!autoExitShaft.skyBlocked() || shaftExitHasPivotEscape(autoExitShaft))
                && countRockets() >= 4;
            if (!eligible) {
                enterAscendOrSwimUp("shaft-fly not eligible");
                return;
            }
            autoBaritoneRetries = 0;
            resetShaftDryRecovery();
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

        if (dxz <= 0.5) {
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            enterState(AutoState.SHAFT_ALIGN, "");
            return;
        }

        IBaritone bt = primaryBaritone();
        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
        boolean exitBaritoneGaveUp = bt == null || !bt.getCustomGoalProcess().isActive();
        boolean exitGrounded = mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isClimbing();
        boolean nearShaft = dxz <= 3.0;
        if (!pathing && !exitBaritoneGaveUp) {
            autoExitIdleTicks++;
            if (nearShaft || autoExitIdleTicks >= 100) {
                bt.getPathingBehavior().cancelEverything();
                exitBaritoneGaveUp = true;
                autoExitIdleTicks = 0;
                if (!nearShaft) autoLog("EXIT", autoSite.center, "baritone idle 5s, restarting");
            }
        } else {
            autoExitIdleTicks = 0;
        }
        if (!pathing && exitBaritoneGaveUp && (autoBaritoneRetries >= 3 || nearShaft)) {
            if (!autoExitWalkingLogged) {
                autoExitWalkingLogged = true;
                autoLog("EXIT", autoSite.center, nearShaft ? "near shaft, walking straight to it" : "baritone gave up, walking to shaft");
            }
            if (autoExitStuckBreaking) {
                Utils.setPressed(mc.options.forwardKey, false);
                Utils.setPressed(mc.options.jumpKey, false);
                Utils.setPressed(mc.options.sneakKey, false);
                BlockState exitBreakState = mc.world.getBlockState(autoExitStuckBreakPos);
                if (isOpenState(exitBreakState, autoExitStuckBreakPos)) {
                    autoLog("EXIT", autoSite.center, String.format("exit stuck, broken %d,%d,%d block=%s",
                        autoExitStuckBreakPos.getX(), autoExitStuckBreakPos.getY(), autoExitStuckBreakPos.getZ(), autoExitStuckBreakLabel));
                    autoExitStuckBreaking = false;
                    autoExitStuckBreakPos = null;
                    autoExitStuckTicks = 0;
                    autoExitStuckAnchor = null;
                } else if (++autoExitStuckBreakTicks >= 200) {
                    autoLog("EXIT", autoSite.center, "exit stuck, break timeout");
                    autoExitStuckBreaking = false;
                    autoExitStuckBreakPos = null;
                    autoExitStuckTicks = 0;
                    autoExitStuckAnchor = null;
                } else {
                    autoMineTick("EXIT", autoExitStuckBreakPos, false);
                }
            } else {
                navigateExitColumn(autoExitShaft.x(), autoExitShaft.z(), 0.3, autoExitShaft.entry(), false);
                autoExitStuckTicks++;
                if (autoExitStuckTicks >= 40) {
                    Vec3d here = new Vec3d(mc.player.getX(), 0.0, mc.player.getZ());
                    boolean stuck = autoExitStuckAnchor != null && here.distanceTo(autoExitStuckAnchor) < 0.3 && (dxz > 0.5 || dy > 0.5);
                    if (stuck) {
                        autoExitStuckCount++;
                        int targetX = autoExitPathCell != null ? autoExitPathCell.getX() : autoExitShaft.x();
                        int targetZ = autoExitPathCell != null ? autoExitPathCell.getZ() : autoExitShaft.z();
                        BlockPos obstacle = autoExitStuckCount <= 3 ? findWalkStuckObstacle(targetX, targetZ) : null;
                        if (obstacle != null) {
                            autoExitStuckBreaking = true;
                            autoExitStuckBreakPos = obstacle;
                            autoExitStuckBreakTicks = 0;
                            autoExitStuckBreakLabel = Registries.BLOCK.getId(mc.world.getBlockState(obstacle).getBlock()).getPath();
                            releaseAllAutoKeys();
                            autoLog("EXIT", autoSite.center, String.format("exit stuck, breaking %d,%d,%d block=%s",
                                obstacle.getX(), obstacle.getY(), obstacle.getZ(), autoExitStuckBreakLabel));
                        } else if (autoExitStuckCount >= 2) {
                            releaseAllAutoKeys();
                            enterAscendOrSwimUp("exit stuck, nothing to break");
                            return;
                        }
                    } else if (autoExitStuckAnchor != null && here.distanceTo(autoExitStuckAnchor) >= 0.3) {
                        autoExitStuckCount = 0;
                    }
                    autoExitStuckAnchor = here;
                    autoExitStuckTicks = 0;
                }
            }
        } else if (!pathing && exitBaritoneGaveUp && exitGrounded && stateTicks > 20 && autoBaritoneRetries < 3) {
            autoBaritoneRetries++;
            if (bt != null) {
                bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(
                    new BlockPos(autoExitShaft.x(), autoExitShaft.bottomY(), autoExitShaft.z())));
            }
        }

        logNavTelemetry("EXIT", new BlockPos(autoExitShaft.x(), autoExitShaft.bottomY(), autoExitShaft.z()), autoBaritoneRetries);
    }

    private void resetShaftDryRecovery() {
        autoShaftDryPhase = ShaftDryPhase.NONE;
        autoShaftDryStartTick = 0;
        autoShaftDryUnderPlacements = 0;
        autoShaftDryBridgeUsed = false;
        autoShaftDryBridgeBlocks = 0;
        autoShaftDryPlacePos = null;
        autoShaftDrySidePos = null;
        autoShaftDryBridgePos = null;
        autoShaftDryVerifyPos = null;
        autoShaftDryVerifyKind = null;
        autoShaftDryVerifyNextPhase = ShaftDryPhase.NONE;
        autoShaftDryVerifyTick = 0;
        autoShaftDryVerifyRetries = 0;
        autoShaftDryBlockLabel = null;
        autoShaftDryPlaceHardFail = false;
    }

    private void clearActiveShaftDryRecovery() {
        autoShaftDryPhase = ShaftDryPhase.NONE;
        autoShaftDryStartTick = 0;
        autoShaftDryBridgeBlocks = 0;
        autoShaftDryPlacePos = null;
        autoShaftDrySidePos = null;
        autoShaftDryBridgePos = null;
        autoShaftDryVerifyPos = null;
        autoShaftDryVerifyKind = null;
        autoShaftDryVerifyNextPhase = ShaftDryPhase.NONE;
        autoShaftDryVerifyTick = 0;
        autoShaftDryVerifyRetries = 0;
        autoShaftDryBlockLabel = null;
        autoShaftDryPlaceHardFail = false;
    }

    private boolean shaftFlyWaterAtBottom() {
        BlockPos feet = mc.player.getBlockPos();
        return mc.player.isTouchingWater() || mc.world.getFluidState(feet).isIn(FluidTags.WATER);
    }

    private boolean waterTakeoffColumnFailed(int x, int z) {
        return autoWaterTakeoffFailures.getOrDefault(new BlockPos(x, 0, z), 0) >= 2;
    }

    private void recordWaterTakeoffFailure(int x, int z) {
        autoWaterTakeoffFailures.merge(new BlockPos(x, 0, z), 1, Integer::sum);
    }

    private void recordShaftFlyWaterFailure() {
        if (autoExitShaft != null && shaftFlyWaterAtBottom()) {
            recordWaterTakeoffFailure(autoExitShaft.x(), autoExitShaft.z());
        }
    }

    private int ensureShaftRecoveryBlockInHotbar() {
        int slot = ensureObsidianInHotbar();
        return slot;
    }

    private String blockItemLabel(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) return Registries.BLOCK.getId(blockItem.getBlock()).getPath();
        return Registries.ITEM.getId(item).getPath();
    }

    private String shaftRecoveryPlacementIssue(BlockPos pos) {
        if (autoExitShaft != null && pos.getY() > autoExitShaft.bottomY() + 1) return "above shaft bottom";
        BlockState target = mc.world.getBlockState(pos);
        if (!isOpenState(target, pos)) return "target blocked";
        if (target.getFluidState().isIn(FluidTags.LAVA)) return "lava";
        BlockState head = mc.world.getBlockState(pos.up());
        if (!isOpenState(head, pos.up())) return "head blocked";
        BlockState support = mc.world.getBlockState(pos.down());
        if (isOpenState(support, pos.down()) || !support.getFluidState().isEmpty()) return "no support";
        return null;
    }

    private boolean placeShaftRecoveryBlock(BlockPos pos, String kind) {
        autoShaftDryPlaceHardFail = false;
        String issue = shaftRecoveryPlacementIssue(pos);
        if (issue != null) {
            autoLog("SHAFT_FLY", autoSite.center, "could not place, reason=" + issue);
            autoShaftDryPlaceHardFail = true;
            return false;
        }
        int slot = ensureShaftRecoveryBlockInHotbar();
        if (slot < 0) {
            autoLog("SHAFT_FLY", autoSite.center, "could not place, reason=no block");
            autoShaftDryPlaceHardFail = true;
            return false;
        }
        InvUtils.swap(slot, false);
        autoShaftDryBlockLabel = blockItemLabel(slot);
        BlockPos supportPos = pos.down();
        Vec3d lookTarget = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(lookTarget, Direction.UP, supportPos, false);
        if ("under".equals(kind)) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        } else if (!interactWhenAligned(lookTarget, hit)) {
            return false;
        }
        mc.player.swingHand(Hand.MAIN_HAND);
        if ("under".equals(kind)) {
            autoLog("SHAFT_FLY", autoSite.center, String.format("water at the bottom, placed %s at %d,%d,%d",
                autoShaftDryBlockLabel, pos.getX(), pos.getY(), pos.getZ()));
        } else if ("side".equals(kind)) {
            autoLog("SHAFT_FLY", autoSite.center, String.format("side step placed at %d,%d,%d",
                pos.getX(), pos.getY(), pos.getZ()));
        } else {
            autoShaftDryBridgeBlocks++;
            autoLog("SHAFT_FLY", autoSite.center, String.format("bridge block %d at %d,%d,%d",
                autoShaftDryBridgeBlocks, pos.getX(), pos.getY(), pos.getZ()));
        }
        return true;
    }

    private void verifyShaftRecoveryBlock(BlockPos pos, String kind, ShaftDryPhase nextPhase) {
        autoShaftDryVerifyPos = pos;
        autoShaftDryVerifyKind = kind;
        autoShaftDryVerifyNextPhase = nextPhase;
        autoShaftDryVerifyTick = stateTicks;
        autoShaftDryVerifyRetries = 0;
    }

    private boolean shaftRecoveryBlockPlaced(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !isOpenState(state, pos) && state.getFluidState().isEmpty();
    }

    private boolean tickShaftRecoveryVerify() {
        if (autoShaftDryVerifyPos == null) return false;
        if (stateTicks - autoShaftDryVerifyTick < 5) return true;
        if (shaftRecoveryBlockPlaced(autoShaftDryVerifyPos)) {
            autoShaftDryVerifyPos = null;
            autoShaftDryVerifyKind = null;
            autoShaftDryPhase = autoShaftDryVerifyNextPhase;
            autoShaftDryVerifyNextPhase = ShaftDryPhase.NONE;
            return true;
        }
        if (autoShaftDryVerifyRetries >= 1) {
            failShaftFlyWaterRecovery("not placed");
            return true;
        }
        autoShaftDryVerifyRetries++;
        if (placeShaftRecoveryBlock(autoShaftDryVerifyPos, autoShaftDryVerifyKind)) {
            autoShaftDryVerifyTick = stateTicks;
        } else if (autoShaftDryPlaceHardFail) {
            failShaftFlyWaterRecovery("not placed");
        }
        return true;
    }

    private BlockPos findShaftRecoverySideStep(BlockPos shaftPos) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos side = shaftPos.offset(dir);
            if (shaftRecoveryPlacementIssue(side) == null) return side;
        }
        return null;
    }

    private boolean startShaftFlyBridgeRecovery(BlockPos shaftPos) {
        if (autoShaftDryBridgeUsed) {
            autoLog("SHAFT_FLY", autoSite.center, "could not place, reason=bridge already used");
            return false;
        }
        if (ensureShaftRecoveryBlockInHotbar() < 0) {
            autoLog("SHAFT_FLY", autoSite.center, "could not place, reason=no block");
            return false;
        }
        BlockPos side = findShaftRecoverySideStep(shaftPos);
        if (side == null) {
            autoLog("SHAFT_FLY", autoSite.center, "could not place, reason=no side step");
            return false;
        }
        autoShaftDryBridgeUsed = true;
        autoShaftDrySidePos = side;
        autoShaftDryBridgePos = shaftPos;
        autoShaftDryBridgeBlocks = 0;
        autoShaftDryPhase = ShaftDryPhase.SIDE_PLACE;
        return true;
    }

    private boolean startShaftFlyWaterRecovery() {
        if (!shaftFlyWaterAtBottom() || autoExitShaft == null
            || mc.player.getBlockPos().getY() > autoExitShaft.bottomY() + 1) return false;
        BlockPos shaftPos = new BlockPos(autoExitShaft.x(), mc.player.getBlockPos().getY(), autoExitShaft.z());
        autoShaftDryStartTick = stateTicks;
        if (autoShaftDryUnderPlacements < 3) {
            if (ensureShaftRecoveryBlockInHotbar() < 0) {
                autoLog("SHAFT_FLY", autoSite.center, "could not place, reason=no block");
                return false;
            }
            autoShaftDryUnderPlacements++;
            autoShaftDryPlacePos = shaftPos;
            autoShaftDryPhase = ShaftDryPhase.UNDER_JUMP;
            return true;
        }
        return startShaftFlyBridgeRecovery(shaftPos);
    }

    private boolean dryAboveShaft() {
        if (autoExitShaft == null || autoShaftDryBridgePos == null) return false;
        BlockPos feet = mc.player.getBlockPos();
        return feet.getX() == autoExitShaft.x() && feet.getZ() == autoExitShaft.z()
            && feet.getY() >= autoShaftDryBridgePos.getY() + 1
            && !mc.player.isTouchingWater()
            && !mc.world.getFluidState(feet).isIn(FluidTags.WATER);
    }

    private void finishShaftFlyRerouteAttempt() {
        autoShaftFlyRerouteAttempts++;
        if (autoShaftFlyRerouteAttempts >= 3) {
            recordShaftFlyWaterFailure();
            enterState(AutoState.ASCEND, "shaft fly retries exhausted");
        } else {
            enterState(AutoState.SHAFT_ALIGN, "shaft fly retry " + autoShaftFlyRerouteAttempts);
        }
    }

    private void failShaftFlyWaterRecovery(String reason) {
        autoLog("SHAFT_FLY", autoSite.center, "could not place, reason=" + reason);
        clearActiveShaftDryRecovery();
        finishShaftFlyRerouteAttempt();
    }

    private void tickShaftFlyWaterRecovery() {
        if (stateTicks - autoShaftDryStartTick > 20 * 20) {
            failShaftFlyWaterRecovery("timeout");
            return;
        }

        Utils.setPressed(mc.options.sprintKey, false);
        if (tickShaftRecoveryVerify()) return;

        if (autoShaftDryPhase == ShaftDryPhase.UNDER_JUMP) {
            mc.player.setPitch(90.0f);
            Utils.setPressed(mc.options.forwardKey, false);
            if (mc.player.getY() >= autoShaftDryPlacePos.getY() + 1.0) {
                Utils.setPressed(mc.options.jumpKey, false);
                if (placeShaftRecoveryBlock(autoShaftDryPlacePos, "under")) {
                    autoShaftDryBridgePos = autoShaftDryPlacePos;
                    verifyShaftRecoveryBlock(autoShaftDryPlacePos, "under", ShaftDryPhase.SHAFT_MOVE);
                } else if (autoShaftDryPlaceHardFail && !startShaftFlyBridgeRecovery(autoShaftDryPlacePos)) {
                    clearActiveShaftDryRecovery();
                    finishShaftFlyRerouteAttempt();
                }
            } else if (stateTicks - autoShaftDryStartTick >= 20) {
                Utils.setPressed(mc.options.jumpKey, false);
                if (!startShaftFlyBridgeRecovery(autoShaftDryPlacePos)) {
                    clearActiveShaftDryRecovery();
                    finishShaftFlyRerouteAttempt();
                }
            } else {
                Utils.setPressed(mc.options.jumpKey, true);
            }
            return;
        }

        if (autoShaftDryPhase == ShaftDryPhase.SIDE_PLACE) {
            Utils.setPressed(mc.options.forwardKey, false);
            Utils.setPressed(mc.options.jumpKey, false);
            if (placeShaftRecoveryBlock(autoShaftDrySidePos, "side")) {
                verifyShaftRecoveryBlock(autoShaftDrySidePos, "side", ShaftDryPhase.SIDE_MOVE);
            } else if (autoShaftDryPlaceHardFail) {
                clearActiveShaftDryRecovery();
                finishShaftFlyRerouteAttempt();
            }
            return;
        }

        if (autoShaftDryPhase == ShaftDryPhase.SIDE_MOVE) {
            boolean centered = recenterDig(autoShaftDrySidePos.getX(), autoShaftDrySidePos.getZ(), 0.25);
            Utils.setPressed(mc.options.jumpKey, mc.player.horizontalCollision || mc.player.isTouchingWater());
            BlockPos feet = mc.player.getBlockPos();
            if (centered && feet.getX() == autoShaftDrySidePos.getX() && feet.getZ() == autoShaftDrySidePos.getZ()
                && feet.getY() >= autoShaftDrySidePos.getY() + 1) {
                releaseAllAutoKeys();
                autoShaftDryPhase = ShaftDryPhase.BRIDGE_PLACE;
            }
            return;
        }

        if (autoShaftDryPhase == ShaftDryPhase.BRIDGE_PLACE) {
            if (autoShaftDryBridgeBlocks >= 4) {
                failShaftFlyWaterRecovery("bridge limit");
                return;
            }
            if (placeShaftRecoveryBlock(autoShaftDryBridgePos, "bridge")) {
                verifyShaftRecoveryBlock(autoShaftDryBridgePos, "bridge", ShaftDryPhase.SHAFT_MOVE);
            } else if (autoShaftDryPlaceHardFail) {
                clearActiveShaftDryRecovery();
                finishShaftFlyRerouteAttempt();
            }
            return;
        }

        if (autoShaftDryPhase == ShaftDryPhase.SHAFT_MOVE) {
            boolean centered = recenterDig(autoExitShaft.x(), autoExitShaft.z(), 0.12);
            Utils.setPressed(mc.options.jumpKey, mc.player.horizontalCollision || mc.player.isTouchingWater());
            if (centered && dryAboveShaft()) {
                releaseAllAutoKeys();
                autoLog("SHAFT_FLY", autoSite.center, "dry above the shaft, retry");
                clearActiveShaftDryRecovery();
                enterState(AutoState.SHAFT_ALIGN, "dry above shaft");
            }
        }
    }

    private int shaftBlockedY(ExitShaft shaft) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int scanFromY = mc.player.getBlockPos().getY() + 1;
        if (Math.hypot((shaft.x() + 0.5) - mc.player.getX(), (shaft.z() + 0.5) - mc.player.getZ()) > 1.0) scanFromY = Math.max(scanFromY, shaft.bottomY());
        boolean topWaterOnly = shaftWaterOnlyAtTop(shaft);
        for (int y = scanFromY; y <= shaft.exitY(); y++) {
            cursor.set(shaft.x(), y, shaft.z());
            BlockState state = mc.world.getBlockState(cursor);
            if (!isOpenState(state, cursor) || (!topWaterOnly && state.getFluidState().isIn(FluidTags.WATER))) return y;
        }
        return -1;
    }

    private boolean shaftWaterOnlyAtTop(ExitShaft shaft) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = shaft.bottomY(); y <= shaft.bottomY() + 1; y++) {
            if (!mc.world.getFluidState(cursor.set(shaft.x(), y, shaft.z())).isEmpty()) return false;
        }
        return dryOpenToWaterAbove(shaft.x(), shaft.z(), shaft.bottomY(), shaft.exitY());
    }

    private void tickShaftAlign() {
        stateTicks++;

        if (stateTicks == 1) {
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            autoShaftAlignCenteredTicks = 0;
            autoShaftAlignRoomBlockPos = null;
            autoShaftAlignRoomBlockTick = 0;
            autoPausedTicks = 0;
            autoShaftReturning = false;
            autoShaftReturnStartTick = 0;
            autoShaftReturnRelaunches = 0;
        }

        if (waterTakeoffColumnFailed(autoExitShaft.x(), autoExitShaft.z())) {
            releaseAllAutoKeys();
            enterState(AutoState.ASCEND, "water takeoff column already failed");
            return;
        }

        if (autoShaftReturning && stateTicks - autoShaftReturnStartTick >= 600) {
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            autoShaftReturning = false;
            autoLog("SHAFT_ALIGN", autoSite.center, String.format("back to shaft gave up after %.1fs, relaunches=%d",
                (stateTicks - autoShaftReturnStartTick) / 20.0, autoShaftReturnRelaunches));
            autoShaftReturnRelaunches = 3;
        }

        if (handleShaftCombat()) {
            autoShaftAlignCenteredTicks = 0;
            return;
        }

        double alignDist = Math.hypot((autoExitShaft.x() + 0.5) - mc.player.getX(), (autoExitShaft.z() + 0.5) - mc.player.getZ());
        BlockPos shaftBottom = new BlockPos(autoExitShaft.x(), autoExitShaft.bottomY(), autoExitShaft.z());
        Goal shaftReturnGoal = new GoalComposite(new GoalNear(shaftBottom, 1), new GoalNear(shaftBottom.down(1), 1),
            new GoalNear(shaftBottom.down(2), 1), new GoalNear(shaftBottom.down(3), 1), new GoalNear(shaftBottom.down(4), 1));
        IBaritone alignBt = primaryBaritone();

        if (!autoShaftReturning && autoShaftReturnRelaunches < 3 && alignBt != null
            && (alignDist > 3.0 || (alignDist > 1.5 && stateTicks - autoPausedTicks > 60))) {
            autoShaftReturning = true;
            autoShaftReturnStartTick = stateTicks;
            releaseAllAutoKeys();
            alignBt.getCustomGoalProcess().setGoalAndPath(shaftReturnGoal);
            autoLog("SHAFT_ALIGN", autoSite.center, String.format("back to shaft with baritone dist=%.1f", alignDist));
        }

        if (autoShaftReturning) {
            if (alignBt != null && alignDist <= 1.5) {
                alignBt.getPathingBehavior().cancelEverything();
                releaseAllAutoKeys();
                autoShaftReturning = false;
                autoShaftAlignCenteredTicks = 0;
            } else {
                boolean gaveUp = alignBt == null || (!alignBt.getPathingBehavior().isPathing() && !alignBt.getCustomGoalProcess().isActive());
                if (gaveUp && (alignBt == null || autoShaftReturnRelaunches >= 3)) {
                    if (alignBt != null) alignBt.getPathingBehavior().cancelEverything();
                    releaseAllAutoKeys();
                    autoShaftReturning = false;
                    autoLog("SHAFT_ALIGN", autoSite.center, String.format("back to shaft gave up after %.1fs, relaunches=%d",
                        (stateTicks - autoShaftReturnStartTick) / 20.0, autoShaftReturnRelaunches));
                    autoShaftReturnRelaunches = 3;
                } else {
                    if (gaveUp) {
                        autoShaftReturnRelaunches++;
                        releaseAllAutoKeys();
                        alignBt.getCustomGoalProcess().setGoalAndPath(shaftReturnGoal);
                        autoLog("SHAFT_ALIGN", autoSite.center, "back to shaft relaunch " + autoShaftReturnRelaunches);
                    }
                    autoPausedTicks++;
                    return;
                }
            }
        }

        BlockPos alignFloor = new BlockPos(autoExitShaft.x(), mc.player.getBlockPos().getY() - 1, autoExitShaft.z());
        Utils.setPressed(mc.options.sneakKey, alignDist > 0.12 && alignDist < 0.6 && mc.player.isOnGround() && !mc.player.isTouchingWater()
            && mc.player.getBlockPos().getY() <= autoExitShaft.bottomY() && !isOpenState(mc.world.getBlockState(alignFloor), alignFloor));
        boolean centered = recenterDig(autoExitShaft.x(), autoExitShaft.z(), 0.12);
        Utils.setPressed(mc.options.jumpKey, !centered && mc.player.horizontalCollision && !mc.player.isTouchingWater());
        if (stateTicks % 40 == 0) {
            autoLog("SHAFT_ALIGN", autoSite.center, String.format("align x=%.2f z=%.2f alignDist=%.2f feetY=%.1f bottomY=%d",
                mc.player.getX(), mc.player.getZ(), alignDist, mc.player.getY(), autoExitShaft.bottomY()));
        }
        double speedH = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
        autoShaftAlignCenteredTicks = (centered && speedH < 0.02) ? autoShaftAlignCenteredTicks + 1 : 0;

        int blockedY = shaftBlockedY(autoExitShaft);
        if (blockedY != -1 && !mc.player.isSubmergedInWater() && blockedY <= mc.player.getBlockPos().getY() + 1
            && mc.world.getFluidState(new BlockPos(autoExitShaft.x(), blockedY, autoExitShaft.z())).isIn(FluidTags.WATER)
            && dryOpenColumn(autoExitShaft.x(), autoExitShaft.z(), blockedY + 1, autoExitShaft.exitY())) {
            blockedY = -1;
        }
        if (blockedY != -1) {
            BlockPos blockedPos = new BlockPos(autoExitShaft.x(), blockedY, autoExitShaft.z());
            BlockState blockedState = mc.world.getBlockState(blockedPos);
            boolean blockedByWater = blockedState.getFluidState().isIn(FluidTags.WATER);
            autoLog("SHAFT_ALIGN", autoSite.center, "shaft blocked at y=" + blockedY);
            releaseAllAutoKeys();
            if (!blockedByWater && shaftRoomBlockedClearable(blockedPos, blockedState)) {
                if (!blockedPos.equals(autoShaftAlignRoomBlockPos)) {
                    autoShaftAlignRoomBlockPos = blockedPos;
                    autoShaftAlignRoomBlockTick = stateTicks;
                } else if (stateTicks - autoShaftAlignRoomBlockTick >= 100) {
                    autoShaftAlignRoomBlockPos = null;
                    autoShaftAlignRoomBlockTick = 0;
                    autoLog("SHAFT_ALIGN", autoSite.center, "room block not broken");
                    enterState(AutoState.ASCEND, "shaft blocked");
                    return;
                }
                if (autoMineTick("SHAFT_ALIGN", blockedPos, false)) {
                    autoShaftAlignCenteredTicks = 0;
                    autoShaftAlignRoomBlockPos = null;
                    autoShaftAlignRoomBlockTick = 0;
                }
                return;
            }
            autoShaftAlignRoomBlockPos = null;
            autoShaftAlignRoomBlockTick = 0;
            if (blockedByWater) {
                enterState(AutoState.SWIM_UP, "shaft blocked by water");
            } else {
                ExitShaft lowered = new ExitShaft(autoExitShaft.x(), autoExitShaft.z(), autoExitShaft.bottomY(), blockedY - 3, autoExitShaft.water(), true, autoExitShaft.entry());
                boolean pivot = blockedY - 4 > autoExitShaft.bottomY() && shaftExitHasPivotEscape(lowered);
                if (!pivot && blockedY - 3 > autoExitShaft.bottomY()) {
                    lowered = new ExitShaft(autoExitShaft.x(), autoExitShaft.z(), autoExitShaft.bottomY(), blockedY - 2, autoExitShaft.water(), true, autoExitShaft.entry());
                    pivot = shaftExitHasPivotEscape(lowered);
                }
                if (pivot) {
                    autoExitShaft = lowered;
                    autoShaftAlignCenteredTicks = 0;
                    autoLog("SHAFT_ALIGN", autoSite.center, String.format("shaft blocked at y=%d, side escape below it, shaft fly with pivot (exitY=%d)", blockedY, lowered.exitY()));
                    return;
                }
                enterState(AutoState.ASCEND, "shaft blocked");
            }
            return;
        }

        if ((stateTicks - autoPausedTicks) > 20 * 20) {
            releaseAllAutoKeys();
            enterState(AutoState.ASCEND, "shaft align timeout");
            return;
        }

        if (autoShaftAlignCenteredTicks < 5 && !(centered && !mc.player.isSubmergedInWater() && shaftFlyWaterAtBottom())) return;

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

    private boolean shaftRoomBlockedClearable(BlockPos pos, BlockState state) {
        if (autoExitShaft == null) return false;
        if (pos.getY() < autoExitShaft.bottomY() || pos.getY() > autoExitShaft.bottomY() + 5) return false;
        if (pos.getX() != autoExitShaft.x() || pos.getZ() != autoExitShaft.z()) return false;
        if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) > 4.2) return false;
        return roomColumnClearable(pos, state);
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

    private boolean shaftExitHasPivotEscape(ExitShaft s) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int obstacleY = Integer.MIN_VALUE;
        for (int y = s.exitY() + 1; y <= s.exitY() + SKY_CHECK_HEIGHT; y++) {
            cursor.set(s.x(), y, s.z());
            if (!isOpenState(mc.world.getBlockState(cursor), cursor)) {
                obstacleY = y;
                break;
            }
        }

        if (obstacleY == Integer.MIN_VALUE) return true;

        if (obstacleY - 2 < s.exitY()) {
            if (autoSite != null && shaftPivotFailureLogged.add(autoSite.center)) {
                autoLog("SHAFT_FLY", autoSite.center, String.format("no pivot: obstacle y=%d exitY=%d too close", obstacleY, s.exitY()));
            }
            return false;
        }

        int minFeetY = Math.max(obstacleY - 4, s.exitY());
        int maxFeetY = obstacleY - 2;
        BlockPos lowestEscape = null;
        for (int h = minFeetY; h <= maxFeetY; h++) {
            BlockPos escape = findShaftPivotEscape(s.x(), h, s.z());
            if (escape == null) {
                if (autoSite != null && shaftPivotFailureLogged.add(autoSite.center)) {
                    autoLog("SHAFT_FLY", autoSite.center, String.format("no pivot: obstacle y=%d exitY=%d no escape at feet y=%d (band %d..%d)",
                        obstacleY, s.exitY(), h, minFeetY, maxFeetY));
                }
                return false;
            }
            if (lowestEscape == null) lowestEscape = escape;
        }

        autoLog("SHAFT_FLY", autoSite.center, "sky blocked at y=" + obstacleY + " escape " + lowestEscape.getX() + "," + lowestEscape.getZ() + ", shaft fly anyway");
        return true;
    }

    private void tickShaftFly() {
        if (waitingForElytra("SHAFT_FLY")) return;
        stateTicks++;

        if (autoShaftDryPhase != ShaftDryPhase.NONE) {
            tickShaftFlyWaterRecovery();
            return;
        }

        if (stateTicks == 1) {
            double centerDist = Math.hypot((autoExitShaft.x() + 0.5) - mc.player.getX(), (autoExitShaft.z() + 0.5) - mc.player.getZ());
            if (centerDist > 0.2) {
                releaseAllAutoKeys();
                enterState(AutoState.SHAFT_ALIGN, String.format("realign before takeoff dist=%.2f", centerDist));
                return;
            }
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
            autoShaftFlyPivotCollisionTicks = 0;
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
            recordShaftFlyWaterFailure();
            enterState(AutoState.ASCEND, "shaft fly timeout");
            return;
        }

        if (autoShaftFlyReroute) {
            autoShaftFlyReroute = false;
            if (takeoff.isActive()) takeoff.cancel();
            releaseAllAutoKeys();
            if (startShaftFlyWaterRecovery()) return;
            finishShaftFlyRerouteAttempt();
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

            if (!autoShaftFlyPivot && y >= autoExitShaft.exitY() + 4) {
                mc.player.setPitch(-90.0f);
                autoLog("SHAFT_FLY", autoSite.center, "out of shaft");
                if (takeoff.isActive()) takeoff.cancel();
                releaseAllAutoKeys();
                autoClimbTargetY = autoExitShaft.exitY() + climbHeight.get();
                enterState(AutoState.CLIMB, "");
                return;
            }

            if (!onGround && !gliding && mc.player.isTouchingWater() && y > autoExitShaft.bottomY() + 2) {
                if (takeoff.isActive()) takeoff.cancel();
                releaseAllAutoKeys();
                autoLog("SHAFT_FLY", autoSite.center, String.format("lost flight in the water above y=%.1f, swimming up", y));
                enterState(AutoState.SWIM_UP, "lost flight in top water");
                return;
            }

            double landedShaftDist = Math.hypot((autoExitShaft.x() + 0.5) - mc.player.getX(), (autoExitShaft.z() + 0.5) - mc.player.getZ());
            if (onGround && landedShaftDist > 0.7 && y > autoExitShaft.bottomY() + 4) {
                if (takeoff.isActive()) takeoff.cancel();
                releaseAllAutoKeys();
                enterState(AutoState.ASCEND, "landed out of the shaft");
                return;
            }

            if (onGround || (!gliding && mc.player.isTouchingWater())) {
                if (takeoff.isActive()) takeoff.cancel();
                releaseAllAutoKeys();
                autoShaftFlyAttempts++;
                if (autoShaftFlyAttempts >= 2) {
                    recordShaftFlyWaterFailure();
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

            int feetY = mc.player.getBlockPos().getY();
            int px = mc.player.getBlockPos().getX();
            int pz = mc.player.getBlockPos().getZ();

            if (autoShaftFlyPivot) {
                int topAbove = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, px, pz);
                if (topAbove <= feetY) {
                    autoShaftFlyPivot = false;
                    autoLog("SHAFT_FLY", autoSite.center, "pivot done");
                    autoShaftFlyLastRocketTick = stateTicks - 20;
                    autoShaftFlyStuck = false;
                    autoShaftFlyStuckSinceTick = 0;
                    autoShaftFlyStuckWindowY = y;
                    autoShaftFlyStuckWindowTick = stateTicks;
                }
            }

            if (!autoShaftFlyPivot) {
                boolean blockedAboveColumn = false;
                int escapeFeetY = feetY;
                if (gliding && y >= autoExitShaft.exitY()) {
                    BlockPos.Mutable aboveCursor = new BlockPos.Mutable();
                    boolean columnBlocked = false;
                    for (int dy = 1; dy <= 4; dy++) {
                        aboveCursor.set(px, feetY + dy, pz);
                        if (!isOpenState(mc.world.getBlockState(aboveCursor), aboveCursor)) {
                            columnBlocked = true;
                            escapeFeetY = Math.min(feetY, feetY + dy - 2);
                            break;
                        }
                    }
                    blockedAboveColumn = columnBlocked || autoShaftFlySlowClimb;
                }

                if (blockedAboveColumn) {
                    if (!autoShaftFlyPivotNoEscapeLogged) {
                        BlockPos escape = findShaftPivotEscape(px, escapeFeetY, pz);
                        if (escape != null) {
                            autoShaftFlyPivot = true;
                            autoShaftFlyPivotCollisionTicks = 0;
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
                if (mc.player.horizontalCollision) {
                    autoShaftFlyPivotCollisionTicks++;
                } else {
                    autoShaftFlyPivotCollisionTicks = 0;
                }
                if (autoShaftFlyPivotCollisionTicks >= 5) {
                    autoShaftFlyPivot = false;
                    autoLog("SHAFT_FLY", autoSite.center, "pivot blocked by a wall, ascend");
                    if (takeoff.isActive()) takeoff.cancel();
                    releaseAllAutoKeys();
                    enterState(AutoState.ASCEND, "pivot blocked by a wall");
                    return;
                }
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
                            recordShaftFlyWaterFailure();
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
        if (isShipwreck() && autoShipwreckRun != null) {
            if (autoShipwreckRun.returning() && resumeShipwreckAtSurface()) return;
        }
        stateTicks++;

        if (stateTicks == 1) {
            autoSwimUpLastY = mc.player.getY();
            autoSwimUpProgressPos = mc.player.getEntityPos();
            autoSwimUpOnColumn = false;
            autoSwimUpProgressTick = 0;
            autoSwimUpOutOfWaterTicks = 0;
            autoSwimUpBreakPos = null;
            autoSwimUpBreakLabel = null;
            autoExitPathCell = null;
            autoExitPathStep = 0;
            autoSwimUpMouthY = Integer.MIN_VALUE;
        }

        if (stateTicks - autoSwimUpProgressTick > 20 * 20) {
            releaseAllAutoKeys();
            enterState(AutoState.ASCEND, "swim up timeout");
            return;
        }

        int swimUpX = autoExitShaft != null ? autoExitShaft.x() : autoColumn.x();
        int swimUpZ = autoExitShaft != null ? autoExitShaft.z() : autoColumn.z();
        int swimUpExitY = autoExitShaft != null ? autoExitShaft.exitY() : autoColumn.exitY();
        BlockPos swimUpEntry = autoExitShaft != null ? autoExitShaft.entry() : null;
        double swimUpColumnDist = Math.hypot((swimUpX + 0.5) - mc.player.getX(), (swimUpZ + 0.5) - mc.player.getZ());
        boolean onSwimUpColumn = swimUpColumnDist <= 0.7;
        if (autoSwimUpMouthY == Integer.MIN_VALUE) {
            autoSwimUpMouthY = shaftMouthY(swimUpX, swimUpZ, autoExitShaft != null ? autoExitShaft.bottomY() : autoColumn.startY(), swimUpExitY);
        }
        boolean swimUpOpenWater = !isShipwreck() && mc.player.isSubmergedInWater()
            && mc.player.getBlockY() >= autoSwimUpMouthY && waterAllAround(mc.player.getBlockPos());

        if (!swimUpOpenWater) navigateExitColumn(swimUpX, swimUpZ, 0.2, swimUpEntry, true);
        boolean fastSwimUp = (onSwimUpColumn || swimUpOpenWater) && mc.player.isSubmergedInWater()
            && autoSwimUpBreakPos == null && autoExitBreakPos == null
            && autoExitTrailBreakPos == null && !autoExitStuckBreaking
            && mc.player.getHungerManager().getFoodLevel() > 6;
        if (fastSwimUp) {
            BlockPos feet = mc.player.getBlockPos();
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighbor = feet.offset(dir);
                BlockState neighborState = mc.world.getBlockState(neighbor);
                if (isOpenState(neighborState, neighbor) && !neighborState.getFluidState().isIn(FluidTags.WATER)) {
                    fastSwimUp = false;
                    break;
                }
            }
        }
        if (fastSwimUp) {
            mc.player.setPitch(-90.0f);
            Utils.setPressed(mc.options.forwardKey, true);
            Utils.setPressed(mc.options.sprintKey, true);
            Utils.setPressed(mc.options.sneakKey, false);
            Utils.setPressed(mc.options.jumpKey, true);
        } else {
            Utils.setPressed(mc.options.sprintKey, false);
        }
        if (!fastSwimUp && autoSwimUpBreakPos == null && !isShipwreck() && mc.player.isTouchingWater()) {
            if (onSwimUpColumn) {
                mc.player.setPitch(-90.0f);
            } else {
                int entryY = autoExitShaft != null ? shaftOpenEntryY(autoExitShaft) : swimUpExitY - 1;
                double entryDy = Math.max(0.0, (entryY + 0.5) - mc.player.getEyeY());
                mc.player.setPitch(MathHelper.clamp((float) -Math.toDegrees(Math.atan2(entryDy, swimUpColumnDist)), -90.0f, 90.0f));
            }
        }

        double swimUpY = mc.player.getY();
        Vec3d swimUpPos = mc.player.getEntityPos();
        boolean walkingProgress = !onSwimUpColumn && !swimUpOpenWater && Math.hypot(swimUpPos.x - autoSwimUpProgressPos.x,
            swimUpPos.z - autoSwimUpProgressPos.z) >= 0.5;
        if (swimUpY >= autoSwimUpLastY + 0.5 || walkingProgress || (onSwimUpColumn && !autoSwimUpOnColumn)) {
            autoSwimUpLastY = Math.max(autoSwimUpLastY, swimUpY);
            autoSwimUpProgressPos = swimUpPos;
            autoSwimUpProgressTick = stateTicks;
        }
        autoSwimUpOnColumn = onSwimUpColumn;

        boolean swimUpAboveCheck = (mc.player.isSubmergedInWater() || onSwimUpColumn) && stateTicks - autoSwimUpProgressTick >= 10;
        boolean swimUpSideCheck = !onSwimUpColumn && mc.player.horizontalCollision && stateTicks - autoSwimUpProgressTick >= 40;
        if (swimUpAboveCheck || swimUpSideCheck) {
            BlockPos target = null;
            if (swimUpAboveCheck) {
                BlockPos swimUpFeet = mc.player.getBlockPos();
                for (int up = 2; up >= 1; up--) {
                    BlockPos p = new BlockPos(swimUpFeet.getX(), swimUpFeet.getY() + up, swimUpFeet.getZ());
                    BlockState pState = mc.world.getBlockState(p);
                    boolean solid = !(isShipwreck() ? isShipwreckPassable(pState, p) : isOpenState(pState, p)) && !pState.getFluidState().isIn(FluidTags.LAVA);
                    if (solid && !isLavaNearShaft(p, p.up())) {
                        target = p;
                        break;
                    }
                }
            }
            boolean sideTarget = false;
            if (target == null && swimUpSideCheck) {
                int keyX = autoExitPathCell != null ? autoExitPathCell.getX() : swimUpX;
                int keyZ = autoExitPathCell != null ? autoExitPathCell.getZ() : swimUpZ;
                target = findWalkStuckObstacle(keyX, keyZ);
                sideTarget = target != null;
            }
            if (target != null && (autoSwimUpBreakPos == null || !autoSwimUpBreakPos.equals(target))) {
                autoSwimUpBreakPos = target;
                autoSwimUpBreakLabel = Registries.BLOCK.getId(mc.world.getBlockState(target).getBlock()).getPath();
            } else if (target == null) {
                autoSwimUpBreakPos = null;
                autoSwimUpBreakLabel = null;
            }
            if (autoSwimUpBreakPos != null) {
                if (sideTarget) Utils.setPressed(mc.options.jumpKey, false);
                if (autoMineTick("SWIM_UP", autoSwimUpBreakPos, false)) {
                    autoLog("SWIM_UP", autoSite.center, String.format("%s %d,%d,%d block=%s", sideTarget ? "path broken" : "broken",
                        autoSwimUpBreakPos.getX(), autoSwimUpBreakPos.getY(), autoSwimUpBreakPos.getZ(), autoSwimUpBreakLabel));
                    autoSwimUpBreakPos = null;
                    autoSwimUpBreakLabel = null;
                    autoSwimUpProgressTick = stateTicks;
                }
            }
        } else {
            autoSwimUpBreakPos = null;
            autoSwimUpBreakLabel = null;
        }

        if (mc.player.getAir() < 40 && stateTicks % 20 == 0) {
            autoLog("SWIM_UP", autoSite.center,
                String.format("low air y=%.1f air=%d", mc.player.getY(), mc.player.getAir()));
        }

        if (stateTicks % 20 == 0) {
            autoLog("SWIM_UP", autoSite.center, String.format("x=%.1f y=%.1f z=%.1f submerged=%b touchingWater=%b sprint=%b swimming=%b fast=%b pitch=%.0f vy=%.3f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.isSubmergedInWater(), mc.player.isTouchingWater(),
                mc.player.isSprinting(), mc.player.isSwimming(), fastSwimUp, mc.player.getPitch(), mc.player.getVelocity().y));
        }

        boolean surfaced = !mc.player.isSubmergedInWater() && mc.player.getY() >= swimUpExitY - 1;
        if (surfaced) {
            releaseAllAutoKeys();
            autoLog("SWIM_UP", autoSite.center, String.format("surfaced x=%.1f z=%.1f", mc.player.getX(), mc.player.getZ()));
            if ((autoSoloMode || autoAreaLoaderWasOn) && waterTakeoffColumnFailed(mc.player.getBlockX(), mc.player.getBlockZ())) {
                enterState(AutoState.ASCEND, "water takeoff column already failed");
            } else if (autoSoloMode) {
                autoWaterJumpAttempts = 0;
                autoWaterJumpVertical = false;
                enterState(AutoState.WATER_JUMP, "solo surfaced");
            } else if (!autoAreaLoaderWasOn) {
                enterState(AutoState.OBSIDIAN_PLATFORM, "no flight module, platform only");
            } else {
                autoWaterJumpAttempts = 0;
                autoWaterJumpVertical = false;
                enterState(AutoState.WATER_JUMP, "surfaced");
            }
            return;
        }

        boolean dryBelowWater = onSwimUpColumn && !mc.player.isSubmergedInWater()
            && (!mc.player.isTouchingWater() || swimUpColumnDist <= 0.2)
            && mc.world.getFluidState(mc.player.getBlockPos().up()).isEmpty()
            && swimUpY < swimUpExitY - 1
            && dryOpenToWaterAbove(swimUpX, swimUpZ, mc.player.getBlockPos().getY() + 1, swimUpExitY);
        if (dryBelowWater && (autoAreaLoaderWasOn || autoSoloMode) && autoShaftFly.get() && autoExitShaft != null
            && shaftWaterOnlyAtTop(autoExitShaft) && countRockets() >= 4 && !isShipwreck()
            && !waterTakeoffColumnFailed(swimUpX, swimUpZ)
            && (!autoExitShaft.skyBlocked() || shaftExitHasPivotEscape(autoExitShaft))) {
            releaseAllAutoKeys();
            autoShaftFlyAttempts = 0;
            autoShaftFlyRerouteAttempts = 0;
            resetShaftDryRecovery();
            autoLog("SWIM_UP", autoSite.center, "dry column below water, shaft-fly through the water");
            enterState(AutoState.SHAFT_ALIGN, "dry column below water, shaft-fly");
            return;
        }
        boolean waitForFallingWater = dryBelowWater && !InvUtils.find(Items.OBSIDIAN).found();
        if (waitForFallingWater && stateTicks % 40 == 0) {
            autoLog("SWIM_UP", autoSite.center, "dry column below water, no throwaway, waiting on the column for the water");
        }
        if (dryBelowWater && !waitForFallingWater) {
            releaseAllAutoKeys();
            autoSelfAscendForcePillar = true;
            autoLog("SWIM_UP", autoSite.center, "dry column below water, self ascend pillar");
            enterState(AutoState.SELF_ASCEND, "dry column below water");
            return;
        }

        boolean poolUnderDryShaft = (autoAreaLoaderWasOn || autoSoloMode) && stateTicks > 20 && !mc.player.isSubmergedInWater()
            && mc.player.getY() < swimUpExitY - 3
            && mc.player.getBlockX() == swimUpX && mc.player.getBlockZ() == swimUpZ
            && dryOpenColumn(swimUpX, swimUpZ, mc.player.getBlockPos().getY() + 1, swimUpExitY);
        if (poolUnderDryShaft) {
            releaseAllAutoKeys();
            if (waterTakeoffColumnFailed(swimUpX, swimUpZ)) {
                enterState(AutoState.ASCEND, "water takeoff column already failed");
                return;
            }
            boolean shaftFlyEligible = autoShaftFly.get()
                && autoExitShaft != null
                && mc.player.getBlockPos().getY() <= autoExitShaft.bottomY() + 1
                && (!mc.player.isTouchingWater() || autoExitShaft.skyBlocked())
                && (!autoExitShaft.skyBlocked() || shaftExitHasPivotEscape(autoExitShaft))
                && countRockets() >= 4;
            if (shaftFlyEligible) {
                resetShaftDryRecovery();
                enterState(AutoState.SHAFT_ALIGN, "pool under dry shaft, shaft-fly");
            } else {
                autoWaterJumpAttempts = 0;
                autoWaterJumpVertical = true;
                enterState(AutoState.WATER_JUMP, "pool under dry shaft");
            }
            return;
        }

        if (!waitForFallingWater && onSwimUpColumn && !mc.player.isSubmergedInWater() && mc.player.getY() < swimUpExitY - 1
            && stateTicks - autoSwimUpProgressTick >= 60) {
            releaseAllAutoKeys();
            autoLog("SWIM_UP", autoSite.center, "surface stall, ascend");
            enterState(AutoState.ASCEND, "surface stall");
            return;
        }

        if (!mc.player.isTouchingWater() && !mc.player.isSubmergedInWater() && mc.player.getY() < swimUpExitY - 3
            && swimUpColumnDist > 1.5 && stateTicks - autoSwimUpProgressTick >= 40) {
            autoSwimUpOutOfWaterTicks++;
        } else {
            autoSwimUpOutOfWaterTicks = 0;
        }
        if (autoSwimUpOutOfWaterTicks >= 100) {
            releaseAllAutoKeys();
            autoLog("SWIM_UP", autoSite.center, "swim up out of water");
            enterState(AutoState.ASCEND, "swim up out of water");
        }
    }

    private final Map<BlockPos, Boolean> monumentCache = new HashMap<>();

    private boolean nearOceanMonument(BlockPos center) {
        Boolean cached = monumentCache.get(center);
        if (cached != null) return cached;
        int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
        boolean allLoaded = true;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
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

    private static final int NO_JUNGLE_TEMPLE = Integer.MAX_VALUE;
    private final Map<BlockPos, Integer> jungleTempleLowestYCache = new HashMap<>();

    private boolean jungleTempleChunksLoaded(BlockPos center) {
        int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!mc.world.getChunkManager().isChunkLoaded(ccx + dx, ccz + dz)) return false;
            }
        }
        return true;
    }

    private Integer jungleTempleLowestY(BlockPos center) {
        Integer cached = jungleTempleLowestYCache.get(center);
        if (cached != null) return cached == NO_JUNGLE_TEMPLE ? null : cached;

        int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
        boolean allLoaded = true;
        int lowest = NO_JUNGLE_TEMPLE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ccx + dx, ccz + dz);
                if (chunk == null) {
                    allLoaded = false;
                    continue;
                }
                ChunkSection[] sections = chunk.getSectionArray();
                int bottomY = chunk.getBottomY();
                for (int si = 0; si < sections.length; si++) {
                    ChunkSection section = sections[si];
                    if (section == null || section.isEmpty()) continue;
                    if (!section.hasAny(st -> st.isOf(Blocks.STICKY_PISTON) || st.isOf(Blocks.TRIPWIRE_HOOK))) continue;

                    int sectionBaseY = bottomY + si * 16;
                    for (int ly = 0; ly < 16 && sectionBaseY + ly < lowest; ly++) {
                        for (int lx = 0; lx < 16; lx++) {
                            for (int lz = 0; lz < 16; lz++) {
                                Block b = section.getBlockState(lx, ly, lz).getBlock();
                                if (b == Blocks.STICKY_PISTON || b == Blocks.TRIPWIRE_HOOK) {
                                    lowest = Math.min(lowest, sectionBaseY + ly);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (allLoaded) jungleTempleLowestYCache.put(center, lowest);
        return lowest == NO_JUNGLE_TEMPLE ? null : lowest;
    }

    private boolean isJunglePyramidSite(Site site) {
        if (site.hasSpawner) return false;
        Integer lowestY = jungleTempleLowestY(site.center);
        if (lowestY == null) return false;
        return site.room.maxY() >= lowestY - 3;
    }

    private int shaftOpenEntryY(ExitShaft s) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int y = s.exitY() - 1;
        if (!isOpenState(mc.world.getBlockState(cursor.set(s.x(), y, s.z())), cursor)) return s.bottomY();
        while (y - 1 >= s.bottomY() && isOpenState(mc.world.getBlockState(cursor.set(s.x(), y - 1, s.z())), cursor)) y--;
        return y;
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

    private int shaftMouthY(int x, int z, int bottomY, int exitY) {
        int highestFloorY = Integer.MIN_VALUE;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            int nx = x + dir.getOffsetX();
            int nz = z + dir.getOffsetZ();
            for (int y = exitY - 1; y >= bottomY; y--) {
                cursor.set(nx, y, nz);
                BlockState state = mc.world.getBlockState(cursor);
                if (state.getFluidState().isIn(FluidTags.WATER) || isOpenState(state, cursor)) continue;
                if (state.getFluidState().isEmpty()) highestFloorY = Math.max(highestFloorY, y);
                break;
            }
        }
        return highestFloorY != Integer.MIN_VALUE ? highestFloorY + 1 : bottomY;
    }

    private boolean waterAllAround(BlockPos feet) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (!mc.world.getFluidState(feet.offset(dir)).isIn(FluidTags.WATER)) return false;
            if (!mc.world.getFluidState(feet.up().offset(dir)).isIn(FluidTags.WATER)) return false;
        }
        return true;
    }

    private boolean dryOpenToWaterAbove(int x, int z, int fromY, int toY) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        boolean foundWater = false;
        for (int y = fromY; y <= toY && y <= fromY + 320; y++) {
            cursor.set(x, y, z);
            BlockState state = mc.world.getBlockState(cursor);
            if (state.getFluidState().isIn(FluidTags.LAVA)) return false;
            if (state.getFluidState().isIn(FluidTags.WATER)) {
                foundWater = true;
                break;
            }
            if (!isOpenState(state, cursor) || !state.getFluidState().isEmpty()) return false;
        }
        return foundWater;
    }

    private boolean takeoffHasClimbable() {
        if (mc.player.isClimbing()) return true;
        for (int dy = 0; dy <= 3; dy++) {
            if (mc.world.getBlockState(mc.player.getBlockPos().up(dy)).isIn(BlockTags.CLIMBABLE)) return true;
        }
        return false;
    }

    private void faceTakeoffClimbableWall() {
        for (int dy = 0; dy <= 3; dy++) {
            BlockState block = mc.world.getBlockState(mc.player.getBlockPos().up(dy));
            if (block.isOf(Blocks.VINE)) {
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    if (block.get(net.minecraft.block.VineBlock.getFacingProperty(dir))) {
                        mc.player.setYaw(dir.getPositiveHorizontalDegrees());
                        return;
                    }
                }
            } else if (block.isOf(Blocks.LADDER)) {
                mc.player.setYaw(block.get(net.minecraft.block.LadderBlock.FACING).getOpposite().getPositiveHorizontalDegrees());
                return;
            }
        }
    }

    private void tickWaterJump() {
        if (stateTicks == 0) {
            if (waterTakeoffColumnFailed(mc.player.getBlockX(), mc.player.getBlockZ())) {
                releaseAllAutoKeys();
                enterState(AutoState.ASCEND, "water takeoff column already failed");
                return;
            }
            if (autoAscendFailedPoints.contains(mc.player.getBlockPos())) {
                releaseAllAutoKeys();
                enterState(AutoState.TAKEOFF_SPOT, "water jump point already failed");
                return;
            }
            autoWaterJumpClimbPos = takeoffHasClimbable() ? mc.player.getBlockPos().toImmutable() : null;
            autoWaterJumpClimbStartMs = System.currentTimeMillis();
        }
        if (autoWaterJumpClimbPos != null) {
            boolean grounded = mc.player.isOnGround() && !mc.player.isTouchingWater();
            if (grounded || System.currentTimeMillis() - autoWaterJumpClimbStartMs >= 5_000L) {
                if (!grounded || takeoffHasClimbable()) {
                    autoAscendFailedPoints.add(autoWaterJumpClimbPos);
                    autoAscendFailedPoints.add(mc.player.getBlockPos().toImmutable());
                }
                autoWaterJumpClimbPos = null;
                releaseAllAutoKeys();
                enterState(AutoState.TAKEOFF_SPOT, grounded ? "climbable exit grounded" : "climbable exit timeout");
            } else {
                stateTicks++;
                faceTakeoffClimbableWall();
                mc.player.setPitch(0.0f);
                Utils.setPressed(mc.options.sneakKey, false);
                Utils.setPressed(mc.options.sprintKey, false);
                Utils.setPressed(mc.options.forwardKey, true);
                Utils.setPressed(mc.options.jumpKey, true);
            }
            return;
        }
        if (waitingForElytra("WATER_JUMP")) return;
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
            if (autoWaterJumpVertical && autoExitShaft != null
                && !recenterDig(autoExitShaft.x(), autoExitShaft.z(), 0.12)) {
                Utils.setPressed(mc.options.jumpKey, true);
                return;
            }
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
            autoClimbRedeployTries = 0;
            autoClimbRedeployTick = 0;
            autoClimbStuckAttempts = 0;
            autoClimbStuck = false;
            autoClimbWindowY = mc.player.getY();
            autoClimbWindowTick = stateTicks + 20;
        }

        if (autoClimbStuck) {
            if (!mc.player.isGliding() && mc.player.isTouchingWater()) {
                recordWaterTakeoffFailure(mc.player.getBlockX(), mc.player.getBlockZ());
            }
            mc.player.setPitch(20.0f);
            if (stateTicks - autoClimbStuckStartTick >= 20 * 10) {
                releaseAllAutoKeys();
                enterState(AutoState.ASCEND, "climb stuck");
            } else if (!mc.player.isGliding()) {
                releaseAllAutoKeys();
                if (mc.player.isTouchingWater() && autoClimbStuckWaterReturns < 3
                    && !waterTakeoffColumnFailed(mc.player.getBlockX(), mc.player.getBlockZ())) {
                    autoClimbStuckWaterReturns++;
                    int shaftX = autoExitShaft != null ? autoExitShaft.x() : autoColumn.x();
                    int shaftZ = autoExitShaft != null ? autoExitShaft.z() : autoColumn.z();
                    if (mc.player.getBlockX() == shaftX && mc.player.getBlockZ() == shaftZ) {
                        enterState(AutoState.SWIM_UP, "climb stuck in water");
                    } else if (isTrueSkyHere()) {
                        autoWaterJumpAttempts = 0;
                        autoWaterJumpVertical = false;
                        enterState(AutoState.WATER_JUMP, "climb stuck in water outside shaft");
                    } else {
                        enterState(AutoState.SWIM_UP, "climb stuck in water");
                    }
                } else {
                    enterState(AutoState.ASCEND, "climb stuck");
                }
            }
            return;
        }

        mc.player.setPitch(-90.0f);

        if (!mc.player.isGliding()) {
            if (!(mc.player.isOnGround() || mc.player.isTouchingWater())) {
                if (autoClimbRedeployTries == 0 && autoClimbRedeployTick == 0) {
                    autoClimbRedeployTick = -1000;
                    autoLog("CLIMB", autoSite.center, "glide lost in air y=" + String.format("%.1f", mc.player.getY())
                        + " vy=" + String.format("%.2f", mc.player.getVelocity().y)
                        + " climbing=" + mc.player.isClimbing()
                        + " feet=" + mc.world.getBlockState(mc.player.getBlockPos()).getBlock()
                        + " chest=" + mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem()
                        + " hurtTime=" + mc.player.hurtTime);
                }
                if (mc.player.isClimbing() && stateTicks <= 20 * 3) {
                    Utils.setPressed(mc.options.jumpKey, true);
                    return;
                }
                boolean redeploy = autoClimbRedeployTries < 8 && stateTicks - autoClimbRedeployTick >= 4
                    && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
                Utils.setPressed(mc.options.jumpKey, redeploy);
                if (redeploy) {
                    autoClimbRedeployTries++;
                    autoClimbRedeployTick = stateTicks;
                }
                return;
            }
            Utils.setPressed(mc.options.jumpKey, false);
            if (autoSoloMode) {
                autoLog("CLIMB", autoSite.center, "solo lost glide");
                if (mc.player.isTouchingWater() && autoClimbLostGlideRetries < 4) {
                    autoClimbLostGlideRetries++;
                    autoWaterJumpAttempts = 0;
                    autoWaterJumpVertical = false;
                    enterState(AutoState.WATER_JUMP, "solo climb lost glide in water");
                } else {
                    enterState(AutoState.ASCEND, "solo climb lost glide");
                }
                return;
            }
            if (autoAreaLoaderWasOn) {
                if (!(mc.player.isOnGround() || mc.player.isTouchingWater())) return;
                autoClimbLostGlideRetries++;
                boolean dryShaftFly = autoClimbLostGlideRetries < 2 && autoExitShaft != null && !autoExitShaft.skyBlocked()
                    && !mc.player.isTouchingWater()
                    && mc.player.getBlockX() == autoExitShaft.x() && mc.player.getBlockZ() == autoExitShaft.z()
                    && (!autoExitShaft.skyBlocked() || shaftExitHasPivotEscape(autoExitShaft)) && autoShaftFly.get() && countRockets() >= 4;
                boolean water = autoClimbLostGlideRetries < 4 && mc.player.isTouchingWater();
                AutoState next = dryShaftFly ? AutoState.SHAFT_ALIGN : water ? AutoState.SWIM_UP : AutoState.ASCEND;
                if (next == AutoState.ASCEND && autoAscendArrivedAt != null && autoClimbLostGlideRetries >= 4) {
                    autoAscendFailedPoints.add(autoAscendArrivedAt);
                }
                autoLog("CLIMB", autoSite.center, "lost glide, retry " + autoClimbLostGlideRetries + " via " + next.name());
                if (next == AutoState.SHAFT_ALIGN) {
                    autoShaftFlyAttempts = 0;
                    autoShaftFlyRerouteAttempts = 0;
                    resetShaftDryRecovery();
                }
                enterState(next, "");
                return;
            }
            if (mc.player.isTouchingWater() && autoClimbLostGlideRetries < 4) {
                autoClimbLostGlideRetries++;
                autoWaterJumpAttempts = 0;
                autoWaterJumpVertical = false;
                autoLog("CLIMB", autoSite.center, "lost glide in water, water jump");
                enterState(AutoState.WATER_JUMP, "lost glide in water");
                return;
            }
            autoLog("CLIMB", autoSite.center, "not gliding");
            enterState(AutoState.RESUME, "");
            return;
        }

        if (autoClimbRedeployTries > 0) Utils.setPressed(mc.options.jumpKey, false);

        double y = mc.player.getY();
        double vy = mc.player.getVelocity().y;

        if (y >= autoClimbTargetY) {
            mc.player.setPitch(-35.0f);
            autoLog("CLIMB", autoSite.center, "climb done y=" + String.format("%.1f", y));
            if (autoSoloMode && !autoWalkReapproach) {
                beginSoloCruise("climb done");
                return;
            }
            enterState(AutoState.RESUME, "");
            return;
        }

        if (stateTicks > 20 * 20) {
            if (autoSoloMode) {
                beginSoloCruise("climb timeout");
                return;
            }
            enterState(AutoState.RESUME, "climb timeout");
            return;
        }

        if (stateTicks == autoClimbWindowTick) autoClimbWindowY = y;
        if (stateTicks - autoClimbWindowTick >= 20) {
            if (y - autoClimbWindowY < 0.5) {
                autoLog("CLIMB", autoSite.center, String.format("climb stuck x=%.1f z=%.1f y=%.1f",
                    mc.player.getX(), mc.player.getZ(), y));
                autoClimbStuck = true;
                autoClimbStuckStartTick = stateTicks;
                autoClimbStuckAttempts = 3;
                return;
            } else {
                autoClimbWindowY = y;
                autoClimbWindowTick = stateTicks;
            }
        }

        if (vy < 0.8 && stateTicks - autoClimbLastRocketTick >= 10 && !ownRocketActive()) {
            if (fireShaftRocket()) {
                autoClimbLastRocketTick = stateTicks;
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
        if (isShipwreck() && autoShipwreckRun != null && autoShipwreckRun.isEmergency()) {
            stopShipwreckAction();
            enterState(AutoState.WALK, "shipwreck platform exhausted, seek shore");
            return;
        }
        autoLog("OBSIDIAN_PLATFORM", autoSite.center, "abandoning: " + reason);
        autoAscendFromAbort = true;
        if (findAscendTakeoffPoints(autoColumn.x(), autoColumn.z(), 48).isEmpty()) {
            autoAscendRadius = 96;
            enterState(AutoState.ASCEND, "stuck in ocean, wider radius");
        } else {
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
                if (autoSoloMode) {
                    autoAscendArrivedAt = null;
                    enterState(AutoState.TAKEOFF, "solo platform done");
                } else if (!autoAreaLoaderWasOn) {
                    enterState(AutoState.RESUME, "staying on foot");
                } else {
                    autoAscendArrivedAt = null;
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
                int ng = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, nx, nz) - 1;
                ng = descendPastFloatingBlocks(autoSite != null ? autoSite.center : null, nx, nz, ng, groundY - 1, false, cursor) + 1;
                if (ng > groundY + 1) return false;
                for (int dy = 1; dy <= 8; dy++) {
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
        int maxTakeoffY = Math.min(mc.player.getBlockPos().getY() + 80, mc.world.getTopYInclusive() - 32);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;
                int x = cx + dx, z = cz + dz;
                int topY = surfaceTopY(x, z);
                if (topY > maxTakeoffY) continue;
                int groundY = topY;

                cursor.set(x, groundY - 1, z);
                BlockState belowState = mc.world.getBlockState(cursor);
                if (belowState.getCollisionShape(mc.world, cursor).isEmpty()) continue;
                if (!belowState.getFluidState().isEmpty()) continue;

                cursor.set(x, groundY, z);
                if (mc.world.getFluidState(cursor).isIn(FluidTags.WATER)) continue;

                boolean open = true;
                for (int dy = 0; dy < 12 && open; dy++) {
                    cursor.set(x, groundY + dy, z);
                    BlockState state = mc.world.getBlockState(cursor);
                    if (!isOpenState(state, cursor) || state.isIn(BlockTags.CLIMBABLE)) open = false;
                }
                if (open && isClearTakeoffGround(x, z, groundY)) {
                    BlockPos candidate = new BlockPos(x, groundY, z);
                    if (!autoAscendFailedPoints.contains(candidate)) candidates.add(candidate);
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getBlockPos())));
        List<BlockPos> points = new ArrayList<>();
        for (BlockPos p : candidates) {
            if (points.size() >= 12) break;
            points.add(p);
        }
        return points;
    }

    private Goal buildAscendGoal() {
        List<BlockPos> targets = autoAscendTargets;
        if (!autoAscendFailedPoints.isEmpty()) {
            List<BlockPos> filtered = new ArrayList<>();
            for (BlockPos p : targets) if (!autoAscendFailedPoints.contains(p)) filtered.add(p);
            if (!filtered.isEmpty()) targets = filtered;
        }
        if (targets.isEmpty()) {
            return new GoalBlock(new BlockPos(autoColumn.x(), autoColumn.exitY(), autoColumn.z()));
        }
        Goal[] goals = new Goal[targets.size()];
        for (int i = 0; i < targets.size(); i++) goals[i] = new GoalBlock(targets.get(i));
        return new GoalComposite(goals);
    }

    private void tickAscend() {
        if (isShipwreck() && autoShipwreckRun != null && autoShipwreckRun.returning()) {
            if (resumeShipwreckAtSurface()) return;
            if (mc.player.isTouchingWater()) {
                enterState(AutoState.SWIM_UP, "shipwreck return to surface");
                return;
            }
        }
        stateTicks++;

        protectAutoThrowawayItems();
        if (takeoffIfSkyClear()) return;

        if (stateTicks == 1) {
            BaritoneAPI.getSettings().allowPlace.value = true;
            autoBaritoneRetries = 0;
            autoAscendStuckPos = null;
            autoAscendStuckTicks = 0;
            autoAscendTimeoutBaseTick = stateTicks;
            autoTakeoffRetries = 0;
            int ascendRadius = autoAscendRadius > 0 ? autoAscendRadius : (autoColumn.water() ? 48 : 16);
            autoAscendRadius = ascendRadius;
            autoAscendTargets = findAscendTakeoffPoints(mc.player.getBlockX(), mc.player.getBlockZ(), ascendRadius);
            BlockPos nearest = autoAscendTargets.isEmpty() ? null : autoAscendTargets.get(0);
            autoLog("ASCEND", autoSite.center, "takeoff points found=" + autoAscendTargets.size()
                + (nearest != null ? " nearest=" + nearest.getX() + "," + nearest.getY() + "," + nearest.getZ() : ""));
            IBaritone bt = primaryBaritone();
            if (bt != null && (!autoAscendTargets.isEmpty() || (autoColumn.water() && autoAscendRadius >= 96))) {
                bt.getCustomGoalProcess().setGoalAndPath(buildAscendGoal());
            }
        }

        if (stateTicks - autoAscendTimeoutBaseTick > 120 * 20) {
            ascendRetryBiggerRadius();
            autoLog("ASCEND", autoSite.center, "retry after timeout");
            return;
        }

        if ((autoAreaLoaderWasOn || autoSoloMode) && mc.player.isTouchingWater() && !mc.player.isSubmergedInWater() && isClearSkyHere()
            && !autoAscendFailedPoints.contains(mc.player.getBlockPos()) && autoAscendSurfacedCount < 2
            && !waterTakeoffColumnFailed(mc.player.getBlockX(), mc.player.getBlockZ())) {
            autoAscendSurfacedCount++;
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
            IBaritone surfacedBt = primaryBaritone();
            if (surfacedBt != null) surfacedBt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            autoWaterJumpAttempts = 0;
            autoWaterJumpVertical = false;
            enterState(AutoState.WATER_JUMP, "surfaced during ascend");
            return;
        }

        if (autoAscendTargets.isEmpty()) {
            if (autoAscendRadius < 96) {
                ascendRetryBiggerRadius();
                return;
            }
            if (!autoColumn.water()) {
                IBaritone bt = primaryBaritone();
                if (bt != null) bt.getPathingBehavior().cancelEverything();
                releaseAllAutoKeys();
                enterState(AutoState.SELF_ASCEND, "no surface takeoff point, self ascend");
                return;
            }
        }

        boolean arrived = false;
        BlockPos arrivedAt = null;
        if (!autoAscendTargets.isEmpty()) {
            for (BlockPos p : autoAscendTargets) {
                double dxz = Math.hypot((p.getX() + 0.5) - mc.player.getX(), (p.getZ() + 0.5) - mc.player.getZ());
                double dy = Math.abs(mc.player.getY() - p.getY());
                if (dxz <= 1.0 && dy <= 1.0) {
                    arrived = mc.player.isOnGround() && !mc.player.isClimbing() && isTrueSkyHere();
                    arrivedAt = p;
                    break;
                }
            }
        } else {
            double dxz = Math.hypot((autoColumn.x() + 0.5) - mc.player.getX(), (autoColumn.z() + 0.5) - mc.player.getZ());
            double dy = Math.abs(mc.player.getY() - autoColumn.exitY());
            arrived = dxz <= 1.0 && dy <= 1.0 && mc.player.isOnGround() && isTrueSkyHere();
            arrivedAt = new BlockPos(autoColumn.x(), autoColumn.exitY(), autoColumn.z());
        }

        if (arrived && autoAscendFailedPoints.contains(arrivedAt)) {
            ascendRetryBiggerRadius();
            return;
        }

        if (arrived && !autoAscendFailedPoints.contains(mc.player.getBlockPos())) {
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
            autoLog("ASCEND", autoSite.center, "reached " + arrivedAt.getX() + "," + arrivedAt.getY() + "," + arrivedAt.getZ());

            if (!autoAscendFromAbort && !autoWalkReapproach && tryChainWalk(autoSite)) return;

            if (!autoSoloMode && !autoAreaLoaderWasOn) {
                enterState(AutoState.RESUME, "staying on foot");
                return;
            }

            autoAscendArrivedAt = arrivedAt;
            enterState(AutoState.TAKEOFF, "");
            return;
        }

        IBaritone bt = primaryBaritone();
        boolean pathing = bt != null && bt.getPathingBehavior().isPathing();
        boolean grounded = mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isClimbing();
        boolean baritoneGaveUp = bt == null || (!bt.getCustomGoalProcess().isActive() && bt.getPathingBehavior().getInProgress().isEmpty());
        if (!pathing && baritoneGaveUp && mc.player.isTouchingWater()) {
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            autoLog("ASCEND", autoSite.center, "baritone stuck, self ascend");
            enterState(AutoState.SELF_ASCEND, "baritone stuck");
            return;
        }
        if (!pathing && grounded && baritoneGaveUp && stateTicks > 20 && autoBaritoneRetries < 3) {
            autoBaritoneRetries++;
            if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(buildAscendGoal());
        }
        BlockPos stuckFeet = mc.player.getBlockPos();
        if (autoAscendStuckPos != null
            && Math.abs(stuckFeet.getX() - autoAscendStuckPos.getX()) <= 2
            && Math.abs(stuckFeet.getY() - autoAscendStuckPos.getY()) <= 2
            && Math.abs(stuckFeet.getZ() - autoAscendStuckPos.getZ()) <= 2) {
            autoAscendStuckTicks++;
        } else {
            autoAscendStuckPos = stuckFeet;
            autoAscendStuckTicks = 0;
        }

        if (autoAscendStuckTicks >= 240) {
            autoAscendStuckPos = null;
            autoAscendStuckTicks = 0;
            if (autoBaritoneRetries < 3) {
                autoBaritoneRetries++;
                if (bt != null) {
                    bt.getPathingBehavior().cancelEverything();
                    bt.getCustomGoalProcess().setGoalAndPath(buildAscendGoal());
                }
                releaseAllAutoKeys();
                autoLog("ASCEND", autoSite.center, "baritone no progress 12s, retry " + autoBaritoneRetries);
                return;
            }
            if (isShipwreck() && autoAscendRadius < 96) {
                ascendRetryBiggerRadius();
                return;
            }
            if (!isShipwreck()) {
            if (mc.player.getBlockPos().getY() <= autoSite.room.cy() + 4 && autoAscendRadius < 96) {
                ascendRetryBiggerRadius();
                autoLog("ASCEND", autoSite.center, "baritone no progress at room level, wider takeoff search");
                return;
            }
            }
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            autoLog("ASCEND", autoSite.center, "baritone stuck, self ascend");
            enterState(AutoState.SELF_ASCEND, "baritone stuck");
            return;
        }

        logNavTelemetry("ASCEND", autoAscendTargets.isEmpty()
            ? new BlockPos(autoColumn.x(), autoColumn.exitY(), autoColumn.z())
            : autoAscendTargets.get(0), autoBaritoneRetries);
    }

    private void ascendRetryBiggerRadius() {
        IBaritone bt = primaryBaritone();
        if (bt != null) bt.getPathingBehavior().cancelEverything();
        releaseAllAutoKeys();
        if (autoAscendRadius >= 96) {
            enterState(AutoState.SELF_ASCEND, "surface path exhausted, self ascend");
            return;
        }
        autoAscendRadius = Math.min(96, Math.max(16, autoAscendRadius) * 2);
        autoAscendTargets = findAscendTakeoffPoints(mc.player.getBlockX(), mc.player.getBlockZ(), autoAscendRadius);
        autoBaritoneRetries = 0;
        autoAscendStuckPos = null;
        autoAscendStuckTicks = 0;
        autoAscendTimeoutBaseTick = stateTicks;
        if (autoAscendTargets.isEmpty()) {
            if (autoAscendRadius < 96) return;
            if (!autoColumn.water()) {
                enterState(AutoState.SELF_ASCEND, "no surface takeoff point, self ascend");
                return;
            }
        }
        if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(buildAscendGoal());
    }

    private void tickSelfAscend() {
        stateTicks++;

        boolean climbable = takeoffHasClimbable();
        if (!climbable && takeoffIfSkyClear()) return;

        if (stateTicks == 1) {
            BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            resetSelfAscendState(false);
            autoSelfAscendBestY = mc.player.getBlockPos().getY();
            autoSelfAscendBestYTick = stateTicks;
            autoSelfAscendLastY = mc.player.getY();
            autoSelfAscendProgressTick = stateTicks;
            if (autoSelfAscendForcePillar) {
                autoSelfAscendForcePillar = false;
                autoSelfAscendPillarMode = true;
                autoLog("SELF_ASCEND", autoSite.center, "self ascend pillar");
            } else {
                chooseSelfAscendDirection(false);
            }
        }

        if (isTrueSkyHere() && !autoAscendFailedPoints.contains(mc.player.getBlockPos())
            && !waterTakeoffColumnFailed(mc.player.getBlockX(), mc.player.getBlockZ())) {
            if (climbable || mc.player.isTouchingWater() || mc.world.getFluidState(mc.player.getBlockPos()).isIn(FluidTags.WATER)) {
                releaseAllAutoKeys();
                autoLog("SELF_ASCEND", autoSite.center, "self ascend sky");
                autoAscendArrivedAt = null;
                autoWaterJumpAttempts = 0;
                autoWaterJumpVertical = false;
                enterState(AutoState.WATER_JUMP, "self ascend sky");
                return;
            }
        }

        if (stateTicks > 180 * 20) {
            releaseAllAutoKeys();
            autoLog("SELF_ASCEND", autoSite.center, "self ascend timeout, fallback ASCEND");
            autoAscendRadius = Math.min(96, Math.max(16, autoAscendRadius) * 2);
            enterState(AutoState.ASCEND, "self ascend timeout");
            return;
        }

        BlockPos feet = mc.player.getBlockPos();
        if (feet.getY() >= autoSelfAscendBestY + 1) {
            autoSelfAscendBestY = feet.getY();
            autoSelfAscendBestYTick = stateTicks;
            autoSelfAscendStepFrom = null;
            autoSelfAscendStepTarget = null;
            resetSelfAscendPlacement();
        }

        if (stateTicks - autoSelfAscendBestYTick >= (autoSelfAscendPillarMode ? 200 : 100)) {
            if (autoSelfAscendPillarMode) {
                releaseAllAutoKeys();
                autoLog("SELF_ASCEND", autoSite.center, "self ascend pillar stuck");
                if (!autoColumn.water() && autoAscendRadius >= 96) {
                    resetSelfAscendState(true);
                    autoAscendFailedPoints.retainAll(Set.of(mc.player.getBlockPos()));
                    autoAscendRadius = 16;
                    autoAscendTargets = List.of();
                    autoTakeoffSpot = null;
                    enterState(AutoState.ASCEND, "new search cycle, radius=16");
                } else {
                    autoAscendRadius = Math.min(96, Math.max(16, autoAscendRadius) * 2);
                    enterState(AutoState.ASCEND, "self ascend pillar stuck");
                }
                return;
            }
            failSelfAscendDirection("no climb");
        }

        if (stateTicks % 40 == 0) {
            autoLog("SELF_ASCEND", autoSite.center, String.format("pos=%.1f,%.1f,%.1f dir=%s water=%b ground=%b",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), selfAscendDirName(autoSelfAscendDirIndex),
                mc.player.isTouchingWater(), mc.player.isOnGround()));
        }

        if ((mc.player.isSubmergedInWater() || mc.player.isTouchingWater()) && !mc.player.isOnGround()
            && tickSelfAscendWater()) {
            return;
        }

        tickSelfAscendStair();
    }

    private boolean tickSelfAscendWater() {
        Utils.setPressed(mc.options.forwardKey, false);
        Utils.setPressed(mc.options.sprintKey, false);
        Utils.setPressed(mc.options.sneakKey, false);
        Utils.setPressed(mc.options.jumpKey, true);
        double y = mc.player.getY();
        if (y > autoSelfAscendLastY + 0.02) {
            autoSelfAscendLastY = y;
            autoSelfAscendProgressTick = stateTicks;
        }
        if (stateTicks - autoSelfAscendProgressTick < 10) return true;
        BlockPos target = firstSelfAscendVerticalBreak(mc.player.getBlockPos());
        if (target == null) return false;
        if (!setSelfAscendBreakTarget(target)) {
            failSelfAscendDirection("water break failed");
            return true;
        }
        if (autoMineTick("SELF_ASCEND", autoSelfAscendBreakPos, false)) {
            logSelfAscendBroken();
            autoSelfAscendProgressTick = stateTicks;
            autoSelfAscendLastY = mc.player.getY();
        }
        return true;
    }

    private void tickSelfAscendStair() {
        if (autoSelfAscendPillarMode) {
            tickSelfAscendPillar();
            return;
        }

        BlockPos feet = mc.player.getBlockPos();
        int[] dir = SELF_ASCEND_DIRS[autoSelfAscendDirIndex];
        BlockPos step = new BlockPos(feet.getX() + dir[0], feet.getY(), feet.getZ() + dir[1]);
        BlockState stepState = mc.world.getBlockState(step);
        boolean stepOpen = isOpenState(stepState, step) && stepState.getFluidState().isEmpty();

        if (autoSelfAscendStepTarget != null && feet.getX() == autoSelfAscendStepTarget.getX()
            && feet.getZ() == autoSelfAscendStepTarget.getZ() && feet.getY() >= autoSelfAscendStepTarget.getY()) {
            autoSelfAscendStepFrom = null;
            autoSelfAscendStepTarget = null;
            resetSelfAscendPlacement();
            return;
        }

        if (stepOpen) {
            BlockPos belowStep = step.down();
            if (selfAscendSolidStep(belowStep)) {
                autoSelfAscendStepFrom = feet;
                autoSelfAscendStepTarget = null;
                resetSelfAscendPlacement();
                autoSelfAscendBreakPos = null;
                autoSelfAscendBreakLabel = null;
                walkSelfAscendTo(step, false);
                return;
            }
            autoSelfAscendStepFrom = feet;
            autoSelfAscendStepTarget = null;
            if (tickSelfAscendPlaceStep(step)) return;
            walkSelfAscendTo(step, false);
            return;
        }

        autoSelfAscendStepFrom = feet;
        autoSelfAscendStepTarget = new BlockPos(step.getX(), step.getY() + 1, step.getZ());

        BlockPos block = firstSelfAscendStairBreak();
        if (block != null) {
            if (!setSelfAscendBreakTarget(block)) {
                failSelfAscendDirection("break failed");
                return;
            }
            if (autoMineTick("SELF_ASCEND", autoSelfAscendBreakPos, false)) {
                logSelfAscendBroken();
            }
            return;
        }

        autoSelfAscendBreakPos = null;
        autoSelfAscendBreakLabel = null;
        resetSelfAscendPlacement();
        walkSelfAscendTo(autoSelfAscendStepTarget, true);
    }

    private void walkSelfAscendTo(BlockPos target, boolean jump) {
        double dx = (target.getX() + 0.5) - mc.player.getX();
        double dz = (target.getZ() + 0.5) - mc.player.getZ();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        Utils.setPressed(mc.options.forwardKey, true);
        Utils.setPressed(mc.options.sprintKey, false);
        Utils.setPressed(mc.options.sneakKey, false);
        Utils.setPressed(mc.options.jumpKey, jump);
    }

    private void tickSelfAscendPillar() {
        BlockPos feet = mc.player.getBlockPos();
        BlockPos breakPos = feet.up(2);
        BlockState breakState = mc.world.getBlockState(breakPos);
        if (!isOpenState(breakState, breakPos) && breakState.getFluidState().isEmpty()) {
            if (!setSelfAscendBreakTarget(breakPos)) {
                releaseAllAutoKeys();
                if (!autoColumn.water() && autoAscendRadius >= 96) {
                    resetSelfAscendState(true);
                    autoAscendFailedPoints.retainAll(Set.of(mc.player.getBlockPos()));
                    autoAscendRadius = 16;
                    autoAscendTargets = List.of();
                    autoTakeoffSpot = null;
                    enterState(AutoState.ASCEND, "new search cycle, radius=16");
                } else {
                    autoAscendRadius = Math.min(96, Math.max(16, autoAscendRadius) * 2);
                    enterState(AutoState.ASCEND, "self ascend pillar blocked");
                }
                return;
            }
            if (autoMineTick("SELF_ASCEND", autoSelfAscendBreakPos, false)) {
                logSelfAscendBroken();
            }
            return;
        }

        autoSelfAscendBreakPos = null;
        autoSelfAscendBreakLabel = null;
        Utils.setPressed(mc.options.forwardKey, false);
        Utils.setPressed(mc.options.sprintKey, false);
        Utils.setPressed(mc.options.sneakKey, false);
        Utils.setPressed(mc.options.jumpKey, true);
        mc.player.setPitch(90.0f);

        if (!mc.player.isOnGround() && mc.player.getVelocity().y <= 0.05) {
            BlockPos placePos = mc.player.getBlockPos().down();
            BlockState placeState = mc.world.getBlockState(placePos);
            if (isOpenState(placeState, placePos)
                && (placeState.getFluidState().isEmpty() || placeState.getFluidState().isIn(FluidTags.WATER))) {
                if (ensureObsidianInHotbar() < 0) {
                    releaseAllAutoKeys();
                    if (!autoColumn.water() && autoAscendRadius >= 96) {
                        resetSelfAscendState(true);
                        autoAscendFailedPoints.retainAll(Set.of(mc.player.getBlockPos()));
                        autoAscendRadius = 16;
                        autoAscendTargets = List.of();
                        autoTakeoffSpot = null;
                        enterState(AutoState.ASCEND, "new search cycle, radius=16");
                    } else {
                        autoAscendRadius = Math.min(96, Math.max(16, autoAscendRadius) * 2);
                        enterState(AutoState.ASCEND, "self ascend no throwaway");
                    }
                    return;
                }
                tickSelfAscendPlaceStep(placePos);
            }
        }
    }

    private BlockPos firstSelfAscendVerticalBreak(BlockPos feet) {
        for (int up = 2; up >= 1; up--) {
            BlockPos pos = new BlockPos(feet.getX(), feet.getY() + up, feet.getZ());
            if (selfAscendBreakable(pos)) return pos;
        }
        return null;
    }

    private BlockPos firstSelfAscendStairBreak() {
        BlockPos f = autoSelfAscendStepFrom;
        int[] dir = SELF_ASCEND_DIRS[autoSelfAscendDirIndex];
        BlockPos[] checks = {
            new BlockPos(f.getX(), f.getY() + 2, f.getZ()),
            new BlockPos(f.getX() + dir[0], f.getY() + 1, f.getZ() + dir[1]),
            new BlockPos(f.getX() + dir[0], f.getY() + 2, f.getZ() + dir[1])
        };
        for (BlockPos pos : checks) {
            BlockState state = mc.world.getBlockState(pos);
            if (isOpenState(state, pos)) continue;
            if (!selfAscendCanBreak(pos, state)) return pos;
            return pos;
        }
        return null;
    }

    private boolean tickSelfAscendPlaceStep(BlockPos pos) {
        if (!pos.equals(autoSelfAscendPlacePos)) {
            autoSelfAscendPlacePos = pos;
            autoSelfAscendPlaceTick = 0;
            autoSelfAscendPlaceAttempts = 0;
            autoSelfAscendPlaceLabel = null;
        }
        if (selfAscendSolidStep(pos)) {
            resetSelfAscendPlacement();
            return false;
        }
        if (!selfAscendCanPlaceStep(pos)) {
            failSelfAscendDirection("place impossible");
            return true;
        }
        if (autoSelfAscendPlaceAttempts > 0 && stateTicks - autoSelfAscendPlaceTick < 5) {
            releaseAllAutoKeys();
            return true;
        }
        if (autoSelfAscendPlaceAttempts >= 2) {
            failSelfAscendDirection("place failed");
            return true;
        }
        int slot = ensureObsidianInHotbar();
        if (slot < 0) {
            failSelfAscendDirection("no throwaway");
            return true;
        }
        InvUtils.swap(slot, false);
        autoSelfAscendPlaceLabel = blockItemLabel(slot);
        Direction side = selfAscendPlaceSide(pos);
        if (side == null) {
            failSelfAscendDirection("place support lost");
            return true;
        }
        BlockPos supportPos = pos.offset(side.getOpposite());
        Vec3d lookTarget = Vec3d.ofCenter(supportPos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(lookTarget, side, supportPos, false);
        releaseAllAutoKeys();
        if (!interactWhenAligned(lookTarget, hit)) return true;
        mc.player.swingHand(Hand.MAIN_HAND);
        autoSelfAscendPlaceAttempts++;
        autoSelfAscendPlaceTick = stateTicks;
        autoLog("SELF_ASCEND", autoSite.center, String.format("self ascend placed %s at %d,%d,%d",
            autoSelfAscendPlaceLabel, pos.getX(), pos.getY(), pos.getZ()));
        return true;
    }

    private void resetSelfAscendPlacement() {
        autoSelfAscendPlacePos = null;
        autoSelfAscendPlaceTick = 0;
        autoSelfAscendPlaceAttempts = 0;
        autoSelfAscendPlaceLabel = null;
    }

    private boolean selfAscendCanPlaceStep(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!isOpenState(state, pos)
            || (!state.getFluidState().isEmpty() && !(autoSelfAscendPillarMode && state.getFluidState().isIn(FluidTags.WATER)))) return false;
        if (isLavaNearShaft(pos, pos.up())) return false;
        return selfAscendPlaceSide(pos) != null;
    }

    private Direction selfAscendPlaceSide(BlockPos pos) {
        Direction[] sides = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction side : sides) {
            BlockPos supportPos = pos.offset(side.getOpposite());
            BlockState support = mc.world.getBlockState(supportPos);
            if (!isOpenState(support, supportPos) && support.getFluidState().isEmpty()) return side;
        }
        return null;
    }

    private boolean selfAscendSolidStep(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !isOpenState(state, pos) && state.getFluidState().isEmpty();
    }

    private boolean setSelfAscendBreakTarget(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (isOpenState(state, pos)) {
            if (pos.equals(autoSelfAscendBreakPos)) logSelfAscendBroken();
            autoSelfAscendBreakPos = null;
            autoSelfAscendBreakLabel = null;
            return false;
        }
        if (!selfAscendCanBreak(pos, state)) return false;
        int count = autoSelfAscendBreakCounts.getOrDefault(pos, 0);
        if (count > 16) return false;
        if (!pos.equals(autoSelfAscendBreakPos)) {
            autoSelfAscendBreakPos = pos;
            autoSelfAscendBreakLabel = Registries.BLOCK.getId(state.getBlock()).getPath();
        }
        releaseAllAutoKeys();
        return true;
    }

    private void logSelfAscendBroken() {
        if (autoSelfAscendBreakPos == null) return;
        autoSelfAscendBreakCounts.put(autoSelfAscendBreakPos, autoSelfAscendBreakCounts.getOrDefault(autoSelfAscendBreakPos, 0) + 1);
        autoLog("SELF_ASCEND", autoSite.center, String.format("self ascend broken %d,%d,%d block=%s",
            autoSelfAscendBreakPos.getX(), autoSelfAscendBreakPos.getY(), autoSelfAscendBreakPos.getZ(), autoSelfAscendBreakLabel));
        autoSelfAscendBreakPos = null;
        autoSelfAscendBreakLabel = null;
    }

    private boolean selfAscendBreakable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !isOpenState(state, pos) && selfAscendCanBreak(pos, state);
    }

    private boolean selfAscendCanBreak(BlockPos pos, BlockState state) {
        if (state.getBlock() == Blocks.SPAWNER || isContainerPresent(pos)) return false;
        if (isLava(state) || isLavaNearShaft(pos, pos.up())) return false;
        return true;
    }

    private int surfaceTopY(int x, int z) {
        int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        int ceiling = mc.world.getTopYInclusive() - 32;
        if (top <= ceiling) return top;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int y = ceiling;
        while (y > mc.world.getBottomY()) {
            cursor.set(x, y, z);
            BlockState state = mc.world.getBlockState(cursor);
            if (!state.getFluidState().isEmpty() || !state.getCollisionShape(mc.world, cursor).isEmpty()) break;
            y--;
        }
        return y + 1;
    }

    private boolean isTrueSkyHere() {
        BlockPos feet = mc.player.getBlockPos();
        return surfaceTopY(feet.getX(), feet.getZ()) <= feet.getY() + 1;
    }

    private void failSelfAscendDirection(String reason) {
        releaseAllAutoKeys();
        if (autoSelfAscendPillarMode) return;
        if (stateTicks - autoSelfAscendLastTurnTick < 40) return;
        autoSelfAscendTried[autoSelfAscendDirIndex] = true;
        boolean anyLeft = false;
        for (boolean tried : autoSelfAscendTried) if (!tried) anyLeft = true;
        autoSelfAscendLastTurnTick = stateTicks;
        if (!anyLeft) {
            autoSelfAscendPillarMode = true;
            autoSelfAscendStepFrom = null;
            autoSelfAscendStepTarget = null;
            autoSelfAscendBreakPos = null;
            autoSelfAscendBreakLabel = null;
            resetSelfAscendPlacement();
            autoLog("SELF_ASCEND", autoSite.center, "self ascend pillar reason=" + reason);
            return;
        }
        chooseSelfAscendDirection(true);
    }

    private void chooseSelfAscendDirection(boolean turn) {
        boolean anyLeft = false;
        for (boolean tried : autoSelfAscendTried) if (!tried) anyLeft = true;
        if (!anyLeft) {
            autoSelfAscendPillarMode = true;
            autoLog("SELF_ASCEND", autoSite.center, "self ascend pillar");
            return;
        }

        int best = -1;
        int bestForbidden = Integer.MAX_VALUE;
        int bestWall = Integer.MAX_VALUE;
        int bestLiquid = Integer.MAX_VALUE;
        int bestBlocks = Integer.MAX_VALUE;
        for (int i = 0; i < SELF_ASCEND_DIRS.length; i++) {
            if (autoSelfAscendTried[i]) continue;
            int[] score = scoreSelfAscendDirection(i);
            if (score[0] < bestForbidden
                || (score[0] == bestForbidden && score[1] < bestWall)
                || (score[0] == bestForbidden && score[1] == bestWall && score[2] < bestLiquid)
                || (score[0] == bestForbidden && score[1] == bestWall && score[2] == bestLiquid && score[3] < bestBlocks)) {
                best = i;
                bestForbidden = score[0];
                bestWall = score[1];
                bestLiquid = score[2];
                bestBlocks = score[3];
            }
        }
        if (best < 0) best = 0;
        autoSelfAscendDirIndex = best;
        autoSelfAscendTried[best] = true;
        autoSelfAscendStepFrom = null;
        autoSelfAscendStepTarget = null;
        autoSelfAscendBreakPos = null;
        autoSelfAscendBreakLabel = null;
        resetSelfAscendPlacement();
        autoSelfAscendBestYTick = stateTicks;
        String msg = turn ? "self ascend turn dir=" : "self ascend start dir=";
        autoLog("SELF_ASCEND", autoSite.center, msg + selfAscendDirName(best));
    }

    private int[] scoreSelfAscendDirection(int dirIndex) {
        int[] dir = SELF_ASCEND_DIRS[dirIndex];
        BlockPos feet = mc.player.getBlockPos();
        int forbidden = 0;
        int wall = 99;
        int liquid = 0;
        int blocks = 0;
        for (int step = 1; step <= 6; step++) {
            BlockPos stair = new BlockPos(feet.getX() + dir[0] * step, feet.getY(), feet.getZ() + dir[1] * step);
            BlockPos[] checks = {
                stair,
                stair.up(),
                stair.up(2)
            };
            for (BlockPos pos : checks) {
                BlockState state = mc.world.getBlockState(pos);
                if (!state.getFluidState().isEmpty()) {
                    liquid++;
                    forbidden = 1;
                }
                if (state.getFluidState().isIn(FluidTags.LAVA)) forbidden = 1;
            }
            BlockState stairState = mc.world.getBlockState(stair);
            if (!isOpenState(stairState, stair)) {
                wall = step;
                if (!selfAscendCanBreak(stair, stairState)) forbidden = 1;
                BlockPos[] cuts = {
                    feet.up(2),
                    stair.up(),
                    stair.up(2)
                };
                for (BlockPos pos : cuts) {
                    BlockState state = mc.world.getBlockState(pos);
                    if (!isOpenState(state, pos)) {
                        blocks++;
                        if (!selfAscendCanBreak(pos, state)) forbidden = 1;
                    }
                }
                break;
            }
        }
        return new int[]{forbidden, wall, liquid, blocks};
    }

    private String selfAscendDirName(int index) {
        return switch (index) {
            case 0 -> "east";
            case 1 -> "south";
            case 2 -> "west";
            default -> "north";
        };
    }

    private boolean takeoffIfSkyClear() {
        if (isShipwreck() && autoShipwreckRun != null && autoShipwreckRun.returning()) {
            return resumeShipwreckAtSurface();
        }
        BlockPos feet = mc.player.getBlockPos();
        if (autoAscendFailedPoints.contains(feet) || !mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isClimbing()
            || !isTrueSkyHere() || !isClearSkyHere() || !isClearTakeoffGround(feet.getX(), feet.getZ(), feet.getY())) return false;
        IBaritone bt = primaryBaritone();
        if (bt != null) bt.getPathingBehavior().cancelEverything();
        releaseAllAutoKeys();
        BaritoneAPI.getSettings().allowPlace.value = autoSavedAllowPlace;
        autoLog(state.name(), autoSite.center, "sky clear here, take off");
        if (!autoAscendFromAbort && !autoWalkReapproach && tryChainWalk(autoSite)) return true;
        if (!autoSoloMode && !autoAreaLoaderWasOn) {
            enterState(AutoState.RESUME, "staying on foot");
        } else {
            autoAscendArrivedAt = feet;
            enterState(AutoState.TAKEOFF, "");
        }
        return true;
    }

    private BlockPos findTakeoffSpot() {
        int cx = mc.player.getBlockX(), cz = mc.player.getBlockZ();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int maxTakeoffY = Math.min(mc.player.getBlockPos().getY() + 80, mc.world.getTopYInclusive() - 32);

        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq > 256 || distSq >= bestDistSq) continue;
                int x = cx + dx, z = cz + dz;
                int topY = surfaceTopY(x, z);
                if (topY > maxTakeoffY) continue;
                int groundY = topY;
                BlockPos candidate = new BlockPos(x, groundY, z);
                if (autoAscendFailedPoints.contains(candidate)) continue;
                boolean open = true;
                for (int dy = 0; dy < 12 && open; dy++) {
                    cursor.set(x, groundY + dy, z);
                    BlockState state = mc.world.getBlockState(cursor);
                    if (!isOpenState(state, cursor) || state.isIn(BlockTags.CLIMBABLE)) open = false;
                }
                if (!open) continue;
                if (!isClearTakeoffGround(x, z, groundY)) continue;
                best = candidate;
                bestDistSq = distSq;
            }
        }

        return best;
    }

    private void tickTakeoffSpot() {
        stateTicks++;

        if (takeoffIfSkyClear()) return;

        if (stateTicks == 1) {
            BlockPos spot = findTakeoffSpot();
            autoTakeoffSpot = spot;
            if (spot == null) {
                IBaritone bt = primaryBaritone();
                if (bt != null) bt.getPathingBehavior().cancelEverything();
                releaseAllAutoKeys();
                if (autoAscendRadius >= 96 && !autoColumn.water()) {
                    enterState(AutoState.SELF_ASCEND, "no takeoff spot, self ascend");
                } else {
                    autoAscendRadius = Math.min(96, Math.max(16, autoAscendRadius) * 2);
                    enterState(AutoState.ASCEND, "no takeoff spot");
                }
                return;
            }
            autoLog("TAKEOFF_SPOT", autoSite.center, String.format("spot=%d,%d,%d dist=%.1f",
                spot.getX(), spot.getY(), spot.getZ(),
                Math.hypot((spot.getX() + 0.5) - mc.player.getX(), (spot.getZ() + 0.5) - mc.player.getZ())));
            boolean alreadyThere = mc.player.getBlockX() == spot.getX() && mc.player.getBlockZ() == spot.getZ()
                && Math.abs(mc.player.getY() - spot.getY()) <= 1.5;
            IBaritone bt = primaryBaritone();
            if (bt != null && !alreadyThere) bt.getCustomGoalProcess().setGoalAndPath(new GoalBlock(spot));
        }

        if (stateTicks > 30 * 20) {
            if (autoTakeoffSpot != null) autoAscendFailedPoints.add(autoTakeoffSpot);
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            releaseAllAutoKeys();
            if (autoAscendRadius >= 96) {
                enterState(AutoState.SELF_ASCEND, "takeoff spot unreachable, self ascend");
                return;
            }
            autoAscendRadius = Math.min(96, Math.max(16, autoAscendRadius) * 2);
            enterState(AutoState.ASCEND, "takeoff spot timeout");
            return;
        }

        if (autoTakeoffSpot == null) return;

        double dist = Math.hypot((autoTakeoffSpot.getX() + 0.5) - mc.player.getX(), (autoTakeoffSpot.getZ() + 0.5) - mc.player.getZ());
        if (dist <= 1.0 && Math.abs(mc.player.getY() - autoTakeoffSpot.getY()) <= 1.5 && mc.player.isOnGround()
            && !autoAscendFailedPoints.contains(autoTakeoffSpot) && !autoAscendFailedPoints.contains(mc.player.getBlockPos())) {
            IBaritone bt = primaryBaritone();
            if (bt != null) bt.getPathingBehavior().cancelEverything();
            autoAscendArrivedAt = autoTakeoffSpot;
            enterState(AutoState.TAKEOFF, "");
            return;
        }

        logNavTelemetry("TAKEOFF_SPOT", autoTakeoffSpot, 0);
    }

    private int ensureRocketInHotbar() {
        FindItemResult hotbar = InvUtils.findInHotbar(com.hunterbuddy.util.Rockets::isPlain);
        if (hotbar.found() && !hotbar.isOffhand()) return hotbar.slot();

        FindItemResult inv = InvUtils.find(com.hunterbuddy.util.Rockets::isPlain);
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
        boolean takeoffPending = !takeoff.isActive() && autoTakeoffOutcome == 0 && mc.player.isOnGround();
        if (takeoffPending) {
            DamageSource recentSource = mc.player.getRecentDamageSource();
            Entity attacker = recentSource != null ? recentSource.getAttacker() : null;
            boolean fighting = autoLastHitMs != 0 && (System.currentTimeMillis() - autoLastHitMs) < 3000L
                && (attacker instanceof AbstractSkeletonEntity || attacker instanceof WitchEntity) && attacker.isAlive();

            if (fighting) {
                double dist = Math.sqrt(attacker.squaredDistanceTo(mc.player));
                if (attacker != autoShaftChaseTarget) {
                    cancelShaftChaseBaritone();
                    autoShaftChaseTarget = attacker;
                    autoShaftChaseLastDist = dist;
                    autoShaftChaseStuckTicks = 0;
                    autoLog("TAKEOFF", autoSite.center, String.format("chase id=%d dist=%.1f", attacker.getId(), dist));
                } else if (dist <= autoShaftChaseLastDist - 0.5) {
                    autoShaftChaseLastDist = dist;
                    autoShaftChaseStuckTicks = 0;
                } else {
                    autoShaftChaseStuckTicks++;
                }

                BlockPos targetPos = attacker.getBlockPos();
                if (!autoShaftChaseBaritoneActive && autoShaftChaseStuckTicks >= 40) {
                    IBaritone bt = primaryBaritone();
                    if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalNear(targetPos, 2));
                    autoShaftChaseBaritoneActive = true;
                    autoShaftChaseBaritoneGoalPos = targetPos;
                    autoLog("TAKEOFF", autoSite.center, String.format("chase via baritone id=%d dist=%.1f", attacker.getId(), dist));
                }

                if (autoShaftChaseBaritoneActive) {
                    if (autoShaftChaseBaritoneGoalPos == null || targetPos.getSquaredDistance(autoShaftChaseBaritoneGoalPos) > 4.0) {
                        IBaritone bt = primaryBaritone();
                        if (bt != null) bt.getCustomGoalProcess().setGoalAndPath(new GoalNear(targetPos, 2));
                        autoShaftChaseBaritoneGoalPos = targetPos;
                    }
                    releaseAllAutoKeys();
                } else {
                    moveTowardShaftTarget(attacker);
                }
                autoTakeoffFought = true;
                return;
            }
        }

        if (autoTakeoffFought) {
            cancelShaftChaseBaritone();
            releaseAllAutoKeys();
            autoTakeoffFought = false;
            enterState(AutoState.ASCEND, "back to takeoff point after combat");
            return;
        }

        if (waitingForElytra("TAKEOFF")) return;
        stateTicks++;

        if (stateTicks == 1) {
            autoTakeoffYaw = mc.player.getYaw();
            int slot = ensureRocketInHotbar();
            if (slot < 0) {
                if (autoSoloMode) {
                    autoLog("SOLO", autoSite.center, "out of rockets, staying with flight-module None behavior");
                    autoRunAbandoned = true;
                    enterState(AutoState.RESUME, "solo out of rockets");
                    return;
                }
                enterState(AutoState.RESUME, "no rockets, staying on foot");
                return;
            }
            autoTakeoffOutcome = 0;
            autoTakeoffFought = false;
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
        mc.player.setPitch(-90.0f);

        if (stateTicks > 40 * 20) {
            if (takeoff.isActive()) takeoff.cancel();
            takeoffFailedRetryOrAscend("takeoff timeout");
            return;
        }

        if (autoTakeoffOutcome == 1) {
            autoTakeoffOutcome = 0;
            autoTakeoffRetries = 0;
            autoClimbTargetY = mc.player.getY() + climbHeight.get();
            enterState(AutoState.CLIMB, "takeoff done");
        } else if (autoTakeoffOutcome == 2) {
            String r = autoTakeoffFailReason;
            autoTakeoffOutcome = 0;
            takeoffFailedRetryOrAscend("takeoff failed: " + r);
        }
    }

    private void takeoffFailedRetryOrAscend(String reason) {
        autoAscendFailedPoints.add(mc.player.getBlockPos());
        if (mc.player.isClimbing()) autoTakeoffRetries = 3;
        autoTakeoffRetries++;
        if (autoTakeoffRetries <= 1) {
            autoLog("TAKEOFF", autoSite.center, "retry " + autoTakeoffRetries + " reason=" + reason);
            enterState(AutoState.TAKEOFF, "");
            return;
        }
        autoTakeoffRetries = 0;
        if (autoAscendArrivedAt != null) autoAscendFailedPoints.add(autoAscendArrivedAt);
        autoLog("TAKEOFF", autoSite.center, "giving up spot, reason=" + reason);
        enterState(AutoState.ASCEND, "");
    }

    private String autoRunOutcome() {
        if (autoRoomNotOpened) return "failed:chest not opened";
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

    private void completeRunBeforeSoloCruise() {
        autoWalkReapproach = false;
        autoWalkReapproachCount = 0;
        if (isShipwreck()) {
            restoreShipwreckSettings();
            autoLog("RESUME", autoSite.center, "shipwreck run complete");
            autoShipwreckRun = null;
            autoSoloTransitOnly = true;
            return;
        }
        restoreAutoThrowawayItems();
        if (autoSoloTransitOnly || autoSite == null) return;
        BlockPos finished = autoSite.center;
        if (!autoRunAbandoned) {
            markVisited(finished, autoRunOutcome());
            statRoomsFarmed++;
        } else if (!finished.equals(autoWatchdogAbandonedSite)) {
            if (autoAbandonedOnce.add(finished)) {
                autoLog("IDLE", finished, "abandoned, next try digs straight down from above");
            } else {
                markVisited(finished, "abandoned twice");
                autoLog("IDLE", finished, "abandoned twice, marked failed");
            }
        }
        autoLog("IDLE", finished, "run complete");
        autoRunAbandoned = true;
        autoSoloTransitOnly = true;
    }

    private void beginSoloCruise(String details) {
        completeRunBeforeSoloCruise();
        Site target = findSoloMemoryTarget();
        if (target == null) {
            autoLog("SOLO", autoSite != null ? autoSite.center : mc.player.getBlockPos(), "no targets, staying with flight-module None behavior");
            autoRunAbandoned = true;
            enterState(AutoState.RESUME, "solo no targets");
            return;
        }
        startSoloTravel(target, details);
    }

    private void tickSoloCruise() {
        stateTicks++;
        if (autoSoloTarget == null || !soloMemoryCandidate(autoSoloTarget)) {
            Site target = findSoloMemoryTarget();
            if (target == null) {
                autoLog("SOLO", autoSite != null ? autoSite.center : mc.player.getBlockPos(), "no targets, staying with flight-module None behavior");
                autoRunAbandoned = true;
                enterState(AutoState.RESUME, "solo no targets");
                return;
            }
            autoSoloTarget = target;
            autoSite = target;
            autoSoloBestDistSq = Double.MAX_VALUE;
            autoSoloBestDistTick = stateTicks;
            autoSoloNearUnloadedTicks = 0;
            autoLog("SOLO", target.center, String.format("target %d,%d,%d remaining=%d",
                target.center.getX(), target.center.getY(), target.center.getZ(), soloMemoryTargetCount()));
        }

        if (!mc.player.isGliding()) {
            releaseAllAutoKeys();
            if (mc.player.isTouchingWater()) {
                autoWaterJumpAttempts = 0;
                autoWaterJumpVertical = false;
                enterState(AutoState.WATER_JUMP, "solo cruise lost glide in water");
            } else if (isClearSkyHere()) {
                autoAscendArrivedAt = null;
                enterState(AutoState.TAKEOFF, "solo cruise lost glide");
            } else {
                enterState(AutoState.TAKEOFF_SPOT, "solo cruise lost glide");
            }
            return;
        }

        Site target = autoSoloTarget;
        double dx = target.center.getX() + 0.5 - mc.player.getX();
        double dz = target.center.getZ() + 0.5 - mc.player.getZ();
        double distSq = dx * dx + dz * dz;
        double dist = Math.sqrt(distSq);

        if (stateTicks == 1 || autoSoloBestDistSq == Double.MAX_VALUE) {
            autoSoloBestDistSq = distSq;
            autoSoloBestDistTick = stateTicks;
            autoSoloNearUnloadedTicks = 0;
            autoSoloCruiseAltitude = Math.max(autoSoloCruiseAltitude, mc.player.getY());
            autoLog("SOLO", target.center, String.format("cruise target=%d,%d,%d remaining=%d dist=%.1f altitude=%.1f",
                target.center.getX(), target.center.getY(), target.center.getZ(), soloMemoryTargetCount(), dist, autoSoloCruiseAltitude));
        }

        if (mc.world.getChunkManager().isChunkLoaded(target.center.getX() >> 4, target.center.getZ() >> 4)) {
            autoLog("SOLO", target.center, String.format("target loaded, requalifying %d,%d,%d",
                target.center.getX(), target.center.getY(), target.center.getZ()));
            QualResult qr = evaluateQualification(target);
            if (qr.reject() == null) {
                startAutoRun(target, qr.plan());
                return;
            }
            autoSoloRejectedThisSession.add(target.center);
            autoLog("SOLO", target.center, String.format("target rejected %d,%d,%d reason=%s",
                target.center.getX(), target.center.getY(), target.center.getZ(), qr.reject()));
            autoSoloTarget = null;
            return;
        }

        if (dist < 96.0) {
            autoSoloNearUnloadedTicks++;
            if (autoSoloNearUnloadedTicks > 15 * 20) {
                autoSoloRejectedThisSession.add(target.center);
                autoLog("SOLO", target.center, String.format("chunk did not load, skip %d,%d,%d",
                    target.center.getX(), target.center.getY(), target.center.getZ()));
                autoSoloTarget = null;
                return;
            }
        } else {
            autoSoloNearUnloadedTicks = 0;
        }

        if (distSq <= autoSoloBestDistSq - 64.0) {
            autoSoloBestDistSq = distSq;
            autoSoloBestDistTick = stateTicks;
        } else if (stateTicks - autoSoloBestDistTick > soloCruiseStuckTime.get() * 20) {
            autoSoloRejectedThisSession.add(target.center);
            autoLog("SOLO", target.center, String.format("no progress, skip %d,%d,%d",
                target.center.getX(), target.center.getY(), target.center.getZ()));
            autoSoloTarget = null;
            return;
        }

        Vec3d vel = mc.player.getVelocity();
        double speedH = Math.hypot(vel.x, vel.z);
        double y = mc.player.getY();
        if ((speedH < 1.2 || y < autoSoloCruiseAltitude - 35.0) && dist > 96.0
            && stateTicks - autoSoloLastRocketTick >= soloCruiseRocketInterval.get() && !ownRocketActive()) {
            if (countRockets() <= autoMinRockets.get()) {
                autoLog("SOLO", target.center, "out of rockets, staying with flight-module None behavior");
                autoRunAbandoned = true;
                enterState(AutoState.RESUME, "solo out of rockets");
                return;
            }
            if (fireShaftRocket()) {
                autoSoloLastRocketTick = stateTicks;
                autoLog("SOLO", target.center, String.format("rocket speedH=%.2f y=%.1f altitude=%.1f", speedH, y, autoSoloCruiseAltitude));
            }
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = y < autoSoloCruiseAltitude - 35.0 ? -25.0f : y > autoSoloCruiseAltitude + 20.0 ? 8.0f : -5.0f;
        applyGlideRotation(yaw, pitch);
        if (log.get() && stateTicks % 20 == 0) {
            HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] SOLO site={},{},{} D={} y={} speedH={} rockets={} remaining={}",
                target.center.getX(), target.center.getY(), target.center.getZ(),
                String.format("%.1f", dist), String.format("%.1f", y), String.format("%.2f", speedH),
                countRockets(), soloMemoryTargetCount());
        }
    }

    private void tickResume() {
        if (isShipwreck() && mc.player.isTouchingWater()) {
            if (autoShipwreckRun == null) {
                autoShipwreckRun = new ShipwreckRun(System.currentTimeMillis());
                autoShipwreckRun.depart(System.currentTimeMillis());
            }
            if (autoShipwreckRun != null && autoShipwreckRun.returning() && resumeShipwreckAtSurface()) return;
            enterState(AutoState.SWIM_UP, "shipwreck cannot idle in water");
            return;
        }
        stateTicks++;

        if (autoWalkReapproach) {
            autoWalkReapproach = false;
            autoLandSpotAttempts = 0;
            autoLandSpotGaveUpLogged = false;
            autoWaterDiveClearTicks = 0;
            autoLandSpot = autoColumn.water() ? null : chooseLandSpot(autoColumn, LAND_RING_RADIUS);
            autoLog("RESUME", autoSite.center, "re-approach airborne, back to APPROACH");
            enterState(AutoState.APPROACH, "");
            return;
        }

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
                    if (autoTrailYawSaved && fm instanceof TrailFollower) {
                        mc.player.setYaw(autoTrailYaw);
                        mc.player.setPitch(0.0f);
                        autoLog("RESUME", autoSite.center, "yaw restored for TrailFollower");
                    } else if (autoWaypointYawSaved && fm instanceof WaypointFollower) {
                        mc.player.setYaw(autoWaypointYaw);
                        autoLog("RESUME", autoSite.center, "yaw restored for WaypointFollower=" + autoWaypointYaw);
                    }
                    fm.toggle();
                    autoLog("RESUME", autoSite.center, "re-enabled " + fm.name);
                }
            }
            autoMemoryChaseFromFlightModule = false;
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
            Module fm = selectedFlightModule();
            if (autoTrailYawSaved && fm instanceof TrailFollower && fm.isActive()) {
                mc.player.setYaw(autoTrailYaw);
                mc.player.setPitch(0.0f);
            }
        }

        if (stateTicks >= 2) {
            if (isShipwreck()) {
                autoLog("IDLE", autoSite.center, "shipwreck run complete");
                resetAutoState();
                return;
            }
            BlockPos finished = autoSite.center;
            if (!autoRunAbandoned) {
                markVisited(finished, autoRunOutcome());
                statRoomsFarmed++;
            } else if (!autoSoloTransitOnly && !finished.equals(autoWatchdogAbandonedSite)) {
                if (autoAbandonedOnce.add(finished)) {
                    autoLog("IDLE", finished, "abandoned, next try digs straight down from above");
                } else {
                    markVisited(finished, "abandoned twice");
                    autoLog("IDLE", finished, "abandoned twice, marked failed");
                }
            }
            autoLog("IDLE", finished, "run complete");
            resetAutoState();
        }
    }

    private void abort(String reason) {
        if (isShipwreck() && autoShipwreckRun != null) {
            if (!isVisited(autoSite.center)) abandonShipwreckChest(reason, System.currentTimeMillis());
            else recoverShipwreckExit(reason);
            return;
        }
        if (state == AutoState.SWIM_UP || state == AutoState.WATER_JUMP || state == AutoState.CLIMB
            || state == AutoState.OBSIDIAN_PLATFORM
            || state == AutoState.ASCEND || state == AutoState.SELF_ASCEND || state == AutoState.TAKEOFF_SPOT || state == AutoState.TAKEOFF) {
            autoLog("ABORT", autoSite.center, "state=" + state + " reason=" + reason + " (ignored, already exiting) player=" + playerPosLabel());
            return;
        }

        AutoState from = state;
        playAutoSound("spawner_failed", soundFailed);
        autoLog("ABORT", autoSite.center, "state=" + from + " reason=" + reason + " player=" + playerPosLabel());

        BaritoneHelper.stopAllPathing();
        releaseAllAutoKeys();
        if (!"dig block timeout".equals(reason) && !autoSite.center.equals(autoWatchdogAbandonedSite)) markVisited(autoSite.center, "aborted:" + reason);
        autoRunAbandoned = true;

        boolean gliding = mc.player.isGliding();
        int feetY = mc.player.getBlockPos().getY();

        if (!gliding && (mc.player.isTouchingWater() || autoColumn.water())) {
            if (mc.player.isSubmergedInWater() || feetY < autoColumn.exitY() - 1) {
                autoAscendFromAbort = true;
                enterAscendOrSwimUp("from ABORT (" + reason + ")");
            } else {
                autoAscendFromAbort = true;
                if (autoSoloMode) {
                    autoWaterJumpAttempts = 0;
                    autoWaterJumpVertical = false;
                    enterState(AutoState.WATER_JUMP, "solo from ABORT (" + reason + ")");
                } else if (!autoAreaLoaderWasOn) {
                    enterState(AutoState.OBSIDIAN_PLATFORM, "no flight module, platform only");
                } else {
                    autoWaterJumpAttempts = 0;
                    autoWaterJumpVertical = false;
                    enterState(AutoState.WATER_JUMP, "from ABORT (" + reason + ")");
                }
            }
        } else if (gliding) {
            if (autoSoloMode) {
                beginSoloCruise("from ABORT (" + reason + ")");
            } else {
                enterState(AutoState.RESUME, "from ABORT (" + reason + ")");
            }
        } else if (feetY >= autoColumn.exitY() - 1) {
            if (autoSoloMode) {
                if (isClearSkyHere()) {
                    autoAscendArrivedAt = null;
                    enterState(AutoState.TAKEOFF, "solo from ABORT (" + reason + ")");
                } else {
                    enterState(AutoState.TAKEOFF_SPOT, "solo from ABORT (" + reason + ")");
                }
            } else if (!autoAreaLoaderWasOn) {
                enterState(AutoState.RESUME, "staying on foot");
            } else {
                boolean blocked = autoColumn.skyBlocked()
                    || hasBlockAbove(mc.player.getBlockPos().getX(), feetY, mc.player.getBlockPos().getZ(), SKY_CHECK_HEIGHT);
                if (blocked) enterState(AutoState.TAKEOFF_SPOT, "from ABORT (" + reason + ")");
                else {
                    autoAscendArrivedAt = null;
                    enterState(AutoState.TAKEOFF, "from ABORT (" + reason + ")");
                }
            }
        } else {
            autoAscendFromAbort = true;
            enterState(AutoState.ASCEND, "from ABORT (" + reason + ")");
        }
    }

    private void fail(String reason) {
        if (isShipwreck() && autoShipwreckRun != null) {
            if (!isVisited(autoSite.center)) abandonShipwreckChest(reason, System.currentTimeMillis());
            else recoverShipwreckExit(reason);
            return;
        }
        if (state == AutoState.FAILED) return;
        restoreExitReturnSettings();

        if (autoSite != null) {
            autoLog("FAILED", autoSite.center, "reason=" + reason + " player=" + playerPosLabel());
            if (!autoSite.center.equals(autoWatchdogAbandonedSite)) markVisited(autoSite.center, "failed:" + reason);
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
        restoreAutoThrowawayItems();
        playAutoSound("spawner_failed", soundFailed);
        setChestplateHold(false);
        stateTicks = 0;
    }

    private void tickFailed() {
        if (isShipwreck()) {
            if (autoShipwreckRun == null) {
                autoShipwreckRun = new ShipwreckRun(System.currentTimeMillis());
                autoShipwreckRun.depart(System.currentTimeMillis());
            }
            if (!autoSoloTransitOnly && !isVisited(autoSite.center)) abandonShipwreckChest("exception", System.currentTimeMillis());
            else recoverShipwreckExit("exception");
            return;
        }
        stateTicks++;
        if (stateTicks < 20) return;
        if (autoSite != null && autoColumn != null && autoFailRecoveries < 2) {
            autoFailRecoveries++;
            autoRunAbandoned = true;
            autoLog("FAILED", autoSite.center, "FAILED recovering, climbing out before handing over (try " + autoFailRecoveries + ")");
            if (mc.player.isGliding()) {
                autoClimbTargetY = mc.player.getY() + climbHeight.get();
                enterState(AutoState.CLIMB, "after failure");
            } else {
                enterAscendOrSwimUp("after failure");
            }
            return;
        }
        if (autoSite != null) {
            autoLog("FAILED", autoSite.center, "FAILED -> IDLE");
        } else if (log.get()) {
            HunterBuddyAddon.LOG.info("[SpawnerDetector/auto] FAILED -> IDLE");
        }
        resetAutoState();
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
        public final ShipwreckSite shipwreck;
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
        public List<DugHoles.Feature> entryShafts = List.of();
        public boolean filled;
        public FilledShaftResult filledShaft;
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
            this.shipwreck = null;
            this.hasSpawner = hasSpawner;
            this.mob = mob;
            this.modern = modern;
        }

        Site(ShipwreckSite shipwreck, BlockPos chest) {
            this.center = chest.toImmutable();
            this.shipwreck = shipwreck;
            this.room = null;
            this.hasSpawner = false;
            this.mob = "shipwreck";
            this.modern = false;
            this.containers = 1;
            this.roomContainers = List.of(this.center);
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
