package com.hunterbuddy.modules.regear.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Module;

public final class ModuleToggles {
    private ModuleToggles() {
    }

    public static void enableLater(Module caller, Module target, Runnable onEnabled) {
        MeteorClient.mc.send(() -> {
            if (!caller.isActive() || target.isActive()) return;
            target.toggle();
            onEnabled.run();
        });
    }

    public static void disableLater(Module caller, Module target, Runnable onDisabled) {
        MeteorClient.mc.send(() -> {
            if (!caller.isActive() || !target.isActive()) return;
            target.toggle();
            onDisabled.run();
        });
    }
}
