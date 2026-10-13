package com.hunterbuddy.behavior.farmer;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

public record FarmerProfile(String key, String label, Set<Block> blocks, Set<Item> acceptedItems, String tool,
                            String strategy, List<String> biomes) {
    public FarmerProfile {
        Objects.requireNonNull(key);
        Objects.requireNonNull(label);
        blocks = Set.copyOf(blocks);
        acceptedItems = Set.copyOf(acceptedItems);
        Objects.requireNonNull(tool);
        Objects.requireNonNull(strategy);
        biomes = List.copyOf(biomes);
    }
}
