package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.SoundEventListSetting;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.List;

public final class RangeSoundSettings {
    public final Setting<Boolean> soundEnabled;
    public final Setting<List<SoundEvent>> sound;
    public final Setting<Double> soundVolume;
    public final Setting<Double> soundPitch;

    public RangeSoundSettings(Settings settings) {
        SettingGroup group = settings.createGroup("Sound");
        soundEnabled = group.add(enabledSetting());
        sound = group.add(soundSetting());
        soundVolume = group.add(volumeSetting());
        soundPitch = group.add(pitchSetting());
    }

    private static Setting<Boolean> enabledSetting() {
        return new BoolSetting.Builder()
            .name("sound-enabled")
            .description("Plays a sound with the alerts.")
            .defaultValue(true)
            .build();
    }

    private Setting<List<SoundEvent>> soundSetting() {
        return new SoundEventListSetting.Builder()
            .name("sound")
            .description("Sound played with the alerts.")
            .defaultValue(SoundEvents.ENTITY_PLAYER_LEVELUP)
            .visible(soundEnabled::get)
            .build();
    }

    private Setting<Double> volumeSetting() {
        return new DoubleSetting.Builder()
            .name("sound-volume")
            .description("Volume of the sound.")
            .defaultValue(1.0)
            .min(0.0)
            .max(2.0)
            .sliderRange(0.0, 2.0)
            .visible(soundEnabled::get)
            .build();
    }

    private Setting<Double> pitchSetting() {
        return new DoubleSetting.Builder()
            .name("sound-pitch")
            .description("Pitch of the sound.")
            .defaultValue(1.0)
            .min(0.5)
            .max(2.0)
            .sliderRange(0.5, 2.0)
            .visible(soundEnabled::get)
            .build();
    }
}
