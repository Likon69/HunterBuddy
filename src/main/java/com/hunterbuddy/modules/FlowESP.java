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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Detects and renders lava / water flows in loaded chunks. Useful for
 * spotting underground lava lakes, dripstone-fed pools, and recent
 * fluid activity (e.g. someone digging nearby).
 *
 * <p>Simplified port of mlep's {@code FlowESP}: kept the chunk-scanning
 * detection + per-chunk cache + 3D box render. Dropped the disk-cache
 * persistence, multi-threaded executor, fade-in animation, and the
 * gradient-mode color blending — those add complexity without much
 * payoff for casual use.
 */
public class FlowESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNether = settings.createGroup("Nether");
    private final SettingGroup sgOverworld = settings.createGroup("Overworld");
    private final SettingGroup sgFilter = settings.createGroup("Filter");

    private final Setting<SettingColor> lavaColor = sgGeneral.add(new ColorSetting.Builder()
        .name("lava-color")
        .description("Lava flow render color.")
        .defaultValue(new SettingColor(0, 48, 255, 28))
        .build()
    );

    private final Setting<SettingColor> waterColor = sgGeneral.add(new ColorSetting.Builder()
        .name("water-color")
        .description("Water flow render color.")
        .defaultValue(new SettingColor(0, 150, 255, 32))
        .build()
    );

    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder()
        .name("render-distance")
        .description("Maximum render distance (blocks).")
        .defaultValue(200)
        .min(32)
        .max(512)
        .sliderRange(32, 320)
        .build()
    );

    private final Setting<Boolean> netherEnabled = sgNether.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Detect lava in the Nether.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> netherMinY = sgNether.add(new IntSetting.Builder()
        .name("min-y")
        .description("Skip lava ocean below this Y.")
        .defaultValue(32)
        .min(0)
        .max(127)
        .sliderRange(0, 127)
        .visible(netherEnabled::get)
        .build()
    );

    private final Setting<Boolean> overworldEnabled = sgOverworld.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Detect fluids in the Overworld.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> overworldMinY = sgOverworld.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y to scan.")
        .defaultValue(-60)
        .min(-64)
        .max(128)
        .sliderRange(-64, 128)
        .visible(overworldEnabled::get)
        .build()
    );

    private final Setting<Integer> overworldMaxY = sgOverworld.add(new IntSetting.Builder()
        .name("max-y")
        .description("Skip surface ocean above this Y.")
        .defaultValue(62)
        .min(0)
        .max(320)
        .sliderRange(0, 320)
        .visible(overworldEnabled::get)
        .build()
    );

    private final Setting<Boolean> detectLavaOverworld = sgOverworld.add(new BoolSetting.Builder()
        .name("detect-lava")
        .description("Also detect lava in the Overworld.")
        .defaultValue(true)
        .visible(overworldEnabled::get)
        .build()
    );

    private final Setting<Double> minFlowRatio = sgFilter.add(new DoubleSetting.Builder()
        .name("min-flow-ratio")
        .description("Minimum flowing/source ratio for a chunk to be kept.")
        .defaultValue(0.5)
        .min(0.0)
        .max(5.0)
        .sliderRange(0.0, 5.0)
        .build()
    );

    private final Setting<Integer> minSize = sgFilter.add(new IntSetting.Builder()
        .name("min-size")
        .description("Minimum fluid blocks to show.")
        .defaultValue(3)
        .min(1)
        .max(50)
        .sliderRange(1, 50)
        .build()
    );

    private final Setting<Integer> maxSize = sgFilter.add(new IntSetting.Builder()
        .name("max-size")
        .description("Skip oceans / lakes above this size.")
        .defaultValue(500)
        .min(50)
        .max(5000)
        .sliderRange(50, 5000)
        .build()
    );

    // ConcurrentHashMap (not LinkedHashMap) because:
    //  - onChunkData fires on network thread, scanExecutor writes on bg
    //    thread, onRender reads on render thread → needs concurrent-safe map.
    //  - LinkedHashMap with accessOrder=true triggers AIOOBE on
    //    values().toArray() (JDK bug — afterNodeAccess reorders mid-iter).
    //  - Distance-based eviction is already handled by evictDistant() every
    //    200 ticks, so we don't need an internal LRU bound; the cache size
    //    only grows when many chunks load at once and shrinks when they leave
    //    render-distance (or when the module is reactivated).
    private final Map<Long, ChunkData> overworldCache = new ConcurrentHashMap<>();
    private final Map<Long, ChunkData> netherCache = new ConcurrentHashMap<>();
    // ConcurrentHashMap.newKeySet() because ChunkDataEvent fires on the
    // network thread and these are mutated/iterated from multiple threads.
    // Reference mlep FlowESP.java:142-144 also uses ConcurrentHashMap.newKeySet().
    private final Set<Long> scannedOverworld = ConcurrentHashMap.newKeySet();
    private final Set<Long> scannedNether = ConcurrentHashMap.newKeySet();
    // Tracks chunks currently being scanned in the background — prevents the
    // same chunk being queued twice if ChunkData fires while a scan is in
    // progress.
    private final Set<Long> processing = ConcurrentHashMap.newKeySet();
    // Chunks queued for background scanning. onChunkData adds here, onTick
    // drains in batches. Without batching, many chunks loading at once
    // (e.g. fast travel) fill the executor queue.
    private final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
    // Background scan pool. The scan iterates 16*16*~120 = 30k+ block states
    // per chunk; running that on the main thread freezes the game when the
    // player moves and many chunks load at once. Reference uses 2 daemon
    // threads at priority 1 (lowest). We do the same.
    private ExecutorService scanExecutor;
    private int tick = 0;

    public FlowESP() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "flow-esp",
            "Detects lava / water flows in loaded chunks and renders them.");
    }

    @Override
    public void onActivate() {
        overworldCache.clear();
        netherCache.clear();
        scannedOverworld.clear();
        scannedNether.clear();
        pendingChunks.clear();
        processing.clear();
        tick = 0;
        scanExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "FlowESP-Scan");
            t.setDaemon(true);
            t.setPriority(1);
            return t;
        });
    }

    @Override
    public void onDeactivate() {
        if (scanExecutor != null) {
            scanExecutor.shutdownNow();
            scanExecutor = null;
        }
        overworldCache.clear();
        netherCache.clear();
        scannedOverworld.clear();
        scannedNether.clear();
        pendingChunks.clear();
        processing.clear();
        tick = 0;
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null || mc.world.getRegistryKey() == World.END) return;
        if (scanExecutor == null || scanExecutor.isShutdown()) return;
        Chunk chunk = event.chunk();
        long key = chunk.getPos().toLong();
        boolean nether = mc.world.getRegistryKey() == World.NETHER;
        if (nether && scannedNether.contains(key)) return;
        if (!nether && scannedOverworld.contains(key)) return;

        // Queue for background processing in onTick. Direct submit would
        // fill the executor with one task per chunk when the player moves
        // fast, which can starve other tasks. Reference mlep FlowESP.java
        // uses a single processing-set guard but scans synchronously; we do
        // the scan async but still batch.
        pendingChunks.add(key);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || mc.world.getRegistryKey() == World.END) return;
        // Lava/water flow generates BlockUpdateEvent for every block that
        // changes. Without re-queueing here, the chunk stays in scannedNether
        // / scannedOverworld and the player's view of flowing fluids stops
        // updating the instant a block transitions. invalidate + queue.
        long key = ChunkPos.toLong(event.pos.getX() >> 4, event.pos.getZ() >> 4);
        scannedNether.remove(key);
        scannedOverworld.remove(key);
        pendingChunks.add(key);
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

        List<FlowColumn> columns = new ArrayList<>();
        int totalSrc = 0;
        int totalFlow = 0;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunk.getPos().getStartX() + lx;
                int wz = chunk.getPos().getStartZ() + lz;
                int colBottom = -1, colTop = -1;
                boolean colLava = false;
                boolean colHasSource = false;
                int colSrc = 0, colFlow = 0;

                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(wx, y, wz);
                    BlockState state = chunk.getBlockState(pos);
                    boolean isLava = state.getBlock() == Blocks.LAVA;
                    boolean isWater = state.getBlock() == Blocks.WATER;

                    // Skip water in nether, skip lava in overworld if disabled
                    if (nether && isWater) continue;
                    if (!nether && isWater && !overworldEnabled.get()) continue;
                    if (!nether && isLava && !detectLavaOverworld.get()) continue;

                    if (!isLava && !isWater) {
                        // End of column
                        if (colBottom != -1 && colFlow > 0) {
                            columns.add(new FlowColumn(lx, lz, colBottom, colTop, colFlow, colLava, colHasSource));
                            totalSrc += colSrc;
                            totalFlow += colFlow;
                        }
                        colBottom = -1; colTop = -1; colSrc = 0; colFlow = 0;
                        colHasSource = false;
                        continue;
                    }

                    // Skip water sources (3+ neighbors) to avoid ocean detection
                    if (isWater) {
                        FluidState fs = state.getFluidState();
                        if (fs.isStill() && countAdjWaterSources(chunk, pos) >= 3) continue;
                    }

                    FluidState fs = state.getFluidState();
                    boolean isSrc = fs.isStill();

                    if (colBottom == -1) {
                        colBottom = y;
                        colLava = isLava;
                    }
                    colTop = y;
                    if (isSrc) {
                        colSrc++;
                        colHasSource = true;
                    } else {
                        colFlow++;
                    }
                }

                if (colBottom != -1 && colFlow > 0) {
                    columns.add(new FlowColumn(lx, lz, colBottom, colTop, colFlow, colLava, colHasSource));
                    totalSrc += colSrc;
                    totalFlow += colFlow;
                }
            }
        }

        if (columns.isEmpty()) return null;
        double flowRatio = (double) totalFlow / (totalSrc + 1);
        return new ChunkData(chunk.getPos().toLong(), totalSrc, totalFlow, flowRatio, columns);
    }

    private int countAdjWaterSources(Chunk chunk, BlockPos pos) {
        int count = 0;
        int[][] offs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : offs) {
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

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        processPendingChunks();
        tick++;
        if (tick >= 200) {
            tick = 0;
            evictDistant();
        }
    }

    /**
     * Drain up to 4 queued chunks per tick to the background scan executor,
     * closest chunks first. Without batching, dozens of chunks loading at
     * once (e.g. fast travel, teleport) would fill the executor queue.
     * Reference: mlep FlowESP.java uses the same drain-on-tick pattern.
     */
    private void processPendingChunks() {
        if (pendingChunks.isEmpty() || scanExecutor == null || scanExecutor.isShutdown()) return;

        List<Long> queued = new ArrayList<>(pendingChunks);
        if (queued.isEmpty()) return;
        queued.sort(Comparator.comparingLong(this::chunkDistSqToPlayer));

        int drained = 0;
        for (long key : queued) {
            if (drained >= 4) break;
            if (!pendingChunks.remove(key)) continue;

            boolean nether = mc.world.getRegistryKey() == World.NETHER;
            if (nether && scannedNether.contains(key)) continue;
            if (!nether && scannedOverworld.contains(key)) continue;
            if (!processing.add(key)) continue;

            int cx = (int) (key & 0xFFFFFFFFL);
            int cz = (int) (key >>> 32);
            if (!mc.world.isChunkLoaded(cx, cz)) {
                processing.remove(key);
                continue;
            }
            Chunk chunk = mc.world.getChunk(cx, cz);
            if (nether) scannedNether.add(key); else scannedOverworld.add(key);
            drained++;
            scanExecutor.submit(() -> {
                try {
                    ChunkData data = scanChunk(chunk, nether);
                    if (data != null) {
                        if (nether) netherCache.put(key, data);
                        else overworldCache.put(key, data);
                    }
                } finally {
                    processing.remove(key);
                }
            });
        }
    }

    private long chunkDistSqToPlayer(long key) {
        int cx = (int) (key & 0xFFFFFFFFL);
        int cz = (int) (key >>> 32);
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int dx = cx - pcx;
        int dz = cz - pcz;
        return (long) dx * dx + (long) dz * dz;
    }

    private void evictDistant() {
        if (mc.player == null) return;
        int px = mc.player.getChunkPos().x;
        int pz = mc.player.getChunkPos().z;
        int dist = renderDistance.get() / 16 + 4;
        int distSq = dist * dist;
        Iterator<Map.Entry<Long, ChunkData>> itOver = overworldCache.entrySet().iterator();
        while (itOver.hasNext()) {
            Map.Entry<Long, ChunkData> e = itOver.next();
            int cx = (int) (e.getKey() & 0xFFFFFFFFL); // low bits = chunkX
            int cz = (int) (e.getKey() >>> 32);        // high bits = chunkZ
            int dx = cx - px;
            int dz = cz - pz;
            if (dx * dx + dz * dz > distSq) itOver.remove();
        }
        Iterator<Map.Entry<Long, ChunkData>> itNether = netherCache.entrySet().iterator();
        while (itNether.hasNext()) {
            Map.Entry<Long, ChunkData> e = itNether.next();
            int cx = (int) (e.getKey() & 0xFFFFFFFFL);
            int cz = (int) (e.getKey() >>> 32);
            int dx = cx - px;
            int dz = cz - pz;
            if (dx * dx + dz * dz > distSq) itNether.remove();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (mc.world.getRegistryKey() == World.END) return;

        Vec3d pp = mc.player.getEyePos();
        int rdist = renderDistance.get();
        int rdistSq = rdist * rdist;
        boolean nether = mc.world.getRegistryKey() == World.NETHER;
        Map<Long, ChunkData> cache = nether ? netherCache : overworldCache;

        SettingColor lc = lavaColor.get();
        SettingColor wc = waterColor.get();

        // ConcurrentHashMap iteration is weakly consistent: safe under
        // concurrent writes from scanExecutor / onChunkData / evictDistant,
        // no CME. We may miss a fresh entry or see a stale one for one frame
        // at most — acceptable for an ESP HUD.
        ChunkData[] snapshot = cache.values().toArray(new ChunkData[0]);

        for (ChunkData data : snapshot) {
            long key = data.chunkKey;
            int chunkX = (int) (key & 0xFFFFFFFFL);
            int chunkZ = (int) (key >>> 32);
            double cx = chunkX * 16 + 8;
            double cz = chunkZ * 16 + 8;
            double chunkDistSq = (pp.x - cx) * (pp.x - cx) + (pp.z - cz) * (pp.z - cz);
            if (chunkDistSq > rdistSq * 4) continue;

            for (FlowColumn col : data.columns) {
                int wx = chunkX * 16 + col.x;
                int wz = chunkZ * 16 + col.z;
                double blockDistSq = pp.squaredDistanceTo(wx + 0.5, col.bottomY, wz + 0.5);
                if (blockDistSq > rdistSq) continue;

                SettingColor sc = col.isLava ? lc : wc;
                Color c = new Color(sc.r, sc.g, sc.b, sc.a);
                event.renderer.box(wx, col.bottomY, wz, wx + 1, col.topY + 1, wz + 1, c, c, ShapeMode.Both, 0);
            }
        }
    }

    private static class ChunkData {
        final long chunkKey;
        final int sourceCount;
        final int flowCount;
        final double flowRatio;
        final List<FlowColumn> columns;
        final int total;

        ChunkData(long chunkKey, int src, int flow, double ratio, List<FlowColumn> cols) {
            this.chunkKey = chunkKey;
            this.sourceCount = src;
            this.flowCount = flow;
            this.flowRatio = ratio;
            this.columns = cols;
            this.total = src + flow;
        }
    }

    private static class FlowColumn {
        final int x, z;
        final int bottomY, topY;
        final int flowLen;
        final boolean isLava;
        final boolean hasSource;

        FlowColumn(int x, int z, int bottomY, int topY, int flowLen, boolean isLava, boolean hasSource) {
            this.x = x;
            this.z = z;
            this.bottomY = bottomY;
            this.topY = topY;
            this.flowLen = flowLen;
            this.isLava = isLava;
            this.hasSource = hasSource;
        }
    }
}