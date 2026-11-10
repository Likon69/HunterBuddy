package com.hunterbuddy.hud;

import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.tabs.builtin.HudTab;
import meteordevelopment.meteorclient.systems.hud.screens.AddHudElementScreen;
import meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen;
import meteordevelopment.meteorclient.systems.hud.screens.HudElementPresetsScreen;
import meteordevelopment.meteorclient.systems.hud.screens.HudElementScreen;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public final class HudEditing {
    private HudEditing() {
    }

    public static boolean active() {
        if (!Utils.canUpdate() || HudEditorScreen.isOpen()) return true;
        Screen screen = MinecraftClient.getInstance().currentScreen;
        for (int depth = 0; screen != null && depth < 16; depth++) {
            if (isHudEditorScreen(screen)) return true;
            screen = screen instanceof WidgetScreen widget ? widget.parent : null;
        }
        return false;
    }

    private static boolean isHudEditorScreen(Screen screen) {
        return screen instanceof HudElementScreen
            || screen instanceof HudEditorScreen
            || screen instanceof AddHudElementScreen
            || screen instanceof HudElementPresetsScreen
            || screen instanceof HudTab.HudScreen;
    }
}
