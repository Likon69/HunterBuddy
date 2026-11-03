package com.hunterbuddy.modules.regear.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hunterbuddy.HunterBuddyAddon;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class DiscordWebhook {
    private static final int EMBED_COLOR = 15258703;
    private static final int TIMEOUT_MS = 5000;
    private static final int FIRST_ERROR_CODE = 400;
    private static final String FOOTER_PREFIX = "From: ";

    private DiscordWebhook() {
    }

    static void sendEmbed(String url, String title, String message, String pingId, String playerName) {
        send(url, embedPayload(title, message, playerName), pingId);
    }

    static void send(String url, String json, String pingId) {
        post(url, json);
        if (pingId != null) post(url, pingPayload(pingId));
    }

    private static String embedPayload(String title, String message, String playerName) {
        JsonObject footer = new JsonObject();
        footer.addProperty("text", FOOTER_PREFIX + playerName);
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", message);
        embed.addProperty("color", EMBED_COLOR);
        embed.add("footer", footer);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        return payload.toString();
    }

    private static String pingPayload(String pingId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", "<@" + pingId + ">");
        return payload.toString();
    }

    private static void post(String url, String json) {
        try {
            HttpURLConnection connection = open(url);
            writeBody(connection, json);
            reportRefusal(connection);
            connection.disconnect();
        } catch (Exception failure) {
            HunterBuddyAddon.LOG.error("[HB] webhook failed", failure);
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setDoOutput(true);
        return connection;
    }

    private static void writeBody(HttpURLConnection connection, String json) throws Exception {
        try (OutputStream out = connection.getOutputStream()) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static void reportRefusal(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        if (code < FIRST_ERROR_CODE) return;
        try (InputStream error = connection.getErrorStream()) {
            String detail = error == null ? "" : new String(error.readAllBytes(), StandardCharsets.UTF_8);
            HunterBuddyAddon.LOG.error("[HB] webhook refused: HTTP {} {}", code, detail);
        }
    }
}
