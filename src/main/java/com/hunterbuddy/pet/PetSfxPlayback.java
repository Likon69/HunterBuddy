package com.hunterbuddy.pet;

import java.util.Random;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.sound.SoundSystem;

public final class PetSfxPlayback implements PetSfx.Backend {
    private final Random random = new Random();
    private final PetSfxVoice[] playing = new PetSfxVoice[3];
    private String refusal = "";

    @Override
    public boolean active(int slot) {
        return playing[slot] != null && MeteorClient.mc.getSoundManager().isPlaying(playing[slot]);
    }

    @Override
    public boolean start(PetSfxAction action, int slot, float volume) {
        PetSfxBank.Sample[] samples = PetResources.INSTANCE.sfx.samples[action.ordinal()];
        if (samples == null || samples.length == 0) {
            refusal = PetResources.INSTANCE.sfx == PetSfxBank.EMPTY ? "no sound loaded for this action (sfx.json missing or unparsable)" : null;
            return false;
        }
        PetSfxBank.Sample sample = samples[random.nextInt(samples.length)];
        if (sample.volume == 0) {
            refusal = "sample volume is zero";
            return false;
        }
        PetSfxVoice voice = sample.voices[slot];
        if (voice == playing[slot] && active(slot)) {
            refusal = "voice still playing";
            return false;
        }
        if (MeteorClient.mc.getSoundManager().get(voice.getId()) == null) {
            refusal = "sound " + voice.getId() + " is not declared by any loaded pack";
            return false;
        }
        voice.configure(volume * sample.volume, sample.low + random.nextFloat() * (sample.high - sample.low));
        if (MeteorClient.mc.getSoundManager().play(voice) == SoundSystem.PlayResult.NOT_STARTED) {
            refusal = "the sound engine did not start the sound";
            return false;
        }
        playing[slot] = voice;
        return true;
    }

    @Override
    public void stop(int slot) {
        if (playing[slot] != null) MeteorClient.mc.getSoundManager().stop(playing[slot]);
    }

    @Override
    public String refusal() {
        return refusal;
    }
}
