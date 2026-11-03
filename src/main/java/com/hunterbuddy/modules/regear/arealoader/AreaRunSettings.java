package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;

final class AreaRunSettings {
    final Setting<String> saveLocation;
    final Setting<Boolean> disconnectOnCompletion;
    final Setting<Double> boatFlySpeed;

    AreaRunSettings(SettingGroup group, AreaModeSettings modeSettings) {
        saveLocation = group.add(new StringSetting.Builder()
            .name("save-name")
            .description("Name of the save the progress is written to. Blank means default.")
            .defaultValue("default")
            .build());
        disconnectOnCompletion = group.add(new BoolSetting.Builder()
            .name("disconnect-on-completion")
            .description("Disconnects when the rectangle is finished.")
            .defaultValue(false)
            .visible(modeSettings.shownIn(AreaLoaderModes.Rectangle))
            .build());
        boatFlySpeed = group.add(new DoubleSetting.Builder()
            .name("boat-fly-speed")
            .description("Speed in blocks per second used for the time estimate.")
            .defaultValue(30.0)
            .min(1.0)
            .sliderRange(1.0, 100.0)
            .visible(modeSettings.shownIn(AreaLoaderModes.Rectangle))
            .build());
    }
}
