package com.hunterbuddy.modules.flowesp;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

final class DimensionOptions {
    final Setting<Boolean> netherEnabled;
    final Setting<Integer> netherMinY;
    final Setting<Integer> netherRoofY;
    final Setting<Boolean> overworldEnabled;
    final Setting<Integer> overworldMinY;
    final Setting<Integer> overworldMaxY;
    final Setting<Boolean> detectLava;

    DimensionOptions(Settings settings) {
        SettingGroup nether = settings.createGroup("Nether");
        netherEnabled = nether.add(SettingFactory.flag("enabled", "Scan the Nether for flowing lava.", true).build());
        netherMinY = nether.add(SettingFactory.number("min-y", "Lava below this level is ignored.", 32).sliderRange(0, 127).build());
        netherRoofY = nether.add(SettingFactory.number("roof-y", "Lava at or above this level counts as roof lava.", 120)
            .sliderRange(100, 127).build());
        SettingGroup overworld = settings.createGroup("Overworld");
        overworldEnabled = overworld.add(SettingFactory.flag("enabled", "Scan the Overworld for flowing water.", true).build());
        overworldMinY = overworld.add(SettingFactory.number("min-y", "Lowest Y level to scan.", 0).sliderRange(-64, 128).build());
        overworldMaxY = overworld.add(SettingFactory.number("max-y", "Highest Y level to scan.", 62).sliderRange(0, 320).build());
        detectLava = overworld.add(SettingFactory.flag("detect-lava", "Also detect flowing lava in the Overworld.", true).build());
    }
}
