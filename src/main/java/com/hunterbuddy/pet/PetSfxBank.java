package com.hunterbuddy.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class PetSfxBank {
    public static final PetSfxBank EMPTY = new PetSfxBank(new Sample[PetSfxAction.ALL.length][]);
    final Sample[][] samples;

    private PetSfxBank(Sample[][] samples) {
        this.samples = samples;
    }

    public static PetSfxBank load(ResourceManager manager, Consumer<String> log) {
        return load(manager, id -> Registries.SOUND_EVENT.containsId(id) ? Registries.SOUND_EVENT.get(id) : null, log);
    }

    static PetSfxBank load(ResourceManager manager, Function<Identifier, SoundEvent> sounds, Consumer<String> log) {
        Identifier id = Identifier.of("hunterbuddy", "pet/sfx.json");
        try (var reader = new InputStreamReader(manager.getResourceOrThrow(id).getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            Sample[][] samples = new Sample[PetSfxAction.ALL.length][];
            int unknown = 0;
            int actions = 0;
            int total = 0;
            for (PetSfxAction action : PetSfxAction.ALL) {
                if (!data.has(action.key)) continue;
                JsonArray entries = data.getAsJsonArray(action.key);
                if (entries.size() > 128) throw new IllegalArgumentException();
                Sample[] group = new Sample[entries.size()];
                int count = 0;
                for (var entry : entries) {
                    JsonObject value = entry.getAsJsonObject();
                    Identifier sound = Identifier.tryParse(value.get("sound").getAsString());
                    SoundEvent event = resolve(sound, sounds);
                    if (event == null) {
                        unknown++;
                        continue;
                    }
                    float volume = value.get("volume").getAsFloat();
                    JsonArray pitch = value.getAsJsonArray("pitch");
                    if (pitch.size() != 2) throw new IllegalArgumentException();
                    float low = pitch.get(0).getAsFloat();
                    float high = pitch.get(1).getAsFloat();
                    if (!Float.isFinite(volume) || volume < 0 || volume > 4 || !Float.isFinite(low)
                        || !Float.isFinite(high) || low <= 0 || high < low) throw new IllegalArgumentException();
                    group[count++] = new Sample(event, volume, low, high);
                }
                samples[action.ordinal()] = Arrays.copyOf(group, count);
                if (count > 0) actions++;
                total += count;
            }
            log.accept("[pet] sfx loaded " + actions + " actions, " + total + " sounds" + (unknown > 0 ? " (" + unknown + " unknown ids ignored)" : ""));
            return new PetSfxBank(samples);
        } catch (Exception failure) {
            log.accept("[pet] sfx not loaded: " + failure);
            return EMPTY;
        }
    }

    private static SoundEvent resolve(Identifier sound, Function<Identifier, SoundEvent> sounds) {
        if (sound == null) return null;
        return switch (sound.getNamespace()) {
            case "minecraft" -> sounds.apply(sound);
            case "hunterbuddy" -> SoundEvent.of(sound);
            default -> null;
        };
    }

    static final class Sample {
        final float volume;
        final float low;
        final float high;
        final PetSfxVoice[] voices = new PetSfxVoice[3];

        Sample(SoundEvent sound, float volume, float low, float high) {
            this.volume = volume;
            this.low = low;
            this.high = high;
            for (int i = 0; i < voices.length; i++) voices[i] = new PetSfxVoice(sound);
        }
    }
}
