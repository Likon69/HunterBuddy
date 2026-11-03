package com.hunterbuddy.modules.waypointfollow;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;

public final class XaeroMapFocus {
    private static final String MAP_SCREEN_CLASS = "xaero.map.gui.GuiMap";
    private static final String TEXT_FIELD_MARK = "TextField";

    private XaeroMapFocus() {
    }

    public static boolean textFieldFocused() {
        Screen screen = MeteorClient.mc.currentScreen;
        if (screen == null || !MAP_SCREEN_CLASS.equals(screen.getClass().getName())) return false;
        Element focused = screen.getFocused();
        return focused instanceof TextFieldWidget
            || (focused != null && focused.getClass().getName().contains(TEXT_FIELD_MARK));
    }
}
