package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified ESP for decorative items and special blocks. Highlights:
 * <ul>
 *   <li>Map item frames (item frames containing filled maps)
 *   <li>Banners (placed on the ground / walls)
 *   <li>Shulker frames (item frames containing shulker boxes)
 *   <li>Ominous vaults (trial chamber vaults with the {@code ominous} property)
 *   <li>Buried treasure (chests with dirt/sand/grass above — typical generated structure)
 *   <li>Endermen holding blocks (a player just broke a block near them)
 *   <li>XP orbs (proximity detection for xp farms)
 *   <li>Stacked entities (chest minecarts / hopper minecarts / chest boats stacked in one block)
 * </ul>
 *
 * <p>Ported from mlep's {@code VanityESP}. Removed the Xaero waypoint
 * integration (no XaeroPlus dependency in HunterBuddy); waypoint settings
 * are kept but hidden via {@code visible() -> false} so the module
 * compiles cleanly without those imports.
 */
public class VanityESP extends Module {
    private final SettingGroup sgFeatures = settings.getDefaultGroup();
    private final SettingGroup sgMapFrames = settings.createGroup("Map Frames");
    private final SettingGroup sgBanners = settings.createGroup("Banners");
    private final SettingGroup sgShulkerFrames = settings.createGroup("Shulker Frames");
    private final SettingGroup sgOminousVaults = settings.createGroup("Ominous Vaults");
    private final SettingGroup sgTreasure = settings.createGroup("Buried Treasure");
    private final SettingGroup sgEnderman = settings.createGroup("Enderman");
    private final SettingGroup sgXPOrbs = settings.createGroup("XP Orbs");
    private final SettingGroup sgStackedEntities = settings.createGroup("Stacked Entities");

    // ---- Features (toggles) ----

    private final Setting<Boolean> highlightMapFrames = sgFeatures.add(new BoolSetting.Builder()
        .name("map-frames").description("Highlights item frames containing maps.").defaultValue(true).build()
    );

    private final Setting<Boolean> highlightBanners = sgFeatures.add(new BoolSetting.Builder()
        .name("banners").description("Highlights banners.").defaultValue(true).build()
    );

    private final Setting<Boolean> highlightShulkerFrames = sgFeatures.add(new BoolSetting.Builder()
        .name("shulker-frames").description("Highlights item frames containing shulker boxes.").defaultValue(true).build()
    );

    private final Setting<Boolean> highlightOminousVaults = sgFeatures.add(new BoolSetting.Builder()
        .name("ominous-vaults").description("Highlights ominous vaults.").defaultValue(true).build()
    );

    private final Setting<Boolean> highlightTreasure = sgFeatures.add(new BoolSetting.Builder()
        .name("buried-treasure").description("Highlights buried treasure chests.").defaultValue(true).build()
    );

    private final Setting<Boolean> highlightEndermanHolding = sgFeatures.add(new BoolSetting.Builder()
        .name("enderman-holding-blocks").description("Highlights endermen that are holding blocks.").defaultValue(true).build()
    );

    private final Setting<Boolean> highlightXPOrbs = sgFeatures.add(new BoolSetting.Builder()
        .name("xp-orbs").description("Highlights experience orbs.").defaultValue(true).build()
    );

    private final Setting<Boolean> highlightStackedEntities = sgFeatures.add(new BoolSetting.Builder()
        .name("stacked-entities")
        .description("Highlights and notifies about stacked minecarts with chests/hoppers and chest boats.")
        .defaultValue(true).build()
    );

    // ---- Map Frames ----

    private final Setting<SettingColor> mapFillColor = sgMapFrames.add(new ColorSetting.Builder()
        .name("side-color").description("Fill color for map frames.")
        .defaultValue(new SettingColor(255, 255, 0, 50)).visible(highlightMapFrames::get).build()
    );

