package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class StashGeneralSettings {
    public final Setting<Integer> minimumStorageCount;
    public final Setting<Integer> minimumDistance;
    public final Setting<Boolean> notifications;
    public final Setting<StashFinder.NotificationMode> notificationMode;
    public final Setting<Integer> clusterRadius;

    public StashGeneralSettings(SettingGroup group) {
        minimumStorageCount = group.add(minimumStorageSetting());
        minimumDistance = group.add(minimumDistanceSetting());
        notifications = group.add(notificationsSetting());
        notificationMode = group.add(notificationModeSetting());
        clusterRadius = group.add(clusterRadiusSetting());
    }

    private static Setting<Integer> minimumStorageSetting() {
        return new IntSetting.Builder()
            .name("minimum-storage-count")
            .description("Minimum number of storage blocks in a chunk to record it.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 20)
            .build();
    }

    private static Setting<Integer> minimumDistanceSetting() {
        return new IntSetting.Builder()
            .name("minimum-distance")
            .description("Minimum distance from spawn (0, 0) to record a chunk.")
            .defaultValue(5000)
            .min(0)
            .sliderRange(0, 50000)
            .build();
    }

    private static Setting<Boolean> notificationsSetting() {
        return new BoolSetting.Builder()
            .name("notifications")
            .description("Announces new stashes.")
            .defaultValue(true)
            .build();
    }

    private Setting<StashFinder.NotificationMode> notificationModeSetting() {
        return new EnumSetting.Builder<StashFinder.NotificationMode>()
            .name("notification-mode")
            .description("How new stashes are announced.")
            .defaultValue(StashFinder.NotificationMode.All)
            .visible(notifications::get)
            .build();
    }

    private static Setting<Integer> clusterRadiusSetting() {
        return new IntSetting.Builder()
            .name("cluster-radius")
            .description("Chunks merged into one waypoint. 5 chunks is 80 blocks.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 32)
            .build();
    }
}
