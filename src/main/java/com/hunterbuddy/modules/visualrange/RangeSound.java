package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;

import java.util.List;

public final class RangeSound {
    private final RangeSoundSettings settings;

    public RangeSound(RangeSoundSettings settings) {
        this.settings = settings;
    }

    public void play() {
        List<SoundEvent> sounds = settings.sound.get();
        if (!settings.soundEnabled.get() || sounds.isEmpty()) return;
        float pitch = settings.soundPitch.get().floatValue();
        float volume = settings.soundVolume.get().floatValue();
        MeteorClient.mc.getSoundManager().play(PositionedSoundInstance.master(sounds.get(0), pitch, volume));
    }
}
