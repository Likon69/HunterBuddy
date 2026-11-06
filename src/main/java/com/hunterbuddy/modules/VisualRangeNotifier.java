package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.visualrange.AlertSink;
import com.hunterbuddy.modules.visualrange.DimensionNames;
import com.hunterbuddy.modules.visualrange.DisconnectMemory;
import com.hunterbuddy.modules.visualrange.DiscordActivitySettings;
import com.hunterbuddy.modules.visualrange.DiscordReporter;
import com.hunterbuddy.modules.visualrange.DiscordSettings;
import com.hunterbuddy.modules.visualrange.GroundItemBeam;
import com.hunterbuddy.modules.visualrange.GroundItemLabels;
import com.hunterbuddy.modules.visualrange.GroundItemSettings;
import com.hunterbuddy.modules.visualrange.GroundItemWatch;
import com.hunterbuddy.modules.visualrange.LeaveClassifier;
import com.hunterbuddy.modules.visualrange.PlayerRangeSettings;
import com.hunterbuddy.modules.visualrange.PlayerRangeWatch;
import com.hunterbuddy.modules.visualrange.PvpAnnouncer;
import com.hunterbuddy.modules.visualrange.PvpSettings;
import com.hunterbuddy.modules.visualrange.PvpWatch;
import com.hunterbuddy.modules.visualrange.RangeSound;
import com.hunterbuddy.modules.visualrange.RangeSoundSettings;
import com.hunterbuddy.modules.visualrange.WatchGuard;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.time.LocalTime;

public class VisualRangeNotifier extends Module {
    private static final String UNKNOWN_POSITION = "unknown";

    private final PlayerRangeSettings playerSettings = new PlayerRangeSettings(settings);
    private final GroundItemSettings itemSettings = new GroundItemSettings(settings);
    private final RangeSoundSettings soundSettings = new RangeSoundSettings(settings);
    private final DiscordSettings discordSettings = new DiscordSettings(settings, itemSettings);
    private final PvpSettings pvpSettings = new PvpSettings(settings);
    private final DiscordActivitySettings activitySettings = new DiscordActivitySettings(discordSettings);

    private final DiscordReporter reporter = new DiscordReporter(discordSettings);
    private final AlertSink alerts = new AlertSink(playerSettings, new RangeSound(soundSettings), reporter);
    private final PlayerRangeWatch playerWatch = new PlayerRangeWatch(playerSettings, discordSettings, alerts);
    private final GroundItemWatch itemWatch = new GroundItemWatch(itemSettings, discordSettings, alerts);
    private final GroundItemBeam itemBeam = new GroundItemBeam(itemSettings);
    private final GroundItemLabels itemLabels = new GroundItemLabels(itemSettings);
    private final PvpWatch pvpWatch = new PvpWatch(
        pvpSettings, new PvpAnnouncer(pvpSettings, playerSettings, activitySettings, alerts));
    private final WatchGuard guard = new WatchGuard();

    public VisualRangeNotifier() {
        super(
            HunterBuddyAddon.HUNT_CATEGORY,
            "visual-range-notifier",
            "Notifies when players enter visual range or selected items appear on the ground,"
                + " with optional Discord webhook alerts."
        );
    }

    public static void notifyDeath(Text message) {
        VisualRangeNotifier module = activeModule();
        if (module != null) module.reportDeath(message);
    }

    public static void notifyDisconnect(Text reason) {
        DisconnectMemory.remember(reason);
    }

    @Override
    public void onActivate() {
        clearWatches();
    }

    @Override
    public void onDeactivate() {
        clearWatches();
    }

    void onDisconnect(Text reason) {
        HunterBuddyAddon.LOG.info("[HB] VisualRange disconnect: {}", reason.getString());
        if (!reporter.ready(activitySettings.disconnect)) return;
        reporter.send("Disconnected", "Reason: " + reason.getString() + "\nTime: " + LocalTime.now().withNano(0));
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        clearWatches();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (playerSettings.playerEnter.get() || playerSettings.playerLeave.get()) {
            guard.run("players", playerWatch::tick);
        }
        if (itemSettings.itemNotify.get()) guard.run("items", itemWatch::tick);
        if (pvpSettings.anchorPop.get()) guard.run("anchors", pvpWatch::forgetVanishedAnchors);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || !itemSettings.itemBeam.get()) return;
        guard.run("item-beams", () -> itemBeam.render(event));
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!itemSettings.itemBeam.get() || !itemSettings.beamLabels.get()) return;
        guard.run("item-labels", () -> itemLabels.render(event));
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        pvpWatch.onPacket(event.packet);
    }

    private void clearWatches() {
        playerWatch.clear();
        itemWatch.clear();
        pvpWatch.clear();
    }

    private void reportDeath(Text message) {
        HunterBuddyAddon.LOG.info(
            "[HB] VisualRange death: master={} death={} url={}",
            discordSettings.enabled.get(),
            activitySettings.death.get(),
            !discordSettings.webhookUrl.get().isBlank());
        if (!reporter.ready(activitySettings.death)) return;
        reporter.send("You Died", message.getString()
            + "\nPosition: " + deathPosition() + "\nDimension: " + DimensionNames.current());
    }

    private static String deathPosition() {
        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player == null) return UNKNOWN_POSITION;
        return String.format("%.0f, %.0f, %.0f", player.getX(), player.getY(), player.getZ());
    }

    private static VisualRangeNotifier activeModule() {
        Modules modules = Modules.get();
        VisualRangeNotifier module = modules == null ? null : modules.get(VisualRangeNotifier.class);
        return module != null && module.isActive() ? module : null;
    }

    private static boolean isModuleActive() {
        return activeModule() != null;
    }

    private static void reportDisconnect(Text reason) {
        VisualRangeNotifier module = activeModule();
        if (module != null) module.onDisconnect(reason);
    }

    public static final class Hooks {
        private final LeaveClassifier classifier = new LeaveClassifier();

        @EventHandler
        private void onGameJoined(GameJoinedEvent event) {
            classifier.joined();
        }

        @EventHandler
        private void onGameLeft(GameLeftEvent event) {
            classifier.left(VisualRangeNotifier::reportDisconnect, VisualRangeNotifier::isModuleActive);
        }
    }
}
