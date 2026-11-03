package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.List;

public final class GroundItemSettings {
    public final Setting<Boolean> itemNotify;
    public final Setting<List<Item>> trackedItems;
    public final Setting<Integer> itemCooldown;

    public GroundItemSettings(Settings settings) {
        SettingGroup group = settings.createGroup("Ground Items");
        itemNotify = group.add(new BoolSetting.Builder()
            .name("item-notify")
            .description("Alerts when a tracked item lies on the ground.")
            .defaultValue(false)
            .build());
        trackedItems = group.add(new ItemListSetting.Builder()
            .name("tracked-items")
            .description("Items that trigger an alert.")
            .defaultValue(
                Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS,
                Items.ENCHANTED_GOLDEN_APPLE, Items.TOTEM_OF_UNDYING, Items.ELYTRA,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
                Items.NETHERITE_BOOTS, Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE)
            .visible(itemNotify::get)
            .build());
        itemCooldown = group.add(new IntSetting.Builder()
            .name("item-cooldown")
            .description("Seconds before the same item can trigger another alert.")
            .defaultValue(30)
            .min(5)
            .sliderRange(5, 120)
            .visible(itemNotify::get)
            .build());
    }
}
