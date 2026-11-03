package com.hunterbuddy.modules.regear.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.utils.input.Input;

public class BaritoneHelper {
    private static final String API_CLASS = "baritone.api.BaritoneAPI";
    private static final String BARITONE_INTERFACE = "baritone.api.IBaritone";
    private static final String ELYTRA_GETTER = "getElytraProcess";
    private static final String FORCE_CANCEL_COMMAND = "forcecancel";

    private static Boolean available;
    private static Boolean elytraProcess;

    public static boolean isAvailable() {
        if (available == null) available = classLoads(API_CLASS);
        return available;
    }

    public static boolean hasElytraProcess() {
        if (elytraProcess == null) elytraProcess = isAvailable() && declaresElytraGetter();
        return elytraProcess;
    }

    public static int[] getGoalXZ() {
        if (!isAvailable()) return null;
        try {
            IBaritone baritone = primary();
            if (baritone == null) return null;
            return hasElytraProcess() ? BaritoneGoalReader.readWithElytra(baritone) : BaritoneGoalReader.readCustomGoal(baritone);
        } catch (Throwable failure) {
            return null;
        }
    }

    public static void stopAllPathing() {
        if (!isAvailable()) return;
        try {
            IBaritone baritone = primary();
            baritone.getPathingBehavior().cancelEverything();
            baritone.getPathingBehavior().forceCancel();
            baritone.getCustomGoalProcess().setGoal(null);
            baritone.getInputOverrideHandler().clearAllKeys();
            if (hasElytraProcess()) baritone.getCommandManager().execute(FORCE_CANCEL_COMMAND);
        } catch (Throwable failure) {
        }
    }

    public static void setJumpPressed(boolean pressed) {
        if (!isAvailable()) return;
        try {
            primary().getInputOverrideHandler().setInputForceState(Input.JUMP, pressed);
        } catch (Throwable failure) {
        }
    }

    public static void clearInputs() {
        if (!isAvailable()) return;
        try {
            primary().getInputOverrideHandler().clearAllKeys();
        } catch (Throwable failure) {
        }
    }

    private static IBaritone primary() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    private static boolean classLoads(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable failure) {
            return false;
        }
    }

    private static boolean declaresElytraGetter() {
        try {
            Class.forName(BARITONE_INTERFACE).getMethod(ELYTRA_GETTER);
            return true;
        } catch (Throwable failure) {
            return false;
        }
    }
}
