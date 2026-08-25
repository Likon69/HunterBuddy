package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.registry.RegistryKey;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;
import xaeroplus.settings.Settings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes one line of measurements per received chunk to a CSV, and nothing else.
 *
 * <p>This is the collector half of the old-chunk work: no radar, no verdict, no
 * classifier. XaeroPlus already ships two of those — {@code OldChunks} looks for
 * a fixed list of blocks introduced in 1.17-1.18 above y=5, {@code
 * PaletteNewChunks} looks at whether the server ever rewrote the chunk's palette
 * — and both are recorded here as columns so a classifier built on this data can
 * be measured against them rather than assumed better.
 *
 * <p>Chunks arrive faster than they can be walked, so the network side does
 * nothing but stamp a row and queue the coordinates. The block work happens on
 * the main thread under a per-tick budget, on a chunk re-fetched by position:
 * holding the chunk object would pin a few hundred megabytes of palettes when
 * the backlog grows.
 */
public class ChunkProfiler extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLabels = settings.createGroup("Ground Truth");

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("How many queued chunks to walk each tick. Every chunk is a few thousand block reads, so this is the knob that decides whether collection costs you frames.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> queueLimit = sgGeneral.add(new IntSetting.Builder()
        .name("queue-limit")
        .description("How many chunks may wait in the backlog. Past it the newest are dropped and counted, never silently: the dropped tally is on the HUD and in the summary.")
        .defaultValue(512)
        .min(32)
        .sliderRange(32, 4096)
        .build()
    );

    private final Setting<Boolean> skipSeen = sgGeneral.add(new BoolSetting.Builder()
        .name("skip-seen")
        .description("Ignore chunks XaeroPlus has already recorded from an earlier visit. Leave this on to keep one line per chunk instead of one per flyover.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> trailKey = sgLabels.add(new KeybindSetting.Builder()
        .name("mark-trail")
        .description("Marks the chunk you are standing in as trail, in the labels file. Press it only when you are sure by eye.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Keybind> offTrailKey = sgLabels.add(new KeybindSetting.Builder()
        .name("mark-off-trail")
        .description("Marks the chunk you are standing in as off trail.")
        .defaultValue(Keybind.none())
        .build()
    );

    /** Rows per file before rolling over to the next one. */
    private static final int ROWS_PER_FILE = 100_000;

    /** How far below a water surface the sea floor is looked for. */
    private static final int FLOOR_SCAN_DEPTH = 48;

    private static final String HEADER = String.join(",",
        // identity
        "dim", "cx", "cz", "epoch_ms", "px", "py", "pz", "dist", "ms_since_prev", "queue_wait_ms",
        // xaero verdicts, plus the context needed to read them
        "xaero_old", "xaero_new", "xaero_seen", "forced", "xaero_upgraded",
        // floor
        "lowest_y", "has_below_zero", "bedrock_0_4", "bedrock_m64_m60", "sections",
        // relief
        "surf_min", "surf_max", "surf_mean", "surf_sd", "edge_north", "edge_south", "edge_east", "edge_west",
        // water
        "water", "water_top", "water_cols", "kelp", "seagrass", "coral", "sea_pickle", "bubble", "magma",
        // floor composition under water columns
        "floor_gravel", "floor_sand", "floor_clay", "floor_dirt",
        // biomes
        "biome_count", "biome_vertical", "ocean_warm", "ocean_lukewarm", "ocean_cold", "ocean_frozen",
        "mountain_118", "cave_biome", "mangrove", "cherry", "pale_garden", "biomes",
        // markers, counted above y=0 only
        "copper", "amethyst", "smooth_basalt", "tuff", "deepslate", "azalea", "dripleaf", "moss",
        "cave_vines", "dripstone", "bamboo", "berry", "beehive", "mud", "calcite", "lichen", "sculk",
        "ancient_debris", "blackstone", "basalt", "nylium", "nether_gold", "chain", "ore_bands",
        // what XaeroPlus would see: it starts its scan at y=5
        "deepslate_5plus", "copper_5plus", "copper_bands", "copper_edge", "copper_interior",
        // caves, counted from y=0 up so an empty pre-1.18 underside cannot drown them
        "air_0_60", "air_0_sea",
        // block entities
        "be_count", "be_types"
    );

    private final java.util.concurrent.ConcurrentLinkedDeque<Pending> queue = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();

    private volatile long lastChunkMs;
    private volatile String lastVerdict = "-";

    private int profiled;
    private int rowsInFile;
    private int fileIndex;
    private int pendingFlush;
    private BufferedWriter profileWriter;
    private BufferedWriter labelWriter;
    private boolean trailPressed;
    private boolean offTrailPressed;

    public ChunkProfiler() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "chunk-profiler",
            "Writes one line of measurements per received chunk to a CSV, for offline analysis. Collector only, no radar.");
    }

    public static ChunkProfiler get() {
        return Modules.get().get(ChunkProfiler.class);
    }

    public int profiledCount() {
        return profiled;
    }

    public int queueSize() {
        return queued.get();
    }

    public int droppedCount() {
        return dropped.get();
    }

    public String lastVerdict() {
        return lastVerdict;
    }

    @Override
    public void onActivate() {
        profiled = 0;
        rowsInFile = 0;
        fileIndex = 0;
        pendingFlush = 0;
        lastChunkMs = 0L;
        lastVerdict = "-";
        dropped.set(0);
        queue.clear();
        queued.set(0);
        XaeroPlus.EVENT_BUS.register(this);

        // Both verdict columns come from XaeroPlus modules that can be switched
        // off, and a module that is off answers false rather than failing. Without
        // this the whole flight would land with two columns of zeroes and nothing
        // anywhere to say why.
        if (!ModuleManager.getModule(OldChunks.class).isEnabled()
            || !ModuleManager.getModule(PaletteNewChunks.class).isEnabled()) {
            warning("XaeroPlus OldChunks or PaletteNewChunks is off: xaero_old and xaero_new will be 0 on every row, silently.");
        }
    }

    @Override
    public void onDeactivate() {
        XaeroPlus.EVENT_BUS.unregister(this);
        queue.clear();
        queued.set(0);
        info("Profiled %d chunk(s), dropped %d.", profiled, dropped.get());
        closeWriters();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        queue.clear();
        queued.set(0);
        closeWriters();
    }

    /**
     * Runs on whichever thread XaeroPlus hands the chunk over on, so it stamps a
     * row and leaves. The priority matches OldChunkNotifier's, which is what puts
     * this behind XaeroPlus's own handlers and makes their verdicts readable here.
     */
    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event) {
        if (mc.player == null || mc.world == null) return;

        WorldChunk chunk = event.chunk();
        ChunkPos pos = chunk.getPos();
        RegistryKey<World> dimension = chunk.getWorld().getRegistryKey();

        // Stamped for every chunk that lands, kept or not. The gap between two
        // arrivals is meant to say how long the server spent on one, and updating
        // it after the skip below would have measured the gap between two chunks
        // we happened to keep instead.
        long now = System.currentTimeMillis();
        long previous = lastChunkMs;
        lastChunkMs = now;

        if (skipSeen.get() && event.seenChunk()) return;

        Pending pending = newPending(pos, dimension, now, previous == 0L ? -1L : now - previous,
            event.seenChunk(), false);

        lastVerdict = (pending.xaeroOld ? "1.12" : pending.xaeroNew ? "fresh" : "stored")
            + (pending.xaeroSeen ? " seen" : "");

        if (queued.get() >= queueLimit.get()) {
            dropped.incrementAndGet();
            return;
        }

        queue.addLast(pending);
        queued.incrementAndGet();
    }

    private Pending newPending(ChunkPos pos, RegistryKey<World> dimension, long now, long sincePrev,
                               boolean seen, boolean forced) {
        Pending pending = new Pending();
        pending.dimension = dimension.getValue().toString();
        pending.cx = pos.x;
        pending.cz = pos.z;
        pending.epochMs = now;
        pending.sincePrevMs = sincePrev;
        pending.xaeroSeen = seen;
        pending.forced = forced;

        Vec3d player = mc.player.getEntityPos();
        pending.px = player.x;
        pending.py = player.y;
        pending.pz = player.z;
        // Horizontal only: how far ahead of you the chunk landed is the question,
        // and altitude would just add noise from the elytra climb.
        pending.distance = Math.hypot((pos.x << 4) + 8 - player.x, (pos.z << 4) + 8 - player.z);

        // Read now rather than at processing time: these are the verdicts as they
        // stood when the chunk landed, which is what a later comparison needs.
        pending.xaeroOld = ModuleManager.getModule(OldChunks.class).isOldChunk(pos.x, pos.z, dimension);
        pending.xaeroNew = ModuleManager.getModule(PaletteNewChunks.class).isNewChunk(pos.x, pos.z, dimension);

        // Recorded per row so the file stands on its own: this option changes what
        // xaero_new means, and nothing in the CSV would otherwise say which of the
        // two meanings was in force.
        pending.upgraded = Boolean.TRUE.equals(Settings.REGISTRY.paletteNewChunksVersionUpgradedChunks.get());

        return pending;
    }

    /**
     * Queues the chunk under the player and its eight neighbours for profiling,
     * whatever skip-seen says.
     *
     * <p>A label with no measurements behind it is worth nothing, and the chunk
     * you are standing on to press the key is exactly the one XaeroPlus is most
     * likely to have seen on an earlier pass. These jump the queue and ignore its
     * limit: nine rows, and they are the only ones with ground truth attached.
     * Their {@code xaero_seen} is written as 0 because the flag only exists on the
     * arrival event — read {@code forced} first.
     */
    private void forceProfile() {
        ChunkPos centre = mc.player.getChunkPos();
        RegistryKey<World> dimension = mc.world.getRegistryKey();
        long now = System.currentTimeMillis();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                queue.addFirst(newPending(new ChunkPos(centre.x + dx, centre.z + dz),
                    dimension, now, -1L, false, true));
                queued.incrementAndGet();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        handleLabelKeys();

        if (mc.world == null) return;

        String dimension = mc.world.getRegistryKey().getValue().toString();
        int budget = chunksPerTick.get();

        while (budget-- > 0) {
            Pending pending = queue.pollFirst();
            if (pending == null) break;
            queued.decrementAndGet();

            // Walking through a portal with a backlog waiting would otherwise
            // measure the chunk that now sits at those coordinates in the new
            // dimension and file it under the old one.
            if (!pending.dimension.equals(dimension)) {
                dropped.incrementAndGet();
                continue;
            }

            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pending.cx, pending.cz);

            // Unloaded while it waited. Counted, not ignored: a growing tally here
            // means the budget is too small for the speed you are flying at.
            if (chunk == null) {
                dropped.incrementAndGet();
                continue;
            }

            pending.waitMs = System.currentTimeMillis() - pending.epochMs;
            writeRow(profile(chunk, pending));
            profiled++;
        }
    }

    private void handleLabelKeys() {
        if (mc.player == null || mc.currentScreen != null) {
            trailPressed = false;
            offTrailPressed = false;
            return;
        }

        if (!trailKey.get().isPressed()) {
            trailPressed = false;
        } else if (!trailPressed) {
            trailPressed = true;
            mark("trail");
        }

        if (!offTrailKey.get().isPressed()) {
            offTrailPressed = false;
        } else if (!offTrailPressed) {
            offTrailPressed = true;
            mark("off_trail");
        }
    }

    /**
     * Records a label, unless both detectors disagree with it.
     *
     * <p>A trail press needs XaeroPlus <em>or</em> the radar to read the chunk
     * as 1.12. The radar finds chunks XaeroPlus never colours — copper under the
     * allowance, nothing else — and those are precisely the ones whose truth is
     * in question, so a veto on XaeroPlus alone would forbid the labels the test
     * exists to collect. The file says which of the two agreed.
     *
     * <p>The veto is still worth having: a press that neither detector supports
     * is a press aimed at the wrong chunk — the one behind you at flying speed,
     * most often — and a wrong label costs far more than a missing one.
     *
     * <p>No coordinates in chat: the chunk under you is the only one a press can
     * mean, and the file has the numbers.
     */
    private void mark(String label) {
        ChunkPos pos = mc.player.getChunkPos();
        boolean old = ModuleManager.getModule(OldChunks.class).isOldChunk(pos.x, pos.z, mc.world.getRegistryKey());
        ChunkRadar radar = ChunkRadar.get();
        boolean hit = radar != null && radar.isActive() && radar.isHit(pos);

        if (label.equals("trail") && !old && !hit) {
            error("Not marking the chunk under you as trail: neither XaeroPlus nor the radar reads it as 1.12.");
            return;
        }

        if (label.equals("off_trail") && (old || hit)) {
            error("Not marking the chunk under you as off trail: %s reads it as 1.12.", old ? "XaeroPlus" : "the radar");
            return;
        }

        String source = old && hit ? "both" : old ? "xaero" : hit ? "radar" : "none";

        forceProfile();
        writeLabel(label, source);
    }

    // Measurement

    private String profile(WorldChunk chunk, Pending p) {
        World world = chunk.getWorld();
        int bottomY = chunk.getBottomY();
        ChunkSection[] sections = chunk.getSectionArray();
        int baseX = p.cx << 4;
        int baseZ = p.cz << 4;

        Census census = new Census();
        List<String> oreBands = new ArrayList<>();
        List<String> copperBands = new ArrayList<>();
        TreeSet<String> biomes = new TreeSet<>();
        boolean biomeVertical = false;
        int lowestY = Integer.MAX_VALUE;
        boolean belowZero = false;
        int bedrockLow = 0;
        int bedrockDeep = 0;
        int deepslate5 = 0;
        int copper5 = 0;
        long airBelow60 = 0L;
        long airBelowSea = 0L;
        int seaLevel = world.getSeaLevel();

        String[][] biomeColumns = new String[4][4];
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null) continue;

            int sectionBottom = bottomY + (i << 4);
            int sectionTop = sectionBottom + 15;

            // Biomes live in every section, empty ones included, and they are the
            // one measurement that separates a converted 1.12 chunk from a native
            // 1.18 one: 3D biomes did not exist before 1.18, so an old chunk keeps
            // the same biome from bedrock to sky.
            //
            // Read from y=0 up only. Everything below zero on this server was put
            // there by the regeneration, cave biomes included, so counting it made
            // every old chunk look vertically varied for a reason that has nothing
            // to do with its age.
            for (int bx = 0; sectionTop >= 0 && bx < 4; bx++) {
                for (int bz = 0; bz < 4; bz++) {
                    for (int by = 0; by < 4; by++) {
                        String biome = biomeName(section, bx, by, bz);
                        biomes.add(biome);
                        String seen = biomeColumns[bx][bz];
                        if (seen == null) biomeColumns[bx][bz] = biome;
                        else if (!seen.equals(biome)) biomeVertical = true;
                    }
                }
            }

            if (section.isEmpty()) {
                airBelow60 += emptyAir(sectionBottom, sectionTop, 60);
                airBelowSea += emptyAir(sectionBottom, sectionTop, seaLevel);
                continue;
            }

            boolean aboveZero = sectionBottom >= 0;
            int oreInSection = 0;
            SectionTotals totals = new SectionTotals();

            // One pass over the packed storage per section rather than 4096 block
            // lookups: the palette already knows how many of each state it holds.
            final boolean countMarkers = aboveZero;
            section.getBlockStateContainer().count((state, count) -> {
                Block block = state.getBlock();
                if (state.isAir()) {
                    totals.air += count;
                    return;
                }

                totals.solid += count;
                if (block == Blocks.WATER) census.water += count;
                if (countMarkers) census.marker(block, count);
                if (isOre(block)) totals.ore += count;
                if (countMarkers && COPPER.contains(block)) totals.copper += count;
            });

            oreInSection = totals.ore;
            oreBands.add(sectionBottom + ":" + oreInSection);
            if (totals.copper > 0) copperBands.add(sectionBottom + ":" + totals.copper);

            if (totals.solid > 0) {
                if (sectionBottom < lowestY) lowestY = lowestSolidY(section, sectionBottom);
                if (sectionBottom < 0) belowZero = true;
            }

            // Both windows start at y=0, never at the world floor. A pre-1.18 chunk
            // that was never extended downwards has four empty sections under zero,
            // and counting those would put 16384 into a cave measurement for chunks
            // with no caves at all — the strongest column in the file, measuring
            // nothing but the absence of a world bottom.
            //
            // The two ceilings are handled apart because they are not ordered: the
            // Nether puts its sea level at 32, well under the 60 the other one uses.
            boolean whole60 = sectionBottom >= 0 && sectionTop < 60;
            boolean wholeSea = sectionBottom >= 0 && sectionTop < seaLevel;
            boolean walk60 = !whole60 && sectionTop >= 0 && sectionBottom < 60;
            boolean walkSea = !wholeSea && sectionTop >= 0 && sectionBottom < seaLevel;

            if (whole60) airBelow60 += totals.air;
            if (wholeSea) airBelowSea += totals.air;

            if (walk60 || walkSea) {
                // Only a section cut by y=0 or by a ceiling has to be walked.
                for (int y = 0; y < 16; y++) {
                    int worldY = sectionBottom + y;
                    if (worldY < 0) continue;
                    if (worldY >= 60 && worldY >= seaLevel) break;

                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (!section.getBlockState(x, y, z).isAir()) continue;
                            if (walk60 && worldY < 60) airBelow60++;
                            if (walkSea && worldY < seaLevel) airBelowSea++;
                        }
                    }
                }
            }

            // The band XaeroPlus actually looks at. It starts its scan at y=5, so a
            // regenerated underside that pushes deepslate or copper above that line
            // is what would make it call an old chunk modern. Counted here so the
            // guess can be checked rather than repeated.
            if (sectionBottom == 0 && section.hasAny(ChunkProfiler::isXaeroBlind)) {
                for (int y = 5; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            Block block = section.getBlockState(x, y, z).getBlock();
                            if (DEEPSLATE.contains(block)) deepslate5++;
                            else if (COPPER.contains(block)) copper5++;
                        }
                    }
                }
            }

            // Bedrock only matters in two thin bands, and the palette says up front
            // whether this section holds any at all.
            if ((sectionBottom <= 4 && sectionTop >= 0) || (sectionBottom <= -60 && sectionTop >= -64)) {
                if (section.hasAny(state -> state.isOf(Blocks.BEDROCK))) {
                    for (int y = 0; y < 16; y++) {
                        int worldY = sectionBottom + y;
                        boolean low = worldY >= 0 && worldY <= 4;
                        boolean deep = worldY >= -64 && worldY <= -60;
                        if (!low && !deep) continue;

                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                if (!section.getBlockState(x, y, z).isOf(Blocks.BEDROCK)) continue;
                                if (low) bedrockLow++;
                                else bedrockDeep++;
                            }
                        }
                    }
                }
            }
        }

        // Relief and water, from the heightmap the client already received.
        Heightmap heightmap = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE);
        int surfMin = Integer.MAX_VALUE;
        int surfMax = Integer.MIN_VALUE;
        double surfSum = 0.0;
        double surfSquares = 0.0;
        double[] edges = new double[4];
        int waterCols = 0;
        int waterTop = Integer.MIN_VALUE;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int height = heightmap.get(x, z);
                surfMin = Math.min(surfMin, height);
                surfMax = Math.max(surfMax, height);
                surfSum += height;
                surfSquares += (double) height * height;

                if (z == 0) edges[0] += height;
                if (z == 15) edges[1] += height;
                if (x == 15) edges[2] += height;
                if (x == 0) edges[3] += height;

                int topY = height - 1;
                if (topY < bottomY) continue;

                BlockState top = chunk.getBlockState(cursor.set(baseX + x, topY, baseZ + z));
                if (!top.isOf(Blocks.WATER)) continue;

                waterCols++;
                waterTop = Math.max(waterTop, topY);
                census.floor(seaFloor(chunk, cursor, baseX + x, topY, baseZ + z, bottomY));
            }
        }

        double surfMean = surfSum / 256.0;
        double surfSd = Math.sqrt(Math.max(0.0, surfSquares / 256.0 - surfMean * surfMean));

        // Block entities: nothing to do with age, everything to do with the radar
        // this collection is meant to feed.
        TreeMap<String, Integer> blockEntities = new TreeMap<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            String id = Registries.BLOCK_ENTITY_TYPE.getId(be.getType()).toString();
            blockEntities.merge(id, 1, Integer::sum);
        }

        StringBuilder row = new StringBuilder(512);
        append(row, p.dimension);
        append(row, p.cx);
        append(row, p.cz);
        append(row, p.epochMs);
        append(row, fixed(p.px));
        append(row, fixed(p.py));
        append(row, fixed(p.pz));
        append(row, fixed(p.distance));
        append(row, p.sincePrevMs);
        append(row, p.waitMs);
        append(row, p.xaeroOld ? 1 : 0);
        append(row, p.xaeroNew ? 1 : 0);
        append(row, p.xaeroSeen ? 1 : 0);
        append(row, p.forced ? 1 : 0);
        append(row, p.upgraded ? 1 : 0);
        append(row, lowestY == Integer.MAX_VALUE ? bottomY : lowestY);
        append(row, belowZero ? 1 : 0);
        append(row, bedrockLow);
        append(row, bedrockDeep);
        append(row, sections.length);
        append(row, surfMin == Integer.MAX_VALUE ? 0 : surfMin);
        append(row, surfMax == Integer.MIN_VALUE ? 0 : surfMax);
        append(row, fixed(surfMean));
        append(row, fixed(surfSd));
        append(row, fixed(edges[0] / 16.0));
        append(row, fixed(edges[1] / 16.0));
        append(row, fixed(edges[2] / 16.0));
        append(row, fixed(edges[3] / 16.0));
        append(row, census.water);
        append(row, waterTop == Integer.MIN_VALUE ? 0 : waterTop);
        append(row, waterCols);
        append(row, census.kelp);
        append(row, census.seagrass);
        append(row, census.coral);
        append(row, census.seaPickle);
        append(row, census.bubble);
        append(row, census.magma);
        append(row, census.floorGravel);
        append(row, census.floorSand);
        append(row, census.floorClay);
        append(row, census.floorDirt);
        append(row, biomes.size());
        append(row, biomeVertical ? 1 : 0);
        append(row, flag(biomes, "warm_ocean"));
        append(row, flag(biomes, "lukewarm_ocean"));
        append(row, flag(biomes, "cold_ocean"));
        append(row, flag(biomes, "frozen_ocean"));
        append(row, anyOf(biomes, "meadow", "grove", "snowy_slopes", "jagged_peaks", "frozen_peaks", "stony_peaks"));
        append(row, anyOf(biomes, "dripstone_caves", "lush_caves", "deep_dark"));
        append(row, flag(biomes, "mangrove_swamp"));
        append(row, flag(biomes, "cherry_grove"));
        append(row, flag(biomes, "pale_garden"));
        append(row, join(biomes));
        append(row, census.copper);
        append(row, census.amethyst);
        append(row, census.smoothBasalt);
        append(row, census.tuff);
        append(row, census.deepslate);
        append(row, census.azalea);
        append(row, census.dripleaf);
        append(row, census.moss);
        append(row, census.caveVines);
        append(row, census.dripstone);
        append(row, census.bamboo);
        append(row, census.berry);
        append(row, census.beehive);
        append(row, census.mud);
        append(row, census.calcite);
        append(row, census.lichen);
        append(row, census.sculk);
        append(row, census.ancientDebris);
        append(row, census.blackstone);
        append(row, census.basalt);
        append(row, census.nylium);
        append(row, census.netherGold);
        append(row, census.chain);
        append(row, String.join(";", oreBands));
        append(row, deepslate5);
        append(row, copper5);

        // Only for the chunks the question is about. A chunk with a real ore
        // distribution has copper everywhere and the bands say nothing; the ones
        // worth locating are those carrying a handful nobody can account for, and
        // above the cap the field is left empty rather than filled with noise.
        // The counts themselves cost nothing extra: they fall out of the palette
        // pass the section census already runs.
        append(row, census.copper <= 20 ? String.join(";", copperBands) : "");

        // Where that handful of copper sits inside the chunk.
        //
        // The theory is that it belongs to a feature in a neighbouring new chunk,
        // which world generation is allowed to spill one chunk past its own edge.
        // If that holds, an old chunk's copper is always within a few blocks of a
        // border and copper_interior is zero — and the radar's rule can become
        // "no copper in the middle" rather than "under ten copper", which would
        // take the ocean false positives with it. Only walked for the chunks the
        // question is about, so the cost is a handful of chunks in a flight.
        int copperEdge = 0;
        int copperInterior = 0;

        if (census.copper > 0 && census.copper <= 20) {
            for (int i = 0; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section == null || section.isEmpty()) continue;
                if (bottomY + (i << 4) < 0) continue;
                if (!section.hasAny(state -> COPPER.contains(state.getBlock()))) continue;

                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (!COPPER.contains(section.getBlockState(x, y, z).getBlock())) continue;
                            if (Math.min(x, 15 - x) <= 4 || Math.min(z, 15 - z) <= 4) copperEdge++;
                            else copperInterior++;
                        }
                    }
                }
            }
        }

        append(row, copperEdge);
        append(row, copperInterior);
        append(row, airBelow60);
        append(row, airBelowSea);
        append(row, blockEntities.values().stream().mapToInt(Integer::intValue).sum());

        StringBuilder types = new StringBuilder();
        for (Map.Entry<String, Integer> entry : blockEntities.entrySet()) {
            if (types.length() > 0) types.append(';');
            types.append(entry.getKey()).append('=').append(entry.getValue());
        }
        row.append(types);

        return row.toString();
    }

    /** Air in an empty section between y=0 and a ceiling, without walking it. */
    private static long emptyAir(int sectionBottom, int sectionTop, int ceiling) {
        int low = Math.max(0, sectionBottom);
        int high = Math.min(ceiling - 1, sectionTop);
        return high < low ? 0L : (long) (high - low + 1) * 256L;
    }

    /** Lowest world Y in this section holding anything other than air. */
    private int lowestSolidY(ChunkSection section, int sectionBottom) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (!section.getBlockState(x, y, z).isAir()) return sectionBottom + y;
                }
            }
        }

        return Integer.MAX_VALUE;
    }

    /** First non-water block under a water column, bounded so a deep ocean cannot run away with the tick. */
    private Block seaFloor(WorldChunk chunk, BlockPos.Mutable cursor, int x, int topY, int z, int bottomY) {
        int limit = Math.max(bottomY, topY - FLOOR_SCAN_DEPTH);

        for (int y = topY - 1; y >= limit; y--) {
            BlockState state = chunk.getBlockState(cursor.set(x, y, z));
            if (state.isOf(Blocks.WATER) || state.isAir()) continue;
            return state.getBlock();
        }

        return Blocks.AIR;
    }

    private String biomeName(ChunkSection section, int x, int y, int z) {
        return section.getBiome(x, y, z).getKey().map(key -> key.getValue().getPath()).orElse("unknown");
    }

    private static int flag(TreeSet<String> biomes, String name) {
        return biomes.contains(name) ? 1 : 0;
    }

    private static int anyOf(TreeSet<String> biomes, String... names) {
        for (String name : names) {
            if (biomes.contains(name)) return 1;
        }

        return 0;
    }

    private static String join(TreeSet<String> biomes) {
        return String.join(";", biomes);
    }

    /**
     * Ore blocks, resolved once.
     *
     * <p>The test used to walk the registry for every palette entry of every
     * section, which is a string allocation and a lookup a thousand times per
     * chunk for an answer that never changes.
     */
    private static final java.util.Set<Block> ORES = buildOres();

    private static java.util.Set<Block> buildOres() {
        java.util.Set<Block> ores = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        for (Block block : Registries.BLOCK) {
            if (Registries.BLOCK.getId(block).getPath().endsWith("_ore")) ores.add(block);
        }

        ores.add(Blocks.ANCIENT_DEBRIS);
        return ores;
    }

    private static boolean isOre(Block block) {
        return ORES.contains(block);
    }

    /** The two families that can push an old chunk over the line XaeroPlus scans from. */
    private static final java.util.Set<Block> DEEPSLATE = java.util.Set.of(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
    private static final java.util.Set<Block> COPPER =
        java.util.Set.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.RAW_COPPER_BLOCK);

    private static boolean isXaeroBlind(BlockState state) {
        Block block = state.getBlock();
        return DEEPSLATE.contains(block) || COPPER.contains(block);
    }

    private static void append(StringBuilder row, Object value) {
        row.append(value).append(',');
    }

    private static String fixed(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // Files

    private void writeRow(String line) {
        try {
            if (profileWriter == null || rowsInFile >= ROWS_PER_FILE) {
                closeProfileWriter();

                File file = new File(new File(MeteorClient.FOLDER, "hunterbuddy"),
                    "chunk-profile-" + LocalDate.now() + (fileIndex == 0 ? "" : "-" + fileIndex) + ".csv");
                file.getParentFile().mkdirs();
                boolean fresh = !file.exists() || file.length() == 0L;
                profileWriter = new BufferedWriter(new FileWriter(file, true));
                if (fresh) profileWriter.write(HEADER + System.lineSeparator());

                fileIndex++;
                rowsInFile = 0;
            }

            profileWriter.write(line);
            profileWriter.write(System.lineSeparator());
            rowsInFile++;

            if (++pendingFlush >= 50) {
                profileWriter.flush();
                pendingFlush = 0;
            }
        } catch (IOException e) {
            HunterBuddyAddon.LOG.error("ChunkProfiler: could not write the chunk profile", e);
            closeProfileWriter();
        }
    }

    private void writeLabel(String label, String source) {
        if (mc.player == null || mc.world == null) return;

        ChunkPos pos = mc.player.getChunkPos();
        Vec3d at = mc.player.getEntityPos();

        try {
            if (labelWriter == null) {
                File file = new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "chunk-labels.csv");
                file.getParentFile().mkdirs();
                retireOldLabelFile(file);
                boolean fresh = !file.exists() || file.length() == 0L;
                labelWriter = new BufferedWriter(new FileWriter(file, true));
                if (fresh) labelWriter.write("epoch_ms,dim,cx,cz,label,px,py,pz,source" + System.lineSeparator());
            }

            labelWriter.write(String.join(",",
                Long.toString(System.currentTimeMillis()),
                mc.world.getRegistryKey().getValue().toString(),
                Integer.toString(pos.x),
                Integer.toString(pos.z),
                label,
                fixed(at.x),
                fixed(at.y),
                fixed(at.z),
                source));
            labelWriter.write(System.lineSeparator());
            labelWriter.flush();

            mc.player.sendMessage(Text.literal("§7[§bChunkProfiler§7] §fMarked the chunk under you as "
                + label + " (" + source + ")"), false);
        } catch (IOException e) {
            HunterBuddyAddon.LOG.error("ChunkProfiler: could not write the label", e);
        }
    }

    /**
     * A labels file written before the {@code source} column is set aside under
     * a dated name rather than appended to, so its header never lies about the
     * rows beneath it.
     */
    private static void retireOldLabelFile(File file) throws IOException {
        if (!file.exists() || file.length() == 0L) return;

        String header;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            header = reader.readLine();
        }

        if (header != null && header.endsWith(",source")) return;

        File retired = new File(file.getParentFile(), "chunk-labels-" + System.currentTimeMillis() + ".csv");
        if (!file.renameTo(retired)) {
            throw new IOException("could not set aside " + file + " as " + retired);
        }
    }

    private void closeWriters() {
        closeProfileWriter();

        if (labelWriter != null) {
            try {
                labelWriter.close();
            } catch (IOException e) {
                HunterBuddyAddon.LOG.error("ChunkProfiler: could not close the labels file", e);
            }

            labelWriter = null;
        }
    }

    private void closeProfileWriter() {
        if (profileWriter == null) return;

        try {
            profileWriter.flush();
            profileWriter.close();
        } catch (IOException e) {
            HunterBuddyAddon.LOG.error("ChunkProfiler: could not close the profile file", e);
        }

        profileWriter = null;
        pendingFlush = 0;
    }

    /** Everything the network side can know without touching a block. */
    private static final class Pending {
        String dimension;
        int cx;
        int cz;
        long epochMs;
        long sincePrevMs;
        long waitMs;
        double px;
        double py;
        double pz;
        double distance;
        boolean xaeroOld;
        boolean xaeroNew;
        boolean xaeroSeen;
        boolean forced;
        boolean upgraded;
    }

    private static final class SectionTotals {
        int air;
        int solid;
        int ore;
        int copper;
    }

    /** Marker tallies for one chunk. */
    private static final class Census {
        int water;
        int kelp;
        int seagrass;
        int coral;
        int seaPickle;
        int bubble;
        int magma;
        int floorGravel;
        int floorSand;
        int floorClay;
        int floorDirt;
        int copper;
        int amethyst;
        int smoothBasalt;
        int tuff;
        int deepslate;
        int azalea;
        int dripleaf;
        int moss;
        int caveVines;
        int dripstone;
        int bamboo;
        int berry;
        int beehive;
        int mud;
        int calcite;
        int lichen;
        int sculk;
        int ancientDebris;
        int blackstone;
        int basalt;
        int nylium;
        int netherGold;
        int chain;

        private static final Map<Block, Integer> KEYS = buildKeys();

        void marker(Block block, int count) {
            Integer key = KEYS.get(block);
            if (key == null) return;

            switch (key) {
                case 0 -> copper += count;
                case 1 -> amethyst += count;
                case 2 -> smoothBasalt += count;
                case 3 -> tuff += count;
                case 4 -> deepslate += count;
                case 5 -> azalea += count;
                case 6 -> dripleaf += count;
                case 7 -> moss += count;
                case 8 -> caveVines += count;
                case 9 -> dripstone += count;
                case 10 -> bamboo += count;
                case 11 -> berry += count;
                case 12 -> beehive += count;
                case 13 -> mud += count;
                case 14 -> calcite += count;
                case 15 -> lichen += count;
                case 16 -> sculk += count;
                case 17 -> ancientDebris += count;
                case 18 -> blackstone += count;
                case 19 -> basalt += count;
                case 20 -> nylium += count;
                case 21 -> netherGold += count;
                case 22 -> chain += count;
                case 23 -> kelp += count;
                case 24 -> seagrass += count;
                case 25 -> coral += count;
                case 26 -> seaPickle += count;
                case 27 -> bubble += count;
                case 28 -> magma += count;
                default -> { }
            }
        }

        void floor(Block block) {
            if (block == Blocks.GRAVEL) floorGravel++;
            else if (block == Blocks.SAND) floorSand++;
            else if (block == Blocks.CLAY) floorClay++;
            else if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT) floorDirt++;
        }

        private static Map<Block, Integer> buildKeys() {
            Map<Block, Integer> keys = new LinkedHashMap<>();
            put(keys, 0, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.RAW_COPPER_BLOCK, Blocks.COPPER_BLOCK);
            put(keys, 1, Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.AMETHYST_CLUSTER);
            put(keys, 2, Blocks.SMOOTH_BASALT);
            put(keys, 3, Blocks.TUFF);
            put(keys, 4, Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
            put(keys, 5, Blocks.AZALEA, Blocks.FLOWERING_AZALEA, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES);
            put(keys, 6, Blocks.BIG_DRIPLEAF, Blocks.SMALL_DRIPLEAF);
            put(keys, 7, Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET);
            put(keys, 8, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT);
            put(keys, 9, Blocks.POINTED_DRIPSTONE, Blocks.DRIPSTONE_BLOCK);
            put(keys, 10, Blocks.BAMBOO, Blocks.BAMBOO_SAPLING);
            put(keys, 11, Blocks.SWEET_BERRY_BUSH);
            put(keys, 12, Blocks.BEE_NEST, Blocks.BEEHIVE);
            put(keys, 13, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.PACKED_MUD);
            put(keys, 14, Blocks.CALCITE);
            put(keys, 15, Blocks.GLOW_LICHEN);
            put(keys, 16, Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_CATALYST, Blocks.SCULK_SENSOR, Blocks.SCULK_SHRIEKER);
            put(keys, 17, Blocks.ANCIENT_DEBRIS);
            put(keys, 18, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.GILDED_BLACKSTONE);
            put(keys, 19, Blocks.BASALT, Blocks.POLISHED_BASALT);
            put(keys, 20, Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM);
            put(keys, 21, Blocks.NETHER_GOLD_ORE);
            // Renamed to iron_chain in 1.21.11 when the copper family arrived; the
            // 1.21.4 server still sends the same block, the client just calls it that.
            put(keys, 22, Blocks.IRON_CHAIN);
            put(keys, 23, Blocks.KELP, Blocks.KELP_PLANT);
            put(keys, 24, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS);
            put(keys, 25, Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK,
                Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK, Blocks.DEAD_TUBE_CORAL_BLOCK,
                Blocks.DEAD_BRAIN_CORAL_BLOCK, Blocks.DEAD_BUBBLE_CORAL_BLOCK, Blocks.DEAD_FIRE_CORAL_BLOCK,
                Blocks.DEAD_HORN_CORAL_BLOCK);
            put(keys, 26, Blocks.SEA_PICKLE);
            put(keys, 27, Blocks.BUBBLE_COLUMN);
            put(keys, 28, Blocks.MAGMA_BLOCK);
            return keys;
        }

        private static void put(Map<Block, Integer> keys, int id, Block... blocks) {
            for (Block block : blocks) keys.put(block, id);
        }
    }
}
