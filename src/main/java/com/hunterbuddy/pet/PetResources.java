package com.hunterbuddy.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

public final class PetResources implements SynchronousResourceReloader {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String[] PART_NAMES = {"head", "torso", "left_arm", "right_arm", "left_leg", "right_leg"};
    public static final String[] ANCHOR_NAMES = {"cigar_tip", "bottle_mouth", "mouth", "eyes", "feet"};
    public static final PetResources INSTANCE = new PetResources();
    public volatile Rig rig;
    public volatile PetSfxBank sfx = PetSfxBank.EMPTY;
    public volatile PetAnimation[] animations = new PetAnimation[0];
    public volatile Line[][] lines = new Line[PetEvent.ALL.length][];
    public volatile int revision;
    public volatile String status = "Resource pack not loaded";
    private float volume = 0.65f;

    private PetResources() {
    }

    @Override
    public void reload(ResourceManager manager) {
        Rig loaded = null;
        Line[][] loadedLines = new Line[PetEvent.ALL.length][];
        String loadedStatus = "Resource pack missing or incomplete";
        try {
            loaded = readRig(manager);
            loadedStatus = "Ready";
            try {
                JsonObject data = readJson(manager, "pet/lines.json");
                for (PetEvent event : PetEvent.ALL) {
                    if (!data.has(event.key)) continue;
                    JsonArray array = data.getAsJsonArray(event.key);
                    Line[] entries = new Line[Math.min(128, array.size())];
                    int count = 0;
                    for (int i = 0; i < entries.length; i++) {
                        try {
                            JsonObject value = array.get(i).getAsJsonObject();
                            String text = value.get("text").getAsString();
                            if (text.isBlank() || text.length() > 512) continue;
                            Identifier sound = Identifier.of(value.get("sound").getAsString());
                            entries[count++] = new Line(sound, text, value.has("pose") ? value.get("pose").getAsString() : null, volume);
                        } catch (RuntimeException ignored) {
                        }
                    }
                    loadedLines[event.ordinal()] = Arrays.copyOf(entries, count);
                }
            } catch (Exception ignored) {
                loadedStatus = "Ready, dialogue missing";
            }
        } catch (Exception ignored) {
        }
        sfx = PetSfxBank.load(manager, LOG::info);
        animations = PetAnimationPack.load(manager, LOG::info);
        rig = loaded;
        lines = loadedLines;
        status = loadedStatus;
        revision++;
    }

    public void volume(float value) {
        volume = value;
        for (Line[] group : lines) {
            if (group == null) continue;
            for (Line line : group) line.voice = PositionedSoundInstance.master(SoundEvent.of(line.sound), 1f, volume);
        }
    }

