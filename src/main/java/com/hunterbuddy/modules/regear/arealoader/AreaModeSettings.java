package com.hunterbuddy.modules.regear.arealoader;

import java.util.function.Consumer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

final class AreaModeSettings {
    final Setting<Boolean> hideCoordinates;
    final Setting<AreaLoaderModes> mode;

    AreaModeSettings(SettingGroup group, Consumer<AreaLoaderModes> onActivated, Consumer<AreaLoaderModes> onChanged) {
        hideCoordinates = group.add(new BoolSetting.Builder()
            .name("hide-coordinates")
            .description("Prints coordinates as hidden in chat and hides the coordinate settings.")
            .defaultValue(false)
            .build());
        mode = group.add(new EnumSetting.Builder<AreaLoaderModes>()
            .name("mode")
            .description("How the chunks are loaded.")
            .defaultValue(AreaLoaderModes.Rectangle)
            .onModuleActivated(setting -> onActivated.accept(setting.get()))
            .onChanged(onChanged)
            .build());
    }

    IVisible shownIn(AreaLoaderModes wanted) {
        return () -> mode.get() == wanted;
    }

    IVisible shownWithCoordinatesIn(AreaLoaderModes wanted) {
        return () -> !hideCoordinates.get() && mode.get() == wanted;
    }
}
