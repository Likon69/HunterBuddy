package com.hunterbuddy.chatdock;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

public final class DockMigration {
    private DockMigration() {
    }

    public static NbtCompound convert(NbtCompound legacy) {
        NbtCompound result = legacy.copy();
        result.putString("name", "hunterbuddy-menu");
        NbtList menu = new NbtList();
        NbtList log = new NbtList();
        for (NbtElement element : legacy.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(element instanceof NbtCompound group)) continue;
            for (NbtElement value : group.getListOrEmpty("settings")) {
                if (!(value instanceof NbtCompound setting)) continue;
                String name = setting.getString("name", "");
                if (name.equals("dock-x") || name.equals("dock-y") || name.equals("menu-key")) menu.add(setting.copy());
                else if (name.equals("scale")) {
                    menu.add(setting.copy());
                    NbtCompound window = setting.copy();
                    window.putString("name", "window-scale");
                    log.add(window);
                } else {
                    NbtCompound journal = setting.copy();
                    if (name.equals("background")) journal.putString("name", "window-background");
                    if (name.equals("accent")) journal.putString("name", "log-accent");
                    log.add(journal);
                    if (name.equals("background") || name.equals("accent")) menu.add(setting.copy());
                }
            }
        }
        NbtList groups = new NbtList();
        groups.add(group("Menu", menu));
        groups.add(group("Log", log));
        NbtCompound settings = new NbtCompound();
        settings.put("groups", groups);
        result.put("settings", settings);
        return result;
    }

    private static NbtCompound group(String name, NbtList settings) {
        NbtCompound group = new NbtCompound();
        group.putString("name", name);
        group.put("settings", settings);
        return group;
    }
}
