package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.StringSetting;

public final class DiscordSettings {
    public final Setting<Boolean> enabled;
    public final Setting<String> webhookUrl;
    public final Setting<Boolean> players;
    public final Setting<Boolean> items;
    public final Setting<String> pingId;

    private final SettingGroup group;
    private final GroundItemSettings itemSettings;

    public DiscordSettings(Settings settings, GroundItemSettings itemSettings) {
        this.itemSettings = itemSettings;
        group = settings.createGroup("Discord");
        enabled = group.add(enabledSetting());
        webhookUrl = group.add(textUnderMaster("webhook-url", "Discord webhook URL."));
        players = group.add(switchUnderMaster("discord-players", "Sends the player alerts to Discord."));
        items = group.add(itemsSetting());
        pingId = group.add(textUnderMaster("ping-id", "Discord user id to ping. Blank pings nobody."));
    }

    SettingGroup group() {
        return group;
    }

    BoolSetting switchUnderMaster(String name, String description) {
        return new BoolSetting.Builder()
            .name(name)
            .description(description)
            .defaultValue(true)
            .visible(enabled::get)
            .build();
    }

    private static Setting<Boolean> enabledSetting() {
        return new BoolSetting.Builder()
            .name("discord-webhook")
            .description("Master switch for the Discord alerts.")
            .defaultValue(false)
            .build();
    }

    private Setting<String> textUnderMaster(String name, String description) {
        return new StringSetting.Builder()
            .name(name)
            .description(description)
            .defaultValue("")
            .visible(enabled::get)
            .build();
    }

    private Setting<Boolean> itemsSetting() {
        return new BoolSetting.Builder()
            .name("discord-items")
            .description("Sends the ground item alerts to Discord.")
            .defaultValue(true)
            .visible(() -> enabled.get() && itemSettings.itemNotify.get())
            .build();
    }
}
