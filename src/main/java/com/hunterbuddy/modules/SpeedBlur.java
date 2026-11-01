package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class SpeedBlur extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> strength = sgGeneral.add(new DoubleSetting.Builder()
        .name("strength")
        .description("Multiplier on the streak length. The blur itself grows from nothing at 0.6 blocks per tick to its full size at 3 blocks per tick, and never shows on the ground.")
        .defaultValue(1.0).min(0.0).max(3.0).sliderRange(0.0, 3.0)
        .build());

    public SpeedBlur() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "speed-blur",
            "Radial motion blur that grows with your real speed and stays sharp around the point you are flying towards. Only the world is blurred, never the HUD or screens.");
    }

    public double strength() {
        return strength.get();
    }
}
