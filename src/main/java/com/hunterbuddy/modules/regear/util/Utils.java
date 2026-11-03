package com.hunterbuddy.modules.regear.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class Utils {
    public static final boolean XAERO_AVAILABLE = FabricLoader.getInstance().isModLoaded("xaeroworldmap")
        && FabricLoader.getInstance().isModLoaded("xaerominimap");

    private static final List<String> COLORS = List.of(
        "\u00a70", "\u00a7f", "\u00a78", "\u00a77", "\u00a72", "\u00a7a", "\u00a73", "\u00a7b",
        "\u00a71", "\u00a79", "\u00a74", "\u00a7c", "\u00a75", "\u00a7d", "\u00a76", "\u00a7e", "");
    private static final List<String> DULL_COLORS = List.of("\u00a70", "\u00a78", "\u00a77");
    private static final int MAX_QUEUE_PLAYERS = 1;

    public static String rCC() {
        String color;
        do {
            color = COLORS.get(ThreadLocalRandom.current().nextInt(COLORS.size()));
        } while (DULL_COLORS.contains(color));
        return color;
    }

    public static boolean isIn2b2tQueue() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return false;
        return PlayerUtils.getDimension() == Dimension.End
            && mc.player.getAbilities().flying
            && mc.getNetworkHandler().getPlayerList().size() <= MAX_QUEUE_PLAYERS;
    }

    public static int firework(MinecraftClient mc, boolean elytraRequired) {
        return FireworkUse.use(mc, elytraRequired);
    }

    public static void setPressed(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
    }

    public static void holdJump(MinecraftClient mc) {
        setJump(mc, true);
    }

    public static void releaseJump(MinecraftClient mc) {
        setJump(mc, false);
    }

    public static Vec3d positionInDirection(Vec3d pos, double yaw, double distance) {
        return YawMath.positionInDirection(pos, yaw, distance);
    }

    public static Vec3d yawToDirection(double yaw) {
        return YawMath.yawToDirection(yaw);
    }

    public static double distancePointToDirection(Vec3d point, Vec3d direction, @Nullable Vec3d start) {
        return YawMath.distancePointToDirection(point, direction, start);
    }

    public static double angleOnAxis(double yaw) {
        return YawMath.angleOnAxis(yaw);
    }

    public static void sendWebhook(String webhookURL, String title, String message, String pingID, String playerName) {
        DiscordWebhook.sendEmbed(webhookURL, title, message, pingID, playerName);
    }

    public static void sendWebhook(String webhookURL, String jsonObject, String pingID) {
        DiscordWebhook.send(webhookURL, jsonObject, pingID);
    }

    private static void setJump(MinecraftClient mc, boolean pressed) {
        if (mc.player == null) return;
        BaritoneHelper.setJumpPressed(pressed);
        setPressed(mc.options.jumpKey, pressed);
    }
}
