package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class TimerSpeedHud extends HudElement {
    public static final HudElementInfo<TimerSpeedHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "TimerGauge",
        "Shows the speed Meteor's Timer module runs the game at.",
        TimerSpeedHud::new);

    private final SettingGroup group = settings.getDefaultGroup();

    private final Setting<Boolean> showTitle = group.add(new BoolSetting.Builder()
        .name("show-title").description("Show a 'Timer' label above the value.")
        .defaultValue(true).build());

    private final Setting<Integer> decimals = group.add(new IntSetting.Builder()
        .name("decimal-places").description("Digits after the decimal point.")
        .defaultValue(2).min(0).max(3).sliderRange(0, 3).build());

    private final Setting<Double> scale = group.add(new DoubleSetting.Builder()
        .name("text-scale").description("Size of the text.")
        .defaultValue(1.0).min(0.1).sliderRange(0.1, 3.0).build());

    private final Setting<Boolean> shadow = group.add(new BoolSetting.Builder()
        .name("text-shadow").description("Draw the text with a shadow.")
        .defaultValue(true).build());

    private final Setting<SettingColor> titleColor = group.add(new ColorSetting.Builder()
        .name("title-color").description("Colour of the label.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> speedColor = group.add(new ColorSetting.Builder()
        .name("speed-color").description("Colour of the value.")
        .defaultValue(new SettingColor(0, 255, 255, 255)).build());

    public TimerSpeedHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean inGame = MeteorClient.mc.player != null && MeteorClient.mc.world != null;
        if (!inGame && !isInEditor()) {
            setSize(0.0, 0.0);
            return;
        }

        double speed = 1.0;
        if (inGame) {
            Timer timer = Modules.get().get(Timer.class);
            if (timer != null && timer.isActive()) speed = timer.getMultiplier();
        }

        String value = String.format("%." + decimals.get() + "fx", speed);
        boolean useShadow = shadow.get();
        double s = scale.get();
        double lineHeight = renderer.textHeight(useShadow, s);

        double width = renderer.textWidth(value, useShadow, s);
        double valueY = y;
        double height = lineHeight;

        if (showTitle.get()) {
            String title = "Timer";
            renderer.text(title, x, y, titleColor.get(), useShadow, s);
            width = Math.max(width, renderer.textWidth(title, useShadow, s));
            valueY = y + lineHeight + 2;
            height = lineHeight * 2 + 2;
        }

        renderer.text(value, x, valueY, speedColor.get(), useShadow, s);
        setSize(width, height);
    }
}
