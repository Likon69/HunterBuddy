package com.hunterbuddy.pet;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

final class PetSfxVoice extends AbstractSoundInstance {
    PetSfxVoice(SoundEvent event) {
        super(event, SoundCategory.UI, SoundInstance.createRandom());
        attenuationType = AttenuationType.NONE;
        relative = true;
    }

    void configure(float volume, float pitch) {
        this.volume = volume;
        this.pitch = pitch;
    }
}
