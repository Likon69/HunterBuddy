package com.hunterbuddy.modules.sorter.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public class CategoryRules {

    public static final String FALLBACK = "random";

    public static final String MIXED = "mixed";

    public static final String SHULKERS = "shulkers";

    public List<SortCategory> categories = new ArrayList<>();

    public CategoryRules() {
    }

    public CategoryRules(List<SortCategory> categories) {
        this.categories = new ArrayList<>(categories);
    }

    public String categoryOf(String itemId) {
        List<SortCategory> ordered = ordered();
        for (SortCategory category : ordered) {
            if (category.matchesExact(itemId)) {
                return category.name;
            }
        }
        for (SortCategory category : ordered) {
            if (category.matchesPattern(itemId)) {
                return category.name;
            }
        }
        return FALLBACK;
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (SortCategory category : ordered()) {
            names.add(category.name);
        }
        if (!names.contains(FALLBACK)) {
            names.add(FALLBACK);
        }
        names.add(MIXED);
        return names;
    }

    public boolean isKnown(String name) {
        return names().contains(name);
    }

    private List<SortCategory> ordered() {
        List<SortCategory> copy = new ArrayList<>(categories);
        copy.sort(Comparator.comparingInt(c -> c.priority));
        return copy;
    }


    public static CategoryRules defaults() {
        List<SortCategory> list = new ArrayList<>();

        list.add(new SortCategory("pvp", 10).items(
            "end_crystal", "obsidian", "respawn_anchor", "glowstone",
            "totem_of_undying", "enchanted_golden_apple", "golden_apple",
            "ender_pearl", "experience_bottle", "chorus_fruit",
            "potion", "splash_potion", "lingering_potion", "tipped_arrow", "spectral_arrow", "arrow",
            "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
            "netherite_sword", "netherite_axe", "diamond_helmet", "diamond_chestplate",
            "diamond_leggings", "diamond_boots", "diamond_sword", "mace", "wind_charge",
            "bow", "crossbow", "trident", "shield", "cobweb", "anvil", "chipped_anvil", "damaged_anvil"));

        list.add(new SortCategory("flight", 20).items(
            "elytra", "firework_rocket", "phantom_membrane"));

        list.add(new SortCategory("valuables", 30).items(
            "diamond", "diamond_block", "netherite_ingot", "netherite_scrap", "netherite_block",
            "ancient_debris", "emerald", "emerald_block", "gold_ingot", "gold_block",
            "iron_ingot", "iron_block", "lapis_block", "beacon", "nether_star", "dragon_egg",
            "dragon_head", "enchanted_book", "heavy_core", "trial_key", "ominous_trial_key",
            "ender_chest", "conduit", "heart_of_the_sea", "music_disc_pigstep")
            .patterns("*_smithing_template", "music_disc_*"));

        list.add(new SortCategory(SHULKERS, 35).patterns("shulker_box", "*_shulker_box"));

        list.add(new SortCategory("tools", 40).items(
            "shears", "flint_and_steel", "fishing_rod", "carrot_on_a_stick", "spyglass",
            "compass", "recovery_compass", "clock", "lead", "name_tag", "bucket", "water_bucket",
            "lava_bucket", "brush")
            .patterns("*_pickaxe", "*_shovel", "*_axe", "*_hoe", "*_sword",
                "*_helmet", "*_chestplate", "*_leggings", "*_boots"));

        list.add(new SortCategory("redstone", 50).items(
            "redstone", "redstone_block", "redstone_torch", "repeater", "comparator", "observer",
            "piston", "sticky_piston", "hopper", "dropper", "dispenser", "lever", "target",
            "daylight_detector", "tripwire_hook", "slime_block", "honey_block", "tnt", "crafter",
            "note_block", "redstone_lamp")
            .patterns("*_pressure_plate", "*_button", "*rail"));

        list.add(new SortCategory("food", 60).items(
            "bread", "golden_carrot", "apple", "carrot", "baked_potato", "pumpkin_pie", "cake",
            "melon_slice", "sweet_berries", "glow_berries", "dried_kelp", "cookie", "honey_bottle")
            .patterns("cooked_*", "*_stew", "*_soup"));

        list.add(new SortCategory("building", 70).items(
            "stone", "cobblestone", "deepslate", "cobbled_deepslate", "netherrack", "dirt",
            "grass_block", "sand", "red_sand", "gravel", "end_stone", "quartz_block", "glass",
            "smooth_stone", "andesite", "diorite", "granite", "blackstone", "basalt", "tuff",
            "calcite", "clay", "snow_block", "ice", "packed_ice", "blue_ice", "sandstone",
            "purpur_block", "prismarine", "sea_lantern", "bookshelf", "chest", "barrel",
            "crafting_table", "furnace", "torch", "lantern", "ladder", "scaffolding")
            .patterns("*_planks", "*_log", "*_wood", "*_stairs", "*_slab", "*_wall", "*_fence",
                "*_fence_gate", "*_door", "*_trapdoor", "*_glass", "*_glass_pane", "*_concrete",
                "*_concrete_powder", "*_terracotta", "*_wool", "*_carpet", "*_bricks", "*_leaves"));

        return new CategoryRules(list);
    }
}
