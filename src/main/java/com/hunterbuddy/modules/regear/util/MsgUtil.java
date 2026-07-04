package com.hunterbuddy.modules.regear.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IChatHud;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

/**
 * Reference parity: mlep.util.MsgUtil. Sends messages to the player chat with
 * the "✨" prefix.
 */
public class MsgUtil {
    private static final Map<String, String> modulePrefixes = new HashMap<>();

    public static String getPrefix() {
        return Formatting.DARK_GRAY + "<" + Utils.rCC() + Formatting.ITALIC + "\u2728" + Formatting.DARK_GRAY + ">";
    }

    public static String getRawPrefix() {
        return "[Mlep]";
    }

    public static String getRawPrefix(String module) {
        return "[" + module + "]";
    }

    /** Register a module name so its prefix is recolored with a fresh random color. */
    public static void registerModulePrefix(String moduleName) {
        modulePrefixes.put(moduleName, Utils.rCC());
    }

    /** Back-compat method that does nothing now (kept so existing callers compile). */
    public static void initModulePrefixes() {
        // Module prefix list is now populated lazily by callers via
        // registerModulePrefix(). Kept for source parity with mlep's startup hook.
    }

    public static String getModulePrefix(String module) {
        return !modulePrefixes.containsKey(module)
            ? Formatting.DARK_GRAY
                + "["
                + Utils.rCC()
                + Formatting.ITALIC
                + meteordevelopment.meteorclient.utils.Utils.nameToTitle(module)
                + Formatting.DARK_GRAY
                + "]"
            : Formatting.DARK_GRAY
                + "["
                + modulePrefixes.get(module)
                + Formatting.ITALIC
                + meteordevelopment.meteorclient.utils.Utils.nameToTitle(module)
                + Formatting.DARK_GRAY
                + "]";
    }

    public static void sendRawMsg(String msg) {
        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendMessage(Text.literal(msg), false);
        }
    }

    public static void sendMsg(String msg) {
        if (MeteorClient.mc.player != null) {
            try {
                StringBuilder sb = new StringBuilder();
                MeteorClient.mc.player.sendMessage(
                    Text.literal(sb.append(getPrefix()).append(' ').append(Formatting.GRAY).append(msg).toString()),
                    false
                );
            } catch (Exception ignored) {}
        }
    }

    public static void sendMsg(String msg, Style style) {
        if (MeteorClient.mc.player != null) {
            try {
                String message = getPrefix() + " " + Formatting.GRAY + msg;
                MeteorClient.mc.player.sendMessage(Text.literal(message).setStyle(style), false);
            } catch (Exception ignored) {}
        }
    }

    public static void sendModuleMsg(String msg, String module) {
        if (MeteorClient.mc.player != null) {
            try {
                StringBuilder sb = new StringBuilder();
                MeteorClient.mc.player.sendMessage(
                    Text.literal(sb.append(getModulePrefix(module)).append(' ').append(Formatting.GRAY).append(msg).toString()),
                    false
                );
            } catch (Exception ignored) {}
        }
    }

    public static void sendModuleMsg(String msg, Style style, String module) {
        if (MeteorClient.mc.player != null) {
            try {
                String message = getModulePrefix(module) + " " + Formatting.GRAY + msg;
                MeteorClient.mc.player.sendMessage(Text.literal(message).setStyle(style), false);
            } catch (Exception ignored) {}
        }
    }

    public static void updateMsg(String msg, int hashcode) {
        if (MeteorClient.mc.player != null) {
            try {
                StringBuilder sb = new StringBuilder();
                ((IChatHud) MeteorClient.mc.inGameHud.getChatHud())
                    .meteor$add(Text.literal(sb.append(getPrefix()).append(' ').append(Formatting.GRAY).append(msg).toString()), hashcode);
            } catch (Exception ignored) {}
        }
    }

    public static void updateModuleMsg(String msg, String module, int hashcode) {
        if (MeteorClient.mc.player != null) {
            try {
                StringBuilder sb = new StringBuilder();
                ((IChatHud) MeteorClient.mc.inGameHud.getChatHud())
                    .meteor$add(
                        Text.literal(sb.append(getModulePrefix(module)).append(' ').append(Formatting.GRAY).append(msg).toString()),
                        hashcode
                    );
            } catch (Exception ignored) {}
        }
    }
}
