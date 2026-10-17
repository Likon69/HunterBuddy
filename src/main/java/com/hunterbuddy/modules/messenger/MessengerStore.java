package com.hunterbuddy.modules.messenger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hunterbuddy.HunterBuddyAddon;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;

public final class MessengerStore {
    public record Spot(double fx, double fy, boolean open) {
    }

    public record Snapshot(Map<String, Conversation> conversations, Map<String, Spot> spots) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MessengerStore() {
    }

    private static Path file() {
        return new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "messenger.json").toPath();
    }

    private static void keepBackup() {
        try {
            Path path = file();
            Files.copy(path, path.resolveSibling("messenger.json.bak"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            HunterBuddyAddon.LOG.warn("Messenger: could not write the .bak copy", e);
        }
    }

    public static Snapshot load() {
        Map<String, Conversation> conversations = new LinkedHashMap<>();
        Map<String, Spot> spots = new LinkedHashMap<>();

        try {
            Path path = file();
            if (!Files.exists(path)) return new Snapshot(conversations, spots);

            JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return new Snapshot(conversations, spots);

            if (root.has("spots")) {
                for (var entry : root.getAsJsonObject("spots").entrySet()) {
                    JsonObject spot = entry.getValue().getAsJsonObject();
                    spots.put(entry.getKey(), new Spot(
                        spot.get("x").getAsDouble(), spot.get("y").getAsDouble(), spot.get("open").getAsBoolean()));
                }
            }

            if (root.has("conversations")) {
                for (var entry : root.getAsJsonObject("conversations").entrySet()) {
                    JsonObject item = entry.getValue().getAsJsonObject();
                    Conversation conversation = new Conversation(item.get("peer").getAsString());

                    for (var element : item.getAsJsonArray("messages")) {
                        JsonObject m = element.getAsJsonObject();
                        conversation.add(new PrivateMessage(conversation.peer(), m.get("in").getAsBoolean(),
                            m.get("text").getAsString(), m.get("at").getAsLong()), Integer.MAX_VALUE);
                    }

                    conversation.restore(item.get("unread").getAsInt(), item.get("last").getAsLong());
                    conversations.put(entry.getKey(), conversation);
                }
            }
        } catch (Exception e) {
            HunterBuddyAddon.LOG.warn("Messenger: could not read the saved state, keeping a .bak copy", e);
            keepBackup();
            conversations.clear();
            spots.clear();
        }

        return new Snapshot(conversations, spots);
    }

    public static void save(Snapshot snapshot, boolean withHistory) {
        JsonObject root = new JsonObject();

        JsonObject spots = new JsonObject();
        for (var entry : snapshot.spots().entrySet()) {
            JsonObject spot = new JsonObject();
            spot.addProperty("x", entry.getValue().fx());
            spot.addProperty("y", entry.getValue().fy());
            spot.addProperty("open", entry.getValue().open());
            spots.add(entry.getKey(), spot);
        }
        root.add("spots", spots);

        JsonObject conversations = new JsonObject();
        if (withHistory) {
            for (var entry : snapshot.conversations().entrySet()) {
                Conversation conversation = entry.getValue();
                JsonObject item = new JsonObject();
                item.addProperty("peer", conversation.peer());
                item.addProperty("unread", conversation.unread());
                item.addProperty("last", conversation.lastActivity());

                JsonArray messages = new JsonArray();
                for (PrivateMessage message : conversation.messages()) {
                    JsonObject m = new JsonObject();
                    m.addProperty("in", message.incoming());
                    m.addProperty("text", message.text());
                    m.addProperty("at", message.epochMillis());
                    messages.add(m);
                }
                item.add("messages", messages);
                conversations.add(entry.getKey(), item);
            }
        }
        root.add("conversations", conversations);

        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling("messenger.json.tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            HunterBuddyAddon.LOG.warn("Messenger: could not save the state", e);
        }
    }
}
