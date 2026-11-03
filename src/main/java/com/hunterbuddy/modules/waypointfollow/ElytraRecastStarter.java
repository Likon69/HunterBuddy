package com.hunterbuddy.modules.waypointfollow;

import com.hunterbuddy.modules.ElytraRecast;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;

public final class ElytraRecastStarter {
    private static final String WAIT_FOR_FALL = "wait-for-fall";
    private static final String ASCEND_MODE = "ascend-mode";
    private static final String AUTO_DISABLE = "auto-disable";

    public enum Ascent {
        CLIMB,
        LEVEL
    }

    private ElytraRecastStarter() {
    }

    public static boolean start(ElytraRecast recast, Ascent ascent) {
        try {
            apply(recast, WAIT_FOR_FALL, false);
            apply(recast, AUTO_DISABLE, true);
            apply(recast, ASCEND_MODE, ascent == Ascent.CLIMB);
            recast.toggle();
            return recast.isActive();
        } catch (Exception e) {
            return false;
        }
    }

    private static void apply(ElytraRecast recast, String name, boolean value) {
        Setting<?> setting = recast.settings.get(name);
        if (setting instanceof BoolSetting bool) bool.set(value);
    }
}
