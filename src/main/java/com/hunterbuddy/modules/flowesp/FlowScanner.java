package com.hunterbuddy.modules.flowesp;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

final class FlowScanner {
    private static final int CHUNK_SIZE = 16;

    private final FlowOptions options;
    private final ScanRules netherRules;
    private final ScanRules overworldRules;

    FlowScanner(FlowOptions options) {
        this.options = options;
        this.netherRules = new NetherRules(options.dimensions);
        this.overworldRules = new OverworldRules(options.dimensions);
    }

    boolean scansDimension(RegistryKey<World> dimension) {
        if (dimension == World.NETHER) return options.dimensions.netherEnabled.get();
        return dimension == World.OVERWORLD && options.dimensions.overworldEnabled.get();
    }

    FlowChunk scan(WorldChunk chunk, RegistryKey<World> dimension) {
        ScanRules rules = dimension == World.NETHER ? netherRules : overworldRules;
        ColumnWalker walker = new ColumnWalker(chunk, rules, options.animation.tipFluidLevel.get());
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                walker.walk(x, z);
            }
        }
        if (walker.columns().isEmpty()) return null;
        FlowChunk.FluidCounts counts = new FlowChunk.FluidCounts(walker.sources(), walker.flows());
        return new FlowChunk(chunk.getPos(), counts, walker.columns(), System.currentTimeMillis());
    }

    boolean shouldCache(FlowChunk chunk) {
        StorageOptions storage = options.storage;
        if (chunk.total() < storage.minSize.get() || chunk.total() > storage.maxSize.get()) return false;
        return chunk.hasRoofColumn() || chunk.flowRatio() >= storage.minFlowRatio.get();
    }
}
