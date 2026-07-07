package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.MsgUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class AngleCalculator extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<BlockPos> to = sgGeneral.add(new BlockPosSetting.Builder()
        .name("to")
        .description("Target coordinate to travel straight towards.")
        .defaultValue(new BlockPos(0, 64, 1000))
        .build()
    );

    private final Setting<Boolean> snapPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("level-pitch")
        .description("Also set your pitch to 0 (level) when applying the angle.")
        .defaultValue(true)
        .build()
    );

    private float targetYaw;

    public AngleCalculator() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "angle-calculator", "Continuously locks your view towards a target coordinate to travel straight along it (highway trails).");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }
        if (!computeYaw()) {
            MsgUtil.sendModuleMsg("You are already at the target point - nothing to aim at.", this.name);
            toggle();
            return;
        }
        double dx = to.get().getX() + 0.5 - mc.player.getX();
        double dz = to.get().getZ() + 0.5 - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        MsgUtil.sendModuleMsg(
            String.format("Locking yaw to target: §a%.2f°§r  (distance §a%.1f§r blocks).", targetYaw, dist), this.name
        );
        applyView();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player != null && mc.world != null) {
            if (computeYaw()) applyView();
        }
    }

    private boolean computeYaw() {
        if (mc.player == null) return false;
        double dx = to.get().getX() + 0.5 - mc.player.getX();
        double dz = to.get().getZ() + 0.5 - mc.player.getZ();
        if (dx == 0.0 && dz == 0.0) return false;
        targetYaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        return true;
    }

    private void applyView() {
        if (mc.player == null) return;
        mc.player.setYaw(targetYaw);
        if (snapPitch.get()) mc.player.setPitch(0.0F);
        if (mc.player.hasVehicle()) {
            mc.player.getVehicle().setYaw(targetYaw);
            if (snapPitch.get()) mc.player.getVehicle().setPitch(0.0F);
        }
    }
}