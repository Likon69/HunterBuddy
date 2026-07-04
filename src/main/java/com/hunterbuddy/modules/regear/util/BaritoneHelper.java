package com.hunterbuddy.modules.regear.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.utils.input.Input;

/**
 * Reference parity: mlep.util.BaritoneHelper. Wraps Baritone API reflection
 * so the module does not need a hard Baritone dependency.
 */
public class BaritoneHelper {
    private static Boolean available;
    private static Boolean elytraProcess;

    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("baritone.api.BaritoneAPI");
                available = true;
            } catch (ClassNotFoundException e) {
                available = false;
            }
        }
        return available;
    }

    public static boolean hasElytraProcess() {
        if (elytraProcess == null) {
            if (!isAvailable()) {
                elytraProcess = false;
            } else {
                try {
                    Class.forName("baritone.api.IBaritone").getMethod("getElytraProcess");
                    elytraProcess = true;
                } catch (Exception e) {
                    elytraProcess = false;
                }
            }
        }
        return elytraProcess;
    }

    public static void stopAllPathing() {
        if (!isAvailable()) return;
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            baritone.getPathingBehavior().cancelEverything();
            baritone.getPathingBehavior().forceCancel();
            baritone.getCustomGoalProcess().setGoal(null);
            baritone.getInputOverrideHandler().clearAllKeys();
            if (hasElytraProcess()) {
                baritone.getCommandManager().execute("forcecancel");
            }
        } catch (Exception ignored) {}
    }

    public static void setInput(Input input, boolean pressed) {
        if (!isAvailable()) return;
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().setInputForceState(input, pressed);
        } catch (Exception ignored) {}
    }

    public static void setJumpPressed(boolean pressed) {
        setInput(Input.JUMP, pressed);
    }

    public static void clearInputs() {
        if (!isAvailable()) return;
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().clearAllKeys();
        } catch (Exception ignored) {}
    }
}
