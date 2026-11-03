package com.hunterbuddy.modules.stash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.StashFinder.StashChunk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StashDiscord {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final String HEADLINE = "**🗃️ Stash Found!**";

    private final StashDiscordSettings settings;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "StashFinder-discord");
        thread.setDaemon(true);
        return thread;
    });

    public StashDiscord(StashDiscordSettings settings) {
        this.settings = settings;
    }

    public void send(StashChunk chunk) {
        String url = settings.webhookUrl.get();
        if (!settings.enabled.get() || url.isBlank()) return;
        String body = payload(chunk);
        executor.execute(() -> post(url, body));
    }

    private String payload(StashChunk chunk) {
        JsonObject json = new JsonObject();
        json.addProperty("username", settings.webhookUsername.get());
        json.addProperty("content", message(chunk));
        return gson.toJson(json);
    }

    private String message(StashChunk chunk) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADLINE);
        lines.add("**Total Containers:** " + chunk.getTotal());
        lines.add("**Dimension:** " + chunk.dimension);
        if (settings.includeCoordinates.get()) lines.add("**Coordinates:** `" + chunk.x + ", " + chunk.z + "`");
        if (settings.includeBreakdown.get()) addBreakdown(lines, chunk);
        return String.join("\n", lines);
    }

    private static void addBreakdown(List<String> lines, StashChunk chunk) {
        lines.add("");
        lines.add("**Breakdown:**");
        for (StashType type : StashType.values()) {
            int count = chunk.countOf(type);
            if (count > 0) lines.add("• " + describe(type, count));
        }
    }

    private static String describe(StashType type, int count) {
        if (type == StashType.STACKED_MINECART) return type.label() + ": " + count + " in one block";
        return type.label() + ": " + count;
    }

    private void post(String url, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            HunterBuddyAddon.LOG.error("Failed to send Discord webhook", failure);
        }
    }
}
