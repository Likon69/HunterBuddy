package com.hunterbuddy.modules.regear.util;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MsgUtil {
    private static final String RAW_PREFIX = "[HunterBuddy]";
    private static final String SPARKLE = "✨";

    private static final Map<String, String> MODULE_COLORS = new HashMap<>();

    private MsgUtil() {
    }

    public static String getRawPrefix() {
        return RAW_PREFIX;
    }

    public static String getRawPrefix(String module) {
        return "[" + module + "]";
    }

    public static void sendMsg(String msg) {
        deliver(randomPrefix(), msg, Style.EMPTY);
    }

    public static void sendMsg(String msg, Style style) {
        deliver(randomPrefix(), msg, style);
    }

    public static void sendModuleMsg(String msg, String module) {
        deliver(modulePrefix(module), msg, Style.EMPTY);
    }

    private static String randomPrefix() {
        return Formatting.DARK_GRAY + "<" + Utils.rCC() + Formatting.ITALIC + SPARKLE + Formatting.DARK_GRAY + ">";
    }

    private static String modulePrefix(String module) {
        String title = meteordevelopment.meteorclient.utils.Utils.nameToTitle(module);
        return Formatting.DARK_GRAY + "[" + MODULE_COLORS.computeIfAbsent(module, name -> Utils.rCC()) + Formatting.ITALIC + title + Formatting.DARK_GRAY + "]";
    }

    private static void deliver(String prefix, String msg, Style style) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        try {
            Text text = Text.literal(prefix + " " + Formatting.GRAY + msg).setStyle(style);
            mc.player.sendMessage(text, false);
        } catch (Exception ignored) {
        }
    }
}
