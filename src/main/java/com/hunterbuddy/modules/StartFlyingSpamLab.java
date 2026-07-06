package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class StartFlyingSpamLab extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLogging = settings.createGroup("Logging");

    private final Setting<SpamMode> mode = sgGeneral.add(new EnumSetting.Builder<SpamMode>()
        .name("mode")
        .description("START_FALL_FLYING spam strategy to test.")
        .defaultValue(SpamMode.ObserveOnly)
        .build()
    );

    private final Setting<Integer> pulseTicks = sgGeneral.add(new IntSetting.Builder()
        .name("pulse-ticks")
        .description("Ticks between START_FALL_FLYING sends.")
        .defaultValue(5)
        .min(1)
        .max(40)
        .sliderRange(1, 20)
        .visible(() -> mode.get() != SpamMode.ObserveOnly && mode.get() != SpamMode.WithRocket)
        .build()
    );

    private final Setting<Boolean> requireRocket = sgGeneral.add(new BoolSetting.Builder()
        .name("require-rocket")
        .description("Only run while a firework rocket is in hotbar or offhand.")
        .defaultValue(false)
        .visible(() -> mode.get() != SpamMode.ObserveOnly && mode.get() != SpamMode.WithRocket)
        .build()
    );

    private final Setting<Boolean> chatSummary = sgLogging.add(new BoolSetting.Builder()
        .name("chat-summary")
        .description("Print a short chat line on each START_FALL_FLYING send.")
        .defaultValue(false)
        .build()
    );

    private int sendCount;
    private File csvFile;
    private int ticks;
    private int totalCorrections;

    public StartFlyingSpamLab() {
        super(HunterBuddyAddon.LAB_CATEGORY, "start-flying-spam-lab", "Spam-tests START_FALL_FLYING timings to probe server tolerance.");
    }

    @Override
    public void onActivate() {
        sendCount = 0;
        ticks = 0;
        totalCorrections = 0;
        csvFile = createCsvFile();
        writeHeader();
        if (csvFile != null) info("Logging StartFlyingSpamLab samples to %s", csvFile.getName());
    }

    @Override
    public void onDeactivate() {
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            totalCorrections++;
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerInteractItemC2SPacket) {
            if (mc.player == null) return;
            if (mode.get() != SpamMode.WithRocket) return;
            if (!mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)
                && !mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) return;
            sendStartFlying("WithRocket");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        ticks++;

        SpamMode currentMode = mode.get();
        if (currentMode == SpamMode.ObserveOnly || currentMode == SpamMode.WithRocket) return;
        if (ticks % pulseTicks.get() != 0) return;
        if (requireRocket.get() && !hasRocket()) return;

        boolean gliding = mc.player.isGliding();

        switch (currentMode) {
            case PulseTick -> sendStartFlying("PulseTick");
            case OnlyIfNotGliding -> { if (!gliding) sendStartFlying("OnlyIfNotGliding"); }
            case PulseWhileGliding -> { if (gliding) sendStartFlying("PulseWhileGliding"); }
        }
    }

    private void sendStartFlying(String modeLabel) {
        boolean glidingBefore = mc.player.isGliding();
        boolean onGroundBefore = mc.player.isOnGround();
        Vec3d velBefore = mc.player.getVelocity();

        if (mc.getNetworkHandler() != null && mc.player != null) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }

        boolean glidingAfter = mc.player.isGliding();
        boolean onGroundAfter = mc.player.isOnGround();
        Vec3d velAfter = mc.player.getVelocity();

        Spam spam = new Spam(
            System.currentTimeMillis(),
            modeLabel,
            glidingBefore, glidingAfter,
            onGroundBefore, onGroundAfter,
            velBefore.x, velBefore.y, velBefore.z,
            velAfter.x, velAfter.y, velAfter.z,
            totalCorrections
        );
        sendCount++;
        writeSpam(spam);

        if (chatSummary.get()) {
            info("Sent START_FALL_FLYING [%s] glide %s->%s ground %s->%s corr=%d",
                modeLabel, glidingBefore, glidingAfter, onGroundBefore, onGroundAfter, totalCorrections);
        }
    }

    private boolean hasRocket() {
        return mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET) || mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET);
    }

    private File createCsvFile() {
        try {
            File dir = new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "start-flying-spam-lab");
            dir.mkdirs();
            return new File(dir, "spam-" + System.currentTimeMillis() + ".csv");
        } catch (Exception e) {
            error("Failed to create StartFlyingSpamLab log file: %s", e.getMessage());
            return null;
        }
    }

    private void writeHeader() {
        if (csvFile == null) return;
        try (Writer writer = new FileWriter(csvFile, false)) {
            writer.write("timestamp,mode,gliding_before,gliding_after,onground_before,onground_after,vel_before_x,vel_before_y,vel_before_z,vel_after_x,vel_after_y,vel_after_z,total_corrections\n");
        } catch (IOException e) {
            error("Failed to write StartFlyingSpamLab header: %s", e.getMessage());
        }
    }

    private void writeSpam(Spam spam) {
        if (csvFile == null) return;
        try (Writer writer = new FileWriter(csvFile, true)) {
            writer.write(spam.toCsv());
            writer.write('\n');
        } catch (IOException e) {
            error("Failed to write StartFlyingSpamLab sample: %s", e.getMessage());
        }
    }

    @Override
    public String getInfoString() {
        return sendCount + " sends | " + totalCorrections + " corr";
    }

    public enum SpamMode {
        ObserveOnly,
        PulseTick,
        OnlyIfNotGliding,
        PulseWhileGliding,
        WithRocket
    }

    private class Spam {
        final long timestamp;
        final String mode;
        final boolean glidingBefore, glidingAfter;
        final boolean onGroundBefore, onGroundAfter;
        final double velBX, velBY, velBZ;
        final double velAX, velAY, velAZ;
        final int totalCorrections;

        Spam(long timestamp, String mode,
             boolean glidingBefore, boolean glidingAfter,
             boolean onGroundBefore, boolean onGroundAfter,
             double velBX, double velBY, double velBZ,
             double velAX, double velAY, double velAZ,
             int totalCorrections) {
            this.timestamp = timestamp;
            this.mode = mode;
            this.glidingBefore = glidingBefore;
            this.glidingAfter = glidingAfter;
            this.onGroundBefore = onGroundBefore;
            this.onGroundAfter = onGroundAfter;
            this.velBX = velBX;
            this.velBY = velBY;
            this.velBZ = velBZ;
            this.velAX = velAX;
            this.velAY = velAY;
            this.velAZ = velAZ;
            this.totalCorrections = totalCorrections;
        }

        String toCsv() {
            return timestamp + ","
                + mode + ","
                + (glidingBefore ? 1 : 0) + ","
                + (glidingAfter ? 1 : 0) + ","
                + (onGroundBefore ? 1 : 0) + ","
                + (onGroundAfter ? 1 : 0) + ","
                + fmt(velBX) + "," + fmt(velBY) + "," + fmt(velBZ) + ","
                + fmt(velAX) + "," + fmt(velAY) + "," + fmt(velAZ) + ","
                + totalCorrections;
        }

        String fmt(double value) {
            if (Double.isNaN(value)) return "";
            return String.format(java.util.Locale.ROOT, "%.3f", value);
        }
    }
}