package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;

public class GuiTheme extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<String> theme = sgGeneral.add(new StringSetting.Builder()
        .name("theme")
        .description("HunterBuddy GUI theme to apply. Valid values: Dark, Snowy, Lambda, Stardust, Midnight, Phosphor, Monochrome.")
        .defaultValue("Dark")
        .build()
    );

    public GuiTheme() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "gui-theme", "Selects the HunterBuddy GUI theme on enable.");
    }

    @Override
    public void onActivate() {
        GuiThemes.select(theme.get());
    }
}
