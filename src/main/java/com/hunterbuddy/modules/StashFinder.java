package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.regear.util.MapUtil;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.modules.regear.util.XaeroWaypointManager;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HangingSignBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public class StashFinder extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDetection = this.settings.createGroup("Detection");
   private final SettingGroup sgThresholds = this.settings.createGroup("Thresholds");
   private final SettingGroup sgPrivacy = this.settings.createGroup("Privacy");
   private final SettingGroup sgWaypoints = this.settings.createGroup("Waypoints");
   private final SettingGroup sgDiscord = this.settings.createGroup("Discord");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> minimumStorageCount = this.sgGeneral
      .add(
         new Builder().name("minimum-storage-count").description("Minimum storage blocks in a chunk to record it.")
               .defaultValue(10)
            .min(1)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Integer> minimumDistance = this.sgGeneral
      .add(
         new Builder().name("minimum-distance").description("Minimum distance from spawn to record chunks.").defaultValue(5000)
            .min(0)
            .sliderRange(0, 50000)
            .build()
      );
   private final Setting<Boolean> sendNotifications = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("notifications")
                  .description("Show notifications when stashes are found.")
               .defaultValue(true)
            .build()
      );
   private final Setting<StashFinder.NotificationMode> notificationMode = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<StashFinder.NotificationMode>()
                        .name("notification-mode")
                     .description("How to notify about found stashes.")
                  .defaultValue(StashFinder.NotificationMode.All)
               .visible(this.sendNotifications::get)
            .build()
      );
   private final Setting<Integer> clusterRadius = this.sgGeneral
      .add(
         new Builder().name("cluster-radius")
                  .description("Chunks within this radius are merged into one waypoint. 5 = 80 blocks.")
               .defaultValue(5)
            .min(1)
            .sliderRange(1, 32)
            .build()
      );
   private final Setting<Boolean> detectChests = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chests")
                  .description("Detect regular chests.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> detectTrappedChests = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("trapped-chests")
                  .description("Detect trapped chests (separately counted).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> detectBarrels = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("barrels")
                  .description("Detect barrels (common in villages - disable for base hunting).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectShulkers = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("shulkers")
                  .description("Detect shulker boxes (high-value player items).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> detectEnderChests = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ender-chests")
                  .description("Detect ender chests (always player-placed).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> detectFurnaces = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("furnaces")
                  .description("Detect furnaces, blast furnaces, and smokers.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectDispensers = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("dispensers-droppers")
                  .description("Detect dispensers and droppers.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectHoppers = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("hoppers")
                  .description("Detect hoppers (player farms/sorting systems).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> detectBrewingStands = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("brewing-stands")
                  .description("Detect brewing stands.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectCrafters = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("crafters")
                  .description("Detect crafters (1.21+).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectDecoratedPots = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("decorated-pots")
                  .description("Detect decorated pots (can store items).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectBanners = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("banners")
                  .description("Detect banners (common at pillager outposts - disable for base hunting).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectSigns = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("signs")
                  .description("Detect signs (often mark player bases/storage - common in villages too).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectHangingSigns = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("hanging-signs")
                  .description("Detect hanging signs (player-placed decoration/labels).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectMapItemFrames = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("map-item-frames")
                  .description("Detect item frames containing maps (map art/base maps).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> detectItemFrames = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("item-frames")
                  .description("Detect all item frames (not just maps).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectEnderPearls = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ender-pearls")
                  .description("Detect loaded ender pearl entities (pearls in stasis chambers).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> detectNamedEntities = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("named-entities")
                  .description("Detect entities with custom name tags (player's named mobs).")
               .defaultValue(true)
            .build()
      );
   private final Setting<List<Block>> blacklistedBlocks = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                  .name("blacklisted-support-blocks")
               .description("Ignore containers near these blocks (checks below, 2 below, and adjacent for wall-mounted blocks like banners).")
            .defaultValue(new Block[]{
                  Blocks.OXIDIZED_COPPER,
                  Blocks.OXIDIZED_CUT_COPPER,
                  Blocks.TUFF_BRICKS,
                  Blocks.WAXED_COPPER_BLOCK,
                  Blocks.WAXED_OXIDIZED_COPPER,
                  Blocks.WAXED_OXIDIZED_CUT_COPPER,
                  Blocks.WAXED_COPPER_BULB,
                  Blocks.BARREL,
                  Blocks.SMOOTH_STONE,
                  Blocks.STONE_BRICKS,
                  Blocks.MOSSY_STONE_BRICKS,
                  Blocks.CRACKED_STONE_BRICKS,
                  Blocks.DEEPSLATE_BRICKS,
                  Blocks.DEEPSLATE_TILES,
                  Blocks.POLISHED_DEEPSLATE,
                  Blocks.SCULK,
                  Blocks.COBBLESTONE,
                  Blocks.MOSSY_COBBLESTONE,
                  Blocks.SANDSTONE,
                  Blocks.CUT_SANDSTONE,
                  Blocks.CHISELED_SANDSTONE,
                  Blocks.MOSSY_COBBLESTONE,
                  Blocks.PRISMARINE,
                  Blocks.PRISMARINE_BRICKS,
                  Blocks.DARK_PRISMARINE,
                  Blocks.END_STONE_BRICKS,
                  Blocks.PURPUR_BLOCK,
                  Blocks.PURPUR_PILLAR,
                  Blocks.POLISHED_BLACKSTONE_BRICKS,
                  Blocks.POLISHED_BLACKSTONE,
                  Blocks.BLACKSTONE,
                  Blocks.GILDED_BLACKSTONE,
                  Blocks.NETHER_BRICKS,
                  Blocks.DARK_OAK_PLANKS,
                  Blocks.OAK_PLANKS,
                  Blocks.DARK_OAK_LOG,
                  Blocks.ACACIA_LOG,
                  Blocks.ACACIA_PLANKS,
                  Blocks.SPRUCE_PLANKS,
                  Blocks.NETHERRACK,
                  Blocks.CRYING_OBSIDIAN
               }
            )
            .build()
      );
   private final Setting<Boolean> oldChunksOnly = this.sgDetection
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("old-chunks-only")
                  .description("Only detect stashes in previously loaded chunks (using XaeroPlus chunk detection).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Integer> minChests = this.sgThresholds
      .add(
         new Builder().name("min-chests")
                  .description("Minimum chests to trigger notification (4+ filters dungeons/mineshafts).")
               .defaultValue(4)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minTrappedChests = this.sgThresholds
      .add(
         new Builder().name("min-trapped-chests")
                  .description("Minimum trapped chests to trigger notification (always player-placed).")
               .defaultValue(1)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minBarrels = this.sgThresholds
      .add(
         new Builder().name("min-barrels")
                  .description("Minimum barrels to trigger notification (villages have 1-2, player stashes more).")
               .defaultValue(4)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minShulkers = this.sgThresholds
      .add(
         new Builder().name("min-shulkers").description("Minimum shulker boxes to trigger notification (always player items).")
               .defaultValue(1)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minEnderChests = this.sgThresholds
      .add(
         new Builder().name("min-ender-chests")
                  .description("Minimum ender chests to trigger notification (always player-placed).")
               .defaultValue(1)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minFurnaces = this.sgThresholds
      .add(
         new Builder().name("min-furnaces")
                  .description("Minimum furnaces to trigger notification (villages have 1, player bases more).")
               .defaultValue(2)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minDispensers = this.sgThresholds
      .add(
         new Builder().name("min-dispensers")
                  .description("Minimum dispensers/droppers to trigger notification (usually player contraptions).")
               .defaultValue(2)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minHoppers = this.sgThresholds
      .add(
         new Builder().name("min-hoppers")
                  .description("Minimum hoppers to trigger notification (player farms usually have 3+).")
               .defaultValue(3)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minBrewingStands = this.sgThresholds
      .add(
         new Builder().name("min-brewing-stands")
                  .description("Minimum brewing stands to trigger notification (always player-placed).")
               .defaultValue(1)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minCrafters = this.sgThresholds
      .add(
         new Builder().name("min-crafters")
                  .description("Minimum crafters to trigger notification (always player-placed, 1.21+ block).")
               .defaultValue(1)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minDecoratedPots = this.sgThresholds
      .add(
         new Builder().name("min-decorated-pots")
                  .description("Minimum decorated pots to trigger notification (trail ruins have some, player builds more).")
               .defaultValue(3)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minBanners = this.sgThresholds
      .add(
         new Builder().name("min-banners")
                  .description("Minimum banners to trigger notification (outposts have few, player bases more).")
               .defaultValue(2)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minSigns = this.sgThresholds
      .add(
         new Builder().name("min-signs")
                  .description("Minimum signs to trigger notification (villages have some, player bases more).")
               .defaultValue(3)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minHangingSigns = this.sgThresholds
      .add(
         new Builder().name("min-hanging-signs").description("Minimum hanging signs to trigger notification.").defaultValue(2)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minMapItemFrames = this.sgThresholds
      .add(
         new Builder().name("min-map-item-frames")
                  .description("Minimum map item frames to trigger notification (map art usually has 2+).")
               .defaultValue(2)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minItemFrames = this.sgThresholds
      .add(
         new Builder().name("min-item-frames").description("Minimum item frames to trigger notification.").defaultValue(4)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minEnderPearls = this.sgThresholds
      .add(
         new Builder().name("min-ender-pearls")
                  .description("Minimum ender pearls to trigger notification (1+ = stasis chamber).")
               .defaultValue(1)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> minNamedEntities = this.sgThresholds
      .add(
         new Builder().name("min-named-entities").description("Minimum named entities to trigger notification.")
               .defaultValue(1)
            .min(0)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Boolean> streamerMode = this.sgPrivacy
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("streamer-mode")
                  .description("Censor all coordinates in the widget and coordinate list. Copy and Go buttons still use the real coordinates.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> hideCoordinates = this.sgPrivacy
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("hide-coordinates")
                  .description("Hide coordinates in the main module widget.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> noCoordChat = this.sgPrivacy
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("no-coord-chat")
                  .description("Never show coordinates in chat messages.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Keybind> openCoordListBind = this.sgPrivacy
      .add(
         new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                     .name("open-coordinate-list")
                  .description("Keybind to open the coordinate list.")
               .defaultValue(Keybind.none()).build()
      );
   private final Setting<Boolean> renderTracer = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render-tracer")
                  .description("Render tracers to stash locations.")
               .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> traceColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("tracer-color")
                  .description("Color of stash tracers.")
               .defaultValue(new SettingColor(255, 215, 0, 255)).visible(this.renderTracer::get)
            .build()
      );
   private final Setting<Integer> traceArrivalDistance = this.sgRender
      .add(
         new Builder().name("hide-at-distance").description("Hide tracer when within this distance.")
                  .defaultValue(16)
               .min(1)
               .sliderRange(1, 100)
               .visible(this.renderTracer::get)
            .build()
      );
   private final Setting<Integer> traceMaxDistance = this.sgRender
      .add(
         new Builder().name("max-trace-distance").description("Maximum distance to render tracers.")
                  .defaultValue(5000)
               .min(100)
               .sliderRange(100, 30000)
               .visible(this.renderTracer::get)
            .build()
      );
   private final Setting<Boolean> renderChunkColumn = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render-column")
                  .description("Render vertical column at stash locations.")
               .defaultValue(false)
            .build()
      );
   private final Setting<SettingColor> columnColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("column-color")
                  .description("Color of the chunk column.")
               .defaultValue(new SettingColor(255, 215, 0, 100)).visible(this.renderChunkColumn::get)
            .build()
      );
   private final Setting<Keybind> clearTracesBind = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                     .name("clear-traces")
                  .description("Keybind to clear all active tracers.")
               .defaultValue(Keybind.none()).build()
      );
   private final Setting<Boolean> addWaypoints = this.sgWaypoints
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("add-waypoints")
                     .description("Add Xaero waypoints for found stashes.")
                  .defaultValue(false)
               .visible(() -> Utils.XAERO_AVAILABLE)
            .build()
      );
   private final Setting<Boolean> tempWaypoints = this.sgWaypoints
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("temporary-waypoints")
                     .description("Waypoints are removed when you disconnect.")
                  .defaultValue(true)
               .visible(() -> Utils.XAERO_AVAILABLE && (Boolean)this.addWaypoints.get())
               .build()
      );
   private final Setting<Boolean> useSymbols = this.sgWaypoints
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("use-symbols")
                     .description("Use symbols as waypoint name based on container types that meet thresholds.")
                  .defaultValue(true)
               .visible(() -> Utils.XAERO_AVAILABLE && (Boolean)this.addWaypoints.get())
               .build()
      );
   private static final String CHEST_SYMBOL = "\ud83d\udce6";
   private static final String TRAPPED_CHEST_SYMBOL = "\ud83e\udea4";
   private static final String BARREL_SYMBOL = "\ud83d\udee2";
   private static final String SHULKER_SYMBOL = "\ud83c\udf81";
   private static final String ENDER_CHEST_SYMBOL = "\u2601";
   private static final String FURNACE_SYMBOL = "\ud83d\udd25";
   private static final String DISPENSER_SYMBOL = "\u2b07";
   private static final String HOPPER_SYMBOL = "\u23ec";
   private static final String BREWING_STAND_SYMBOL = "\ud83e\uddea";
   private static final String CRAFTER_SYMBOL = "\ud83d\udd27";
   private static final String DECORATED_POT_SYMBOL = "\ud83c\udffa";
   private static final String BANNER_SYMBOL = "\ud83d\udea9";
   private static final String SIGN_SYMBOL = "\ud83e\udea7";
   private static final String HANGING_SIGN_SYMBOL = "\ud83e\ude9d";
   private static final String MAP_ITEM_FRAME_SYMBOL = "\ud83d\uddfa";
   private static final String ITEM_FRAME_SYMBOL = "\ud83d\uddbc";
   private static final String ENDER_PEARL_SYMBOL = "\ud83d\udc41";
   private static final String NAMED_ENTITY_SYMBOL = "\ud83c\udff7";
   private final Setting<Boolean> showQuantity = this.sgWaypoints
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("show-quantity")
                     .description("Append total container count to waypoint name.")
                  .defaultValue(true)
               .visible(() -> Utils.XAERO_AVAILABLE && (Boolean)this.addWaypoints.get())
               .build()
      );
   private final Setting<MapUtil.WpColor> waypointColor = this.sgWaypoints
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MapUtil.WpColor>()
                        .name("waypoint-color")
                     .description("Color of stash waypoints.")
                  .defaultValue(MapUtil.WpColor.Gold)
               .visible(() -> Utils.XAERO_AVAILABLE && (Boolean)this.addWaypoints.get())
               .build()
      );
   private final Setting<Boolean> discordEnabled = this.sgDiscord
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("discord-webhook")
                  .description("Send stash notifications to a Discord webhook.")
               .defaultValue(false)
            .build()
      );
   private final Setting<String> discordWebhookUrl = this.sgDiscord
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                        .name("webhook-url")
                     .description("Discord webhook URL.")
                  .defaultValue("")
               .visible(this.discordEnabled::get)
            .build()
      );
   private final Setting<Boolean> discordIncludeCoords = this.sgDiscord
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("include-coordinates")
                     .description("Include coordinates in Discord messages (careful with privacy!).")
                  .defaultValue(false)
               .visible(this.discordEnabled::get)
            .build()
      );
   private final Setting<Boolean> discordIncludeBreakdown = this.sgDiscord
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("include-breakdown")
                     .description("Include container type breakdown in Discord messages.")
                  .defaultValue(true)
               .visible(this.discordEnabled::get)
            .build()
      );
   private final Setting<String> discordUsername = this.sgDiscord
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                        .name("webhook-username")
                     .description("Custom username for the webhook bot.")
                  .defaultValue("StashFinder")
               .visible(this.discordEnabled::get)
            .build()
      );
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final Map<ChunkPos, Vec3d> tracerPositions = new HashMap<>();
   private final Set<ChunkPos> notifiedChunks = new HashSet<>();
   private final Map<String, StashFinder.StashChunk> clusterWaypoints = new HashMap<>();
   private final ExecutorService discordExecutor = Executors.newSingleThreadExecutor();
   private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   public List<StashFinder.StashChunk> chunks = new ArrayList<>();
   private boolean loaded = false;
   private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
   private static final String CATEGORY = "StashFinder";

   public StashFinder() {
      super(Categories.World, "stash-finder", "Enhanced stash detection with privacy-focused coordinate management.");
   }

   public void onActivate() {
      this.chunks = new ArrayList<>();
      this.loaded = false;
      this.notifiedChunks.clear();
      this.clusterWaypoints.clear();
   }

   @EventHandler
   private void onTick(Post event) {
      if (((Keybind)this.clearTracesBind.get()).isPressed()) {
         this.tracerPositions.clear();
      }

      if (((Keybind)this.openCoordListBind.get()).isPressed() && this.mc.currentScreen == null) {
         this.openCoordinateList();
      }
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         double chunkXAbs = Math.abs(event.chunk().getPos().x * 16);
         double chunkZAbs = Math.abs(event.chunk().getPos().z * 16);
         if (!(Math.sqrt(chunkXAbs * chunkXAbs + chunkZAbs * chunkZAbs) < ((Integer)this.minimumDistance.get()).intValue())) {
            if ((Boolean)this.oldChunksOnly.get()) {
               ChunkPos cp = event.chunk().getPos();
               RegistryKey<World> dim = this.mc.world.getRegistryKey();
               PaletteNewChunks paletteNewChunks = (PaletteNewChunks)ModuleManager.getModule(PaletteNewChunks.class);
               OldChunks oldChunksModule = (OldChunks)ModuleManager.getModule(OldChunks.class);
               boolean isNewChunk = paletteNewChunks.isNewChunk(cp.x, cp.z, dim);
               boolean isOldChunk = oldChunksModule.isOldChunk(cp.x, cp.z, dim);
               if (isNewChunk && !isOldChunk) {
                  return;
               }
            }

            StashFinder.StashChunk chunk = new StashFinder.StashChunk(event.chunk().getPos());
            chunk.dimension = this.getCurrentDimension();
            List<Block> blockBlacklist = (List<Block>)this.blacklistedBlocks.get();

            for (BlockEntity blockEntity : event.chunk().getBlockEntities().values()) {
               if (!blockBlacklist.isEmpty()) {
                  boolean isWallMounted = blockEntity instanceof BannerBlockEntity || blockEntity instanceof SignBlockEntity;
                  if (this.isNearBlacklistedBlock(blockEntity.getPos(), blockBlacklist, isWallMounted)) {
                     continue;
                  }
               }

               if (blockEntity instanceof TrappedChestBlockEntity && (Boolean)this.detectTrappedChests.get()) {
                  chunk.trappedChests++;
               } else if (blockEntity instanceof ChestBlockEntity && (Boolean)this.detectChests.get()) {
                  chunk.chests++;
               } else if (blockEntity instanceof BarrelBlockEntity && (Boolean)this.detectBarrels.get()) {
                  chunk.barrels++;
               } else if (blockEntity instanceof ShulkerBoxBlockEntity && (Boolean)this.detectShulkers.get()) {
                  chunk.shulkers++;
               } else if (blockEntity instanceof EnderChestBlockEntity && (Boolean)this.detectEnderChests.get()) {
                  chunk.enderChests++;
               } else if (blockEntity instanceof AbstractFurnaceBlockEntity && (Boolean)this.detectFurnaces.get()) {
                  chunk.furnaces++;
               } else if (blockEntity instanceof DispenserBlockEntity && (Boolean)this.detectDispensers.get()) {
                  chunk.dispensersDroppers++;
               } else if (blockEntity instanceof HopperBlockEntity && (Boolean)this.detectHoppers.get()) {
                  chunk.hoppers++;
               } else if (blockEntity instanceof BrewingStandBlockEntity && (Boolean)this.detectBrewingStands.get()) {
                  chunk.brewingStands++;
               } else if (blockEntity instanceof CrafterBlockEntity && (Boolean)this.detectCrafters.get()) {
                  chunk.crafters++;
               } else if (blockEntity instanceof DecoratedPotBlockEntity && (Boolean)this.detectDecoratedPots.get()) {
                  chunk.decoratedPots++;
               } else if (blockEntity instanceof BannerBlockEntity && (Boolean)this.detectBanners.get()) {
                  chunk.banners++;
               } else if (blockEntity instanceof HangingSignBlockEntity && (Boolean)this.detectHangingSigns.get()) {
                  chunk.hangingSigns++;
               } else if (blockEntity instanceof SignBlockEntity && (Boolean)this.detectSigns.get()) {
                  chunk.signs++;
               }
            }

            if (chunk.getTotal() >= (Integer)this.minimumStorageCount.get() || this.meetsThresholds(chunk)) {
               StashFinder.StashChunk prevChunk = null;
               int i = this.chunks.indexOf(chunk);
               if (i < 0) {
                  this.chunks.add(chunk);
               } else {
                  prevChunk = this.chunks.set(i, chunk);
               }

               if ((Boolean)this.renderTracer.get()) {
                  double y = this.mc.player.getEyeY();
                  this.tracerPositions.put(chunk.chunkPos, new Vec3d(chunk.x, y, chunk.z));
               }

               this.save();
               boolean isNew = prevChunk == null || !chunk.countsEqual(prevChunk);
               if (isNew && this.meetsThresholds(chunk)) {
                  this.addWaypoint(chunk);
                  if ((Boolean)this.sendNotifications.get()) {
                     this.sendNotification(chunk);
                  }

                  this.sendDiscordNotification(chunk);
               }
            }
         }
      }
   }

   @EventHandler
   private void onEntityAdded(EntityAddedEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         Entity entity = event.entity;
         boolean detected = false;
         String detectionType = null;
         ChunkPos chunkPos;
         if ((Boolean)this.detectEnderPearls.get() && entity instanceof EnderPearlEntity) {
            chunkPos = new ChunkPos(entity.getBlockPos());
            detectionType = "enderPearl";
            detected = true;
         } else if ((Boolean)this.detectNamedEntities.get() && entity instanceof LivingEntity living) {
            if (!living.hasCustomName()) {
               return;
            }

            chunkPos = new ChunkPos(entity.getBlockPos());
            detectionType = "namedEntity";
            detected = true;
         } else {
            if (!(entity instanceof ItemFrameEntity itemFrame)) {
               return;
            }

            chunkPos = new ChunkPos(itemFrame.getBlockPos());
            if ((Boolean)this.detectMapItemFrames.get() && itemFrame.getHeldItemStack().contains(DataComponentTypes.MAP_ID)) {
               detectionType = "mapItemFrame";
               detected = true;
            } else {
               if (!(Boolean)this.detectItemFrames.get() || itemFrame.getHeldItemStack().isEmpty()) {
                  return;
               }

               detectionType = "itemFrame";
               detected = true;
            }
         }

         if (detected) {
            double chunkXAbs = Math.abs(chunkPos.x * 16);
            double chunkZAbs = Math.abs(chunkPos.z * 16);
            if (!(Math.sqrt(chunkXAbs * chunkXAbs + chunkZAbs * chunkZAbs) < ((Integer)this.minimumDistance.get()).intValue())) {
               StashFinder.StashChunk chunk = null;

               for (StashFinder.StashChunk c : this.chunks) {
                  if (c.chunkPos.equals(chunkPos)) {
                     chunk = c;
                     break;
                  }
               }

               if (chunk == null) {
                  chunk = new StashFinder.StashChunk(chunkPos);
                  chunk.dimension = this.getCurrentDimension();
               }

               switch (detectionType) {
                  case "enderPearl":
                     chunk.enderPearls++;
                     break;
                  case "namedEntity":
                     chunk.namedEntities++;
                     break;
                  case "mapItemFrame":
                     chunk.mapItemFrames++;
                     break;
                  case "itemFrame":
                     chunk.itemFrames++;
               }

               if (chunk.getTotal() >= (Integer)this.minimumStorageCount.get() || this.meetsThresholds(chunk)) {
                  StashFinder.StashChunk prevChunk = null;
                  int i = this.chunks.indexOf(chunk);
                  if (i < 0) {
                     this.chunks.add(chunk);
                  } else {
                     prevChunk = this.chunks.set(i, chunk);
                  }

                  if ((Boolean)this.renderTracer.get()) {
                     double y = this.mc.player.getEyeY();
                     this.tracerPositions.put(chunk.chunkPos, new Vec3d(chunk.x, y, chunk.z));
                  }

                  this.save();
                  boolean isNew = prevChunk == null || !chunk.countsEqual(prevChunk);
                  if (isNew && this.meetsThresholds(chunk)) {
                     this.addWaypoint(chunk);
                     if ((Boolean)this.sendNotifications.get()) {
                        this.sendNotification(chunk);
                     }

                     this.sendDiscordNotification(chunk);
                  }
               }
            }
         }
      }
   }

   private boolean meetsThresholds(StashFinder.StashChunk chunk) {
      boolean allZero = (Integer)this.minChests.get() == 0
         && (Integer)this.minTrappedChests.get() == 0
         && (Integer)this.minBarrels.get() == 0
         && (Integer)this.minShulkers.get() == 0
         && (Integer)this.minEnderChests.get() == 0
         && (Integer)this.minFurnaces.get() == 0
         && (Integer)this.minDispensers.get() == 0
         && (Integer)this.minHoppers.get() == 0
         && (Integer)this.minBrewingStands.get() == 0
         && (Integer)this.minCrafters.get() == 0
         && (Integer)this.minDecoratedPots.get() == 0
         && (Integer)this.minBanners.get() == 0
         && (Integer)this.minSigns.get() == 0
         && (Integer)this.minHangingSigns.get() == 0
         && (Integer)this.minMapItemFrames.get() == 0
         && (Integer)this.minItemFrames.get() == 0
         && (Integer)this.minEnderPearls.get() == 0
         && (Integer)this.minNamedEntities.get() == 0;
      if (allZero) {
         return true;
      } else if ((Integer)this.minChests.get() > 0 && chunk.chests >= (Integer)this.minChests.get()) {
         return true;
      } else if ((Integer)this.minTrappedChests.get() > 0 && chunk.trappedChests >= (Integer)this.minTrappedChests.get()) {
         return true;
      } else if ((Integer)this.minBarrels.get() > 0 && chunk.barrels >= (Integer)this.minBarrels.get()) {
         return true;
      } else if ((Integer)this.minShulkers.get() > 0 && chunk.shulkers >= (Integer)this.minShulkers.get()) {
         return true;
      } else if ((Integer)this.minEnderChests.get() > 0 && chunk.enderChests >= (Integer)this.minEnderChests.get()) {
         return true;
      } else if ((Integer)this.minFurnaces.get() > 0 && chunk.furnaces >= (Integer)this.minFurnaces.get()) {
         return true;
      } else if ((Integer)this.minDispensers.get() > 0 && chunk.dispensersDroppers >= (Integer)this.minDispensers.get()) {
         return true;
      } else if ((Integer)this.minHoppers.get() > 0 && chunk.hoppers >= (Integer)this.minHoppers.get()) {
         return true;
      } else if ((Integer)this.minBrewingStands.get() > 0 && chunk.brewingStands >= (Integer)this.minBrewingStands.get()) {
         return true;
      } else if ((Integer)this.minCrafters.get() > 0 && chunk.crafters >= (Integer)this.minCrafters.get()) {
         return true;
      } else if ((Integer)this.minDecoratedPots.get() > 0 && chunk.decoratedPots >= (Integer)this.minDecoratedPots.get()) {
         return true;
      } else if ((Integer)this.minBanners.get() > 0 && chunk.banners >= (Integer)this.minBanners.get()) {
         return true;
      } else if ((Integer)this.minSigns.get() > 0 && chunk.signs >= (Integer)this.minSigns.get()) {
         return true;
      } else if ((Integer)this.minHangingSigns.get() > 0 && chunk.hangingSigns >= (Integer)this.minHangingSigns.get()) {
         return true;
      } else if ((Integer)this.minMapItemFrames.get() > 0 && chunk.mapItemFrames >= (Integer)this.minMapItemFrames.get()) {
         return true;
      } else if ((Integer)this.minItemFrames.get() > 0 && chunk.itemFrames >= (Integer)this.minItemFrames.get()) {
         return true;
      } else {
         return this.minEnderPearls.get() > 0 && chunk.enderPearls >= this.minEnderPearls.get()
            ? true
            : (Integer)this.minNamedEntities.get() > 0 && chunk.namedEntities >= (Integer)this.minNamedEntities.get();
      }
   }

   private boolean isNearBlacklistedBlock(BlockPos pos, List<Block> blacklist, boolean checkHorizontalAdjacent) {
      if (this.mc.world == null) {
         return false;
      }

      if (blacklist.contains(this.mc.world.getBlockState(pos.down()).getBlock())) {
         return true;
      }

      if (blacklist.contains(this.mc.world.getBlockState(pos.down(2)).getBlock())) {
         return true;
      }

      if (checkHorizontalAdjacent) {
         if (blacklist.contains(this.mc.world.getBlockState(pos.north()).getBlock())) {
            return true;
         }

         if (blacklist.contains(this.mc.world.getBlockState(pos.south()).getBlock())) {
            return true;
         }

         if (blacklist.contains(this.mc.world.getBlockState(pos.east()).getBlock())) {
            return true;
         }

         if (blacklist.contains(this.mc.world.getBlockState(pos.west()).getBlock())) {
            return true;
         }
      }

      return false;
   }

   private String getClusterId(StashFinder.StashChunk chunk) {
      int radius = (Integer)this.clusterRadius.get();
      int clusterX = Math.floorDiv(chunk.chunkPos.x, radius);
      int clusterZ = Math.floorDiv(chunk.chunkPos.z, radius);
      return chunk.dimension + ":" + clusterX + ":" + clusterZ;
   }

   private void addWaypoint(StashFinder.StashChunk chunk) {
      if (XaeroWaypointManager.isAvailable() && (Boolean)this.addWaypoints.get()) {
         String clusterId = this.getClusterId(chunk);
         StashFinder.StashChunk existingCluster = this.clusterWaypoints.get(clusterId);
         boolean isNewCluster = existingCluster == null;
         int oldX = 0;
         int oldZ = 0;
         if (!isNewCluster) {
            oldX = existingCluster.x;
            oldZ = existingCluster.z;
         }

         StashFinder.StashChunk mergedChunk = new StashFinder.StashChunk(chunk.chunkPos);
         mergedChunk.dimension = chunk.dimension;
         int bestTotal = 0;
         int radius = (Integer)this.clusterRadius.get();

         for (StashFinder.StashChunk c : this.chunks) {
            if (Objects.equals(c.dimension, chunk.dimension)) {
               int clusterX = Math.floorDiv(c.chunkPos.x, radius);
               int clusterZ = Math.floorDiv(c.chunkPos.z, radius);
               String cId = c.dimension + ":" + clusterX + ":" + clusterZ;
               if (cId.equals(clusterId)) {
                  mergedChunk.chests = mergedChunk.chests + c.chests;
                  mergedChunk.trappedChests = mergedChunk.trappedChests + c.trappedChests;
                  mergedChunk.barrels = mergedChunk.barrels + c.barrels;
                  mergedChunk.shulkers = mergedChunk.shulkers + c.shulkers;
                  mergedChunk.enderChests = mergedChunk.enderChests + c.enderChests;
                  mergedChunk.furnaces = mergedChunk.furnaces + c.furnaces;
                  mergedChunk.dispensersDroppers = mergedChunk.dispensersDroppers + c.dispensersDroppers;
                  mergedChunk.hoppers = mergedChunk.hoppers + c.hoppers;
                  mergedChunk.brewingStands = mergedChunk.brewingStands + c.brewingStands;
                  mergedChunk.crafters = mergedChunk.crafters + c.crafters;
                  mergedChunk.decoratedPots = mergedChunk.decoratedPots + c.decoratedPots;
                  mergedChunk.banners = mergedChunk.banners + c.banners;
                  mergedChunk.signs = mergedChunk.signs + c.signs;
                  mergedChunk.hangingSigns = mergedChunk.hangingSigns + c.hangingSigns;
                  mergedChunk.mapItemFrames = mergedChunk.mapItemFrames + c.mapItemFrames;
                  mergedChunk.itemFrames = mergedChunk.itemFrames + c.itemFrames;
                  mergedChunk.enderPearls = mergedChunk.enderPearls + c.enderPearls;
                  mergedChunk.namedEntities = mergedChunk.namedEntities + c.namedEntities;
                  if (c.getTotal() > bestTotal) {
                     bestTotal = c.getTotal();
                     mergedChunk.x = c.x;
                     mergedChunk.z = c.z;
                  }
               }
            }
         }

         this.clusterWaypoints.put(clusterId, mergedChunk);
         String name;
         String initials;
         if ((Boolean)this.useSymbols.get()) {
            StringBuilder symbolBuilder = new StringBuilder();
            if ((Integer)this.minChests.get() > 0 && mergedChunk.chests >= (Integer)this.minChests.get()) {
               symbolBuilder.append("\ud83d\udce6");
            }

            if ((Integer)this.minTrappedChests.get() > 0 && mergedChunk.trappedChests >= (Integer)this.minTrappedChests.get()) {
               symbolBuilder.append("\ud83e\udea4");
            }

            if ((Integer)this.minBarrels.get() > 0 && mergedChunk.barrels >= (Integer)this.minBarrels.get()) {
               symbolBuilder.append("\ud83d\udee2");
            }

            if ((Integer)this.minShulkers.get() > 0 && mergedChunk.shulkers >= (Integer)this.minShulkers.get()) {
               symbolBuilder.append("\ud83c\udf81");
            }

            if ((Integer)this.minEnderChests.get() > 0 && mergedChunk.enderChests >= (Integer)this.minEnderChests.get()) {
               symbolBuilder.append("\u2601");
            }

            if ((Integer)this.minFurnaces.get() > 0 && mergedChunk.furnaces >= (Integer)this.minFurnaces.get()) {
               symbolBuilder.append("\ud83d\udd25");
            }

            if ((Integer)this.minDispensers.get() > 0 && mergedChunk.dispensersDroppers >= (Integer)this.minDispensers.get()) {
               symbolBuilder.append("\u2b07");
            }

            if ((Integer)this.minHoppers.get() > 0 && mergedChunk.hoppers >= (Integer)this.minHoppers.get()) {
               symbolBuilder.append("\u23ec");
            }

            if ((Integer)this.minBrewingStands.get() > 0 && mergedChunk.brewingStands >= (Integer)this.minBrewingStands.get()) {
               symbolBuilder.append("\ud83e\uddea");
            }

            if ((Integer)this.minCrafters.get() > 0 && mergedChunk.crafters >= (Integer)this.minCrafters.get()) {
               symbolBuilder.append("\ud83d\udd27");
            }

            if ((Integer)this.minDecoratedPots.get() > 0 && mergedChunk.decoratedPots >= (Integer)this.minDecoratedPots.get()) {
               symbolBuilder.append("\ud83c\udffa");
            }

            if ((Integer)this.minBanners.get() > 0 && mergedChunk.banners >= (Integer)this.minBanners.get()) {
               symbolBuilder.append("\ud83d\udea9");
            }

            if ((Integer)this.minSigns.get() > 0 && mergedChunk.signs >= (Integer)this.minSigns.get()) {
               symbolBuilder.append("\ud83e\udea7");
            }

            if ((Integer)this.minHangingSigns.get() > 0 && mergedChunk.hangingSigns >= (Integer)this.minHangingSigns.get()) {
               symbolBuilder.append("\ud83e\ude9d");
            }

            if ((Integer)this.minMapItemFrames.get() > 0 && mergedChunk.mapItemFrames >= (Integer)this.minMapItemFrames.get()) {
               symbolBuilder.append("\ud83d\uddfa");
            }

            if ((Integer)this.minItemFrames.get() > 0 && mergedChunk.itemFrames >= (Integer)this.minItemFrames.get()) {
               symbolBuilder.append("\ud83d\uddbc");
            }

            if ((Integer)this.minEnderPearls.get() > 0 && mergedChunk.enderPearls >= (Integer)this.minEnderPearls.get()) {
               symbolBuilder.append("\ud83d\udc41");
            }

            if ((Integer)this.minNamedEntities.get() > 0 && mergedChunk.namedEntities >= (Integer)this.minNamedEntities.get()) {
               symbolBuilder.append("\ud83c\udff7");
            }

            String symbols = symbolBuilder.toString();
            if (symbols.isEmpty()) {
               symbols = "\ud83d\udce6";
            }

            initials = symbols;
            if ((Boolean)this.showQuantity.get()) {
               name = symbols + " " + mergedChunk.getTotal();
            } else {
               name = symbols;
            }
         } else {
            initials = "S";
            if ((Boolean)this.showQuantity.get()) {
               name = "Stash (" + mergedChunk.getTotal() + ")";
            } else {
               name = "Stash";
            }
         }

         if (!isNewCluster) {
            XaeroWaypointManager.removeWaypointXZ("StashFinder", oldX, oldZ);
         }

         XaeroWaypointManager.addWaypoint(
            "StashFinder",
            new BlockPos(mergedChunk.x, 64, mergedChunk.z),
            name,
            initials,
            (MapUtil.WpColor)this.waypointColor.get(),
            (Boolean)this.tempWaypoints.get(),
            mergedChunk.dimension
         );
      }
   }

   private String escapeDiscordJson(String value) {
      if (value == null) {
         return "";
      }

      return value
         .replace("\\", "\\\\")
         .replace("\"", "\\\"")
         .replace("\n", "\\n")
         .replace("\r", "\\r");
   }

   private void sendDiscordNotification(StashFinder.StashChunk chunk) {
      if ((Boolean)this.discordEnabled.get() && !((String)this.discordWebhookUrl.get()).isEmpty()) {
         this.discordExecutor
            .submit(
               () -> {
                  try {
                     StringBuilder content = new StringBuilder();
                     content.append("**🗃️ Stash Found!**\n");
                     content.append("**Total Containers:** ").append(chunk.getTotal()).append("\n");
                     content.append("**Dimension:** ").append(chunk.dimension).append("\n");
                     if ((Boolean)this.discordIncludeCoords.get()) {
                        content.append("**Coordinates:** `").append(chunk.x).append(", ").append(chunk.z).append("`\n");
                     }

                     if ((Boolean)this.discordIncludeBreakdown.get()) {
                        content.append("\n**Breakdown:**\n");
                        if (chunk.chests > 0) {
                           content.append("• Chests: ").append(chunk.chests).append("\n");
                        }

                        if (chunk.trappedChests > 0) {
                           content.append("• Trapped Chests: ").append(chunk.trappedChests).append("\n");
                        }

                        if (chunk.barrels > 0) {
                           content.append("• Barrels: ").append(chunk.barrels).append("\n");
                        }

                        if (chunk.shulkers > 0) {
                           content.append("• Shulkers: ").append(chunk.shulkers).append("\n");
                        }

                        if (chunk.enderChests > 0) {
                           content.append("• Ender Chests: ").append(chunk.enderChests).append("\n");
                        }

                        if (chunk.furnaces > 0) {
                           content.append("• Furnaces: ").append(chunk.furnaces).append("\n");
                        }

                        if (chunk.dispensersDroppers > 0) {
                           content.append("• Dispensers/Droppers: ").append(chunk.dispensersDroppers).append("\n");
                        }

                        if (chunk.hoppers > 0) {
                           content.append("• Hoppers: ").append(chunk.hoppers).append("\n");
                        }

                        if (chunk.brewingStands > 0) {
                           content.append("• Brewing Stands: ").append(chunk.brewingStands).append("\n");
                        }

                        if (chunk.crafters > 0) {
                           content.append("• Crafters: ").append(chunk.crafters).append("\n");
                        }

                        if (chunk.decoratedPots > 0) {
                           content.append("• Decorated Pots: ").append(chunk.decoratedPots).append("\n");
                        }

                        if (chunk.banners > 0) {
                           content.append("• Banners: ").append(chunk.banners).append("\n");
                        }

                        if (chunk.signs > 0) {
                           content.append("• Signs: ").append(chunk.signs).append("\n");
                        }

                        if (chunk.hangingSigns > 0) {
                           content.append("• Hanging Signs: ").append(chunk.hangingSigns).append("\n");
                        }

                        if (chunk.mapItemFrames > 0) {
                           content.append("• Map Item Frames: ").append(chunk.mapItemFrames).append("\n");
                        }

                        if (chunk.itemFrames > 0) {
                           content.append("• Item Frames: ").append(chunk.itemFrames).append("\n");
                        }

                        if (chunk.enderPearls > 0) {
                           content.append("• Ender Pearls: ").append(chunk.enderPearls).append("\n");
                        }

                        if (chunk.namedEntities > 0) {
                           content.append("• Named Entities: ").append(chunk.namedEntities).append("\n");
                        }
                     }

                     String json = "{\"username\":\"" + this.escapeDiscordJson((String)this.discordUsername.get()) + "\",\"content\":\"" + this.escapeDiscordJson(content.toString()) + "\"}";
                     HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create((String)this.discordWebhookUrl.get()))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(json)).build();
                     this.httpClient.send(request, BodyHandlers.ofString());
                  } catch (Exception e) {
                     MeteorClient.LOG.error("Failed to send Discord webhook", e);
                  }
               }
            );
      }
   }

   private void sendNotification(StashFinder.StashChunk chunk) {
      boolean hideCoords = (Boolean)this.noCoordChat.get() || (Boolean)this.streamerMode.get();
      String message = hideCoords ? "Found stash! (" + chunk.getTotal() + " containers)" : "Found stash at [" + chunk.x + ", " + chunk.z + "]";
      switch ((StashFinder.NotificationMode)this.notificationMode.get()) {
         case Chat:
            if (!hideCoords) {
               ChatUtils.info("StashFinder", new Object[]{message});
            } else {
               ChatUtils.info("StashFinder", new Object[]{"Found stash! Use secure menu to view coordinates."});
            }
            break;
         case Toast: {
            MeteorToast toast = new meteordevelopment.meteorclient.utils.render.MeteorToast(Items.CHEST, this.title, "Stash Found!");
            this.mc.getToastManager().add(toast);
            break;
         }
         case Sound:
            this.playDingSound();
            break;
         case Both: {
            if (!hideCoords) {
               ChatUtils.info("StashFinder", new Object[]{message});
            } else {
               ChatUtils.info("StashFinder", new Object[]{"Found stash! Use secure menu to view coordinates."});
            }

            MeteorToast toast = new meteordevelopment.meteorclient.utils.render.MeteorToast(Items.CHEST, this.title, "Stash Found!");
            this.mc.getToastManager().add(toast);
            break;
         }
         case All: {
            if (!hideCoords) {
               ChatUtils.info("StashFinder", new Object[]{message});
            } else {
               ChatUtils.info("StashFinder", new Object[]{"Found stash! Use secure menu to view coordinates."});
            }

            MeteorToast toast = new meteordevelopment.meteorclient.utils.render.MeteorToast(Items.CHEST, this.title, "Stash Found!");
            this.mc.getToastManager().add(toast);
            this.playDingSound();
            break;
         }
         case Silent:
      }
   }

   private void playDingSound() {
      if (this.mc.player != null) {
         this.mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
         new Thread(() -> {
            try {
               Thread.sleep(150L);
               if (this.mc.player != null) {
                  this.mc.execute(() -> this.mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F));
               }

               Thread.sleep(150L);
               if (this.mc.player != null) {
                  this.mc.execute(() -> this.mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.4F));
               }
            } catch (InterruptedException var2) {
            }
         }).start();
      }
   }

   public WWidget getWidget(GuiTheme theme) {
      this.ensureLoaded();
      WVerticalList list = theme.verticalList();
      WHorizontalList stats = theme.horizontalList();
      stats.add(theme.label("Total Stashes: " + this.chunks.size()));
      list.add(stats);
      WHorizontalList buttons = theme.horizontalList();
      WButton openSecure = (WButton)buttons.add(theme.button("Open Coordinate List")).widget();
      openSecure.action = this::openCoordinateList;
      WButton clearAll = (WButton)buttons.add(theme.button("Clear All")).widget();
      clearAll.action = () -> {
         this.chunks.clear();
         this.tracerPositions.clear();
         this.loaded = true;
         this.save();
      };
      WButton resetTracers = (WButton)buttons.add(theme.button("Reset Tracers")).widget();
      resetTracers.action = () -> this.tracerPositions.clear();
      WButton testEmojis = (WButton)buttons.add(theme.button("Test Emojis")).widget();
      testEmojis.action = this::createTestWaypoints;
      list.add(buttons);
      if (!this.chunks.isEmpty()) {
         WTable dimTable = theme.table();
         Map<String, Integer> dimCounts = new HashMap<>();

         for (StashFinder.StashChunk chunk : this.chunks) {
            dimCounts.merge(chunk.dimension, 1, Integer::sum);
         }

         for (Entry<String, Integer> entry : dimCounts.entrySet()) {
            dimTable.add(theme.label(entry.getKey() + ":")).padRight(10.0);
            dimTable.add(theme.label(entry.getValue() + " stashes"));
            dimTable.row();
         }

         list.add(dimTable);
      }

      if ((Boolean)this.hideCoordinates.get()) {
         list.add(theme.label("Coordinates hidden. Use coordinate list to view.").color(theme.textSecondaryColor()));
      }

      return list;
   }

   private String coordText(int x, int z) {
      return this.streamerMode.get() ? "*****, *****" : x + ", " + z;
   }

   private void openCoordinateList() {
      this.ensureLoaded();
      GuiTheme theme = GuiThemes.get();
      this.mc.setScreen(new StashFinder.CoordinateListScreen(theme));
   }

   private void createTestWaypoints() {
      if (this.mc.player == null) {
         this.error("Player not found", new Object[0]);
      } else if (!Utils.XAERO_AVAILABLE) {
         this.error("Xaero's Minimap not available", new Object[0]);
      } else {
         int x = (int)this.mc.player.getX();
         int y = (int)this.mc.player.getY();
         int z = (int)this.mc.player.getZ();
         String dimension = this.mc.world != null ? this.mc.world.getRegistryKey().getValue().toString() : "minecraft:overworld";
         String[][] testSymbols = new String[][]{
            {"\ud83d\udce6", "Chest"},
            {"\ud83e\udea4", "TrappedChest"},
            {"\ud83d\udee2", "Barrel"},
            {"\ud83c\udf81", "Shulker"},
            {"\u2601", "EnderChest"},
            {"\ud83d\udd25", "Furnace"},
            {"\u2b07", "Dispenser"},
            {"\u23ec", "Hopper"},
            {"\ud83e\uddea", "BrewingStand"},
            {"\ud83d\udd27", "Crafter"},
            {"\ud83c\udffa", "DecoratedPot"},
            {"\ud83d\udea9", "Banner"},
            {"\ud83e\udea7", "Sign"},
            {"\ud83e\ude9d", "HangingSign"},
            {"\ud83d\uddfa", "MapItemFrame"}
         };
         int offset = 0;

         for (String[] symbolData : testSymbols) {
            String waypointName = symbolData[0] + " " + symbolData[1];
            XaeroWaypointManager.addWaypoint(
               "StashFinder", new BlockPos(x + offset, y, z), waypointName, symbolData[0], (MapUtil.WpColor)this.waypointColor.get(), true, dimension
            );
            offset += 16;
         }

         this.info("Created %d test waypoints at your location. Check which emojis render correctly!", new Object[]{testSymbols.length});
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!this.tracerPositions.isEmpty() && this.mc.player != null) {
         double playerX = this.mc.player.getX();
         double playerZ = this.mc.player.getZ();
         this.tracerPositions.entrySet().removeIf(entry -> {
            Vec3d posx = entry.getValue();
            double distx = Math.hypot(posx.x - playerX, posx.z - playerZ);
            return distx <= ((Integer)this.traceArrivalDistance.get()).intValue();
         });
         if ((Boolean)this.renderTracer.get() || (Boolean)this.renderChunkColumn.get()) {
            for (Vec3d pos : this.tracerPositions.values()) {
               double dist = Math.hypot(pos.x - playerX, pos.z - playerZ);
               if (!(dist > ((Integer)this.traceMaxDistance.get()).intValue())) {
                  if ((Boolean)this.renderTracer.get()) {
                     event.renderer
                        .line(
                           RenderUtils.center.x,
                           RenderUtils.center.y,
                           RenderUtils.center.z,
                           pos.x,
                           pos.y,
                           pos.z,
                           (Color)this.traceColor.get()
                        );
                  }

                  if ((Boolean)this.renderChunkColumn.get()) {
                     int chunkX = (int)pos.x - 8 >> 4 << 4;
                     int chunkZ = (int)pos.z - 8 >> 4 << 4;
                     double x1 = chunkX;
                     double x2 = chunkX + 16;
                     double z1 = chunkZ;
                     double z2 = chunkZ + 16;
                     int bottomY = this.mc.world.getBottomY();
                     int topY = bottomY + this.mc.world.getDimension().height();
                     event.renderer.line(x1, bottomY, z1, x1, topY, z1, (Color)this.columnColor.get());
                     event.renderer.line(x1, bottomY, z2, x1, topY, z2, (Color)this.columnColor.get());
                     event.renderer.line(x2, bottomY, z1, x2, topY, z1, (Color)this.columnColor.get());
                     event.renderer.line(x2, bottomY, z2, x2, topY, z2, (Color)this.columnColor.get());
                     event.renderer.line(x1, bottomY, z1, x2, bottomY, z1, (Color)this.columnColor.get());
                     event.renderer.line(x1, bottomY, z1, x1, bottomY, z2, (Color)this.columnColor.get());
                     event.renderer.line(x2, bottomY, z2, x1, bottomY, z2, (Color)this.columnColor.get());
                     event.renderer.line(x2, bottomY, z2, x2, bottomY, z1, (Color)this.columnColor.get());
                     event.renderer.line(x1, topY, z1, x2, topY, z1, (Color)this.columnColor.get());
                     event.renderer.line(x1, topY, z1, x1, topY, z2, (Color)this.columnColor.get());
                     event.renderer.line(x2, topY, z2, x1, topY, z2, (Color)this.columnColor.get());
                     event.renderer.line(x2, topY, z2, x2, topY, z1, (Color)this.columnColor.get());
                  }
               }
            }
         }
      }
   }

   private String getCurrentDimension() {
      return this.mc.world == null ? "unknown" : this.mc.world.getRegistryKey().getValue().getPath();
   }

   private void ensureLoaded() {
      if (!this.loaded) {
         Map<ChunkPos, StashFinder.StashChunk> byPos = new LinkedHashMap<>();

         for (StashFinder.StashChunk c : this.readFromDisk()) {
            byPos.put(c.chunkPos, c);
         }

         for (StashFinder.StashChunk c : this.chunks) {
            byPos.put(c.chunkPos, c);
         }

         this.chunks = new ArrayList<>(byPos.values());
         this.loaded = true;
      }
   }

   private List<StashFinder.StashChunk> readFromDisk() {
      File file = this.getJsonFile();
      if (!file.exists()) {
         return new ArrayList<>();
      }

      try (FileReader reader = new FileReader(file)) {
         List<StashFinder.StashChunk> list = (List<StashFinder.StashChunk>)GSON.fromJson(reader, new TypeToken<List<StashFinder.StashChunk>>() {}.getType());
         if (list == null) {
            return new ArrayList<>();
         }

         for (StashFinder.StashChunk chunk : list) {
            chunk.calculatePos();
         }

         return list;
      } catch (Exception e) {
         return new ArrayList<>();
      }
   }

   private void writeToDisk(List<StashFinder.StashChunk> list) {
      try {
         File file = this.getJsonFile();
         file.getParentFile().mkdirs();

         try (Writer writer = new FileWriter(file)) {
            GSON.toJson(list, writer);
         }
      } catch (IOException e) {
         MeteorClient.LOG.error("Error saving stash list", e);
      }
   }

   private void save() {
      List<StashFinder.StashChunk> snapshot = new ArrayList<>(this.chunks);
      boolean full = this.loaded;
      this.ioExecutor.submit(() -> {
         List<StashFinder.StashChunk> toWrite;
         if (full) {
            toWrite = snapshot;
         } else {
            Map<ChunkPos, StashFinder.StashChunk> byPos = new LinkedHashMap<>();

            for (StashFinder.StashChunk c : this.readFromDisk()) {
               byPos.put(c.chunkPos, c);
            }

            for (StashFinder.StashChunk c : snapshot) {
               byPos.put(c.chunkPos, c);
            }

            toWrite = new ArrayList<>(byPos.values());
         }

         this.writeToDisk(toWrite);
      });
   }

   private File getJsonFile() {
      return new File(new File(new File(MeteorClient.FOLDER, "stashes"), meteordevelopment.meteorclient.utils.Utils.getFileWorldName()), "stashes-mlep.json");
   }

   public String getInfoString() {
      return String.valueOf(this.chunks.size());
   }

   private class ChunkDetailScreen extends WindowScreen {
      private final StashFinder.StashChunk chunk;
      private static final Color GOLD = new Color(255, 170, 0);
      private static final Color YELLOW = new Color(255, 255, 85);
      private static final Color RED = new Color(255, 85, 85);
      private static final Color MAGENTA = new Color(255, 85, 255);
      private static final Color DARK_PURPLE = new Color(170, 0, 170);
      private static final Color GRAY = new Color(170, 170, 170);
      private static final Color CYAN = new Color(85, 255, 255);
      private static final Color GREEN = new Color(85, 255, 85);
      private static final Color BLUE = new Color(85, 85, 255);

      public ChunkDetailScreen(GuiTheme theme, StashFinder.StashChunk chunk) {
         super(theme, "Stash Details");
         this.chunk = chunk;
      }

      public void initWidgets() {
         WTable t = (WTable)this.add(this.theme.table()).expandX().widget();
         t.add(this.theme.label("Coordinates:").color(GOLD));
         t.add(this.theme.label(StashFinder.this.coordText(this.chunk.x, this.chunk.z)));
         t.row();
         t.add(this.theme.label("Dimension:").color(GOLD));
         t.add(this.theme.label(this.chunk.dimension));
         t.row();
         t.add(this.theme.horizontalSeparator()).expandX();
         t.row();
         t.add(this.theme.label("Total:").color(GOLD));
         t.add(this.theme.label(String.valueOf(this.chunk.getTotal())));
         t.row();
         t.add(this.theme.horizontalSeparator()).expandX();
         t.row();
         this.addCountRow(t, "\ud83d\udce6 Chests", this.chunk.chests, YELLOW);
         this.addCountRow(t, "\ud83d\udca3 Trapped Chests", this.chunk.trappedChests, RED);
         this.addCountRow(t, "\ud83d\udee2\ufe0f Barrels", this.chunk.barrels, GOLD);
         this.addCountRow(t, "\ud83d\udfea Shulkers", this.chunk.shulkers, MAGENTA);
         this.addCountRow(t, "\ud83d\udc41\ufe0f Ender Chests", this.chunk.enderChests, DARK_PURPLE);
         this.addCountRow(t, "\ud83d\udd25 Furnaces", this.chunk.furnaces, GRAY);
         this.addCountRow(t, "\u2b07\ufe0f Dispensers/Droppers", this.chunk.dispensersDroppers, GRAY);
         this.addCountRow(t, "\u2935\ufe0f Hoppers", this.chunk.hoppers, GRAY);
         this.addCountRow(t, "\u2697\ufe0f Brewing Stands", this.chunk.brewingStands, CYAN);
         this.addCountRow(t, "\u2699\ufe0f Crafters", this.chunk.crafters, GREEN);
         this.addCountRow(t, "\ud83c\udffa Decorated Pots", this.chunk.decoratedPots, GOLD);
         this.addCountRow(t, "\ud83d\udea9 Banners", this.chunk.banners, RED);
         this.addCountRow(t, "\ud83e\udea7 Signs", this.chunk.signs, GOLD);
         this.addCountRow(t, "\ud83e\ude9d Hanging Signs", this.chunk.hangingSigns, GOLD);
         this.addCountRow(t, "\ud83d\uddfa\ufe0f Map Item Frames", this.chunk.mapItemFrames, BLUE);
         this.add(this.theme.horizontalSeparator()).expandX();
         WHorizontalList buttons = (WHorizontalList)this.add(this.theme.horizontalList()).widget();
         WButton copyBtn = (WButton)buttons.add(this.theme.button("Copy Coordinates")).widget();
         copyBtn.action = () -> {
            StashFinder.this.mc.keyboard.setClipboard(this.chunk.x + " " + this.chunk.z);
            if ((Boolean)StashFinder.this.streamerMode.get()) {
               StashFinder.this.info("Copied coordinates to clipboard.", new Object[0]);
            } else {
               StashFinder.this.info("Copied coordinates: (highlight)%d %d", new Object[]{this.chunk.x, this.chunk.z});
            }
         };
         if (BaritoneHelper.isAvailable()) {
            WButton flyBtn = (WButton)buttons.add(this.theme.button("Fly Here")).widget();
            flyBtn.action = () -> {
               BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("goal " + this.chunk.x + " " + this.chunk.z);
               BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("elytra");
               this.close();
            };
         }

         WButton back = (WButton)buttons.add(this.theme.button("Back")).widget();
         back.action = this::close;
      }

      private void addCountRow(WTable t, String name, int count, Color color) {
         if (count > 0) {
            t.add(this.theme.label(name + ":"));
            t.add(this.theme.label(String.valueOf(count)).color(color));
            t.row();
         }
      }
   }

   private class CoordinateListScreen extends WindowScreen {
      public CoordinateListScreen(GuiTheme theme) {
         super(theme, "Stash Coordinates");
      }

      public void initWidgets() {
         StashFinder.this.chunks.sort(Comparator.comparingInt(c -> -c.getTotal()));
         this.add(this.theme.label("\u26a0 Coordinates - Do not share! \u26a0").color(new Color(255, 170, 0)));
         this.add(this.theme.horizontalSeparator()).expandX();
         if (StashFinder.this.chunks.isEmpty()) {
            this.add(this.theme.label("No stashes found yet."));
         } else {
            WHorizontalList filterRow = (WHorizontalList)this.add(this.theme.horizontalList()).widget();
            filterRow.add(this.theme.label("Filter:"));
            WButton allBtn = (WButton)filterRow.add(this.theme.button("All")).widget();
            WButton owBtn = (WButton)filterRow.add(this.theme.button("Overworld")).widget();
            WButton netherBtn = (WButton)filterRow.add(this.theme.button("Nether")).widget();
            WButton endBtn = (WButton)filterRow.add(this.theme.button("End")).widget();
            WTable table = (WTable)this.add(this.theme.table()).expandX().widget();
            Runnable refreshAll = () -> this.fillTable(table, null);
            Runnable refreshOW = () -> this.fillTable(table, "overworld");
            Runnable refreshNether = () -> this.fillTable(table, "the_nether");
            Runnable refreshEnd = () -> this.fillTable(table, "the_end");
            allBtn.action = refreshAll;
            owBtn.action = refreshOW;
            netherBtn.action = refreshNether;
            endBtn.action = refreshEnd;
            this.fillTable(table, null);
         }
      }

      private void fillTable(WTable table, String dimensionFilter) {
         table.clear();
         table.add(this.theme.label("Coordinates", true)).padRight(10.0);
         table.add(this.theme.label("Dim", true)).padRight(5.0);
         table.add(this.theme.label("Total", true)).padRight(10.0);
         table.add(this.theme.label("Actions", true));
         table.row();

         for (StashFinder.StashChunk chunk : StashFinder.this.chunks) {
            if (dimensionFilter == null || chunk.dimension.equals(dimensionFilter)) {
               table.add(this.theme.label(StashFinder.this.coordText(chunk.x, chunk.z))).padRight(10.0);

               String dimText = switch (chunk.dimension) {
                  case "overworld" -> "OW";
                  case "the_nether" -> "Neth";
                  case "the_end" -> "End";
                  default -> "?";
               };

               Color dimColor = switch (chunk.dimension) {
                  case "overworld" -> new Color(85, 255, 85);
                  case "the_nether" -> new Color(255, 85, 85);
                  case "the_end" -> new Color(255, 85, 255);
                  default -> new Color(170, 170, 170);
               };
               table.add(this.theme.label(dimText).color(dimColor)).padRight(5.0);
               table.add(this.theme.label(String.valueOf(chunk.getTotal()))).padRight(10.0);
               WHorizontalList actions = this.theme.horizontalList();
               WButton details = (WButton)actions.add(this.theme.button("Info")).widget();
               details.action = () -> StashFinder.this.mc.setScreen(StashFinder.this.new ChunkDetailScreen(this.theme, chunk));
               WButton copy = (WButton)actions.add(this.theme.button("Copy")).widget();
               copy.action = () -> {
                  StashFinder.this.mc.keyboard.setClipboard(chunk.x + " " + chunk.z);
                  if ((Boolean)StashFinder.this.streamerMode.get()) {
                     StashFinder.this.info("Copied coordinates to clipboard.", new Object[0]);
                  } else {
                     StashFinder.this.info("Copied coordinates: (highlight)%d %d", new Object[]{chunk.x, chunk.z});
                  }
               };
               actions.add(this.theme.label("Trace:"));
               WCheckbox tracer = (WCheckbox)actions.add(this.theme.checkbox(StashFinder.this.tracerPositions.containsKey(chunk.chunkPos))).widget();
               tracer.action = () -> {
                  if (tracer.checked) {
                     double y = StashFinder.this.mc.player != null ? StashFinder.this.mc.player.getEyeY() : 64.0;
                     StashFinder.this.tracerPositions.put(chunk.chunkPos, new Vec3d(chunk.x, y, chunk.z));
                  } else {
                     StashFinder.this.tracerPositions.remove(chunk.chunkPos);
                  }
               };
               WButton gotoBtn = (WButton)actions.add(this.theme.button("Go")).widget();
               gotoBtn.action = () -> PathManagers.get().moveTo(new BlockPos(chunk.x, 64, chunk.z), true);
               WMinus delete = (WMinus)actions.add(this.theme.minus()).widget();
               delete.action = () -> {
                  StashFinder.this.chunks.remove(chunk);
                  StashFinder.this.tracerPositions.remove(chunk.chunkPos);
                  StashFinder.this.save();
                  this.fillTable(table, dimensionFilter);
               };
               table.add(actions);
               table.row();
            }
         }
      }
   }

   public enum NotificationMode {
      Chat,
      Toast,
      Sound,
      Both,
      All,
      Silent;
   }

   public static class StashChunk {
      public ChunkPos chunkPos;
      public transient int x;
      public transient int z;
      public String dimension = "overworld";
      public int chests;
      public int trappedChests;
      public int barrels;
      public int shulkers;
      public int enderChests;
      public int furnaces;
      public int dispensersDroppers;
      public int hoppers;
      public int brewingStands;
      public int crafters;
      public int decoratedPots;
      public int banners;
      public int signs;
      public int hangingSigns;
      public int mapItemFrames;
      public int itemFrames;
      public int enderPearls;
      public int namedEntities;

      public StashChunk(ChunkPos chunkPos) {
         this.chunkPos = chunkPos;
         this.calculatePos();
      }

      public void calculatePos() {
         this.x = this.chunkPos.x * 16 + 8;
         this.z = this.chunkPos.z * 16 + 8;
      }

      public int getTotal() {
         return this.chests
            + this.trappedChests
            + this.barrels
            + this.shulkers
            + this.enderChests
            + this.furnaces
            + this.dispensersDroppers
            + this.hoppers
            + this.brewingStands
            + this.crafters
            + this.decoratedPots
            + this.banners
            + this.signs
            + this.hangingSigns
            + this.mapItemFrames
            + this.itemFrames
            + this.enderPearls
            + this.namedEntities;
      }

      public int getHighValueTotal() {
         return this.chests + this.trappedChests + this.shulkers + this.barrels;
      }

      public boolean countsEqual(StashFinder.StashChunk c) {
         return c == null
            ? false
            : this.chests == c.chests
               && this.trappedChests == c.trappedChests
               && this.barrels == c.barrels
               && this.shulkers == c.shulkers
               && this.enderChests == c.enderChests
               && this.furnaces == c.furnaces
               && this.dispensersDroppers == c.dispensersDroppers
               && this.hoppers == c.hoppers
               && this.brewingStands == c.brewingStands
               && this.crafters == c.crafters
               && this.decoratedPots == c.decoratedPots
               && this.banners == c.banners
               && this.signs == c.signs
               && this.hangingSigns == c.hangingSigns
               && this.mapItemFrames == c.mapItemFrames
               && this.itemFrames == c.itemFrames
               && this.enderPearls == c.enderPearls
               && this.namedEntities == c.namedEntities;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            StashFinder.StashChunk that = (StashFinder.StashChunk)o;
            return Objects.equals(this.chunkPos, that.chunkPos);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.chunkPos);
      }
   }
}
