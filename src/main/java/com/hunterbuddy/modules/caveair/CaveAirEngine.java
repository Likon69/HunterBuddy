package com.hunterbuddy.modules.caveair;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public final class CaveAirEngine {
    private static final int CHUNK_SHIFT = 4;
    private static final int DISPATCH_PERIOD = 3;
    private static final int CLUSTER_PERIOD = 2;
    private static final int CAVE_AIR_PERIOD = 10;
    private static final int EVICTION_PERIOD = 200;
    private static final int EVICTION_MARGIN_CHUNKS = 2;
    private static final double BLOCKS_PER_CHUNK = 16.0;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final CaveAirOptions options;
    private final ResultCache cache = new ResultCache();
    private final ScanDispatcher dispatcher = new ScanDispatcher(this::scanChunk);
    private final ChunkScanner scanner;
    private final PortalAnnouncer announcer;
    private final RenderLists lists;
    private final CaveAirRenderer renderer;
    private int ticks;

    public CaveAirEngine(CaveAirOptions options) {
        this.options = options;
        this.scanner = new ChunkScanner(options);
        this.announcer = new PortalAnnouncer(options);
        this.lists = new RenderLists(options, cache);
        this.renderer = new CaveAirRenderer(options, lists);
    }

    public CaveAirRenderer renderer() {
        return renderer;
    }

    public void start() {
        dispatcher.start();
        if (options.debug.get()) {
            ChatUtils.info("CaveAirESP activated");
        }
    }

    public void stop() {
        dispatcher.stop();
        clearState();
    }

    public void clearCache() {
        clearState();
        ChatUtils.info("Cache cleared");
    }

    private void clearState() {
        cache.clear();
        dispatcher.clearPending();
        lists.clear();
        announcer.forget();
    }

    public String stats() {
        return "Chunks: " + cache.size() + " | Portals: " + cache.portalCount() + " | Clusters: " + lists.clusters().size()
            + " | CaveAir boxes: " + lists.caveAirBoxes().size();
    }

    public boolean isDimensionEnabled() {
        if (mc.world == null) return false;
        if (mc.world.getRegistryKey() == World.OVERWORLD) return options.overworld.get();
        if (mc.world.getRegistryKey() == World.NETHER) return options.nether.get();
        return mc.world.getRegistryKey() == World.END && options.end.get();
    }

    public void onChunkLoaded(WorldChunk chunk) {
        if (isDimensionEnabled()) {
            dispatcher.queue(chunk.getPos().toLong());
        }
    }

    public void onBlockUpdated(BlockPos pos) {
        if (!isDimensionEnabled()) return;
        long key = ChunkPos.toLong(pos.getX() >> CHUNK_SHIFT, pos.getZ() >> CHUNK_SHIFT);
        if (dispatcher.isScanning(key)) return;
        cache.remove(key);
        dispatcher.queue(key);
    }

    public void tick() {
        if (mc.player == null || !isDimensionEnabled()) return;
        ticks++;
        if (ticks % DISPATCH_PERIOD == 0) {
            dispatcher.dispatch(mc.player.getChunkPos());
        }
        if (ticks % CLUSTER_PERIOD == 0) {
            lists.rebuildClusters(mc.player.getEyePos());
        }
        if (ticks % CAVE_AIR_PERIOD == 0) {
            lists.rebuildCaveAir(mc.player.getEyePos());
        }
        if (ticks >= EVICTION_PERIOD) {
            ticks = 0;
            evictFarChunks();
        }
    }

    private void evictFarChunks() {
        double range = Math.max(options.renderDistance.get(), options.caveAirRenderDistance.get());
        double maxChunks = range / BLOCKS_PER_CHUNK + EVICTION_MARGIN_CHUNKS;
        int evicted = cache.evictBeyond(mc.player.getChunkPos(), maxChunks);
        if (options.debug.get()) {
            ChatUtils.info("Evicted %d far chunks", evicted);
        }
    }

    private void scanChunk(long chunkKey) {
        if (mc.world == null) return;
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(chunkKey), ChunkPos.getPackedZ(chunkKey));
        if (chunk == null) return;
        ChunkResult result = scanner.scan(mc.world, chunk);
        if (result.isEmpty()) {
            cache.remove(chunkKey);
            return;
        }
        cache.put(chunkKey, result);
        announcer.announce(result);
    }
}
