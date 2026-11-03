package com.hunterbuddy.hud.elytrahelper;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class HelperSections {
    public final Setting<Boolean> showTitle;
    public final Setting<Boolean> showSession;
    public final Setting<Boolean> showAverageSpeed;
    public final Setting<Boolean> showPeakSpeed;
    public final Setting<Boolean> showSpeedKmh;
    public final Setting<Boolean> showSpeed;

    public HelperSections(SettingGroup group) {
        showTitle = group.add(flag("show-title", "Shows the title line."));
        showSession = group.add(flag("show-session", "Shows the session statistics."));
        showAverageSpeed = group.add(underSession("show-average-speed", "Shows the average speed of the session."));
        showPeakSpeed = group.add(underSession("show-peak-speed", "Shows the peak speed of the session."));
        showSpeedKmh = group.add(flag("show-speed-kmh", "Appends km/h to the speeds in blocks per second."));
        showSpeed = group.add(flag("show-speed", "Shows the current speed."));
    }

    private static BoolSetting flag(String name, String description) {
        return new BoolSetting.Builder().name(name).description(description).defaultValue(true).build();
    }

    private BoolSetting underSession(String name, String description) {
        return new BoolSetting.Builder()
            .name(name).description(description).defaultValue(true).visible(showSession::get).build();
    }
}
