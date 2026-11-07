package com.hunterbuddy.pet;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class PetInput {
    private KeyBinding[] bindings;
    private double mouseX;
    private double mouseY;
    private boolean observed;

    public void reset() {
        observed = false;
    }

    public boolean active(MinecraftClient mc) {
        double x = mc.mouse.getX();
        double y = mc.mouse.getY();
        boolean moved = observed && (x != mouseX || y != mouseY);
        mouseX = x;
        mouseY = y;
        observed = true;
        return moved || mc.currentScreen == null && held(mc);
    }

    private boolean held(MinecraftClient mc) {
        if (bindings == null) {
            bindings = new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey,
                mc.options.jumpKey, mc.options.sneakKey, mc.options.attackKey, mc.options.useKey};
        }
        for (KeyBinding binding : bindings) {
            InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
            if (key.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(mc.getWindow(), key.getCode())) return true;
            if (key.getCategory() == InputUtil.Type.MOUSE
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS) return true;
        }
        return false;
    }
}
