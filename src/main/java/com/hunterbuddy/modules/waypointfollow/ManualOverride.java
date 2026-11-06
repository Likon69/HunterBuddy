package com.hunterbuddy.modules.waypointfollow;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class ManualOverride {
    private long lastInputAt = Long.MIN_VALUE;

    public void noteLook(double dx, double dy) {
        if (dx == 0.0 && dy == 0.0) return;
        if (cameraDetached()) return;
        if (MinecraftClient.getInstance().currentScreen != null) return;
        this.lastInputAt = System.currentTimeMillis();
    }

    public void poll(MinecraftClient mc) {
        if (mc.currentScreen != null || mc.options == null) return;
        if (anyHeld(mc, mc.options.forwardKey, mc.options.backKey, mc.options.leftKey,
            mc.options.rightKey, mc.options.jumpKey, mc.options.sneakKey)) {
            this.lastInputAt = System.currentTimeMillis();
        }
    }

    public boolean steering(long idleMillis) {
        return this.lastInputAt != Long.MIN_VALUE && System.currentTimeMillis() - this.lastInputAt < idleMillis;
    }

    public void reset() {
        this.lastInputAt = Long.MIN_VALUE;
    }

    private static boolean anyHeld(MinecraftClient mc, KeyBinding... bindings) {
        for (KeyBinding binding : bindings) {
            if (physicallyHeld(mc, binding)) return true;
        }
        return false;
    }

    private static boolean physicallyHeld(MinecraftClient mc, KeyBinding binding) {
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        return key.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
    }

    private static boolean cameraDetached() {
        Modules modules = Modules.get();
        return modules.isActive(Freecam.class) || modules.isActive(FreeLook.class);
    }
}
