package com.hunterbuddy.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public final class PetAnimationPack {
    private static final int MAX_ANIMATIONS = 64;
    private static final int MAX_KEYS = 64;

    private PetAnimationPack() {
    }

    public static PetAnimation[] load(ResourceManager manager, Consumer<String> log) {
        Identifier id = Identifier.of("hunterbuddy", "pet/animations.json");
        try (InputStreamReader reader = new InputStreamReader(manager.getResourceOrThrow(id).getInputStream(), StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader), log);
        } catch (FileNotFoundException e) {
            return new PetAnimation[0];
        } catch (Exception e) {
            log.accept("[pet] animations.json unreadable: " + e.getMessage());
            return new PetAnimation[0];
        }
    }

    public static PetAnimation[] parse(String text, Consumer<String> log) {
        try {
            return parse(JsonParser.parseString(text), log);
        } catch (RuntimeException e) {
            log.accept("[pet] animations.json unreadable: " + e.getMessage());
            return new PetAnimation[0];
        }
    }

    static PetAnimation[] parse(JsonElement root, Consumer<String> log) {
        JsonArray list = root.getAsJsonObject().getAsJsonArray("animations");
        List<PetAnimation> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < list.size() && result.size() < MAX_ANIMATIONS; i++) {
            String label = "#" + i;
            try {
                JsonObject value = list.get(i).getAsJsonObject();
                if (value.has("name")) label = value.get("name").getAsString();
                PetAnimation animation = animation(value);
                if (!names.add(animation.name())) throw new IllegalArgumentException("duplicate name");
                result.add(animation);
            } catch (RuntimeException e) {
                log.accept("[pet] animation " + label + " skipped: " + e.getMessage());
            }
        }
        return result.toArray(PetAnimation[]::new);
    }

    private static PetAnimation animation(JsonObject value) {
        String name = value.get("name").getAsString();
        if (name.isBlank() || name.length() > 48) throw new IllegalArgumentException("bad name");
        float duration = number(value, "duration", 0.2f, 60);
        int loops = value.has("loops") ? (int) number(value, "loops", 1, 20) : 1;
        float weight = value.has("weight") ? number(value, "weight", 0, 100) : 0;
        boolean smoke = false;
        if (value.has("props")) {
            JsonObject props = value.getAsJsonObject("props");
            smoke = props.has("smoke") && props.get("smoke").getAsBoolean();
        }
        JsonArray frames = value.getAsJsonArray("keyframes");
        if (frames.isEmpty() || frames.size() > MAX_KEYS) throw new IllegalArgumentException("keyframe count");
        PetAnimation.Key[] keys = new PetAnimation.Key[frames.size()];
        for (int i = 0; i < keys.length; i++) keys[i] = key(frames.get(i).getAsJsonObject());
        Arrays.sort(keys, Comparator.comparingDouble(PetAnimation.Key::t));
        return new PetAnimation(name, duration, loops, weight, smoke, keys);
    }

    private static PetAnimation.Key key(JsonObject frame) {
        float t = number(frame, "t", 0, 1);
        String easing = frame.has("easing") ? frame.get("easing").getAsString() : "smooth";
        if (!easing.equals("smooth") && !easing.equals("linear")) throw new IllegalArgumentException("easing " + easing);
        float[] joints = new float[PetAnimation.JOINTS.length];
        JsonObject values = frame.getAsJsonObject("joints");
        for (String joint : values.keySet()) {
            int index = Arrays.asList(PetAnimation.JOINTS).indexOf(joint);
            if (index < 0) throw new IllegalArgumentException("unknown joint " + joint);
            float raw = values.get(joint).getAsFloat();
            if (!Float.isFinite(raw) || raw < PetAnimation.LOW[index] || raw > PetAnimation.HIGH[index]) {
                throw new IllegalArgumentException(joint + " out of range");
            }
            joints[index] = PetAnimation.toInternal(index, raw);
        }
        return new PetAnimation.Key(t, easing.equals("smooth"), joints);
    }

    private static float number(JsonObject object, String key, float low, float high) {
        float value = object.get(key).getAsFloat();
        if (!Float.isFinite(value) || value < low || value > high) throw new IllegalArgumentException(key + " out of range");
        return value;
    }
}
