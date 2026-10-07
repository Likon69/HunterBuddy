package com.hunterbuddy.modules.sorter.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hunterbuddy.modules.sorter.logic.ShulkerClassifier.Result;
import com.hunterbuddy.modules.sorter.logic.ShulkerClassifier.Slot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;


class ShulkerClassifierTest {
    private final CategoryRules rules = CategoryRules.defaults();

    private static List<Slot> fill(String id, int slots, int count) {
        List<Slot> list = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            list.add(new Slot(id, count));
        }
        return list;
    }

    @Test
    void exactIdBeatsPattern() {
        assertEquals("pvp", rules.categoryOf("minecraft:netherite_sword"));
        assertEquals("tools", rules.categoryOf("minecraft:iron_sword"));
        assertEquals("tools", rules.categoryOf("netherite_pickaxe"));
    }

    @Test
    void patternsAndFallback() {
        assertEquals("building", rules.categoryOf("minecraft:oak_planks"));
        assertEquals("shulkers", rules.categoryOf("minecraft:red_shulker_box"));
        assertEquals("shulkers", rules.categoryOf("minecraft:shulker_box"));
        assertEquals("valuables", rules.categoryOf("minecraft:netherite_upgrade_smithing_template"));
        assertEquals("redstone", rules.categoryOf("minecraft:powered_rail"));
        assertEquals(CategoryRules.FALLBACK, rules.categoryOf("minecraft:dead_bush"));
        assertEquals(CategoryRules.FALLBACK, rules.categoryOf("othermod:thing"));
    }

    @Test
    void pureShulker() {
        Result r = ShulkerClassifier.classify(fill("minecraft:end_crystal", 27, 64), rules, 0.8);
        assertEquals("pvp", r.category());
        assertEquals(1.0, r.purity(), 1e-9);
        assertFalse(r.isMixed());
    }

    @Test
    void slotsCountNotItems() {
        List<Slot> slots = fill("minecraft:totem_of_undying", 26, 1);
        slots.add(new Slot("minecraft:cobblestone", 64));
        Result r = ShulkerClassifier.classify(slots, rules, 0.8);
        assertEquals("pvp", r.category());
    }

    @Test
    void mixedBelowThreshold() {
        List<Slot> slots = fill("minecraft:end_crystal", 10, 64);
        slots.addAll(fill("minecraft:oak_log", 10, 64));
        slots.addAll(fill("minecraft:bread", 7, 64));
        Result r = ShulkerClassifier.classify(slots, rules, 0.8);
        assertTrue(r.isMixed());
        assertTrue(r.describe().startsWith("mixed ("));
    }

    @Test
    void emptyShulker() {
        Result r = ShulkerClassifier.classify(List.of(), rules, 0.8);
        assertTrue(r.empty());
        assertEquals(CategoryRules.SHULKERS, r.category());
    }

    @Test
    void glob() {
        assertTrue(SortCategory.glob("*_planks", "oak_planks"));
        assertTrue(SortCategory.glob("cooked_*", "cooked_beef"));
        assertTrue(SortCategory.glob("*rail", "rail"));
        assertFalse(SortCategory.glob("*_planks", "planks"));
        assertTrue(SortCategory.glob("minecraft:*_shulker_box", "minecraft:blue_shulker_box"));
    }
}
