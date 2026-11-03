package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.utils.player.ChatUtils;

final class AreaDebug {
    private final AreaLoader area;

    AreaDebug(AreaLoader area) {
        this.area = area;
    }

    void info(String message) {
        info("%s", message);
    }

    void info(String format, Object... args) {
        if (!area.debugMode.get()) return;
        ChatUtils.info(format, args);
    }
}
