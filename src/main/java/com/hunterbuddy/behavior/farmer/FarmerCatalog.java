package com.hunterbuddy.behavior.farmer;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class FarmerCatalog {
    private static final String RESOURCE = "assets/hunterbuddy/farmerbuddy/catalog.json";
    private final Map<String, FarmerProfile> profiles;
    private final String error;

    private FarmerCatalog(Map<String, FarmerProfile> profiles, String error) {
        this.profiles = profiles;
        this.error = error;
    }

    public static FarmerCatalog load() {
        try (InputStream in = FarmerCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) return new FarmerCatalog(Map.of(), RESOURCE + " is missing from resources");
            CatalogData data = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), CatalogData.class);
            if (data == null || data.profiles == null) return new FarmerCatalog(Map.of(), RESOURCE + " is empty or malformed");
            Map<String, FarmerProfile> resolved = new LinkedHashMap<>();
            for (ProfileData profile : data.profiles) resolved.put(profile.key, resolve(profile));
            return new FarmerCatalog(Map.copyOf(resolved), null);
        } catch (IOException | RuntimeException ex) {
            return new FarmerCatalog(Map.of(), RESOURCE + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private static FarmerProfile resolve(ProfileData data) {
        Set<Block> blocks = resolveBlocks(data.blocks);
        Set<Item> items = resolveItems(data.acceptedItems);
        return new FarmerProfile(data.key, data.label, blocks, items, data.tool, data.strategy,
            data.biomes == null ? List.of() : List.copyOf(data.biomes));
    }

    private static Set<Block> resolveBlocks(List<String> ids) {
        return (ids == null ? List.<String>of() : ids).stream().map(FarmerCatalog::block).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<Item> resolveItems(List<String> ids) {
        return (ids == null ? List.<String>of() : ids).stream().map(FarmerCatalog::item).collect(Collectors.toUnmodifiableSet());
    }

    private static Block block(String id) {
        Identifier identifier = Identifier.tryParse(id);
        Block block = identifier == null ? Blocks.AIR : Registries.BLOCK.get(identifier);
        if (block == Blocks.AIR) throw new IllegalArgumentException("unknown block in catalog: " + id);
        return block;
    }

    private static Item item(String id) {
        Identifier identifier = Identifier.tryParse(id);
        Item item = identifier == null ? Items.AIR : Registries.ITEM.get(identifier);
        if (item == Items.AIR) throw new IllegalArgumentException("unknown item in catalog: " + id);
        return item;
    }

    public Optional<FarmerProfile> profile(String key) {
        return Optional.ofNullable(key == null ? null : profiles.get(key));
    }

    public List<String> keys() {
        return List.copyOf(profiles.keySet());
    }

    public String error() {
        return error;
    }

    private static final class CatalogData {
        List<ProfileData> profiles;
    }

    private static final class ProfileData {
        String key;
        String label;
        List<String> blocks;
        List<String> acceptedItems;
        String tool;
        String strategy;
        List<String> biomes;
    }
}
