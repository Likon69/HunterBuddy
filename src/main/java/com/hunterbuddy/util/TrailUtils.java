package com.hunterbuddy.util;

import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownServiceException;
import javax.net.ssl.HttpsURLConnection;
import net.minecraft.util.math.Vec3d;

public final class TrailUtils {
    private static Boolean available;
    private static Boolean elytraProcess;

    private TrailUtils() {}

    public static Vec3d yawToDirection(double yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3d(-Math.sin(rad), 0.0, Math.cos(rad));
    }

    public static Vec3d positionInDirection(Vec3d pos, double yaw, double distance) {
        Vec3d offset = yawToDirection(yaw).multiply(distance);
        return pos.add(offset);
    }

    public static double angleDifference(double target, double current) {
        double diff = (target - current + 180.0) % 360.0 - 180.0;
        return diff < -180.0 ? diff + 360.0 : diff;
    }

    public static float smoothRotation(double current, double target, double rotationScaling) {
        double difference = angleDifference(target, current);
        return (float) (current + difference * rotationScaling);
    }

    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("baritone.api.BaritoneAPI");
                available = true;
            } catch (ClassNotFoundException e) {
                available = false;
            }
        }

        return available;
    }

    public static boolean hasElytraProcess() {
        if (elytraProcess == null) {
            if (!isAvailable()) {
                elytraProcess = false;
            } else {
                try {
                    Class.forName("baritone.api.IBaritone").getMethod("getElytraProcess");
                    elytraProcess = true;
                } catch (Exception e) {
                    elytraProcess = false;
                }
            }
        }

        return elytraProcess;
    }

    public static void sendWebhook(String webhookURL, String title, String message, String pingID, String playerName) {
        String json = "{\"embeds\": [{\"title\": \"" + title + "\",\"description\": \"" + message
            + "\",\"color\": 15258703,\"footer\": {\"text\": \"From: " + playerName + "\"}}]}";
        sendRequest(webhookURL, json);
        if (pingID != null) {
            json = "{\"content\": \"<@" + pingID + ">\"}";
            sendRequest(webhookURL, json);
        }
    }

    private static void sendRequest(String webhookURL, String json) {
        try {
            URL url = URI.create(webhookURL).toURL();
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.addRequestProperty("Content-Type", "application/json");
            con.addRequestProperty("User-Agent", "Mozilla");
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            OutputStream stream = con.getOutputStream();
            stream.write(json.getBytes());
            stream.flush();
            stream.close();
            con.getInputStream().close();
            con.disconnect();
        } catch (MalformedURLException | UnknownServiceException var5) {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
