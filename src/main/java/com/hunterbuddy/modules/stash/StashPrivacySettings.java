package com.hunterbuddy.modules.stash;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.misc.Keybind;

public final class StashPrivacySettings {
    public final Setting<Boolean> streamerMode;
    public final Setting<Boolean> hideCoordinates;
    public final Setting<Boolean> noCoordChat;
    public final Setting<Keybind> openCoordinateList;

    public StashPrivacySettings(SettingGroup group) {
        streamerMode = group.add(new BoolSetting.Builder()
            .name("streamer-mode")
            .description("Censors coordinates in the widget and the list. Copy and go still use the real values.")
            .defaultValue(false)
            .build());
        hideCoordinates = group.add(new BoolSetting.Builder()
            .name("hide-coordinates")
            .description("Hides the coordinates in the main widget.")
            .defaultValue(true)
            .build());
        noCoordChat = group.add(new BoolSetting.Builder()
            .name("no-coord-chat")
            .description("Keeps the coordinates out of the chat messages.")
            .defaultValue(true)
            .build());
        openCoordinateList = group.add(new KeybindSetting.Builder()
            .name("open-coordinate-list")
            .description("Opens the coordinate list.")
            .defaultValue(Keybind.none())
            .build());
    }

    public boolean hidesCoordinatesInChat() {
        return noCoordChat.get() || streamerMode.get();
    }
}
