package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.modules.regear.arealoader.modes.Circle;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.util.math.BlockPos;

final class AreaCircleSettings {
    final Setting<BlockPos> center;
    final Setting<Double> layerRadius;
    final Setting<Double> steps;
    final Setting<Circle.SpiralDirection> direction;

    private final AreaModeSettings modeSettings;

    AreaCircleSettings(SettingGroup group, AreaModeSettings modeSettings) {
        this.modeSettings = modeSettings;
        center = group.add(centerSetting());
        layerRadius = group.add(layerRadiusSetting());
        steps = group.add(stepsSetting());
        direction = group.add(directionSetting());
    }

    private Setting<BlockPos> centerSetting() {
        return new BlockPosSetting.Builder()
            .name("circle-center")
            .description("Center of the circle.")
            .defaultValue(BlockPos.ORIGIN)
            .visible(modeSettings.shownWithCoordinatesIn(AreaLoaderModes.Circle))
            .build();
    }

    private Setting<Double> layerRadiusSetting() {
        return new DoubleSetting.Builder()
            .name("circle-layer-radius")
            .description("Radius gained by every layer, in chunks.")
            .defaultValue(1.0)
            .min(1.0)
            .sliderRange(1.0, 34.0)
            .visible(modeSettings.shownIn(AreaLoaderModes.Circle))
            .build();
    }

    private Setting<Double> stepsSetting() {
        return new DoubleSetting.Builder()
            .name("circle-steps")
            .description("Number of steps in one full turn.")
            .defaultValue(64.0)
            .min(6.0)
            .sliderRange(6.0, 100.0)
            .visible(modeSettings.shownIn(AreaLoaderModes.Circle))
            .build();
    }

    private Setting<Circle.SpiralDirection> directionSetting() {
        return new EnumSetting.Builder<Circle.SpiralDirection>()
            .name("circle-direction")
            .description("Direction of the spiral.")
            .defaultValue(Circle.SpiralDirection.CLOCKWISE)
            .visible(modeSettings.shownIn(AreaLoaderModes.Circle))
            .build();
    }
}
