package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

public final class PlayerRangeSettings {
    public final Setting<Boolean> chatNotify;
    public final Setting<Boolean> ignoreFriends;
    public final Setting<Boolean> playerEnter;
    public final Setting<Boolean> playerLeave;
    public final Setting<Boolean> playerCoords;
    public final Setting<Boolean> playerEquipment;

    public PlayerRangeSettings(Settings settings) {
        SettingGroup general = settings.getDefaultGroup();
        SettingGroup players = settings.createGroup("Players");
        chatNotify = general.add(flag("chat-notify", "Prints the alerts in chat.", true));
        ignoreFriends = general.add(flag("ignore-friends", "Skips the players on your Meteor friends list.", false));
        playerEnter = players.add(flag("player-enter", "Alerts when a player enters visual range.", true));
        playerLeave = players.add(flag("player-leave", "Alerts when a player leaves visual range.", true));
        playerCoords = players.add(flag("player-coords", "Includes the coordinates of the player.", true));
        playerEquipment = players.add(new BoolSetting.Builder()
            .name("player-equipment")
            .description("Includes the equipment of the player.")
            .defaultValue(true)
            .visible(playerEnter::get)
            .build());
    }

    private static BoolSetting flag(String name, String description, boolean defaultValue) {
        return new BoolSetting.Builder().name(name).description(description).defaultValue(defaultValue).build();
    }
}
