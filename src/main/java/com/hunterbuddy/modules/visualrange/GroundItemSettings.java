package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.List;

public final class GroundItemSettings {
    public final Setting<Boolean> itemNotify;
    public final Setting<Boolean> itemBeam;
    public final Setting<List<Item>> trackedItems;
    public final Setting<Integer> itemCooldown;
    public final Setting<SettingColor> beamColor;
    public final Setting<Integer> beamHeight;
    public final Setting<Boolean> throughWalls;
    public final Setting<Boolean> beamLabels;

    public GroundItemSettings(Settings settings) {
        SettingGroup group = settings.createGroup("Ground Items");
        itemNotify = group.add(new BoolSetting.Builder()
            .name("item-notify")
            .description("Alerts when a tracked item lies on the ground.")
            .defaultValue(false)
            .build());
        itemBeam = group.add(new BoolSetting.Builder()
            .name("item-beam")
            .description("Draws a beam above tracked items on the ground.")
            .defaultValue(true)
            .build());
        trackedItems = group.add(new ItemListSetting.Builder()
            .name("tracked-items")
            .description("Items that trigger an alert or a beam.")
            .defaultValue(
                Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS,
                Items.ENCHANTED_GOLDEN_APPLE, Items.TOTEM_OF_UNDYING, Items.ELYTRA,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
                Items.NETHERITE_BOOTS, Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE)
            .visible(() -> itemNotify.get() || itemBeam.get())
            .build());
        itemCooldown = group.add(new IntSetting.Builder()
            .name("item-cooldown")
            .description("Seconds before the same item can trigger another alert.")
            .defaultValue(30)
            .min(5)
            .sliderRange(5, 120)
            .visible(itemNotify::get)
            .build());
        beamColor = group.add(new ColorSetting.Builder()
            .name("beam-color")
            .description("Color of the beam and the marker cube.")
            .defaultValue(new SettingColor(255, 60, 200, 200))
            .visible(itemBeam::get)
            .build());
        beamHeight = group.add(new IntSetting.Builder()
            .name("beam-height")
            .description("Beam height in blocks above the item.")
            .defaultValue(128)
            .min(1)
            .sliderRange(16, 320)
            .visible(itemBeam::get)
            .build());
        throughWalls = group.add(new BoolSetting.Builder()
            .name("through-walls")
            .description("Beams show through terrain.")
            .defaultValue(true)
            .visible(itemBeam::get)
            .build());
        beamLabels = group.add(new BoolSetting.Builder()
            .name("beam-labels")
            .description("Shows name, count and distance of the closest items.")
            .defaultValue(true)
            .visible(itemBeam::get)
            .build());
    }
}
