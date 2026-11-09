package com.hunterbuddy.chatdock;

import meteordevelopment.meteorclient.settings.Settings;
import java.util.HashSet;
import com.hunterbuddy.chatdock.entries.LogDockEntry;
import net.minecraft.client.gui.DrawContext;

public final class DockRegistry {
    private final DockEntry[] entries = { new LogDockEntry() };

    public DockRegistry() {
        var ids = new HashSet<String>();
        for (DockEntry entry : entries) {
            if (!ids.add(entry.id())) throw new IllegalStateException("Duplicate dock entry: " + entry.id());
        }
    }

    public DockEntry[] entries() {
        return entries;
    }

    public void configure(Settings settings) {
        for (DockEntry entry : entries()) entry.configure(settings);
    }

    public void activate() { for (DockEntry entry : entries()) entry.activate(); }

    public void deactivate() { for (DockEntry entry : entries()) entry.deactivate(); }

    public void shutdown() { for (DockEntry entry : entries()) entry.shutdown(); }

    public void fontChanged() { for (DockEntry entry : entries()) entry.fontChanged(); }

    public void render(DrawContext context, boolean chat) {
        DockEntry[] all = entries();
        for (int i = 0; i < all.length; i++) if (all[i].visible()) all[i].render(context, chat);
    }

    public boolean click(double x, double y, int button) {
        DockEntry[] all = entries();
        for (int i = all.length - 1; i >= 0; i--) if (all[i].visible() && all[i].click(x, y, button)) return true;
        return false;
    }

    public boolean scroll(double x, double y, double amount) {
        DockEntry[] all = entries();
        for (int i = all.length - 1; i >= 0; i--) if (all[i].visible() && all[i].scroll(x, y, amount)) return true;
        return false;
    }

    public boolean key(int key, int modifiers) {
        DockEntry[] all = entries();
        for (int i = all.length - 1; i >= 0; i--) if (all[i].visible() && all[i].key(key, modifiers)) return true;
        return false;
    }

    public boolean inputFocused() {
        DockEntry[] all = entries();
        for (int i = 0; i < all.length; i++) if (all[i].visible() && all[i].inputFocused()) return true;
        return false;
    }

    public void closed() {
        for (int i = 0; i < entries.length; i++) entries[i].closed();
    }
}
