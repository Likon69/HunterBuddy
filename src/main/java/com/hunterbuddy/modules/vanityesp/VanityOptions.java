package com.hunterbuddy.modules.vanityesp;

import com.hunterbuddy.modules.vanityesp.FeatureStyle.Defaults;
import com.hunterbuddy.modules.vanityesp.FeatureStyle.TracerColorOrder;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class VanityOptions {
    private final Settings settings;
    Setting<Boolean> mapFrames;
    Setting<Boolean> banners;
    Setting<Boolean> shulkerFrames;
    Setting<Boolean> ominousVaults;
    Setting<Boolean> buriedTreasure;
    Setting<Boolean> endermanHoldingBlocks;
    Setting<Boolean> xpOrbs;
    Setting<Boolean> stackedEntities;
    FeatureStyle mapFrameStyle;
    FeatureStyle bannerStyle;
    FeatureStyle shulkerFrameStyle;
    FeatureStyle vaultStyle;
    FeatureStyle treasureStyle;
    FeatureStyle endermanStyle;
    FeatureStyle xpOrbStyle;
    FeatureStyle stackedStyle;
    AlertOptions treasureAlerts;
    AlertOptions stackedAlerts;
    Setting<Integer> minStackedCount;

    public VanityOptions(Settings settings) {
        this.settings = settings;
        registerToggles(settings.getDefaultGroup());
        registerFrameAndVaultStyles();
        registerTreasure();
        registerEntityStyles();
        registerStacked();
    }

    private void registerToggles(SettingGroup group) {
        mapFrames = toggle(group, "map-frames", "Highlight item frames holding maps.");
        banners = toggle(group, "banners", "Highlight banners.");
        shulkerFrames = toggle(group, "shulker-frames", "Highlight item frames holding shulker boxes.");
        ominousVaults = toggle(group, "ominous-vaults", "Highlight ominous vaults.");
        buriedTreasure = toggle(group, "buried-treasure", "Highlight buried treasure chests.");
        endermanHoldingBlocks = toggle(group, "enderman-holding-blocks", "Highlight endermen carrying blocks.");
        xpOrbs = toggle(group, "xp-orbs", "Highlight experience orbs.");
        stackedEntities = toggle(group, "stacked-entities", "Highlight stacked inventory-holding entities.");
    }

    private void registerFrameAndVaultStyles() {
        mapFrameStyle = style("Map Frames", mapFrames, new Defaults(rgba(255, 255, 0, 50), rgba(255, 255, 0, 255), rgba(255, 255, 0, 125),
            false, true, false), TracerColorOrder.AFTER_FLAGS);
        bannerStyle = style("Banners", banners, new Defaults(rgba(255, 0, 0, 50), rgba(255, 0, 0, 255), rgba(255, 0, 0, 125),
            true, true, false), TracerColorOrder.AFTER_FLAGS);
        shulkerFrameStyle = style("Shulker Frames", shulkerFrames, new Defaults(rgba(152, 98, 43, 50), rgba(85, 43, 19, 255),
            rgba(166, 150, 101, 255), true, true, false), TracerColorOrder.BEFORE_FLAGS);
        vaultStyle = style("Ominous Vaults", ominousVaults, new Defaults(rgba(0, 120, 120, 50), rgba(31, 161, 159, 255),
            rgba(40, 200, 195, 255), true, true, false), TracerColorOrder.BEFORE_FLAGS);
    }

    private void registerTreasure() {
        SettingGroup group = settings.createGroup("Buried Treasure");
        treasureAlerts = new AlertOptions(group, buriedTreasure);
        treasureAlerts.addChat(false);
        treasureAlerts.addWaypoints(false, true);
        treasureAlerts.addSound();
        treasureStyle = new FeatureStyle(group, buriedTreasure, new Defaults(rgba(147, 233, 190, 25), rgba(147, 233, 190, 255),
            rgba(147, 233, 190, 125), true, true, true), TracerColorOrder.BEFORE_FLAGS);
    }

    private void registerEntityStyles() {
        endermanStyle = style("Enderman", endermanHoldingBlocks, new Defaults(rgba(128, 0, 128, 50), rgba(200, 0, 200, 255),
            rgba(200, 0, 200, 125), true, true, false), TracerColorOrder.AFTER_FLAGS);
        xpOrbStyle = style("XP Orbs", xpOrbs, new Defaults(rgba(0, 255, 0, 75), rgba(0, 255, 0, 255), rgba(0, 255, 0, 125),
            true, true, false), TracerColorOrder.AFTER_FLAGS);
    }

    private void registerStacked() {
        SettingGroup group = settings.createGroup("Stacked Entities");
        stackedAlerts = new AlertOptions(group, stackedEntities);
        stackedAlerts.addChat(true);
        stackedAlerts.addSound();
        minStackedCount = group.add(new IntSetting.Builder().name("min-stacked-count")
            .description("Smallest stack of entities that counts.").defaultValue(2).min(2).sliderRange(2, 10)
            .visible(stackedEntities::get).build());
        stackedAlerts.addWaypoints(true, false);
        stackedStyle = new FeatureStyle(group, stackedEntities, new Defaults(rgba(255, 165, 0, 75), rgba(255, 140, 0, 255),
            rgba(255, 165, 0, 200), true, true, true), TracerColorOrder.AFTER_FLAGS);
    }

    private FeatureStyle style(String groupName, Setting<Boolean> toggle, Defaults defaults, TracerColorOrder order) {
        return new FeatureStyle(settings.createGroup(groupName), toggle, defaults, order);
    }

    private static SettingColor rgba(int red, int green, int blue, int alpha) {
        return new SettingColor(red, green, blue, alpha);
    }

    private static Setting<Boolean> toggle(SettingGroup group, String name, String description) {
        return group.add(new BoolSetting.Builder().name(name).description(description).defaultValue(true).build());
    }
}
