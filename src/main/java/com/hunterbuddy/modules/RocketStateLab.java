package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.Utils;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class RocketStateLab extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSequence = settings.createGroup("Sequence");
    private final SettingGroup sgLogging = settings.createGroup("Logging");

    private final Setting<TestMode> mode = sgGeneral.add(new EnumSetting.Builder<TestMode>()
        .name("mode")
        .description("Rocket/start-flying sequence to observe or execute.")
        .defaultValue(TestMode.ObserveOnly)
        .build()
    );

    private final Setting<Boolean> autoRun = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-run")
        .description("Automatically run the selected sequence every interval. Leave off to only observe manual rocket uses.")
        .defaultValue(false)
        .visible(() -> mode.get() != TestMode.ObserveOnly)
        .build()
    );

    private final Setting<Boolean> requireGliding = sgGeneral.add(new BoolSetting.Builder()
        .name("require-gliding")
        .description("Only record or run tests while elytra gliding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireForward = sgGeneral.add(new BoolSetting.Builder()
        .name("require-forward")
        .description("Only auto-run while forward is pressed.")
        .defaultValue(true)
        .visible(autoRun::get)
        .build()
    );

    private final Setting<Integer> autoRunIntervalTicks = sgSequence.add(new IntSetting.Builder()
        .name("auto-run-interval")
        .description("Ticks between automatic test sequence attempts.")
        .defaultValue(160)
        .min(20)
        .max(2400)
        .sliderRange(40, 400)
        .visible(autoRun::get)
        .build()
    );

    private final Setting<Integer> startFlyAfterDelay = sgSequence.add(new IntSetting.Builder()
        .name("start-fly-after-delay")
        .description("Delay after firework use before sending START_FALL_FLYING.")
        .defaultValue(2)
        .min(0)
        .max(20)
        .sliderRange(0, 10)
        .visible(() -> mode.get() == TestMode.StartFlyAfter)
        .build()
    );

    private final Setting<Integer> pulseTicks = sgSequence.add(new IntSetting.Builder()
        .name("pulse-ticks")
        .description("Ticks to send START_FALL_FLYING in pulse mode.")
        .defaultValue(6)
        .min(1)
        .max(40)
        .sliderRange(1, 20)
        .visible(() -> mode.get() == TestMode.StartFlyPulse)
        .build()
    );

    private final Setting<Boolean> chatSummary = sgLogging.add(new BoolSetting.Builder()
        .name("chat-summary")
        .description("Print a short summary when a sample finishes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logManualUses = sgLogging.add(new BoolSetting.Builder()
        .name("log-manual-uses")
        .description("Create samples from manual firework uses detected in outgoing packets.")
        .defaultValue(true)
        .build()
    );

    private final List<Sample> samples = new ArrayList<>();
    private File csvFile;
    private int ticks;
    private int nextSampleId;
    private int totalCorrections;
    private int scheduledStartFlyTicks;
    private int scheduledStartFlyDelay = -1;
    private int suppressInteractSamples;

    public RocketStateLab() {
        super(HunterBuddyAddon.LAB_CATEGORY, "rocket-state-lab", "Logs rocket/start-flying timing, speed gains, and server corrections.");
    }

    @Override
    public void onActivate() {
        samples.clear();
        ticks = 0;
        nextSampleId = 1;
        totalCorrections = 0;
        scheduledStartFlyTicks = 0;
        scheduledStartFlyDelay = -1;
        suppressInteractSamples = 0;
        csvFile = createCsvFile();
        writeHeader();
        if (csvFile != null) info("Logging RocketStateLab samples to %s", csvFile.getName());
    }

    @Override
    public void onDeactivate() {
        samples.clear();
        scheduledStartFlyTicks = 0;
        scheduledStartFlyDelay = -1;
        suppressInteractSamples = 0;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerInteractItemC2SPacket)) return;
        if (!logManualUses.get()) return;
        if (suppressInteractSamples > 0) {
            suppressInteractSamples--;
            return;
        }
        if (!canRecord()) return;
        if (!isHoldingRocket()) return;

        startSample("ManualUse");
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            totalCorrections++;
            for (Sample sample : samples) sample.corrections++;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        ticks++;
        tickScheduledStartFlying();
        tickSamples();

        if (!autoRun.get() || mode.get() == TestMode.ObserveOnly) return;
        if (ticks % autoRunIntervalTicks.get() != 0) return;
        if (!canRecord()) return;
        if (requireForward.get() && !mc.options.forwardKey.isPressed()) return;
        if (countRockets() <= 0) return;

        runSequence();
    }

    private void runSequence() {
        switch (mode.get()) {
            case ObserveOnly -> {
            }
            case NormalUse -> {
                Sample sample = startSample(mode.get().name());
                suppressInteractSamples++;
                int status = Utils.firework(mc, false);
                if (status < 0) discardSample(sample);
            }
            case SlotSwapUse -> {
                int rocketSlot = findRocketInHotbarSlot();
                if (rocketSlot < 0) break;
                Sample sample = startSample(mode.get().name());
                suppressInteractSamples++;
                InvUtils.swap(rocketSlot, true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.swapBack();
            }
            case OffhandUse -> {
                if (!mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) break;
                Sample sample = startSample(mode.get().name());
                suppressInteractSamples++;
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
            }
            case MoveFromInv -> {
                if (hotbarHasRocket()) break;
                int invSlot = findRocketInInventory();
                if (invSlot < 0) break;
                Sample sample = startSample(mode.get().name());
                suppressInteractSamples++;
                int selected = mc.player.getInventory().getSelectedSlot();
                InvUtils.move().from(invSlot).to(selected);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.move().from(selected).to(invSlot);
            }
            case StartFlyBefore -> {
                sendStartFlying();
                Sample sample = startSample(mode.get().name());
                suppressInteractSamples++;
                int status = Utils.firework(mc, false);
                if (status < 0) discardSample(sample);
            }
            case StartFlyAfter -> {
                Sample sample = startSample(mode.get().name());
                suppressInteractSamples++;
                int status = Utils.firework(mc, false);
                if (status >= 0) {
                    scheduledStartFlyDelay = startFlyAfterDelay.get();
                } else {
                    discardSample(sample);
                }
            }
            case StartFlyPulse -> {
                Sample sample = startSample(mode.get().name());
                suppressInteractSamples++;
                int status = Utils.firework(mc, false);
                if (status >= 0) {
                    scheduledStartFlyTicks = pulseTicks.get();
                } else {
                    discardSample(sample);
                }
            }
        }
    }

    private void tickScheduledStartFlying() {
        if (scheduledStartFlyDelay >= 0) {
            if (scheduledStartFlyDelay == 0) {
                sendStartFlying();
                scheduledStartFlyDelay = -1;
            } else {
                scheduledStartFlyDelay--;
            }
        }

        if (scheduledStartFlyTicks > 0) {
            sendStartFlying();
            scheduledStartFlyTicks--;
        }
    }

    private void tickSamples() {
        Iterator<Sample> iterator = samples.iterator();
        while (iterator.hasNext()) {
            Sample sample = iterator.next();
            int age = ticks - sample.startTick;
            sample.capture(age);

            if (age >= 100) {
                writeSample(sample);
                if (chatSummary.get()) {
                    info("#%d %s gain1=%.1f gain3=%.1f gain5=%.1f corr=%d rockets=%d",
                        sample.id,
                        sample.trigger,
                        sample.gain20(),
                        sample.gain60(),
                        sample.gain100(),
                        sample.corrections,
                        sample.rocketsBefore - sample.rocketsAfter);
                }
                iterator.remove();
            }
        }
    }

    private Sample startSample(String trigger) {
        Sample sample = new Sample(nextSampleId++, trigger);
        samples.add(sample);
        return sample;
    }

    private void discardSample(Sample sample) {
        samples.remove(sample);
    }

    private boolean canRecord() {
        return mc.player != null && mc.world != null && (!requireGliding.get() || mc.player.isGliding());
    }

    private boolean isHoldingRocket() {
        return mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET) || mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET);
    }

    private void sendStartFlying() {
        if (mc.getNetworkHandler() != null && mc.player != null) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

    private double horizontalSpeedBps() {
        Vec3d velocity = mc.player.getVelocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
    }

    private int countRockets() {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.FIREWORK_ROCKET)) count += stack.getCount();
        }
        return count;
    }

    private int currentRocketFlightDuration() {
        ItemStack stack = findFirstRocketStack();
        FireworksComponent component = stack.get(DataComponentTypes.FIREWORKS);
        return component != null ? component.flightDuration() : 1;
    }

    private ItemStack findFirstRocketStack() {
        if (mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) return mc.player.getMainHandStack();
        if (mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) return mc.player.getOffHandStack();

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.FIREWORK_ROCKET)) return stack;
        }

        return ItemStack.EMPTY;
    }

    private int findRocketInHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) return i;
        }
        return -1;
    }

    private boolean hotbarHasRocket() {
        return findRocketInHotbarSlot() >= 0;
    }

    private int findRocketInInventory() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) return i;
        }
        return -1;
    }

    private File createCsvFile() {
        try {
            File dir = new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "rocket-state-lab");
            dir.mkdirs();
            return new File(dir, "rocket-state-" + System.currentTimeMillis() + ".csv");
        } catch (Exception e) {
            error("Failed to create RocketStateLab log file: %s", e.getMessage());
            return null;
        }
    }

    private void writeHeader() {
        if (csvFile == null) return;
        try (Writer writer = new FileWriter(csvFile, false)) {
            writer.write("id,trigger,start_tick,flight_duration,pitch,yaw,start_speed,speed_20,speed_60,speed_100,gain_20,gain_60,gain_100,start_y,end_y,rockets_before,rockets_after,corrections,total_corrections,main_hand_rocket,offhand_rocket,selected_slot,is_gliding_start\n");
        } catch (IOException e) {
            error("Failed to write RocketStateLab header: %s", e.getMessage());
        }
    }

    private void writeSample(Sample sample) {
        if (csvFile == null) return;
        try (Writer writer = new FileWriter(csvFile, true)) {
            writer.write(sample.toCsv());
            writer.write('\n');
        } catch (IOException e) {
            error("Failed to write RocketStateLab sample: %s", e.getMessage());
        }
    }

    @Override
    public String getInfoString() {
        return samples.size() + " samples | " + totalCorrections + " corr";
    }

    public enum TestMode {
        ObserveOnly,
        NormalUse,
        SlotSwapUse,
        OffhandUse,
        MoveFromInv,
        StartFlyBefore,
        StartFlyAfter,
        StartFlyPulse
    }

    private class Sample {
        final int id;
        final String trigger;
        final int startTick;
        final int flightDuration;
        final float pitch;
        final float yaw;
        final double startSpeed;
        final double startY;
        final int rocketsBefore;
        final boolean mainHandRocket;
        final boolean offhandRocket;
        final int selectedSlot;
        final boolean isGlidingStart;
        int rocketsAfter;
        int corrections;
        double speed20 = Double.NaN;
        double speed60 = Double.NaN;
        double speed100 = Double.NaN;
        double endY;

        Sample(int id, String trigger) {
            this.id = id;
            this.trigger = trigger;
            this.startTick = ticks;
            this.flightDuration = currentRocketFlightDuration();
            this.pitch = mc.player.getPitch();
            this.yaw = mc.player.getYaw();
            this.startSpeed = horizontalSpeedBps();
            this.startY = mc.player.getY();
            this.rocketsBefore = countRockets();
            this.mainHandRocket = mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET);
            this.offhandRocket = mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET);
            this.selectedSlot = mc.player.getInventory().getSelectedSlot();
            this.isGlidingStart = mc.player.isGliding();
            this.rocketsAfter = this.rocketsBefore;
            this.endY = this.startY;
        }

        void capture(int age) {
            double speed = horizontalSpeedBps();
            if (age >= 20 && Double.isNaN(speed20)) speed20 = speed;
            if (age >= 60 && Double.isNaN(speed60)) speed60 = speed;
            if (age >= 100 && Double.isNaN(speed100)) speed100 = speed;
            rocketsAfter = countRockets();
            endY = mc.player.getY();
        }

        double gain20() {
            return Double.isNaN(speed20) ? 0.0 : speed20 - startSpeed;
        }

        double gain60() {
            return Double.isNaN(speed60) ? 0.0 : speed60 - startSpeed;
        }

        double gain100() {
            return Double.isNaN(speed100) ? 0.0 : speed100 - startSpeed;
        }

        String toCsv() {
            return id + ","
                + trigger + ","
                + startTick + ","
                + flightDuration + ","
                + fmt(pitch) + ","
                + fmt(yaw) + ","
                + fmt(startSpeed) + ","
                + fmt(speed20) + ","
                + fmt(speed60) + ","
                + fmt(speed100) + ","
                + fmt(gain20()) + ","
                + fmt(gain60()) + ","
                + fmt(gain100()) + ","
                + fmt(startY) + ","
                + fmt(endY) + ","
                + rocketsBefore + ","
                + rocketsAfter + ","
                + corrections + ","
                + totalCorrections + ","
                + (mainHandRocket ? 1 : 0) + ","
                + (offhandRocket ? 1 : 0) + ","
                + selectedSlot + ","
                + (isGlidingStart ? 1 : 0);
        }

        String fmt(double value) {
            if (Double.isNaN(value)) return "";
            return String.format(java.util.Locale.ROOT, "%.3f", value);
        }
    }
}
