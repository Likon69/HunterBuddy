package com.hunterbuddy.modules.regear.arealoader.modes;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;

final class PathCompletion {
    private static final String MESSAGE = "[Search Area] Path is complete";

    private PathCompletion() {
    }

    static void disconnect() {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && autoReconnect.isActive()) {
            autoReconnect.toggle();
        }
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) {
            handler.onDisconnect(new DisconnectS2CPacket(Text.literal(MESSAGE)));
        }
    }
}