    private final Setting<SettingColor> mapOutlineColor = sgMapFrames.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color for map frames.")
        .defaultValue(new SettingColor(255, 255, 0, 255)).visible(highlightMapFrames::get).build()
    );

    private final Setting<Boolean> mapRenderFill = sgMapFrames.add(new BoolSetting.Builder()
        .name("render-sides").description("Render sides of map frames.").defaultValue(false)
        .visible(highlightMapFrames::get).build()
    );

    private final Setting<Boolean> mapRenderOutline = sgMapFrames.add(new BoolSetting.Builder()
        .name("render-lines").description("Render lines of map frames.").defaultValue(true)
        .visible(highlightMapFrames::get).build()
    );

    private final Setting<Boolean> mapRenderTracer = sgMapFrames.add(new BoolSetting.Builder()
        .name("tracers").description("Add tracers to map frames.").defaultValue(false)
        .visible(highlightMapFrames::get).build()
    );

    private final Setting<SettingColor> mapTracerColor = sgMapFrames.add(new ColorSetting.Builder()
        .name("tracer-color").description("Tracer color for map frames.")
        .defaultValue(new SettingColor(255, 255, 0, 125)).visible(highlightMapFrames::get).build()
    );

    // ---- Banners ----

    private final Setting<SettingColor> bannerFillColor = sgBanners.add(new ColorSetting.Builder()
        .name("side-color").description("Fill color for banners.")
        .defaultValue(new SettingColor(255, 0, 0, 50)).visible(highlightBanners::get).build()
    );

    private final Setting<SettingColor> bannerOutlineColor = sgBanners.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color for banners.")
        .defaultValue(new SettingColor(255, 0, 0, 255)).visible(highlightBanners::get).build()
    );

    private final Setting<Boolean> bannerRenderFill = sgBanners.add(new BoolSetting.Builder()
        .name("render-sides").description("Render sides of banners.").defaultValue(true)
        .visible(highlightBanners::get).build()
    );

    private final Setting<Boolean> bannerRenderOutline = sgBanners.add(new BoolSetting.Builder()
        .name("render-lines").description("Render lines of banners.").defaultValue(true)
        .visible(highlightBanners::get).build()
    );

    private final Setting<Boolean> bannerRenderTracer = sgBanners.add(new BoolSetting.Builder()
        .name("tracers").description("Add tracers to banners.").defaultValue(false)
        .visible(highlightBanners::get).build()
    );

    private final Setting<SettingColor> bannerTracerColor = sgBanners.add(new ColorSetting.Builder()
        .name("tracer-color").description("Tracer color for banners.")
        .defaultValue(new SettingColor(255, 0, 0, 125)).visible(highlightBanners::get).build()
    );

    // ---- Shulker Frames ----

    private final Setting<SettingColor> shulkerFillColor = sgShulkerFrames.add(new ColorSetting.Builder()
        .name("side-color").description("Fill color for shulker frames.")
        .defaultValue(new SettingColor(152, 98, 43, 50)).visible(highlightShulkerFrames::get).build()
    );

    private final Setting<SettingColor> shulkerOutlineColor = sgShulkerFrames.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color for shulker frames.")
        .defaultValue(new SettingColor(85, 43, 19, 255)).visible(highlightShulkerFrames::get).build()
    );

    private final Setting<SettingColor> shulkerTracerColor = sgShulkerFrames.add(new ColorSetting.Builder()
        .name("tracer-color").description("Tracer color for shulker frames.")
        .defaultValue(new SettingColor(166, 150, 101, 255)).visible(highlightShulkerFrames::get).build()
    );

    private final Setting<Boolean> shulkerRenderFill = sgShulkerFrames.add(new BoolSetting.Builder()
        .name("render-sides").description("Render sides of shulker frames.").defaultValue(true)
        .visible(highlightShulkerFrames::get).build()
    );

    private final Setting<Boolean> shulkerRenderOutline = sgShulkerFrames.add(new BoolSetting.Builder()
        .name("render-lines").description("Render lines of shulker frames.").defaultValue(true)
        .visible(highlightShulkerFrames::get).build()
    );

    private final Setting<Boolean> shulkerRenderTracer = sgShulkerFrames.add(new BoolSetting.Builder()
        .name("tracers").description("Add tracers to shulker frames.").defaultValue(false)
        .visible(highlightShulkerFrames::get).build()
    );

    // ---- Ominous Vaults ----

    private final Setting<SettingColor> vaultFillColor = sgOminousVaults.add(new ColorSetting.Builder()
        .name("side-color").description("Fill color for ominous vaults.")
        .defaultValue(new SettingColor(0, 120, 120, 50)).visible(highlightOminousVaults::get).build()
    );

    private final Setting<SettingColor> vaultOutlineColor = sgOminousVaults.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color for ominous vaults.")
        .defaultValue(new SettingColor(31, 161, 159, 255)).visible(highlightOminousVaults::get).build()
    );

    private final Setting<SettingColor> vaultTracerColor = sgOminousVaults.add(new ColorSetting.Builder()
        .name("tracer-color").description("Tracer color for ominous vaults.")
        .defaultValue(new SettingColor(40, 200, 195, 255)).visible(highlightOminousVaults::get).build()
    );

    private final Setting<Boolean> vaultRenderFill = sgOminousVaults.add(new BoolSetting.Builder()
        .name("render-sides").description("Render sides of ominous vaults.").defaultValue(true)
        .visible(highlightOminousVaults::get).build()
    );

    private final Setting<Boolean> vaultRenderOutline = sgOminousVaults.add(new BoolSetting.Builder()
        .name("render-lines").description("Render lines of ominous vaults.").defaultValue(true)
        .visible(highlightOminousVaults::get).build()
    );

    private final Setting<Boolean> vaultRenderTracer = sgOminousVaults.add(new BoolSetting.Builder()
        .name("tracers").description("Add tracers to ominous vaults.").defaultValue(false)
        .visible(highlightOminousVaults::get).build()
    );

    // ---- Buried Treasure ----

    private final Setting<Boolean> treasureChat = sgTreasure.add(new BoolSetting.Builder()
        .name("chat-notification").description("Notify with a chat message.").defaultValue(true)
        .visible(highlightTreasure::get).build()
    );

    private final Setting<Boolean> treasureCoords = sgTreasure.add(new BoolSetting.Builder()
        .name("show-coords").description("Display chest coordinates in chat notifications.").defaultValue(false)
        .visible(() -> highlightTreasure.get() && treasureChat.get()).build()
    );

    private final Setting<Boolean> treasureSound = sgTreasure.add(new BoolSetting.Builder()
        .name("sound-notification").description("Notify with sound.").defaultValue(true)
        .visible(highlightTreasure::get).build()
    );

    private final Setting<Double> treasureVolume = sgTreasure.add(new DoubleSetting.Builder()
        .name("volume").min(0.0).max(10.0).sliderMin(0.0).sliderMax(5.0).defaultValue(1.0)
        .visible(() -> highlightTreasure.get() && treasureSound.get()).build()
    );

    // ---- Buried Treasure : Xaero waypoint integration ----
    private final Setting<Boolean> treasureWaypoints = sgTreasure.add(new BoolSetting.Builder()
        .name("add-waypoints").description("Adds waypoints to your Xaeros map for treasure chests.")
        .defaultValue(false)
        .visible(() -> highlightTreasure.get() && com.hunterbuddy.modules.vanityesp.XaeroWaypointHelper.isAvailable()).build()
    );

    private final Setting<Boolean> treasureTempWaypoints = sgTreasure.add(new BoolSetting.Builder()
        .name("temporary-waypoints").description("Temporary waypoints are removed when you disconnect.")
        .defaultValue(true)
        .visible(() -> highlightTreasure.get() && com.hunterbuddy.modules.vanityesp.XaeroWaypointHelper.isAvailable()
            && treasureWaypoints.get()).build()
    );

    private final Setting<SettingColor> treasureFillColor = sgTreasure.add(new ColorSetting.Builder()
        .name("side-color").description("Fill color for treasure chests.")
        .defaultValue(new SettingColor(147, 233, 190, 25)).visible(highlightTreasure::get).build()
    );

    private final Setting<SettingColor> treasureOutlineColor = sgTreasure.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color for treasure chests.")
        .defaultValue(new SettingColor(147, 233, 190, 255)).visible(highlightTreasure::get).build()
    );

    private final Setting<SettingColor> treasureTracerColor = sgTreasure.add(new ColorSetting.Builder()
        .name("tracer-color").description("Tracer color for treasure chests.")
        .defaultValue(new SettingColor(147, 233, 190, 125)).visible(highlightTreasure::get).build()
    );

    private final Setting<Boolean> treasureRenderFill = sgTreasure.add(new BoolSetting.Builder()
        .name("render-sides").description("Render sides of treasure chests.").defaultValue(true)
        .visible(highlightTreasure::get).build()
    );

    private final Setting<Boolean> treasureRenderOutline = sgTreasure.add(new BoolSetting.Builder()
        .name("render-lines").description("Render lines of treasure chests.").defaultValue(true)
        .visible(highlightTreasure::get).build()
    );

    private final Setting<Boolean> treasureRenderTracer = sgTreasure.add(new BoolSetting.Builder()
        .name("tracers").description("Add tracers to treasure chests.").defaultValue(true)
        .visible(highlightTreasure::get).build()
    );

    // ---- Enderman ----

    private final Setting<SettingColor> endermanFillColor = sgEnderman.add(new ColorSetting.Builder()
        .name("side-color").description("Fill color for endermen holding blocks.")
        .defaultValue(new SettingColor(128, 0, 128, 50)).visible(highlightEndermanHolding::get).build()
    );

    private final Setting<SettingColor> endermanOutlineColor = sgEnderman.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color for endermen holding blocks.")
        .defaultValue(new SettingColor(200, 0, 200, 255)).visible(highlightEndermanHolding::get).build()
    );

    private final Setting<Boolean> endermanRenderFill = sgEnderman.add(new BoolSetting.Builder()
        .name("render-sides").description("Render sides of endermen holding blocks.").defaultValue(true)
        .visible(highlightEndermanHolding::get).build()
    );

    private final Setting<Boolean> endermanRenderOutline = sgEnderman.add(new BoolSetting.Builder()
        .name("render-lines").description("Render lines of endermen holding blocks.").defaultValue(true)
        .visible(highlightEndermanHolding::get).build()
    );

    private final Setting<Boolean> endermanRenderTracer = sgEnderman.add(new BoolSetting.Builder()
        .name("tracers").description("Add tracers to endermen holding blocks.").defaultValue(false)
        .visible(highlightEndermanHolding::get).build()
    );

    private final Setting<SettingColor> endermanTracerColor = sgEnderman.add(new ColorSetting.Builder()
        .name("tracer-color").description("Tracer color for endermen holding blocks.")
        .defaultValue(new SettingColor(200, 0, 200, 125)).visible(highlightEndermanHolding::get).build()
    );

    // ---- XP Orbs ----

    private final Setting<SettingColor> xpOrbFillColor = sgXPOrbs.add(new ColorSetting.Builder()
        .name("side-color").description("Fill color for XP orbs.")
        .defaultValue(new SettingColor(0, 255, 0, 75)).visible(highlightXPOrbs::get).build()
    );

    private final Setting<SettingColor> xpOrbOutlineColor = sgXPOrbs.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color for XP orbs.")
        .defaultValue(new SettingColor(0, 255, 0, 255)).visible(highlightXPOrbs::get).build()
    );

    private final Setting<Boolean> xpOrbRenderFill = sgXPOrbs.add(new BoolSetting.Builder()
        .name("render-sides").description("Render sides of XP orbs.").defaultValue(true)
        .visible(highlightXPOrbs::get).build()
    );

    private final Setting<Boolean> xpOrbRenderOutline = sgXPOrbs.add(new BoolSetting.Builder()
        .name("render-lines").description("Render lines of XP orbs.").defaultValue(true)
        .visible(highlightXPOrbs::get).build()
    );

    private final Setting<Boolean> xpOrbRenderTracer = sgXPOrbs.add(new BoolSetting.Builder()
        .name("tracers").description("Add tracers to XP orbs.").defaultValue(false)
        .visible(highlightXPOrbs::get).build()
    );

    private final Setting<SettingColor> xpOrbTracerColor = sgXPOrbs.add(new ColorSetting.Builder()
        .name("tracer-color").description("Tracer color for XP orbs.")
        .defaultValue(new SettingColor(0, 255, 0, 125)).visible(highlightXPOrbs::get).build()
    );

    // ---- Stacked Entities ----

    private final Setting<Boolean> stackedEntitiesChat = sgStackedEntities.add(new BoolSetting.Builder()
        .name("chat-notification").description("Notify with a chat message when stacked entities are detected.").defaultValue(true)
        .visible(highlightStackedEntities::get).build()
    );

    private final Setting<Boolean> stackedEntitiesCoords = sgStackedEntities.add(new BoolSetting.Builder()
        .name("show-coords").description("Display coordinates in chat notifications.").defaultValue(true)
        .visible(() -> highlightStackedEntities.get() && stackedEntitiesChat.get()).build()
    );

    private final Setting<Boolean> stackedEntitiesSound = sgStackedEntities.add(new BoolSetting.Builder()
        .name("sound-notification").description("Notify with sound when stacked entities are detected.").defaultValue(true)
        .visible(highlightStackedEntities::get).build()
    );

    private final Setting<Double> stackedEntitiesVolume = sgStackedEntities.add(new DoubleSetting.Builder()
        .name("volume").min(0.0).max(10.0).sliderMin(0.0).sliderMax(5.0).defaultValue(1.0)
        .visible(() -> highlightStackedEntities.get() && stackedEntitiesSound.get()).build()
    );

    private final Setting<Integer> stackedEntitiesMinCount = sgStackedEntities.add(new IntSetting.Builder()
        .name("min-stacked-count").description("Minimum number of entities stacked together to trigger detection.")
        .min(2).defaultValue(2).sliderMin(2).sliderMax(10).visible(highlightStackedEntities::get).build()
    );

    // ---- Stacked Entities : Xaero waypoint integration ----
    private final Setting<Boolean> stackedEntitiesWaypoints = sgStackedEntities.add(new BoolSetting.Builder()
        .name("add-waypoints").description("Adds waypoints to your Xaeros map for stacked entities.")
        .defaultValue(true)
        .visible(() -> highlightStackedEntities.get() && com.hunterbuddy.modules.vanityesp.XaeroWaypointHelper.isAvailable()).build()
    );

    private final Setting<Boolean> stackedEntitiesTempWaypoints = sgStackedEntities.add(new BoolSetting.Builder()
        .name("temporary-waypoints").description("Temporary waypoints are removed when you disconnect.")
        .defaultValue(false)
        .visible(() -> highlightStackedEntities.get() && com.hunterbuddy.modules.vanityesp.XaeroWaypointHelper.isAvailable()
            && stackedEntitiesWaypoints.get()).build()
    );

    private final Setting<SettingColor> stackedEntitiesFillColor = sgStackedEntities.add(new ColorSetting.Builder()
        .name("side-color").description("Fill color for stacked entities.")
        .defaultValue(new SettingColor(255, 165, 0, 75)).visible(highlightStackedEntities::get).build()
    );

    private final Setting<SettingColor> stackedEntitiesOutlineColor = sgStackedEntities.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color for stacked entities.")
        .defaultValue(new SettingColor(255, 140, 0, 255)).visible(highlightStackedEntities::get).build()
    );

    private final Setting<Boolean> stackedEntitiesRenderFill = sgStackedEntities.add(new BoolSetting.Builder()
        .name("render-sides").description("Render sides of stacked entities.").defaultValue(true)
        .visible(highlightStackedEntities::get).build()
    );

    private final Setting<Boolean> stackedEntitiesRenderOutline = sgStackedEntities.add(new BoolSetting.Builder()
        .name("render-lines").description("Render lines of stacked entities.").defaultValue(true)
        .visible(highlightStackedEntities::get).build()
    );

    private final Setting<Boolean> stackedEntitiesRenderTracer = sgStackedEntities.add(new BoolSetting.Builder()
        .name("tracers").description("Add tracers to stacked entities.").defaultValue(true)
        .visible(highlightStackedEntities::get).build()
    );

    private final Setting<SettingColor> stackedEntitiesTracerColor = sgStackedEntities.add(new ColorSetting.Builder()
        .name("tracer-color").description("Tracer color for stacked entities.")
        .defaultValue(new SettingColor(255, 165, 0, 200)).visible(highlightStackedEntities::get).build()
    );

    // ---- Runtime state ----

    // Synchronized because ominousVaults / chunkVaults are mutated on the
    // network thread (onChunkData) and iterated on the render thread
    // (Render3DEvent) — LinkedHashMap / HashSet are not thread-safe.
    private final Set<BlockPos> ominousVaults = Collections.synchronizedSet(new HashSet<>());
    private final Map<ChunkPos, Set<BlockPos>> chunkVaults = new HashMap<>();
    private Set<ChunkPos> lastLoadedChunks = new HashSet<>();
    private long lastRecheckTime = 0L;
    private final Map<ChunkPos, Integer> pendingChunks = new HashMap<>();
    private final Set<BlockPos> lootedTreasure = new HashSet<>();
    private final List<BlockPos> notifiedTreasure = new ArrayList<>();
    private final Set<BlockPos> notifiedStackedEntities = new HashSet<>();

    public VanityESP() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "vanity-esp",
            "Unified ESP for decorative items and special blocks.");
    }

    @Override
    public void onActivate() {
        // Initial scan of already-loaded chunks. onChunkData only fires for
        // chunks that load after activation, so without this we'd miss
        // pre-existing chunks.
        if (highlightTreasure.get() && mc.player != null && mc.world != null) {
            BlockPos pos = mc.player.getBlockPos();
            int viewDistance = mc.options.getViewDistance().getValue();
            int startChunkX = (pos.getX() - viewDistance * 16) >> 4;
            int endChunkX = (pos.getX() + viewDistance * 16) >> 4;
            int startChunkZ = (pos.getZ() - viewDistance * 16) >> 4;
            int endChunkZ = (pos.getZ() + viewDistance * 16) >> 4;

            for (int x = startChunkX; x < endChunkX; x++) {
                for (int z = startChunkZ; z < endChunkZ; z++) {
                    if (mc.world.isChunkLoaded(x, z)) {
                        scanChunkForTreasure(mc.world.getChunk(x, z));
                    }
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        notifiedTreasure.clear();
        lootedTreasure.clear();
        ominousVaults.clear();
        chunkVaults.clear();
        pendingChunks.clear();
        lastLoadedChunks.clear();
        notifiedStackedEntities.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;
        if (highlightOminousVaults.get()) {
            int chunkRadius = mc.options.getViewDistance().getValue();
            BlockPos playerPos = mc.player.getBlockPos();
            int minY = mc.world.getBottomY();
            int maxY = mc.world.getTopY();

            // Detect new / unloaded chunks
            Set<ChunkPos> currentChunks = new HashSet<>();
            for (int cx = (playerPos.getX() >> 4) - chunkRadius; cx <= (playerPos.getX() >> 4) + chunkRadius; cx++) {
                for (int cz = (playerPos.getZ() >> 4) - chunkRadius; cz <= (playerPos.getZ() >> 4) + chunkRadius; cz++) {
                    currentChunks.add(new ChunkPos(cx, cz));
                }
            }

            Set<ChunkPos> unloadedChunks = new HashSet<>(lastLoadedChunks);
            unloadedChunks.removeAll(currentChunks);
            for (ChunkPos chunkPos : unloadedChunks) {
                Set<BlockPos> removed = chunkVaults.remove(chunkPos);
                if (removed != null) ominousVaults.removeAll(removed);
                pendingChunks.remove(chunkPos);
            }

            Set<ChunkPos> newChunks = new HashSet<>(currentChunks);
            newChunks.removeAll(lastLoadedChunks);
            for (ChunkPos chunkPos : newChunks) {
                pendingChunks.put(chunkPos, 10);
            }

            Set<ChunkPos> toScan = new HashSet<>();
            for (Map.Entry<ChunkPos, Integer> entry : new HashMap<>(pendingChunks).entrySet()) {
                int ticksLeft = entry.getValue() - 1;
                if (ticksLeft <= 0) {
                    toScan.add(entry.getKey());
                } else {
                    pendingChunks.put(entry.getKey(), ticksLeft);
                }
            }

            for (ChunkPos chunkPos : toScan) {
                scanChunkForVaults(chunkPos, minY, maxY);
                pendingChunks.remove(chunkPos);
            }

            // Periodic re-check: a vault may have been opened (ominous property toggled off)
            long now = System.currentTimeMillis();
            if (now - lastRecheckTime >= 4000L) {
                lastRecheckTime = now;
                Set<BlockPos> toRemove = new HashSet<>();
                for (BlockPos vaultPos : ominousVaults) {
                    BlockState state = mc.world.getBlockState(vaultPos);
                    boolean stillOminous = false;
                    for (var prop : state.getProperties()) {
                        if (prop.getName().equals("ominous")) {
                            if (Boolean.TRUE.equals(state.get(prop))) {
                                stillOminous = true;
                            } else {
                                toRemove.add(vaultPos);
                            }
                            break;
                        }
                    }
                    if (!stillOminous && !toRemove.contains(vaultPos)) {
                        // No ominous property at all — block isn't an ominous vault anymore.
                        toRemove.add(vaultPos);
                    }
                }
                ominousVaults.removeAll(toRemove);
            }

            lastLoadedChunks = currentChunks;
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (highlightTreasure.get() && mc.world != null) {
            scanChunkForTreasure(event.chunk());
        }
        if (highlightOminousVaults.get() && mc.world != null) {
            BlockPos playerPos = mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN;
            int chunkRadius = mc.options.getViewDistance().getValue();
            int minY = mc.world.getBottomY();
            int maxY = mc.world.getTopY();
            // Quick scan for new chunks in range
            ChunkPos cp = new ChunkPos(event.chunk().getPos().x, event.chunk().getPos().z);
            if (Math.abs(cp.x - (playerPos.getX() >> 4)) <= chunkRadius
                && Math.abs(cp.z - (playerPos.getZ() >> 4)) <= chunkRadius) {
                scanChunkForVaults(cp, minY, maxY);
            }
        }
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        // When the player opens a chest we already flagged as buried
        // treasure, mark it looted so we stop rendering it and remove its
        // Xaero waypoint. Reference mlep VanityESP.java:510-520.
        if (highlightTreasure.get() && mc.player != null && mc.world != null) {
            BlockPos pos = event.result.getBlockPos();
            if (notifiedTreasure.contains(pos)
                && event.result.getType() == HitResult.Type.BLOCK
                && mc.world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                lootedTreasure.add(pos);
                if (treasureWaypoints.get()) {
                    com.hunterbuddy.modules.vanityesp.XaeroWaypointHelper.removeWaypoint("VanityESP", pos);
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        if (highlightMapFrames.get()) renderMapFrames(event);
        if (highlightBanners.get()) renderBanners(event);
        if (highlightShulkerFrames.get()) renderShulkerFrames(event);
        if (highlightOminousVaults.get()) renderOminousVaults(event);
        if (highlightTreasure.get()) renderTreasure(event);
        if (highlightEndermanHolding.get()) renderEndermanHoldingBlocks(event);
        if (highlightXPOrbs.get()) renderXPOrbs(event);
        if (highlightStackedEntities.get()) detectAndRenderStackedEntities(event);
    }

    // ---- Render helpers ----

    private void renderMapFrames(Render3DEvent event) {
        ShapeMode mode = getShapeMode(mapRenderFill.get(), mapRenderOutline.get());
        if (mode == null) return;
        Color fill = new Color(mapFillColor.get());
        Color line = new Color(mapOutlineColor.get());
        Color tracer = new Color(mapTracerColor.get());

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity frame)) continue;
            ItemStack stack = frame.getHeldItemStack();
            if (!stack.isOf(Items.FILLED_MAP)) continue;
            Box box = frame.getBoundingBox().expand(0.12, 0.12, 0.01);
            event.renderer.box(box, fill, line, mode, 0);
            if (mapRenderTracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    box.getCenter().x, box.getCenter().y, box.getCenter().z,
                    tracer
                );
            }
        }
    }

    private void renderBanners(Render3DEvent event) {
        ShapeMode mode = getShapeMode(bannerRenderFill.get(), bannerRenderOutline.get());
        if (mode == null) return;
        Color fill = new Color(bannerFillColor.get());
        Color line = new Color(bannerOutlineColor.get());
        Color tracer = new Color(bannerTracerColor.get());

        int radius = 8;
        BlockPos playerPos = mc.player.getBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (mc.world.isChunkLoaded(playerPos.getX() / 16 + dx, playerPos.getZ() / 16 + dz)) {
                    WorldChunk chunk = mc.world.getChunk(playerPos.getX() / 16 + dx, playerPos.getZ() / 16 + dz);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (!(be instanceof BannerBlockEntity)) continue;
                        BlockPos pos = be.getPos();
                        event.renderer.box(pos, fill, line, mode, 0);
                        if (bannerRenderTracer.get()) {
                            event.renderer.line(
                                RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                tracer
                            );
                        }
                    }
                }
            }
        }
    }

    private void renderShulkerFrames(Render3DEvent event) {
        ShapeMode mode = getShapeMode(shulkerRenderFill.get(), shulkerRenderOutline.get());
        if (mode == null) return;
        Color fill = new Color(shulkerFillColor.get());
        Color line = new Color(shulkerOutlineColor.get());
        Color tracer = new Color(shulkerTracerColor.get());

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity frame)) continue;
            if (!isShulkerBox(frame.getHeldItemStack())) continue;
            event.renderer.box(entity.getBoundingBox(), fill, line, mode, 0);
            if (shulkerRenderTracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    entity.getX(), entity.getY() + 0.5, entity.getZ(),
                    tracer
                );
            }
        }
    }

    private void renderOminousVaults(Render3DEvent event) {
        ShapeMode mode = getShapeMode(vaultRenderFill.get(), vaultRenderOutline.get());
        if (mode == null) return;
        Color fill = new Color(vaultFillColor.get());
        Color line = new Color(vaultOutlineColor.get());
        Color tracer = new Color(vaultTracerColor.get());

        // Iteration of Collections.synchronizedSet is NOT internally synchronized.
        // Wrap in synchronized on the set itself so concurrent removeAll() in
        // onTick doesn't cause a ConcurrentModificationException.
        synchronized (ominousVaults) {
            for (BlockPos pos : ominousVaults) {
                event.renderer.box(pos, fill, line, mode, 0);
                if (vaultRenderTracer.get()) {
                    event.renderer.line(
                        RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        tracer
                    );
                }
            }
        }
    }

    private void renderTreasure(Render3DEvent event) {
        ShapeMode mode = getShapeMode(treasureRenderFill.get(), treasureRenderOutline.get());
        if (mode == null) return;
        Color fill = new Color(treasureFillColor.get());
        Color line = new Color(treasureOutlineColor.get());
        Color tracer = new Color(treasureTracerColor.get());

        for (BlockPos pos : notifiedTreasure) {
            if (lootedTreasure.contains(pos)) continue;
            if (!pos.isWithinDistance(mc.player.getBlockPos(), 64)) continue;
            event.renderer.box(pos, fill, line, mode, 0);
            if (treasureRenderTracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    tracer
                );
            }
        }
    }

    private void renderEndermanHoldingBlocks(Render3DEvent event) {
        ShapeMode mode = getShapeMode(endermanRenderFill.get(), endermanRenderOutline.get());
        if (mode == null) return;
        Color fill = new Color(endermanFillColor.get());
        Color line = new Color(endermanOutlineColor.get());
        Color tracer = new Color(endermanTracerColor.get());

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndermanEntity enderman)) continue;
            BlockState carried = enderman.getCarriedBlock();
            // getCarriedBlock() can return null on 1.21 (no Optional wrap),
            // NPEs the renderer when the Enderman is not carrying anything.
            if (carried == null || carried.isAir()) continue;
            event.renderer.box(entity.getBoundingBox(), fill, line, mode, 0);
            if (endermanRenderTracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    enderman.getX(), enderman.getY() + 1, enderman.getZ(),
                    tracer
                );
            }
        }
    }

    private void renderXPOrbs(Render3DEvent event) {
        ShapeMode mode = getShapeMode(xpOrbRenderFill.get(), xpOrbRenderOutline.get());
        if (mode == null) return;
        Color fill = new Color(xpOrbFillColor.get());
        Color line = new Color(xpOrbOutlineColor.get());
        Color tracer = new Color(xpOrbTracerColor.get());

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ExperienceOrbEntity)) continue;
            event.renderer.box(entity.getBoundingBox(), fill, line, mode, 0);
            if (xpOrbRenderTracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    entity.getX(), entity.getY() + 0.2, entity.getZ(),
                    tracer
                );
            }
        }
    }

    private void detectAndRenderStackedEntities(Render3DEvent event) {
        ShapeMode mode = getShapeMode(stackedEntitiesRenderFill.get(), stackedEntitiesRenderOutline.get());
        if (mode == null) return;
        Color fill = new Color(stackedEntitiesFillColor.get());
        Color line = new Color(stackedEntitiesOutlineColor.get());
        Color tracer = new Color(stackedEntitiesTracerColor.get());

        Map<BlockPos, List<Entity>> positionMap = new HashMap<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!isInventoryEntity(entity)) continue;
            BlockPos pos = entity.getBlockPos();
            positionMap.computeIfAbsent(pos, k -> new ArrayList<>()).add(entity);
        }

        Set<BlockPos> currentStacked = new HashSet<>();

        for (Map.Entry<BlockPos, List<Entity>> entry : positionMap.entrySet()) {
            BlockPos pos = entry.getKey();
            List<Entity> entities = entry.getValue();

            if (entities.size() < stackedEntitiesMinCount.get()) continue;
            currentStacked.add(pos);

            if (notifiedStackedEntities.add(pos)) {
                notifyStackedEntities(pos, entities);
            }

            for (Entity e : entities) {
                event.renderer.box(e.getBoundingBox(), fill, line, mode, 0);
                if (stackedEntitiesRenderTracer.get()) {
                    event.renderer.line(
                        RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                        e.getX(), e.getY() + 0.5, e.getZ(),
                        tracer
                    );
                }
            }
        }

        notifiedStackedEntities.retainAll(currentStacked);
    }

    private void notifyStackedEntities(BlockPos pos, List<Entity> entities) {
        String type = getEntityTypeName(entities.get(0));
        int count = entities.size();

        if (stackedEntitiesChat.get()) {
            String msg = stackedEntitiesCoords.get()
                ? String.format("§6Found §e§l%d §6stacked %s at §8[§7§o%d§8, §7§o%d§8, §7§o%d§8]", count, type, pos.getX(), pos.getY(), pos.getZ())
                : String.format("§6Found §e§l%d §6stacked %s§7§o!", count, type);
            info(msg);
        }

        if (stackedEntitiesSound.get() && mc.player != null) {
            mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, stackedEntitiesVolume.get().floatValue(), 0.5f);
        }

        if (stackedEntitiesWaypoints.get()) {
            com.hunterbuddy.modules.vanityesp.XaeroWaypointHelper.addWaypoint(
                "VanityESP", pos, "Stacked " + type + " x" + count,
                "⚠", "Gold", stackedEntitiesTempWaypoints.get()
            );
        }
    }

    private String getEntityTypeName(Entity entity) {
        if (entity instanceof ChestMinecartEntity) return "Chest Minecarts";
        if (entity instanceof HopperMinecartEntity) return "Hopper Minecarts";
        if (entity instanceof ChestBoatEntity) return "Chest Boats";
        return "Entities";
    }

    // ---- Chunk scanners ----

    private void scanChunkForVaults(ChunkPos chunkPos, int minY, int maxY) {
        if (!mc.world.isChunkLoaded(chunkPos.x, chunkPos.z)) return;
        WorldChunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
        Set<BlockPos> found = new HashSet<>();

        for (BlockEntity be : chunk.getBlockEntities().values()) {
            BlockState state = mc.world.getBlockState(be.getPos());
            if (state.getBlock() != Blocks.VAULT) continue;
            for (var prop : state.getProperties()) {
                if (prop.getName().equals("ominous") && Boolean.TRUE.equals(state.get(prop))) {
                    found.add(be.getPos());
                    break;
                }
            }
        }

        if (!found.isEmpty()) {
            chunkVaults.put(chunkPos, found);
            ominousVaults.addAll(found);
        }
    }

    private void scanChunkForTreasure(WorldChunk chunk) {
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (!(be instanceof ChestBlockEntity)) continue;
            BlockPos pos = be.getPos();
            if (notifiedTreasure.contains(pos) || lootedTreasure.contains(pos)) continue;
            if (!isBuriedNaturally(pos)) continue;

            notifiedTreasure.add(pos);
            if (notifiedTreasure.size() > 100) notifiedTreasure.remove(0);

            if (treasureSound.get() && mc.player != null) {
                mc.player.playSound(SoundEvents.BLOCK_CHEST_OPEN, treasureVolume.get().floatValue(), 1.0f);
            }
            if (treasureChat.get()) {
                String msg = treasureCoords.get()
                    ? "§3§oFound buried treasure at §8[§7§o" + pos.getX() + "§8, §7§o" + pos.getY() + "§8, §7§o" + pos.getZ() + "§8]"
                    : "§3§oFound buried treasure§7§o!";
                info(msg);
            }
            if (treasureWaypoints.get()) {
                com.hunterbuddy.modules.vanityesp.XaeroWaypointHelper.addWaypoint(
                    "VanityESP", pos, "Buried Treasure", "★", "Blue", treasureTempWaypoints.get()
                );
            }
        }
    }

    // ---- Utility ----

    private ShapeMode getShapeMode(boolean fill, boolean outline) {
        if (fill && outline) return ShapeMode.Both;
        if (fill) return ShapeMode.Sides;
        if (outline) return ShapeMode.Lines;
        return null;
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.isOf(Items.SHULKER_BOX)
            || stack.isOf(Items.WHITE_SHULKER_BOX) || stack.isOf(Items.ORANGE_SHULKER_BOX)
            || stack.isOf(Items.MAGENTA_SHULKER_BOX) || stack.isOf(Items.LIGHT_BLUE_SHULKER_BOX)
            || stack.isOf(Items.YELLOW_SHULKER_BOX) || stack.isOf(Items.LIME_SHULKER_BOX)
            || stack.isOf(Items.PINK_SHULKER_BOX) || stack.isOf(Items.GRAY_SHULKER_BOX)
            || stack.isOf(Items.LIGHT_GRAY_SHULKER_BOX) || stack.isOf(Items.CYAN_SHULKER_BOX)
            || stack.isOf(Items.PURPLE_SHULKER_BOX) || stack.isOf(Items.BLUE_SHULKER_BOX)
            || stack.isOf(Items.BROWN_SHULKER_BOX) || stack.isOf(Items.GREEN_SHULKER_BOX)
            || stack.isOf(Items.RED_SHULKER_BOX) || stack.isOf(Items.BLACK_SHULKER_BOX);
    }

    private boolean isInventoryEntity(Entity entity) {
        return entity instanceof ChestMinecartEntity
            || entity instanceof HopperMinecartEntity
            || entity instanceof ChestBoatEntity
            || entity instanceof Inventory;
    }

    private boolean isBuriedNaturally(BlockPos pos) {
        Block blockAbove = mc.world.getBlockState(pos.up()).getBlock();
        return blockAbove == Blocks.DIRT
            || blockAbove == Blocks.COARSE_DIRT
            || blockAbove == Blocks.PODZOL
            || blockAbove == Blocks.GRASS_BLOCK
            || blockAbove == Blocks.MYCELIUM
            || blockAbove == Blocks.SAND
            || blockAbove == Blocks.RED_SAND
            || blockAbove == Blocks.GRAVEL
            || blockAbove == Blocks.CLAY;
    }
}