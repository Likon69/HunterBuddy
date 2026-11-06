package com.hunterbuddy.pet;

import java.util.Random;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.sound.SoundSystem;

public final class PetSfxPlayback implements PetSfx.Backend {
    private final Random random = new Random();
    private final PetSfxVoice[] playing = new PetSfxVoice[3];

    @Override
    public boolean active(int slot) {
        return playing[slot] != null && MeteorClient.mc.getSoundManager().isPlaying(playing[slot]);
    }

    @Override
    public boolean start(PetSfxAction action, int slot, float volume) {
        PetSfxBank.Sample[] samples = PetResources.INSTANCE.sfx.samples[action.ordinal()];
        if (samples == null || samples.length == 0) return false;
        PetSfxBank.Sample sample = samples[random.nextInt(samples.length)];
        if (sample.volume == 0) return false;
        PetSfxVoice voice = sample.voices[slot];
        if (voice == playing[slot] && active(slot)) return false;
        voice.configure(volume * sample.volume, sample.low + random.nextFloat() * (sample.high - sample.low));
        if (MeteorClient.mc.getSoundManager().play(voice) == SoundSystem.PlayResult.NOT_STARTED) return false;
        playing[slot] = voice;
        return true;
    }

    @Override
    public void stop(int slot) {
        if (playing[slot] != null) MeteorClient.mc.getSoundManager().stop(playing[slot]);

    }
}
