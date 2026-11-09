package com.hunterbuddy.chatdock;

import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.DrawContext;

public interface DockEntry {
    void configure(Settings settings);
    default void activate() { }
    default void deactivate() { }
    default void shutdown() { }
    default void fontChanged() { }
    String id();
    String label();
    boolean visible();
    boolean selected();
    void toggle();
    void icon(HudRenderer renderer, double x, double y, double size, Color color);
    void render(DrawContext context, boolean chat);
    boolean click(double x, double y, int button);
    boolean scroll(double x, double y, double amount);
    boolean key(int key, int modifiers);
    boolean inputFocused();
    void closed();
}
