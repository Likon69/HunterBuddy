package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;

public class YawLock extends Module {
    private final SettingGroup sgMain = settings.getDefaultGroup();

    private final Setting<Boolean> bypass = sgMain.add(new BoolSetting.Builder()
        .name("2b2t-bypass")
        .description("Wobble the held yaw by a tiny random amount now and then, so it is never perfectly constant.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> jitterAmount = sgMain.add(new DoubleSetting.Builder()
        .name("jitter-amount")
        .description("Largest wobble in degrees, either side.")
        .defaultValue(0.1)
        .min(0.01)
        .max(0.5)
        .visible(bypass::get)
        .build()
    );

    private final Setting<Integer> jitterInterval = sgMain.add(new IntSetting.Builder()
        .name("jitter-interval")
        .description("Ticks between two new wobble values.")
        .defaultValue(10)
        .min(1)
        .max(40)
        .visible(bypass::get)
        .build()
    );

    private float heldYaw;
    private boolean yawPending;
    private float wobble;
    private int ticks;

    public YawLock() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "heading-lock", "Holds your yaw on the nearest multiple of 45 degrees, optionally with a tiny random wobble.", "yaw-lock");
    }

    @Override
    public void onActivate() {
        wobble = 0.0F;
        ticks = 0;
        yawPending = true;
        captureYaw();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (yawPending) captureYaw();

        if (bypass.get()) {
            ticks++;
            if (ticks >= jitterInterval.get()) {
                ticks = 0;
                double amount = jitterAmount.get();
                wobble = (float) ThreadLocalRandom.current().nextDouble(-amount, amount);
            }
        }

        float yaw = bypass.get() ? heldYaw + wobble : heldYaw;
        mc.player.setYaw(yaw);
        if (mc.player.getVehicle() != null) mc.player.getVehicle().setYaw(yaw);
    }

    private void captureYaw() {
        if (mc.player == null) return;
        heldYaw = MathHelper.wrapDegrees(Math.round(mc.player.getYaw() / 45.0F) * 45.0F);
        yawPending = false;
    }
}
