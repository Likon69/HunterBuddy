package com.hunterbuddy.modules.messenger;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.hud.MessengerBubbleHud;
import com.hunterbuddy.modules.messenger.ui.MessengerOverlay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.XAnchor;
import meteordevelopment.meteorclient.systems.hud.YAnchor;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public class Messenger extends Module {
    private static final long SAVE_INTERVAL_MS = 5000L;
    private static final int MESSAGE_LIMIT = 256;
    private static final int RAW_LIMIT = 300;
    private static final String METEOR_PREFIX = "[Meteor]";
    private static final String OWN_MARKER = "[Meteor] [Messenger]";

    private final MessengerSettings cfg = new MessengerSettings(settings, this::openLast);
    private final PrivateMessageParser parser = new PrivateMessageParser();
    private final OutgoingQueue outbox = new OutgoingQueue();
    private final MessengerOverlay overlay = new MessengerOverlay(this);
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();

    private String lastPeer;
    private boolean dirty;
    private boolean bubbleChecked;
    private long lastSave;

    public Messenger() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "messenger",
            "Private messages in small movable windows over the chat, with an unread counter button.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isActive() && dirty) save();
        }));
    }

    public static Messenger get() {
        Modules modules = Modules.get();
        return modules == null ? null : modules.get(Messenger.class);
    }

    public MessengerSettings cfg() {
        return cfg;
    }

    public MessengerOverlay overlay() {
        return overlay;
    }

    public static int messageLimit() {
        return MESSAGE_LIMIT;
    }

    @Override
    public void onActivate() {
        MessengerStore.Snapshot snapshot = MessengerStore.load();
        conversations.clear();
        if (cfg.keepHistory.get()) conversations.putAll(snapshot.conversations());
        enforceHistoryLimit();
        List<Conversation> recent = sortedConversations();
        lastPeer = recent.isEmpty() ? null : recent.get(0).peer();
        overlay.restore(snapshot.spots());
        outbox.clear();
        dirty = false;
        bubbleChecked = false;
    }

    @Override
    public void onDeactivate() {
        save();
        overlay.reset();
        outbox.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!bubbleChecked) ensureBubble();
        purgeIgnored();
        enforceHistoryLimit();

        if (mc.player != null) {
            Optional<OutgoingQueue.Pending> next = outbox.poll(System.currentTimeMillis(), cfg.sendInterval.get());
            next.ifPresent(p -> ChatUtils.sendPlayerMsg("/" + cfg.command() + " " + p.peer() + " " + p.text(), false));
        }

        if (dirty && System.currentTimeMillis() - lastSave >= SAVE_INTERVAL_MS) save();
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        Text raw = event.getMessage();
        String line = raw.getString();
        if (line.startsWith(METEOR_PREFIX) || line.contains(OWN_MARKER)) return;

        MessageIndicator indicator = event.getIndicator();
        int id = event.id;

        if (cfg.debugRaw.get()) logRaw(raw);

        Optional<PrivateMessage> parsed = parser.parse(raw, cfg);
        if (parsed.isEmpty()) return;

        PrivateMessage message = parsed.get();

        if (isIgnored(message.peer())) {
            if (message.incoming() && cfg.hideIgnoredFromMainChat.get()) event.cancel();
            return;
        }

        if (cfg.friendsOnly.get() && Friends.get().get(message.peer()) == null) return;

        boolean fresh = record(message);

        if (fresh && message.incoming() && cfg.chatNotice.get()) {
            if (cfg.streamMode.get()) info("%s wrote to you.", message.peer());
            else info("%s: %s", message.peer(), message.text());
            restore(event, raw, indicator, id);
        }

        if (cfg.hideFromMainChat.get()) event.cancel();
    }

    private void logRaw(Text raw) {
        String described = parser.describe(raw).replace('\n', ' ').replace('\r', ' ');
        if (described.length() > RAW_LIMIT) described = described.substring(0, RAW_LIMIT);
        HunterBuddyAddon.LOG.info("Messenger raw: {}", described);
    }

    private static void restore(ReceiveMessageEvent event, Text raw, MessageIndicator indicator, int id) {
        event.setMessage(raw);
        event.setIndicator(indicator);
        event.id = id;
    }

    private boolean record(PrivateMessage message) {
        long now = message.epochMillis();

        if (!message.incoming() && outbox.consumeEcho(message.peer(), message.text(), now)) return false;

        Conversation conversation = conversation(message.peer());
        conversation.add(message, cfg.historySize.get());
        lastPeer = conversation.peer();
        dirty = true;

        if (!message.incoming()) return true;

        conversation.addUnread();
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        if (chatOpen && cfg.autoOpen.get()) overlay.openWindow(conversation.peer(), false);

        playSound();
        return true;
    }

    public Conversation conversation(String peer) {
        return conversations.computeIfAbsent(key(peer), k -> new Conversation(peer));
    }

    public Conversation existing(String peer) {
        return conversations.get(key(peer));
    }

    public List<Conversation> sortedConversations() {
        List<Conversation> list = new ArrayList<>(conversations.values());
        list.sort(Comparator.comparingLong(Conversation::lastActivity).reversed());
        return list;
    }

    public int unreadTotal() {
        int total = 0;
        for (Conversation conversation : conversations.values()) total += conversation.unread();
        return total;
    }

    public boolean send(String peer, String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || mc.player == null) return false;

        if (!outbox.offer(peer, trimmed)) {
            warning("Too many messages waiting to be sent.");
            return false;
        }

        Conversation conversation = conversation(peer);
        conversation.add(new PrivateMessage(conversation.peer(), false, trimmed, System.currentTimeMillis()), cfg.historySize.get());
        lastPeer = conversation.peer();
        dirty = true;
        return true;
    }

    public void clear(String peer) {
        Conversation conversation = existing(peer);
        if (conversation != null) conversation.clear();
        dirty = true;
    }

    public void clearAll() {
        conversations.clear();
        lastPeer = null;
        overlay.closeAll();
        dirty = true;
    }

    public void remove(String peer) {
        conversations.remove(key(peer));
        if (lastPeer != null && lastPeer.equalsIgnoreCase(peer)) lastPeer = null;
        overlay.closeWindow(peer);
        dirty = true;
    }

    private void ensureBubble() {
        bubbleChecked = true;
        if (cfg.bubbleAdded.get()) return;

        Hud hud = Hud.get();
        boolean present = false;
        for (HudElement element : hud) {
            if (element instanceof MessengerBubbleHud) present = true;
        }

        if (!present) {
            hud.add(MessengerBubbleHud.INFO, -4, mc.getWindow().getFramebufferHeight() / 2, XAnchor.Right, YAnchor.Top);
            hud.save();
        }

        cfg.bubbleAdded.set(true);
        Modules.get().save();
    }

    private void enforceHistoryLimit() {
        int limit = cfg.historySize.get();
        for (Conversation conversation : conversations.values()) {
            if (conversation.trim(limit)) dirty = true;
        }
    }

    private void purgeIgnored() {
        if (cfg.ignoredNames.get().isEmpty()) return;

        List<String> gone = new ArrayList<>();
        for (Conversation conversation : conversations.values()) {
            if (isIgnored(conversation.peer())) gone.add(conversation.peer());
        }
        for (String peer : gone) remove(peer);
    }

    public void ignore(String peer) {
        if (!isIgnored(peer)) {
            cfg.ignoredNames.get().add(peer);
            cfg.ignoredNames.onChanged();
            Modules.get().save();
        }
        remove(peer);
    }

    public boolean unignore(String name) {
        boolean removed = cfg.ignoredNames.get().removeIf(entry -> entry.equalsIgnoreCase(name));
        if (removed) {
            cfg.ignoredNames.onChanged();
            Modules.get().save();
        }
        return removed;
    }

    public boolean isIgnored(String peer) {
        for (String name : cfg.ignoredNames.get()) {
            if (name.equalsIgnoreCase(peer)) return true;
        }
        return false;
    }

    public void markDirty() {
        dirty = true;
    }

    public void openChat(String peer) {
        conversation(peer);
        mc.setScreen(new ChatScreen("", false));
        overlay.openWindow(peer, false);
    }

    private void openLast() {
        if (!isActive() || mc.currentScreen != null || lastPeer == null) return;
        openChat(lastPeer);
    }

    private void save() {
        MessengerStore.save(new MessengerStore.Snapshot(conversations, overlay.spots()), cfg.keepHistory.get());
        dirty = false;
        lastSave = System.currentTimeMillis();
    }

    private void playSound() {
        SoundEvent event = switch (cfg.sound.get()) {
            case None -> null;
            case Pling -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case Bell -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case Orb -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
        };
        if (event == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(event, 1.0f, cfg.soundVolume.get().floatValue()));
    }

    private static String key(String peer) {
        return peer.toLowerCase(Locale.ROOT);
    }
}
