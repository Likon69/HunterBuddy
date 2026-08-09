package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.events.PlayerDeathEvent;
import com.hunterbuddy.events.ServerDisconnectEvent;
import com.hunterbuddy.modules.regear.util.MsgUtil;
import com.hunterbuddy.modules.regear.util.Utils;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.network.PlayerListEntry;

public class VisualRangeNotifier extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> chatNotify = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Show chat messages for notifications.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Don't notify for Meteor friends.")
        .defaultValue(false)
        .build());

    private final SettingGroup sgPlayers = settings.createGroup("Players");

    private final Setting<Boolean> playerEnter = sgPlayers.add(new BoolSetting.Builder()
        .name("player-enter")
        .description("Notify when a player enters visual range.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> playerLeave = sgPlayers.add(new BoolSetting.Builder()
        .name("player-leave")
        .description("Notify when a player leaves visual range.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> playerCoords = sgPlayers.add(new BoolSetting.Builder()
        .name("player-coords")
        .description("Include their coordinates in the notification.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> playerEquipment = sgPlayers.add(new BoolSetting.Builder()
        .name("player-equipment")
        .description("Include visible equipment in the enter notification.")
        .defaultValue(true)
        .visible(playerEnter::get)
        .build());

    private final SettingGroup sgItems = settings.createGroup("Ground Items");

    private final Setting<Boolean> itemNotify = sgItems.add(new BoolSetting.Builder()
        .name("item-notify")
        .description("Notify when selected items appear on the ground nearby.")
        .defaultValue(false)
        .build());

    private final Setting<List<Item>> trackedItems = sgItems.add(new ItemListSetting.Builder()
        .name("tracked-items")
        .description("Items to notify about when found on the ground.")
        .defaultValue(List.of(
            Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS,
            Items.ENCHANTED_GOLDEN_APPLE, Items.TOTEM_OF_UNDYING, Items.ELYTRA,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS, Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE
        )).visible(itemNotify::get)
        .build());

    private final Setting<Integer> itemCooldown = sgItems.add(new IntSetting.Builder()
        .name("item-cooldown")
        .description("Seconds before the same item entity triggers another notification.")
        .defaultValue(30)
        .min(5)
        .sliderRange(5, 120)
        .visible(itemNotify::get)
        .build());

    private final SettingGroup sgSound = settings.createGroup("Sound");

    private final Setting<Boolean> soundEnabled = sgSound.add(new BoolSetting.Builder()
        .name("sound-enabled")
        .description("Play a sound on player enter notification.")
        .defaultValue(true)
        .build());

    private final Setting<List<SoundEvent>> sound = sgSound.add(new SoundEventListSetting.Builder()
        .name("sound")
        .description("Sound to play.")
        .defaultValue(List.of(SoundEvents.ENTITY_PLAYER_LEVELUP)).visible(soundEnabled::get)
        .build());

    private final Setting<Double> soundVolume = sgSound.add(new DoubleSetting.Builder()
        .name("sound-volume")
        .description("Volume of the notification sound.")
        .defaultValue(1.0)
        .min(0.0)
        .max(2.0)
        .sliderRange(0.0, 2.0)
        .visible(soundEnabled::get)
        .build());

    private final Setting<Double> soundPitch = sgSound.add(new DoubleSetting.Builder()
        .name("sound-pitch")
        .description("Pitch of the notification sound.")
        .defaultValue(1.0)
        .min(0.5)
        .max(2.0)
        .sliderRange(0.5, 2.0)
        .visible(soundEnabled::get)
        .build());

    private final SettingGroup sgDiscord = settings.createGroup("Discord");

    private final Setting<Boolean> discordEnabled = sgDiscord.add(new BoolSetting.Builder()
        .name("discord-webhook")
        .description("Send notifications to a Discord webhook.")
        .defaultValue(false)
        .build());

    private final Setting<String> webhookUrl = sgDiscord.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL.")
        .defaultValue("")
        .visible(discordEnabled::get)
        .build());

    private final Setting<Boolean> discordPlayers = sgDiscord.add(new BoolSetting.Builder()
        .name("discord-players")
        .description("Send player enter/leave events to Discord.")
        .defaultValue(true)
        .visible(discordEnabled::get)
        .build());

    private final Setting<Boolean> discordItems = sgDiscord.add(new BoolSetting.Builder()
        .name("discord-items")
        .description("Send ground item events to Discord.")
        .defaultValue(true)
        .visible(() -> discordEnabled.get() && itemNotify.get())
        .build());

    private final Setting<String> pingId = sgDiscord.add(new StringSetting.Builder()
        .name("ping-id")
        .description("Discord user ID to ping. Leave blank to disable.")
        .defaultValue("")
        .visible(discordEnabled::get)
        .build());

    private final SettingGroup sgPvp = settings.createGroup("PvP Activity");

    private final Setting<Boolean> crystalPlace = sgPvp.add(new BoolSetting.Builder()
        .name("crystal-place")
        .description("Notify when a player places an end crystal nearby.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> crystalPop = sgPvp.add(new BoolSetting.Builder()
        .name("crystal-pop")
        .description("Notify when a player pops (destroys) an end crystal nearby.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> anchorPlace = sgPvp.add(new BoolSetting.Builder()
        .name("anchor-place")
        .description("Notify when a respawn anchor is placed nearby.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> anchorPop = sgPvp.add(new BoolSetting.Builder()
        .name("anchor-pop")
        .description("Notify when a respawn anchor is destroyed (exploded) nearby.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> pvpRange = sgPvp.add(new IntSetting.Builder()
        .name("pvp-range")
        .description("Maximum range to attribute crystal/anchor activity to a player.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 20)
        .build());

    private final Setting<Boolean> pvpIgnoreSelf = sgPvp.add(new BoolSetting.Builder()
        .name("pvp-ignore-self")
        .description("Don't notify for your own crystal/anchor activity.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> discordPvp = sgDiscord.add(new BoolSetting.Builder()
        .name("discord-pvp")
        .description("Send crystal/anchor activity to Discord.")
        .defaultValue(true)
        .visible(discordEnabled::get)
        .build());

    private final Setting<Boolean> discordDeath = sgDiscord.add(new BoolSetting.Builder()
        .name("discord-death")
        .description("Send your own death to Discord, with the server's own wording so the killer and the cause come along.")
        .defaultValue(true)
        .visible(discordEnabled::get)
        .build());

    private final Setting<Boolean> discordDisconnect = sgDiscord.add(new BoolSetting.Builder()
        .name("discord-disconnect")
        .description("Send every disconnect to Discord — kicks, timeouts and AutoLogPlus alike. Leaving through the menu counts too.")
        .defaultValue(true)
        .visible(discordEnabled::get)
        .build());

    private final Set<UUID> trackedPlayers = new HashSet<>();
    private final Map<UUID, String> uuidNameCache = new HashMap<>();
    private final Map<Integer, Long> notifiedItems = new HashMap<>();
    private final Set<BlockPos> trackedAnchors = new HashSet<>();

    public VisualRangeNotifier() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "visual-range-notifier",
            "Notifies when players enter visual range or selected items appear on the ground with optional Discord webhook alerts.");
    }

    @Override
    public void onActivate() {
        trackedPlayers.clear();
        uuidNameCache.clear();
        notifiedItems.clear();
        trackedAnchors.clear();
    }

    @Override
    public void onDeactivate() {
        trackedPlayers.clear();
        uuidNameCache.clear();
        notifiedItems.clear();
        trackedAnchors.clear();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        trackedPlayers.clear();
        uuidNameCache.clear();
        notifiedItems.clear();
        trackedAnchors.clear();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (playerEnter.get() || playerLeave.get()) tickPlayers();
        if (itemNotify.get()) tickItems();
        if (anchorPop.get()) tickAnchorTracking();
    }

    @EventHandler
    public void onReceivePacket(Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof BundleS2CPacket bundle) {
            for (Packet<?> subPacket : bundle.getPackets()) {
                handleInboundPacket(subPacket);
            }
        } else {
            handleInboundPacket(event.packet);
        }
    }

    private void handleInboundPacket(Packet<?> packet) {
        if (packet instanceof EntitySpawnS2CPacket spawn && spawn.getEntityType() == EntityType.END_CRYSTAL) {
            handleCrystalSpawn(spawn);
        } else if (packet instanceof EntitiesDestroyS2CPacket destroy) {
            handleEntitiesDestroy(destroy);
        } else if (packet instanceof BlockUpdateS2CPacket blockUpdate) {
            handleBlockUpdate(blockUpdate);
        }
    }

    private void tickPlayers() {
        Set<UUID> currentPlayers = new HashSet<>();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && player != mc.player) {
                UUID uuid = player.getUuid();
                currentPlayers.add(uuid);
                uuidNameCache.put(uuid, player.getGameProfile().name());

                if (playerEnter.get() && !trackedPlayers.contains(uuid) &&
                    (!ignoreFriends.get() || !Friends.get().isFriend(player))) {

                    String name = player.getGameProfile().name();
                    int x = (int) player.getX();
                    int y = (int) player.getY();
                    int z = (int) player.getZ();
                    String equipStr = playerEquipment.get() ? getEquipmentString(player) : null;

                    if (chatNotify.get()) {
                        StringBuilder msg = new StringBuilder();
                        msg.append("§a").append(name).append(" §7entered visual range");
                        if (playerCoords.get()) msg.append(" at §f").append(x).append(" ").append(y).append(" ").append(z);
                        if (equipStr != null && !equipStr.isEmpty()) {
                            msg.append("\n §8└ §7Gear: §f").append(equipStr);
                        }
                        MsgUtil.sendMsg(msg.toString());
                    }

                    playNotifSound();

                    if (discordEnabled.get() && discordPlayers.get() && !webhookUrl.get().isEmpty()) {
                        sendDiscordAsync("Player Entered Visual Range",
                            buildPlayerMessage(name, "entered", x, y, z, equipStr));
                    }
                }
            }
        }

        if (playerLeave.get()) {
            for (UUID uuid : trackedPlayers) {
                if (!currentPlayers.contains(uuid)) {
                    if (ignoreFriends.get()) {
                        PlayerListEntry entry = mc.getNetworkHandler() != null ?
                            mc.getNetworkHandler().getPlayerListEntry(uuid) : null;
                        if (entry != null && Friends.get().get(entry.getProfile().name()) != null) continue;
                    }

                    String name = getNameFromUuid(uuid);
                    if (chatNotify.get()) {
                        MsgUtil.sendMsg("§c" + name + " §7left visual range.");
                    }

                    if (discordEnabled.get() && discordPlayers.get() && !webhookUrl.get().isEmpty()) {
                        sendDiscordAsync("Player Left Visual Range",
                            "**" + name + "** left visual range\nDimension: " + getDimension());
                    }
                }
            }
        }

        trackedPlayers.clear();
        trackedPlayers.addAll(currentPlayers);
    }

    private void tickItems() {
        long now = System.currentTimeMillis();
        long cooldownMs = (long) itemCooldown.get() * 1000L;
        notifiedItems.entrySet().removeIf(e -> now - e.getValue() > cooldownMs);

        List<Item> watchList = trackedItems.get();
        if (watchList.isEmpty()) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                Item item = itemEntity.getStack().getItem();
                if (!watchList.contains(item)) continue;

                int entityId = itemEntity.getId();
                if (notifiedItems.containsKey(entityId)) continue;

                notifiedItems.put(entityId, now);

                int count = itemEntity.getStack().getCount();
                String itemName = itemEntity.getStack().getName().getString();
                int x = (int) itemEntity.getX();
                int y = (int) itemEntity.getY();
                int z = (int) itemEntity.getZ();

                if (chatNotify.get()) {
                    String countStr = count > 1 ? " x" + count : "";
                    MsgUtil.sendMsg("§d" + itemName + countStr + " §7found on ground at §f" + x + " " + y + " " + z);
                }

                playNotifSound();

                if (discordEnabled.get() && discordItems.get() && !webhookUrl.get().isEmpty()) {
                    String countStr = count > 1 ? " x" + count : "";
                    String message = "**" + itemName + countStr + "** found on ground\nCoords: " +
                        x + " " + y + " " + z + "\nDimension: " + getDimension();
                    sendDiscordAsync("Ground Item Alert", message);
                }
            }
        }
    }

    private void handleCrystalSpawn(EntitySpawnS2CPacket packet) {
        if (!crystalPlace.get()) return;

        Vec3d pos = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
        PlayerEntity nearest = findNearestPlayer(pos);
        String name = nearest != null ? nearest.getGameProfile().name() : "Unknown";

        int x = (int) pos.x;
        int y = (int) pos.y;
        int z = (int) pos.z;

        if (chatNotify.get()) {
            MsgUtil.sendMsg("§d" + name + " §7placed an §dEnd Crystal §7at §f" + x + " " + y + " " + z);
        }

        playNotifSound();

        if (discordEnabled.get() && discordPvp.get() && !webhookUrl.get().isEmpty()) {
            sendDiscordAsync("End Crystal Placed",
                "**" + name + "** placed an End Crystal\nCoords: " + x + " " + y + " " + z + "\nDimension: " + getDimension());
        }
    }

    private void handleEntitiesDestroy(EntitiesDestroyS2CPacket packet) {
        if (!crystalPop.get()) return;

        IntListIterator iterator = packet.getEntityIds().iterator();
        while (iterator.hasNext()) {
            int id = iterator.nextInt();
            Entity entity = mc.world.getEntityById(id);
            if (!(entity instanceof EndCrystalEntity)) continue;

            Vec3d pos = entity.getEntityPos();
            PlayerEntity nearest = findNearestPlayer(pos);
            String name = nearest != null ? nearest.getGameProfile().name() : "Unknown";

            int x = (int) pos.x;
            int y = (int) pos.y;
            int z = (int) pos.z;

            if (chatNotify.get()) {
                MsgUtil.sendMsg("§c" + name + " §7popped an §cEnd Crystal §7at §f" + x + " " + y + " " + z);
            }

            playNotifSound();

            if (discordEnabled.get() && discordPvp.get() && !webhookUrl.get().isEmpty()) {
                sendDiscordAsync("End Crystal Popped",
                    "**" + name + "** popped an End Crystal\nCoords: " + x + " " + y + " " + z + "\nDimension: " + getDimension());
            }
        }
    }

    private void handleBlockUpdate(BlockUpdateS2CPacket packet) {
        BlockPos pos = packet.getPos();
        boolean isAnchor = packet.getState().getBlock() instanceof net.minecraft.block.RespawnAnchorBlock;

        if (isAnchor && !trackedAnchors.contains(pos)) {
            trackedAnchors.add(pos);

            if (anchorPlace.get()) {
                Vec3d center = Vec3d.ofCenter(pos);
                PlayerEntity nearest = findNearestPlayer(center);
                String name = nearest != null ? nearest.getGameProfile().name() : "Unknown";

                if (chatNotify.get()) {
                    MsgUtil.sendMsg("§d" + name + " §7placed a §dRespawn Anchor §7at §f" +
                        pos.getX() + " " + pos.getY() + " " + pos.getZ());
                }

                playNotifSound();

                if (discordEnabled.get() && discordPvp.get() && !webhookUrl.get().isEmpty()) {
                    sendDiscordAsync("Respawn Anchor Placed",
                        "**" + name + "** placed a Respawn Anchor\nCoords: " +
                            pos.getX() + " " + pos.getY() + " " + pos.getZ() + "\nDimension: " + getDimension());
                }
            }
        } else if (!isAnchor && trackedAnchors.remove(pos) && anchorPop.get()) {
            Vec3d center = Vec3d.ofCenter(pos);
            PlayerEntity nearest = findNearestPlayer(center);
            String name = nearest != null ? nearest.getGameProfile().name() : "Unknown";

            if (chatNotify.get()) {
                MsgUtil.sendMsg("§c" + name + " §7popped a §cRespawn Anchor §7at §f" +
                    pos.getX() + " " + pos.getY() + " " + pos.getZ());
            }

            playNotifSound();

            if (discordEnabled.get() && discordPvp.get() && !webhookUrl.get().isEmpty()) {
                sendDiscordAsync("Respawn Anchor Exploded",
                    "**" + name + "** popped a Respawn Anchor\nCoords: " +
                        pos.getX() + " " + pos.getY() + " " + pos.getZ() + "\nDimension: " + getDimension());
            }
        }
    }

    private void tickAnchorTracking() {
        trackedAnchors.removeIf(pos ->
            !(mc.world.getBlockState(pos).getBlock() instanceof net.minecraft.block.RespawnAnchorBlock));
    }

    private PlayerEntity findNearestPlayer(Vec3d pos) {
        double maxRange = pvpRange.get();
        PlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (pvpIgnoreSelf.get() && player == mc.player) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;

            double dist = player.getEntityPos().distanceTo(pos);
            if (dist <= maxRange && dist < nearestDist) {
                nearest = player;
                nearestDist = dist;
            }
        }
        return nearest;
    }

    private String buildPlayerMessage(String name, String action, int x, int y, int z, String equipment) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(name).append("** ").append(action).append(" visual range");
        if (playerCoords.get()) sb.append("\\nCoords: ").append(x).append(" ").append(y).append(" ").append(z);
        sb.append("\\nDimension: ").append(getDimension());
        if (equipment != null && !equipment.isEmpty()) sb.append("\\nGear: ").append(equipment);
        return sb.toString();
    }

    private String getEquipmentString(PlayerEntity player) {
        List<String> parts = new ArrayList<>();

        ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack legs = player.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();

        if (!head.isEmpty()) parts.add(head.getName().getString());
        if (!chest.isEmpty()) parts.add(chest.getName().getString());
        if (!legs.isEmpty()) parts.add(legs.getName().getString());
        if (!feet.isEmpty()) parts.add(feet.getName().getString());
        if (!mainHand.isEmpty()) parts.add(mainHand.getName().getString() + " (hand)");
        if (!offHand.isEmpty()) parts.add(offHand.getName().getString() + " (off)");

        return String.join(", ", parts);
    }

    private void playNotifSound() {
        if (!soundEnabled.get() || sound.get().isEmpty()) return;

        mc.getSoundManager().play(PositionedSoundInstance.master(
            sound.get().get(0),
            soundPitch.get().floatValue(),
            soundVolume.get().floatValue()
        ));
    }

    @EventHandler
    private void onPlayerDeath(PlayerDeathEvent event) {
        if (!discordEnabled.get() || !discordDeath.get() || webhookUrl.get().isEmpty()) return;

        // Snapshot now: by the time the request leaves, the respawn screen has
        // already moved the player somewhere else.
        String where = mc.player != null
            ? String.format("%.0f, %.0f, %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
            : "unknown";

        sendDiscordAsync("You Died",
            event.message().getString() + "\nPosition: " + where + "\nDimension: " + getDimension());
    }

    @EventHandler
    private void onServerDisconnect(ServerDisconnectEvent event) {
        if (!discordEnabled.get() || !discordDisconnect.get() || webhookUrl.get().isEmpty()) return;

        // AutoLogPlus writes its own reason into this text, so a logout it caused
        // arrives already labelled without either module knowing about the other.
        sendDiscordAsync("Disconnected",
            "Reason: " + event.reason().getString()
                + "\nTime: " + java.time.LocalTime.now().withNano(0));
    }

    private void sendDiscordAsync(String title, String message) {
        String url = webhookUrl.get();
        String ping = pingId.get().isEmpty() ? null : pingId.get();
        // The player is already being torn down while a disconnect is dispatched,
        // so the session is the only name still available at that point.
        String sender = mc.player != null
            ? mc.player.getGameProfile().name()
            : (mc.getSession() != null ? mc.getSession().getUsername() : "Unknown");

        MeteorExecutor.execute(() -> Utils.sendWebhook(url, title, message, ping, sender));
    }

    private String getDimension() {
        if (mc.world == null) return "Unknown";

        String dim = mc.world.getRegistryKey().getValue().getPath();
        return switch (dim) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "The End";
            default -> dim;
        };
    }

    private String getNameFromUuid(UUID uuid) {
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) return entry.getProfile().name();
        }
        return uuidNameCache.getOrDefault(uuid, uuid.toString());
    }
}