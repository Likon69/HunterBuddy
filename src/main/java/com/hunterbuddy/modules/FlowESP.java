package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Detects and renders lava / water flows in loaded chunks.
 *
 * <p>Full port of mlep's {@code FlowESP}. Includes:
 * <ul>
 *   <li>Per-chunk water/lava flow scan with column detection</li>
 *   <li>Per-column color picking based on flow length and source ratio</li>
 *   <li>Roof detection (lava at high Y gets a dedicated color)</li>
 *   <li>Disk-backed cache (GZIP serialised, configurable save interval)</li>
 *   <li>Gradient mode (fresh → mature per column, optional)</li>
 *   <li>Fade-in / fade-out animation when flows load/unload</li>
 *   <li>Distance and alpha fades</li>
 * </ul>
 *
 * <p>Held-over from the previous slim port: async background scan via a
 * 2-thread daemon executor at priority 1 (mlep scans synchronously, which
 * freezes when many chunks load at once) and a {@code onBlockUpdate}
 * handler that re-queues the chunk when a fluid block transitions (mlep
 * only re-scans on chunk reload, so flowing water is invisible until you
 * leave and come back).
 */
public class FlowESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNether = settings.createGroup("Nether");
    private final SettingGroup sgOverworld = settings.createGroup("Overworld");
    private final SettingGroup sgCache = settings.createGroup("Cache");
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final SettingGroup sgAnim = settings.createGroup("Animation");

    // -- General --
    private final Setting<SettingColor> lavaColor = sgGeneral.add(new ColorSetting.Builder()
        .name("lava-color").description("Base color for lava (variations auto-generated).")
        .defaultValue(new SettingColor(0, 48, 255, 28)).build()
    );

    private final Setting<SettingColor> waterColor = sgGeneral.add(new ColorSetting.Builder()
        .name("water-color").description("Base color for water (variations auto-generated).")
        .defaultValue(new SettingColor(0, 150, 255, 32)).build()
    );

    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder()
        .name("render-distance").description("Max render distance in blocks.")
        .defaultValue(400).sliderRange(32, 320).build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").description("Show cache stats.")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> gradientMode = sgGeneral.add(new BoolSetting.Builder()
        .name("gradient-mode")
        .description("Use gradient colors based on per-column flow maturity.")
        .defaultValue(true).build()
    );

    private final Setting<SettingColor> lavaFreshColor = sgGeneral.add(new ColorSetting.Builder()
        .name("lava-fresh-color")
        .description("Color for fresh lava flows (recently loaded, short span).")
        .defaultValue(new SettingColor(255, 120, 0, 140))
        .visible(gradientMode::get).build()
    );

    private final Setting<SettingColor> lavaMatureColor = sgGeneral.add(new ColorSetting.Builder()
        .name("lava-mature-color")
        .description("Color for mature lava flows (loaded longer, longer span).")
        .defaultValue(new SettingColor(180, 20, 0, 140))
        .visible(gradientMode::get).build()
    );

    private final Setting<SettingColor> waterFreshColor = sgGeneral.add(new ColorSetting.Builder()
        .name("water-fresh-color")
        .description("Color for fresh water flows (recently loaded, short span).")
        .defaultValue(new SettingColor(0, 180, 255, 120))
        .visible(gradientMode::get).build()
    );

    private final Setting<SettingColor> waterMatureColor = sgGeneral.add(new ColorSetting.Builder()
        .name("water-mature-color")
        .description("Color for mature water flows (loaded longer, longer span).")
        .defaultValue(new SettingColor(0, 40, 150, 120))
        .visible(gradientMode::get).build()
    );

    private final Setting<GradientSource> gradientSource = sgGeneral.add(new EnumSetting.Builder<GradientSource>()
        .name("gradient-source")
        .description("What the gradient represents. FlowLength = blocks flowed from source; ChunkAge = time since first seen; Both = blend.")
        .defaultValue(GradientSource.FlowLength)
        .visible(gradientMode::get).build()
    );

    private final Setting<Integer> maxFlowLength = sgGeneral.add(new IntSetting.Builder()
        .name("max-flow-length")
        .description("Number of blocks a fluid has flowed from its source (0 = source only) at which the column reaches the mature color.")
        .defaultValue(10).min(1).sliderRange(1, 64).build()
    );

    private final Setting<Integer> maxAgeSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("max-age-seconds")
        .description("Chunk age (seconds since first seen) at which the age gradient reaches full maturity.")
        .defaultValue(600).min(5).sliderRange(30, 3600)
        .visible(() -> gradientMode.get() && gradientSource.get() != GradientSource.FlowLength).build()
    );

    private final Setting<Double> alphaFade = sgGeneral.add(new DoubleSetting.Builder()
        .name("alpha-fade")
        .description("How much alpha decreases with flow maturity (0 = no fade, 1 = full fade to transparent).")
        .defaultValue(0.7).min(0.0).max(1.0).sliderRange(0.0, 1.0).build()
    );

    private final Setting<Double> distanceFade = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance-fade")
        .description("How much alpha decreases with distance from player (0 = no fade, 1 = invisible at max range).")
        .defaultValue(0.3).min(0.0).max(1.0).sliderRange(0.0, 1.0).build()
    );

    // -- Nether --
    private final Setting<Boolean> netherEnabled = sgNether.add(new BoolSetting.Builder()
        .name("enabled").description("Detect lava in nether.")
        .defaultValue(true).build()
    );

    private final Setting<Integer> netherMinY = sgNether.add(new IntSetting.Builder()
        .name("min-y").description("Skip lava ocean below this.")
        .defaultValue(32).sliderRange(0, 127).build()
    );

    private final Setting<Integer> roofY = sgNether.add(new IntSetting.Builder()
        .name("roof-y").description("Y level for roof detection.")
        .defaultValue(120).sliderRange(100, 127).build()
    );

    // -- Overworld --
    private final Setting<Boolean> overworldEnabled = sgOverworld.add(new BoolSetting.Builder()
        .name("enabled").description("Detect water in overworld.")
        .defaultValue(true).build()
    );

    private final Setting<Integer> overworldMinY = sgOverworld.add(new IntSetting.Builder()
        .name("min-y").description("Minimum Y to scan.")
        .defaultValue(0).sliderRange(-64, 128).build()
    );

    private final Setting<Integer> overworldMaxY = sgOverworld.add(new IntSetting.Builder()
        .name("max-y").description("Skip surface ocean above this.")
        .defaultValue(62).sliderRange(0, 320).build()
    );

    private final Setting<Boolean> detectLavaOverworld = sgOverworld.add(new BoolSetting.Builder()
        .name("detect-lava").description("Also detect lava.")
        .defaultValue(true).build()
    );

    // -- Cache --
    private final Setting<Integer> maxCachedChunks = sgCache.add(new IntSetting.Builder()
        .name("max-chunks").description("Max chunks in memory.")
        .defaultValue(2000).sliderRange(500, 10000).build()
    );

    private final Setting<Integer> evictDistance = sgCache.add(new IntSetting.Builder()
        .name("evict-distance").description("Remove chunks beyond this (in chunks).")
        .defaultValue(32).sliderRange(16, 64).build()
    );

    private final Setting<Integer> saveInterval = sgCache.add(new IntSetting.Builder()
        .name("save-interval").description("Auto-save seconds (0 = off).")
        .defaultValue(300).sliderRange(0, 600).build()
    );

    private final Setting<Boolean> persistCache = sgCache.add(new BoolSetting.Builder()
        .name("persist").description("Save to disk.")
        .defaultValue(true).build()
    );

    // -- Filters --
    private final Setting<Double> minFlowRatio = sgFilters.add(new DoubleSetting.Builder()
        .name("min-flow-ratio").description("Minimum flowing/source ratio.")
        .defaultValue(0.5).sliderRange(0.0, 5.0).build()
    );

    private final Setting<Integer> minSize = sgFilters.add(new IntSetting.Builder()
        .name("min-size").description("Minimum blocks to show.")
        .defaultValue(3).sliderRange(1, 50).build()
    );

    private final Setting<Integer> maxSize = sgFilters.add(new IntSetting.Builder()
        .name("max-size").description("Skip oceans/lakes above this.")
        .defaultValue(500).sliderRange(50, 5000).build()
    );

    // -- Animation --
    private final Setting<Boolean> fadeLoad = sgAnim.add(new BoolSetting.Builder()
        .name("fade-load")
        .description("Smoothly fade flows in when they load into render range and out when they leave it.")
        .defaultValue(true).build()
    );

    private final Setting<Double> fadeDuration = sgAnim.add(new DoubleSetting.Builder()
        .name("fade-duration").description("How long the load/unload fade takes, in seconds.")
        .defaultValue(0.4).min(0.0).sliderRange(0.0, 2.0)
        .visible(fadeLoad::get).build()
    );

    // -- Runtime state --
    // LinkedHashMap with accessOrder=false (insertion order) and removeEldestEntry
    // gives us a FIFO eviction with a hard upper bound, without the AIOOBE bug
    // that mlep's accessOrder=true triggers on values().toArray(). Synchronized
    // externally on the LinkedHashMap for cross-thread access.
    private LRUCache overworldCache;
    private LRUCache netherCache;
    private Set<Long> processing;
    private Set<Long> scannedOverworldChunks;
    private Set<Long> scannedNetherChunks;
    private ExecutorService saveThread;
    private ScheduledExecutorService autoSave;
    private volatile boolean dirty;
    private final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
    private ExecutorService scanExecutor;
    private int tick;
    private final Map<Long, RenderState> renderStates = new HashMap<>();
    private long lastRenderNanos;
    private Object lastRenderDim;

    private static final Path CACHE_DIR = Path.of("meteor-client", "flow-esp-cache");

    // Pre-computed colors (computed in computeColors() and refreshed every 100 ticks).
    private Color lavaSource;
    private Color lavaFlow;
    private Color lavaAged;
    private Color lavaRoof;
    private Color waterSource;
    private Color waterFlow;
    private Color waterAged;

    public FlowESP() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "flow-esp",
            "Chunk activity detector via fluid spread analysis.");
    }

    private LRUCache getCache() {
        return (mc.world != null && mc.world.getRegistryKey() == World.NETHER) ? netherCache : overworldCache;
    }

    @Override
    public void onActivate() {
        int max = maxCachedChunks.get();
        overworldCache = new LRUCache(max);
        netherCache = new LRUCache(max);
        processing = ConcurrentHashMap.newKeySet();
        scannedOverworldChunks = ConcurrentHashMap.newKeySet();
        scannedNetherChunks = ConcurrentHashMap.newKeySet();
        pendingChunks.clear();
        dirty = false;
        tick = 0;
        renderStates.clear();
        lastRenderNanos = 0L;
        computeColors();

        scanExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "FlowESP-Scan");
            t.setDaemon(true);
            t.setPriority(1);
            return t;
        });

        saveThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FlowESP-IO");
            t.setDaemon(true);
            return t;
        });

        if (persistCache.get()) loadCache();

        int interval = saveInterval.get();
        if (interval > 0 && persistCache.get()) {
            autoSave = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FlowESP-AutoSave");
                t.setDaemon(true);
                return t;
            });
            autoSave.scheduleAtFixedRate(this::saveCache, interval, interval, TimeUnit.SECONDS);
        }

        if (debug.get()) {
            info("FlowESP active - OW: " + overworldCache.size() + ", Nether: " + netherCache.size());
        }
    }

    @Override
    public void onDeactivate() {
        if (persistCache.get() && dirty) saveCache();

        if (saveThread != null) saveThread.shutdownNow();
        if (autoSave != null) autoSave.shutdownNow();
        if (scanExecutor != null) { scanExecutor.shutdownNow(); scanExecutor = null; }

        if (overworldCache != null) { synchronized (overworldCache) { overworldCache.clear(); } }
        if (netherCache != null) { synchronized (netherCache) { netherCache.clear(); } }

        if (processing != null) processing.clear();
        if (scannedOverworldChunks != null) scannedOverworldChunks.clear();
        if (scannedNetherChunks != null) scannedNetherChunks.clear();
        pendingChunks.clear();

        renderStates.clear();
    }

    private void computeColors() {
        SettingColor lc = lavaColor.get();
        SettingColor wc = waterColor.get();
        lavaSource = new Color(lc.r, lc.g, lc.b, lc.a);
        lavaFlow = darken(lc, 0.7f);
        lavaAged = brighten(lc, 1.3f);
        lavaRoof = brighten(lc, 1.5f);
        waterSource = new Color(wc.r, wc.g, wc.b, wc.a);
        waterFlow = darken(wc, 0.7f);
        waterAged = brighten(wc, 1.3f);
    }

    private Color darken(SettingColor c, float factor) {
        return new Color((int) (c.r * factor), (int) (c.g * factor), (int) (c.b * factor), c.a);
    }

    private Color brighten(SettingColor c, float factor) {
        return new Color(
            Math.min(255, (int) (c.r * factor)),
            Math.min(255, (int) (c.g * factor)),
            Math.min(255, (int) (c.b * factor)),
            Math.min(255, (int) (c.a * 1.2f))
        );
    }

    private Color lerpColor(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
            (int) (a.r + (b.r - a.r) * t),
            (int) (a.g + (b.g - a.g) * t),
            (int) (a.b + (b.b - a.b) * t),
            (int) (a.a + (b.a - a.a) * t)
        );
    }

    private Color withAlpha(Color c, float mul) {
        int a = Math.max(0, Math.min(255, Math.round(c.a * mul)));
        return new Color(c.r, c.g, c.b, a);
    }

    private Path cacheFile(boolean nether) {
        return CACHE_DIR.resolve(nether ? "nether.dat" : "overworld.dat");
    }

    private void loadCache() {
        saveThread.submit(() -> {
            try {
                Files.createDirectories(CACHE_DIR);
                loadFile(cacheFile(false), overworldCache);
                loadFile(cacheFile(true), netherCache);
                if (debug.get()) info("Cache loaded");
            } catch (Exception e) {
                if (debug.get()) error("Load failed: " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void loadFile(Path f, LRUCache cache) {
        if (Files.exists(f)) {
            boolean isNether = f.getFileName().toString().contains("nether");
            Set<Long> scannedSet = isNether ? scannedNetherChunks : scannedOverworldChunks;
            try (InputStream fis = Files.newInputStream(f);
                 GZIPInputStream gis = new GZIPInputStream(fis);
                 ObjectInputStream ois = new ObjectInputStream(gis)) {
                int n = ois.readInt();
                long now = System.currentTimeMillis();
                for (int i = 0; i < n; i++) {
                    ChunkData d = (ChunkData) ois.readObject();
                    if (d.firstSeen <= 0L) d.firstSeen = now;
                    synchronized (cache) { cache.put(d.key(), d); }
                    scannedSet.add(d.key());
                }
            } catch (Exception ignored) { /* corrupt or missing cache — start fresh */ }
        }
    }

    private void saveCache() {
        if (dirty) {
            dirty = false;
            saveThread.submit(() -> {
                try {
                    Files.createDirectories(CACHE_DIR);
                    saveFile(cacheFile(false), overworldCache);
                    saveFile(cacheFile(true), netherCache);
                    if (debug.get()) info("Cache saved");
                } catch (Exception e) {
                    if (debug.get()) error("Save failed: " + e.getMessage());
                }
            });
        }
    }

    private void saveFile(Path f, LRUCache cache) {
        try (OutputStream fos = Files.newOutputStream(f);
             GZIPOutputStream gos = new GZIPOutputStream(fos);
             ObjectOutputStream oos = new ObjectOutputStream(gos)) {
            synchronized (cache) {
                oos.writeInt(cache.size());
                for (ChunkData d : cache.values()) oos.writeObject(d);
            }
        } catch (Exception ignored) { /* IO error — non-fatal */ }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null || mc.world.getRegistryKey() == World.END) return;
        Chunk chunk = event.chunk();
        long key = chunk.getPos().toLong();
        boolean nether = mc.world.getRegistryKey() == World.NETHER;
        if (nether && scannedNetherChunks.contains(key)) return;
        if (!nether && scannedOverworldChunks.contains(key)) return;

        // Queue for the background executor. The actual scan happens in
        // processPendingChunks() during onTick. Direct submit would fill
        // the executor queue when many chunks load at once.
        pendingChunks.add(key);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        // Lava/water flow generates BlockUpdateEvent for every block that
        // changes. Without re-queueing here, the chunk stays "scanned" and
        // the player's view of flowing fluids freezes the instant a block
        // transitions. invalidate + queue. (Improvement over mlep which
        // only re-scans on chunk reload.)
        if (mc.world == null || mc.world.getRegistryKey() == World.END) return;
        long key = ChunkPos.toLong(event.pos.getX() >> 4, event.pos.getZ() >> 4);
        scannedNetherChunks.remove(key);
        scannedOverworldChunks.remove(key);
        pendingChunks.add(key);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        processPendingChunks();

        tick++;
        if (tick % 100 == 0) computeColors();
        if (tick >= 200) {
            tick = 0;
            evictDistant();
        }
    }

    private void processPendingChunks() {
        if (pendingChunks.isEmpty() || scanExecutor == null || scanExecutor.isShutdown()) return;
        if (mc.player == null) return;

        // Sort closest-first (matches mlep's onChunkData orderering by
        // distance and uses long arithmetic to avoid overflow at world
        // border).
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        List<Long> queued = new ArrayList<>(pendingChunks);
        queued.sort(Comparator.comparingLong(key -> {
            int dx = ChunkPos.getPackedX(key) - pcx;
            int dz = ChunkPos.getPackedZ(key) - pcz;
            return (long) dx * dx + (long) dz * dz;
        }));

        int drained = 0;
        for (long key : queued) {
            if (drained >= 4) break;
            if (!pendingChunks.remove(key)) continue;

            boolean nether = mc.world.getRegistryKey() == World.NETHER;
            LRUCache cache = getCache();
            Set<Long> scannedSet = nether ? scannedNetherChunks : scannedOverworldChunks;
            if (scannedSet.contains(key)) continue;
            if (!processing.add(key)) continue;

            // Use ChunkPos.getPackedX/Z which are signed (mlep convention)
            // to avoid the unsigned-wrap bug at world-border chunks.
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (!mc.world.isChunkLoaded(cx, cz)) { processing.remove(key); continue; }

            Chunk chunk = mc.world.getChunk(cx, cz);
            scannedSet.add(key);
            drained++;
            scanExecutor.submit(() -> {
                try {
                    ChunkData data = scanChunk(chunk, nether);
                    if (data != null && shouldCache(data)) {
                        synchronized (cache) { cache.put(key, data); }
                        dirty = true;
                    }
                } finally {
                    processing.remove(key);
                }
            });
        }
    }

    private ChunkData scanChunk(Chunk chunk, boolean nether) {
        int minY, maxY;
        if (nether) {
            if (!netherEnabled.get()) return null;
            minY = netherMinY.get();
            maxY = 128;
        } else {
            if (!overworldEnabled.get()) return null;
            minY = overworldMinY.get();
            maxY = overworldMaxY.get();
        }

        int roofLevel = roofY.get();
        List<FlowColumn> columns = new ArrayList<>();
        int srcTotal = 0;
        int flowTotal = 0;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunk.getPos().getStartX() + lx;
                int wz = chunk.getPos().getStartZ() + lz;
                int colBottom = -1, colTop = -1;
                boolean colLava = false, colHasSource = false, colRoof = false;
                int colSrc = 0, colFlow = 0;

                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(wx, y, wz);
                    BlockState state = chunk.getBlockState(pos);
                    boolean isLava = state.getBlock() == Blocks.LAVA;
                    boolean isWater = state.getBlock() == Blocks.WATER;

                    if (!isLava && !isWater) {
                        // End of column
                        if (colBottom != -1 && colFlow > 0 && (!colRoof || colFlow > 0)) {
                            columns.add(new FlowColumn(lx, lz, colBottom, colTop, colFlow, colLava, colHasSource, colRoof));
                            srcTotal += colSrc;
                            flowTotal += colFlow;
                        }
                        colBottom = -1; colTop = -1; colSrc = 0; colFlow = 0;
                        colHasSource = false; colRoof = false;
                    } else if ((!nether || !isWater)
                        && (nether || !isWater || overworldEnabled.get())
                        && (nether || !isLava || detectLavaOverworld.get())) {
                        if (isWater) {
                            FluidState fs = state.getFluidState();
                            if (fs.isStill() && countAdjSources(chunk, pos) >= 3) continue;
                        }

                        FluidState fs = state.getFluidState();
                        boolean isSrc = fs.isStill();
                        boolean isRoofLevel = nether && isLava && y >= roofLevel;
                        if (colBottom == -1) {
                            colBottom = y;
                            colLava = isLava;
                        }
                        colTop = y;
                        if (isSrc) { colSrc++; colHasSource = true; } else colFlow++;
                        if (isRoofLevel) colRoof = true;
                    }
                }

                if (colBottom != -1 && colFlow > 0 && (!colRoof || colFlow > 0)) {
                    columns.add(new FlowColumn(lx, lz, colBottom, colTop, colFlow, colLava, colHasSource, colRoof));
                    srcTotal += colSrc;
                    flowTotal += colFlow;
                }
            }
        }

        return columns.isEmpty() ? null : new ChunkData(chunk.getPos().x, chunk.getPos().z, srcTotal, flowTotal, columns);
    }

    private int countAdjSources(Chunk chunk, BlockPos pos) {
        int count = 0;
        int[][] off = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : off) {
            BlockPos n = pos.add(o[0], 0, o[1]);
            int lx = n.getX() - chunk.getPos().getStartX();
            int lz = n.getZ() - chunk.getPos().getStartZ();
            if (lx >= 0 && lx <= 15 && lz >= 0 && lz <= 15) {
                BlockState s = chunk.getBlockState(n);
                if (s.getBlock() == Blocks.WATER && s.getFluidState().isStill()) count++;
            }
        }
        return count;
    }

    private boolean shouldCache(ChunkData d) {
        if (d.columns.length == 0) return false;
        if (d.total() >= minSize.get() && d.total() <= maxSize.get()) {
            for (FlowColumn c : d.columns) if (c.isRoof()) return true;
            return d.flowRatio() >= minFlowRatio.get();
        }
        return false;
    }

    private void evictDistant() {
        if (mc.player == null) return;
        int px = mc.player.getChunkPos().x;
        int pz = mc.player.getChunkPos().z;
        int dist = evictDistance.get();
        int distSq = dist * dist;
        LRUCache cache = getCache();
        List<Long> remove = new ArrayList<>();
        synchronized (cache) {
            for (Entry<Long, ChunkData> e : cache.entrySet()) {
                ChunkData d = e.getValue();
                int dx = d.chunkX - px;
                int dz = d.chunkZ - pz;
                if (dx * dx + dz * dz > distSq) remove.add(e.getKey());
            }
            remove.forEach(cache::remove);
        }
        if (debug.get() && !remove.isEmpty()) info("Evicted " + remove.size() + " chunks");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || mc.world.getRegistryKey() == World.END) return;
        Object dim = mc.world.getRegistryKey();
        if (!dim.equals(lastRenderDim)) {
            renderStates.clear();
            lastRenderNanos = 0L;
            lastRenderDim = dim;
        }

        LRUCache cache = getCache();
        // getPos() is the Yarn 1.21.1 equivalent of mlep's getEntityPos()
        // (renamed somewhere between Yarn 1.21.4 and 1.21.1 mappings).
        Vec3d pp = mc.player.getPos();
        int rdist = renderDistance.get();
        int rdistSq = rdist * rdist;
        ChunkData[] snapshot;
        synchronized (cache) {
            snapshot = cache.values().toArray(new ChunkData[0]);
        }

        boolean fade = fadeLoad.get();
        double dur = fadeDuration.get();
        long nowNanos = System.nanoTime();
        float dt = lastRenderNanos == 0L ? 0f : (float) (nowNanos - lastRenderNanos) / 1e9f;
        lastRenderNanos = nowNanos;
        dt = Math.max(0f, Math.min(dt, 0.1f));
        float step = fade && dur > 0.0 ? (float) (dt / dur) : 1f;
        Set<Long> inRange = new HashSet<>();

        for (ChunkData data : snapshot) {
            double cx = data.chunkX * 16 + 8;
            double cz = data.chunkZ * 16 + 8;
            double dSq = (pp.x - cx) * (pp.x - cx) + (pp.z - cz) * (pp.z - cz);
            if (!(dSq > rdistSq)) {
                long key = data.key();
                inRange.add(key);
                RenderState st = renderStates.get(key);
                if (st == null) {
                    renderStates.put(key, new RenderState(data, fade ? 0f : 1f));
                } else {
                    st.data = data;
                }
            }
        }

        long nowMs = System.currentTimeMillis();
        Iterator<Entry<Long, RenderState>> it = renderStates.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Long, RenderState> e = it.next();
            RenderState st = e.getValue();
            boolean active = inRange.contains(e.getKey());
            float target = active ? 1f : 0f;
            if (st.alpha < target) st.alpha = Math.min(target, st.alpha + step);
            else if (st.alpha > target) st.alpha = Math.max(target, st.alpha - step);
            if (!active && st.alpha <= 0.001f) it.remove();
            else if (!(st.alpha <= 0.001f)) renderChunk(event, st.data, pp, rdistSq, st.alpha, nowMs);
        }
    }

    private void renderChunk(Render3DEvent event, ChunkData data, Vec3d pp, int rdistSq, float animAlpha, long nowMs) {
        int baseX = data.chunkX * 16;
        int baseZ = data.chunkZ * 16;
        long age = data.firstSeen > 0L ? Math.max(0L, nowMs - data.firstSeen) : 0L;
        float chunkAgeFactor = Math.min(1f, (float) age / (maxAgeSeconds.get() * 1000f));

        for (FlowColumn col : data.columns) {
            int wx = baseX + col.x;
            int wz = baseZ + col.z;
            double bDistSq = pp.squaredDistanceTo(wx + 0.5, col.bottomY, wz + 0.5);
            if (!(bDistSq > rdistSq)) {
                float distRatio = (float) (bDistSq / rdistSq);
                if (gradientMode.get()) renderGradientColumn(event, col, wx, wz, chunkAgeFactor, animAlpha, distRatio);
                else {
                    float lengthFactor = Math.min(1f, (float) col.flowLen / maxFlowLength.get());
                    float alphaMul = 1f - lengthFactor * (float) (double) alphaFade.get();
                    float distMul = 1f - distRatio * (float) (double) distanceFade.get();
                    Color color = withAlpha(pickColor(col, data.flowRatio()),
                        animAlpha * Math.max(0.02f, alphaMul) * Math.max(0.05f, distMul));
                    event.renderer.box(wx, col.bottomY, wz, wx + 1, col.topY + 1, wz + 1, color, color, ShapeMode.Both, 0);
                }
            }
        }
    }

    private void renderGradientColumn(Render3DEvent event, FlowColumn col, int wx, int wz,
                                      float chunkAgeFactor, float animAlpha, float distRatio) {
        int span = col.topY - col.bottomY + 1;
        float lengthFactor = Math.min(1f, (float) col.flowLen / maxFlowLength.get());

        float maturity = switch (gradientSource.get()) {
            case FlowLength -> lengthFactor;
            case ChunkAge -> chunkAgeFactor;
            case Both -> lengthFactor * 0.6f + chunkAgeFactor * 0.4f;
        };
        SettingColor freshC = col.isLava() ? lavaFreshColor.get() : waterFreshColor.get();
        SettingColor matureC = col.isLava() ? lavaMatureColor.get() : waterMatureColor.get();
        Color freshColor = new Color(freshC.r, freshC.g, freshC.b, freshC.a);
        Color matureColor = new Color(matureC.r, matureC.g, matureC.b, matureC.a);
        float alphaMul = 1f - maturity * (float) (double) alphaFade.get();
        float distMul = 1f - distRatio * (float) (double) distanceFade.get();
        float totalAlpha = animAlpha * Math.max(0.02f, alphaMul) * Math.max(0.05f, distMul);
        float gradientStrength = Math.min(0.15f, span * 0.005f);
        float topMaturity = Math.max(0f, maturity - gradientStrength);
        float bottomMaturity = Math.min(1f, maturity + gradientStrength);
        Color topColor = withAlpha(lerpColor(freshColor, matureColor, topMaturity), totalAlpha);
        Color bottomColor = withAlpha(lerpColor(freshColor, matureColor, bottomMaturity), totalAlpha);
        double x1 = wx, x2 = wx + 1, y1 = col.bottomY, y2 = col.topY + 1, z1 = wz, z2 = wz + 1;
        event.renderer.gradientQuadVertical(x1, y1, z1, x2, y2, z1, topColor, bottomColor);
        event.renderer.gradientQuadVertical(x1, y1, z2, x2, y2, z2, topColor, bottomColor);
        event.renderer.gradientQuadVertical(x1, y1, z1, x1, y2, z2, topColor, bottomColor);
        event.renderer.gradientQuadVertical(x2, y1, z1, x2, y2, z2, topColor, bottomColor);
        event.renderer.quadHorizontal(x1, y2, z1, x2, z2, topColor);
        event.renderer.quadHorizontal(x1, y1, z1, x2, z2, bottomColor);
        event.renderer.line(x1, y1, z1, x1, y2, z1, bottomColor, topColor);
        event.renderer.line(x2, y1, z1, x2, y2, z1, bottomColor, topColor);
        event.renderer.line(x1, y1, z2, x1, y2, z2, bottomColor, topColor);
        event.renderer.line(x2, y1, z2, x2, y2, z2, bottomColor, topColor);
        event.renderer.line(x1, y1, z1, x2, y1, z1, bottomColor, bottomColor);
        event.renderer.line(x1, y1, z2, x2, y1, z2, bottomColor, bottomColor);
        event.renderer.line(x1, y2, z1, x2, y2, z1, topColor, topColor);
        event.renderer.line(x1, y2, z2, x2, y2, z2, topColor, topColor);
        event.renderer.line(x1, y1, z1, x1, y1, z2, bottomColor, bottomColor);
        event.renderer.line(x2, y1, z1, x2, y1, z2, bottomColor, bottomColor);
        event.renderer.line(x1, y2, z1, x1, y2, z2, topColor, topColor);
        event.renderer.line(x2, y2, z1, x2, y2, z2, topColor, topColor);
    }

    private Color pickColor(FlowColumn col, double ratio) {
        if (col.isLava()) {
            if (col.isRoof()) return lavaRoof;
            if (ratio > 2.0) return lavaAged;
            return col.hasSource() ? lavaSource : lavaFlow;
        }
        if (ratio > 2.0) return waterAged;
        return col.hasSource() ? waterSource : waterFlow;
    }

    public void clearCache() {
        if (overworldCache != null) { synchronized (overworldCache) { overworldCache.clear(); } }
        if (netherCache != null) { synchronized (netherCache) { netherCache.clear(); } }
        dirty = true;
        info("Cache cleared");
    }

    public void forceSave() {
        dirty = true;
        saveCache();
    }

    public String stats() {
        int ow = overworldCache != null ? overworldCache.size() : 0;
        int n = netherCache != null ? netherCache.size() : 0;
        return "OW: " + ow + " | Nether: " + n;
    }

    public enum GradientSource {
        FlowLength, ChunkAge, Both
    }

    private static class ChunkData implements Serializable {
        private static final long serialVersionUID = 1L;
        final int chunkX;
        final int chunkZ;
        final short sourceCount;
        final short flowCount;
        final FlowColumn[] columns;
        long firstSeen;

        ChunkData(int cx, int cz, int src, int flow, List<FlowColumn> cols) {
            this.chunkX = cx;
            this.chunkZ = cz;
            this.sourceCount = (short) Math.min(src, 32767);
            this.flowCount = (short) Math.min(flow, 32767);
            this.columns = cols.toArray(new FlowColumn[0]);
            this.firstSeen = System.currentTimeMillis();
        }

        double flowRatio() {
            return (double) flowCount / (sourceCount + 1);
        }

        int total() {
            return sourceCount + flowCount;
        }

        long key() {
            return ChunkPos.toLong(chunkX, chunkZ);
        }
    }

    private static class FlowColumn implements Serializable {
        private static final long serialVersionUID = 1L;
        final short x, z, bottomY, topY, flowLen;
        final byte flags;

        FlowColumn(int x, int z, int bottomY, int topY, int flowLen,
                   boolean isLava, boolean hasSource, boolean isRoof) {
            this.x = (short) x;
            this.z = (short) z;
            this.bottomY = (short) bottomY;
            this.topY = (short) topY;
            this.flowLen = (short) flowLen;
            this.flags = (byte) ((isLava ? 1 : 0) | (hasSource ? 2 : 0) | (isRoof ? 4 : 0));
        }

        boolean isLava() { return (flags & 1) != 0; }
        boolean hasSource() { return (flags & 2) != 0; }
        boolean isRoof() { return (flags & 4) != 0; }
    }

    private static class RenderState {
        ChunkData data;
        float alpha;

        RenderState(ChunkData data, float alpha) {
            this.data = data;
            this.alpha = alpha;
        }
    }

    /**
     * Bounded cache. {@code accessOrder=false} avoids the AIOOBE bug that
     * mlep's accessOrder=true triggers when iterating {@code values().toArray()}
     * (the JDK's {@code afterNodeAccess} re-links entries mid-iteration).
     * External synchronisation is required for cross-thread access.
     */
    private static class LRUCache extends LinkedHashMap<Long, ChunkData> {
        private final int max;

        LRUCache(int max) {
            super(max / 4, 0.75f, false);
            this.max = max;
        }

        @Override
        protected boolean removeEldestEntry(Entry<Long, ChunkData> e) {
            return size() > max;
        }
    }
}
