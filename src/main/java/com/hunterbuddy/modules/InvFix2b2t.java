package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class InvFix2b2t extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> fixGhostItems = sgGeneral.add(new BoolSetting.Builder()
        .name("fix-ghost-items")
        .description("Prevents ghost items from appearing when dragging items like shulker boxes, bundles, and filled maps.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> fixBundles = sgGeneral.add(new BoolSetting.Builder()
        .name("fix-bundles")
        .description("Fixes bundle contents being in reverse order on 2b2t, allowing you to select the correct item.")
        .defaultValue(true)
        .build()
    );

    public InvFix2b2t() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "2b2t-inv-fix", "Fixes ghost items and broken bundles on 2b2t.");
    }
}