    private static Rig readRig(ResourceManager manager) throws Exception {
        JsonObject data = readJson(manager, "pet/rig.json");
        float width = coordinate(data.getAsJsonArray("source_size"), 0);
        float height = coordinate(data.getAsJsonArray("source_size"), 1);
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) throw new IllegalArgumentException();
        JsonObject parts = data.getAsJsonObject("parts");
        Part[] ordered = new Part[PART_NAMES.length];
        for (int i = 0; i < ordered.length; i++) {
            JsonObject value = parts.getAsJsonObject(PART_NAMES[i]);
            Identifier texture = Identifier.of(value.get("texture").getAsString());
            int imageWidth;
            int imageHeight;
            float[] outline;
            try (InputStream stream = manager.getResourceOrThrow(texture).getInputStream(); NativeImage image = NativeImage.read(stream)) {
                imageWidth = image.getWidth();
                imageHeight = image.getHeight();
                outline = PetOutline.read(image);
            }
            ordered[i] = new Part(i, texture, coordinate(value.getAsJsonArray("offset"), 0),
                coordinate(value.getAsJsonArray("offset"), 1), coordinate(value.getAsJsonArray("pivot"), 0),
                coordinate(value.getAsJsonArray("pivot"), 1), imageWidth, imageHeight, outline, value.get("z").getAsInt());
        }
        Arrays.sort(ordered, Comparator.comparingInt(Part::z));
        float[] anchors = new float[ANCHOR_NAMES.length * 2];
        JsonObject points = data.getAsJsonObject("anchors");
        for (int i = 0; i < ANCHOR_NAMES.length; i++) {
            anchors[i * 2] = coordinate(points.getAsJsonArray(ANCHOR_NAMES[i]), 0);
            anchors[i * 2 + 1] = coordinate(points.getAsJsonArray(ANCHOR_NAMES[i]), 1);
        }
        JsonObject prop = data.getAsJsonObject("props").getAsJsonObject("bottle");
        Identifier bottle = Identifier.of(prop.get("texture").getAsString());
        try (InputStream stream = manager.getResourceOrThrow(bottle).getInputStream(); NativeImage image = NativeImage.read(stream)) {
            if (image.getWidth() <= 0 || image.getHeight() <= 0) throw new IllegalArgumentException();
        }
        int bottleWidth = Math.round(coordinate(prop.getAsJsonArray("size"), 0));
        int bottleHeight = Math.round(coordinate(prop.getAsJsonArray("size"), 1));
        if (bottleWidth <= 0 || bottleHeight <= 0 || bottleWidth > 4096 || bottleHeight > 4096) throw new IllegalArgumentException();
        FullPose[] poses = new FullPose[0];
        if (data.has("poses")) {
            JsonObject definitions = data.getAsJsonObject("poses");
            FullPose[] candidates = new FullPose[definitions.size()];
            int count = 0;
            for (String name : definitions.keySet()) {
                if (count == candidates.length) break;
                try {
                    JsonObject value = definitions.getAsJsonObject(name);
                    Identifier texture = Identifier.of(value.get("texture").getAsString());
                    int pw = Math.round(coordinate(value.getAsJsonArray("size"), 0));
                    int ph = Math.round(coordinate(value.getAsJsonArray("size"), 1));
                    float fx = coordinate(value.getAsJsonArray("feet"), 0);
                    float fy = coordinate(value.getAsJsonArray("feet"), 1);
                    float poseScale = value.get("scale").getAsFloat();
                    float eyesX = coordinate(value.getAsJsonArray("eyes"), 0);
                    float eyesY = coordinate(value.getAsJsonArray("eyes"), 1);
                    if (!Float.isFinite(poseScale) || poseScale <= 0 || poseScale > 16 || eyesX < 0 || eyesX > pw || eyesY < 0 || eyesY > ph) continue;
                    if (pw < 1 || ph < 1 || pw > 4096 || ph > 4096 || fx < 0 || fx > pw || fy < 0 || fy > ph) continue;
                    long hangingSum = 0;
                    int hangingCount = 0;
                    try (InputStream stream = manager.getResourceOrThrow(texture).getInputStream(); NativeImage image = NativeImage.read(stream)) {
                        if (image.getWidth() != pw || image.getHeight() != ph) continue;
                        for (int py = Math.max(0, (int) Math.ceil(fy)); py < ph; py++) {
                            for (int px = 0; px < pw; px++) {
                                if ((image.getColorArgb(px, py) >>> 24) < 128) continue;
                                hangingSum += px;
                                hangingCount++;
                            }
                        }
                    }
                    candidates[count++] = new FullPose(name, texture, pw, ph, fx, fy, poseScale, eyesX, eyesY, hangingCount == 0 ? 0 : (float) hangingSum / hangingCount - fx);
                } catch (Exception ignored) {
                }
            }
            poses = Arrays.copyOf(candidates, count);
        }
        FullPose[] dances = danceFrames(poses);
        return new Rig(width, height, ordered, anchors, bottle, bottleWidth, bottleHeight,
            coordinate(prop.getAsJsonArray("neck"), 0), coordinate(prop.getAsJsonArray("neck"), 1), poses, dances,
            new int[dances.length], new int[dances.length]);
    }

    static FullPose[] danceFrames(FullPose[] poses) {
        int count = 0;
        for (FullPose pose : poses) if (danceNumber(pose.name()) > 0) count++;
        FullPose[] frames = new FullPose[count];
        count = 0;
        for (FullPose pose : poses) if (danceNumber(pose.name()) > 0) frames[count++] = pose;
        return frames;
    }

    static int danceNumber(String name) {
        if (!name.startsWith("dance_") || name.length() <= 6) return 0;
        int number = 0;
        for (int i = 6; i < name.length(); i++) {
            int digit = name.charAt(i) - '0';
            if (digit < 0 || digit > 9 || number > (Integer.MAX_VALUE - digit) / 10) return 0;
            number = number * 10 + digit;
        }
        return number;
    }

    private static float coordinate(JsonArray array, int index) {
        float value = array.get(index).getAsFloat();
        if (!Float.isFinite(value) || Math.abs(value) > 16384) throw new IllegalArgumentException();
        return value;
    }

    private static JsonObject readJson(ResourceManager manager, String path) throws Exception {
        try (InputStream stream = manager.getResourceOrThrow(Identifier.of("hunterbuddy", path)).getInputStream();
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    public record Part(int index, Identifier texture, float x, float y, float pivotX, float pivotY, int width, int height, float[] outline, int z) {
    }

    public record Rig(float width, float height, Part[] parts, float[] anchors, Identifier bottle, int bottleWidth,
                      int bottleHeight, float neckX, float neckY, FullPose[] poses, FullPose[] dances, int[] danceOrder, int[] previousDanceOrder) {
    }

    public record FullPose(String name, Identifier texture, int width, int height, float feetX, float feetY,
                           float scale, float eyesX, float eyesY, float hangingX) {
    }

    public static final class Line {
        public final Identifier sound;
        public final String value;
        public final String pose;
        public final OrderedText[] text;
        public final int width;
        public PositionedSoundInstance voice;

        private Line(Identifier sound, String value, String pose, float volume) {
            this.sound = sound;
            this.value = value;
            this.pose = pose;
            List<OrderedText> wrapped = MeteorClient.mc.textRenderer.wrapLines(Text.literal(value), 210);
            text = wrapped.toArray(OrderedText[]::new);
            int longest = 0;
            for (OrderedText row : text) longest = Math.max(longest, MeteorClient.mc.textRenderer.getWidth(row));
            width = longest;
            voice = PositionedSoundInstance.master(SoundEvent.of(sound), 1f, volume);
        }
    }
}
