package com.hunterbuddy.modules.flowesp;

import meteordevelopment.meteorclient.settings.Settings;

public final class FlowOptions {
    final GeneralOptions general;
    final DimensionOptions dimensions;
    final StorageOptions storage;
    final AnimationOptions animation;

    public FlowOptions(Settings settings) {
        general = new GeneralOptions(settings);
        dimensions = new DimensionOptions(settings);
        storage = new StorageOptions(settings);
        animation = new AnimationOptions(settings);
    }
}
