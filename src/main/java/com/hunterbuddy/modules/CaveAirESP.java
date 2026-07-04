package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
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
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Detects portal-shaped disturbances hidden in cave air.
 *
 * <p>Full port of mlep's {@code CaveAirESP}. Three render layers, all
 * optional via setting:
 * <ul>
 *   <li><b>Portal disturbance</b> (always on): clusters of {@code AIR}
 *       blocks adjacent to {@code CAVE_AIR} whose bounding box matches
 *       a Nether portal (4-5 wide, 5+ tall, 1 block thick). This is
 *       what catches a player-built portal concealed in a cave.</li>
 *   <li><b>highlight-all-air-in-cave</b>: every {@code AIR} block that
 *       is fully enclosed by {@code CAVE_AIR} on at least one axis.</li>
 *   <li><b>show-all-cave-air</b>: greedy-meshes every {@code CAVE_AIR}
 *       region into boxes, visualising the cave itself.</li>
 * </ul>
 *
 * <p>Also plays a note-block pling on first detection of each new
 * portal (debounced by portal position).
 */
public class CaveAirESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgCaveAir = settings.createGroup("Cave Air Render");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> overworld = sgGeneral.add(new BoolSetting.Builder()
        .name("overworld").description("Scan in the Overworld.").defaultValue(true).build()
    );

    private final Setting<Boolean> nether = sgGeneral.add(new BoolSetting.Builder()
        .name("nether").description("Scan in the Nether.").defaultValue(true).build()
    );

    private final Setting<Boolean> end = sgGeneral.add(new BoolSetting.Builder()
        .name("end").description("Scan in the End.").defaultValue(false).build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y").description("Minimum Y level to scan.").defaultValue(-60)
        .sliderRange(-64, 320).build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y").description("Maximum Y level to scan.").defaultValue(128)
        .sliderRange(-64, 320).build()
    );

    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder()
        .name("render-distance").description("Maximum render distance (blocks).").defaultValue(128)
        .sliderRange(16, 256).build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").description("Show debug information.").defaultValue(false).build()
    );

    private final Setting<Boolean> portalDetectionSound = sgGeneral.add(new BoolSetting.Builder()
        .name("portal-detection-sound")
        .description("Play a sound when a portal-shaped disturbance is detected.")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> highlightAllAirInCave = sgDetection.add(new BoolSetting.Builder()
        .name("highlight-all-air-in-cave")
        .description("Highlight every regular AIR block that is inside a CAVE_AIR region.")
        .defaultValue(false).build()
    );

    private final Setting<Integer> maxPortalWidth = sgDetection.add(new IntSetting.Builder()
        .name("max-portal-width")
        .description("Maximum portal width to detect (outer frame). Larger = more false positives.")
        .defaultValue(8).sliderRange(4, 23).build()
    );

    private final Setting<Integer> maxPortalHeight = sgDetection.add(new IntSetting.Builder()
        .name("max-portal-height")
        .description("Maximum portal height to detect (outer frame). Larger = more false positives.")
        .defaultValue(10).sliderRange(5, 23).build()
    );

    private final Setting<Boolean> showAllCaveAir = sgCaveAir.add(new BoolSetting.Builder()
        .name("show-all-cave-air")
        .description("Render all cave air blocks (merged into large boxes).")
        .defaultValue(false).build()
    );

    private final Setting<Integer> caveAirRenderDist = sgCaveAir.add(new IntSetting.Builder()
        .name("cave-air-render-distance")
        .description("Max distance to render cave air.").defaultValue(64)
        .sliderRange(16, 256).visible(showAllCaveAir::get).build()
    );

    private final Setting<SettingColor> caveAirFillColor = sgCaveAir.add(new ColorSetting.Builder()
        .name("cave-air-fill").description("Fill color for cave air.")
        .defaultValue(new SettingColor(50, 150, 255, 30)).visible(showAllCaveAir::get).build()
    );

    private final Setting<SettingColor> caveAirLineColor = sgCaveAir.add(new ColorSetting.Builder()
        .name("cave-air-line").description("Outline color for cave air.")
        .defaultValue(new SettingColor(50, 150, 255, 80)).visible(showAllCaveAir::get).build()
    );

    private final Setting<ShapeMode> caveAirShapeMode = sgCaveAir.add(new EnumSetting.Builder<ShapeMode>()
        .name("cave-air-shape").description("How to render cave air boxes.")
        .defaultValue(ShapeMode.Both).visible(showAllCaveAir::get).build()
    );

    private final Setting<SettingColor> disturbanceColor = sgRender.add(new ColorSetting.Builder()
        .name("disturbance-color")
        .description("Color for detected disturbances.")
        .defaultValue(new SettingColor(255, 50, 50, 150)).build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color for disturbances.")
        .defaultValue(new SettingColor(255, 100, 100, 255)).build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("How to render disturbance boxes.")
        .defaultValue(ShapeMode.Both).build()
    );

    private final Setting<Boolean> renderBeacon = sgRender.add(new BoolSetting.Builder()
        .name("beacon").description("Draw vertical beacon line above portal detections.")
        .defaultValue(true).build()
    );

    private final Setting<Integer> beaconHeight = sgRender.add(new IntSetting.Builder()
        .name("beacon-height").description("Height of beacon line.")
        .defaultValue(64).sliderRange(8, 128).visible(renderBeacon::get).build()
    );

    // Cache keyed by ChunkPos packed-long so we don't rescan a chunk just
    // because it re-loaded. Mutated from scanExecutor, read from render /
    // onTick threads → ConcurrentHashMap.
    private final Map<Long, ChunkData> chunkCache = new ConcurrentHashMap<>();
    private final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> processingChunks = ConcurrentHashMap.newKeySet();
    private final Set<String> notifiedPortals = ConcurrentHashMap.newKeySet();
    private ExecutorService scanExecutor;
    // Snapshot lists for the render thread. Volatile + per-tick rebuild
    // (rebuildClusterCache / rebuildCaveAirCache) means the render thread
    // always reads consistent, distance-filtered data without locking.
    private volatile List<RenderCluster> renderClusters = Collections.emptyList();
    private volatile List<RenderBox> renderCaveAir = Collections.emptyList();
    private volatile List<long[]> renderAirInCave = Collections.emptyList();
    private Color fillColor;
    private Color outlineColor;
    private Color caveAirFill;
    private Color caveAirLine;
    private int tick;

    private static final int[][] OFFSETS = {
        {0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}
    };

    public CaveAirESP() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "cave-air",
            "Detects portal-shaped disturbances in cave air.");
    }

    private boolean isDimensionEnabled() {
        if (mc.world == null) return false;
        var key = mc.world.getRegistryKey();
        if (key == World.OVERWORLD) return overworld.get();
        if (key == World.NETHER) return nether.get();
        if (key == World.END) return end.get();
        return false;
    }

    @Override
    public void onActivate() {
        chunkCache.clear();
        pendingChunks.clear();
        processingChunks.clear();
        notifiedPortals.clear();
        renderClusters = Collections.emptyList();
        renderCaveAir = Collections.emptyList();
        renderAirInCave = Collections.emptyList();
        tick = 0;
        computeColors();
        scanExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "CaveAirESP-Scan");
            t.setDaemon(true);
            t.setPriority(1);
            return t;
        });
        if (debug.get()) info("CaveAirESP activated");
    }

    @Override
    public void onDeactivate() {
        if (scanExecutor != null) {
            scanExecutor.shutdownNow();
            scanExecutor = null;
        }
        chunkCache.clear();
        pendingChunks.clear();
        processingChunks.clear();
        notifiedPortals.clear();
        renderClusters = Collections.emptyList();
        renderCaveAir = Collections.emptyList();
        renderAirInCave = Collections.emptyList();
    }

    private void computeColors() {
        SettingColor dc = disturbanceColor.get();
        SettingColor lc = lineColor.get();
        fillColor = new Color(dc.r, dc.g, dc.b, dc.a);
        outlineColor = new Color(lc.r, lc.g, lc.b, lc.a);
        SettingColor caf = caveAirFillColor.get();
        SettingColor cal = caveAirLineColor.get();
        caveAirFill = new Color(caf.r, caf.g, caf.b, caf.a);
        caveAirLine = new Color(cal.r, cal.g, cal.b, cal.a);
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (!isDimensionEnabled()) return;
        Chunk chunk = event.chunk();
        long key = chunk.getPos().toLong();
        if (!processingChunks.contains(key)) {
            pendingChunks.add(key);
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!isDimensionEnabled()) return;
        BlockPos pos = event.pos;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        long key = ChunkPos.toLong(cx, cz);
        chunkCache.remove(key);
        if (!processingChunks.contains(key)) {
            pendingChunks.add(key);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !isDimensionEnabled()) return;
        tick++;
        if (tick % 20 == 0) computeColors();
        if (tick % 3 == 0) processPendingChunks();
        if (tick % 2 == 0) rebuildClusterCache();
        if (tick % 10 == 0) rebuildCaveAirCache();
        if (tick >= 200) {
            tick = 0;
            evictDistantChunks();
        }
    }

    /**
     * Drain up to 4 queued chunks per tick to the background scan executor,
     * closest chunks first. Without batching, dozens of chunks loading at
     * once (e.g. fast travel) would fill the executor queue.
     * Reference: mlep CaveAirESP.java uses the same 4-per-tick drain pattern.
     */
    private void processPendingChunks() {
        if (pendingChunks.isEmpty() || scanExecutor == null || scanExecutor.isShutdown()) return;
        if (mc.player == null) return;

        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        List<Long> sorted = new ArrayList<>(pendingChunks);
        // Sort closest-chunks-first so the player sees nearest results sooner.
        sorted.sort(Comparator.comparingLong(key -> {
            int dx = ChunkPos.getPackedX(key) - playerChunkX;
            int dz = ChunkPos.getPackedZ(key) - playerChunkZ;
            return (long) dx * dx + (long) dz * dz;
        }));

        int processed = 0;
        for (long key : sorted) {
            if (processed >= 4) break;
            pendingChunks.remove(key);
            if (processingChunks.add(key)) {
                int cx = ChunkPos.getPackedX(key);
                int cz = ChunkPos.getPackedZ(key);
                scanExecutor.submit(() -> {
                    try {
                        scanChunk(cx, cz);
                    } finally {
                        processingChunks.remove(key);
                    }
                });
                processed++;
            }
        }
    }

    private void scanChunk(int cx, int cz) {
        if (mc.world == null) return;
        Chunk chunk;
        try {
            chunk = mc.world.getChunk(cx, cz);
        } catch (Exception e) {
            return;
        }
        if (chunk == null) return;

        int baseX = cx << 4;
        int baseZ = cz << 4;
        int minYVal = minY.get();
        int maxYVal = maxY.get();
        boolean collectCaveAir = showAllCaveAir.get();
        boolean collectAllAir = highlightAllAirInCave.get();

        Set<Long> disturbancePositions = new HashSet<>();
        Set<Long> caveAirPositions = new HashSet<>();
        List<long[]> allAirInCaveList = new ArrayList<>();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable neighborPos = new BlockPos.Mutable();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                for (int y = minYVal; y <= maxYVal; y++) {
                    pos.set(wx, y, wz);
                    BlockState state = chunk.getBlockState(pos);
                    Block block = state.getBlock();
                    if (block == Blocks.CAVE_AIR) {
                        if (collectCaveAir) caveAirPositions.add(packPos(wx, y, wz));
                    } else if (block == Blocks.AIR) {
                        boolean[] hasCaveAir = new boolean[6];
                        int caveAirCount = 0;
                        for (int i = 0; i < OFFSETS.length; i++) {
                            int[] off = OFFSETS[i];
                            neighborPos.set(wx + off[0], y + off[1], wz + off[2]);
                            BlockState neighborState = getBlockStateSafe(neighborPos);
                            if (neighborState != null && neighborState.getBlock() == Blocks.CAVE_AIR) {
                                hasCaveAir[i] = true;
                                caveAirCount++;
                            }
                        }
                        if (caveAirCount > 0) disturbancePositions.add(packPos(wx, y, wz));

                        if (collectAllAir) {
                            boolean enclosedY = hasCaveAir[0] && hasCaveAir[1];
                            boolean enclosedZ = hasCaveAir[2] && hasCaveAir[3];
                            boolean enclosedX = hasCaveAir[4] && hasCaveAir[5];
                            if (enclosedY || enclosedZ || enclosedX) {
                                allAirInCaveList.add(new long[]{wx, y, wz});
                            }
                        }
                    }
                }
            }
        }

        List<DisturbanceCluster> clusters = clusterDisturbances(disturbancePositions);
        List<MergedBox> caveAirBoxes = greedyMesh(caveAirPositions);
        if (!clusters.isEmpty() || !caveAirBoxes.isEmpty() || !allAirInCaveList.isEmpty()) {
            long key = ChunkPos.toLong(cx, cz);
            chunkCache.put(key, new ChunkData(cx, cz, clusters, caveAirBoxes, allAirInCaveList));

            for (DisturbanceCluster cluster : clusters) {
                if (cluster.portalShaped) {
                    String portalKey = cluster.bounds.minX + "," + cluster.bounds.minY + "," + cluster.bounds.minZ;
                    if (notifiedPortals.add(portalKey)) {
                        if (portalDetectionSound.get() && mc.player != null) {
                            // playSound must run on the main thread (it touches
                            // the player's audio source). mc.execute defers
                            // safely without blocking the bg scan.
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
                                }
                            });
                        }
                        if (debug.get()) {
                            info("Portal detected at " + cluster.bounds.minX + ", "
                                + cluster.bounds.minY + ", " + cluster.bounds.minZ);
                        }
                    }
                }
            }

            if (debug.get()) {
                long portalCount = clusters.stream().filter(c -> c.portalShaped).count();
                info("Chunk " + cx + "," + cz + ": " + portalCount + " portals, "
                    + clusters.size() + " clusters, " + allAirInCaveList.size() + " air blocks");
            }
        }
    }

    private BlockState getBlockStateSafe(BlockPos pos) {
        if (mc.world == null) return null;
        try {
            return mc.world.getBlockState(pos);
        } catch (Exception e) {
            return null;
        }
    }

    private long packPos(int x, int y, int z) {
        // x:26 | y:12 | z:26 = 64 bits
        return (long) (x & 0x3FFFFFF) << 38 | (long) (y & 0xFFF) << 26 | (z & 0x3FFFFFF);
    }

    private int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private int unpackY(long packed) {
        // 12-bit sign-extended (commit 4bd6491 fix; same as mlep).
        return ((int) ((packed >> 26) & 0xFFF)) << 20 >> 20;
    }

    private int unpackZ(long packed) {
        // 26-bit sign-extended — packed & 0x3FFFFFF extracts unsigned; the
        // (x << 6) >> 6 idiom shifts bit 25 (the sign bit of the 26-bit
        // value) into bit 31, then arithmetic right-shift propagates it.
        // Without this, chunks at negative Z (e.g. 2b2t where the player
        // is at Z=-3 738 329 → chunk Z=-233 645) get unpacked as huge
        // positive ints, putting every cluster's bounding box thousands of
        // chunks away from the player. Reference mlep CaveAirESP.java:330.
        return (int) (packed & 0x3FFFFFFL) << 6 >> 6;
    }

    private List<DisturbanceCluster> clusterDisturbances(Set<Long> positions) {
        List<DisturbanceCluster> clusters = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        int maxW = maxPortalWidth.get();
        int maxH = maxPortalHeight.get();

        for (long p : positions) {
            if (visited.contains(p)) continue;
            List<Long> cluster = new ArrayList<>();
            Queue<Long> queue = new LinkedList<>();
            queue.add(p);
            visited.add(p);
            int minX = Integer.MAX_VALUE, minY2 = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY2 = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

            while (!queue.isEmpty()) {
                long cur = queue.poll();
                cluster.add(cur);
                int cx = unpackX(cur), cy = unpackY(cur), cz = unpackZ(cur);
                minX = Math.min(minX, cx); minY2 = Math.min(minY2, cy); minZ = Math.min(minZ, cz);
                maxX = Math.max(maxX, cx); maxY2 = Math.max(maxY2, cy); maxZ = Math.max(maxZ, cz);
                for (int[] off : OFFSETS) {
                    long nb = packPos(cx + off[0], cy + off[1], cz + off[2]);
                    if (positions.contains(nb) && !visited.contains(nb)) {
                        visited.add(nb);
                        queue.add(nb);
                    }
                }
            }

            int blockCount = cluster.size();
            int widthX = maxX - minX + 1;
            int height = maxY2 - minY2 + 1;
            int widthZ = maxZ - minZ + 1;
            boolean facesX = widthX == 1;
            boolean facesZ = widthZ == 1;

            if (facesX || facesZ) {
                int W = facesX ? widthZ : widthX;
                int H = height;
                if (W >= 4 && H >= 5 && W <= maxW && H <= maxH) {
                    boolean validDimensions = isValidPortalDimensions(W, H, blockCount);
                    if (validDimensions) {
                        boolean validShape = isValidPortalShape(cluster, minX, minY2, minZ, W, H, blockCount, facesX);
                        if (validShape) {
                            MergedBox bounds = new MergedBox(minX, minY2, minZ, maxX, maxY2, maxZ);
                            clusters.add(new DisturbanceCluster(bounds, blockCount, true));
                        }
                    }
                }
            }
        }
        return clusters;
    }

    private boolean isValidPortalDimensions(int W, int H, int blockCount) {
        int filledRect = W * H;
        int noCorners = filledRect - 4;
        return blockCount == filledRect || blockCount == noCorners;
    }

    private boolean isValidPortalShape(List<Long> cluster, int minX, int minY, int minZ,
                                       int W, int H, int blockCount, boolean facesX) {
        int filledRect = W * H;
        int noCorners = filledRect - 4;
        Set<Long> positions = new HashSet<>(cluster);
        if (blockCount == filledRect) {
            return validateFilledRectangle(positions, minX, minY, minZ, W, H, facesX);
        } else {
            return blockCount == noCorners
                && validateFrameWithoutCorners(positions, minX, minY, minZ, W, H, facesX);
        }
    }

    private boolean validateFilledRectangle(Set<Long> positions, int minX, int minY, int minZ,
                                            int W, int H, boolean facesX) {
        int maxY = minY + H - 1;
        if (facesX) {
            int x = minX;
            int maxZ2 = minZ + W - 1;
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ2; z++) {
                    if (!positions.contains(packPos(x, y, z))) return false;
                }
            }
        } else {
            int z = minZ;
            int maxX2 = minX + W - 1;
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX2; x++) {
                    if (!positions.contains(packPos(x, y, z))) return false;
                }
            }
        }
        return true;
    }

    private boolean validateFrameWithoutCorners(Set<Long> positions, int minX, int minY, int minZ,
                                                int W, int H, boolean facesX) {
        int maxY = minY + H - 1;
        if (facesX) {
            int x = minX;
            int maxZ2 = minZ + W - 1;
            for (int y = minY; y <= maxY; y++) {
                boolean isTopOrBottom = (y == minY || y == maxY);
                for (int z = minZ; z <= maxZ2; z++) {
                    boolean isLeftOrRight = (z == minZ || z == maxZ2);
                    boolean isCorner = isTopOrBottom && isLeftOrRight;
                    boolean hasBlock = positions.contains(packPos(x, y, z));
                    if (isCorner) { if (hasBlock) return false; }
                    else if (!hasBlock) return false;
                }
            }
        } else {
            int z = minZ;
            int maxX2 = minX + W - 1;
            for (int y = minY; y <= maxY; y++) {
                boolean isTopOrBottom = (y == minY || y == maxY);
                for (int x = minX; x <= maxX2; x++) {
                    boolean isLeftOrRight = (x == minX || x == maxX2);
                    boolean isCorner = isTopOrBottom && isLeftOrRight;
                    boolean hasBlock = positions.contains(packPos(x, y, z));
                    if (isCorner) { if (hasBlock) return false; }
                    else if (!hasBlock) return false;
                }
            }
        }
        return true;
    }

    /**
     * Greedy mesh: collapse a set of positions into axis-aligned boxes by
     * repeatedly scanning from the smallest-sorted start position and
     * growing the box greedily in +X, then +Z, then +Y until the next
     * position would not be in {@code remaining}. Reference mlep
     * CaveAirESP.greedyMesh, used for both cave-air rendering and any
     * future expansion.
     */
    private List<MergedBox> greedyMesh(Set<Long> positions) {
        if (positions.isEmpty()) return Collections.emptyList();
        List<MergedBox> boxes = new ArrayList<>();
        Set<Long> remaining = new HashSet<>(positions);
        List<Long> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.<Long>comparingInt(a -> unpackY(a))
            .thenComparingInt(a -> unpackZ(a))
            .thenComparingInt(a -> unpackX(a)));

        for (long start : sorted) {
            if (!remaining.contains(start)) continue;
            remaining.remove(start);
            int startX = unpackX(start);
            int startY = unpackY(start);
            int startZ = unpackZ(start);
            int endX = startX;
            while (remaining.contains(packPos(endX + 1, startY, startZ))) {
                remaining.remove(packPos(++endX, startY, startZ));
            }
            int endZ = startZ;
            boolean canExpandZ = true;
            while (canExpandZ) {
                for (int x = startX; x <= endX; x++) {
                    if (!remaining.contains(packPos(x, startY, endZ + 1))) {
                        canExpandZ = false;
                        break;
                    }
                }
                if (canExpandZ) {
                    endZ++;
                    for (int x = startX; x <= endX; x++) {
                        remaining.remove(packPos(x, startY, endZ));
                    }
                }
            }
            int endY = startY;
            boolean canExpandY = true;
            while (canExpandY) {
                boolean abort = false;
                for (int x = startX; x <= endX && !abort; x++) {
                    for (int z = startZ; z <= endZ; z++) {
                        if (!remaining.contains(packPos(x, endY + 1, z))) {
                            abort = true;
                            break;
                        }
                    }
                }
                if (abort) break;
                endY++;
                for (int x = startX; x <= endX; x++) {
                    for (int z = startZ; z <= endZ; z++) {
                        remaining.remove(packPos(x, endY, z));
                    }
                }
            }
            boxes.add(new MergedBox(startX, startY, startZ, endX, endY, endZ));
        }
        return boxes;
    }

    private void rebuildClusterCache() {
        if (mc.player == null) {
            renderClusters = Collections.emptyList();
            renderAirInCave = Collections.emptyList();
            return;
        }
        Vec3d playerPos = mc.player.getEyePos();
        int rdist = renderDistance.get();
        int rdistSq = rdist * rdist;
        List<RenderCluster> newClusters = new ArrayList<>();
        List<long[]> newAirInCave = new ArrayList<>();

        for (Map.Entry<Long, ChunkData> entry : chunkCache.entrySet()) {
            ChunkData data = entry.getValue();
            if (data == null) continue;
            double chunkCenterX = data.chunkX * 16 + 8;
            double chunkCenterZ = data.chunkZ * 16 + 8;
            double chunkDistSq = (playerPos.x - chunkCenterX) * (playerPos.x - chunkCenterX)
                + (playerPos.z - chunkCenterZ) * (playerPos.z - chunkCenterZ);
            if (chunkDistSq > rdistSq * 4) continue;
            if (data.clusters != null) {
                for (DisturbanceCluster cluster : data.clusters) {
                    double distSq = nearestPointDistanceSq(playerPos, cluster.bounds);
                    if (distSq <= rdistSq) {
                        newClusters.add(new RenderCluster(cluster.bounds, cluster.portalShaped));
                    }
                }
            }
            if (highlightAllAirInCave.get() && data.allAirInCave != null) {
                for (long[] ap : data.allAirInCave) {
                    double distSq = playerPos.squaredDistanceTo(ap[0] + 0.5, ap[1] + 0.5, ap[2] + 0.5);
                    if (distSq <= rdistSq) newAirInCave.add(ap);
                }
            }
        }
        renderClusters = newClusters;
        renderAirInCave = newAirInCave;
    }

    private void rebuildCaveAirCache() {
        if (mc.player == null || !showAllCaveAir.get()) {
            renderCaveAir = Collections.emptyList();
            return;
        }
        Vec3d playerPos = mc.player.getEyePos();
        int caRdist = caveAirRenderDist.get();
        int bufferedDist = caRdist + caRdist / 10 + 8;
        int bufferedDistSq = bufferedDist * bufferedDist;
        List<RenderBox> newCaveAir = new ArrayList<>();

        for (Map.Entry<Long, ChunkData> entry : chunkCache.entrySet()) {
            ChunkData data = entry.getValue();
            if (data == null || data.caveAirBoxes == null) continue;
            double chunkCenterX = data.chunkX * 16 + 8;
            double chunkCenterZ = data.chunkZ * 16 + 8;
            double chunkDistSq = (playerPos.x - chunkCenterX) * (playerPos.x - chunkCenterX)
                + (playerPos.z - chunkCenterZ) * (playerPos.z - chunkCenterZ);
            if (chunkDistSq > bufferedDistSq * 4) continue;
            for (MergedBox box : data.caveAirBoxes) {
                double distSq = nearestPointDistanceSq(playerPos, box);
                if (distSq <= bufferedDistSq) newCaveAir.add(new RenderBox(box));
            }
        }
        renderCaveAir = newCaveAir;
    }

    private void evictDistantChunks() {
        if (mc.player == null) return;
        int px = mc.player.getChunkPos().x;
        int pz = mc.player.getChunkPos().z;
        int dist = Math.max(renderDistance.get(), caveAirRenderDist.get()) / 16 + 2;
        int distSq = dist * dist;
        List<Long> toRemove = new ArrayList<>();
        for (Map.Entry<Long, ChunkData> entry : chunkCache.entrySet()) {
            ChunkData data = entry.getValue();
            int dx = data.chunkX - px;
            int dz = data.chunkZ - pz;
            if (dx * dx + dz * dz > distSq) toRemove.add(entry.getKey());
        }
        toRemove.forEach(chunkCache::remove);
        if (debug.get() && !toRemove.isEmpty()) info("Evicted " + toRemove.size() + " distant chunks");
    }

    /** Drop all cached chunk scan results. Reference parity with mlep CaveAirESP. */
    public void clearCache() {
        chunkCache.clear();
        pendingChunks.clear();
        renderClusters = Collections.emptyList();
        renderCaveAir = Collections.emptyList();
        renderAirInCave = Collections.emptyList();
        info("Cache cleared");
    }

    /** @return human-readable stats about the current cache. Reference parity with mlep CaveAirESP. */
    public String stats() {
        int chunks = chunkCache.size();
        int portals = (int) chunkCache.values().stream()
            .flatMap(c -> c.clusters.stream())
            .filter(c -> c.portalShaped)
            .count();
        int totalClusters = chunkCache.values().stream().mapToInt(c -> c.clusters.size()).sum();
        int caveAirBoxes = chunkCache.values().stream().mapToInt(c -> c.caveAirBoxes.size()).sum();
        return "Chunks: " + chunks
            + " | Portals: " + portals
            + " | Clusters: " + totalClusters
            + " | CaveAir boxes: " + caveAirBoxes;
    }

    private static double nearestPointDistanceSq(Vec3d point, MergedBox box) {
        double dx = Math.max(0.0, Math.max(box.minX - point.x, point.x - (box.maxX + 1)));
        double dy = Math.max(0.0, Math.max(box.minY - point.y, point.y - (box.maxY + 1)));
        double dz = Math.max(0.0, Math.max(box.minZ - point.z, point.z - (box.maxZ + 1)));
        return dx * dx + dy * dy + dz * dz;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || !isDimensionEnabled()) return;

        ShapeMode shape = shapeMode.get();
        boolean beacon = renderBeacon.get();
        int bHeight = beaconHeight.get();

        for (RenderCluster c : renderClusters) {
            event.renderer.box(c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ,
                fillColor, outlineColor, shape, 0);
            if (c.portalShaped && beacon) {
                event.renderer.line(c.centerX, c.maxY, c.centerZ, c.centerX,
                    c.maxY + bHeight, c.centerZ, outlineColor);
            }
        }

        if (highlightAllAirInCave.get()) {
            for (long[] ap : renderAirInCave) {
                int x = (int) ap[0], y = (int) ap[1], z = (int) ap[2];
                event.renderer.box(x, y, z, x + 1, y + 1, z + 1, fillColor, outlineColor, shape, 0);
            }
        }

        if (showAllCaveAir.get()) {
            ShapeMode caShape = caveAirShapeMode.get();
            for (RenderBox b : renderCaveAir) {
                event.renderer.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ,
                    caveAirFill, caveAirLine, caShape, 0);
            }
        }
    }

    private static class ChunkData {
        final int chunkX;
        final int chunkZ;
        final List<DisturbanceCluster> clusters;
        final List<MergedBox> caveAirBoxes;
        final List<long[]> allAirInCave;

        ChunkData(int cx, int cz, List<DisturbanceCluster> clusters,
                  List<MergedBox> caveAirBoxes, List<long[]> allAirInCave) {
            this.chunkX = cx;
            this.chunkZ = cz;
            this.clusters = clusters;
            this.caveAirBoxes = caveAirBoxes;
            this.allAirInCave = allAirInCave;
        }
    }

    private static class DisturbanceCluster {
        final MergedBox bounds;
        @SuppressWarnings("unused")
        final int blockCount;
        final boolean portalShaped;

        DisturbanceCluster(MergedBox bounds, int blockCount, boolean portalShaped) {
            this.bounds = bounds;
            this.blockCount = blockCount;
            this.portalShaped = portalShaped;
        }
    }

    private static class MergedBox {
        final int minX, minY, minZ, maxX, maxY, maxZ;

        MergedBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }

        // Reference parity: mlep's MergedBox exposes these helpers even
        // though CaveAirESP.java itself doesn't call them. Keep them so
        // external reflection-based consumers (or future modules) keep
        // working.
        int width() {
            return this.maxX - this.minX + 1;
        }

        int height() {
            return this.maxY - this.minY + 1;
        }

        int depth() {
            return this.maxZ - this.minZ + 1;
        }

        Vec3d center() {
            return new Vec3d(
                (this.minX + this.maxX + 1) / 2.0,
                (this.minY + this.maxY + 1) / 2.0,
                (this.minZ + this.maxZ + 1) / 2.0
            );
        }
    }

    private static class RenderBox {
        final int minX, minY, minZ, maxX, maxY, maxZ;

        RenderBox(MergedBox b) {
            this.minX = b.minX; this.minY = b.minY; this.minZ = b.minZ;
            this.maxX = b.maxX + 1; this.maxY = b.maxY + 1; this.maxZ = b.maxZ + 1;
        }
    }

    private static class RenderCluster {
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final double centerX, centerZ;
        final boolean portalShaped;

        RenderCluster(MergedBox b, boolean portalShaped) {
            this.minX = b.minX; this.minY = b.minY; this.minZ = b.minZ;
            this.maxX = b.maxX + 1; this.maxY = b.maxY + 1; this.maxZ = b.maxZ + 1;
            this.centerX = (b.minX + b.maxX + 1) / 2.0;
            this.centerZ = (b.minZ + b.maxZ + 1) / 2.0;
            this.portalShaped = portalShaped;
        }
    }
}
