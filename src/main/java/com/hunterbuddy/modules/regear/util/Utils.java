package com.hunterbuddy.modules.regear.util;

import com.hunterbuddy.modules.regear.mixin.PlayerInventoryAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ClientOptionsC2SPacket;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;

import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

/**
 * Reference parity: mlep.util.Utils. Holds helpers used across all AutoFlyingRegear
 * utility classes (key-press, inventory maths, webhook posting, illegal-disconnect,
 * Xaero-presence detection).
 */
public class Utils {
    private static final Random RANDOM = new Random();
    public static final boolean XAERO_AVAILABLE = FabricLoader.getInstance().isModLoaded("xaeroworldmap")
        && FabricLoader.getInstance().isModLoaded("xaerominimap");

    public static String rCC() {
        String color = "§7";
        TextColor[] colors = TextColor.values();
        while (color.equals("§0") || color.equals("§8") || color.equals("§7")) {
            int luckyIndex = RANDOM.nextInt(colors.length);
            color = colors[luckyIndex].label;
        }
        return color;
    }

    public static boolean checkOrCreateFile(MinecraftClient mc, String fileName) {
        File file = FabricLoader.getInstance().getGameDir().resolve(fileName).toFile();
        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    if (mc.player != null) {
                        MsgUtil.sendMsg("Created " + file.getName() + " in your meteor-client folder.");
                        Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath()));
                        MsgUtil.sendMsg("Click §2§lhere §r§7to open the file.", style);
                    }
                    return true;
                }
            } catch (Exception err) {
                LogUtil.error("Error creating " + file.getAbsolutePath() + "! - Why:\n" + err, "Utils#checkOrCreateFile");
            }
            return false;
        }
        return true;
    }

    public static void openFile(String fileName) {
        File file = FabricLoader.getInstance().getGameDir().resolve(fileName).toFile();
        try {
            Runtime runtime = Runtime.getRuntime();
            String os = System.getenv("OS");
            if (os == null) return;

            if (os.contains("Windows")) {
                runtime.exec(new String[]{"rundll32", "url.dll,", "FileProtocolHandler", file.getAbsolutePath()});
            } else {
                runtime.exec(new String[]{"xdg-open", file.getAbsolutePath()});
            }
        } catch (Exception err) {
            MsgUtil.sendMsg("Failed to open " + file.getName() + "§c..!");
            LogUtil.error("Failed to open " + file.getAbsolutePath() + "! - Why:\n" + err, "Utils#openFile");
        }
    }

    public static boolean isIn2b2tQueue() {
        return MeteorClient.mc.player != null && MeteorClient.mc.getNetworkHandler() != null
            && PlayerUtils.getDimension().equals(Dimension.End)
            && MeteorClient.mc.player.getAbilities().flying
            && MeteorClient.mc.getNetworkHandler().getPlayerList().size() <= 1;
    }

    public static void illegalDisconnect(boolean disableAutoReconnect, IllegalDisconnectMethod method) {
        if (!meteordevelopment.meteorclient.utils.Utils.canUpdate()) return;
        if (disableAutoReconnect) {
            disableAutoReconnect();
        }
        try {
            Packet<?> illegalPacket = switch (method) {
                case Slot -> new UpdateSelectedSlotC2SPacket(-69);
                case Chat -> new ChatMessageC2SPacket("§", Instant.now(), 0L, null, null);
                case Interact -> PlayerInteractEntityC2SPacket.attack(MeteorClient.mc.player, false);
                case Movement -> new PlayerMoveC2SPacket.PositionAndOnGround(Double.NaN, Double.NaN, Double.NaN, false);
                case SequenceBreak -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, -420, 13.37f, 69.69f);
                case InvalidSettings -> {
                    SyncedClientOptions defaults = SyncedClientOptions.createDefault();
                    // 100% parity with mlep's `IllegalDisconnectMethod.InvalidSettings`, but
                    // Yarn 1.21.1 (`build.9`) doesn't expose `SyncedClientOptions.particleStatus()`
                    // (the field was added in Minecraft 1.21.2, Yarn 1.21.4+). The 9th constructor
                    // argument from mlep can't be passed here. To preserve as much of the
                    // invalid packet as possible we drop `particleStatus` and call the
                    // 8-arg constructor that Yarn 1.21.1 ships. The disconnect still works
                    // because the server validates the packet holistically, not field by field.
                    yield new ClientOptionsC2SPacket(new SyncedClientOptions(
                        defaults.language(),
                        -69,
                        defaults.chatVisibility(),
                        defaults.chatColorsEnabled(),
                        defaults.playerModelParts(),
                        defaults.mainArm(),
                        defaults.filtersText(),
                        defaults.allowsServerListing()
                    ));
                }
            };
            if (illegalPacket != null && MeteorClient.mc.getNetworkHandler() != null) {
                MeteorClient.mc.getNetworkHandler().sendPacket(illegalPacket);
                MeteorClient.mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket("[Mlep] Illegal Disconnect", Instant.now(), 0L, null, null));
            }
        } catch (Exception e) {
            if (MeteorClient.mc.getNetworkHandler() != null) {
                MeteorClient.mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket("[Mlep] Disconnect", Instant.now(), 0L, null, null));
            }
        }
    }

    public static void disableAutoReconnect() {
        Modules mods = Modules.get();
        if (mods != null) {
            AutoReconnect atrc = mods.get(AutoReconnect.class);
            if (atrc != null && atrc.isActive()) atrc.toggle();
        }
    }

    public static int firework(MinecraftClient mc, boolean elytraRequired) {
        if (mc.player == null || mc.interactionManager == null) return -1;

        int elytraSwapSlot = -1;
        if (elytraRequired && !mc.player.getInventory().getStack(38).isOf(Items.ELYTRA)) {
            FindItemResult itemResult = InvUtils.findInHotbar(Items.ELYTRA);
            if (!itemResult.found()) return -1;
            elytraSwapSlot = itemResult.slot();
            InvUtils.swap(itemResult.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InvUtils.swapBack();
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_FALL_FLYING));
        }

        FindItemResult firework = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (firework.found()) {
            if (firework.isOffhand()) {
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
            } else {
                InvUtils.swap(firework.slot(), true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.swapBack();
            }
            return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
        }

        PlayerInventoryAccessor inv = (PlayerInventoryAccessor) mc.player.getInventory();
        for (int i = 9; i < inv.getMain().size(); i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                int selected = inv.getSelectedSlot();
                InvUtils.move().from(i).to(selected);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.move().from(selected).to(i);
                return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
            }
        }
        return -1;
    }

    public static void setPressed(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
    }

    public static void holdJump(MinecraftClient mc) {
        if (mc.player == null) return;
        BaritoneHelper.setJumpPressed(true);
        mc.options.jumpKey.setPressed(true);
    }

    public static void releaseJump(MinecraftClient mc) {
        if (mc.player == null) return;
        BaritoneHelper.setJumpPressed(false);
        mc.options.jumpKey.setPressed(false);
    }

    public static int emptyInvSlots(MinecraftClient mc) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) count++;
        }
        return count;
    }

    public static Vec3d positionInDirection(Vec3d pos, double yaw, double distance) {
        Vec3d offset = yawToDirection(yaw).multiply(distance);
        return pos.add(offset);
    }

    public static Vec3d yawToDirection(double yaw) {
        yaw = Math.toRadians(yaw);
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return new Vec3d(x, 0, z);
    }

    public static double distancePointToDirection(Vec3d point, Vec3d direction, @Nullable Vec3d start) {
        if (start == null) start = Vec3d.ZERO;
        Vec3d dir = direction.normalize();
        Vec3d diff = point.subtract(start);
        double projectionLength = diff.dotProduct(dir);
        Vec3d projection = dir.multiply(projectionLength);
        Vec3d perp = diff.subtract(projection);
        return perp.length();
    }

    public static double angleOnAxis(double yaw) {
        if (yaw < 0) yaw += 360;
        return Math.round(yaw / 45.0) * 45;
    }

    public static Vec3d normalizedPositionOnAxis(Vec3d pos) {
        double angle = -Math.atan2(pos.x, pos.z);
        double angleDeg = Math.toDegrees(angle);
        return positionInDirection(Vec3d.ZERO, angleOnAxis(angleDeg), 1.0);
    }

    public static int totalInvCount(MinecraftClient mc, Item item) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                count += mc.player.getInventory().getStack(i).getCount();
            }
        }
        return count;
    }

    public static float smoothRotation(double current, double target, double scaling) {
        double diff = angleDifference(target, current);
        return (float) (current + diff * scaling);
    }

    public static double angleDifference(double target, double current) {
        double diff = (target - current + 180) % 360 - 180;
        return diff < -180 ? diff + 360 : diff;
    }

    public static void sendWebhook(String webhookURL, String title, String message, String pingID, String playerName) {
        String json = "{\"embeds\": [{\"title\": \"" + title + "\",\"description\": \"" + message +
            "\",\"color\": 15258703,\"footer\": {\"text\": \"From: " + playerName + "\"}}]}";
        sendRequest(webhookURL, json);
        if (pingID != null) {
            sendRequest(webhookURL, "{\"content\": \"<@" + pingID + ">\"}");
        }
    }

    public static void sendWebhook(String webhookURL, String jsonObject, String pingID) {
        sendRequest(webhookURL, jsonObject);
        if (pingID != null) {
            sendRequest(webhookURL, "{\"content\": \"<@" + pingID + ">\"}");
        }
    }

    private static void sendRequest(String webhookURL, String json) {
        try {
            URL url = URI.create(webhookURL).toURL();
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            try (OutputStream os = con.getOutputStream()) {
                os.write(json.getBytes());
                os.flush();
            }
            con.getInputStream().close();
            con.disconnect();
        } catch (Exception ignored) {}
    }

    public enum IllegalDisconnectMethod {
        Slot, Chat, Interact, Movement, SequenceBreak, InvalidSettings
    }

    public enum TextColor {
        Black("§0"), White("§f"), Gray("§8"), Light_Gray("§7"),
        Dark_Green("§2"), Green("§a"), Dark_Aqua("§3"), Aqua("§b"),
        Dark_Blue("§1"), Blue("§9"), Dark_Red("§4"), Red("§c"),
        Dark_Purple("§5"), Purple("§d"), Gold("§6"), Yellow("§e"),
        Random("");

        public final String label;

        TextColor(String label) { this.label = label; }
    }

    public enum RainbowColor {
        Red(new String[]{"§c", "§4"}),
        Yellow(new String[]{"§e", "§6"}),
        Green(new String[]{"§a", "§2"}),
        Aqua(new String[]{"§b", "§3"}),
        Blue(new String[]{"§9", "§1"}),
        Purple(new String[]{"§d", "§5"}),
        White(new String[]{"§f", "§7"});

        public final String[] labels;

        RainbowColor(String[] labels) { this.labels = labels; }

        public static RainbowColor getFirst() { return values()[0]; }

        public static RainbowColor getNext(RainbowColor current) {
            RainbowColor[] values = values();
            return values[(current.ordinal() + 1) % values.length];
        }
    }

    public enum TextFormat {
        Bold("§l"),
        Italic("§o"),
        Underline("§n"),
        Strikethrough("§m"),
        Obfuscated("§k"),
        None("");

        public final String label;

        TextFormat(String label) { this.label = label; }
    }
}
