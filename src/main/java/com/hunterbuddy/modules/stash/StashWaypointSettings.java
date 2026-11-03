package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.regear.util.MapUtil;
import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class StashWaypointSettings {
    public final Setting<Boolean> addWaypoints;
    public final Setting<Boolean> temporaryWaypoints;
    public final Setting<Boolean> useSymbols;
    public final Setting<Boolean> showQuantity;
    public final Setting<MapUtil.WpColor> waypointColor;

    public StashWaypointSettings(SettingGroup group) {
        addWaypoints = group.add(new BoolSetting.Builder()
            .name("add-waypoints")
            .description("Adds Xaero waypoints for the found stashes.")
            .defaultValue(false)
            .visible(() -> Utils.XAERO_AVAILABLE)
            .build());
        IVisible underWaypoints = () -> Utils.XAERO_AVAILABLE && addWaypoints.get();
        temporaryWaypoints = group.add(new BoolSetting.Builder()
            .name("temporary-waypoints")
            .description("Makes the waypoints temporary.")
            .defaultValue(true)
            .visible(underWaypoints)
            .build());
        useSymbols = group.add(new BoolSetting.Builder()
            .name("use-symbols")
            .description("Names the waypoints with symbols of the stash content.")
            .defaultValue(true)
            .visible(underWaypoints)
            .build());
        showQuantity = group.add(new BoolSetting.Builder()
            .name("show-quantity")
            .description("Adds the total to the waypoint name.")
            .defaultValue(true)
            .visible(underWaypoints)
            .build());
        waypointColor = group.add(new EnumSetting.Builder<MapUtil.WpColor>()
            .name("waypoint-color")
            .description("Color of the waypoints.")
            .defaultValue(MapUtil.WpColor.Gold)
            .visible(underWaypoints)
            .build());
    }
}
