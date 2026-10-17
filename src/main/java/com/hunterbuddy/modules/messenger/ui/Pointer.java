package com.hunterbuddy.modules.messenger.ui;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.util.Window;

public final class Pointer {
    private Pointer() {
    }

    public static double x() {
        Window window = MeteorClient.mc.getWindow();
        return MeteorClient.mc.mouse.getScaledX(window) * window.getScaleFactor();
    }

    public static double y() {
        Window window = MeteorClient.mc.getWindow();
        return MeteorClient.mc.mouse.getScaledY(window) * window.getScaleFactor();
    }

    public static double fromGui(double value) {
        return value * MeteorClient.mc.getWindow().getScaleFactor();
    }
}
