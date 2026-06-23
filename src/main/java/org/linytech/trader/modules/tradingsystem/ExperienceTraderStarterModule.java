/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;

public class ExperienceTraderStarterModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final ExperienceTraderModule module;

    private final Setting<Integer> hourToEnable = sgGeneral.add(
        new IntSetting.Builder()
            .name("hour-to-enable-trader")
            .min(0)
            .max(23)
            .sliderMin(0)
            .sliderMax(23)
            .defaultValue(13)
            .onChanged(ignored -> this.updateParsedTime())
            .build()
    );

    private final Setting<Integer> minutesToEnable = sgGeneral.add(
        new IntSetting.Builder()
            .name("minute-to-enable-trader")
            .min(0)
            .max(59)
            .sliderMin(0)
            .sliderMax(59)
            .defaultValue(0)
            .onChanged(ignored -> this.updateParsedTime())
            .build()
    );

    private String parsedTime;

    public ExperienceTraderStarterModule(Category category, ExperienceTraderModule module) {
        super(category, "Experience Trader Starter", "AI is going to take over the world");
        this.module = module;
    }

    @EventHandler
    public void onTickPost(TickEvent.Post event) {
        if (parsedTime == null) updateParsedTime();

        if (this.parsedTime.equals(Utils.getWorldTime())) {
            if (!this.module.isActive()) {
                this.module.blocked.clear();
                this.module.toggle();
            }
        }
    }

    private void updateParsedTime() {
        this.parsedTime = compileNumber(this.hourToEnable.get())+":"+compileNumber(this.minutesToEnable.get());
    }

    private String compileNumber(int number) {
        return number < 10 ? "0" + number : String.valueOf(number);
    }
}
