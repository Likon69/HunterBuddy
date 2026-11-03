package com.hunterbuddy.modules.flowesp;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

final class StorageOptions {
    final Setting<Integer> maxChunks;
    final Setting<Integer> evictDistance;
    final Setting<Integer> saveInterval;
    final Setting<Boolean> persist;
    final Setting<Double> minFlowRatio;
    final Setting<Integer> minSize;
    final Setting<Integer> maxSize;

    StorageOptions(Settings settings) {
        SettingGroup cache = settings.createGroup("Cache");
        maxChunks = cache.add(SettingFactory.number("max-chunks", "Maximum number of cached chunks per dimension.", 2000)
            .sliderRange(500, 10000).build());
        evictDistance = cache.add(SettingFactory.number("evict-distance", "Chunks farther than this many chunks are dropped.", 32)
            .sliderRange(16, 64).build());
        saveInterval = cache.add(SettingFactory.number("save-interval", "Seconds between automatic saves, 0 disables them.", 300)
            .sliderRange(0, 600).build());
        persist = cache.add(SettingFactory.flag("persist", "Keep the cache on disk between sessions.", true).build());
        SettingGroup filters = settings.createGroup("Filters");
        minFlowRatio = filters.add(SettingFactory.decimal("min-flow-ratio", "Minimum ratio of flowing blocks to source blocks.", 0.5)
            .sliderRange(0, 5).build());
        minSize = filters.add(SettingFactory.number("min-size", "Smallest number of fluid blocks a chunk may have.", 3)
            .sliderRange(1, 50).build());
        maxSize = filters.add(SettingFactory.number("max-size", "Largest number of fluid blocks a chunk may have.", 500)
            .sliderRange(50, 5000).build());
    }
}
