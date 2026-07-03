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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Detects portal-shaped disturbances in cave air.
 *
 * <p>Scans loaded chunks for AIR blocks adjacent to CAVE_AIR (a strong
 * signal that someone carved a tunnel through stone into an existing
 * cave). Clusters these disturbances and validates them against
 * nether-portal dimensions (4–8 wide, 5+ tall), highlighting likely
 * player-built portals hidden in caves. Useful for finding portal traps.
 *
 * <p>Simplified port of mlep's {@code CaveAirESP}: kept the portal
 * detection + cluster validation + 3D render. Dropped the optional
 * cave-air render mode, all-air-in-cave highlight, sound notification,
 * and async chunk scanner (single-threaded, main-thread only).
 */
public class CaveAirESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetection = settings.createGroup("Detection");
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
        .name("min-y").description("Minimum Y to scan.").defaultValue(-60)
        .sliderRange(-64, 320).build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y").description("Maximum Y to scan.").defaultValue(128)
        .sliderRange(-64, 320).build()
    );

    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder()
        .name("render-distance").description("Maximum render distance (blocks).").defaultValue(128)
        .sliderRange(16, 256).build()
    );

    private final Setting<Integer> maxPortalWidth = sgDetection.add(new IntSetting.Builder()
        .name("max-portal-width").description("Maximum outer portal width (more = false positives).")
        .defaultValue(8).sliderRange(4, 23).build()
    );

    private final Setting<Integer> maxPortalHeight = sgDetection.add(new IntSetting.Builder()
        .name("max-portal-height").description("Maximum outer portal height.")
        .defaultValue(10).sliderRange(5, 23).build()
    );

    private final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder()
        .name("disturbance-fill").description("Fill color for portal detection boxes.")
        .defaultValue(new SettingColor(255, 50, 50, 60)).build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("disturbance-line").description("Outline color.")
        .defaultValue(new SettingColor(255, 100, 100, 255)).build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("How to render detection boxes.")
        .defaultValue(ShapeMode.Both).build()
    );

    private final Setting<Boolean> renderBeacon = sgRender.add(new BoolSetting.Builder()
        .name("beacon").description("Draw vertical beacon line above portal detections.")
        .defaultValue(true).build()
    );

    private final Setting<Integer> beaconHeight = sgRender.add(new IntSetting.Builder()
        .name("beacon-height").description("Beacon line height.")
        .defaultValue(64).sliderRange(8, 128).visible(renderBeacon::get).build()
    );

    private static final int[][] OFFSETS = {
        {0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}
    };

    // ConcurrentHashMap.newKeySet() because ChunkDataEvent fires on the
    // network thread and the scanExecutor below runs scans off the main thread.
    private final Set<Long> scannedChunks = ConcurrentHashMap.newKeySet();
    // Tracks chunks currently being scanned in the background; prevents
    // duplicate submissions.
    private final Set<Long> processingChunks = ConcurrentHashMap.newKeySet();
    // Background scan pool. The scan iterates 16*16*~192 = ~49k block states
    // per chunk; running that on the main thread freezes the game when the
    // player moves. Reference mlep CaveAirESP.java uses 2 daemon threads at
    // priority 1 (lowest).
    private ExecutorService scanExecutor;
    private final List<RenderCluster> renderClusters = new ArrayList<>();
    private Color fill;
    private Color line;

    public CaveAirESP() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "cave-air",
            "Detects portal-shaped cave-air disturbances (hidden portals / traps).");
    }

    @Override
    public void onActivate() {
        scannedChunks.clear();
        processingChunks.clear();
        renderClusters.clear();
        computeColors();
        scanExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "CaveAirESP-Scan");
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
        scannedChunks.clear();
        processingChunks.clear();
        renderClusters.clear();
    }

    private boolean isDimensionEnabled() {
        if (mc.world == null) return false;
        var key = mc.world.getRegistryKey();
        if (key == World.OVERWORLD) return overworld.get();
        if (key == World.NETHER) return nether.get();
        if (key == World.END) return end.get();
        return false;
    }

    private void computeColors() {
        SettingColor f = fillColor.get();
        SettingColor l = lineColor.get();
        fill = new Color(f.r, f.g, f.b, f.a);
        line = new Color(l.r, l.g, l.b, l.a);
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (!isDimensionEnabled()) return;
        if (scanExecutor == null || scanExecutor.isShutdown()) return;
        Chunk chunk = event.chunk();
        long key = chunk.getPos().toLong();
        if (scannedChunks.contains(key)) return;
        // Deduplicate: skip if a scan for this chunk is already in flight.
        if (!processingChunks.add(key)) return;
        scannedChunks.add(key);

        // Scan off the main thread. The scan iterates 16*16*~192 = ~49k
        // block states per chunk; sync scanning freezes the game when the
        // player moves.
        scanExecutor.submit(() -> {
            try {
                scanChunk(chunk);
            } finally {
                processingChunks.remove(key);
            }
        });
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!isDimensionEnabled()) return;
        BlockPos pos = event.pos;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        long key = ChunkPos.toLong(cx, cz);
        // Invalidate cache entry and re-scan synchronously. Without the re-scan,
        // the chunk is already loaded so onChunkData won't fire again, leaving
        // us with stale detection. Block updates are rare (player break/place)
        // so a sync scan here is cheap.
        scannedChunks.remove(key);
        if (mc.world != null && mc.world.isChunkLoaded(cx, cz)) {
            scanChunk(mc.world.getChunk(cx, cz));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || !isDimensionEnabled()) return;
        computeColors();
        rebuildClusterCache();
    }

    private void scanChunk(Chunk chunk) {
        int baseX = chunk.getPos().getStartX();
        int baseZ = chunk.getPos().getStartZ();
        int minYVal = minY.get();
        int maxYVal = maxY.get();

        Set<Long> disturbancePositions = new HashSet<>();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;

                for (int y = minYVal; y <= maxYVal; y++) {
                    BlockPos pos = new BlockPos(wx, y, wz);
                    Block block = chunk.getBlockState(pos).getBlock();
                    if (block == Blocks.AIR) {
                        // Check if any neighbor is CAVE_AIR
                        for (int[] off : OFFSETS) {
                            BlockPos n = pos.add(off[0], off[1], off[2]);
                            Block nb;
                            try {
                                nb = mc.world.getBlockState(n).getBlock();
                            } catch (Exception e) {
                                continue;
                            }
                            if (nb == Blocks.CAVE_AIR) {
                                disturbancePositions.add(packPos(wx, y, wz));
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Cluster disturbances and find portal-shaped ones
        int maxW = maxPortalWidth.get();
        int maxH = maxPortalHeight.get();

        Set<Long> visited = new HashSet<>();
        for (long p : disturbancePositions) {
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
                    if (disturbancePositions.contains(nb) && !visited.contains(nb)) {
                        visited.add(nb);
                        queue.add(nb);
                    }
                }
            }

            int widthX = maxX - minX + 1;
            int height = maxY2 - minY2 + 1;
            int widthZ = maxZ - minZ + 1;
            boolean facesX = widthX == 1;
            boolean facesZ = widthZ == 1;
            if (!facesX && !facesZ) continue;
            int W = facesX ? widthZ : widthX;
            int H = height;
            if (W < 4 || H < 5 || W > maxW || H > maxH) continue;

            int blockCount = cluster.size();
            int filledRect = W * H;
            int noCorners = filledRect - 4;
            if (blockCount != filledRect && blockCount != noCorners) continue;

            Set<Long> positions = new HashSet<>(cluster);
            boolean valid = validateShape(positions, minX, minY2, minZ, W, H, facesX, blockCount == noCorners);
            if (!valid) continue;

            // Store as a renderable cluster
            renderClusters.add(new RenderCluster(
                minX, minY2, minZ,
                maxX, maxY2, maxZ,
                (minX + maxX + 1) / 2.0,
                (minZ + maxZ + 1) / 2.0,
                true // portalShaped
            ));
        }
    }

    private boolean validateShape(Set<Long> positions, int minX, int minY, int minZ,
                                  int W, int H, boolean facesX, boolean noCorners) {
        int maxY = minY + H - 1;
        if (facesX) {
            int x = minX;
            int maxZ = minZ + W - 1;
            for (int y = minY; y <= maxY; y++) {
                boolean isTopOrBottom = (y == minY || y == maxY);
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isCorner = isTopOrBottom && (z == minZ || z == maxZ);
                    boolean hasBlock = positions.contains(packPos(x, y, z));
                    if (noCorners) {
                        if (isCorner) { if (hasBlock) return false; }
                        else if (!hasBlock) { return false; }
                    } else {
                        if (!hasBlock) return false;
                    }
                }
            }
        } else {
            int z = minZ;
            int maxX = minX + W - 1;
            for (int y = minY; y <= maxY; y++) {
                boolean isTopOrBottom = (y == minY || y == maxY);
                for (int x = minX; x <= maxX; x++) {
                    boolean isCorner = isTopOrBottom && (x == minX || x == maxX);
                    boolean hasBlock = positions.contains(packPos(x, y, z));
                    if (noCorners) {
                        if (isCorner) { if (hasBlock) return false; }
                        else if (!hasBlock) { return false; }
                    } else {
                        if (!hasBlock) return false;
                    }
                }
            }
        }
        return true;
    }

    private void rebuildClusterCache() {
        if (mc.player == null || renderClusters.isEmpty()) return;
        Vec3d pp = mc.player.getEyePos();
        int rdist = renderDistance.get();
        int rdistSq = rdist * rdist;
        renderClusters.removeIf(c -> {
            double dx = pp.x - c.centerX;
            double dz = pp.z - c.centerZ;
            return (dx * dx + dz * dz) > rdistSq * 4;
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || !isDimensionEnabled()) return;
        if (renderClusters.isEmpty()) return;
        ShapeMode shape = shapeMode.get();
        boolean beacon = renderBeacon.get();
        int bHeight = beaconHeight.get();
        for (RenderCluster c : renderClusters) {
            event.renderer.box(c.minX, c.minY, c.minZ, c.maxX + 1, c.maxY + 1, c.maxZ + 1,
                fill, line, shape, 0);
            if (beacon) {
                event.renderer.line(c.centerX, c.maxY + 1, c.centerZ, c.centerX, c.maxY + 1 + bHeight, c.centerZ, line);
            }
        }
    }

    private static long packPos(int x, int y, int z) {
        return (long) (x & 0x3FFFFFF) << 38 | (long) (y & 0xFFF) << 26 | (z & 0x3FFFFFF);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackY(long packed) {
        // The 12-bit Y field is sign-extended so negative Y values (e.g. -60
        // for NetherCaveAirESP's default minY) round-trip correctly. The
        // (x << 20) >> 20 idiom shifts the high bit of the 12-bit value into
        // the int's sign position, then arithmetic shift right propagates it.
        // Reference: mlep CaveAirESP.java:333.
        return ((int) ((packed >> 26) & 0xFFF)) << 20 >> 20;
    }

    private static int unpackZ(long packed) {
        return (int) (packed & 0x3FFFFFF);
    }

    private record RenderCluster(
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        double centerX, double centerZ,
        boolean portalShaped
    ) {}
}