package com.hunterbuddy.chatdock;

import com.hunterbuddy.util.LegacyNames;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class DockMigrationTest {
    @Test
    void preservesPositionAndWindowSettingsWithoutChangingTheOldProfile() {
        NbtCompound old = new NbtCompound();
        old.putString("name", "log");
        old.putBoolean("active", true);
        NbtList general = new NbtList();
        general.add(number("dock-x", 0.73));
        general.add(number("dock-y", 0.28));
        general.add(number("scale", 1.3));
        general.add(number("text-scale", 0.9));
        NbtList colors = new NbtList();
        NbtCompound background = new NbtCompound();
        background.putString("name", "background");
        background.putInt("value", 12345);
        colors.add(background);
        NbtList groups = new NbtList();
        groups.add(group("General", general));
        groups.add(group("Colors", colors));
        NbtCompound settings = new NbtCompound();
        settings.put("groups", groups);
        old.put("settings", settings);
        NbtCompound original = old.copy();

        NbtCompound migrated = DockMigration.convert(old);
        assertEquals(original, old);
        assertEquals("hunterbuddy-menu", LegacyNames.module("log"));
        assertEquals("hunterbuddy-menu", migrated.getString("name", ""));
        assertTrue(migrated.getBoolean("active", false));
        NbtList result = migrated.getCompoundOrEmpty("settings").getListOrEmpty("groups");
        NbtCompound menu = result.getCompoundOrEmpty(0);
        NbtCompound log = result.getCompoundOrEmpty(1);
        assertEquals("Menu", menu.getString("name", ""));
        assertEquals("Log", log.getString("name", ""));
        assertEquals(0.73, find(menu, "dock-x").getDouble("value", 0));
        assertEquals(0.28, find(menu, "dock-y").getDouble("value", 0));
        assertEquals(1.3, find(menu, "scale").getDouble("value", 0));
        assertEquals(1.3, find(log, "window-scale").getDouble("value", 0));
        assertEquals(0.9, find(log, "text-scale").getDouble("value", 0));
        assertEquals(12345, find(menu, "background").getInt("value", 0));
        assertEquals(12345, find(log, "window-background").getInt("value", 0));
        assertFalse(log.getListOrEmpty("settings").stream()
            .anyMatch(value -> value instanceof NbtCompound setting && setting.getString("name", "").equals("dock-x")));
    }

    @Test
    void absentSettingsKeepAutomaticPlacementAndAllNewDefaults() {
        NbtCompound old = new NbtCompound();
        old.putString("name", "log");
        NbtCompound converted = DockMigration.convert(old);
        assertFalse(converted.getBoolean("active", false));
        for (var group : converted.getCompoundOrEmpty("settings").getListOrEmpty("groups"))
            assertTrue(((NbtCompound) group).getListOrEmpty("settings").isEmpty());
    }

    private static NbtCompound number(String name, double value) {
        NbtCompound setting = new NbtCompound();
        setting.putString("name", name);
        setting.putDouble("value", value);
        return setting;
    }

    private static NbtCompound group(String name, NbtList settings) {
        NbtCompound group = new NbtCompound();
        group.putString("name", name);
        group.put("settings", settings);
        return group;
    }

    private static NbtCompound find(NbtCompound group, String name) {
        return group.getListOrEmpty("settings").stream().filter(value -> value instanceof NbtCompound setting
            && setting.getString("name", "").equals(name)).map(NbtCompound.class::cast).findFirst().orElseThrow();
    }
}
