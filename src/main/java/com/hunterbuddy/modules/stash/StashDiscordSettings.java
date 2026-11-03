package com.hunterbuddy.modules.stash;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;

public final class StashDiscordSettings {
    public final Setting<Boolean> enabled;
    public final Setting<String> webhookUrl;
    public final Setting<Boolean> includeCoordinates;
    public final Setting<Boolean> includeBreakdown;
    public final Setting<String> webhookUsername;

    public StashDiscordSettings(SettingGroup group) {
        enabled = group.add(new BoolSetting.Builder()
            .name("discord-webhook")
            .description("Sends new stashes to Discord.")
            .defaultValue(false)
            .build());
        webhookUrl = group.add(new StringSetting.Builder()
            .name("webhook-url")
            .description("Discord webhook URL.")
            .defaultValue("")
            .visible(enabled::get)
            .build());
        includeCoordinates = group.add(new BoolSetting.Builder()
            .name("include-coordinates")
            .description("Includes the coordinates in the message.")
            .defaultValue(false)
            .visible(enabled::get)
            .build());
        includeBreakdown = group.add(new BoolSetting.Builder()
            .name("include-breakdown")
            .description("Includes the count of every container type.")
            .defaultValue(true)
            .visible(enabled::get)
            .build());
        webhookUsername = group.add(new StringSetting.Builder()
            .name("webhook-username")
            .description("Name shown as the sender of the message.")
            .defaultValue("StashFinder")
            .visible(enabled::get)
            .build());
    }
}
