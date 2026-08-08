package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaeroplus.event.ChunkDataEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RotationDetector extends Module {
    private final SettingGroup sgBlocks = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgPerformance = settings.createGroup("Performance");

    private final Setting<Boolean> detectLogs = sgBlocks.add(new BoolSetting.Builder()
        .name("logs")
        .description("Trees only generate vertical logs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectBasalt = sgBlocks.add(new BoolSetting.Builder()
        .name("basalt")
        .description("Basalt deltas/pillars only generate vertical.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectPurpur = sgBlocks.add(new BoolSetting.Builder()
        .name("purpur-pillars")
        .description("End cities only generate vertical purpur pillars.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectHayBlocks = sgBlocks.add(new BoolSetting.Builder()
        .name("hay-blocks")
        .description("Village hay bales only generate vertical.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectDeepslate = sgBlocks.add(new BoolSetting.Builder()
        .name("deepslate")
        .description("Regular deepslate only generates vertical in natural terrain.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectPaletteGhosts = sgBlocks.add(new BoolSetting.Builder()
        .name("palette-ghosts")
        .description("Flags sections whose palette holds never-generated blocks (nether portal, beacon...) - proof of player activity even if the blocks were removed years ago.")
        .defaultValue(false)
        .onChanged(v -> {
            if (!v) {
                this.ghostCache.clear();
                this.ghostWaypointed.clear();
            }
        })
        .build()
    );

    private final Setting<Boolean> ghostWaypoints = sgBlocks.add(new BoolSetting.Builder()
        .name("ghost-waypoints")
        .description("Creates a Xaeros waypoint on chunks with palette ghosts.")
        .defaultValue(true)
        .visible(detectPaletteGhosts::get)
        .build()
    );

    private final Setting<Integer> minY = sgFilters.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan.")
        .defaultValue(-64)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> maxY = sgFilters.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan.")
        .defaultValue(320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Boolean> ignoreNearAir = sgFilters.add(new BoolSetting.Builder()
        .name("ignore-near-air")
        .description("Ignore blocks with air above. Filters surface builds.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> airCheckDistance = sgFilters.add(new IntSetting.Builder()
        .name("air-check-distance")
        .description("Blocks above to check for air.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 20)
        .visible(ignoreNearAir::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Side color for detected blocks.")
        .defaultValue(new SettingColor(255, 0, 0, 40))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line color for detected blocks.")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .build()
    );

    private final Setting<SettingColor> ghostSideColor = sgRender.add(new ColorSetting.Builder()
        .name("ghost-side-color")
        .description("Side color for palette ghost sections.")
        .defaultValue(new SettingColor(170, 0, 255, 30))
        .visible(detectPaletteGhosts::get)
        .build()
    );

    private final Setting<SettingColor> ghostLineColor = sgRender.add(new ColorSetting.Builder()
        .name("ghost-line-color")
        .description("Line color for palette ghost sections.")
        .defaultValue(new SettingColor(170, 0, 255, 200))
        .visible(detectPaletteGhosts::get)
        .build()
    );

    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
        .name("render-distance")
        .description("Maximum render distance.")
        .defaultValue(256)
        .min(16)
        .sliderRange(16, 512)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgPerformance.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("Chunks to scan per tick.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> maxCacheSize = sgPerformance.add(new IntSetting.Builder()
        .name("max-cache-size")
        .description("Maximum cached chunks.")
        .defaultValue(4000)
        .min(100)
        .sliderRange(100, 10000)
        .build()
    );

    // Blocks that appear in none of the 1202 structure templates and nowhere in the
    // levelgen/worldgen code (verified against the decompiled 1.21.11 server jar).
    // Their id in a section palette can only have been put there by a player.
    private static final Set<Block> GHOST_BLOCKS = Set.of(
        Blocks.NETHER_PORTAL, Blocks.BEACON, Blocks.ANVIL, Blocks.ENCHANTING_TABLE,
        Blocks.SHULKER_BOX, Blocks.RESPAWN_ANCHOR, Blocks.JUKEBOX, Blocks.PISTON,
        Blocks.OBSERVER, Blocks.DROPPER, Blocks.NETHERITE_BLOCK, Blocks.IRON_BLOCK,
        Blocks.SPONGE
    );

    private final Map<Long, Set<BlockPos>> chunkCache = new ConcurrentHashMap<>();
    // Section origins whose palette carries a ghost block, keyed by chunk.
    private final Map<Long, Set<BlockPos>> ghostCache = new ConcurrentHashMap<>();
    private final Set<Long> ghostWaypointed = Collections.synchronizedSet(new HashSet<>());
    private final Set<Long> pendingChunks = Collections.synchronizedSet(new LinkedHashSet<>());
    private int totalDetected = 0;

    public RotationDetector() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "rotation-detector", "Detects horizontal blocks that only spawn vertical naturally.");
    }

    @Override
    public void onActivate() {
        chunkCache.clear();
        ghostCache.clear();
        ghostWaypointed.clear();
        pendingChunks.clear();
        totalDetected = 0;
        if (mc.world != null && mc.player != null) {
            int viewDist = mc.options.getViewDistance().getValue();
            ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());

            for (int dx = -viewDist; dx <= viewDist; dx++) {
                for (int dz = -viewDist; dz <= viewDist; dz++) {
                    int cx = playerChunk.x + dx;
                    int cz = playerChunk.z + dz;
                    if (mc.world.getChunk(cx, cz, ChunkStatus.FULL, false) != null) {
                        pendingChunks.add(ChunkPos.toLong(cx, cz));
                    }
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        chunkCache.clear();
        ghostCache.clear();
        ghostWaypointed.clear();
        pendingChunks.clear();
        totalDetected = 0;
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        long key = ChunkPos.toLong(event.chunk().getPos().x, event.chunk().getPos().z);
        chunkCache.remove(key);
        pendingChunks.add(key);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world != null && mc.player != null && !pendingChunks.isEmpty()) {
            int processed = 0;
            Iterator<Long> iter = pendingChunks.iterator();

            while (iter.hasNext() && processed < chunksPerTick.get()) {
                long key = iter.next();
                iter.remove();
                Chunk chunk = mc.world.getChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key), ChunkStatus.FULL, false);
                if (chunk != null) {
                    Set<BlockPos> detected = scanChunk(chunk);
                    if (!detected.isEmpty()) {
                        chunkCache.put(key, detected);
                    } else {
                        chunkCache.remove(key);
                    }
                    if (detectPaletteGhosts.get()) {
                        Set<BlockPos> ghosts = scanSectionPalettes(chunk);
                        if (!ghosts.isEmpty()) {
                            ghostCache.put(key, ghosts);
                            if (ghostWaypoints.get()) addGhostWaypoint(chunk.getPos(), ghosts);
                        } else {
                            ghostCache.remove(key);
                        }
                    }
                    processed++;
                }
            }
            enforceCacheLimit();
            updateCount();
        }
    }

    private Set<BlockPos> scanChunk(Chunk chunk) {
        Set<BlockPos> detected = new HashSet<>();
        if (mc.world == null) return detected;

        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        int scanMinY = Math.max(minY.get(), mc.world.getBottomY());
        int scanMaxY = Math.min(maxY.get(), mc.world.getTopYInclusive());
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = scanMinY; y <= scanMaxY; y++) {
                    pos.set(startX + x, y, startZ + z);
                    BlockState state = chunk.getBlockState(pos);
                    if (!state.isAir() && isHorizontalUnnatural(state) && (!ignoreNearAir.get() || !hasAirAbove(pos))) {
                        detected.add(pos.toImmutable());
                    }
                }
            }
        }
        return detected;
    }

    /**
     * Checks section palettes for ghost entries instead of walking block positions.
     *
     * <p>Palette entries are never pruned: once a block state enters a section palette it
     * survives being broken, saved, reloaded and sent over the network — and ViaVersion
     * remaps palettes in place without dropping unused entries. So a nether portal that was
     * built and fully dismantled still leaves {@code nether_portal} in the palette, and
     * {@code ChunkSection.hasAny} reads the palette, not the block data.
     *
     * <p>Empty sections are deliberately not skipped: a fully mined-out section is exactly
     * where dead entries live.
     */
    private Set<BlockPos> scanSectionPalettes(Chunk chunk) {
        Set<BlockPos> ghosts = new HashSet<>();
        ChunkPos chunkPos = chunk.getPos();
        ChunkSection[] sections = chunk.getSectionArray();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null) continue;

            int bottomY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(i));
            if (bottomY + 15 < minY.get() || bottomY > maxY.get()) continue;

            if (section.hasAny(state -> GHOST_BLOCKS.contains(state.getBlock()))) {
                ghosts.add(new BlockPos(chunkPos.getStartX(), bottomY, chunkPos.getStartZ()));
            }
        }
        return ghosts;
    }

    private void addGhostWaypoint(ChunkPos chunkPos, Set<BlockPos> ghosts) {
        if (!ghostWaypointed.add(chunkPos.toLong())) return;

        WaypointSet waypointSet = getWaypointSet();
        if (waypointSet == null) return;

        BlockPos first = ghosts.iterator().next();
        waypointSet.add(new Waypoint(
            chunkPos.getStartX() + 8,
            first.getY() + 8,
            chunkPos.getStartZ() + 8,
            "Ghost " + chunkPos.x + ", " + chunkPos.z,
            "G",
            13,
            0,
            false));
    }

    private WaypointSet getWaypointSet() {
        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return null;
        MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
        if (currentWorld == null) return null;
        return currentWorld.getCurrentWaypointSet();
    }

    private boolean isHorizontalUnnatural(BlockState state) {
        if (!state.contains(Properties.AXIS)) return false;
        Direction.Axis axis = state.get(Properties.AXIS);
        if (axis == Direction.Axis.Y) return false;

        Block block = state.getBlock();
        if (detectLogs.get() && isLog(state)) return true;
        if (detectBasalt.get() && isBasalt(block)) return true;
        if (detectPurpur.get() && block == Blocks.PURPUR_PILLAR) return true;
        if (detectHayBlocks.get() && block == Blocks.HAY_BLOCK) return true;
        return detectDeepslate.get() && isDeepslatePillar(block);
    }

    private boolean isLog(BlockState state) {
        return state.isIn(BlockTags.LOGS) || state.isOf(Blocks.BAMBOO_BLOCK);
    }

    private boolean isBasalt(Block block) {
        return block == Blocks.BASALT || block == Blocks.POLISHED_BASALT;
    }

    private boolean isDeepslatePillar(Block block) {
        return block == Blocks.DEEPSLATE;
    }

    private boolean hasAirAbove(BlockPos pos) {
        if (mc.world == null) return false;
        BlockPos.Mutable check = new BlockPos.Mutable();
        int distance = airCheckDistance.get();
        for (int dy = 1; dy <= distance; dy++) {
            check.set(pos.getX(), pos.getY() + dy, pos.getZ());
            if (mc.world.getBlockState(check).isAir()) return true;
        }
        return false;
    }

    private void enforceCacheLimit() {
        while (chunkCache.size() > maxCacheSize.get()) {
            Iterator<Long> iter = chunkCache.keySet().iterator();
            if (iter.hasNext()) {
                iter.next();
                iter.remove();
            }
        }
        while (ghostCache.size() > maxCacheSize.get()) {
            Iterator<Long> iter = ghostCache.keySet().iterator();
            if (iter.hasNext()) {
                iter.next();
                iter.remove();
            }
        }
    }

    private void updateCount() {
        totalDetected = 0;
        for (Set<BlockPos> blocks : chunkCache.values()) {
            totalDetected += blocks.size();
        }
        for (Set<BlockPos> sections : ghostCache.values()) {
            totalDetected += sections.size();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world != null && mc.player != null) {
            double maxDistSq = (double) renderDistance.get() * renderDistance.get();
            double px = mc.player.getX();
            double py = mc.player.getY();
            double pz = mc.player.getZ();
            Color side = new Color(sideColor.get());
            Color line = new Color(lineColor.get());

            for (Set<BlockPos> blocks : chunkCache.values()) {
                for (BlockPos pos : blocks) {
                    double dx = pos.getX() + 0.5 - px;
                    double dy = pos.getY() + 0.5 - py;
                    double dz = pos.getZ() + 0.5 - pz;
                    if (!(dx * dx + dy * dy + dz * dz > maxDistSq)) {
                        event.renderer.box(pos, side, line, shapeMode.get(), 0);
                    }
                }
            }

            if (!ghostCache.isEmpty()) {
                Color ghostSide = new Color(ghostSideColor.get());
                Color ghostLine = new Color(ghostLineColor.get());

                for (Set<BlockPos> sections : ghostCache.values()) {
                    for (BlockPos pos : sections) {
                        double dx = pos.getX() + 8 - px;
                        double dy = pos.getY() + 8 - py;
                        double dz = pos.getZ() + 8 - pz;
                        if (!(dx * dx + dy * dy + dz * dz > maxDistSq)) {
                            event.renderer.box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 16, pos.getY() + 16, pos.getZ() + 16, ghostSide, ghostLine, shapeMode.get(), 0);
                        }
                    }
                }
            }
        }
    }

    public String getInfoString() {
        return String.valueOf(totalDetected);
    }
}