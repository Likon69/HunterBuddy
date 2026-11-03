package com.hunterbuddy.modules.vanityesp;

import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

final class AlertOptions {
    private static final double MAX_VOLUME = 10.0;
    private static final double SLIDER_MAX_VOLUME = 5.0;

    private final SettingGroup group;
    private final Setting<Boolean> feature;
    Setting<Boolean> chat;
    Setting<Boolean> showCoords;
    Setting<Boolean> sound;
    Setting<Double> volume;
    Setting<Boolean> addWaypoints;
    Setting<Boolean> temporaryWaypoints;

    AlertOptions(SettingGroup group, Setting<Boolean> feature) {
        this.group = group;
        this.feature = feature;
    }

    void addChat(boolean showCoordsDefault) {
        chat = group.add(new BoolSetting.Builder().name("chat-notification").description("Send a chat message when something is found.")
            .defaultValue(true).visible(feature::get).build());
        showCoords = group.add(new BoolSetting.Builder().name("show-coords").description("Include the coordinates in the chat message.")
            .defaultValue(showCoordsDefault).visible(() -> feature.get() && chat.get()).build());
    }

    void addSound() {
        sound = group.add(new BoolSetting.Builder().name("sound-notification").description("Play a sound when something is found.")
            .defaultValue(true).visible(feature::get).build());
        volume = group.add(new DoubleSetting.Builder().name("volume").defaultValue(1.0).min(0).max(MAX_VOLUME)
            .sliderRange(0, SLIDER_MAX_VOLUME).visible(() -> feature.get() && sound.get()).build());
    }

    void addWaypoints(boolean enabledByDefault, boolean temporaryByDefault) {
        addWaypoints = group.add(new BoolSetting.Builder().name("add-waypoints").description("Add a Xaero waypoint for each find.")
            .defaultValue(enabledByDefault).visible(() -> feature.get() && Utils.XAERO_AVAILABLE).build());
        temporaryWaypoints = group.add(new BoolSetting.Builder().name("temporary-waypoints")
            .description("Make the waypoints temporary.").defaultValue(temporaryByDefault)
            .visible(() -> feature.get() && Utils.XAERO_AVAILABLE && addWaypoints.get()).build());
    }
}
