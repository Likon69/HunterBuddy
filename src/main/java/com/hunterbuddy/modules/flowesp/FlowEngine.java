package com.hunterbuddy.modules.flowesp;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public final class FlowEngine {
    private static final int PALETTE_PERIOD = 100;
    private static final int EVICTION_PERIOD = 200;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final FlowOptions options;
    private final FlowScanner scanner;
    private final FlowPalette palette = new FlowPalette();
    private final FlowRenderer renderer;
    private FlowCache cache;
    private FlowPersistence persistence;
    private int ticks;

    public FlowEngine(FlowOptions options) {
        this.options = options;
        this.scanner = new FlowScanner(options);
        this.renderer = new FlowRenderer(options, palette);
    }

    public void start() {
        cache = new FlowCache(options.storage.maxChunks.get());
        persistence = new FlowPersistence(cache, options.storage, this::debugMessage);
        palette.refresh(options.general);
        renderer.reset();
        ticks = 0;
        persistence.start();
    }

    public void stop() {
        if (persistence != null) {
            persistence.stop();
        }
        if (cache != null) {
            cache.clear();
        }
        renderer.reset();
        cache = null;
        persistence = null;
    }

    public void clearCache() {
        if (cache == null) return;
        cache.clear();
        cache.markDirty();
        ChatUtils.info("Cache cleared");
    }

    public void forceSave() {
        if (cache == null) return;
        cache.markDirty();
        persistence.saveIfDirty();
    }

    public String stats() {
        if (cache == null) return "OW: 0 | Nether: 0";
        return "OW: " + cache.overworld().size() + " | Nether: " + cache.nether().size();
    }

    public void onChunkData(WorldChunk chunk) {
        if (mc.world == null || cache == null) return;
        RegistryKey<World> dimension = mc.world.getRegistryKey();
        long key = chunk.getPos().toLong();
        if (!scanner.scansDimension(dimension) || cache.of(dimension).isScanned(key) || !cache.beginScan(key)) return;
        try {
            storeIfInteresting(scanner.scan(chunk, dimension), dimension);
        } finally {
            cache.endScan(key);
        }
    }

    private void storeIfInteresting(FlowChunk scanned, RegistryKey<World> dimension) {
        if (scanned == null || !scanner.shouldCache(scanned)) return;
        cache.of(dimension).add(scanned);
        cache.markDirty();
    }

    public void tick() {
        if (mc.world == null || mc.player == null || cache == null) return;
        ticks++;
        if (ticks % PALETTE_PERIOD == 0) {
            palette.refresh(options.general);
        }
        if (ticks % EVICTION_PERIOD == 0) {
            cache.of(mc.world.getRegistryKey()).evictBeyond(mc.player.getChunkPos(), options.storage.evictDistance.get());
        }
    }

    public void render(Render3DEvent event) {
        if (cache != null) {
            renderer.render(event, cache);
        }
    }

    private void debugMessage(String message) {
        if (options.general.debug.get()) {
            mc.execute(() -> ChatUtils.info("%s", message));
        }
    }
}
