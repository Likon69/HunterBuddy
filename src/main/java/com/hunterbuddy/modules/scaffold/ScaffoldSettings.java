package com.hunterbuddy.modules.scaffold;

import java.util.List;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import net.minecraft.block.Block;

public final class ScaffoldSettings {
    public enum ListMode { Whitelist, Blacklist }

    public final Setting<List<Block>> blocks;
    public final Setting<ListMode> blocksFilter;
    public final Setting<Boolean> airPlace;
    public final Setting<Double> closestBlockRange;
    public final Setting<Boolean> tower;
    public final Setting<Double> towerSpeed;
    public final Setting<Boolean> onlyOnClick;
    public final Setting<Integer> placeDelay;
    public final Setting<Boolean> autoSwitch;
    public final Setting<Boolean> rotate;
    public final Setting<Double> extendDistance;
    public final Setting<Boolean> velocityPredict;
    public final Setting<Boolean> safewalk;
    public final ScaffoldRenderSettings render;

    private final SettingGroup general;
    private final SettingGroup speed;

    public ScaffoldSettings(Settings settings) {
        general = settings.getDefaultGroup();
        speed = settings.createGroup("Speed");
        blocks = blocks();
        blocksFilter = blocksFilter();
        airPlace = airPlace();
        closestBlockRange = closestBlockRange();
        tower = tower();
        towerSpeed = towerSpeed();
        onlyOnClick = onlyOnClick();
        placeDelay = placeDelay();
        autoSwitch = autoSwitch();
        rotate = rotate();
        extendDistance = extendDistance();
        velocityPredict = velocityPredict();
        safewalk = safewalk();
        render = new ScaffoldRenderSettings(settings.createGroup("Render"));
    }

    private Setting<List<Block>> blocks() {
        return general.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Selected blocks.")
            .build());
    }

    private Setting<ListMode> blocksFilter() {
        return general.add(new EnumSetting.Builder<ListMode>()
            .name("blocks-filter")
            .description("How the selected blocks are used.")
            .defaultValue(ListMode.Blacklist)
            .build());
    }

    private Setting<Boolean> airPlace() {
        return general.add(new BoolSetting.Builder()
            .name("air-place")
            .description("Places blocks in mid-air, without a neighbouring block.")
            .defaultValue(true)
            .build());
    }

    private Setting<Double> closestBlockRange() {
        return general.add(new DoubleSetting.Builder()
            .name("closest-block-range")
            .description("How far to look for a block to build on.")
            .defaultValue(4.0)
            .min(0.0)
            .sliderMax(8.0)
            .visible(() -> !airPlace.get())
            .build());
    }

    private Setting<Boolean> tower() {
        return general.add(new BoolSetting.Builder()
            .name("tower")
            .description("Climbs by placing blocks under you while jumping.")
            .defaultValue(false)
            .build());
    }

    private Setting<Double> towerSpeed() {
        return general.add(new DoubleSetting.Builder()
            .name("tower-speed")
            .description("Upward speed while towering.")
            .defaultValue(0.42)
            .min(0.0)
            .max(1.0)
            .sliderMax(1.0)
            .visible(tower::get)
            .build());
    }

    private Setting<Boolean> onlyOnClick() {
        return general.add(new BoolSetting.Builder()
            .name("only-on-click")
            .description("Only places while the use key is held.")
            .defaultValue(false)
            .build());
    }

    private Setting<Integer> placeDelay() {
        return general.add(new IntSetting.Builder()
            .name("place-delay")
            .description("Ticks between placements.")
            .defaultValue(0)
            .min(0)
            .max(10)
            .build());
    }

    private Setting<Boolean> autoSwitch() {
        return general.add(new BoolSetting.Builder()
            .name("auto-switch")
            .description("Switches to a block automatically.")
            .defaultValue(true)
            .build());
    }

    private Setting<Boolean> rotate() {
        return general.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Faces the block before placing it.")
            .defaultValue(true)
            .build());
    }

    private Setting<Double> extendDistance() {
        return general.add(new DoubleSetting.Builder()
            .name("extend-distance")
            .description("How far ahead of you the target is shifted while moving in regear mode.")
            .defaultValue(0.1)
            .min(0.0)
            .max(2.0)
            .sliderMax(2.0)
            .build());
    }

    private Setting<Boolean> velocityPredict() {
        return speed.add(new BoolSetting.Builder()
            .name("velocity-predict")
            .description("Predicts where you will be from your velocity.")
            .defaultValue(true)
            .build());
    }

    private Setting<Boolean> safewalk() {
        return general.add(new BoolSetting.Builder()
            .name("safewalk")
            .description("Stops you from walking off edges.")
            .defaultValue(false)
            .build());
    }
}
