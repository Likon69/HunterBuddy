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
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class SlotUseDesyncLab extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSequence = settings.createGroup("Sequence");
    private final SettingGroup sgLogging = settings.createGroup("Logging");

    private final Setting<DesyncMode> mode = sgGeneral.add(new EnumSetting.Builder<DesyncMode>()
        .name("mode")
        .description("Slot/use sequence to test for desync.")
        .defaultValue(DesyncMode.ObserveOnly)
        .build()
    );

    private final Setting<Integer> autoRunInterval = sgSequence.add(new IntSetting.Builder()
        .name("auto-run-interval")
        .description("Ticks between auto-run test sequences.")
        .defaultValue(80)
        .min(20)
        .max(2400)
        .sliderRange(40, 200)
        .visible(() -> mode.get() != DesyncMode.ObserveOnly)
        .build()
    );

    private final Setting<Integer> rapidSwapCount = sgSequence.add(new IntSetting.Builder()
        .name("rapid-swap-count")
        .description("Number of rapid slot swaps before using rocket (RapidSwapUse mode).")
        .defaultValue(3)
        .min(1)
        .max(10)
        .sliderRange(1, 6)
        .visible(() -> mode.get() == DesyncMode.RapidSwapUse)
        .build()
    );

    private final Setting<Integer> swapTargetSlot = sgSequence.add(new IntSetting.Builder()
        .name("swap-target-slot")
        .description("Hotbar slot to swap to after use (UseThenSwap mode).")
        .defaultValue(0)
        .min(0)
        .max(8)
        .sliderRange(0, 8)
        .visible(() -> mode.get() == DesyncMode.UseThenSwap)
        .build()
    );

    private final Setting<Boolean> requireRocket = sgGeneral.add(new BoolSetting.Builder()
        .name("require-rocket")
        .description("Only run while a firework rocket is in hotbar or offhand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatSummary = sgLogging.add(new BoolSetting.Builder()
        .name("chat-summary")
        .description("Print a short chat line on each sample.")
        .defaultValue(false)
        .build()
    );

    private int sampleCount;
    private File csvFile;
    private int ticks;
    private int totalCorrections;

    public SlotUseDesyncLab() {
        super(HunterBuddyAddon.LAB_CATEGORY, "slot-use-desync-lab", "Tests slot-swap / use-item orderings for server-side desync.");
    }

    @Override
    public void onActivate() {
        sampleCount = 0;
        ticks = 0;
        totalCorrections = 0;
        csvFile = createCsvFile();
        writeHeader();
        if (csvFile != null) info("Logging SlotUseDesyncLab samples to %s", csvFile.getName());
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
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        ticks++;

        DesyncMode currentMode = mode.get();
        if (currentMode == DesyncMode.ObserveOnly) return;
        if (ticks % autoRunInterval.get() != 0) return;
        if (requireRocket.get() && !hasRocket()) return;

        runSequence();
    }

    private void runSequence() {
        int originalSlot = mc.player.getInventory().getSelectedSlot();
        int rocketSlot = findRocketInHotbar();
        if (rocketSlot < 0) return;

        boolean glidingBefore = mc.player.isGliding();
        Vec3d velBefore = mc.player.getVelocity();

        DesyncMode currentMode = mode.get();

        switch (currentMode) {
            case GhostSwap -> {
                InvUtils.swap(rocketSlot, false);
                InvUtils.swap(originalSlot, false);
            }
            case SwapUseSwap -> {
                InvUtils.swap(rocketSlot, true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.swapBack();
            }
            case RapidSwapUse -> {
                for (int i = 0; i < rapidSwapCount.get(); i++) {
                    InvUtils.swap(rocketSlot, false);
                    InvUtils.swap(originalSlot, false);
                }
                InvUtils.swap(rocketSlot, false);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            case UseThenSwap -> {
                if (!mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) return;
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.swap(swapTargetSlot.get(), false);
            }
        }

        recordSample(currentMode.name(), originalSlot, rocketSlot, mc.player.getInventory().getSelectedSlot(),
            glidingBefore, mc.player.isGliding(), velBefore, mc.player.getVelocity());
    }

    private void recordSample(String modeLabel, int slotBefore, int slotTarget, int slotAfter,
                              boolean glidingBefore, boolean glidingAfter,
                              Vec3d velBefore, Vec3d velAfter) {
        Sample sample = new Sample(
            System.currentTimeMillis(),
            modeLabel,
            slotBefore, slotTarget, slotAfter,
            glidingBefore, glidingAfter,
            velBefore.x, velBefore.y, velBefore.z,
            velAfter.x, velAfter.y, velAfter.z,
            totalCorrections
        );
        sampleCount++;
        writeSample(sample);
        if (chatSummary.get()) {
            info("[%s] slot %d->%d->%d glide %s->%s corr=%d",
                modeLabel, slotBefore, slotTarget, slotAfter, glidingBefore, glidingAfter, totalCorrections);
        }
    }

    private boolean hasRocket() {
        return mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET) || mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET);
    }

    private int findRocketInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) return i;
        }
        return -1;
    }

    private File createCsvFile() {
        try {
            File dir = new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "slot-use-desync-lab");
            dir.mkdirs();
            return new File(dir, "desync-" + System.currentTimeMillis() + ".csv");
        } catch (Exception e) {
            error("Failed to create SlotUseDesyncLab log file: %s", e.getMessage());
            return null;
        }
    }

    private void writeHeader() {
        if (csvFile == null) return;
        try (Writer writer = new FileWriter(csvFile, false)) {
            writer.write("timestamp,mode,slot_before,slot_target,slot_after,gliding_before,gliding_after,vel_before_x,vel_before_y,vel_before_z,vel_after_x,vel_after_y,vel_after_z,total_corrections\n");
        } catch (IOException e) {
            error("Failed to write SlotUseDesyncLab header: %s", e.getMessage());
        }
    }

    private void writeSample(Sample sample) {
        if (csvFile == null) return;
        try (Writer writer = new FileWriter(csvFile, true)) {
            writer.write(sample.toCsv());
            writer.write('\n');
        } catch (IOException e) {
            error("Failed to write SlotUseDesyncLab sample: %s", e.getMessage());
        }
    }

    @Override
    public String getInfoString() {
        return sampleCount + " samples | " + totalCorrections + " corr";
    }

    public enum DesyncMode {
        ObserveOnly,
        GhostSwap,
        SwapUseSwap,
        RapidSwapUse,
        UseThenSwap
    }

    private class Sample {
        final long timestamp;
        final String mode;
        final int slotBefore, slotTarget, slotAfter;
        final boolean glidingBefore, glidingAfter;
        final double velBX, velBY, velBZ;
        final double velAX, velAY, velAZ;
        final int totalCorrections;

        Sample(long timestamp, String mode,
               int slotBefore, int slotTarget, int slotAfter,
               boolean glidingBefore, boolean glidingAfter,
               double velBX, double velBY, double velBZ,
               double velAX, double velAY, double velAZ,
               int totalCorrections) {
            this.timestamp = timestamp;
            this.mode = mode;
            this.slotBefore = slotBefore;
            this.slotTarget = slotTarget;
            this.slotAfter = slotAfter;
            this.glidingBefore = glidingBefore;
            this.glidingAfter = glidingAfter;
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
                + slotBefore + ","
                + slotTarget + ","
                + slotAfter + ","
                + (glidingBefore ? 1 : 0) + ","
                + (glidingAfter ? 1 : 0) + ","
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