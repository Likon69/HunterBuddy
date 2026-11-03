package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.modules.Pitch40;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.settings.BoolSetting;

final class Pitch40Tweaks {
    private static final String AUTO_FIREWORK = "auto-firework";
    private static final String AUTO_BOUND_ADJUST = "auto-bound-adjust";

    private record Override(BoolSetting setting, boolean before) {
    }

    private final List<Override> overrides = new ArrayList<>();

    void forceAutoFirework(Pitch40 module) {
        override(module, AUTO_FIREWORK, true);
    }

    void disableAutoBoundAdjust(Pitch40 module) {
        override(module, AUTO_BOUND_ADJUST, false);
    }

    private void override(Pitch40 module, String name, boolean value) {
        if (!(module.settings.get(name) instanceof BoolSetting setting)) return;
        overrides.add(new Override(setting, setting.get()));
        setting.set(value);
    }

    void restore() {
        for (Override override : overrides) {
            override.setting().set(override.before());
        }
        overrides.clear();
    }
}
